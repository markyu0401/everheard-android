package io.github.markyu0401.everheard.data.backup

import com.google.common.truth.Truth.assertThat
import io.github.markyu0401.everheard.capture.schedule.ScheduleMode
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.Inspiration
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.MemoryEvidence
import io.github.markyu0401.everheard.data.memory.RuleBasedMemoryAnalyzer
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import java.time.DayOfWeek
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val evidence = MemoryEvidence(
        utteranceId = "u1",
        quote = "我们下午三点开会",
        startMs = 0,
        speaker = "me"
    )

    private val session = TranscriptSession(
        id = "s1",
        startedAt = 1_000L,
        endedAt = 2_000L,
        title = "组会",
        utterances = listOf(
            TranscriptUtterance(
                id = "u1",
                sessionId = "s1",
                startMs = 0,
                endMs = 1_500,
                text = "我们下午三点开会",
                speaker = "me",
                marked = true
            )
        )
    )

    private val memory = SessionMemory(
        sessionId = "s1",
        summary = "下午三点开会",
        summaryEvidence = evidence,
        events = listOf(
            MemoryEvent(
                id = "e1",
                type = MemoryEventType.DECISION,
                text = "三点开会",
                evidence = evidence
            )
        ),
        commitments = listOf(
            Commitment(
                id = "c1",
                text = "下午三点开会",
                owner = "me",
                dueHint = "today",
                evidence = evidence
            )
        ),
        inspirations = listOf(
            Inspiration(id = "i1", text = "提前发议程", evidence = evidence)
        ),
        generatedAt = 3_000L,
        analyzer = RuleBasedMemoryAnalyzer.NAME
    )

    private val rule = ScheduleRule(
        id = 1,
        days = setOf(DayOfWeek.MONDAY),
        startMinute = 23 * 60 + 30,
        endMinute = 7 * 60,
        mode = ScheduleMode.BLOCK
    )

    private fun payload() = BackupPayload(
        createdAt = 4_000L,
        transcripts = listOf(session),
        memories = listOf(memory),
        scheduleRules = listOf(rule),
        excludedApps = listOf("com.google.android.youtube")
    )

    @Test
    fun payload_round_trips() {
        val decoded = BackupCodec.decodePayload(BackupCodec.encodePayload(payload()))

        assertThat(decoded.version).isEqualTo(BackupCodec.FORMAT_VERSION)
        assertThat(decoded.createdAt).isEqualTo(4_000L)
        assertThat(decoded.transcripts).hasSize(1)
        assertThat(decoded.transcripts.first().title).isEqualTo("组会")
        assertThat(decoded.transcripts.first().utterances.first().marked).isTrue()
        assertThat(decoded.memories).hasSize(1)
        assertThat(decoded.memories.first().commitments).hasSize(1)
        assertThat(decoded.scheduleRules).hasSize(1)
        assertThat(decoded.scheduleRules.first().mode).isEqualTo(ScheduleMode.BLOCK)
        assertThat(decoded.excludedApps).containsExactly("com.google.android.youtube")
    }

    @Test
    fun empty_payload_round_trips() {
        val decoded = BackupCodec.decodePayload(
            BackupCodec.encodePayload(BackupPayload(createdAt = 1L))
        )
        assertThat(decoded.transcripts).isEmpty()
        assertThat(decoded.memories).isEmpty()
        assertThat(decoded.scheduleRules).isEmpty()
        assertThat(decoded.excludedApps).isEmpty()
    }

    @Test
    fun encrypted_payload_round_trips() {
        val plain = BackupCodec.encodePayload(payload())
        val encrypted = BackupCodec.encrypt(plain, "correct horse battery staple")
        val decrypted = BackupCodec.decrypt(encrypted, "correct horse battery staple")

        assertThat(decrypted).isEqualTo(plain)
        assertThat(encrypted.copyOfRange(0, 4)).isEqualTo("EHB1".toByteArray())
    }

    @Test
    fun wrong_passphrase_is_rejected() {
        val encrypted = BackupCodec.encrypt(
            BackupCodec.encodePayload(payload()),
            "correct horse battery staple"
        )
        assertThrows(BackupDecryptException::class.java) {
            BackupCodec.decrypt(encrypted, "wrong passphrase")
        }
    }

    @Test
    fun tampered_ciphertext_is_rejected() {
        val encrypted = BackupCodec.encrypt(
            BackupCodec.encodePayload(payload()),
            "passphrase"
        )
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        assertThrows(BackupDecryptException::class.java) {
            BackupCodec.decrypt(encrypted, "passphrase")
        }
    }

    @Test
    fun bad_magic_is_rejected() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decrypt(ByteArray(64), "passphrase")
        }
    }

    @Test
    fun newer_payload_version_is_rejected() {
        val json = """{"version":99,"createdAt":1,"transcripts":[],"memories":[]}"""
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decodePayload(json.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun file_name_is_timestamped() {
        assertThat(BackupCodec.fileName(0L, ZoneId.of("UTC")))
            .isEqualTo("everheard-19700101-000000.ehb")
    }
}
