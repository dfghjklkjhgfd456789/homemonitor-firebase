package com.homemonitor.superadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SuperAdminPortalScreen()
                }
            }
        }
    }
}

/**
 * SuperAdminPortalScreen — Exact replica of web dashboard interface.
 * Matches web-dashboard/index.html layout, colors, components, sidebar structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminPortalScreen() {
    var selectedDeviceId by remember { mutableStateOf<String?>("DEV_816675") }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Fleet devices — real data from Firebase RTDB in production
    val fleetDevices = remember {
        listOf(
            FleetDeviceItem(
                deviceId = "DEV_816675",
                parentUid = "PARENT_01",
                parentEmail = "parent1@homemonitor.com",
                modelName = "samsung SM-A045F",
                androidVersion = "Android 14",
                isOnline = true,
                batteryLevel = 0,
                isCharging = false,
                lastSeen = System.currentTimeMillis(),
                locationStr = "Awaiting sync...",
                licenseStatus = "ACTIVE"
            ),
            FleetDeviceItem(
                deviceId = "DEV_GALAXY_S24",
                parentUid = "PARENT_02",
                parentEmail = "parent2@homemonitor.com",
                modelName = "Emma's Galaxy Tab",
                androidVersion = "Android 14",
                isOnline = true,
                batteryLevel = 14,
                isCharging = true,
                lastSeen = System.currentTimeMillis(),
                locationStr = "Home Safe Zone",
                licenseStatus = "ACTIVE"
            ),
            FleetDeviceItem(
                deviceId = "DEV_PIXEL_7A",
                parentUid = "PARENT_03",
                parentEmail = "parent3@homemonitor.com",
                modelName = "Lucas' Phone",
                androidVersion = "Android 14",
                isOnline = false,
                batteryLevel = 92,
                isCharging = false,
                lastSeen = System.currentTimeMillis(),
                locationStr = "School Zone",
                licenseStatus = "SUSPENDED"
            )
        )
    }

    val activeDevice = fleetDevices.find { it.deviceId == selectedDeviceId }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = Color(0xFF7CB3FF),
                secondary = Color(0xFF599BFF),
                background = Color(0xFF121417),
                surface = Color(0xFF1A1E23),
                surfaceVariant = Color(0xFF22272E),
                onPrimary = Color(0xFF121417),
                onSecondary = Color(0xFFF3F4F6),
                onBackground = Color(0xFFF3F4F6),
                onSurface = Color(0xFFF3F4F6),
                onSurfaceVariant = Color(0xFFA7AFBA),
                error = Color(0xFFF0787E)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF2468C8),
                secondary = Color(0xFF1D55A5),
                background = Color(0xFFF6F6F3),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF1F2F4),
                onPrimary = Color(0xFFF6F6F3),
                onSecondary = Color(0xFF1D1D1F),
                onBackground = Color(0xFF1D1D1F),
                onSurface = Color(0xFF1D1D1F),
                onSurfaceVariant = Color(0xFF646B75),
                error = Color(0xFFC43D45)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP HEADER BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("HomeMonitor Portal", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Role: Standard Parent (admin)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEAF5EE),
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF27834D), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF27834D))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = true, onCheckedChange = {})
                        Text("Auto-Save", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2468C8)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // DEVICE HEADER
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Text("Monitored Device: ${activeDevice?.modelName ?: "samsung SM-A045F"}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Device ID: ${activeDevice?.deviceId ?: "DEV_816675"} | Model: ${activeDevice?.modelName ?: "samsung SM-A045F"} | OS: ${activeDevice?.androidVersion ?: "Android 14"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // MAIN CONTENT + LEFT SIDEBAR
            Row(
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                // LEFT SIDEBAR
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("MONITORED FAMILY DEVICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(selectedDeviceId ?: "Select Device", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2468C8)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SIDEBAR CATEGORIES (matching exact web dashboard index.html structure)
                    SidebarCategory("SOCIAL CHAT FEEDS (12 APPS)") {
                        SidebarItem("WhatsApp", Icons.Default.Chat)
                        SidebarItem("Facebook Messenger", Icons.Default.Message)
                        SidebarItem("Instagram Direct", Icons.Default.Image)
                        SidebarItem("Snapchat", Icons.Default.CameraAlt)
                        SidebarItem("Tinder", Icons.Default.Favorite)
                        SidebarItem("TikTok DMs", Icons.Default.Videocam)
                        SidebarItem("Telegram", Icons.Default.Message)
                        SidebarItem("Viber Messenger", Icons.Default.Message)
                        SidebarItem("Discord DMs", Icons.Default.Gamepad)
                        SidebarItem("LINE Chat", Icons.Default.Message)
                        SidebarItem("Kik Messenger", Icons.Default.Message)
                        SidebarItem("iMessage", Icons.Default.Message)
                    }

                    SidebarCategory("AI CHATBOT STREAMS") {
                        SidebarItem("ChatGPT", Icons.Default.SmartToy)
                        SidebarItem("Google Gemini", Icons.Default.SmartToy)
                        SidebarItem("Claude AI", Icons.Default.SmartToy)
                        SidebarItem("Microsoft Copilot", Icons.Default.SmartToy)
                    }

                    SidebarCategory("STORAGE & DEVICE FILES") {
                        SidebarItem("Device File Storage", Icons.Default.Storage)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFCEEEF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFC43D45), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC43D45))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // MAIN CONTENT AREA
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    // Family Overview Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF0F7FF),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF2468C8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Family Overview", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Real-time family safety dashboard showing connected devices, last updated activity, and safety alerts.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Stat cards row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard("TOTAL FAMILY DEVICES", "--", "Enrolled Child Devices", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatCard("CONNECTED DEVICES", "--", "Active Update (<45s)", Color(0xFF27834D), Modifier.weight(1f))
                        StatCard("NOT CONNECTED", "--", "Standby or Offline", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                        StatCard("SAFETY ALERTS", "0", "Warnings", Color(0xFFC43D45), Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloud Archive Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF27834D), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Google Drive Cloud Archive & Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFCEEEF),
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 6.dp)
                                ) {
                                    Text("Drive Disconnected", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC43D45))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Automatic background sync transfers historical family logs directly into your parent Google Drive account.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoChip("CONNECTED ACCOUNT", "Not Connected", Modifier.weight(1f))
                                InfoChip("LAST SYNC", "Never", Modifier.weight(1f))
                                InfoChip("PENDING RECORDS", "0 Records", Modifier.weight(1f))
                                InfoChip("ARCHIVE INTERVAL", "Every 6 Hours", Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3 Detail cards row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailCard(
                            title = "Parent Subscription",
                            tag = "Active",
                            tagColor = Color(0xFF27834D),
                            lines = listOf("Plan: Standard Family Plan", "Status: Unlimited Device Sync"),
                            modifier = Modifier.weight(1f)
                        )
                        DetailCard(
                            title = "Active Child Device Status",
                            tag = "Status",
                            tagColor = Color(0xFF2468C8),
                            lines = listOf(
                                "Device: ${activeDevice?.modelName ?: "Awaiting..."}",
                                "Battery: ${if (activeDevice != null && activeDevice.batteryLevel >= 0) "${activeDevice.batteryLevel}%" else "Awaiting pulse..."}",
                                "Updated: Just now"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        DetailCard(
                            title = "Device Management",
                            tag = "Actions",
                            tagColor = Color(0xFFC43D45),
                            lines = listOf("Check Status", "Device Specs", "Rear Photo", "Screenshot", "Rename", "Remove Device"),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Location Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Latest Location Overview", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Location data loading...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// COMPONENT HELPERS
// ============================================================

@Composable
private fun SidebarCategory(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SidebarItem(label: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(subtitle, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    tag: String,
    tagColor: Color,
    lines: List<String>,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (tag.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tagColor.copy(alpha = 0.12f)
                    ) {
                        Text(tag, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = tagColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            lines.forEach { line ->
                Text(line, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
