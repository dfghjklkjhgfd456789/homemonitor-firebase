package com.homemonitor.parent.ui.theme

import androidx.compose.ui.graphics.Color

// Web Dashboard Light Theme Palette (per DESIGN.md)
val LightBackground = Color(0xFFF6F6F3)   // --hm-bg-page
val LightSurface = Color(0xFFFFFFFF)       // --hm-bg-surface
val LightSurfaceSubtle = Color(0xFFF1F2F4) // --hm-bg-subtle
val LightSurfaceHover = Color(0xFFF7F8FA)  // --hm-bg-hover
val PrimaryBlue = Color(0xFF2468C8)        // --hm-primary
val PrimaryBlueHover = Color(0xFF1D55A5)   // --hm-primary-hover
val PrimaryBlueSubtle = Color(0xFFEAF1FC)  // --hm-primary-subtle
val TextPrimary = Color(0xFF1D1D1F)        // --hm-text-main
val TextSecondary = Color(0xFF646B75)      // --hm-text-secondary
val TextMuted = Color(0xFF9299A3)          // --hm-text-muted
val BorderSubtle = Color(0xFFE4E6EA)       // --hm-border-subtle

// Status Colors (Light)
val StatusSuccessLight = Color(0xFF27834D)   // --hm-success
val StatusSuccessBgLight = Color(0xFFEAF5EE) // --hm-success-subtle
val StatusWarningLight = Color(0xFFA66A12)   // --hm-warning
val StatusWarningBgLight = Color(0xFFFFF4DE) // --hm-warning-subtle
val StatusErrorLight = Color(0xFFC43D45)     // --hm-danger
val StatusErrorBgLight = Color(0xFFFCEEEF)   // --hm-danger-subtle

// Web Dashboard Dark Theme Palette (per DESIGN.md)
val DarkBackground = Color(0xFF121417)   // --hm-bg-page
val DarkSurface = Color(0xFF1A1E23)      // --hm-bg-surface
val DarkSurfaceSubtle = Color(0xFF22272E) // --hm-bg-subtle
val DarkSurfaceHover = Color(0xFF2B323B)  // --hm-bg-hover
val PrimaryBlueDark = Color(0xFF7CB3FF)   // --hm-primary
val PrimaryBlueDarkHover = Color(0xFF599BFF) // --hm-primary-hover
val PrimaryBlueDarkSubtle = Color(0xFF1C2A3A) // --hm-primary-subtle
val TextPrimaryDark = Color(0xFFF3F4F6)    // --hm-text-main
val TextSecondaryDark = Color(0xFFA7AFBA)   // --hm-text-secondary
val TextMutedDark = Color(0xFF858D99)       // --hm-text-muted
val BorderSubtleDark = Color(0xFF313842)     // --hm-border-subtle

// Status Colors (Dark)
val StatusSuccessDark = Color(0xFF30B46F)   // --hm-success
val StatusSuccessBgDark = Color(0xFF142B20) // --hm-success-subtle
val StatusWarningDark = Color(0xFFE8B455)   // --hm-warning
val StatusWarningBgDark = Color(0xFF2C2414) // --hm-warning-subtle
val StatusErrorDark = Color(0xFFF0787E)     // --hm-danger
val StatusErrorBgDark = Color(0xFF2E181A)   // --hm-danger-subtle

// ============================================================
// Backward-compatible aliases for existing screens
// These map old color names to the new web dashboard palette
// ============================================================

// Dark theme aliases (old names -> new dark palette)
val DarkSurfaceElevated = DarkSurfaceSubtle
val DarkSurfaceSelected = PrimaryBlueDarkSubtle
val PrimaryTeal = PrimaryBlueDark
val PrimaryTealVariant = PrimaryBlueDarkHover
val TextPrimaryDarkAlias = TextPrimaryDark
val TextSecondaryDarkAlias = TextSecondaryDark
val DividerDark = BorderSubtleDark

// Light theme aliases (old names -> new light palette)
val LightSurfaceElevated = LightSurfaceSubtle
val PrimaryTealLight = PrimaryBlue
val TextPrimaryLight = TextPrimary
val TextSecondaryLight = TextSecondary

// Status color aliases (old names -> new light palette)
val StatusSuccess = StatusSuccessLight
val StatusWarning = StatusWarningLight
val StatusError = StatusErrorLight
