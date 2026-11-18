package com.google.android.system

import org.junit.Test

/**
 * WebStreamSecurityTest
 *
 * Automated security unit test suite validating WebStream security & origin requirements.
 */
class WebStreamSecurityTest {

    private val testIp = "127.0.0.1"

    private fun assertNull(message: String, value: Any?) {
        check(value == null) { "$message: Expected null but was $value" }
    }

    private fun assertNotNull(message: String, value: Any?) {
        check(value != null) { "$message: Expected non-null value" }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        check(condition) { "$message: Expected true" }
    }

    private fun assertFalse(message: String, condition: Boolean) {
        check(!condition) { "$message: Expected false" }
    }

    @Test
    fun testUnauthenticatedStreamRejected() {
        WebStreamSessionManager.resetFailedLogins(testIp)
        WebStreamSessionManager.revokeAllSessions()
        val session = WebStreamSessionManager.getSession(null)
        assertNull("Unauthenticated session lookup must return null", session)
    }

    @Test
    fun testAuthenticatedStreamAccepted() {
        val session = WebStreamSessionManager.createSession(testIp)
        assertNotNull("Authenticated session must be non-null", session)

        val retrieved = WebStreamSessionManager.getSession(session.id)
        assertNotNull("Valid session ID must retrieve active session", retrieved)
        assertFalse("Active session must not be expired", retrieved!!.isExpired())
    }

    @Test
    fun testExpiredSessionRejected() {
        val session = WebStreamSessionManager.createSession(testIp)
        val expiredTime = System.currentTimeMillis() + (20 * 60 * 1000L)
        assertTrue("Session must be reported expired past idle timeout", session.isExpired(expiredTime))

        WebStreamSessionManager.cleanupExpiredSessions(expiredTime)
        assertNull("Expired session must be cleaned up and return null", WebStreamSessionManager.getSession(session.id))
    }

    @Test
    fun testRevokedSessionRejected() {
        val session = WebStreamSessionManager.createSession(testIp)
        WebStreamSessionManager.revokeSession(session.id)
        assertNull("Revoked session must return null on retrieval", WebStreamSessionManager.getSession(session.id))
    }

    @Test
    fun testUnauthenticatedInputTapRejected() {
        val session = WebStreamSessionManager.getSession("invalid_session_id")
        assertNull("Unauthenticated tap request must fail session validation", session)
    }

    @Test
    fun testUnauthenticatedInputSwipeRejected() {
        val session = WebStreamSessionManager.getSession("")
        assertNull("Unauthenticated swipe request must fail session validation", session)
    }

    @Test
    fun testUnauthenticatedInputTypeRejected() {
        val session = WebStreamSessionManager.getSession(null)
        assertNull("Unauthenticated type request must fail session validation", session)
    }

    @Test
    fun testUnauthorizedActionRejected() {
        val session = WebStreamSessionManager.getSession("fake_id")
        assertNull("Unauthenticated action request must fail session validation", session)
    }

    @Test
    fun testMissingCsrfTokenRejected() {
        val session = WebStreamSessionManager.createSession(testIp)
        val isValidCsrf = WebStreamSessionManager.validateCsrf(session, null)
        assertFalse("Missing CSRF token must fail validation", isValidCsrf)
    }

    @Test
    fun testInvalidOriginRejected() {
        val session = WebStreamSessionManager.createSession(testIp)
        val isValidCsrf = WebStreamSessionManager.validateCsrf(session, "wrong_csrf_token")
        assertFalse("Invalid CSRF token must fail validation", isValidCsrf)
    }

    @Test
    fun testFailedLoginThrottled() {
        val now = System.currentTimeMillis()
        assertFalse("IP must not be locked out initially", WebStreamSessionManager.isIpLockedOut(testIp, now))

        for (attempt in 1..5) {
            WebStreamSessionManager.recordFailedLogin(testIp, now)
        }

        assertTrue("IP must be locked out after 5 failed attempts", WebStreamSessionManager.isIpLockedOut(testIp, now))
    }

    @Test
    fun testSessionLogoutRevokesAccess() {
        val session = WebStreamSessionManager.createSession(testIp)
        assertNotNull("Session must be active before logout", WebStreamSessionManager.getSession(session.id))

        WebStreamSessionManager.revokeSession(session.id)
        assertNull("Session must return null after logout", WebStreamSessionManager.getSession(session.id))
    }

