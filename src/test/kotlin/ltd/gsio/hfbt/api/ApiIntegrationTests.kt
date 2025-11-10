package ltd.gsio.hfbt.api

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["webseed.base-url="])
class ApiIntegrationTests {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val id = "runwayml/stable-diffusion-v1-5"
    private val rev = "abc123"

    @Test
    fun `catalog endpoint returns items`() {
        mockMvc.get("/api/v1/catalog")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.items", hasSize<Int>(greaterThanOrEqualTo(1)))
                jsonPath("$.items[0].id", `is`(id))
                jsonPath("$.items[0].torrentUrl", notNullValue())
            }
    }

    @Test
    fun `model revision endpoint returns manifest`() {
        mockMvc.get("/api/v1/models/${id}/revs/${rev}")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.id", `is`(id))
                jsonPath("$.rev", `is`(rev))
                jsonPath("$.files", hasSize<Int>(2))
            }
    }

    @Test
    fun `bootstrap endpoint returns peers and trackers`() {
        mockMvc.get("/api/v1/bootstrap") { param("model", id); param("rev", rev) }
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.peers", not(empty<List<*>>()))
                jsonPath("$.trackers", not(empty<List<*>>()))
                jsonPath("$.parameters.peerDegreeK", greaterThan(0))
            }
    }

    @Test
    fun `health endpoint returns swarm metrics`() {
        mockMvc.get("/api/v1/health/swarm") { param("model", id); param("rev", rev) }
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.activePeers", greaterThanOrEqualTo(0))
                jsonPath("$.seeders", greaterThanOrEqualTo(0))
            }
    }

    @Test
    fun `torrent endpoint returns torrent file`() {
        val result = mockMvc.get("/torrents/${id}/${rev}.torrent")
            .andExpect {
                status { isOk() }
                header { string("Content-Type", startsWith("application/x-bittorrent")) }
                header { string("Content-Disposition", containsString("$rev.torrent")) }
            }
            .andReturn()
        val bytes = result.response.contentAsByteArray
        assertThat(bytes.size).isGreaterThan(10)
    }

    @Test
    fun `webseed endpoint returns stub file`() {
        val result = mockMvc.get("/webseed/${id}/${rev}/config.json")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
            }
            .andReturn()
        val body = result.response.contentAsString
        assertThat(body).contains("stub-config")
    }

    @Test
    fun `static UI page is served`() {
        val result = mockMvc.get("/index.html")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith("text/html") }
            }
            .andReturn()
        val html = result.response.contentAsString
        assertThat(html).contains("HF-BT Torrent Catalog")
        assertThat(html).contains("fetchCatalog")
        assertThat(html).contains("/api/v1/catalog")
    }

    @Test
    fun `catalog endpoint returns magnet URIs`() {
        mockMvc.get("/api/v1/catalog")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.items[0].magnetUri", notNullValue())
                jsonPath("$.items[0].magnetUri", startsWith("magnet:"))
            }
    }
}
