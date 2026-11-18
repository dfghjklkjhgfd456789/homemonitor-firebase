package com.google.android.system.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ApkValidator(private val context: Context) {

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val packageName: String? = null,
        val versionCode: Long = 0,
        val versionName: String? = null
    )

    fun validateApk(apkFile: File): ValidationResult {
        if (!apkFile.exists() || !apkFile.canRead()) {
            return ValidationResult(false, "APK file does not exist or cannot be read.")
        }

        // 1. Verify SHA-256 if configured (and not placeholder)
        if (InstallerConfig.EXPECTED_APK_SHA256 != "REPLACE_WITH_64_CHARACTER_SHA256") {
            val calculatedSha256 = calculateSha256(apkFile)
            if (!calculatedSha256.equals(InstallerConfig.EXPECTED_APK_SHA256, ignoreCase = true)) {
                return ValidationResult(false, "APK SHA-256 digest verification failed.")
            }
        }

        // 2. Parse package info and verify package name
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        } catch (e: Exception) {
            return ValidationResult(false, "Failed to parse APK package info: ${e.message}")
        } ?: return ValidationResult(false, "Invalid APK file format or structure.")

        // Ensure sourceDir is set for certificate extraction
        packageInfo.applicationInfo?.sourceDir = apkFile.absolutePath
        packageInfo.applicationInfo?.publicSourceDir = apkFile.absolutePath

        val packageName = packageInfo.packageName
        if (packageName != InstallerConfig.MAIN_APP_PACKAGE_NAME) {
            return ValidationResult(false, "Unexpected application package name: $packageName (expected ${InstallerConfig.MAIN_APP_PACKAGE_NAME})")
        }

        // 3. Verify signing certificate SHA-256 if configured
        if (InstallerConfig.EXPECTED_SIGNING_CERT_SHA256 != "REPLACE_WITH_MAIN_APP_SIGNING_CERTIFICATE_SHA256") {
            val certValid = verifySigningCertificate(packageInfo, InstallerConfig.EXPECTED_SIGNING_CERT_SHA256)
            if (!certValid) {
                return ValidationResult(false, "Signing certificate digest verification failed.")
            }
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return ValidationResult(
            isValid = true,
            packageName = packageName,
            versionCode = versionCode,
            versionName = packageInfo.versionName
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifySigningCertificate(packageInfo: PackageInfo, expectedCertSha256: String): Boolean {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        } ?: return false

        for (signature in signatures) {
            val digest = MessageDigest.getInstance("SHA-256")
            val certBytes = digest.digest(signature.toByteArray())
            val hexCert = certBytes.joinToString("") { "%02x".format(it) }
            if (hexCert.equals(expectedCertSha256, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
