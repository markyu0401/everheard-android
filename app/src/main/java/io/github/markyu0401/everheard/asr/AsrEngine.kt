package io.github.markyu0401.everheard.asr

/**
 * Speech-to-text behind a small interface so the app can swap an on-device
 * model for a cloud provider without touching the capture pipeline.
 */
interface AsrEngine {
    val id: String
    suspend fun transcribe(request: AsrRequest): AsrResult
}

data class AsrRequest(
    val pcm16: ShortArray,
    val sampleRate: Int = 16_000,
    val language: String = "zh",
    val hotwords: List<String> = emptyList()
)

data class AsrSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class AsrResult(
    val text: String,
    val segments: List<AsrSegment>,
    val model: String,
    val latencyMs: Long
)

class AsrModelMissingException(message: String) : IllegalStateException(message)
