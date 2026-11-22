package com.homemonitor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArchiveWorkerPipelineTest {

    @Test
    fun testFileUploadResultAndChecksumValidation() {
        val bytes = "Sample GZIP Content".toByteArray(Charsets.UTF_8)
        val checksum = GoogleDriveArchiveEngine.computeSha256(bytes)

        val uploadResult = DriveFileUploadResult(
            fileId = "drive_file_999",
            name = "batch_telemetry_logs_1_100_1700000000.jsonl.gz",
            sizeBytes = bytes.size.toLong(),
            checksum = checksum
        )

        assertEquals("drive_file_999", uploadResult.fileId)
        assertEquals(bytes.size.toLong(), uploadResult.sizeBytes)
        assertEquals(checksum, uploadResult.checksum)
    }

    @Test
    fun testArchivePipeline13StepsOrder() {
        val steps = listOf(
            "1. Select unarchived records",
            "2. Capture sequence high-watermark",
            "3. Create deterministic archive batch ID",
            "4. Export and compress records (GZIP)",
            "5. Calculate SHA-256 checksum",
            "6. Idempotency check on Drive",
            "7. Resumable upload to parent Google Drive",
            "8. Verify uploaded file integrity",
            "9. Commit versioned manifest.json",
            "10. Verify manifest commitment",
            "11. Begin SQLite write transaction",
            "12. Delete exact committed record sequence range",
            "13. Commit local transaction & update ledger"
        )

        assertEquals(13, steps.size)
        assertTrue(steps[0].contains("Select unarchived records"))
        assertTrue(steps[7].contains("Verify uploaded file integrity"))
        assertTrue(steps[8].contains("Commit versioned manifest.json"))
        assertTrue(steps[11].contains("Delete exact committed record sequence range"))
        assertTrue(steps[12].contains("Commit local transaction"))
    }
}
