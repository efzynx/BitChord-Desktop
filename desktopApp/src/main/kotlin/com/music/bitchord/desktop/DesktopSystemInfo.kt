package com.music.bitchord.desktop

import java.io.File

/**
 * Mendeteksi jenis paket atau metode bagaimana BitChord Desktop dipasang di sistem.
 */
enum class InstallType {
    APPIMAGE,
    DEB,
    RPM,
    LOCAL_USER,
    WINDOWS_INSTALLED,
    WINDOWS_PORTABLE,
    STANDALONE,
    DEV;

    val label: String
        get() = when (this) {
            APPIMAGE -> DesktopStrings["d_install_appimage_label", "AppImage (Linux)"]
            DEB -> DesktopStrings["d_install_deb_label", "Debian Package (.deb)"]
            RPM -> DesktopStrings["d_install_rpm_label", "RedHat Package (.rpm)"]
            LOCAL_USER -> DesktopStrings["d_install_local_label", "Local User Install (~/.local)"]
            WINDOWS_INSTALLED -> DesktopStrings["d_install_win_installed_label", "Windows Installer (.exe / .msi)"]
            WINDOWS_PORTABLE -> DesktopStrings["d_install_win_portable_label", "Windows Portable (.zip)"]
            STANDALONE -> DesktopStrings["d_install_standalone_label", "Standalone Distribution"]
            DEV -> DesktopStrings["d_install_dev_label", "Development / Gradle Run"]
        }

    val formatDescription: String
        get() = when (this) {
            APPIMAGE -> DesktopStrings["d_install_appimage_desc", "Standalone portable Linux package (.AppImage)"]
            DEB -> DesktopStrings["d_install_deb_desc", "Debian / Ubuntu installation package (.deb)"]
            RPM -> DesktopStrings["d_install_rpm_desc", "Fedora / RHEL / openSUSE package (.rpm)"]
            LOCAL_USER -> DesktopStrings["d_install_local_desc", "Installed in user directory via install-desktop.sh"]
            WINDOWS_INSTALLED -> DesktopStrings["d_install_win_installed_desc", "Installed on Windows via installer (.exe / .msi)"]
            WINDOWS_PORTABLE -> DesktopStrings["d_install_win_portable_desc", "Standalone portable Windows archive (extracted from .zip)"]
            STANDALONE -> DesktopStrings["d_install_standalone_desc", "Standalone jpackage distribution (bin/BitChord)"]
            DEV -> DesktopStrings["d_install_dev_desc", "Running directly from source code with Gradle"]
        }

    val uninstallHint: String
        get() = when (this) {
            APPIMAGE -> DesktopStrings["d_uninstall_appimage_hint", "Simply delete the .AppImage file from your system."]
            DEB -> DesktopStrings["d_uninstall_deb_hint", "Run 'sudo apt remove bitchord' in the terminal."]
            RPM -> DesktopStrings["d_uninstall_rpm_hint", "Run 'sudo dnf remove bitchord' in the terminal."]
            LOCAL_USER -> DesktopStrings["d_uninstall_local_hint", "Run 'scripts/uninstall-desktop.sh' from the project directory, or delete ~/.local/share/bitchord."]
            WINDOWS_INSTALLED -> DesktopStrings["d_uninstall_win_installed_hint", "Open Windows Settings > Apps > Installed apps, search for 'BitChord', and select Uninstall."]
            WINDOWS_PORTABLE -> DesktopStrings["d_uninstall_win_portable_hint", "Simply delete this BitChord folder. No registry residue remains."]
            STANDALONE -> DesktopStrings["d_uninstall_standalone_hint", "Delete the extracted folder."]
            DEV -> DesktopStrings["d_uninstall_dev_hint", "Development environment; no uninstallation required."]
        }
}

/**
 * Informasi sistem dan runtime perangkat tempat BitChord Desktop berjalan.
 */
internal object DesktopSystemInfo {

    val osName: String = System.getProperty("os.name").orEmpty()
    val osVersion: String = System.getProperty("os.version").orEmpty()
    val osArch: String = System.getProperty("os.arch").orEmpty()
    val javaVersion: String = System.getProperty("java.version").orEmpty()
    val javaVendor: String = System.getProperty("java.vendor").orEmpty()
    val appVersion: String = DesktopUpdateChecker.currentVersion
    const val BASE_UPSTREAM_VERSION = "1.9.1"

    val repoUrl: String = "https://github.com/efzynx/BitChord-Desktop"

    data class WindowsOsDetails(
        val productName: String? = null,
        val displayVersion: String? = null,
        val buildNumber: Int? = null,
        val ubr: Int? = null,
    )

    val windowsDetails: WindowsOsDetails by lazy {
        if (!DesktopPlatform.isWindows) WindowsOsDetails() else queryWindowsOsDetails()
    }

    val windowsBuildNumber: Int
        get() = windowsDetails.buildNumber ?: -1

