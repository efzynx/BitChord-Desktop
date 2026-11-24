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
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
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
        val notice: String? = null,
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

    /**
     * Checks if [candidateUrl] matches the active Cloudflare Tunnel URL currently hosted on this machine.
     */
    fun isLocalTunnelUrl(candidateUrl: String?): Boolean {
        if (candidateUrl.isNullOrBlank()) return false
        val activeUrl = _state.value.url ?: return false
        val cleanCandidate = candidateUrl.trim().trimEnd('/')
        val cleanActive = activeUrl.trim().trimEnd('/')
        return cleanCandidate.equals(cleanActive, ignoreCase = true)
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
    }

    /**
     * Checks if a port is currently available to bind on both 0.0.0.0 and 127.0.0.1.
     */
    fun isPortAvailable(port: Int): Boolean {
        if (port !in 1024..65535) return false
        val bindAll = runCatching {
            ServerSocket().use { s ->
                s.reuseAddress = true
                s.bind(InetSocketAddress("0.0.0.0", port))
            }
        }.isSuccess
        val bindLoopback = runCatching {
            ServerSocket().use { s ->
                s.reuseAddress = true
                s.bind(InetSocketAddress("127.0.0.1", port))
            }
        }.isSuccess
        return bindAll && bindLoopback
    }

    /**
     * Probes the party server's /healthz endpoint locally using NO_PROXY.
     * Ensures both HTTP success (200..299) and proper BitChord party server signature ("ok":true).
     */
    fun isLocalHealthOk(port: Int): Boolean = runCatching {
        val conn = URI("http://127.0.0.1:$port/healthz").toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        conn.connectTimeout = 800
        conn.readTimeout = 800
        conn.instanceFollowRedirects = false
        if (conn.responseCode in 200..299) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            body.contains("\"ok\":true")
        } else {
            false
        }
    }.getOrDefault(false)

    /**
     * Determines the optimal port to run the party server.
     * 1. If an existing healthy party server is already active on a candidate port, reuses it.
     * 2. Otherwise finds the first free port available for binding.
     * Returns Pair(resolvedPort, wasPortSwitched).
     */
    fun resolveServerPort(preferredPort: Int = 8080): Pair<Int, Boolean> {
        val candidates = listOf(preferredPort, 8080, 8000, 8081, 8082, 8088, 8888, 9090, 9091, 18080).distinct()

        // 1. If party server is already running & healthy on any candidate port, reuse it immediately
        for (candidate in candidates) {
            if (isLocalHealthOk(candidate)) {
                return candidate to (candidate != preferredPort)
            }
        }

        // 2. If preferred port is fully available to bind, use it
        if (isPortAvailable(preferredPort)) {
            return preferredPort to false
        }

        // 3. Preferred port is busy with another service: auto-detect and switch to another free port
        for (candidate in candidates) {
            if (candidate != preferredPort && isPortAvailable(candidate)) {
                return candidate to true
            }
        }

        // 4. Fallback: ask the OS to allocate an ephemeral free port
        val ephemeral = runCatching {
            ServerSocket(0).use { it.localPort }
        }.getOrDefault(preferredPort)
        return ephemeral to (ephemeral != preferredPort)
    }

    /**
     * Thoroughly searches for the party-server executable across all potential locations.
     */
    fun findPartyServerBinary(): File? {
        val exeName = if (DesktopPlatform.isWindows) "party-server.exe" else "party-server"
        val candidateDirs = mutableListOf<File>()

        // 1. Current working directory and related folders
        val userDir = File(System.getProperty("user.dir") ?: ".")
        candidateDirs.add(userDir)
        candidateDirs.add(File(userDir, "backend"))
        candidateDirs.add(File(userDir, "bin"))
        candidateDirs.add(File(userDir, "app"))
        candidateDirs.add(File(userDir, "lib"))

        // 2. Running JAR directory and installation root
        runCatching {
            val codeSource = DesktopCloudflareTunnel::class.java.protectionDomain?.codeSource
            val jarFile = codeSource?.location?.toURI()?.let { File(it) }
            if (jarFile != null) {
                val jarDir = jarFile.parentFile
                if (jarDir != null) {
                    candidateDirs.add(jarDir)
                    jarDir.parentFile?.let { installRoot ->
                        candidateDirs.add(installRoot)
                        candidateDirs.add(File(installRoot, "backend"))
                        candidateDirs.add(File(installRoot, "bin"))
                        candidateDirs.add(File(installRoot, "app"))
                    }
                }
            }
        }

        // 3. User home project paths
        val homeDir = System.getProperty("user.home")?.let { File(it) }
        if (homeDir != null) {
            candidateDirs.add(File(homeDir, "Project/BitChord-Desktop/backend"))
            candidateDirs.add(File(homeDir, "Project/BitChord/backend"))
        }

        // 4. Platform-specific standard installation directories
        if (DesktopPlatform.isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA")
            val appData = System.getenv("APPDATA")
            val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            listOfNotNull(
                localAppData?.let { File(it, "BitChord") },
                localAppData?.let { File(it, "BitChord\\bin") },
                localAppData?.let { File(it, "Programs\\BitChord") },
                localAppData?.let { File(it, "Programs\\BitChord\\bin") },
                appData?.let { File(it, "BitChord") },
                File(progFiles, "BitChord"),
                File(progFiles, "BitChord\\bin"),
            ).forEach { candidateDirs.add(it) }
        } else {
            val home = System.getProperty("user.home") ?: ""
            listOf(
                File(home, ".local/share/bitchord"),
                File(home, ".local/share/bitchord/bin"),
                File(home, ".local/bin"),
                File("/usr/local/bin"),
                File("/usr/bin"),
                File("/opt/bitchord"),
                File("/opt/bitchord/bin"),
                File("/app/bin"),
            ).forEach { candidateDirs.add(it) }
        }

        for (dir in candidateDirs) {
            if (!dir.isDirectory) continue
            val target = File(dir, exeName)
            if (target.isFile && (DesktopPlatform.isWindows || target.canExecute())) {
                return target
            }
        }

        // 5. System PATH
        findExecutable(if (DesktopPlatform.isWindows) "party-server.exe" else "party-server")?.let {
            val f = File(it)
            if (f.isFile) return f
        }

        return null
    }

    fun start(port: Int = 8080, onUrlAssigned: ((String) -> Unit)? = null) {
        if (!refreshAvailable()) {
            _state.value = State(
                Status.ERROR,
                error = DesktopStrings["d_tunnel_error_not_installed", "cloudflared CLI is not installed on this system. Please install cloudflared first."],
                port = port,
            )
            return
        }
        if (_state.value.status == Status.STARTING || _state.value.status == Status.RUNNING) return

        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                // 1. Resolve party server port (auto-detect free port if default/preferred is in use)
                val (actualPort, switched) = resolveServerPort(port)
                val portNotice = if (switched) {
                    DesktopStrings.format(
                        "d_tunnel_port_switched",
                        port,
                        actualPort,
                        fallback = "Port %1\$d was in use; switched to port %2\$d.",
                    )
                } else null

                _state.value = State(Status.STARTING, port = actualPort, notice = portNotice)

                // 2. Ensure local party server is running on actualPort
                if (!isLocalHealthOk(actualPort)) {
                    val serverBinary = findPartyServerBinary()
                    val goExe = findExecutable("go")
                    val backendDir = listOf(
                        File("backend"),
                        File(System.getProperty("user.dir"), "backend"),
                        File(System.getProperty("user.home"), "Project/BitChord-Desktop/backend"),
                    ).firstOrNull { it.isDirectory }

                    val pb = when {
                        serverBinary != null && (DesktopPlatform.isWindows || serverBinary.canExecute()) -> {
                            ProcessBuilder(serverBinary.absolutePath)
                                .directory(backendDir ?: serverBinary.parentFile)
                        }
                        backendDir != null && goExe != null -> {
                            ProcessBuilder(goExe, "run", ".")
                                .directory(backendDir)
                        }
                        else -> null
                    }

                    if (pb == null) {
                        _state.value = State(
                            Status.ERROR,
                            error = DesktopStrings[
                                "d_tunnel_error_server_not_found",
                                "Local party server binary (party-server) was not found. Please verify your installation.",
                            ],
                            port = actualPort,
                            notice = portNotice,
                        )
                        return@launch
                    }

                    pb.environment()["PORT"] = actualPort.toString()
                    val serverErrBuffer = StringBuilder()
                    val serverProc = pb.start()
                    serverProcess = serverProc

                    // Drain stderr for crash diagnostics
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            val reader = BufferedReader(InputStreamReader(serverProc.errorStream))
                            val buf = CharArray(1024)
                            var bytesRead = reader.read(buf)
                            while (bytesRead != -1) {
                                synchronized(serverErrBuffer) {
                                    if (serverErrBuffer.length < 4096) {
                                        serverErrBuffer.append(buf, 0, bytesRead)
                                    }
                                }
                                bytesRead = reader.read(buf)
                            }
                        }
                    }

                    // Poll for local server readiness (up to 8 seconds)
                    for (i in 0 until 40) {
                        if (isLocalHealthOk(actualPort)) break
                        if (!serverProc.isAlive) {
                            val exitCode = serverProc.exitValue()
                            val detail = synchronized(serverErrBuffer) { serverErrBuffer.toString().trim() }
                            val msg = if (detail.isNotBlank()) {
                                DesktopStrings.format(
                                    "d_tunnel_error_server_crashed",
                                    exitCode,
                                    detail.take(200),
                                    fallback = "Local party server stopped unexpectedly (exit code %1\$d): %2\$s",
                                )
                            } else {
                                DesktopStrings.format(
                                    "d_tunnel_error_server_start_failed",
                                    actualPort,
                                    fallback = "Local party server failed to start on port %1\$d.",
                                )
                            }
                            _state.value = State(Status.ERROR, error = msg, port = actualPort, notice = portNotice)
                            return@launch
                        }
                        delay(200)
                    }

                    if (!isLocalHealthOk(actualPort)) {
                        val detail = synchronized(serverErrBuffer) { serverErrBuffer.toString().trim() }
                        val msg = if (!serverProc.isAlive) {
                            DesktopStrings.format(
                                "d_tunnel_error_server_crashed",
                                serverProc.exitValue(),
                                detail.take(200),
                                fallback = "Local party server stopped unexpectedly (exit code %1\$d): %2\$s",
                            )
                        } else {
                            DesktopStrings.format(
                                "d_tunnel_error_server_start_failed",
                                actualPort,
                                fallback = "Local party server failed to start on port %1\$d.",
                            )
                        }
                        _state.value = State(Status.ERROR, error = msg, port = actualPort, notice = portNotice)
                        return@launch
                    }
                }

                // 3. Launch cloudflared quick tunnel
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
                    _state.value = State(
                        Status.ERROR,
                        error = DesktopStrings[
                            "d_tunnel_error_url_failed",
                            "Failed to obtain trycloudflare.com URL from cloudflared.",
                        ],
                        port = actualPort,
                        notice = portNotice,
                    )
                    safeDestroy(proc)
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

                // 4. Set negative DNS cache TTL to 1 second so JVM doesn't cache transient NXDOMAIN
                runCatching {
                    java.security.Security.setProperty("networkaddress.cache.negative.ttl", "1")
                }

                // 5. Wait up to 20 seconds for Cloudflare edge routing to be live
                for (attempt in 0 until 20) {
                    val ok = runCatching {
                        val conn = URI("$capturedUrl/healthz").toURL().openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BitChord/1.10")
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

                _state.value = State(Status.RUNNING, url = capturedUrl, port = actualPort, notice = portNotice)
                onUrlAssigned?.invoke(capturedUrl)

                // 6. Monitor tunnel lifetime
                while (proc.isAlive) {
                    delay(1500)
                }
                drainJob.cancel()
                if (_state.value.status == Status.RUNNING) {
                    _state.value = State(Status.STOPPED, port = actualPort, notice = portNotice)
                }
            } catch (e: Exception) {
                _state.value = State(Status.ERROR, error = e.message ?: "Tunnel failed to start", port = port)
            }
        }
    }

    private fun safeDestroy(proc: Process?) {
        if (proc == null) return
        runCatching {
            proc.descendants().forEach { it.destroyForcibly() }
            proc.destroyForcibly()
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        safeDestroy(tunnelProcess)
        tunnelProcess = null
        safeDestroy(serverProcess)
        serverProcess = null
        _state.value = State(Status.STOPPED)
    }
}
