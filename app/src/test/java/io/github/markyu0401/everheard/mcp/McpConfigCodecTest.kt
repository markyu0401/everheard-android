package io.github.markyu0401.everheard.mcp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class McpConfigCodecTest {

    @Test
    fun round_trips_enabled_port_and_token() {
        val config = McpConfig(enabled = true, port = 9123, token = "abc123")
        val decoded = McpConfigCodec.decode(McpConfigCodec.encode(config))

        assertThat(decoded).isEqualTo(config)
    }

    @Test
    fun invalid_port_falls_back_to_default() {
        val decoded = McpConfigCodec.decode("""{"enabled":true,"port":80,"token":"t"}""")
        assertThat(decoded.port).isEqualTo(McpConfig.DEFAULT_PORT)
    }

    @Test
    fun generated_tokens_are_unique_and_url_safe() {
        val first = McpConfigStore.newToken()
        val second = McpConfigStore.newToken()
        assertThat(first).isNotEmpty()
        assertThat(second).isNotEmpty()
        assertThat(first).isNotEqualTo(second)
        assertThat(first).matches("[A-Za-z0-9_-]+")
    }

    @Test
    fun malformed_json_is_unconfigured() {
        assertThat(McpConfigCodec.decode("not json")).isEqualTo(McpConfig())
    }
}
