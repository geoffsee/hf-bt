package ltd.gsio.hfbt.service

import ltd.gsio.hfbt.config.DemoPeerProperties
import ltd.gsio.hfbt.model.*
import org.springframework.stereotype.Service

@Service
class StubCatalogService(
    private val demoPeer: DemoPeerProperties,
    private val peerProcess: ltd.gsio.hfbt.peer.PeerProcess
) {
    init {
        if (demoPeer.enabled) {
            try {
                peerProcess.start()
            } catch (t: Throwable) {
                // Best-effort: demo mode should not break the app
            }
        }
    }
    private val sampleModelId = "runwayml/stable-diffusion-v1-5"
    private val sampleRev = "abc123"

    private val files = listOf(
        FileEntry(path = "model.safetensors", sizeBytes = 1024L, sha256 = "deadbeef"),
        FileEntry(path = "config.json", sizeBytes = 64, sha256 = "beadfeed")
    )

    private val fileContents: Map<String, ByteArray> = mapOf(
        "model.safetensors" to ByteArray(1024) { 0x42 },
        "config.json" to "{\"_\":\"stub-config\"}".toByteArray()
    )

    private val torrentBytes: ByteArray = "d8:announce13:stub-tracker12:created by12:StubCataloge".toByteArray()

    private val modelRevision = ModelRevision(
        id = sampleModelId,
        rev = sampleRev,
        files = files,
        manifestSignature = null,
        torrentUrl = "/torrents/$sampleModelId/$sampleRev.torrent",
        magnetUri = "magnet:?xt=urn:btih:example"
    )

    fun catalog(query: String?): CatalogResponse {
        val item = CatalogItem(
            id = sampleModelId,
            latestRev = sampleRev,
            sizeBytes = files.sumOf { it.sizeBytes },
            torrentUrl = modelRevision.torrentUrl,
            magnetUri = modelRevision.magnetUri
        )
        val list = if (query.isNullOrBlank()) listOf(item) else listOf(item).filter { it.id.contains(query, ignoreCase = true) }
        return CatalogResponse(items = list)
    }

    fun getRevision(id: String, rev: String): ModelRevision? {
        return if (id == sampleModelId && rev == sampleRev) modelRevision else null
    }

    fun bootstrap(id: String, rev: String): BootstrapResponse? {
        if (getRevision(id, rev) == null) return null
        val basePeers = mutableListOf(
            PeerEndpoint(url = "https://peer1.hswarm.net:443/.well-known/webtransport", peerId = "peer-1"),
            PeerEndpoint(url = "https://peer2.hswarm.net:443/.well-known/webtransport", peerId = "peer-2")
        )
        if (demoPeer.enabled) {
            val runtimeUrl = peerProcess.endpointUrl()
            val url = runtimeUrl ?: demoPeer.url
            if (!url.isNullOrBlank()) {
                basePeers.add(PeerEndpoint(url = url, peerId = "demo-local"))
            }
        }
        return BootstrapResponse(
            modelId = id,
            rev = rev,
            peers = basePeers,
            trackers = listOf(
                "udp://tracker.opentrackr.org:1337/announce",
                "udp://tracker.torrent.eu.org:451/announce"
            ),
            relays = listOf(
                RelayInfo(url = "https://relay-us.hswarm.net", region = "us"),
                RelayInfo(url = "https://relay-eu.hswarm.net", region = "eu")
            ),
            parameters = BootstrapParameters()
        )
    }

    fun health(id: String, rev: String): SwarmHealth? {
        if (getRevision(id, rev) == null) return null
        return SwarmHealth(
            modelId = id,
            rev = rev,
            activePeers = 12,
            seeders = 5,
            avgRttMs = 120.5,
            availability = 0.99
        )
    }

    fun getTorrent(id: String, rev: String): ByteArray? {
        if (getRevision(id, rev) == null) return null
        return torrentBytes
    }

    fun getFile(id: String, rev: String, path: String): ByteArray? {
        if (getRevision(id, rev) == null) return null
        return fileContents[path]
    }
}
