package ltd.gsio.hfbt.peer

import ltd.gsio.hfbt.config.DemoPeerProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Default PeerProcess that does not start any real peer.
 * It simply exposes the configured demo peer URL when enabled, otherwise null.
 */
@ConditionalOnProperty(
    prefix = "demo.peer",
    name = ["impl"],
    havingValue = "jetty",
    matchIfMissing = true
)
@Component
class NoopPeerProcess(
    private val demoPeer: DemoPeerProperties
) : PeerProcess {
    override fun start() {
        // no-op: nothing to start in default mode
    }

    override fun stop() {
        // no-op
    }

    override fun isRunning(): Boolean = demoPeer.enabled && !demoPeer.url.isNullOrBlank()

    override fun endpointUrl(): String? = if (demoPeer.enabled) demoPeer.url else null
}
