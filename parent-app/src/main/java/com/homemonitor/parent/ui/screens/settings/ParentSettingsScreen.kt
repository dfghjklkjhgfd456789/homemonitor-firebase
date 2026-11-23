package com.homemonitor.parent.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.data.repository.ParentUserSession

@Composable
fun ParentSettingsScreen(
    activeSession: ParentUserSession?,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Parent Account Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Email: ${activeSession?.email ?: "parent@homemonitor.com"}", fontSize = 14.sp)
                Text("UID: ${activeSession?.uid ?: "usr_parent"}", fontSize = 12.sp)
                Text("Role: ${if (activeSession?.isSuperAdmin == true) "👑 Master Super Admin" else "Standard Parent"}", fontSize = 14.sp)
            }
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Sign Out")
        }
    }
}
