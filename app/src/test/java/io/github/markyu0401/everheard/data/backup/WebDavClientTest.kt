package io.github.markyu0401.everheard.data.backup

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class WebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = NasBackupConfig(
            serverUrl = server.url("/").toString().trimEnd('/'),
            username = "user",
            password = "pass",
            folder = "/home/EverheardBackups",
            passphrase = "secret",
            trustSelfSigned = false
        )
        client = WebDavClient(config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun test_sends_a_depth_zero_propfind() {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody("""<D:multistatus xmlns:D="DAV:"/>""")
        )
        client.test()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PROPFIND")
        assertThat(request.path).isEqualTo("/")
        assertThat(request.getHeader("Depth")).isEqualTo("0")
        assertThat(request.getHeader("Authorization")).isEqualTo(
            "Basic " + Base64.getEncoder().encodeToString("user:pass".toByteArray())
        )
    }

    @Test
    fun ensure_folder_tolerates_an_existing_parent() {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(201))

        client.ensureFolder()

        val parent = server.takeRequest()
        assertThat(parent.method).isEqualTo("MKCOL")
        assertThat(parent.path).isEqualTo("/home")
        val folder = server.takeRequest()
        assertThat(folder.method).isEqualTo("MKCOL")
        assertThat(folder.path).isEqualTo("/home/EverheardBackups")
    }

    @Test
    fun put_sends_the_encrypted_bytes() {
        server.enqueue(MockResponse().setResponseCode(201))
        val bytes = byteArrayOf(1, 2, 3, 4)
        client.put("everheard-20261028-010000.ehb", bytes)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/home/EverheardBackups/everheard-20261028-010000.ehb")
        assertThat(request.body.readByteArray()).isEqualTo(bytes)
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/octet-stream")
    }

    @Test
    fun list_parses_and_filters_backup_files() {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <D:multistatus xmlns:D="DAV:">
                      <D:response>
                        <D:href>/home/EverheardBackups/</D:href>
                        <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                      </D:response>
                      <D:response>
                        <D:href>/home/EverheardBackups/everheard-20261028-010000.ehb</D:href>
                        <D:propstat><D:prop>
                          <D:getcontentlength>1234</D:getcontentlength>
                          <D:getlastmodified>Tue, 28 Oct 2026 01:00:00 GMT</D:getlastmodified>
                        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                      </D:response>
                      <D:response>
                        <D:href>/home/EverheardBackups/notes.txt</D:href>
                        <D:propstat><D:prop><D:getcontentlength>10</D:getcontentlength></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
                      </D:response>
                    </D:multistatus>
                    """.trimIndent()
                )
        )

        val entries = client.list()

        assertThat(entries).hasSize(1)
        assertThat(entries.first().name).isEqualTo("everheard-20261028-010000.ehb")
        assertThat(entries.first().size).isEqualTo(1234L)
        assertThat(entries.first().lastModified).isGreaterThan(0L)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/home/EverheardBackups/")
        assertThat(request.getHeader("Depth")).isEqualTo("1")
    }

    @Test
    fun get_returns_the_file_bytes() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ciphertext"))
        assertThat(client.get("x.ehb")).isEqualTo("ciphertext".toByteArray())
    }

    @Test
    fun an_unauthorized_response_is_reported() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        try {
            client.test()
            throw AssertionError("expected WebDavException")
        } catch (error: WebDavException) {
            assertThat(error.statusCode).isEqualTo(401)
        }
    }
}
