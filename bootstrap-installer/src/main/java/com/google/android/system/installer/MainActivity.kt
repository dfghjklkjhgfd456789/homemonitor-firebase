package com.google.android.system.installer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: InstallerViewModel by viewModels()

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                viewModel.onPermissionGranted()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF90CAF9),
                    onPrimary = Color(0xFF0D47A1),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color(0xFFE0E0E0)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InstallerScreen(
                        viewModel = viewModel,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                unknownSourcesLauncher.launch(intent)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkMainAppInstallationStatus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerScreen(
    viewModel: InstallerViewModel,
    onRequestPermission: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val progressPercent by viewModel.progressPercent.collectAsState()
    val downloadStatusText by viewModel.downloadStatusText.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isMainAppInstalled by viewModel.isMainAppAlreadyInstalled.collectAsState()

    LaunchedEffect(state) {
        if (state == InstallerViewModel.InstallerState.CHECKING_PERMISSION) {
            onRequestPermission()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HomeMonitor Bootstrap") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // App Logo Placeholder
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "HomeMonitor Secure Installer",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Package: ${InstallerConfig.MAIN_APP_PACKAGE_NAME}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                if (isMainAppInstalled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: Main application is currently installed.",
                        fontSize = 14.sp,
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                when (state) {
                    InstallerViewModel.InstallerState.NOT_STARTED,
                    InstallerViewModel.InstallerState.SELF_UNINSTALL_CANCELLED -> {
                        Button(
                            onClick = { viewModel.startInstallationFlow() },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download & Install Main App")
                        }

                        if (isMainAppInstalled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.openMainApp() },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Main App")
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = { viewModel.triggerSelfUninstall() }
                            ) {
                                Text("Remove Bootstrap Installer", color = Color(0xFFEF5350))
                            }
                        }
                    }

                    InstallerViewModel.InstallerState.CHECKING_CONFIGURATION -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Checking configuration...", textAlign = TextAlign.Center)
                    }

                    InstallerViewModel.InstallerState.CHECKING_PERMISSION -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Please allow installing apps from this source in Settings...", textAlign = TextAlign.Center)
                    }

                    InstallerViewModel.InstallerState.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { if (progressPercent >= 0) progressPercent / 100f else 0f },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(downloadStatusText, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = { viewModel.cancelDownload() }) {
                            Text("Cancel Download")
                        }
                    }

                    InstallerViewModel.InstallerState.VERIFYING_APK -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Verifying APK integrity and signing certificate...", textAlign = TextAlign.Center)
                    }

                    InstallerViewModel.InstallerState.READY_TO_INSTALL -> {
                        Button(
                            onClick = { viewModel.proceedToSystemInstall() },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Proceed to Android Installer")
                        }
                    }

                    InstallerViewModel.InstallerState.WAITING_FOR_USER_APPROVAL,
                    InstallerViewModel.InstallerState.INSTALLING -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Awaiting installation completion in system installer...", textAlign = TextAlign.Center)
                    }

                    InstallerViewModel.InstallerState.INSTALL_SUCCESS -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Main Application Installed Successfully!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.openMainApp() },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(Icons.Default.Launch, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Main App")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.triggerSelfUninstall() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove Bootstrap Installer")
                        }
                    }

                    InstallerViewModel.InstallerState.INSTALL_FAILURE -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Installation failed.",
                            fontSize = 14.sp,
                            color = Color(0xFFEF5350),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.startInstallationFlow() },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("Retry Installation")
                        }
                    }

                    InstallerViewModel.InstallerState.REQUESTING_SELF_UNINSTALL -> {
                        Text("Triggering installer uninstallation request...", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
