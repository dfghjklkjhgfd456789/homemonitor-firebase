package com.homemonitor.parent.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DevicePairingQrScreen(onPairSuccess: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Scan Enrollment QR Code", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Point camera at the QR code displayed on the Child App setup screen.", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onPairSuccess("DEV_PIXEL8_PRO") },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Simulate QR Code Scan & Pair")
        }
    }
}
