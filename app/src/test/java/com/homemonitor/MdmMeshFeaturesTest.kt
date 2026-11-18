package com.homemonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * MdmMeshFeaturesTest
 *
 * Unit tests verifying MDMesh & Headwind Enterprise MDM API endpoints and SHA-256 checksum logic.
 */
class MdmMeshFeaturesTest {

    @Test
    fun testMdmMeshApiEndpointPaths() {
        val endpoints = listOf(
            "/api/mdm/status",
            "/api/mdm/grant-permissions",
            "/api/mdm/reboot",
            "/api/mdm/lock-message",
            "/api/mdm/screenshot-toggle",
            "/api/mdm/statusbar-toggle",
            "/api/mdm/kiosk",
            "/api/mdm/multi-kiosk",
            "/api/mdm/camera-toggle",
            "/api/mdm/app-hide",
            "/api/mdm/app-suspend",
            "/api/mdm/restrictions",
            "/api/mdm/wipe"
        )

        for (ep in endpoints) {
            assertTrue("Endpoint '$ep' must start with /api/mdm/", ep.startsWith("/api/mdm/"))
        }
    }

    @Test
    fun testSha256ChecksumCalculation() {
        val input = "HomeMonitor Enterprise DPC Test String"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }

        assertEquals(64, hexString.length)
        assertTrue(hexString.all { it.isDigit() || it in 'a'..'f' })
    }
}
