package io.github.markyu0401.everheard.data.backup

import android.annotation.SuppressLint
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** One `.ehb` file found in the configured WebDAV folder. */
data class WebDavEntry(
    val name: String,
    val size: Long,
    val lastModified: Long
)

class WebDavException(val statusCode: Int, message: String) : Exception(message)

/**
 * The WebDAV operations the backup manager needs. Extracted so the manager can
 * be unit-tested with a fake gateway.
 */
interface WebDavGateway {
    fun test()
    fun ensureFolder()
    fun put(name: String, bytes: ByteArray)
    fun list(): List<WebDavEntry>
    fun get(name: String): ByteArray
}

/**
 * Minimal WebDAV client for Synology DSM (and any RFC 4918 server).
 *
 * `HttpURLConnection` refuses PROPFIND/MKCOL, so this uses OkHttp. Synology's
 * default WebDAV endpoint is HTTPS with a self-signed certificate; when the
 * user accepts that in Settings, the client trusts the configured host without
 * a system CA. The backup file itself is encrypted with the user's passphrase,
 * but the NAS password travels in a Basic-auth header, so this is a
 * personal-network trade-off, not a public-internet recommendation.
 */
class WebDavClient(
    private val config: NasBackupConfig,
    private val http: OkHttpClient = buildClient(config)
) : WebDavGateway {

    private val baseUrl = config.serverUrl.trimEnd('/')
    private val folder = NasBackupConfig.normalizeFolder(config.folder)
    private val folderUrl = baseUrl + encodePath(folder)

    override fun test() {
        val response = execute(
            method = "PROPFIND",
            url = "$baseUrl/",
            body = propfindBody(),
            headers = mapOf("Depth" to "0")
        )
        if (response.code !in setOf(200, 207)) {
            throw WebDavException(response.code, response.message)
        }
    }

    override fun ensureFolder() {
        val segments = folder.trim('/').split('/').filter { it.isNotEmpty() }
        var path = ""
        for (segment in segments) {
            path += "/$segment"
            val response = execute(
                method = "MKCOL",
                url = baseUrl + encodePath(path),
                body = ByteArray(0).toRequestBody(null)
            )
            // 405 = already exists; anything else outside 2xx is a real error.
            if (response.code !in 200..299 && response.code != 405) {
                throw WebDavException(response.code, response.message)
            }
        }
    }

    override fun put(name: String, bytes: ByteArray) {
        val response = execute(
            method = "PUT",
            url = fileUrl(name),
            body = bytes.toRequestBody("application/octet-stream".toMediaType())
        )
        if (response.code !in setOf(200, 201, 204)) {
            throw WebDavException(response.code, response.message)
        }
    }

    override fun list(): List<WebDavEntry> {
        val response = execute(
            method = "PROPFIND",
            url = "$folderUrl/",
            body = propfindBody(),
            headers = mapOf("Depth" to "1")
        )
        if (response.code !in setOf(200, 207)) {
            throw WebDavException(response.code, response.message)
        }
        return parseMultiStatus(response.text)
    }

    override fun get(name: String): ByteArray {
        val response = execute(
            method = "GET",
            url = fileUrl(name),
            body = null
        )
        if (response.code != 200) {
            throw WebDavException(response.code, response.message)
        }
        return response.bytes
    }

    private fun execute(
        method: String,
        url: String,
        body: RequestBody?,
        headers: Map<String, String> = emptyMap()
    ): WebDavResponse {
        val builder = Request.Builder()
            .url(url)
            .method(method, body)
            .header("Authorization", basicAuth())
            .header("Accept", "*/*")
        headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            http.newCall(builder.build()).execute().use { response ->
                WebDavResponse(
                    code = response.code,
                    message = response.message,
                    bytes = response.body?.bytes() ?: ByteArray(0)
                )
            }
        } catch (error: IOException) {
            throw error
        }
    }

    private fun basicAuth(): String {
        val raw = "${config.username}:${config.password}".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }

    private fun fileUrl(name: String): String = "$folderUrl/${encodeSegment(name)}"

    private fun propfindBody(): RequestBody {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
        return xml.toRequestBody("application/xml; charset=utf-8".toMediaType())
    }

    private fun parseMultiStatus(xml: String): List<WebDavEntry> {
        if (xml.isBlank()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val responses = document.getElementsByTagNameNS("*", "response")
        val entries = mutableListOf<WebDavEntry>()
        for (index in 0 until responses.length) {
            val response = responses.item(index) as? Element ?: continue
            val href = firstText(response, "href") ?: continue
            val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
            if (decoded.endsWith("/")) continue
            val name = decoded.substringAfterLast('/')
            if (!name.endsWith(".${BackupCodec.FILE_EXTENSION}")) continue
            entries += WebDavEntry(
                name = name,
                size = firstText(response, "getcontentlength")?.toLongOrNull() ?: 0L,
                lastModified = parseHttpDate(firstText(response, "getlastmodified"))
            )
        }
        return entries.sortedWith(
            compareByDescending<WebDavEntry> { it.lastModified }.thenByDescending { it.name }
        )
    }

    private fun firstText(parent: Element, localName: String): String? {
        val nodes = parent.getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseHttpDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        return runCatching { format.parse(value)?.time ?: 0L }.getOrDefault(0L)
    }

    private fun encodePath(path: String): String {
        val leading = if (path.startsWith("/")) "/" else ""
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        return leading + segments.joinToString("/") { encodeSegment(it) }
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private data class WebDavResponse(
        val code: Int,
        val message: String,
        val bytes: ByteArray
    ) {
        val text: String
            get() = String(bytes, Charsets.UTF_8)
    }

    // The user explicitly opts in to trusting this NAS's self-signed
    // certificate; the backup file is encrypted, but the Basic-auth password
    // still relies on the user's own network being trustworthy.
    @SuppressLint("TrustAllX509TrustManager")
    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        fun buildClient(config: NasBackupConfig): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
            if (config.trustSelfSigned && config.serverUrl.startsWith("https://")) {
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(TrustAllManager), SecureRandom())
                }
                builder.sslSocketFactory(sslContext.socketFactory, TrustAllManager)
                builder.hostnameVerifier { _, _ -> true }
            }
            return builder.build()
        }
    }
}
