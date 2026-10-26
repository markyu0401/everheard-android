package io.github.markyu0401.everheard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val EverheardDarkScheme = darkColorScheme(
    primary = Color(0xFFE8B45A),
    onPrimary = Color(0xFF14100A),
    primaryContainer = Color(0xFF3A2A12),
    onPrimaryContainer = Color(0xFFF6D9A6),
    secondary = Color(0xFF5FD3C4),
    onSecondary = Color(0xFF06201C),
    secondaryContainer = Color(0xFF123330),
    onSecondaryContainer = Color(0xFFB9F1E9),
    tertiary = Color(0xFFA99CF5),
    onTertiary = Color(0xFF120E2A),
    error = Color(0xFFE88A8A),
    onError = Color(0xFF2A0F0F),
    background = Color(0xFF0A0B0D),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF141619),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF1B1E22),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF2A2D31),
    outlineVariant = Color(0xFF1F2226)
)

@Composable
fun EverheardTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSemanticColors provides EverheardSemanticColors) {
        MaterialTheme(
            colorScheme = EverheardDarkScheme,
            typography = EverheardTypography,
            content = content
        )
    }
}
