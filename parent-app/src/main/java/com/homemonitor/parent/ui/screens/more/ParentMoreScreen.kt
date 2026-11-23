package com.homemonitor.parent.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
            .padding(16.dp)
    ) {
        // User Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = activeSession?.displayName ?: "Parent Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = activeSession?.email ?: "parent@homemonitor.com",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    if (activeSession?.isSuperAdmin == true) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color(0xFFFEF08A)
                        ) {
                            Text(
                                text = "👑 SUPER ADMIN",
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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "MANAGEMENT & CONTROLS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                MoreMenuItem(
                    title = "Monitored Devices",
                    subtitle = "View and select registered child devices",
                    icon = Icons.Default.Devices,
                    onClick = { onNavigateTo(Screen.Devices.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Pair New Device",
                    subtitle = "Add a child device via QR code pairing",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = { onNavigateTo(Screen.DevicePairing.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Safety & MDM Controls",
                    subtitle = "Remote screen lock, camera toggle, kiosk mode",
                    icon = Icons.Default.Security,
                    onClick = { onNavigateTo(Screen.Safety.route) }
                )
                HorizontalDivider()
                MoreMenuItem(
                    title = "Account Settings",
                    subtitle = "Manage security preferences and credentials",
                    icon = Icons.Default.Settings,
                    onClick = { onNavigateTo(Screen.Settings.route) }
                )
            }
        }

        if (activeSession?.isSuperAdmin == true) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "ADMINISTRATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB45309)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
            ) {
                MoreMenuItem(
                    title = "👑 Super Admin Portal",
                    subtitle = "Global fleet management, remote shell, DuckyScript",
                    icon = Icons.Default.AdminPanelSettings,
                    iconTint = Color(0xFFB45309),
                    onClick = { onNavigateTo(Screen.SuperAdmin.route) }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out of Account", fontWeight = FontWeight.Bold)
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
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
