package ltd.gsio.hfbt.peer

/**
 * Abstraction for running a local Peer endpoint (e.g., WebTransport server).
 * Implementations may start an embedded JVM server or launch a sidecar process.
 */
interface PeerProcess {
    /** Start the peer if not running. No-op if already started. */
    fun start()

    /** Stop the peer if running. No-op if already stopped. */
    fun stop()

    /** Whether the peer is currently running. */
    fun isRunning(): Boolean

    /**
     * Returns the public URL for the peer endpoint (e.g., WebTransport URL),
     * or null if not available.
     */
    fun endpointUrl(): String?
}
