package com.homemonitor

import android.content.Context
import android.util.Log
import com.google.android.system.C2LocalDatabase
import com.google.android.system.DeviceIdProvider
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * HistoricalDataRepository
 * Authoritative data-source router routing queries between live local SQLite (`C2LocalDatabase`)
 * and the Google Drive historical cloud archive (`GoogleDriveArchiveEngine`).
 *
 * Routing Rules:
 * - Unarchived records (sequence > lastArchivedSequence): fetched from local SQLite over WebRTC API.
 * - Archived records (sequence <= lastArchivedSequence): fetched from Google Drive archive batch files.
 * - Boundary requests: queries both local SQLite and Google Drive, merging results by stable record ID and timestamp.
 */
object HistoricalDataRepository {

    private const val TAG = "HistoricalDataRepository"

    fun getSocialMessages(context: Context, appKey: String): JSONArray {
        val mergedArray = JSONArray()
        val key = appKey.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

        try {
            // 1. Fetch unarchived live records from local SQLite
            val db = C2LocalDatabase.getInstance(context)
            val localJson = db.getSocialMessagesJson(key)
            val seenRecordKeys = mutableSetOf<String>()

            for (i in 0 until localJson.length()) {
                val obj = localJson.getJSONObject(i)
                val recordKey = "${obj.optString("sender")}_${obj.optLong("timestamp")}"
                seenRecordKeys.add(recordKey)
                mergedArray.put(obj)
            }

            // 2. If Drive is connected, query Drive archive manifest for older records
            if (GoogleDriveArchiveEngine.isDriveConnected(context)) {
                val accessToken = GoogleDriveArchiveEngine.getOrRefreshToken(context)
                if (!accessToken.isNullOrBlank()) {
                    val deviceId = DeviceIdProvider.getDeviceId(context)
                    val familyId = "FAMILY_$deviceId"
                    val deviceFolderId = GoogleDriveArchiveEngine.findDeviceRootFolder(accessToken, familyId, deviceId)

                    if (!deviceFolderId.isNullOrBlank()) {
                        val manifest = GoogleDriveArchiveEngine.getManifest(accessToken, deviceFolderId)
                        val datasetObj = manifest?.optJSONObject("datasets")?.optJSONObject(C2LocalDatabase.TABLE_SOCIAL_MESSAGES)
                        val batchesArr = datasetObj?.optJSONArray("batches")

                        if (batchesArr != null) {
                            for (b in 0 until batchesArr.length()) {
                                val batchObj = batchesArr.getJSONObject(b)
                                val driveFileId = batchObj.optString("driveFileId", "")
                                if (driveFileId.isNotBlank()) {
                                    val archivedRecords = GoogleDriveArchiveEngine.readArchivedBatchRecords(accessToken, driveFileId)
                                    for (rec in archivedRecords) {
                                        val recAppKey = rec.optString("app_key", "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                                        if (recAppKey == key) {
                                            val sender = rec.optString("sender")
                                            val ts = rec.optLong("timestamp")
                                            val recordKey = "${sender}_$ts"
                                            if (!seenRecordKeys.contains(recordKey)) {
                                                seenRecordKeys.add(recordKey)
                                                val formattedObj = JSONObject().apply {
                                                    put("sender", sender)
                                                    put("text", rec.optString("text_content"))
                                                    put("direction", rec.optString("direction"))
                                                    put("time", rec.optString("time_str"))
                                                    put("timestamp", ts)
                                                    put("source", "GOOGLE_DRIVE_ARCHIVE")
                                                }
                                                mergedArray.put(formattedObj)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSocialMessages exception for $appKey: ${e.message}", e)
        }
        return mergedArray
    }

    fun getTelemetryLogs(context: Context, category: String): JSONArray {
        val mergedArray = JSONArray()
        val catKey = category.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "")

        try {
            val db = C2LocalDatabase.getInstance(context)
            val localJson = db.getTelemetryLogsJson(catKey)
            val seenRecordKeys = mutableSetOf<String>()

            for (i in 0 until localJson.length()) {
                val obj = localJson.getJSONObject(i)
                val recordKey = "${obj.optString("content")}_${obj.optLong("timestamp")}"
                seenRecordKeys.add(recordKey)
                mergedArray.put(obj)
            }

            if (GoogleDriveArchiveEngine.isDriveConnected(context)) {
                val accessToken = GoogleDriveArchiveEngine.getOrRefreshToken(context)
                if (!accessToken.isNullOrBlank()) {
                    val deviceId = DeviceIdProvider.getDeviceId(context)
                    val familyId = "FAMILY_$deviceId"
                    val deviceFolderId = GoogleDriveArchiveEngine.findDeviceRootFolder(accessToken, familyId, deviceId)

                    if (!deviceFolderId.isNullOrBlank()) {
                        val manifest = GoogleDriveArchiveEngine.getManifest(accessToken, deviceFolderId)
                        val datasetObj = manifest?.optJSONObject("datasets")?.optJSONObject(C2LocalDatabase.TABLE_TELEMETRY_LOGS)
                        val batchesArr = datasetObj?.optJSONArray("batches")

                        if (batchesArr != null) {
                            for (b in 0 until batchesArr.length()) {
                                val batchObj = batchesArr.getJSONObject(b)
                                val driveFileId = batchObj.optString("driveFileId", "")
                                if (driveFileId.isNotBlank()) {
                                    val archivedRecords = GoogleDriveArchiveEngine.readArchivedBatchRecords(accessToken, driveFileId)
                                    for (rec in archivedRecords) {
                                        val recCatKey = rec.optString("category", "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "")
                                        if (recCatKey == catKey) {
                                            val content = rec.optString("content")
                                            val ts = rec.optLong("timestamp")
                                            val recordKey = "${content}_$ts"
                                            if (!seenRecordKeys.contains(recordKey)) {
                                                seenRecordKeys.add(recordKey)
                                                val formattedObj = JSONObject().apply {
                                                    put("content", content)
                                                    put("timestamp", ts)
                                                    put("source", "GOOGLE_DRIVE_ARCHIVE")
                                                }
                                                mergedArray.put(formattedObj)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTelemetryLogs exception for $category: ${e.message}", e)
        }
        return mergedArray
    }

    fun getHistoricalDataset(
        context: Context,
        dataset: String,
        fromTime: Long = 0L,
        toTime: Long = System.currentTimeMillis()
    ): JSONObject {
        val result = JSONObject()
        val recordsArr = JSONArray()
        var source = "LOCAL_SQLITE"

        try {
            val db = C2LocalDatabase.getInstance(context)
            val lastArchivedSeq = db.getLastArchivedSequence(dataset)
            val pendingCount = db.getPendingRecordCountForDataset(dataset)

            val unarchivedRecords = db.getUnarchivedRecords(dataset, lastArchivedSeq + 1, Long.MAX_VALUE)
            for (rec in unarchivedRecords) {
                val ts = rec.optLong("timestamp")
                if (ts in fromTime..toTime) {
                    rec.put("source", "LOCAL_SQLITE")
                    recordsArr.put(rec)
                }
            }

            if (GoogleDriveArchiveEngine.isDriveConnected(context)) {
                val accessToken = GoogleDriveArchiveEngine.getOrRefreshToken(context)
                if (!accessToken.isNullOrBlank()) {
                    val deviceId = DeviceIdProvider.getDeviceId(context)
                    val familyId = "FAMILY_$deviceId"
                    val deviceFolderId = GoogleDriveArchiveEngine.findDeviceRootFolder(accessToken, familyId, deviceId)

                    if (!deviceFolderId.isNullOrBlank()) {
                        val manifest = GoogleDriveArchiveEngine.getManifest(accessToken, deviceFolderId)
                        val datasetObj = manifest?.optJSONObject("datasets")?.optJSONObject(dataset)
                        val batchesArr = datasetObj?.optJSONArray("batches")

                        if (batchesArr != null && batchesArr.length() > 0) {
                            source = "HYBRID_LOCAL_AND_DRIVE"
                            for (i in 0 until batchesArr.length()) {
                                val bObj = batchesArr.getJSONObject(i)
                                val driveFileId = bObj.optString("driveFileId")
                                if (driveFileId.isNotBlank()) {
                                    val batchRecords = GoogleDriveArchiveEngine.readArchivedBatchRecords(accessToken, driveFileId)
                                    for (rec in batchRecords) {
                                        val ts = rec.optLong("timestamp")
                                        if (ts in fromTime..toTime) {
                                            rec.put("source", "GOOGLE_DRIVE_ARCHIVE")
                                            recordsArr.put(rec)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            result.apply {
                put("dataset", dataset)
                put("lastArchivedSequence", lastArchivedSeq)
                put("pendingUnarchivedCount", pendingCount)
                put("totalCount", recordsArr.length())
                put("dataSource", source)
                put("records", recordsArr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHistoricalDataset exception for $dataset: ${e.message}", e)
            result.apply {
                put("error", true)
                put("message", e.message)
            }
        }
        return result
    }
}
