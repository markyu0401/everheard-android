package io.github.markyu0401.everheard.data.transcript

/**
 * Search that survives ASR spelling.
 *
 * Transcripts are literal, so "AirPods" never matched the stored "airpos pro3".
 * This matcher tries, in order: case-insensitive substring, a normalized form
 * with spacing and punctuation removed, and a one-edit comparison for Latin
 * tokens of four or more characters.
 */
object TextMatch {

    private val LATIN_TOKEN = Regex("[A-Za-z][A-Za-z0-9_-]+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    fun matches(query: String, text: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return false
        if (text.contains(needle, ignoreCase = true)) return true
        val normalizedNeedle = normalize(needle)
        val normalizedText = normalize(text)
        if (normalizedNeedle.isNotEmpty() && normalizedText.contains(normalizedNeedle)) return true
        return latinTokens(needle).any { token ->
            token.length >= 4 && latinTokens(text).any { candidate -> withinOneEdit(token, candidate) }
        }
    }

    private fun normalize(value: String): String =
        NON_WORD.replace(value.lowercase(), "")

    private fun latinTokens(value: String): List<String> =
        LATIN_TOKEN.findAll(value).map { it.value.lowercase() }.toList()

    private fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        // Require the same first character: without it a five-letter query
        // matches unrelated words and search returns noise.
        if (a.isEmpty() || b.isEmpty() || a[0] != b[0]) return false
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            edits++
            if (edits > 1) return false
            when {
                a.length > b.length -> i++
                b.length > a.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        edits += (a.length - i) + (b.length - j)
        return edits <= 1
    }
}
