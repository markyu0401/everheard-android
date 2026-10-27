package io.github.markyu0401.everheard.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours carry meaning only: decision, commitment, risk, idea, live.
 * They are muted so the evergreen accent stays the one signal.
 */
@Immutable
data class SemanticColors(
    val decision: Color,
    val commitment: Color,
    val risk: Color,
    val insight: Color,
    val mark: Color,
    val live: Color
)

val EverheardSemanticColorsLight = SemanticColors(
    decision = Color(0xFF2F5D8A),
    commitment = Color(0xFFA66A1E),
    risk = Color(0xFFA9453C),
    insight = Color(0xFF6B5B8E),
    mark = Color(0xFF101310),
    live = Color(0xFFC24E3A)
)

val EverheardSemanticColorsDark = SemanticColors(
    decision = Color(0xFF8FB8DD),
    commitment = Color(0xFFE0B36A),
    risk = Color(0xFFE08B84),
    insight = Color(0xFFB9A8DD),
    mark = Color(0xFFF1F4EF),
    live = Color(0xFFF08A72)
)

val LocalSemanticColors = staticCompositionLocalOf { EverheardSemanticColorsLight }
