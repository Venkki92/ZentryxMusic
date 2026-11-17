package com.zentryx.music.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.MbuxAccentColor
import java.util.Calendar

@Composable
fun ZentryxTheme(
    accentColor: MbuxAccentColor = MbuxAccentColor.LIME,
    cockpitThemeMode: CockpitThemeMode = CockpitThemeMode.NIGHT,
    content: @Composable () -> Unit
) {
    val activeAccent = Color(accentColor.hexColor)

    val useDayMode = when (cockpitThemeMode) {
        CockpitThemeMode.DAY -> true
        CockpitThemeMode.NIGHT -> false
        CockpitThemeMode.AUTO -> {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            hour in 7..17
        }
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

    val daylightCockpitScheme = darkColorScheme(
        primary = activeAccent,
        onPrimary = Color.Black,
        primaryContainer = activeAccent.copy(alpha = 0.28f),
        onPrimaryContainer = Color.White,
        secondary = activeAccent,
        onSecondary = Color.Black,
        background = Color(0xFF0D1427),
        onBackground = Color.White,
        surface = Color(0xFF162038),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = activeAccent.copy(alpha = 0.45f),
        outlineVariant = Color(0xFF334155),
        error = StatusError
    )

    val colorScheme = if (useDayMode) daylightCockpitScheme else darkScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
