package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceWatchdogTest {

    @Test
    fun testWatchdogConstants() {
        assertEquals("watchdog_last_check_timestamp", ServiceWatchdogWorker.PREF_LAST_CHECK_TIME)
        assertEquals("watchdog_check_count", ServiceWatchdogWorker.PREF_CHECK_COUNT)
        assertEquals("watchdog_recovery_count", ServiceWatchdogWorker.PREF_RECOVERY_COUNT)
    }

    @Test
    fun testWatchdogStatusReportDataClass() {
        val report = ServiceWatchdogScheduler.WatchdogStatusReport(
            isWatchdogActive = true,
            lastCheckTimestamp = 1700000000000L,
            lastCheckFormatted = "2026-11-12 12:00:00",
            checkCount = 42L,
            recoveryCount = 3L,
            isMonitorServiceAlive = true,
            cloudflareState = "DISABLED"
        )

        assertTrue(report.isWatchdogActive)
        assertEquals(42L, report.checkCount)
        assertEquals(3L, report.recoveryCount)
        assertTrue(report.isMonitorServiceAlive)
        assertEquals("DISABLED", report.cloudflareState)
        assertNotNull(report.lastCheckFormatted)
    }

    @Test
    fun testInactiveWatchdogStatusReport() {
        val report = ServiceWatchdogScheduler.WatchdogStatusReport(
            isWatchdogActive = false,
            lastCheckTimestamp = 0L,
            lastCheckFormatted = "Not Yet Executed",
            checkCount = 0L,
            recoveryCount = 0L,
            isMonitorServiceAlive = false,
            cloudflareState = "DISABLED"
        )

        assertFalse(report.isWatchdogActive)
        assertEquals(0L, report.checkCount)
        assertEquals(0L, report.recoveryCount)
        assertFalse(report.isMonitorServiceAlive)
        assertEquals("DISABLED", report.cloudflareState)
    }
}
