package ltd.gsio.hfbt.service

import ltd.gsio.hfbt.config.WebseedProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.util.UriUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.time.Duration

data class WebseedProxyResponse(
    val status: Int,
    val body: ByteArray,
    val headers: HttpHeaders
)

@Service
class WebseedProxyService(
    private val properties: WebseedProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.timeoutMs))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetch(model: String, rev: String, path: String, rangeHeader: String?): WebseedProxyResponse {
        val uri = buildUri(model, rev, path)
        log.debug("Proxying webseed request to {}", uri)

        val requestBuilder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(properties.timeoutMs))
            .GET()
            .header("User-Agent", properties.userAgent)

        rangeHeader?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Range", it)
        }

        val response = httpClient.send(requestBuilder.build(), BodyHandlers.ofByteArray())
        val headers = HttpHeaders()
        response.headers().map().forEach { (name, values) ->
            values.forEach { headers.add(name, it) }
        }

        return WebseedProxyResponse(
            status = response.statusCode(),
            body = response.body(),
            headers = headers
        )
    }

    private fun buildUri(model: String, rev: String, path: String): URI {
        val base = properties.baseUrl.trimEnd('/')
        val modelSegments = model
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { UriUtils.encodePathSegment(it, StandardCharsets.UTF_8) }
        val encodedRev = UriUtils.encodePathSegment(rev, StandardCharsets.UTF_8)
        val encodedPath = UriUtils.encodePath(path, StandardCharsets.UTF_8)
        val fullPath = if (modelSegments.isBlank()) {
            "$base/resolve/$encodedRev/$encodedPath"
        } else {
            "$base/$modelSegments/resolve/$encodedRev/$encodedPath"
        }
        return URI.create(fullPath)
    }

    fun isEnabled(): Boolean = properties.baseUrl.isNotBlank()
}
