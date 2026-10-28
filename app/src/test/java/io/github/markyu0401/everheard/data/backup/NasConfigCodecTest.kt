package io.github.markyu0401.everheard.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NasConfigCodecTest {

    @Test
    fun config_round_trips() {
        val config = NasBackupConfig(
            serverUrl = "https://nas.example:5006",
            username = "user",
            password = "secret",
            folder = "/home/EverheardBackups",
            passphrase = "backup passphrase",
            trustSelfSigned = false,
            lastBackupAt = 123_456L,
            lastBackupName = "everheard-20261028-010000.ehb"
        )
        val decoded = NasConfigCodec.decode(NasConfigCodec.encode(config))
        assertThat(decoded).isEqualTo(config)
    }

    @Test
    fun blank_or_corrupt_config_decodes_to_defaults() {
        assertThat(NasConfigCodec.decode("")).isEqualTo(NasBackupConfig())
        assertThat(NasConfigCodec.decode("{not json")).isEqualTo(NasBackupConfig())
    }

    @Test
    fun folder_normalization() {
        assertThat(NasBackupConfig.normalizeFolder("")).isEqualTo(NasBackupConfig.DEFAULT_FOLDER)
        assertThat(NasBackupConfig.normalizeFolder("home/Backups/"))
            .isEqualTo("/home/Backups")
        assertThat(NasBackupConfig.normalizeFolder("/")).isEqualTo("/")
    }
}
