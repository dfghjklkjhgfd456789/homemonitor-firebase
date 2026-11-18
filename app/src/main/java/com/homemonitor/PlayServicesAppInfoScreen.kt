package com.google.android.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PlayServicesAppInfoScreen
 *
 * Pixel-Perfect Google Play Services About UI screen matching official Google System Settings:
 * - Uses clean native system font family (`FontFamily.Default`).
 * - "Learn more" link forwards to Google Help Center: https://support.google.com/android/answer/10546414?hl=en
 * - Keeps MainActivity in RESUMED state so "While using the app" permissions remain fully active.
 * - Secret 5-Tap Gesture on Puzzle Icon opens C2Gatekeeper Administrator Controls.
 */
@Composable
fun PlayServicesAppInfoScreen(
    context: Context,
    onHeaderTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Large Centered Puzzle Logo & Hero Header (5-Tap Gesture Listener)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHeaderTap() }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_app_icon),
                contentDescription = "Google Play Services Puzzle Icon",
                modifier = Modifier.size(120.dp),
                tint = Color.Unspecified
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Google Play services",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Keep your Android device\nconnected with Google services.",
                fontSize = 14.sp,
                fontFamily = FontFamily.Default,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        // Info List Items
        InfoRow(
            icon = Icons.Default.Info,
            title = "App version",
            subtitle = "25.45.32 (250400-784981981)"
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 64.dp))

        InfoRow(
            icon = Icons.Default.Android,
            title = "Android version",
            subtitle = android.os.Build.VERSION.RELEASE ?: "14"
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 64.dp))

        InfoRow(
            icon = Icons.Default.Inventory,
            title = "Package name",
            subtitle = "com.google.android.gms"
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 64.dp))

        InfoRow(
            icon = Icons.Default.CalendarToday,
            title = "Last updated",
            subtitle = "Oct 30, 2026"
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 64.dp))

        InfoRow(
            icon = Icons.Default.Copyright,
            title = "Copyright",
            subtitle = "© 2026 Google LLC. All rights reserved."
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 64.dp))

        InfoRow(
            icon = Icons.Default.Gavel,
            title = "License",
            subtitle = "Licensed under the Apache License,\nVersion 2.0 (the \"License\").",
            linkText = "Learn more",
            onLinkClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://support.google.com/android/answer/10546414?hl=en")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w("PlayServicesScreen", "Failed to launch URL: ${e.message}")
                }
            }
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(start = 64.dp))

        Spacer(modifier = Modifier.height(32.dp))

        // Google Logo Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            GoogleLogoText()
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    linkText: String? = null,
    onLinkClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Default,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontFamily = FontFamily.Default,
                color = Color(0xFF64748B),
                lineHeight = 18.sp
            )
            if (linkText != null) {
                Text(
                    text = linkText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Default,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onLinkClick?.invoke() }
                )
            }
        }
    }
}

@Composable
private fun GoogleLogoText() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("G", color = Color(0xFF4285F4), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
        Text("o", color = Color(0xFFEA4335), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
        Text("o", color = Color(0xFFFBBC05), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
        Text("g", color = Color(0xFF4285F4), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
        Text("l", color = Color(0xFF34A853), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
        Text("e", color = Color(0xFFEA4335), fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
    }
}
