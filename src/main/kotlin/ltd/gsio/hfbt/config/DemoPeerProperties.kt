package ltd.gsio.hfbt.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "demo.peer")
data class DemoPeerProperties(
    var enabled: Boolean = false,
    var url: String? = null,
    var impl: String = "jetty",
    var host: String = "127.0.0.1",
    var port: Int? = null,
    var scriptPath: String = "scripts/peer_sidecar.py",
    var certPath: String? = null,
    var keyPath: String? = null,
    var pythonCommand: String = "python3",
    var startRetries: Int = 3,
    var startBackoffMs: Long = 1_000
)
