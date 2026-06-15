package com.safering.android.ui.theme

import androidx.compose.ui.graphics.Color

// Primary — Blue-based for trust and calm
val PrimaryLight = Color(0xFF1565C0)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD1E4FF)
val OnPrimaryContainerLight = Color(0xFF001D36)

// Secondary — Teal for accent
val SecondaryLight = Color(0xFF006B5E)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFF72F8E2)
val OnSecondaryContainerLight = Color(0xFF00201B)

// Tertiary — Warm amber for warnings
val TertiaryLight = Color(0xFF7C5800)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDEA1)
val OnTertiaryContainerLight = Color(0xFF271900)

// Error — Red for scam alerts
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Background & Surface
val BackgroundLight = Color(0xFFFDFCFF)
val OnBackgroundLight = Color(0xFF1A1C1E)
val SurfaceLight = Color(0xFFFDFCFF)
val OnSurfaceLight = Color(0xFF1A1C1E)
val SurfaceVariantLight = Color(0xFFDFE2EB)
val OnSurfaceVariantLight = Color(0xFF43474E)
val OutlineLight = Color(0xFF73777F)

// Dark theme
val PrimaryDark = Color(0xFF9ECAFF)
val OnPrimaryDark = Color(0xFF003258)
val PrimaryContainerDark = Color(0xFF00497D)
val OnPrimaryContainerDark = Color(0xFFD1E4FF)

val SecondaryDark = Color(0xFF4EDBC6)
val OnSecondaryDark = Color(0xFF00382F)
val SecondaryContainerDark = Color(0xFF005046)
val OnSecondaryContainerDark = Color(0xFF72F8E2)

val TertiaryDark = Color(0xFFF5BF48)
val OnTertiaryDark = Color(0xFF412D00)
val TertiaryContainerDark = Color(0xFF5E4200)
val OnTertiaryContainerDark = Color(0xFFFFDEA1)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF1A1C1E)
val OnBackgroundDark = Color(0xFFE2E2E6)
val SurfaceDark = Color(0xFF1A1C1E)
val OnSurfaceDark = Color(0xFFE2E2E6)
val SurfaceVariantDark = Color(0xFF43474E)
val OnSurfaceVariantDark = Color(0xFFC3C7CF)
val OutlineDark = Color(0xFF8D9199)

// SafeRing-specific semantic colors
object SafeRingColors {
    // Risk level colors
    val RiskHigh = Color(0xFFD32F2F)
    val RiskMedium = Color(0xFFF57C00)
    val RiskLow = Color(0xFFFBC02D)
    val RiskSafe = Color(0xFF388E3C)

    // Protection status
    val ProtectionActive = Color(0xFF388E3C)
    val ProtectionWarning = Color(0xFFF57C00)
    val ProtectionInactive = Color(0xFF9E9E9E)

    // Badge colors
    val BadgeScam = Color(0xFFD32F2F)
    val BadgeSpam = Color(0xFFF57C00)
    val BadgeLegit = Color(0xFF388E3C)
    val BadgeUnknown = Color(0xFF9E9E9E)
}
