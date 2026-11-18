package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhysicalSecurityGuardTest {

    @Test
    fun testInitialArmedState() {
        assertFalse(PhysicalSecurityGuard.isArmed)
    }

    @Test
    fun testDefaultSensitivityThreshold() {
        assertEquals(3.5f, PhysicalSecurityGuard.sensitivityThreshold, 0.01f)
    }
}
