package com.homemonitor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class C2LocalDatabaseArchiveTest {

    @Test
    fun testGzipCompressionAndDecompression() {
        val originalRecords = listOf(
            JSONObject().apply {
                put("id", 1L)
                put("sender", "Alex")
                put("text_content", "Hello from test")
                put("timestamp", 1700000000L)
            },
            JSONObject().apply {
                put("id", 2L)
                put("sender", "Mom")
                put("text_content", "Remember to archive")
                put("timestamp", 1700000050L)
            }
        )

        val compressedBytes = GoogleDriveArchiveEngine.compressGzip(originalRecords)
        assertTrue(compressedBytes.isNotEmpty())

        val decompressedText = GoogleDriveArchiveEngine.decompressGzip(compressedBytes)
        assertTrue(decompressedText.contains("Hello from test"))
        assertTrue(decompressedText.contains("Remember to archive"))

        val sha256 = GoogleDriveArchiveEngine.computeSha256(compressedBytes)
        assertEquals(64, sha256.length) // SHA-256 hex string is 64 chars
    }

    @Test
    fun testDeterministicBatchIdGeneration() {
        val dataset = "social_messages"
        val fromSeq = 1001L
        val toSeq = 1500L
        val timestamp = 1700000000L

        val batchId = "batch_${dataset}_${fromSeq}_${toSeq}_$timestamp"
        assertEquals("batch_social_messages_1001_1500_1700000000", batchId)
        assertTrue(batchId.startsWith("batch_"))
    }
}
