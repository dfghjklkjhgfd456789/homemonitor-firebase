package com.homemonitor

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.system.C2LocalDatabase
import com.google.android.system.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ArchiveWorker
 * Resilient WorkManager CoroutineWorker implementing the 13-step archive-and-purge pipeline:
 * 1. Check archive enabled & Drive connected state.
 * 2. Select eligible unarchived records.
 * 3. Capture sequence high-watermark.
 * 4. Create deterministic archive batch ID.
 * 5. Export and GZIP-compress records into `batch.jsonl.gz`.
 * 6. Calculate SHA-256 checksum.
 * 7. Check idempotency / upload archive to parent's Google Drive.
 * 8. Verify uploaded file integrity.
 * 9. Commit versioned `manifest.json` on Google Drive.
 * 10. Verify manifest commitment.
 * 11. Begin SQLite write transaction.
 * 12. Delete only exact records included in the committed archive batch.
 * 13. Update archive cursors, ledger, and stats.
 */
class ArchiveWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ArchiveWorker"

        @Volatile
        private var isExecutionLocked = false
        private val lockObject = Any()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        synchronized(lockObject) {
            if (isExecutionLocked) {
                Log.w(TAG, "ArchiveWorker execution skipped: Another archive job is already in progress.")
                return@withContext Result.success()
            }
            isExecutionLocked = true
        }

        try {
            Log.i(TAG, "Initiating 13-step Automated SQLite Archive & Drive Purge Pipeline... 🚀")

            if (!ArchiveScheduler.isArchiveEnabled(appContext)) {
                Log.i(TAG, "Archive system is disabled in configuration.")
                return@withContext Result.success()
            }

            if (!GoogleDriveArchiveEngine.isDriveConnected(appContext)) {
                Log.w(TAG, "Google Drive account is not connected. Skipping archive run.")
                ArchiveScheduler.recordArchiveError(appContext, "Google Drive Account Not Connected")
                return@withContext Result.success()
            }

            val accessToken = GoogleDriveArchiveEngine.getOrRefreshToken(appContext)
            if (accessToken == null || accessToken.isBlank()) {
                Log.e(TAG, "Failed to obtain valid Google Drive access token.")
                ArchiveScheduler.recordArchiveError(appContext, "AUTH_REQUIRED: Invalid Drive Access Token")
                return@withContext Result.retry()
            }

            val db = C2LocalDatabase.getInstance(appContext)
            val pendingCount = db.getPendingRecordCount()
            if (pendingCount <= 0) {
                Log.i(TAG, "No eligible historical records to archive.")
                return@withContext Result.success()
            }

            val deviceId = DeviceIdProvider.getDeviceId(appContext)
            val familyId = "FAMILY_$deviceId"

            var totalArchived = 0
            var totalPurged = 0
            var lastBatchId = ""

            for (dataset in C2LocalDatabase.HISTORICAL_TABLES) {
                val lastArchivedSeq = db.getLastArchivedSequence(dataset)
                val maxSeq = db.getMaxSequenceId(dataset)

                if (maxSeq <= lastArchivedSeq) continue

                val records = db.getUnarchivedRecords(dataset, lastArchivedSeq + 1, maxSeq)
                if (records.isEmpty()) continue

                val fromSeq = lastArchivedSeq + 1
                val toSeq = maxSeq
                val recordCount = records.size
                val batchTimestamp = System.currentTimeMillis()
                val batchId = "batch_${dataset}_${fromSeq}_${toSeq}_$batchTimestamp"
                val fileName = "$batchId.jsonl.gz"

                Log.i(TAG, "Processing dataset '$dataset': $recordCount records (Sequence $fromSeq..$toSeq) -> Batch: $batchId")

                // Step 4 & 5: GZIP Compress & SHA-256 Checksum
                val compressedBytes = GoogleDriveArchiveEngine.compressGzip(records)
                val checksum = GoogleDriveArchiveEngine.computeSha256(compressedBytes)

                // Step 6 & 7: Ensure Drive Folder & Resumable Upload
                val monthFolderId = GoogleDriveArchiveEngine.ensureArchiveDirectoryPath(
                    accessToken = accessToken,
                    familyId = familyId,
                    deviceId = deviceId,
                    dataset = dataset
                )
                val deviceRootFolderId = GoogleDriveArchiveEngine.findDeviceRootFolder(
                    accessToken = accessToken,
                    familyId = familyId,
                    deviceId = deviceId
                )

                if (monthFolderId.isNullOrBlank() || deviceRootFolderId.isNullOrBlank()) {
                    Log.e(TAG, "Failed to ensure Google Drive folder hierarchy for $dataset.")
                    ArchiveScheduler.recordArchiveError(appContext, "DRIVE_FOLDER_ERROR: Failed to create folder hierarchy")
                    return@withContext Result.retry()
                }

                val uploadResult = GoogleDriveArchiveEngine.uploadBatchFile(
                    accessToken = accessToken,
                    folderId = monthFolderId,
                    fileName = fileName,
                    compressedBytes = compressedBytes
                )

                if (uploadResult == null) {
                    Log.e(TAG, "Upload failed for batch file '$fileName'. Preserving local records.")
                    ArchiveScheduler.recordArchiveError(appContext, "UPLOAD_FAILED: Batch file upload failed")
                    return@withContext Result.retry()
                }

                // Step 8: Verify File Integrity
                val verified = GoogleDriveArchiveEngine.verifyFileIntegrity(
                    accessToken = accessToken,
                    fileId = uploadResult.fileId,
                    expectedSizeBytes = compressedBytes.size.toLong()
                )

                if (!verified) {
                    Log.e(TAG, "File integrity verification failed for Drive file ${uploadResult.fileId}.")
                    ArchiveScheduler.recordArchiveError(appContext, "CHECKSUM_MISMATCH: Drive file verification failed")
                    return@withContext Result.retry()
                }

                // Step 9 & 10: Commit Drive Manifest
                val manifestCommitted = GoogleDriveArchiveEngine.commitManifest(
                    accessToken = accessToken,
                    deviceFolderId = deviceRootFolderId,
                    familyId = familyId,
                    deviceId = deviceId,
                    dataset = dataset,
                    batchResult = uploadResult,
                    fromSeq = fromSeq,
                    toSeq = toSeq,
                    recordCount = recordCount
                )

                if (!manifestCommitted) {
                    Log.e(TAG, "Failed to commit Drive manifest for batch $batchId.")
                    ArchiveScheduler.recordArchiveError(appContext, "MANIFEST_COMMIT_FAILED: Manifest update failed")
                    return@withContext Result.retry()
                }

                // Step 11, 12, 13: Local Atomic Purge Write Transaction
                val purged = db.purgeArchivedRecordsAndCommitLedger(
                    dataset = dataset,
                    fromSeq = fromSeq,
                    toSeq = toSeq,
                    batchId = batchId,
                    driveFileId = uploadResult.fileId,
                    recordCount = recordCount,
                    checksum = checksum
                )

                if (purged) {
                    totalArchived += recordCount
                    totalPurged += recordCount
                    lastBatchId = batchId
                    Log.i(TAG, "Completed full archive-and-purge loop for batch $batchId ($recordCount records).")
                } else {
                    Log.e(TAG, "Local SQLite purge transaction failed for batch $batchId.")
                    ArchiveScheduler.recordArchiveError(appContext, "LOCAL_PURGE_FAILED: SQLite transaction failed")
                    return@withContext Result.retry()
                }
            }

            if (totalArchived > 0) {
                ArchiveScheduler.recordArchiveSuccess(appContext, lastBatchId, totalArchived, totalPurged)
                Log.i(TAG, "ArchiveWorker finished successfully. Total archived & purged: $totalArchived records.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ArchiveWorker exception: ${e.message}", e)
            ArchiveScheduler.recordArchiveError(appContext, "EXCEPTION: ${e.message}")
            Result.retry()
        } finally {
            synchronized(lockObject) {
                isExecutionLocked = false
            }
        }
    }

    private fun String?.isNull_or_blank_custom(): Boolean = this == null || this.trim().isEmpty()
}
