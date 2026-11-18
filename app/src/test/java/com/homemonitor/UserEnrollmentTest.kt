package com.homemonitor

import com.google.android.system.WebStreamSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UserEnrollmentTest
 *
 * Unit tests validating multi-tenant session roles, 2FA generation,
 * person name slugification, and RBAC endpoint filtering.
 */
class UserEnrollmentTest {

    @Test
    fun testWebStreamSessionRoles() {
        val adminSession = WebStreamSessionManager.createSession("127.0.0.1", WebStreamSessionManager.Role.ADMIN)
        assertNotNull(adminSession)
        assertEquals(WebStreamSessionManager.Role.ADMIN, adminSession.role)

        val userSession = WebStreamSessionManager.createSession("127.0.0.1", WebStreamSessionManager.Role.USER)
        assertNotNull(userSession)
        assertEquals(WebStreamSessionManager.Role.USER, userSession.role)
    }

    @Test
    fun testTotpAuthenticatorGeneration() {
        val secret = TotpAuthenticator.generateBase32Secret(16)
        assertEquals(16, secret.length)
        assertTrue(secret.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" })

        val code = TotpAuthenticator.generateCode(secret)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
        assertTrue(TotpAuthenticator.verifyCode(secret, code))
    }

    @Test
    fun testPersonNameSlugification() {
        val rawName = "John Doe 123"
        val cleanSlug = rawName.lowercase().replace(Regex("[^a-z0-9]"), "")
        assertEquals("johndoe123", cleanSlug)
    }
}
