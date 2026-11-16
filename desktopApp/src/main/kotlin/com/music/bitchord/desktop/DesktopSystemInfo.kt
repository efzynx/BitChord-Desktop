package com.music.bitchord.desktop

import java.io.File

/**
 * Mendeteksi jenis paket atau metode bagaimana BitChord Desktop dipasang di sistem.
 */
enum class InstallType(
    val label: String,
    val formatDescription: String,
    val uninstallHint: String,
) {
    APPIMAGE(
        label = "AppImage",
        formatDescription = "Paket portabel mandiri Linux (.AppImage)",
        uninstallHint = "Cukup hapus file .AppImage dari sistem Anda.",
    ),
    DEB(
        label = "Debian Package (.deb)",
        formatDescription = "Paket instalasi Debian / Ubuntu (.deb)",
        uninstallHint = "Jalankan 'sudo apt remove bitchord' di terminal.",
    ),
    RPM(
        label = "RedHat Package (.rpm)",
        formatDescription = "Paket instalasi Fedora / RHEL / openSUSE (.rpm)",
        uninstallHint = "Jalankan 'sudo dnf remove bitchord' di terminal.",
    ),
    LOCAL_USER(
        label = "Instalasi Lokal Pengguna (~/.local)",
        formatDescription = "Terpasang di folder pengguna via script install-desktop.sh",
        uninstallHint = "Jalankan script 'scripts/uninstall-desktop.sh' dari folder proyek, atau hapus ~/.local/share/bitchord.",
    ),
    WINDOWS_INSTALLED(
        label = "Windows Installer (Setup / MSI)",
        formatDescription = "Terpasang di sistem Windows via installer (.exe / .msi)",
        uninstallHint = "Buka Settings Windows > Apps > Installed apps, lalu cari 'BitChord' dan pilih Uninstall.",
    ),
    WINDOWS_PORTABLE(
        label = "Windows Portable (.zip)",
        formatDescription = "Arsip portabel mandiri Windows (diekstrak dari .zip)",
        uninstallHint = "Cukup hapus folder BitChord ini. Tidak ada file sisa di registry sistem Windows.",
    ),
    STANDALONE(
        label = "Standalone Distribution",
        formatDescription = "Distribusi mandiri jpackage (bin/BitChord)",
        uninstallHint = "Cukup hapus direktori aplikasi ini secara langsung.",
    ),
    DEV(
        label = "Development / Gradle Run",
        formatDescription = "Dijalankan langsung melalui Gradle dev runner (:desktopApp:run)",
        uninstallHint = "Tidak terpasang di sistem (berjalan langsung dari source code).",
    ),
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

    val repoUrl: String = "https://github.com/efzynx/BitChord-Desktop"

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
            DesktopPlatform.isWindows -> "Windows $osVersion ($osArch)"
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
