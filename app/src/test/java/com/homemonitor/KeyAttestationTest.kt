package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyAttestationTest {

    @Test
    fun testKeyAttestationReportDataClass() {
        val report = KeyAttestationManager.KeyAttestationReport(
            isHardwareAttestationSupported = true,
            securityLevel = "Hardware TEE / StrongBox 🟢",
            verifiedBootState = "VERIFIED (Cryptographically Signed by Google CA) 🟢",
            isBootloaderLocked = true,
            attestationChallenge = "TestChallenge123",
            certificateChainLength = 3,
            summaryDetails = "Attestation Test Report Summary"
        )

        assertTrue(report.isHardwareAttestationSupported)
        assertEquals("Hardware TEE / StrongBox 🟢", report.securityLevel)
        assertEquals("VERIFIED (Cryptographically Signed by Google CA) 🟢", report.verifiedBootState)
        assertTrue(report.isBootloaderLocked)
        assertEquals(3, report.certificateChainLength)
        assertEquals("TestChallenge123", report.attestationChallenge)
        assertNotNull(report.summaryDetails)
    }
}
