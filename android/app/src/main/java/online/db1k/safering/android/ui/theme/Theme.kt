package online.db1k.safering.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Colors ─────────────────────────────────────────────────────

val SafeGreen = Color(0xFF4CAF50)
val WarningYellow = Color(0xFFFFC107)
val HighRiskOrange = Color(0xFFFF9800)
val CriticalRed = Color(0xFFF44336)

val AccentBlue = Color(0xFF1565C0)
val BackgroundLight = Color(0xFFF5F5F5)
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)

// ─── Color Scheme ───────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = SafeGreen,
    error = CriticalRed,
    background = BackgroundLight,
    surface = SurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF81C784),
    error = Color(0xFFEF9A9A),
    background = BackgroundDark,
    surface = SurfaceDark,
)

// ─── Theme ──────────────────────────────────────────────────────

@Composable
fun SafeRingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
