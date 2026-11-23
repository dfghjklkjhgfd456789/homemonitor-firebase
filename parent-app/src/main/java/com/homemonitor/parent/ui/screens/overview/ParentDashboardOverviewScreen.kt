package com.homemonitor.parent.ui.screens.overview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val modelName = specs?.optString("model", "Pixel 8 Pro") ?: "Pixel 8 Pro"
    val battery = specs?.optInt("battery", 88) ?: 88
    val isCharging = specs?.optBoolean("isCharging", true) ?: true
    val storagePct = specs?.optInt("storageUsedPct", 42) ?: 42

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Device Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Monitored Target Device", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    AssistChip(
                        onClick = {},
                        label = { Text("Direct P2P 🟢") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32)) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Device Model: $modelName", fontSize = 14.sp)
                Text("Battery: $battery% (${if (isCharging) "Charging ⚡" else "Discharging"}) | Storage: $storagePct% Used", fontSize = 14.sp)
                Text("Last Seen: Just now", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Text("Quick Family Management", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        // Grid of Feature Navigation Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onNavigateTo("location") },
                modifier = Modifier.weight(1f).height(90.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Text("Location", fontSize = 12.sp)
                }
            }
            Button(
                onClick = { onNavigateTo("usage") },
                modifier = Modifier.weight(1f).height(90.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Text("Screen Time", fontSize = 12.sp)
                }
            }
            Button(
                onClick = { onNavigateTo("activity") },
                modifier = Modifier.weight(1f).height(90.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Text("Activity Feed", fontSize = 12.sp)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onNavigateTo("safety") },
                modifier = Modifier.weight(1f).height(90.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Text("Safety & MDM", fontSize = 12.sp)
                }
            }
            Button(
                onClick = { onNavigateTo("devices") },
                modifier = Modifier.weight(1f).height(90.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Devices, contentDescription = null)
                    Text("Devices List", fontSize = 12.sp)
                }
            }
            Button(
                onClick = { onNavigateTo("settings") },
                modifier = Modifier.weight(1f).height(90.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text("Settings", fontSize = 12.sp)
                }
            }
        }

        if (isSuperAdmin) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                onClick = { onNavigateTo("superadmin") },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("👑 Master Super Admin Control Portal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Global fleet management & cross-tenant control across all devices", fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
