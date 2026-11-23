package com.homemonitor.parent.ui.screens.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.viewmodel.DeviceViewModel

@Composable
fun ActivityFeedScreen(deviceViewModel: DeviceViewModel) {
    val deviceState by deviceViewModel.uiState.collectAsState()
    val logs = deviceState.activityLogs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (logs.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⌨️ Keystroke Activity Log", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("App: Chrome | Query: \"best android monitor app 2026\"", fontSize = 12.sp)
                    Text("Time: 10 mins ago", fontSize = 10.sp)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔔 Notification Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("App: WhatsApp | Sender: Mom | Text: \"Home soon?\"", fontSize = 12.sp)
                    Text("Time: 25 mins ago", fontSize = 10.sp)
                }
            }
        } else {
            logs.forEach { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Log Event: ${log.optString("title", "Telemetry Entry")}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(log.optString("text", "Activity record"), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
