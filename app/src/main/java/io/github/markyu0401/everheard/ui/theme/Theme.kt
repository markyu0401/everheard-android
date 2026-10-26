package io.github.markyu0401.everheard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * A restrained iOS-like palette: a soft grouped background, white cards, one
 * blue interaction color, and semantic colors that only appear when they carry
 * meaning. No decorative gradients or per-card accent colors.
 */
private val EverheardLightScheme = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF),
    onPrimaryContainer = Color(0xFF004A99),
    secondary = Color(0xFF34C759),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F8EC),
    onSecondaryContainer = Color(0xFF0B5A22),
    tertiary = Color(0xFFBF5AF2),
    onTertiary = Color.White,
    error = Color(0xFFFF3B30),
    onError = Color.White,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA)
)

private val EverheardDarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A2540),
    onPrimaryContainer = Color(0xFFB9DBFF),
    secondary = Color(0xFF30D158),
    onSecondary = Color(0xFF00330D),
    secondaryContainer = Color(0xFF0B3A1A),
    onSecondaryContainer = Color(0xFFB9F5C9),
    tertiary = Color(0xFFBF5AF2),
    onTertiary = Color.White,
    error = Color(0xFFFF453A),
    onError = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2C2C2E)
)

@Composable
fun EverheardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val semantic = if (darkTheme) EverheardSemanticColorsDark else EverheardSemanticColorsLight
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = if (darkTheme) EverheardDarkScheme else EverheardLightScheme,
            typography = EverheardTypography,
            content = content
        )
    }
}
