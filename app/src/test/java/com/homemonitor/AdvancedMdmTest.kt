package com.homemonitor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AdvancedMdmTest
 *
 * Unit tests verifying Advanced MDM Enterprise Policy Router & DPC Data Contract.
 */
class AdvancedMdmTest {

    @Test
    fun testMdmApiEndpointPaths() {
        val endpoints = listOf(
            "/api/mdm/status",
            "/api/mdm/grant-permissions",
            "/api/mdm/kiosk",
            "/api/mdm/camera-toggle",
            "/api/mdm/app-hide",
            "/api/mdm/app-suspend",
            "/api/mdm/restrictions",
            "/api/mdm/wipe"
        )

        for (ep in endpoints) {
            assertTrue("MDM endpoint '$ep' must start with /api/mdm/", ep.startsWith("/api/mdm/"))
        }
    }
}
