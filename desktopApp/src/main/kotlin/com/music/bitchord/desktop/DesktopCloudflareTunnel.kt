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

    fun findExecutable(name: String = "cloudflared"): String? {
        val pathDirs = (System.getenv("PATH") ?: "")
            .split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
        val names = if (DesktopPlatform.isWindows) listOf("$name.exe", name) else listOf(name)
        for (dir in pathDirs) {
            for (n in names) {
                val f = File(dir, n)
                if (f.isFile && (f.canExecute() || DesktopPlatform.isWindows)) return f.absolutePath
            }
        }
        val common = if (DesktopPlatform.isWindows) {
            val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val progFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val localAppData = System.getenv("LOCALAPPDATA")
            val userProfile = System.getenv("USERPROFILE")
            val choco = System.getenv("ChocolateyInstall") ?: "C:\\ProgramData\\chocolatey"
            listOfNotNull(
                "$progFiles\\cloudflared\\$name.exe",
                "$progFiles\\cloudflared\\bin\\$name.exe",
                "$progFilesX86\\cloudflared\\$name.exe",
                "$progFilesX86\\cloudflared\\bin\\$name.exe",
                localAppData?.let { "$it\\Microsoft\\WinGet\\Links\\$name.exe" },
                localAppData?.let { "$it\\Programs\\cloudflared\\$name.exe" },
                userProfile?.let { "$it\\scoop\\shims\\$name.exe" },
                "$choco\\bin\\$name.exe",
            )
        } else {
            val home = System.getProperty("user.home") ?: ""
            listOf(
                "/usr/bin/$name",
                "/usr/local/bin/$name",
                "/opt/homebrew/bin/$name",
                "/bin/$name",
                "$home/.local/bin/$name",
                "$home/bin/$name",
            )
        }
        for (c in common) {
            val f = File(c)
            if (f.isFile && (f.canExecute() || DesktopPlatform.isWindows)) return f.absolutePath
        }
        return null
    }

    private val _isAvailable = MutableStateFlow(findExecutable() != null)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    fun refreshAvailable(): Boolean {
        val available = findExecutable() != null
        _isAvailable.value = available
        return available
    }

    val installedPath: String?
        get() = findExecutable()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
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
        if (!refreshAvailable()) {
            _state.value = State(
                Status.ERROR,
                error = "cloudflared CLI belum terpasang di sistem ini. Harap pasang cloudflared terlebih dahulu.",
                port = port,
            )
            return
        }
        if (_state.value.status == Status.STARTING || _state.value.status == Status.RUNNING) return
        _state.value = State(Status.STARTING, port = port)

        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                // 1. Ensure local party server is running on an active port (try preferred, 8000, or 8080)
                val actualPort = when {
                    isLocalHealthOk(port) -> port
                    isLocalHealthOk(8000) -> 8000
                    isLocalHealthOk(8080) -> 8080
                    else -> port
                }

                if (!isLocalHealthOk(actualPort)) {
                    val backendDir = listOf(
                        File("backend"),
                        File(System.getProperty("user.dir"), "backend"),
                        File(System.getProperty("user.home"), "Project/BitChord-Desktop/backend"),
                    ).firstOrNull { it.isDirectory }

                    val serverBinary = backendDir?.let {
                        File(it, if (DesktopPlatform.isWindows) "party-server.exe" else "party-server")
                    } ?: findExecutable("party-server")?.let { File(it) }

                    val goExe = findExecutable("go")

                    val pb = when {
                        serverBinary != null && serverBinary.canExecute() -> {
                            ProcessBuilder(serverBinary.absolutePath)
                                .directory(backendDir ?: serverBinary.parentFile)
                        }
                        backendDir != null && goExe != null -> {
                            ProcessBuilder(goExe, "run", ".")
                                .directory(backendDir)
                        }
                        else -> null
                    }

                    if (pb != null) {
                        pb.environment()["PORT"] = actualPort.toString()
                        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
                        serverProcess = pb.start()
                    }

                    // Poll for local server readiness (up to 8 seconds)
                    for (i in 0 until 40) {
                        if (isLocalHealthOk(actualPort)) break
                        delay(200)
                    }

                    if (!isLocalHealthOk(actualPort)) {
                        _state.value = State(Status.ERROR, error = "Local party server failed to start on port $actualPort", port = actualPort)
                        return@launch
                    }
                }

                // 2. Launch cloudflared quick tunnel
                val cloudflaredExe = findExecutable("cloudflared") ?: "cloudflared"
                val pbTunnel = ProcessBuilder(
                    cloudflaredExe,
                    "tunnel",
                    "--url",
                    "http://127.0.0.1:$actualPort",
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

                if (capturedUrl == null) {
                    _state.value = State(Status.ERROR, error = "Failed to obtain trycloudflare.com URL", port = actualPort)
                    proc.destroy()
                    return@launch
                }

                // Continuously drain tunnel process stdout/stderr so pipe buffer never fills and freezes cloudflared
                val drainJob = scope.launch(Dispatchers.IO) {
                    runCatching {
                        val buf = CharArray(2048)
                        while (proc.isAlive && reader.read(buf) != -1) {
                            // drain
                        }
                    }
                }

                // 3. Set negative DNS cache TTL to 1 second so JVM doesn't cache transient NXDOMAIN
                runCatching {
                    java.security.Security.setProperty("networkaddress.cache.negative.ttl", "1")
                }

                // 4. Wait up to 20 seconds for Cloudflare edge routing to be live
                for (attempt in 0 until 20) {
                    val ok = runCatching {
                        val conn = URI("$capturedUrl/healthz").toURL().openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) BitChord/1.9")
                        conn.connectTimeout = 2000
                        conn.readTimeout = 2000
                        if (conn.responseCode in 200..299) {
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            body.contains("\"ok\":true")
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                    if (ok) break
                    delay(1000)
                }

                _state.value = State(Status.RUNNING, url = capturedUrl, port = actualPort)
                onUrlAssigned?.invoke(capturedUrl)

                // 5. Monitor tunnel lifetime
                while (proc.isAlive) {
                    delay(1500)
                }
                drainJob.cancel()
                if (_state.value.status == Status.RUNNING) {
                    _state.value = State(Status.STOPPED, port = actualPort)
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
