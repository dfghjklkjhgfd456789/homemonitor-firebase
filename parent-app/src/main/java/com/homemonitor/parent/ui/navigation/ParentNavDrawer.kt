package com.homemonitor.parent.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentNavDrawerContent(
    currentRoute: String,
    userEmail: String,
    isSuperAdmin: Boolean,
    onNavigateTo: (String) -> Unit,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        // Dark Slate Header Banner (matching Web Dashboard sidebar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "HomeMonitor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = userEmail,
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (isSuperAdmin) Color(0xFFFEF08A) else Color(0xFFDBEAFE)
                ) {
                    Text(
                        text = if (isSuperAdmin) "👑 MASTER SUPER ADMIN" else "STANDARD PARENT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isSuperAdmin) Color(0xFF854D0E) else Color(0xFF1E40AF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp)
        ) {
            NavigationDrawerItem(
                label = { Text("Home Overview", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Overview.route,
                onClick = { onNavigateTo(Screen.Overview.route) },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Monitored Devices", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Devices.route,
                onClick = { onNavigateTo(Screen.Devices.route) },
                icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Location & Safe Zones", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Location.route,
                onClick = { onNavigateTo(Screen.Location.route) },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Screen Time & Apps", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Usage.route,
                onClick = { onNavigateTo(Screen.Usage.route) },
                icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Activity Feed", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Activity.route,
                onClick = { onNavigateTo(Screen.Activity.route) },
                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Safety & MDM Controls", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Safety.route,
                onClick = { onNavigateTo(Screen.Safety.route) },
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Account Settings", fontWeight = FontWeight.SemiBold) },
                selected = currentRoute == Screen.Settings.route,
                onClick = { onNavigateTo(Screen.Settings.route) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider()
            TextButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
