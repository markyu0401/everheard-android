package io.github.markyu0401.everheard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors. Color carries meaning only: decision, commitment, risk,
 * insight, mark. They are tuned for the light and dark grouped surfaces.
 */
@Immutable
data class SemanticColors(
    val decision: Color,
    val commitment: Color,
    val risk: Color,
    val insight: Color,
    val mark: Color
)

val EverheardSemanticColorsLight = SemanticColors(
    decision = Color(0xFF0A84FF),
    commitment = Color(0xFFFF9F0A),
    risk = Color(0xFFFF3B30),
    insight = Color(0xFFBF5AF2),
    mark = Color(0xFF1C1C1E)
)

val EverheardSemanticColorsDark = SemanticColors(
    decision = Color(0xFF64D2FF),
    commitment = Color(0xFFFFD60A),
    risk = Color(0xFFFF6961),
    insight = Color(0xFFBF5AF2),
    mark = Color(0xFFFFFFFF)
)

val LocalSemanticColors = staticCompositionLocalOf { EverheardSemanticColorsLight }
