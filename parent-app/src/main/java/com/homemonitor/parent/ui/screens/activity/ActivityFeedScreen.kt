package com.homemonitor.parent.ui.screens.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homemonitor.parent.ui.theme.StatusError
import com.homemonitor.parent.ui.theme.StatusSuccess
import com.homemonitor.parent.ui.theme.StatusWarning
import com.homemonitor.parent.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFeedScreen(deviceViewModel: DeviceViewModel) {
    var selectedFilter by remember { mutableStateOf("Today") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header & Time Filters
        Text("ACTIVITY & WELLBEING ANALYTICS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "Today",
                onClick = { selectedFilter = "Today" },
                label = { Text("Today") }
            )
            FilterChip(
                selected = selectedFilter == "7 Days",
                onClick = { selectedFilter = "7 Days" },
                label = { Text("Last 7 Days") }
            )
            FilterChip(
                selected = selectedFilter == "30 Days",
                onClick = { selectedFilter = "30 Days" },
                label = { Text("30 Days") }
            )
        }

        // Total Screen Time Metric Card
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
                    Column {
                        Text("TOTAL SCREEN TIME TODAY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("3h 45m", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StatusSuccess.copy(alpha = 0.2f)
                    ) {
                        Text("12% lower than yesterday", fontSize = 11.sp, color = StatusSuccess, modifier = Modifier.padding(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hourly Usage Compose Canvas Bar Chart
                val barColor1 = Color(0xFF818CF8) // Education
                val barColor2 = Color(0xFFF472B6) // Social
                val barColor3 = Color(0xFF34D399) // AI Platforms
                val barColor4 = Color(0xFFFBBF24) // Gaming

                Text("Hourly Screen-Time Distribution (Hours)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val barWidth = width / 12f

                    val hourlyData = listOf(0.2f, 0.4f, 0.1f, 0.8f, 0.6f, 0.9f, 0.7f, 0.5f, 0.3f, 0.6f, 0.2f, 0.4f)
                    val colors = listOf(barColor1, barColor2, barColor3, barColor4)

                    hourlyData.forEachIndexed { index, ratio ->
                        val barHeight = height * ratio
                        drawRect(
                            color = colors[index % colors.size],
                            topLeft = Offset(x = index * barWidth + 6f, y = height - barHeight),
                            size = Size(width = barWidth - 12f, height = barHeight)
                        )
                    }
                }
            }
        }

        // AI Chat & Assistant Activity Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI PLATFORMS & CHAT LOGS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text("55m Today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                AiPlatformItem(
                    name = "ChatGPT (OpenAI)",
                    duration = "35 minutes • 3 sessions",
                    statusText = "Clean & Verified ✅",
                    statusColor = StatusSuccess
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AiPlatformItem(
                    name = "Claude (Anthropic)",
                    duration = "20 minutes • Homework & History",
                    statusText = "Clean & Verified ✅",
                    statusColor = StatusSuccess
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AiPlatformItem(
                    name = "Character.AI",
                    duration = "0m • Blocked by Policy",
                    statusText = "Blocked by Policy 🛡️",
                    statusColor = StatusWarning
                )
            }
        }

        // Social Media & Communications Alerts
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("COMMUNICATIONS & SOCIAL SAFETY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CommunicationItem(
                    title = "📞 Call Summary Today",
                    subtitle = "4 Incoming • 6 Outgoing • 1 Missed Call",
                    timeText = "Last call: Dad (5m) • 4:15 PM"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CommunicationItem(
                    title = "💬 SMS Messages Today",
                    subtitle = "28 Messages Total • Top Contact: Dad (14 msgs)",
                    timeText = "Last message 12m ago"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                CommunicationItem(
                    title = "📸 Instagram Safety Alert",
                    subtitle = "Flagged notification from unknown sender",
                    timeText = "Alert triggered 25m ago"
                )
            }
        }
    }
}

@Composable
private fun AiPlatformItem(
    name: String,
    duration: String,
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(duration, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
    }
}

@Composable
private fun CommunicationItem(
    title: String,
    subtitle: String,
    timeText: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(timeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ActivityFeedPreview() {
    com.homemonitor.parent.ui.theme.HomeMonitorTheme {
        ActivityFeedScreen(
            deviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        )
    }
}
