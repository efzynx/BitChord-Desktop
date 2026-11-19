package com.music.bitchord.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI

/**
 * Manages an ephemeral Cloudflare Quick Tunnel and embedded party server
 * to enable zero-configuration Listen Together hosting over the internet.
 */
internal object DesktopCloudflareTunnel {

    enum class Status {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR,
    }

    data class State(
        val status: Status = Status.STOPPED,
        val url: String? = null,
        val error: String? = null,
        val port: Int = 8080,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var tunnelProcess: Process? = null
    private var serverProcess: Process? = null
    private var monitorJob: Job? = null

    val isAvailable: Boolean by lazy {
        findExecutable("cloudflared") != null
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
    }

    private fun findExecutable(name: String): String? {
        val pathDirs = (System.getenv("PATH") ?: "")
            .split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
        val names = if (DesktopPlatform.isWindows) listOf("$name.exe", name) else listOf(name)
        for (dir in pathDirs) {
            for (n in names) {
                val f = File(dir, n)
                if (f.isFile && f.canExecute()) return f.absolutePath
            }
        }
        val common = listOf(
            "/usr/bin/$name",
            "/usr/local/bin/$name",
            "/opt/homebrew/bin/$name",
            "/bin/$name",
        )
        for (c in common) {
            val f = File(c)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        return null
    }

    private fun isPortInUse(port: Int): Boolean = runCatching {
        Socket("127.0.0.1", port).use { true }
    }.getOrDefault(false)

    private fun isLocalHealthOk(port: Int): Boolean = runCatching {
        val conn = URI("http://127.0.0.1:$port/healthz").toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 1200
        conn.readTimeout = 1200
        conn.responseCode in 200..299
    }.getOrDefault(false)

    fun start(port: Int = 8080, onUrlAssigned: ((String) -> Unit)? = null) {
        if (_state.value.status == Status.STARTING || _state.value.status == Status.RUNNING) return
        _state.value = State(Status.STARTING, port = port)

        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                // 1. Ensure local party server is running on the target port
                if (!isLocalHealthOk(port)) {
                    val backendDir = listOf(
                        File("backend"),
                        File(System.getProperty("user.dir"), "backend"),
                        File(System.getProperty("user.home"), "Project/BitChord-Desktop/backend"),
                    ).firstOrNull { it.isDirectory }

                    if (backendDir != null) {
                        val serverBinary = File(backendDir, if (DesktopPlatform.isWindows) "party-server.exe" else "party-server")
                        val goExe = findExecutable("go")

                        val pb = when {
                            serverBinary.canExecute() -> {
                                ProcessBuilder(serverBinary.absolutePath)
                                    .directory(backendDir)
                            }
                            goExe != null -> {
                                ProcessBuilder(goExe, "run", ".")
                                    .directory(backendDir)
                            }
                            else -> null
                        }

                        if (pb != null) {
                            pb.environment()["PORT"] = port.toString()
                            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
                            serverProcess = pb.start()
                        }
                    }

                    // Poll for local server readiness (up to 8 seconds)
                    for (i in 0 until 40) {
                        if (isLocalHealthOk(port)) break
                        delay(200)
                    }
                }

                // 2. Launch cloudflared quick tunnel
                val cloudflaredExe = findExecutable("cloudflared") ?: "cloudflared"
                val pbTunnel = ProcessBuilder(
                    cloudflaredExe,
                    "tunnel",
                    "--url",
                    "http://127.0.0.1:$port",
                    "--no-autoupdate",
                ).redirectErrorStream(true)

                val proc = pbTunnel.start()
                tunnelProcess = proc

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val regex = Regex("""https://[a-zA-Z0-9-]+\.trycloudflare\.com""")
                var capturedUrl: String? = null

                while (proc.isAlive) {
                    val line = reader.readLine() ?: break
                    val match = regex.find(line)
                    if (match != null) {
                        capturedUrl = match.value
                        break
                    }
                }

                if (capturedUrl != null) {
                    // 3. Wait up to 10 seconds for Cloudflare edge routing to be live
                    for (attempt in 0 until 10) {
                        val ok = runCatching {
                            val conn = URI("$capturedUrl/healthz").toURL().openConnection() as HttpURLConnection
                            conn.connectTimeout = 2000
                            conn.readTimeout = 2000
                            conn.responseCode in 200..299
                        }.getOrDefault(false)
                        if (ok) break
                        delay(1000)
                    }

                    _state.value = State(Status.RUNNING, url = capturedUrl, port = port)
                    onUrlAssigned?.invoke(capturedUrl)
                } else {
                    _state.value = State(Status.ERROR, error = "Failed to obtain trycloudflare.com URL", port = port)
                }
            } catch (e: Exception) {
                _state.value = State(Status.ERROR, error = e.message ?: "Tunnel failed to start", port = port)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            tunnelProcess?.destroy()
            tunnelProcess = null
        } catch (_: Exception) {}
        try {
            serverProcess?.destroy()
            serverProcess = null
        } catch (_: Exception) {}
        _state.value = State(Status.STOPPED)
    }
}
