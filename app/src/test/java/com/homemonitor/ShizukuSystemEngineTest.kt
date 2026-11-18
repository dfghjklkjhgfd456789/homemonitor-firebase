package com.google.android.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuSystemEngineTest {

    @Test
    fun testShizukuStatusReportDataClass() {
        val report = ShizukuSystemEngine.ShizukuStatusReport(
            isBinderAvailable = true,
            isPermissionGranted = true,
            statusMessage = "Active & Granted 🟢 (Full ADB Privileges Unlocked)"
        )

        assertTrue(report.isBinderAvailable)
        assertTrue(report.isPermissionGranted)
        assertEquals("Active & Granted 🟢 (Full ADB Privileges Unlocked)", report.statusMessage)
    }

    @Test
    fun testShizukuAvailabilityInUnitTest() {
        // In local unit test JVM environment without Shizuku service running,
        // availability should return false safely without throwing exception.
        val isAvailable = ShizukuSystemEngine.isShizukuAvailable()
        assertFalse(isAvailable)

        val hasPermission = ShizukuSystemEngine.hasShizukuPermission()
        assertFalse(hasPermission)

        val report = ShizukuSystemEngine.getStatusReport()
        assertFalse(report.isBinderAvailable)
        assertFalse(report.isPermissionGranted)
        assertTrue(report.statusMessage.contains("Unavailable"))
    }

    @Test
    fun testExecuteAdbCommandFallbackWhenUnavailable() = runBlocking {
        val result = ShizukuSystemEngine.executeAdbCommand("getprop ro.build.version.release")
        assertNotNull(result)
        assertTrue(result.contains("Shizuku service is not running"))
    }

    @Test
    fun testGrantPermissionFallbackWhenUnavailable() = runBlocking {
        val result = ShizukuSystemEngine.grantPermission("com.example.app", "android.permission.WRITE_SETTINGS")
        assertNotNull(result)
        assertTrue(result.contains("Shizuku service is not running"))
    }

    @Test
    fun testForceStopAppFallbackWhenUnavailable() = runBlocking {
        val result = ShizukuSystemEngine.forceStopApp("com.example.app")
        assertNotNull(result)
        assertTrue(result.contains("Shizuku service is not running"))
    }
}
