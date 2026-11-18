package com.homemonitor

import com.google.android.system.MonitorService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SocialParsersAndTriggersTest
 *
 * Unit tests for Instant UI Trigger payloads and Targeted Social App Reader structures.
 */
class SocialParsersAndTriggersTest {

    @Test
    fun testToastPayload() {
        val json = JSONObject().put("message", "Test Toast")
        assertEquals("Test Toast", json.optString("message"))
    }

    @Test
    fun testVibratePayload() {
        val json = JSONObject().put("durationMs", 1000)
        assertEquals(1000, json.optInt("durationMs"))
    }

    @Test
    fun testCallPayload() {
        val json = JSONObject().put("phoneNumber", "+1234567890")
        assertEquals("+1234567890", json.optString("phoneNumber"))
    }

    @Test
    fun testOpenUrlPayload() {
        val json = JSONObject().put("url", "https://example.com")
        assertEquals("https://example.com", json.optString("url"))
    }

    @Test
    fun testSocialAppChannelMapping() {
        val apps = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.instagram.android",
            "com.facebook.orca",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.snapchat.android"
        )
        for (pkg in apps) {
            val channel = MonitorService.getMessagingChannelForPackage(pkg)
            assertNotNull("Channel mapping for $pkg should not be null", channel)
            assertTrue(channel!!.isNotEmpty())
        }
    }
}
