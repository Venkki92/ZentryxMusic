package com.zentryx.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * MBUX-style dark luxury theme combined with Zentryx.bg brand electric lime accents.
 * Optimized for OLED/IPS automotive displays (1024x600 8-inch).
 */
private val DarkColorScheme = darkColorScheme(
    primary = ZentryxLime,
    onPrimary = Color.Black,
    primaryContainer = ZentryxLimeDark,
    onPrimaryContainer = Color.White,
    secondary = ZentryxLimeLight,
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

private val LightColorScheme = lightColorScheme(
    primary = ZentryxLimeDark,
    onPrimary = Color.White,
    primaryContainer = ZentryxLimeLight,
    onPrimaryContainer = Color.Black,
    secondary = ZentryxLime,
    onSecondary = Color.Black,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = Color(0xFFCBD5E1),
    error = StatusError
)

@Composable
fun ZentryxTheme(
    darkTheme: Boolean = true, // Default to Dark MBUX Theme on automotive displays
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
