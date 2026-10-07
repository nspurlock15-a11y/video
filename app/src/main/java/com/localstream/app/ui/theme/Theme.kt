package com.localstream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1513),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFB0B0B8),
    background = Color(0xFF0E0E11),
    onBackground = Color(0xFFEDEDF0),
    surface = Color(0xFF16161B),
    onSurface = Color(0xFFEDEDF0),
    surfaceVariant = Color(0xFF24242B),
    onSurfaceVariant = Color(0xFFB4B4BE),
    surfaceContainer = Color(0xFF1B1B21),
    surfaceContainerHigh = Color(0xFF222229),
    surfaceContainerHighest = Color(0xFF2A2A32),
    outline = Color(0xFF55555F),
)

@Composable
fun LocalStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
