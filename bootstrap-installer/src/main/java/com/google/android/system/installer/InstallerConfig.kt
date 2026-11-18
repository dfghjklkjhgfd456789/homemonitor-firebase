package com.google.android.system.installer

object InstallerConfig {
    // Configurable HTTPS URL for downloading the main application APK
    const val MAIN_APP_APK_URL = "https://YOUR_DOMAIN.example/downloads/main-app.apk"

    // Expected main application package name
    const val MAIN_APP_PACKAGE_NAME = "com.google.android.system"

    // Expected SHA-256 digest of the main APK (64 hex characters)
    const val EXPECTED_APK_SHA256 = "REPLACE_WITH_64_CHARACTER_SHA256"

    // Expected SHA-256 digest of the main application signing certificate
    const val EXPECTED_SIGNING_CERT_SHA256 = "REPLACE_WITH_MAIN_APP_SIGNING_CERTIFICATE_SHA256"

    fun isConfigValid(): Boolean {
        return MAIN_APP_APK_URL.startsWith("https://") &&
                !MAIN_APP_APK_URL.contains("YOUR_DOMAIN.example") &&
                MAIN_APP_PACKAGE_NAME == "com.google.android.system"
    }
}
