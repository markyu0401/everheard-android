package io.github.markyu0401.everheard.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceEnrollmentTest {

    @Test
    fun accepts_a_clear_speech_sample() {
        val pcm = ShortArray(16_000 * 8) { 4_000 }
        val assessment = VoiceEnrollment.assess(pcm, speechSamples = pcm.size)

        assertThat(assessment.ok).isTrue()
        assertThat(assessment.durationSeconds).isEqualTo(8f)
        assertThat(assessment.rms).isGreaterThan(VoiceEnrollment.MIN_RMS)
    }

    @Test
    fun rejects_a_short_sample() {
        val pcm = ShortArray(16_000 * 2) { 4_000 }
        assertThat(VoiceEnrollment.assess(pcm, pcm.size).issue)
            .isEqualTo(VoiceIssue.TOO_SHORT)
    }

    @Test
    fun rejects_a_quiet_sample() {
        val pcm = ShortArray(16_000 * 8) { 10 }
        assertThat(VoiceEnrollment.assess(pcm, pcm.size).issue)
            .isEqualTo(VoiceIssue.TOO_QUIET)
    }

    @Test
    fun rejects_a_sample_with_little_speech() {
        val pcm = ShortArray(16_000 * 8) { 4_000 }
        assertThat(VoiceEnrollment.assess(pcm, speechSamples = 0).issue)
            .isEqualTo(VoiceIssue.NOT_ENOUGH_SPEECH)
    }
}
