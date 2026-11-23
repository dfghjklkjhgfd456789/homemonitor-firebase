package com.homemonitor.parent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    // Auth Routes
    object Login : Screen("login", "Sign In")
    object Register : Screen("register", "Create Account")

    // Core 5 Bottom Navigation Destinations
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Map : Screen("map", "Map & Safe Zones", Icons.Default.Map)
    object Activity : Screen("activity", "Activity & Usage", Icons.Default.BarChart)
    object Alerts : Screen("alerts", "Alerts & Feeds", Icons.Default.Notifications)
    object More : Screen("more", "More & Settings", Icons.Default.MoreHoriz)

    // Sub-screens & Direct Action Destinations
    object Devices : Screen("devices", "Family Devices")
    object DevicePairing : Screen("device_pairing", "Pair New Device")
    object Safety : Screen("safety", "Safety & MDM Controls")
    object SuperAdmin : Screen("superadmin", "Global Fleet Super Admin")
    object Settings : Screen("settings", "Account Settings")

    // Aliases for compatibility
    companion object {
        val Overview = Home
        val Location = Map
        val Usage = Activity
    }
}

val BottomNavScreens = listOf(
    Screen.Home,
    Screen.Map,
    Screen.Activity,
    Screen.Alerts,
    Screen.More
)
