package io.github.markyu0401.everheard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors. Color carries meaning only: decision, commitment, risk, insight, mark.
 * Do not use these for decoration.
 */
@Immutable
data class SemanticColors(
    val decision: Color,
    val commitment: Color,
    val risk: Color,
    val insight: Color,
    val mark: Color
)

val EverheardSemanticColors = SemanticColors(
    decision = Color(0xFF5FD3C4),
    commitment = Color(0xFFE8B45A),
    risk = Color(0xFFE88A8A),
    insight = Color(0xFFA99CF5),
    mark = Color(0xFFF2F3F5)
)

val LocalSemanticColors = staticCompositionLocalOf { EverheardSemanticColors }
