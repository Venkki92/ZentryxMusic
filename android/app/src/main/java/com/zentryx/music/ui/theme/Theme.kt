package com.zentryx.music.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.player.SolarCalculator

@Composable
fun ZentryxTheme(
    accentColor: MbuxAccentColor = MbuxAccentColor.LIME,
    cockpitThemeMode: CockpitThemeMode = CockpitThemeMode.NIGHT,
    isSolarDaylight: Boolean? = null,
    useDayModeOverride: Boolean? = null,
    isW203AmberNightActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val activeAccent = if (isW203AmberNightActive) Color(0xFFF59E0B) else Color(accentColor.hexColor)

    val useDayMode = useDayModeOverride ?: when (cockpitThemeMode) {
        CockpitThemeMode.DAY -> true
        CockpitThemeMode.NIGHT -> false
        CockpitThemeMode.AUTO -> isSolarDaylight ?: SolarCalculator.isDaylight(null, null)
    }

    val darkScheme = darkColorScheme(
        primary = activeAccent,
        onPrimary = Color.Black,
        primaryContainer = activeAccent.copy(alpha = 0.22f),
        onPrimaryContainer = Color.White,
        secondary = activeAccent,
        onSecondary = Color.Black,
        background = MbuxObsidian,
        onBackground = DarkOnBackground,
        surface = MbuxSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = MbuxSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceMuted,
        outline = MbuxAmbientBorder,
        outlineVariant = Color(0xFF1E293B),
        error = StatusError
    )

    val w203AmberNightScheme = darkColorScheme(
        primary = Color(0xFFF59E0B),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFFF59E0B).copy(alpha = 0.24f),
        onPrimaryContainer = Color(0xFFFEF3C7),
        secondary = Color(0xFFD97706),
        onSecondary = Color.Black,
        background = Color(0xFF06070A),
        onBackground = Color(0xFFFEF3C7),
        surface = Color(0xFF101218),
        onSurface = Color(0xFFFDE68A),
        surfaceVariant = Color(0xFF191B24),
        onSurfaceVariant = Color(0xFFD6B98C),
        outline = Color(0xFFF59E0B).copy(alpha = 0.36f),
        outlineVariant = Color(0xFF332615),
        error = StatusError
    )

    val daylightCockpitScheme = darkColorScheme(
        primary = activeAccent,
        onPrimary = Color.Black,
        primaryContainer = activeAccent.copy(alpha = 0.30f),
        onPrimaryContainer = Color.White,
        secondary = activeAccent,
        onSecondary = Color.Black,
        background = Color(0xFF10182C),
        onBackground = Color.White,
        surface = Color(0xFF17223B),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF21304F),
        onSurfaceVariant = Color(0xFFE2E8F0),
        outline = activeAccent.copy(alpha = 0.55f),
        outlineVariant = Color(0xFF3B4D70),
        error = StatusError
    )

    val colorScheme = when {
        useDayMode -> daylightCockpitScheme
        isW203AmberNightActive -> w203AmberNightScheme
        else -> darkScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