    private fun queryWindowsOsDetails(): WindowsOsDetails {
        var productName: String? = null
        var displayVersion: String? = null
        var buildNumber: Int? = null
        var ubr: Int? = null

        // 1. Query Windows NT CurrentVersion registry
        runCatching {
            val process = ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            for (rawLine in text.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val parts = line.split(Regex("\\s{2,}"))
                if (parts.size >= 3) {
                    val key = parts[0]
                    val value = parts[2]
                    when {
                        key.equals("ProductName", ignoreCase = true) -> productName = value
                        key.equals("DisplayVersion", ignoreCase = true) -> displayVersion = value
                        key.equals("CurrentBuildNumber", ignoreCase = true) || key.equals("CurrentBuild", ignoreCase = true) -> {
                            if (buildNumber == null) buildNumber = value.toIntOrNull()
                        }
                        key.equals("UBR", ignoreCase = true) -> {
                            ubr = if (value.startsWith("0x", ignoreCase = true)) {
                                value.substring(2).toIntOrNull(16)
                            } else {
                                value.toIntOrNull()
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback / supplement build number from cmd.exe ver
        if (buildNumber == null) {
            runCatching {
                val process = ProcessBuilder("cmd.exe", "/c", "ver")
                    .redirectErrorStream(true)
                    .start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                val match = Regex("""\[Version\s+(\d+)\.(\d+)\.(\d+)(?:\.(\d+))?\]""", RegexOption.IGNORE_CASE).find(text)
                if (match != null) {
                    buildNumber = match.groupValues[3].toIntOrNull()
                    if (ubr == null) ubr = match.groupValues.getOrNull(4)?.toIntOrNull()
                }
            }
        }

        return WindowsOsDetails(productName, displayVersion, buildNumber, ubr)
    }

    /**
     * Nama OS yang ramah pengguna (misal: "CachyOS Linux", "Ubuntu 24.04 LTS", "Windows 11").
     */
    val prettyOsName: String by lazy {
        when {
            DesktopPlatform.isLinux -> {
                runCatching {
                    val osRelease = File("/etc/os-release")
                    if (osRelease.isFile) {
                        val lines = osRelease.readLines()
                        val pretty = lines.firstOrNull { it.startsWith("PRETTY_NAME=") }
                            ?.substringAfter("=")?.trim('"', '\'')
                        val name = lines.firstOrNull { it.startsWith("NAME=") }
                            ?.substringAfter("=")?.trim('"', '\'')
                        pretty ?: name ?: osName
                    } else osName
                }.getOrDefault(osName)
            }
            DesktopPlatform.isWindows -> {
                val details = windowsDetails
                val build = details.buildNumber
                val ubr = details.ubr
                val displayVer = details.displayVersion

                val baseName = when {
                    build != null && build >= 22000 -> {
                        val prod = details.productName
                        if (prod != null && prod.contains("Windows 10", ignoreCase = true)) {
                            prod.replace("Windows 10", "Windows 11")
                        } else if (prod != null && prod.contains("Windows", ignoreCase = true)) {
                            prod
                        } else {
                            "Windows 11"
                        }
                    }
                    details.productName != null -> details.productName
                    else -> "Windows $osVersion"
                }

                buildString {
                    append(baseName)
                    if (!displayVer.isNullOrBlank()) {
                        append(" ").append(displayVer)
                    }
                    if (build != null) {
                        append(" (Build ").append(build)
                        if (ubr != null) append(".").append(ubr)
                        append(")")
                    }
                    if (osArch.isNotBlank()) {
                        append(" (").append(osArch).append(")")
                    }
                }
            }
            DesktopPlatform.isMac -> "macOS $osVersion ($osArch)"
            else -> "$osName $osVersion ($osArch)"
        }
    }

    /**
     * Mendeteksi otomatis metode instalasi aplikasi.
     */
    val installType: InstallType by lazy {
        val appImageEnv = System.getenv("APPIMAGE") ?: System.getenv("APPDIR")
        if (!appImageEnv.isNullOrBlank()) {
            return@lazy InstallType.APPIMAGE
        }

        val appPath = runCatching {
            File(DesktopSystemInfo::class.java.protectionDomain.codeSource.location.toURI()).absolutePath
        }.getOrDefault("")

        if (DesktopPlatform.isWindows) {
            val progFiles = System.getenv("ProgramFiles").orEmpty()
            val progFilesX86 = System.getenv("ProgramFiles(x86)").orEmpty()
            val localAppData = System.getenv("LOCALAPPDATA").orEmpty()
            when {
                (progFiles.isNotBlank() && appPath.contains(progFiles, ignoreCase = true)) ||
                    (progFilesX86.isNotBlank() && appPath.contains(progFilesX86, ignoreCase = true)) ||
                    (localAppData.isNotBlank() && appPath.contains("$localAppData\\Programs", ignoreCase = true)) -> {
                    InstallType.WINDOWS_INSTALLED
                }
                appPath.contains("build\\classes") || appPath.contains("build\\libs") -> {
                    InstallType.DEV
                }
                else -> {
                    InstallType.WINDOWS_PORTABLE
                }
            }
        } else if (DesktopPlatform.isLinux) {
            val userHome = System.getProperty("user.home").orEmpty()
            when {
                File("/var/lib/dpkg/info/bitchord.list").isFile -> InstallType.DEB
                appPath.contains("$userHome/.local/share/bitchord") -> InstallType.LOCAL_USER
                appPath.contains("/usr/") || appPath.contains("/opt/") -> InstallType.STANDALONE
                appPath.contains("build/classes") || appPath.contains("build/libs") -> InstallType.DEV
                else -> InstallType.STANDALONE
            }
        } else {
            InstallType.STANDALONE
        }
    }
}
