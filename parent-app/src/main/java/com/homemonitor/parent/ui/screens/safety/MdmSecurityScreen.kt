package com.homemonitor.parent.ui.screens.safety

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.viewmodel.DeviceViewModel

@Composable
fun MdmSecurityScreen(deviceViewModel: DeviceViewModel) {
    var lockSent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Administrator & Policy Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Device Admin Active: Yes 🟢", fontSize = 14.sp)
                Text("Accessibility Service: Active 🟢", fontSize = 14.sp)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Remote Lock & Toast Message", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        deviceViewModel.lockActiveDevice()
                        lockSent = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lockSent) "Lock Command Sent 🟢" else "Send Remote Lock Signal")
                }
            }
        }
    }
}
