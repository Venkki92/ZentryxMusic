package com.zentryx.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Zentryx Brand Color Scheme (matching www.zentryx.bg) integrated with Mercedes MBUX UI aesthetic.
 * Deep Obsidian (#020617) + Electric Lime (#A3E635) + Mercedes MBUX glass tile surfaces.
 */
val ZentryxLime = Color(0xFFA3E635)          // Signature Electric Lime (#a3e635)
val ZentryxLimeLight = Color(0xFF86EFAC)     // Soft Mint Glow (#86efac)
val ZentryxLimeGlow = Color(0xFF91F4BF)      // Teal-Mint Ambient (#91f4bf)
val ZentryxLimeDark = Color(0xFF65A30D)      // Deep Olive Accent (#65a30d)

// MBUX Cockpit Night Obsidian Palette
val MbuxObsidian = Color(0xFF020617)         // Deepest Dash Black-Slate (#020617)
val MbuxSurface = Color(0xFF0B1120)          // MBUX Dashboard Tile
val MbuxSurfaceVariant = Color(0xFF131D33)   // MBUX Elevated Glass Card
val MbuxAmbientBorder = Color(0x33A3E635)    // MBUX Neon Ambient Edge (20% Lime)
val MbuxGlassHighlight = Color(0x1A86EFAC)   // Ambient Edge Backlight

val DarkOnBackground = Color(0xFFF8FAFC)
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkOnSurfaceMuted = Color(0xFF94A3B8)

// Daytime High-Contrast Palette
val LightBackground = Color(0xFFF1F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnBackground = Color(0xFF020617)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceMuted = Color(0xFF64748B)

// Status & Warnings
val StatusError = Color(0xFFEF4444)
val StatusSuccess = Color(0xFFA3E635)
val StatusWarning = Color(0xFFF59E0B)

// Alias for seamless backward compatibility across components
val NextcloudBlue = ZentryxLime
