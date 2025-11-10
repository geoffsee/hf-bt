package ltd.gsio.hfbt.controller

import ltd.gsio.hfbt.model.CatalogResponse
import ltd.gsio.hfbt.service.StubCatalogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class CatalogController(
    private val catalogService: StubCatalogService
) {
    @GetMapping("/catalog")
    fun catalog(@RequestParam(required = false) query: String?): ResponseEntity<CatalogResponse> =
        ResponseEntity.ok(catalogService.catalog(query))
}