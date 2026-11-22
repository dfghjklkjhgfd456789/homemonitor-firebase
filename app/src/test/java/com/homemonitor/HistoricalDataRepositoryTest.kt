package com.homemonitor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoricalDataRepositoryTest {

    @Test
    fun testHistoricalDataQueryWithoutContext() {
        val res = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "archive.getHistoricalData",
            params = JSONObject().apply {
                put("dataset", "social_messages")
            },
            role = UserRole.PARENT
        )
        assertFalse(res.success)
        assertTrue(res.error!!.contains("Context required"))
    }

    @Test
    fun testManifestFormattingAndDatasetParsing() {
        val manifestJson = JSONObject().apply {
            put("archiveVersion", 1)
            put("parentUid", "FAMILY_DEV_001")
            put("deviceId", "DEV_001")
            put("datasets", JSONObject().apply {
                put("social_messages", JSONObject().apply {
                    put("lastArchivedSequence", 1500L)
                    put("batches", JSONArray().apply {
                        put(JSONObject().apply {
                            put("batchId", "batch_social_messages_1001_1500_1700000000")
                            put("driveFileId", "file_12345")
                            put("fromSequence", 1001L)
                            put("toSequence", 1500L)
                            put("recordCount", 500)
                            put("checksum", "a1b2c3d4e5f6")
                        })
                    })
                })
            })
        }

        assertEquals(1, manifestJson.getInt("archiveVersion"))
        assertEquals("FAMILY_DEV_001", manifestJson.getString("parentUid"))

        val datasetObj = manifestJson.getJSONObject("datasets").getJSONObject("social_messages")
        assertEquals(1500L, datasetObj.getLong("lastArchivedSequence"))

        val batch = datasetObj.getJSONArray("batches").getJSONObject(0)
        assertEquals("file_12345", batch.getString("driveFileId"))
        assertEquals(500, batch.getInt("recordCount"))
    }
}
