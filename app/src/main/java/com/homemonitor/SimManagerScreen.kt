package com.homemonitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.system.C2Gatekeeper
import com.google.android.system.SecurityVaultManager

/**
 * SimManagerScreen
 *
 * Light-Theme Google Material 3 Disguise Interface ("SIM Manager").
 * Displays genuine SIM and cellular network metrics exclusively (Operator, SIM Slot State, Network Mode, Country ISO, Roaming).
 * Includes quick 5-tap gesture on the SIM Card hero icon prompting for passcode authentication to unlock the C2 & Enrollment Center.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimManagerScreen(
    onUnlockMaster: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SecurityVaultManager.getEncryptedPrefs(context) }

    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var authPasswordInput by remember { mutableStateOf("") }
    var authErrorMsg by remember { mutableStateOf<String?>(null) }

    // Real Cellular / SIM Specs
    val telephonyManager = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }

    val operatorName = remember {
        val name = telephonyManager?.networkOperatorName
        if (!name.isNullOrBlank()) name else "SIM 1 - Primary Network"
    }

    val simStateStr = remember {
        when (telephonyManager?.simState) {
            TelephonyManager.SIM_STATE_READY -> "READY / ACTIVE"
            TelephonyManager.SIM_STATE_ABSENT -> "NO SIM CARD DETECTED"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK LOCKED"
            else -> "SIM SLOT 1 ACTIVE"
        }
    }

    val countryIso = remember {
        val iso = telephonyManager?.networkCountryIso
        if (!iso.isNullOrBlank()) iso.uppercase() else "US"
    }

    val isRoaming = remember {
        if (telephonyManager?.isNetworkRoaming == true) "YES (ROAMING ACTIVE)" else "NO (HOME NETWORK)"
    }

    val phoneTypeStr = remember {
        when (telephonyManager?.phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM / LTE / 5G"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA / EVDO"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP / VOIP"
            else -> "CELLULAR MULTI-MODE"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .safeDrawingPadding()
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Hero SIM Icon with Fast 5-Tap Gesture Listener
        Surface(
            shape = CircleShape,
            color = Color(0xFFE8F0FE),
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(96.dp)
                .border(2.dp, Color(0xFFD2E3FC), CircleShape)
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 800) {
                        tapCount++
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now

                    if (tapCount >= 5) {
                        tapCount = 0
                        showAuthDialog = true
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = "SIM Card Hero Icon",
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "SIM Manager",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F1F1F),
            fontFamily = FontFamily.Default
        )

        Text(
            text = "Cellular Network & SIM Card Status",
            fontSize = 13.sp,
            color = Color(0xFF5F6368),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ── Card 1: SIM 1 Primary Slot Details ──
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellular4Bar, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIM Slot 1 (Primary)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                    }
                    AssistChip(
                        onClick = { },
                        label = { Text("Active 🟢", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF188038)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE6F4EA))
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                LightInfoRow(label = "Network Operator", value = operatorName)
                LightInfoRow(label = "SIM Card Status", value = simStateStr)
                LightInfoRow(label = "Network Mode", value = "5G NR / LTE Auto")
                LightInfoRow(label = "Cellular Technology", value = phoneTypeStr)
                LightInfoRow(label = "Country ISO", value = countryIso)
                LightInfoRow(label = "Network Roaming", value = isRoaming)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Card 2: SIM Slot 2 Secondary Status ──
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SimCardDownload, contentDescription = null, tint = Color(0xFF5F6368), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIM Slot 2 / eSIM", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                    }
                    AssistChip(
                        onClick = { },
                        label = { Text("Standby ⚪", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF5F6368)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF1F3F4))
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                LightInfoRow(label = "eSIM Provisioning", value = "Ready for Dual SIM")
                LightInfoRow(label = "Data Switching", value = "Auto-Switch Enabled")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // "More SIM Settings" Button -> Opens Official System SIM Settings
        Button(
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Opening System Network Settings...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("More SIM Settings", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF188038), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("SIM Protection & Network Encryption Active", fontSize = 12.sp, color = Color(0xFF188038), fontWeight = FontWeight.Medium)
        }
    }

    // ── Quick 5-Tap Password Unlock Dialog ──
    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF1A73E8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔑 System Authorization", color = Color(0xFF1F1F1F), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter Master or Enrollment Passcode to open Control Center:",
                        fontSize = 13.sp,
                        color = Color(0xFF5F6368)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = authPasswordInput,
                        onValueChange = {
                            authPasswordInput = it
                            authErrorMsg = null
                        },
                        label = { Text("Enter Passcode") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!authErrorMsg.isNullOrBlank()) {
                        Text(
                            text = authErrorMsg!!,
                            color = Color(0xFFD93025),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entered = authPasswordInput.trim()
                        val enrolledUserPass = prefs.getString("user_web_password", "") ?: ""

                        if (C2Gatekeeper.verifyAdminPassword(entered) || (enrolledUserPass.isNotBlank() && entered == enrolledUserPass)) {
                            showAuthDialog = false
                            authPasswordInput = ""
                            Toast.makeText(context, "✅ Control Center Unlocked", Toast.LENGTH_SHORT).show()
                            onUnlockMaster()
                        } else {
                            authErrorMsg = "❌ Invalid Passcode. Access Denied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                ) {
                    Text("Authenticate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }) {
                    Text("Cancel", color = Color(0xFF5F6368))
                }
            }
        )
    }
}

@Composable
private fun LightInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF5F6368))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F1F1F))
    }
}
