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
}
