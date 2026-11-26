package com.music.bitchord.desktop

import java.awt.EventQueue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

/**
 * Ensures only one BitChord Desktop process runs at a time per user session.
 *
 * When a second instance is launched (from an app menu, desktop shortcut, URI handler,
 * or tray action), it forwards its arguments over localhost IPC to the primary instance
 * and exits before any UI window or audio pipeline is initialized.
 */
internal class DesktopSingleInstance private constructor(
    private val lockPath: Path,
    private val ipcPath: Path,
    private val lockChannel: FileChannel,
    private val fileLock: FileLock,
    private val serverSocket: ServerSocket,
    private val authToken: String,
) : AutoCloseable {

    @Volatile
    private var closed = false

    @Volatile
    private var onShowHandler: () -> Unit = {}

    @Volatile
    private var onActionHandler: (String) -> Unit = {}

    fun bindActions(
        onShow: () -> Unit,
        onAction: (String) -> Unit,
    ) {
        onShowHandler = onShow
        onActionHandler = onAction
    }

    private fun startListening() {
        thread(isDaemon = true, name = "BitChord-SingleInstance") {
            while (!closed && !serverSocket.isClosed) {
                val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                handleClient(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = CLIENT_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val writer = OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)
            val line = reader.readLine().orEmpty()
            val parts = line.split('\t', limit = 2)
            val token = parts.getOrNull(0).orEmpty()
            if (token != authToken) {
                writer.write("ERR\n")
                writer.flush()
                return
            }
            val payload = parts.getOrNull(1).orEmpty().trim()
            dispatchPayload(payload)
            writer.write("OK\n")
            writer.flush()
        } catch (_: Exception) {
            // Ignore malformed or aborted local probes.
        } finally {
            runCatching { client.close() }
        }
    }

    private fun dispatchPayload(payload: String) {
        val args = if (payload.isEmpty() || payload == COMMAND_SHOW) {
            emptyList()
        } else {
            payload.split('\u001F').filter(String::isNotBlank)
        }
        val mediaAction = args.firstOrNull {
            it == "--play-pause" || it == "--next" || it == "--previous"
        }
        if (mediaAction != null) {
            onActionHandler(mediaAction)
            return
        }
        onShowHandler()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { serverSocket.close() }
        runCatching { Files.deleteIfExists(ipcPath) }
        runCatching {
            if (fileLock.isValid) fileLock.release()
        }
        runCatching { lockChannel.close() }
    }

    companion object {
        private const val LOCK_FILE_NAME = "instance.lock"
        private const val IPC_FILE_NAME = "instance.ipc"
        private const val COMMAND_SHOW = "SHOW"
        private const val FORWARD_RETRIES = 10
        private const val FORWARD_RETRY_DELAY_MS = 80L
        private const val CLIENT_TIMEOUT_MS = 1_500

        @Volatile
        private var activeInstance: DesktopSingleInstance? = null

        @Volatile
        private var pendingShow: (() -> Unit)? = null

        @Volatile
        private var pendingAction: ((String) -> Unit)? = null

        sealed class Acquisition {
            data class Primary(val instance: DesktopSingleInstance) : Acquisition()
            data object Forwarded : Acquisition()
            data object Failed : Acquisition()
        }

        fun defaultStateDirectory(): Path {
            return when {
                DesktopPlatform.isWindows -> {
                    val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
                        ?: "${System.getProperty("user.home")}\\AppData\\Local"
                    Path.of(base, "BitChord")
                }
                DesktopPlatform.isMac -> {
                    Path.of(System.getProperty("user.home"), "Library", "Application Support", "BitChord")
                }
                else -> {
                    val state = System.getenv("XDG_STATE_HOME")?.takeIf(String::isNotBlank)
                        ?: "${System.getProperty("user.home")}/.local/state"
                    Path.of(state, "bitchord")
                }
            }
        }

        /**
         * Acquires the primary instance lock or forwards [args] to an already running instance.
         * Returns true if this process is primary (or lock couldn't be established safely),
         * or false if forwarded to an active instance and this process should exit.
         */
        fun acquireOrForward(args: Array<String>): Boolean {
            return when (val result = acquire(defaultStateDirectory(), args)) {
                is Acquisition.Primary -> {
                    val instance = result.instance
                    activeInstance = instance
                    instance.bindActions(
                        onShow = { pendingShow?.invoke() ?: DesktopWindowVisibility.show() },
                        onAction = { cmd -> pendingAction?.invoke(cmd) },
                    )
                    Runtime.getRuntime().addShutdownHook(
                        Thread { release() }.apply { name = "BitChord-SingleInstance-Shutdown" },
                    )
                    true
                }
                Acquisition.Forwarded -> false
                Acquisition.Failed -> true
            }
        }

        fun bindUiActions(
            onShow: () -> Unit,
            onAction: (String) -> Unit,
        ) {
            pendingShow = onShow
            pendingAction = onAction
            activeInstance?.bindActions(onShow, onAction)
        }

        fun release() {
            val current = activeInstance ?: return
            activeInstance = null
            current.close()
        }

        internal fun acquire(
            stateDir: Path,
            args: Array<String>,
        ): Acquisition {
            runCatching { Files.createDirectories(stateDir) }
            val lockPath = stateDir.resolve(LOCK_FILE_NAME)
            val ipcPath = stateDir.resolve(IPC_FILE_NAME)

            val channel = runCatching {
                FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            }.getOrNull() ?: return Acquisition.Failed

            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (_: Exception) {
                null
            }

            if (lock == null) {
                runCatching { channel.close() }
                if (forwardWithRetry(ipcPath, args)) {
                    return Acquisition.Forwarded
                }
                // Retry lock once in case the previous holder was mid-shutdown.
                val retryChannel = runCatching {
                    FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
                }.getOrNull() ?: return Acquisition.Failed
                val retryLock = runCatching { retryChannel.tryLock() }.getOrNull()
                if (retryLock == null) {
                    runCatching { retryChannel.close() }
                    return Acquisition.Failed
                }
                return startPrimary(lockPath, ipcPath, retryChannel, retryLock)
            }

            return startPrimary(lockPath, ipcPath, channel, lock)
        }

        private fun startPrimary(
            lockPath: Path,
            ipcPath: Path,
            channel: FileChannel,
            lock: FileLock,
        ): Acquisition {
            val server = runCatching {
                ServerSocket(0, 8, InetAddress.getLoopbackAddress())
            }.getOrElse {
                runCatching { lock.release() }
                runCatching { channel.close() }
                return Acquisition.Failed
            }

            val tokenBytes = ByteArray(18)
            SecureRandom().nextBytes(tokenBytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)

            val wroteIpc = runCatching {
                Files.writeString(
                    ipcPath,
                    "${server.localPort}\n$token\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }.isSuccess

            if (!wroteIpc) {
                runCatching { server.close() }
                runCatching { lock.release() }
                runCatching { channel.close() }
                return Acquisition.Failed
            }

            val instance = DesktopSingleInstance(
                lockPath = lockPath,
                ipcPath = ipcPath,
                lockChannel = channel,
                fileLock = lock,
                serverSocket = server,
                authToken = token,
            )
            instance.startListening()
            return Acquisition.Primary(instance)
        }

        private fun forwardWithRetry(ipcPath: Path, args: Array<String>): Boolean {
            val payload = if (args.isEmpty()) {
                COMMAND_SHOW
            } else {
                args.joinToString("\u001F")
            }

            repeat(FORWARD_RETRIES) { attempt ->
                val info = readIpcInfo(ipcPath)
                if (info != null) {
                    val (port, token) = info
                    val sent = runCatching {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CLIENT_TIMEOUT_MS)
                            socket.soTimeout = CLIENT_TIMEOUT_MS
                            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                            writer.write("$token\t$payload\n")
                            writer.flush()
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                            reader.readLine() == "OK"
                        }
                    }.getOrDefault(false)
                    if (sent) return true
                }
                if (attempt < FORWARD_RETRIES - 1) {
                    runCatching { Thread.sleep(FORWARD_RETRY_DELAY_MS) }
                }
            }
            return false
        }

        private fun readIpcInfo(ipcPath: Path): Pair<Int, String>? {
            val lines = runCatching {
                Files.readAllLines(ipcPath, StandardCharsets.UTF_8)
            }.getOrNull() ?: return null
            if (lines.size < 2) return null
            val port = lines[0].trim().toIntOrNull() ?: return null
            val token = lines[1].trim()
            if (port !in 1..65535 || token.isBlank()) return null
            return port to token
        }

        internal fun runOnUiThread(block: () -> Unit) {
            if (EventQueue.isDispatchThread()) {
                block()
            } else {
                EventQueue.invokeLater(block)
            }
        }
    }
}
