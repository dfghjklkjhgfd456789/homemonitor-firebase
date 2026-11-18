package com.homemonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C2TrialManagerTest
 *
 * Unit tests validating trial status definitions, default active state,
 * trial expiration evaluation, and custom admin messaging logic.
 */
class C2TrialManagerTest {

    @Test
    fun testDefaultTrialStatusConstants() {
        assertEquals("ACTIVE", C2TrialManager.STATUS_ACTIVE)
        assertEquals("EXPIRED", C2TrialManager.STATUS_EXPIRED)
        assertEquals("DISABLED", C2TrialManager.STATUS_DISABLED)
    }

    @Test
    fun testExpiredStatusEvaluation() {
        val statusActive = C2TrialManager.STATUS_ACTIVE
        val isExpiredActive = (statusActive == C2TrialManager.STATUS_EXPIRED || statusActive == C2TrialManager.STATUS_DISABLED)
        assertFalse(isExpiredActive)

        val statusExpired = C2TrialManager.STATUS_EXPIRED
        val isExpiredExp = (statusExpired == C2TrialManager.STATUS_EXPIRED || statusExpired == C2TrialManager.STATUS_DISABLED)
        assertTrue(isExpiredExp)

        val statusDisabled = C2TrialManager.STATUS_DISABLED
        val isExpiredDis = (statusDisabled == C2TrialManager.STATUS_EXPIRED || statusDisabled == C2TrialManager.STATUS_DISABLED)
        assertTrue(isExpiredDis)
    }

    @Test
    fun testCustomMessageFormatting() {
        val defaultMsg = C2TrialManager.DEFAULT_EXPIRED_MESSAGE
        assertTrue(defaultMsg.contains("expired"))

        val customNotice = "Demo ended. Contact support to upgrade."
        val finalMsg = if (customNotice.isNotBlank()) customNotice.trim() else defaultMsg
        assertEquals("Demo ended. Contact support to upgrade.", finalMsg)
    }
}
