package com.homemonitor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceCommandServiceTest {

    @Test
    fun testRoleAuthorizationForSuperAdminMethods() {
        // Parent role trying SuperAdmin method must fail
        val parentRes = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "admin.getGlobalFleet",
            params = JSONObject(),
            role = UserRole.PARENT
        )
        assertFalse(parentRes.success)
        assertNotNull(parentRes.error)
        assertTrue(parentRes.error!!.contains("Authorization Failed"))

        // Shell execution blocked for parent role
        val parentShellRes = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "shell.exec",
            params = JSONObject().apply { put("command", "id") },
            role = UserRole.PARENT
        )
        assertFalse(parentShellRes.success)
        assertTrue(parentShellRes.error!!.contains("Authorization Failed"))
    }

    @Test
    fun testSecurityContainmentPolicyBlocksCovertActions() {
        val covertActions = listOf("camera", "frontcam", "screenshot", "audio", "sendsms", "stealth")
        for (action in covertActions) {
            val res = DeviceCommandService.executeMethod(
                context = null,
                methodStr = action,
                params = JSONObject(),
                role = UserRole.SUPER_ADMIN
            )
            assertFalse("Action '$action' must be blocked per security policy", res.success)
            assertNotNull(res.error)
            assertTrue(res.error!!.contains("Security Containment Policy"))
        }
    }

    @Test
    fun testAvailableCommandsDirectory() {
        val parentCmds = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "system.getAvailableCommands",
            params = JSONObject(),
            role = UserRole.PARENT
        )
        assertTrue(parentCmds.success)
        val parentMethodsArr = parentCmds.data.optJSONArray("methods")
        assertNotNull(parentMethodsArr)

        val parentList = mutableListOf<String>()
        for (i in 0 until parentMethodsArr!!.length()) parentList.add(parentMethodsArr.getString(i))

        assertTrue(parentList.contains("device.getSummary"))
        assertTrue(parentList.contains("device.getInfo"))
        assertTrue(parentList.contains("location.getCurrent"))
        assertFalse(parentList.contains("admin.getGlobalFleet")) // Excluded for parent

        val adminCmds = DeviceCommandService.executeMethod(
            context = null,
            methodStr = "system.getAvailableCommands",
            params = JSONObject(),
            role = UserRole.SUPER_ADMIN
        )
        assertTrue(adminCmds.success)
        val adminMethodsArr = adminCmds.data.optJSONArray("methods")
        assertNotNull(adminMethodsArr)

        val adminList = mutableListOf<String>()
        for (i in 0 until adminMethodsArr!!.length()) adminList.add(adminMethodsArr.getString(i))

        assertTrue(adminList.contains("admin.getGlobalFleet")) // Included for SuperAdmin
    }
}
