package com.homemonitor.parent.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Login : Screen("login", "Sign In")
    object Register : Screen("register", "Create Account")
    object Overview : Screen("overview", "Home Overview")
    object Devices : Screen("devices", "Family Devices")
    object DevicePairing : Screen("device_pairing", "Pair New Device")
    object Location : Screen("location", "Location & Safe Zones")
    object Usage : Screen("usage", "Screen Time & Apps")
    object Activity : Screen("activity", "Activity Feed")
    object Safety : Screen("safety", "Safety & Controls")
    object SuperAdmin : Screen("superadmin", "Global Fleet Super Admin")
    object Settings : Screen("settings", "Account Settings")
}
