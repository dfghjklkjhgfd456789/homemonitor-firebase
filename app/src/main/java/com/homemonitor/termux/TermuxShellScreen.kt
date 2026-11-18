package com.google.android.system.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TermuxShellScreen
 *
 * Jetpack Compose interface for the embedded Termux Linux Subsystem.
 * Provides on-device interactive command execution, quick presets, and live output logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxShellScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var commandInput by remember { mutableStateOf("") }
    var outputLog by remember { mutableStateOf("Termux Subsystem Initialized.\nReady for interactive command execution.") }
    var isExecuting by remember { mutableStateOf(false) }

    val quickCommands = remember {
        listOf("uname -a", "ls -la", "top", "df -h", "whoami", "id", "pkg list")
    }

    fun executeCmd(cmdToRun: String) {
        if (cmdToRun.isBlank() || isExecuting) return
        isExecuting = true
        outputLog = "⚡ Executing: '$cmdToRun'...\n"

        coroutineScope.launch(Dispatchers.IO) {
            val response = TermuxShellManager.executeRemoteCommand(context, cmdToRun)
            withContext(Dispatchers.Main) {
                outputLog = "💻 Exec Result (Exit Code: ${response.exitCode}):\n\n${response.rawOutput.ifBlank { "No output returned." }}"
                isExecuting = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "💻 Termux Linux Subsystem",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF020617)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "🟢 Subsystem Status: Active & Headless Integrated",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                    Text(
                        "Discord Channel: #16-termux-shell (`!termux <command>`)",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Quick Preset Buttons
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(quickCommands) { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            commandInput = preset
                            executeCmd(preset)
                        },
                        label = { Text(preset, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFE2E8F0)
                        )
                    )
                }
            }

            // Command Input Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("Enter shell command...", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { executeCmd(commandInput) },
                    enabled = !isExecuting && commandInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = Color.White)
                    }
                }
            }

            // Output Actions Bar
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    "Output Log",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )

                Row {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Log", outputLog))
                            Toast.makeText(context, "Output copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8))
                    }

                    IconButton(
                        onClick = { outputLog = "Log cleared." }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                }
            }

            // Console Output Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = outputLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}
