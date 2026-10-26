package io.github.markyu0401.everheard.spike

internal fun formatDuration(ms: Long): String {
    val tenths = (ms / 100).coerceAtLeast(0)
    val seconds = tenths / 10
    val remainder = tenths % 10
    return "%02d:%02d.%d".format(seconds / 60, seconds % 60, remainder)
}
