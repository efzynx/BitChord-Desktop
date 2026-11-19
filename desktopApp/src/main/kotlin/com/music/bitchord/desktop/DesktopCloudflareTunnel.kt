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
import java.net.Socket

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

    fun start(port: Int = 8080, onUrlAssigned: ((String) -> Unit)? = null) {
        if (_state.value.status == Status.STARTING || _state.value.status == Status.RUNNING) return
        _state.value = State(Status.STARTING, port = port)

        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                // 1. Ensure local party server is running on the target port
                if (!isPortInUse(port)) {
                    val backendDir = File("backend")
                    if (backendDir.isDirectory) {
                        val goExe = findExecutable("go")
                        val pb = if (File(backendDir, "party-server").canExecute()) {
                            ProcessBuilder("./party-server")
                                .directory(backendDir)
                        } else if (goExe != null) {
                            ProcessBuilder(goExe, "run", ".")
                                .directory(backendDir)
                        } else {
                            null
                        }
                        if (pb != null) {
                            pb.environment()["PORT"] = port.toString()
                            pb.redirectErrorStream(true)
                            serverProcess = pb.start()
                        }
                    }

                    // Poll for port readiness (up to 8 seconds)
                    var ready = false
                    for (i in 0 until 40) {
                        if (isPortInUse(port)) {
                            ready = true
                            break
                        }
                        delay(200)
                    }
                    if (!ready && !isPortInUse(port)) {
                        // Port might still be starting, continue to launch tunnel
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