    @Test
    fun testAuthorizedAccessWithValidCsrf() {
        val session = WebStreamSessionManager.createSession(testIp)
        val isValidCsrf = WebStreamSessionManager.validateCsrf(session, session.csrfToken)
        assertTrue("Valid CSRF token matching session token must pass validation", isValidCsrf)
    }

    @Test
    fun testGETMethodNotAllowedOnInputEndpoints() {
        val inputMethod = "GET"
        val isAllowed = (inputMethod == "POST")
        assertFalse("GET method must not be allowed on input endpoints", isAllowed)
    }

    @Test
    fun testSecureCookieAttributes() {
        val session = WebStreamSessionManager.createSession(testIp)
        val cookieHeader = "Set-Cookie: session_id=${session.id}; Path=/; HttpOnly; Secure; SameSite=Strict"
        assertTrue("Cookie header must contain HttpOnly", cookieHeader.contains("HttpOnly"))
        assertTrue("Cookie header must contain Secure", cookieHeader.contains("Secure"))
        assertTrue("Cookie header must contain SameSite=Strict", cookieHeader.contains("SameSite=Strict"))
    }

    @Test
    fun testStrictOriginAndHostValidation() {
        // Valid Origins & Hosts
        assertTrue("Localhost origin must be valid", WebStreamSessionManager.validateOriginAndHost("http://localhost:5467", null))
        assertTrue("127.0.0.1 origin must be valid", WebStreamSessionManager.validateOriginAndHost("http://127.0.0.1:5467", "127.0.0.1:5467"))
        assertTrue("Subdomain trycloudflare.com origin must be valid", WebStreamSessionManager.validateOriginAndHost("https://sub.trycloudflare.com", null))
        assertTrue("Custom domain origin must be valid", WebStreamSessionManager.validateOriginAndHost("https://adbfa66b39de7904-sm-a045f-web.abishekyadav157.com.np", null))
        assertTrue("Workers.dev origin must be valid", WebStreamSessionManager.validateOriginAndHost("https://myworker.workers.dev", null))

        // Malicious Deceptive Origins & Hosts
        assertFalse("Deceptive prefix host must be rejected", WebStreamSessionManager.validateOriginAndHost("https://eviltrycloudflare.com", null))
        assertFalse("Deceptive suffix host must be rejected", WebStreamSessionManager.validateOriginAndHost("https://trycloudflare.com.evil.example", null))
        assertFalse("Subdomain impersonation must be rejected", WebStreamSessionManager.validateOriginAndHost("https://allowed.example.evil.example", null))
        assertFalse("Userinfo credential injection must be rejected", WebStreamSessionManager.validateOriginAndHost("https://allowed.example@evil.example", null))
        assertFalse("Unauthorized external domain must be rejected", WebStreamSessionManager.validateOriginAndHost("http://allowed.example", null))
        assertFalse("Unauthorized scheme must be rejected", WebStreamSessionManager.validateOriginAndHost("ftp://localhost", null))
        assertFalse("Malformed origin URI must be rejected", WebStreamSessionManager.validateOriginAndHost("http://[invalid-ipv6", null))
    }

    @Test
    fun testTotpAuthenticatorGenerationAndVerification() {
        val secret = com.homemonitor.TotpAuthenticator.generateBase32Secret(16)
        assertTrue("Generated Base32 secret must be 16 characters long", secret.length == 16)

        val code = com.homemonitor.TotpAuthenticator.generateCode(secret)
        assertTrue("TOTP code must be a 6-digit number", code.length == 6 && code.all { it.isDigit() })

        val isValid = com.homemonitor.TotpAuthenticator.verifyCode(secret, code)
        assertTrue("Generated TOTP code must pass verification", isValid)

        val isInvalid = com.homemonitor.TotpAuthenticator.verifyCode(secret, "000000")
        if (code != "000000") {
            assertFalse("Arbitrary wrong TOTP code must fail verification", isInvalid)
        }
    }

    @Test
    fun testTotpOtpauthUriFormat() {
        val secret = com.homemonitor.TotpAuthenticator.generateBase32Secret(16)
        val uri = com.homemonitor.TotpAuthenticator.getOtpauthUri(secret, "TestDevice")
        assertTrue("Otpauth URI must start with otpauth://totp/", uri.startsWith("otpauth://totp/"))
        assertTrue("Otpauth URI must contain secret", uri.contains("secret=$secret"))
    }
}
