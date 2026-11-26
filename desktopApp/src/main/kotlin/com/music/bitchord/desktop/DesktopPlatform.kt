package com.music.bitchord.desktop

/** Which desktop this is running on. */
internal object DesktopPlatform {

    private val name: String = System.getProperty("os.name").orEmpty()

    val isWindows: Boolean = name.startsWith("Windows", ignoreCase = true)

    val isLinux: Boolean = name.contains("linux", ignoreCase = true)

    val isMac: Boolean = name.contains("mac", ignoreCase = true)

    /**
     * Windows 11 begins at build 22000. In many JDK runtimes, os.name reports "Windows 10"
     * because both share the 10.0 kernel major version. Checking actual build number ensures
     * true Windows 11 detection.
     */
    val isWindows11: Boolean by lazy {
        if (!isWindows) false else (DesktopSystemInfo.windowsBuildNumber >= 22000 || name.contains("11"))
    }

    val isWindows10: Boolean by lazy {
        if (!isWindows) false else (DesktopSystemInfo.windowsBuildNumber in 10240 until 22000 || (name.contains("10") && !isWindows11))
    }

    /** Whether the application draws the window's own frame instead of the system drawing it. */
    val drawsOwnWindowFrame: Boolean = isWindows || isMac

    /**
     * Liquid Glass (real-time Skia refraction + backdrop blur) requires Windows 10 or newer on
     * Windows, and is available without OS version restrictions on Linux and macOS.
     */
    val supportsLiquidGlass: Boolean by lazy {
        liquidGlassSupportedFor(
            isWindows = isWindows,
            isLinux = isLinux,
            isMac = isMac,
            windowsBuildNumber = if (isWindows) DesktopSystemInfo.windowsBuildNumber else -1,
            osVersion = DesktopSystemInfo.osVersion,
            osName = name,
        )
    }

    internal fun liquidGlassSupportedFor(
        isWindows: Boolean,
        isLinux: Boolean,
        isMac: Boolean,
        windowsBuildNumber: Int,
        osVersion: String,
        osName: String = "",
    ): Boolean {
        if (isLinux || isMac) return true
        if (!isWindows) return false
        if (windowsBuildNumber >= 10240) return true
        if (windowsBuildNumber in 0 until 10240) return false
        val major = osVersion.substringBefore('.').toIntOrNull()
        if (major != null) return major >= 10
        return osName.contains("10") || osName.contains("11")
    }
}
