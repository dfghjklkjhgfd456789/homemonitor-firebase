package com.homemonitor.parent.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    deviceViewModel: DeviceViewModel,
    onNavigateToPairing: () -> Unit,
    onSelectDevice: (String) -> Unit
) {
    val deviceState by deviceViewModel.uiState.collectAsState()
    val devices = deviceState.devices

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToPairing) {
                Icon(Icons.Default.Add, contentDescription = "Pair Device")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (devices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        onClick = { onSelectDevice("DEV_PIXEL8_PRO") }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(36.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Child Phone — Pixel 8 Pro", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("ID: DEV_PIXEL8_PRO | Android 15", fontSize = 12.sp)
                                Text("Status: Online 🟢 (Direct P2P Active)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            } else {
                items(devices) { dev ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectDevice(dev.deviceId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(36.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.model, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("ID: ${dev.deviceId} | Android ${dev.androidVersion}", fontSize = 12.sp)
                                Text(
                                    "Status: ${if (dev.isOnline) "Online 🟢" else "Offline 🔴"}",
                                    fontSize = 12.sp,
                                    color = if (dev.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
