package online.db1k.safering.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import android.graphics.drawable.ColorDrawable

// Timeless / senior palette (parity with iOS SR tokens)
val Ivory = Color(0xFFF7F6F4)
val SoftGold = Color(0xFF9E8560)
val HelpBurgundy = Color(0xFF7A383D)
val UnsureBronze = Color(0xFF856647)
val CallSage = Color(0xFF47665C)
val Ink = Color(0xFF1F1E1C)
val Mute = Color(0xFF6B6560)
val SurfaceCard = Color(0xFFFFFFFF)

object Appearance {
    val forceLight = androidx.compose.runtime.mutableStateOf(false)
    private const val PREFS = "humananswer_appearance"
    private const val KEY = "force_light"

    fun load(context: android.content.Context) {
        forceLight.value = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun setForceLight(context: android.content.Context, value: Boolean) {
        forceLight.value = value
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }
}

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
    onPrimary = Color(0xFF1C1915),
    secondary = CallSage,
    onSecondary = Color.White,
    error = Color(0xFFC9898C),
    onError = Color(0xFF1C1915),
    background = Color(0xFF161412),
    onBackground = Color(0xFFF4EFE6),
    surface = Color(0xFF221F1C),
    onSurface = Color(0xFFF4EFE6),
    surfaceVariant = Color(0xFF2C2824),
    onSurfaceVariant = Color(0xFFC8BEB2),
    outline = Color(0x33FFFFFF),
)

@Composable
fun SafeRingTheme(
    content: @Composable () -> Unit
) {
    val forceLight = Appearance.forceLight.value
    val darkTheme = !forceLight && isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? android.app.Activity ?: return@SideEffect
            val bg = if (darkTheme) 0xFF161412.toInt() else 0xFFF7F6F4.toInt()
            activity.window.setBackgroundDrawable(ColorDrawable(bg))
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
            content = content
        )
    }
}
