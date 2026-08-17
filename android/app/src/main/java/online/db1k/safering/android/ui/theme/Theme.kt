package online.db1k.safering.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Timeless / senior palette (parity with iOS SR tokens)
val Ivory = Color(0xFFF7F6F4)
val SoftGold = Color(0xFF9E8560)
val HelpBurgundy = Color(0xFF7A383D)
val UnsureBronze = Color(0xFF856647)
val CallSage = Color(0xFF47665C)
val Ink = Color(0xFF1F1E1C)
val Mute = Color(0xFF6B6560)
val SurfaceCard = Color(0xFFFFFFFF)

val SafeGreen = CallSage
val WarningYellow = UnsureBronze
val HighRiskOrange = UnsureBronze
val CriticalRed = HelpBurgundy
val AccentBlue = SoftGold
val BackgroundLight = Ivory
val SurfaceLight = SurfaceCard
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)

private val LightColorScheme = lightColorScheme(
    primary = SoftGold,
    onPrimary = Color.White,
    secondary = CallSage,
    onSecondary = Color.White,
    error = HelpBurgundy,
    onError = Color.White,
    background = Ivory,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EDE8),
    onSurfaceVariant = Mute,
    outline = Color(0x33000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftGold,
    secondary = CallSage,
    error = Color(0xFFC9898C),
    background = BackgroundDark,
    surface = SurfaceDark,
)

@Composable
fun SafeRingTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
