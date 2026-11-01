package io.github.markyu0401.everheard.asr

/**
 * Decides when a pause is long enough to end one conversation and start
 * another. Capture used to keep a single session open from mic-open to
 * mic-close, so a three-hour session could contain a 30-minute silence and
 * report three hours. Five minutes is longer than any natural turn gap and
 * shorter than the 15-minute silence pause.
 */
object SessionSplitter {

    const val GAP_MS: Long = 5 * 60 * 1000L

    fun shouldSplit(lastEndMs: Long?, nextStartMs: Long): Boolean =
        lastEndMs != null && nextStartMs - lastEndMs >= GAP_MS
}
