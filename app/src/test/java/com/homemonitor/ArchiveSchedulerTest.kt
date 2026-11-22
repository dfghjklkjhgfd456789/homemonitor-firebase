package com.homemonitor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArchiveSchedulerTest {

    @Test
    fun testConfigurationJsonDefaults() {
        val config = JSONObject().apply {
            put("archiveEnabled", true)
            put("archiveIntervalHours", 6)
            put("archiveRecordThreshold", 1000)
            put("requiresCharging", false)
            put("requiresUnmeteredNetwork", true)
            put("deleteLocalAfterVerifiedCommit", true)
            put("manualArchiveEnabled", true)
        }

        assertTrue(config.getBoolean("archiveEnabled"))
        assertEquals(6, config.getInt("archiveIntervalHours"))
        assertEquals(1000, config.getInt("archiveRecordThreshold"))
        assertFalse(config.getBoolean("requiresCharging"))
        assertTrue(config.getBoolean("requiresUnmeteredNetwork"))
        assertTrue(config.getBoolean("deleteLocalAfterVerifiedCommit"))
        assertTrue(config.getBoolean("manualArchiveEnabled"))
    }

    @Test
    fun testArchiveCommandRouting() {
        val statusRes = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "archive.getStatus",
            params = JSONObject(),
            role = UserRole.PARENT
        )
        assertTrue(statusRes.success)
        assertNotNull(statusRes.data)

        val runNowRes = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "archive.runNow",
            params = JSONObject(),
            role = UserRole.PARENT
        )
        assertFalse(runNowRes.success)
        assertTrue(runNowRes.error!!.contains("Context required"))
    }
}
