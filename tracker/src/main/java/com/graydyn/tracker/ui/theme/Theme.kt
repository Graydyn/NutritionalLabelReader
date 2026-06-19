package com.graydyn.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Light, nutrition-oriented color scheme anchored on a health green (matching [MacroCarbs])
 * so the macro accent colors and the app chrome read as one system. The screens are
 * light-only and fully token-driven, so this is the single source of their styling.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),            // green 800 — matches launcher background
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7E4BA),   // soft green fill
    onPrimaryContainer = Color(0xFF052109),
    secondary = Color(0xFF52634F),          // muted sage
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F0E),
    tertiary = Color(0xFF38656A),           // teal accent
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFFBFDF7),          // barely-green off white
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9),      // card / chip backdrop
    onSurfaceVariant = Color(0xFF424940),    // secondary text
    outline = Color(0xFF727970)
)

@Composable
fun TrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = TrackerTypography,
        shapes = TrackerShapes,
        content = content
    )
}
