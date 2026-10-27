package io.github.markyu0401.everheard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Evergreen on paper, mint on true black. The accent is the only chromatic
 * colour in the chrome; semantic colours only carry meaning.
 */
private val EverheardLightScheme = lightColorScheme(
    primary = Color(0xFF1D6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EFE8),
    onPrimaryContainer = Color(0xFF0B3D2C),
    secondary = Color(0xFFA66A1E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E9D8),
    onSecondaryContainer = Color(0xFF5A3A0E),
    tertiary = Color(0xFF6B5B8E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFECE7F4),
    onTertiaryContainer = Color(0xFF3D3355),
    error = Color(0xFFA9453C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6E1DE),
    onErrorContainer = Color(0xFF6B211C),
    background = Color(0xFFF5F6F2),
    onBackground = Color(0xFF101310),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101310),
    surfaceVariant = Color(0xFFECEEE8),
    onSurfaceVariant = Color(0xFF4E554E),
    outline = Color(0xFFCDD2C9),
    outlineVariant = Color(0xFFE2E5DE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF101310),
    inverseOnSurface = Color(0xFFF5F6F2),
    inversePrimary = Color(0xFF74D3A8)
)

private val EverheardDarkScheme = darkColorScheme(
    primary = Color(0xFF74D3A8),
    onPrimary = Color(0xFF06130D),
    primaryContainer = Color(0xFF143126),
    onPrimaryContainer = Color(0xFFB9F3D6),
    secondary = Color(0xFFE0B36A),
    onSecondary = Color(0xFF2A1B06),
    secondaryContainer = Color(0xFF3A2A12),
    onSecondaryContainer = Color(0xFFF5D9A8),
    tertiary = Color(0xFFB9A8DD),
    onTertiary = Color(0xFF1B1428),
    tertiaryContainer = Color(0xFF2E2740),
    onTertiaryContainer = Color(0xFFE4DCF4),
    error = Color(0xFFE08B84),
    onError = Color(0xFF2A0E0B),
    errorContainer = Color(0xFF3A1C19),
    onErrorContainer = Color(0xFFF5C9C4),
    background = Color(0xFF0A0C0A),
    onBackground = Color(0xFFF1F4EF),
    surface = Color(0xFF121512),
    onSurface = Color(0xFFF1F4EF),
    surfaceVariant = Color(0xFF181C18),
    onSurfaceVariant = Color(0xFFA9B0A8),
    outline = Color(0xFF343B34),
    outlineVariant = Color(0xFF232823),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF1F4EF),
    inverseOnSurface = Color(0xFF0A0C0A),
    inversePrimary = Color(0xFF1D6B4F)
)

val EverheardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
            shapes = EverheardShapes,
            content = content
        )
    }
}
