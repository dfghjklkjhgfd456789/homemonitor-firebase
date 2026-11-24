package com.homemonitor.parent.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.homemonitor.parent.ui.theme.StatusWarning
import com.homemonitor.parent.viewmodel.DeviceViewModel

data class UiChatThread(
    val threadId: String,
    val appId: String, // "whatsapp", "sms", "chatgpt", "instagram", "telegram"
    val contactName: String,
    val avatarInitials: String,
    val lastSnippet: String,
    val lastTimestampStr: String,
    val unreadCount: Int = 0,
    val messages: List<UiChatMessage> = emptyList()
)

data class UiChatMessage(
    val messageId: String,
    val senderName: String,
    val text: String,
    val isOutgoing: Boolean, // true = Right Cyan Bubble, false = Left Slate Bubble
    val timeStr: String,
    val isFlagged: Boolean = false
)

/**
 * PersonChatboxScreen
 *
 * Person-specific 2-way chat thread & chatbox viewer for HomeMonitor Parent.
 * Reads real-time chat telemetry from DeviceViewModel and Firebase RTDB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonChatboxScreen(
    deviceViewModel: DeviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit = {}
) {
    var selectedAppFilter by remember { mutableStateOf("All") }
    var activeThread by remember { mutableStateOf<UiChatThread?>(null) }

    val deviceState by deviceViewModel.uiState.collectAsState()
    val rawLogs = deviceState.activityLogs

    // Parse real activity logs from Firebase/DataChannel into ChatThreads
    val threads = remember(rawLogs) {
        val parsedList = mutableListOf<UiChatThread>()
        rawLogs.forEachIndexed { idx, log ->
            val appId = log.optString("sourceApp", log.optString("category", "whatsapp")).lowercase()
            val contact = log.optString("title", "Contact ${idx + 1}")
            val text = log.optString("body", "")
            val time = log.optLong("timestamp", System.currentTimeMillis())
            val timeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(time))

            parsedList.add(
                UiChatThread(
                    threadId = "THREAD_$idx",
                    appId = appId,
                    contactName = contact,
                    avatarInitials = contact.take(1).uppercase(),
                    lastSnippet = text,
                    lastTimestampStr = timeStr,
                    unreadCount = 0,
                    messages = listOf(
                        UiChatMessage("MSG_$idx", contact, text, false, timeStr, log.optBoolean("flagged", false))
                    )
                )
            )
        }
        parsedList
    }

    if (activeThread != null) {
        // Active 2-Way Chatbox Conversation View
        ChatboxConversationView(
            thread = activeThread!!,
            onBackToThreads = { activeThread = null }
        )
    } else {
        // Chat Thread List View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Person-Specific Chat Threads", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            // App Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedAppFilter == "All",
                    onClick = { selectedAppFilter = "All" },
                    label = { Text("All Apps") }
                )
                FilterChip(
                    selected = selectedAppFilter == "whatsapp",
                    onClick = { selectedAppFilter = "whatsapp" },
                    label = { Text("WhatsApp") }
                )
                FilterChip(
                    selected = selectedAppFilter == "chatgpt",
                    onClick = { selectedAppFilter = "chatgpt" },
                    label = { Text("AI Platforms") }
                )
                FilterChip(
                    selected = selectedAppFilter == "instagram",
                    onClick = { selectedAppFilter = "instagram" },
                    label = { Text("Instagram") }
                )
            }

            val filteredThreads = if (selectedAppFilter == "All") threads else threads.filter { it.appId.contains(selectedAppFilter) }

            if (filteredThreads.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Chat Threads Recorded Yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Real-time notifications and message telemetry from WhatsApp, Instagram, ChatGPT, or SMS will appear here as they occur on the child device.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredThreads) { thread ->
                        ThreadCardItem(
                            thread = thread,
                            onClick = { activeThread = thread }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCardItem(
    thread: UiChatThread,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
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
                    text = thread.avatarInitials,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(thread.contactName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(thread.lastTimestampStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = thread.lastSnippet,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatboxConversationView(
    thread: UiChatThread,
    onBackToThreads: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(thread.contactName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Platform: ${thread.appId.uppercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToThreads) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Threads")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(thread.messages) { msg ->
                ChatMessageBubble(msg = msg)
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(msg: UiChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (msg.isOutgoing) 4.dp else 16.dp
            ),
            color = if (msg.isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.isFlagged) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Safety Keyword Flagged", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusWarning)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = msg.senderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (msg.isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.timeStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PersonChatboxPreview() {
    com.homemonitor.parent.ui.theme.HomeMonitorTheme {
        PersonChatboxScreen()
    }
}
