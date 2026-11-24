package com.homemonitor.parent.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.ui.navigation.Screen
import com.homemonitor.parent.ui.theme.StatusError
import com.homemonitor.parent.ui.theme.StatusSuccess
import com.homemonitor.parent.ui.theme.StatusWarning
import com.homemonitor.parent.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardOverviewScreen(
    deviceViewModel: DeviceViewModel,
    onNavigateTo: (String) -> Unit,
    isSuperAdmin: Boolean = false
) {
    val deviceState by deviceViewModel.uiState.collectAsState()
    val specs = deviceState.activeDeviceSpecs

    val modelName = specs?.optString("model", "Alex's Pixel 8 Pro") ?: "Alex's Pixel 8 Pro"
    val battery = specs?.optInt("battery", 84) ?: 84
    val isCharging = specs?.optBoolean("isCharging", false) ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "The Miller Family ▼",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Family Command Center",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onNavigateTo(Screen.DevicePairing.route) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Device", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // 1. Family Device Status Card (Elevated Surface)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FAMILY DEVICE STATUS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusSuccess.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Sync: Just now",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("2 Children", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Monitored Workspace", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("2 Connected", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StatusSuccess)
                        Text("Active Heartbeat", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 2. Children & Devices List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CHILDREN & DEVICES", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextTextButton(text = "VIEW ALL", onClick = { onNavigateTo(Screen.Devices.route) })
        }

        // Child Device Card 1
        ChildDeviceCardItem(
            childName = "Alex",
            deviceModel = modelName,
            batteryPercent = battery,
            isCharging = isCharging,
            networkType = "5G Cellular",
            statusText = "Online • Updated 2m ago",
            statusColor = StatusSuccess,
            onClick = { onNavigateTo(Screen.Devices.route) }
        )

        // Child Device Card 2
        ChildDeviceCardItem(
            childName = "Emma",
            deviceModel = "Emma's Galaxy Tab",
            batteryPercent = 14,
            isCharging = false,
            networkType = "Home_WiFi_5G",
            statusText = "Low Battery Warning • Updated 5m ago",
            statusColor = StatusWarning,
            onClick = { onNavigateTo(Screen.Devices.route) }
        )

        // 3. Location Overview Preview Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateTo(Screen.Map.route) }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LOCATION OVERVIEW", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    TextTextButton(text = "OPEN MAP", onClick = { onNavigateTo(Screen.Map.route) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("MapLibre • Central Park, NY (Accuracy: 12m)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 4. Attention Needed Section
        Text("ATTENTION NEEDED", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StatusWarning.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = StatusWarning)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Low Battery Warning", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Emma's Galaxy Tab is at 14% battery", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onNavigateTo(Screen.Devices.route) }) {
                    Text("Details", fontWeight = FontWeight.Bold, color = StatusWarning)
                }
            }
        }
    }
}

@Composable
private fun TextTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChildDeviceCardItem(
    childName: String,
    deviceModel: String,
    batteryPercent: Int,
    isCharging: Boolean,
    networkType: String,
    statusText: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = childName.take(1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "$childName's $deviceModel", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (batteryPercent < 20) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (batteryPercent < 20) StatusWarning else StatusSuccess
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$batteryPercent% ${if (isCharging) "⚡" else ""} • $networkType",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ParentDashboardOverviewPreview() {
    com.homemonitor.parent.ui.theme.HomeMonitorTheme {
        ParentDashboardOverviewScreen(
            deviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onNavigateTo = {},
            isSuperAdmin = true
        )
    }
}
