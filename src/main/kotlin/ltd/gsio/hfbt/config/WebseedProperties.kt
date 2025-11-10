package ltd.gsio.hfbt.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "webseed")
data class WebseedProperties(
    /**
     * Base URL of the upstream hosting (typically Hugging Face) whose files will be proxied.
     * The proxy builds URLs like {baseUrl}/{namespace}/{name}/resolve/{rev}/{path}.
     */
    var baseUrl: String = "https://huggingface.co",

    /**
     * Timeout applied to the HTTP request in milliseconds.
     */
    var timeoutMs: Long = 10_000,

    /**
     * Optional user agent to send to the upstream service (can be useful for logging/protection).
     */
    var userAgent: String = "hf-bt-webseed-proxy/1.0"
)
