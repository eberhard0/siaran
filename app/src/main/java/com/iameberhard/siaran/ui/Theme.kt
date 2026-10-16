package com.iameberhard.siaran.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF5A4E)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF12161C),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1C2129),
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceContainer = Color(0xFF12161C),
    secondaryContainer = Color(0xFF3A1F1D),
    onSecondaryContainer = Color(0xFFFFB4AB),
)

/** Always dark: it's a TV app, usually watched in the evening. */
@Composable
fun SiaranTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
