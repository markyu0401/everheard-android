package io.github.markyu0401.everheard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.markyu0401.everheard.R

/**
 * Instrument Sans for everything read at speed, Instrument Serif for titles and
 * the wordmark. Both are embedded so the app never inherits the phone's
 * handwriting theme. Missing CJK glyphs fall back to the ROM's sans.
 */
val EverheardSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold)
)

val EverheardSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/**
 * Instrument Serif has no CJK glyphs. Chinese screen titles use Noto Serif SC
 * so the editorial display voice survives localization; Latin titles keep
 * Instrument Serif.
 */
val EverheardSerifCjk = FontFamily(
    Font(R.font.noto_serif_sc_regular, FontWeight.Normal)
)

fun displayFontFor(text: String): FontFamily =
    if (text.any { it.isCjk() }) EverheardSerifCjk else EverheardSerif

private fun Char.isCjk(): Boolean =
    code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF

val EverheardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = EverheardSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.9).sp
    ),
    displayMedium = TextStyle(
        fontFamily = EverheardSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.7).sp
    ),
    displaySmall = TextStyle(
        fontFamily = EverheardSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = EverheardSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = EverheardSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        fontSize = 29.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = EverheardSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = EverheardSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = "tnum"
    )
)
