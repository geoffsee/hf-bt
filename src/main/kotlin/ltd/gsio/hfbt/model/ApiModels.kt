package ltd.gsio.hfbt.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CatalogItem(
    val id: String,
    val latestRev: String?,
    val sizeBytes: Long?,
    val torrentUrl: String?,
    val magnetUri: String?
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CatalogResponse(
    val items: List<CatalogItem>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FileEntry(
    val path: String,
    val sizeBytes: Long,
    val sha256: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ModelRevision(
    val id: String,
    val rev: String,
    val files: List<FileEntry>,
    val manifestSignature: String? = null,
    val torrentUrl: String? = null,
    val magnetUri: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BootstrapResponse(
    val modelId: String,
    val rev: String,
    val peers: List<PeerEndpoint>,
    val trackers: List<String>,
    val relays: List<RelayInfo>,
    val parameters: BootstrapParameters
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PeerEndpoint(
    val url: String,
    val peerId: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RelayInfo(
    val url: String,
    val region: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BootstrapParameters(
    val peerDegreeK: Int = 48,
    val redundantProvidersN: Int = 2,
    val bootstrapRefreshSeconds: Int = 30
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SwarmHealth(
    val modelId: String,
    val rev: String,
    val activePeers: Int,
    val seeders: Int,
    val avgRttMs: Double?,
    val availability: Double?
)
