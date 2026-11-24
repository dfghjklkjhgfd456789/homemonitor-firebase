package com.homemonitor.parent.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.data.repository.ParentUserSession
import com.homemonitor.parent.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMoreScreen(
    activeSession: ParentUserSession?,
    onNavigateTo: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = activeSession?.displayName ?: "Parent Workspace Owner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = activeSession?.email ?: "parent@homemonitor.com",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (activeSession?.isSuperAdmin == true) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color(0xFFFEF08A)
                        ) {
                            Text(
                                text = "👑 MASTER SUPER ADMIN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFF854D0E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Family & Device Management
        Text(
            text = "FAMILY & DEVICES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                MoreMenuItem(
                    title = "Family Devices List",
                    subtitle = "View and manage all registered child devices",
                    icon = Icons.Default.Devices,
                    onClick = { onNavigateTo(Screen.Devices.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Pair / Enroll New Device",
                    subtitle = "Scan QR code or generate Android Enterprise DPC QR",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = { onNavigateTo(Screen.DevicePairing.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Safety & MDM Controls",
                    subtitle = "Remote lock, app blocking, and kiosk mode policies",
                    icon = Icons.Default.Security,
                    onClick = { onNavigateTo(Screen.Safety.route) }
                )
            }
        }

        // Section 2: Data & Historical Archives
        Text(
            text = "DATA & ARCHIVES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                MoreMenuItem(
                    title = "Google Drive Archive",
                    subtitle = "Verified historical telemetry database backups",
                    icon = Icons.Default.CloudSync,
                    onClick = { onNavigateTo(Screen.Settings.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Consented Support Sessions",
                    subtitle = "Interactive support session requests with child approval",
                    icon = Icons.Default.SupportAgent,
                    onClick = { onNavigateTo(Screen.Settings.route) }
                )
            }
        }

        // Section 3: Account & System Settings
        Text(
            text = "ACCOUNT & PRIVACY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                MoreMenuItem(
                    title = "Account & Security Settings",
                    subtitle = "Parent credentials, MFA setup, and active sessions",
                    icon = Icons.Default.Settings,
                    onClick = { onNavigateTo(Screen.Settings.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Privacy Policy & Disclosures",
                    subtitle = "Disclosed monitoring transparency guidelines",
                    icon = Icons.Default.Policy,
                    onClick = { onNavigateTo(Screen.Settings.route) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out of Workspace", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MoreMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ParentMorePreview() {
    com.homemonitor.parent.ui.theme.HomeMonitorTheme {
        ParentMoreScreen(
            activeSession = com.homemonitor.parent.data.repository.ParentUserSession(
                uid = "PARENT_01",
                email = "alex.parent@homemonitor.com",
                displayName = "Alex Miller",
                isEmailVerified = true,
                isSuperAdmin = true
            ),
            onNavigateTo = {},
            onSignOut = {}
        )
    }
}
