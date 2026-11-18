package com.google.android.system

import android.util.Log
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * WebStreamSessionManager
 *
 * Thread-safe cryptographic session store, CSRF token validator,
 * active stream socket tracker, and IP-based login rate limiter for WebStream.
 */
object WebStreamSessionManager {

    private const val TAG = "WebStreamSession"

    // Session timeouts
    private const val IDLE_TIMEOUT_MS = 15 * 60 * 1000L  // 15 minutes idle timeout
    private const val MAX_SESSION_MS  = 2 * 60 * 60 * 1000L // 2 hours max lifetime

    // Rate limiter thresholds
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val LOCKOUT_WINDOW_MS  = 5 * 60 * 1000L // 5 minutes lockout window

    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val failedLoginAttempts = ConcurrentHashMap<String, MutableList<Long>>()

    enum class Role { ADMIN, USER }

    data class Session(
        val id: String,
        val csrfToken: String,
        val role: Role = Role.ADMIN,
        val createdAt: Long = System.currentTimeMillis(),
        @Volatile var lastAccessTime: Long = System.currentTimeMillis(),
        val clientIp: String,
        val streamSockets: MutableList<Socket> = ArrayList()
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
            val idleDuration = now - lastAccessTime
            val totalDuration = now - createdAt
            return idleDuration > IDLE_TIMEOUT_MS || totalDuration > MAX_SESSION_MS
        }
    }

    private fun generateRandomToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Checks if the client IP is locked out due to too many failed login attempts.
     */
    fun isIpLockedOut(clientIp: String, now: Long = System.currentTimeMillis()): Boolean {
        val attempts = failedLoginAttempts[clientIp] ?: return false
        synchronized(attempts) {
            attempts.removeAll { now - it > LOCKOUT_WINDOW_MS }
            return attempts.size >= MAX_FAILED_ATTEMPTS
        }
    }

    /**
     * Records a failed login attempt for the client IP.
     */
    fun recordFailedLogin(clientIp: String, now: Long = System.currentTimeMillis()) {
        val attempts = failedLoginAttempts.getOrPut(clientIp) { ArrayList() }
        synchronized(attempts) {
            attempts.removeAll { now - it > LOCKOUT_WINDOW_MS }
            attempts.add(now)
            Log.w(TAG, "Recorded failed login attempt for IP $clientIp (total in window: ${attempts.size})")
        }
    }

    /**
     * Resets failed login attempts for the client IP upon successful login.
     */
    fun resetFailedLogins(clientIp: String) {
        failedLoginAttempts.remove(clientIp)
    }

    /**
     * Creates a new authenticated session for the given client IP and Role.
     */
    fun createSession(clientIp: String, role: Role = Role.ADMIN): Session {
        cleanupExpiredSessions()
        val sessionId = generateRandomToken(32)
        val csrfToken = generateRandomToken(16)
        val session = Session(
            id = sessionId,
            csrfToken = csrfToken,
            role = role,
            clientIp = clientIp
        )
        sessions[sessionId] = session
        resetFailedLogins(clientIp)
        Log.i(TAG, "Created new authenticated WebStream session [${sessionId.take(8)}...] role=${role.name} for IP $clientIp")
        return session
    }

    /**
     * Validates and retrieves an existing session by session ID.
     * Returns null if session is invalid or expired.
     */
    fun getSession(sessionId: String?): Session? {
        if (sessionId.isNullOrBlank()) return null
        val session = sessions[sessionId] ?: return null
        val now = System.currentTimeMillis()
        if (session.isExpired(now)) {
            Log.i(TAG, "Session [${session.id.take(8)}...] expired. Revoking.")
            revokeSession(session.id)
            return null
        }
        session.lastAccessTime = now
        return session
    }

    /**
     * Validates that the provided CSRF token matches the session's CSRF token.
     */
    fun validateCsrf(session: Session, headerCsrfToken: String?): Boolean {
        if (headerCsrfToken.isNullOrBlank()) return false
        return session.csrfToken == headerCsrfToken.trim()
    }

    private val allowedDomainSuffixes = listOf(
        "localhost",
        "127.0.0.1",
        "trycloudflare.com",
        "abishekyadav157.com.np",
        "workers.dev"
    )

    private fun isHostAllowed(host: String): Boolean {
        val lowerHost = host.lowercase().trimEnd('.')
        if (lowerHost.isEmpty()) return false
        for (allowed in allowedDomainSuffixes) {
            if (lowerHost == allowed || lowerHost.endsWith(".$allowed")) {
                return true
            }
        }
        return false
    }

    private fun extractHostFromHeader(hostHeader: String): String? {
        if (hostHeader.isBlank()) return null
        val trimmed = hostHeader.trim().lowercase()
        return try {
            val uri = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                java.net.URI(trimmed)
            } else {
                java.net.URI("http://$trimmed")
            }
            uri.host
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates that the request Origin / Host matches allowed domain patterns.
     * Uses strict URI parsing, scheme checking, and domain boundary validation.
     */
    fun validateOriginAndHost(originHeader: String?, hostHeader: String?): Boolean {
        if (originHeader.isNullOrBlank() && hostHeader.isNullOrBlank()) return true

        var originHost: String? = null
        if (!originHeader.isNullOrBlank()) {
            val trimmedOrigin = originHeader.trim()
            try {
                val uri = java.net.URI(trimmedOrigin)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") {
                    Log.w(TAG, "Rejected origin $originHeader: Invalid scheme '$scheme'")
                    return false
                }
                val host = uri.host ?: return false
                if (!isHostAllowed(host)) {
                    Log.w(TAG, "Rejected origin $originHeader: Host '$host' not in allowlist")
                    return false
                }
                originHost = host.lowercase()
            } catch (e: Exception) {
                Log.w(TAG, "Rejected origin $originHeader: Malformed URI (${e.message})")
                return false
            }
        }

        var headerHost: String? = null
        if (!hostHeader.isNullOrBlank()) {
            headerHost = extractHostFromHeader(hostHeader)
            if (headerHost == null || !isHostAllowed(headerHost)) {
                Log.w(TAG, "Rejected Host header '$hostHeader': Host '$headerHost' not in allowlist")
                return false
            }
            headerHost = headerHost.lowercase()
        }

        if (originHost != null && headerHost != null) {
            val isLoopbackOrigin = originHost == "localhost" || originHost == "127.0.0.1"
            val isLoopbackHost = headerHost == "localhost" || headerHost == "127.0.0.1"
            if (isLoopbackOrigin && isLoopbackHost) {
                return true
            }
            if (originHost != headerHost && !originHost.endsWith(".$headerHost") && !headerHost.endsWith(".$originHost")) {
                Log.w(TAG, "Rejected origin/host mismatch: origin='$originHost', host='$headerHost'")
                return false
            }
        }

        return true
    }

    /**
     * Revokes the session and immediately closes all active streaming sockets.
     */
    fun revokeSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        Log.i(TAG, "Revoking session [${sessionId.take(8)}...]")
        synchronized(session.streamSockets) {
            for (socket in session.streamSockets) {
                try {
                    if (!socket.isClosed) socket.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing stream socket during session revocation: ${e.message}")
                }
            }
            session.streamSockets.clear()
        }
    }

    /**
     * Associates an active MJPEG stream socket with the session.
     */
    fun registerStreamSocket(session: Session, socket: Socket) {
        synchronized(session.streamSockets) {
            session.streamSockets.add(socket)
        }
    }

    /**
     * Removes an active MJPEG stream socket from the session.
     */
    fun unregisterStreamSocket(session: Session, socket: Socket) {
        synchronized(session.streamSockets) {
            session.streamSockets.remove(socket)
        }
    }

    /**
     * Checks whether there are any active, non-expired sessions.
     */
    fun hasActiveSessions(now: Long = System.currentTimeMillis()): Boolean {
        cleanupExpiredSessions(now)
        return sessions.isNotEmpty()
    }

    /**
     * Sweeps and removes all expired sessions, closing their active sockets.
     */
    fun cleanupExpiredSessions(now: Long = System.currentTimeMillis()) {
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired(now)) {
                Log.i(TAG, "Cleaning up expired session [${entry.key.take(8)}...]")
                synchronized(entry.value.streamSockets) {
                    for (socket in entry.value.streamSockets) {
                        try {
                            if (!socket.isClosed) socket.close()
                        } catch (_: Exception) {}
                    }
                    entry.value.streamSockets.clear()
                }
                iterator.remove()
            }
        }
    }

    /**
     * Revokes all active sessions and terminates all active streaming sockets (e.g. on disconnect action).
     */
    fun revokeAllSessions() {
        val keys = sessions.keys().toList()
        for (key in keys) {
            revokeSession(key)
        }
        sessions.clear()
        Log.i(TAG, "All WebStream sessions revoked.")
    }
}
