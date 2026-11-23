package com.homemonitor.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.homemonitor.parent.data.repository.ParentUserSession
import com.homemonitor.parent.ui.navigation.ParentNavDrawerContent
import com.homemonitor.parent.ui.navigation.Screen
import com.homemonitor.parent.ui.screens.activity.ActivityFeedScreen
import com.homemonitor.parent.ui.screens.auth.LoginScreen
import com.homemonitor.parent.ui.screens.auth.RegisterScreen
import com.homemonitor.parent.ui.screens.devices.DeviceListScreen
import com.homemonitor.parent.ui.screens.devices.DevicePairingQrScreen
import com.homemonitor.parent.ui.screens.location.MapLocationScreen
import com.homemonitor.parent.ui.screens.overview.ParentDashboardOverviewScreen
import com.homemonitor.parent.ui.screens.safety.MdmSecurityScreen
import com.homemonitor.parent.ui.screens.settings.ParentSettingsScreen
import com.homemonitor.parent.ui.screens.superadmin.SuperAdminControlPanelScreen
import com.homemonitor.parent.ui.screens.usage.ScreenTimeScreen
import com.homemonitor.parent.viewmodel.AuthUiState
import com.homemonitor.parent.viewmodel.AuthViewModel
import com.homemonitor.parent.viewmodel.DeviceViewModel
import com.homemonitor.parent.viewmodel.WebRtcViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val authViewModel: AuthViewModel = viewModel()
                    val deviceViewModel: DeviceViewModel = viewModel()
                    val webRtcViewModel: WebRtcViewModel = viewModel()

                    val authState by authViewModel.uiState.collectAsState()
                    val deviceState by deviceViewModel.uiState.collectAsState()
                    val webRtcState by webRtcViewModel.uiState.collectAsState()

                    val navController = rememberNavController()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Login.route

                    val activeSession = (authState as? AuthUiState.Authenticated)?.session

                    LaunchedEffect(activeSession) {
                        activeSession?.let { session ->
                            deviceViewModel.observeUserDevices(session.uid)
                            if (session.isSuperAdmin) {
                                deviceViewModel.observeGlobalFleet()
                                webRtcViewModel.setSuperAdminMode(true)
                            }
                        }
                    }

                    if (activeSession != null) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ParentNavDrawerContent(
                                    currentRoute = currentRoute,
                                    userEmail = activeSession.email,
                                    isSuperAdmin = activeSession.isSuperAdmin,
                                    onNavigateTo = { route ->
                                        scope.launch { drawerState.close() }
                                        navController.navigate(route)
                                    },
                                    onSignOut = {
                                        scope.launch { drawerState.close() }
                                        authViewModel.signOut()
                                    }
                                )
                            }
                        ) {
                            Scaffold(
                                topBar = {
                                    if (currentRoute != Screen.Login.route && currentRoute != Screen.Register.route) {
                                        OptInTopBar(
                                            title = ScreenTitle(currentRoute),
                                            onOpenDrawer = { scope.launch { drawerState.open() } }
                                        )
                                    }
                                }
                            ) { padding ->
                                Box(modifier = Modifier.padding(padding)) {
                                    NavGraphContent(
                                        navController = navController,
                                        authViewModel = authViewModel,
                                        deviceViewModel = deviceViewModel,
                                        webRtcViewModel = webRtcViewModel,
                                        activeSession = activeSession
                                    )
                                }
                            }
                        }
                    } else {
                        NavGraphContent(
                            navController = navController,
                            authViewModel = authViewModel,
                            deviceViewModel = deviceViewModel,
                            webRtcViewModel = webRtcViewModel,
                            activeSession = null
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptInTopBar(title: String, onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Open Left Navigation Drawer")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    )
}

private fun ScreenTitle(route: String): String {
    return when (route) {
        Screen.Overview.route -> Screen.Overview.title
        Screen.Devices.route -> Screen.Devices.title
        Screen.DevicePairing.route -> Screen.DevicePairing.title
        Screen.Location.route -> Screen.Location.title
        Screen.Usage.route -> Screen.Usage.title
        Screen.Activity.route -> Screen.Activity.title
        Screen.Safety.route -> Screen.Safety.title
        Screen.SuperAdmin.route -> Screen.SuperAdmin.title
        Screen.Settings.route -> Screen.Settings.title
        else -> "HomeMonitor Parent"
    }
}

@Composable
private fun NavGraphContent(
    navController: androidx.navigation.NavHostController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    webRtcViewModel: WebRtcViewModel,
    activeSession: ParentUserSession?
) {
    val startDest = if (activeSession != null) Screen.Overview.route else Screen.Login.route

    NavHost(navController = navController, startDestination = startDest) {
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = { navController.navigate(Screen.Login.route) }
            )
        }

        composable(Screen.Overview.route) {
            ParentDashboardOverviewScreen(
                deviceViewModel = deviceViewModel,
                onNavigateTo = { route -> navController.navigate(route) },
                isSuperAdmin = activeSession?.isSuperAdmin ?: false
            )
        }

        composable(Screen.Devices.route) {
            DeviceListScreen(
                deviceViewModel = deviceViewModel,
                onNavigateToPairing = { navController.navigate(Screen.DevicePairing.route) },
                onSelectDevice = { devId ->
                    deviceViewModel.selectDevice(devId)
                    navController.navigate(Screen.Overview.route)
                }
            )
        }

        composable(Screen.DevicePairing.route) {
            DevicePairingQrScreen(
                onPairSuccess = { devId ->
                    activeSession?.let { session ->
                        deviceViewModel.pairNewDevice(session.uid, devId, "Pixel 8 Pro")
                    }
                    navController.navigate(Screen.Devices.route)
                }
            )
        }

        composable(Screen.Location.route) {
            MapLocationScreen(deviceViewModel = deviceViewModel)
        }

        composable(Screen.Usage.route) {
            ScreenTimeScreen(deviceViewModel = deviceViewModel)
        }

        composable(Screen.Activity.route) {
            ActivityFeedScreen(deviceViewModel = deviceViewModel)
        }

        composable(Screen.Safety.route) {
            MdmSecurityScreen(deviceViewModel = deviceViewModel)
        }

        composable(Screen.SuperAdmin.route) {
            SuperAdminControlPanelScreen(
                deviceViewModel = deviceViewModel,
                webRtcViewModel = webRtcViewModel
            )
        }

        composable(Screen.Settings.route) {
            ParentSettingsScreen(
                activeSession = activeSession,
                onSignOut = { authViewModel.signOut() }
            )
        }
    }
}
