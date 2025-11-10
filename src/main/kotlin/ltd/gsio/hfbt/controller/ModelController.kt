package ltd.gsio.hfbt.controller

import ltd.gsio.hfbt.model.BootstrapResponse
import ltd.gsio.hfbt.model.ModelRevision
import ltd.gsio.hfbt.model.SwarmHealth
import ltd.gsio.hfbt.service.StubCatalogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ModelController(
    private val catalogService: StubCatalogService
) {

    @GetMapping("/models/{namespace}/{name}/revs/{rev}")
    fun getModelRevision(
        @PathVariable namespace: String,
        @PathVariable name: String,
        @PathVariable rev: String
    ): ResponseEntity<ModelRevision> {
        val id = "$namespace/$name"
        return catalogService.getRevision(id, rev)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/bootstrap")
    fun bootstrap(
        @RequestParam("model") id: String,
        @RequestParam("rev") rev: String
    ): ResponseEntity<BootstrapResponse> =
        catalogService.bootstrap(id, rev)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @GetMapping("/health/swarm")
    fun health(
        @RequestParam("model") id: String,
        @RequestParam("rev") rev: String
    ): ResponseEntity<SwarmHealth> =
        catalogService.health(id, rev)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}