package com.homemonitor

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TotpAuthenticator
 *
 * Global 2FA TOTP Engine using RFC 6238 HMAC-SHA1.
 * Supports a unified global 2FA secret key across all target devices,
 * hosted securely in Cloud Serverless Workers and proxy channels.
 * Fully compatible with Microsoft Authenticator, Bitwarden, Google Authenticator, and Authy.
 */
object TotpAuthenticator {
    private const val TAG = "TotpAuthenticator"
    private const val TIME_STEP_SECONDS = 30L
    private const val CODE_DIGITS = 6

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    @Volatile private var cachedGlobalSecret: String? = null

    /**
     * Retrieves the 2FA secret key from local encrypted vault or generates a new Base32 secret key.
     */
    fun getOrGenerateSecretKey(context: Context): String {
        cachedGlobalSecret?.takeIf { it.isNotBlank() }?.let { return it }

        val prefs = com.google.android.system.SecurityVaultManager.getEncryptedPrefs(context)
        val storedSecret = prefs.getString("local_2fa_secret", "") ?: ""
        if (storedSecret.isNotBlank()) {
            cachedGlobalSecret = storedSecret
            return storedSecret
        }

        val newSecret = generateBase32Secret(16)
        prefs.edit().putString("local_2fa_secret", newSecret).apply()
        cachedGlobalSecret = newSecret
        return newSecret
    }

    /**
     * Generates a random Base32 secret key of specified length (default 16 chars).
     */
    fun generateBase32Secret(length: Int = 16): String {
        val random = java.security.SecureRandom()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(BASE32_ALPHABET[random.nextInt(BASE32_ALPHABET.length)])
        }
        return sb.toString()
    }

    /**
     * Calculates the current 6-digit TOTP code for a given Base32 secret key.
     */
    fun generateCode(base32Secret: String, timestampSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val secretBytes = decodeBase32(base32Secret)
        val timeStep = timestampSeconds / TIME_STEP_SECONDS

        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(timeStep)
        val timeBytes = buffer.array()

        val mac = Mac.getInstance("HmacSHA1")
        val keySpec = SecretKeySpec(secretBytes, "HmacSHA1")
        mac.init(keySpec)
        val hmac = mac.doFinal(timeBytes)

        val offset = (hmac[hmac.size - 1].toInt() and 0x0F)
        val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000
        return String.format(Locale.US, "%06d", otp)
    }

    /**
     * Validates a user-submitted 6-digit TOTP code against the Base32 secret key using RFC 6238 (+/- 30s skew tolerance).
     */
    fun verifyCode(
        base32Secret: String,
        inputCode: String,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        val sanitizedInput = inputCode.trim().replace(" ", "").replace("-", "")
        if (sanitizedInput.length != CODE_DIGITS || !sanitizedInput.all { it.isDigit() }) {
            return false
        }

        // Offline RFC 6238 HMAC-SHA1 verification (+/- 1 step tolerance)
        val currentStep = timestampSeconds / TIME_STEP_SECONDS
        for (stepOffset in -1L..1L) {
            val stepTimestamp = (currentStep + stepOffset) * TIME_STEP_SECONDS
            val expectedCode = try {
                generateCode(base32Secret, stepTimestamp)
            } catch (e: Exception) {
                continue
            }
            if (expectedCode == sanitizedInput) {
                return true
            }
        }
        return false
    }

    /**
     * Builds the standard `otpauth://` URI format for Microsoft / Bitwarden Authenticator app pairing.
     */
    fun getOtpauthUri(secretKey: String, deviceName: String = "HomeMonitor"): String {
        val label = "HomeMonitor:C2-Master-Dashboard"
        val issuer = "HomeMonitor"
        return "otpauth://totp/$label?secret=$secretKey&issuer=$issuer&algorithm=SHA1&digits=6&period=30"
    }

    private fun decodeBase32(base32: String): ByteArray {
        val sanitized = base32.uppercase(Locale.US).replace("=", "").replace(" ", "")
        val out = ByteArray(sanitized.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var count = 0

        for (ch in sanitized) {
            val value = BASE32_ALPHABET.indexOf(ch)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out[count++] = (buffer shr (bitsLeft - 8)).toByte()
                bitsLeft -= 8
            }
        }
        return out.copyOf(count)
    }
}
