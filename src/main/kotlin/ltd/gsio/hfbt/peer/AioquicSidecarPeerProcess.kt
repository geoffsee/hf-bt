package ltd.gsio.hfbt.peer

import ltd.gsio.hfbt.config.DemoPeerProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import jakarta.annotation.PreDestroy

private const val READY_TIMEOUT_MS = 5_000L

/**
 * Demo implementation that launches the aioquic-based peer sidecar process.
 * It keeps the process alive while the application context is running, retries on failures,
 * and provides the dynamically allocated WebTransport endpoint URL when available.
 */
@Component
@ConditionalOnProperty(prefix = "demo.peer", name = ["impl"], havingValue = "aioquic")
class AioquicSidecarPeerProcess(
    private val demoPeer: DemoPeerProperties
) : PeerProcess {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lifecycleLock = Any()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "peer-sidecar-launcher").apply { isDaemon = true }
    }

    @Volatile
    private var process: Process? = null

    @Volatile
    private var endpoint: String? = null

    @Volatile
    private var logReader: Thread? = null

    @Volatile
    private var startFuture: Future<*>? = null

    override fun start() {
        if (!demoPeer.enabled) {
            log.debug("Demo peer disabled; skipping aioquic sidecar startup")
            return
        }
        synchronized(lifecycleLock) {
            if (startFuture?.isDone == false) {
                log.debug("Peer sidecar already starting")
                return
            }
            startFuture = executor.submit {
                try {
                    startWithRetries()
                } finally {
                    synchronized(lifecycleLock) {
                        startFuture = null
                    }
                }
            }
        }
    }

    private fun startWithRetries() {
        var attempt = 1
        val maxAttempts = maxOf(1, demoPeer.startRetries)
        while (attempt <= maxAttempts) {
            try {
                spawnSidecar()
                return
            } catch (t: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Sidecar startup interrupted", t)
                return
            } catch (t: Throwable) {
                log.warn("Sidecar launch attempt {} failed", attempt, t)
                if (attempt == maxAttempts) {
                    log.error("Giving up on starting aioquic sidecar after {} failed attempts", attempt)
                    break
                }
                Thread.sleep(demoPeer.startBackoffMs * attempt)
                attempt++
            }
        }
    }

    private fun spawnSidecar() {
        val scriptPath = resolveScriptPath()
        val host = demoPeer.host.ifBlank { "127.0.0.1" }
        val port = demoPeer.port ?: findFreePort()
        val endpointUrl = "https://$host:$port/.well-known/webtransport"

        val command = listOf(demoPeer.pythonCommand, scriptPath.toString())
        val builder = ProcessBuilder(command)
            .directory(scriptPath.parent?.toFile() ?: File("."))
            .redirectErrorStream(true)

        val env = builder.environment()
        env["PEER_HOST"] = host
        env["PEER_PORT"] = port.toString()
        demoPeer.certPath?.takeIf { it.isNotBlank() }?.let { env["PEER_CERT"] = it }
        demoPeer.keyPath?.takeIf { it.isNotBlank() }?.let { env["PEER_KEY"] = it }

        log.info("Starting aioquic peer sidecar ({}:{}). Script={}", host, port, scriptPath)
        val startedProcess = builder.start()
        val latch = CountDownLatch(1)
        val readerThread = startLogReader(startedProcess, latch)

        if (!latch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            log.warn("Sidecar did not announce readiness within ${READY_TIMEOUT_MS}ms")
        }

        if (!startedProcess.isAlive) {
            readerThread.interrupt()
            throw IllegalStateException("Sidecar process exited before becoming ready")
        }

        synchronized(lifecycleLock) {
            process?.destroyForcibly()
            process = startedProcess
            endpoint = endpointUrl
            logReader = readerThread
        }
        log.info("Sidecar is running and reachable at {}", endpointUrl)
    }

    private fun startLogReader(process: Process, latch: CountDownLatch): Thread {
        val thread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    log.debug("[peer-sidecar] {}", line)
                    if (line.contains("Listening on https://")) {
                        latch.countDown()
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        return thread
    }

    private fun resolveScriptPath(): Path {
        val configured = demoPeer.scriptPath
        val path = Paths.get(configured).toAbsolutePath().normalize()
        if (!Files.exists(path)) {
            throw IllegalStateException("Peer sidecar script not found at $path")
        }
        return path
    }

    private fun findFreePort(): Int =
        ServerSocket(0).use { it.localPort }

    override fun stop() {
        synchronized(lifecycleLock) {
            startFuture?.cancel(true)
            startFuture = null
            logReader?.interrupt()
            logReader = null

            process?.let {
                if (it.isAlive) {
                    it.destroy()
                    it.waitFor(2, TimeUnit.SECONDS)
                }
                if (it.isAlive) {
                    it.destroyForcibly()
                }
            }
            process = null
            endpoint = null
        }
    }

    @PreDestroy
    fun destroy() {
        stop()
        executor.shutdownNow()
    }

    override fun isRunning(): Boolean = process?.isAlive == true

    override fun endpointUrl(): String? = endpoint
}
