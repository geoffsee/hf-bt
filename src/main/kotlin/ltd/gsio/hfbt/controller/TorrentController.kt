package ltd.gsio.hfbt.controller

import ltd.gsio.hfbt.service.StubCatalogService
import ltd.gsio.hfbt.service.WebseedProxyService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class TorrentController(
    private val catalogService: StubCatalogService,
    private val webseedProxyService: WebseedProxyService
) {
    @GetMapping("/torrents/{namespace}/{name}/{rev}.torrent")
    fun torrent(
        @PathVariable namespace: String,
        @PathVariable name: String,
        @PathVariable rev: String
    ): ResponseEntity<ByteArrayResource> {
        val id = "$namespace/$name"
        val bytes = catalogService.getTorrent(id, rev) ?: return ResponseEntity.notFound().build()
        val resource = ByteArrayResource(bytes)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-bittorrent"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$name-$rev.torrent\"")
            .contentLength(bytes.size.toLong())
            .body(resource)
    }

    @GetMapping("/webseed/{namespace}/{name}/{rev}/{path:.+}")
    fun webseed(
        @PathVariable namespace: String,
        @PathVariable name: String,
        @PathVariable rev: String,
        @PathVariable("path") path: String,
        @RequestHeader(value = "Range", required = false) range: String?
    ): ResponseEntity<ByteArrayResource> {
        val id = "$namespace/$name"
        if (catalogService.getRevision(id, rev) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        if (!webseedProxyService.isEnabled()) {
            val bytes = catalogService.getFile(id, rev, path) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            val resource = ByteArrayResource(bytes)
            val contentType =
                if (path.endsWith(".json")) MediaType.APPLICATION_JSON else MediaType.APPLICATION_OCTET_STREAM
            return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(bytes.size.toLong())
                .body(resource)
        }
        val proxyResponse = webseedProxyService.fetch(id, rev, path, range)
        val resource = ByteArrayResource(proxyResponse.body)
        val responseHeaders = HttpHeaders().apply {
            putAll(proxyResponse.headers)
        }
        return ResponseEntity.status(proxyResponse.status)
            .headers(responseHeaders)
            .body(resource)
    }
}
