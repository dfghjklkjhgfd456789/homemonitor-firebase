package com.google.android.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class DashboardUiTest {

    @Test
    fun testGatekeeperInitialUnlockedState() {
        assertFalse(C2Gatekeeper.isUnlockedByAdmin)
    }

    @Test
    fun testMasterPasscodeConstant() {
        val passcode = Placeholder.C2_MASTER_PASSCODE
        assertNotNull(passcode)
        assertEquals("", passcode)
    }
}
