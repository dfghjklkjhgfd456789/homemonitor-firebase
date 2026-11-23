package com.homemonitor.parent.ui.screens.usage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.viewmodel.DeviceViewModel

@Composable
fun ScreenTimeScreen(deviceViewModel: DeviceViewModel) {
    var tiktokBlocked by remember { mutableStateOf(true) }
    var youtubeBlocked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Today\'s Screen Time Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Total Active Time: 2 hrs 45 mins", fontSize = 14.sp)
            }
        }

        Text("Installed Applications & Block Rules", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TikTok", fontWeight = FontWeight.Bold)
                        Text("com.zhiliaoapp.musically", fontSize = 12.sp)
                    }
                    Switch(
                        checked = tiktokBlocked,
                        onCheckedChange = { checked ->
                            tiktokBlocked = checked
                            val list = mutableListOf<String>()
                            if (tiktokBlocked) list.add("com.zhiliaoapp.musically")
                            if (youtubeBlocked) list.add("com.google.android.youtube")
                            deviceViewModel.setAppBlockRules(list)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("YouTube", fontWeight = FontWeight.Bold)
                        Text("com.google.android.youtube", fontSize = 12.sp)
                    }
                    Switch(
                        checked = youtubeBlocked,
                        onCheckedChange = { checked ->
                            youtubeBlocked = checked
                            val list = mutableListOf<String>()
                            if (tiktokBlocked) list.add("com.zhiliaoapp.musically")
                            if (youtubeBlocked) list.add("com.google.android.youtube")
                            deviceViewModel.setAppBlockRules(list)
                        }
                    )
                }
            }
        }
    }
}
