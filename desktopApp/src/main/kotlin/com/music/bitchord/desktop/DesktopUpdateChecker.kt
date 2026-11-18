package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Pilihan saluran pembaruan aplikasi.
 */
enum class DesktopUpdateChannel(val id: String, val label: String, val description: String) {
    STABLE("stable", "Rilis Stabil (Stable)", "Hanya memeriksa versi stabil resmi"),
    PRERELEASE("prerelease", "Beta / Pre-release", "Menerima pembaruan versi beta dan fitur terkini"),
}

/**
 * Pengaturan preferensi saluran pembaruan.
 */
internal object DesktopUpdateSettings {
    private const val KEY_CHANNEL = "update_channel"
    private val persistence = DesktopPersistence()

    fun channel(): DesktopUpdateChannel {
        val stored = persistence.string(KEY_CHANNEL)
        return when (stored) {
            "stable" -> DesktopUpdateChannel.STABLE
            "prerelease" -> DesktopUpdateChannel.PRERELEASE
            else -> {
                if (DesktopUpdateChecker.currentVersion.contains(Regex("(beta|alpha|rc|preview)", RegexOption.IGNORE_CASE))) {
                    DesktopUpdateChannel.PRERELEASE
                } else {
                    DesktopUpdateChannel.STABLE
                }
            }
        }
    }

    fun setChannel(channel: DesktopUpdateChannel) {
        persistence.saveString(KEY_CHANNEL, channel.id)
    }
}

/**
 * Memeriksa pembaruan rilis terbaru BitChord Desktop dari repository GitHub efzynx/BitChord-Desktop.
 */
internal object DesktopUpdateChecker {

    data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long?,
    )

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val downloadUrl: String?,
        val notes: String?,
        val assets: List<ReleaseAsset> = emptyList(),
        val isPrerelease: Boolean = false,
    )

    sealed interface UpdateCheckStatus {
        object Idle : UpdateCheckStatus
        object Checking : UpdateCheckStatus
        data class UpToDate(val currentVersion: String, val releaseUrl: String? = null) : UpdateCheckStatus
        data class UpdateAvailable(val update: UpdateInfo) : UpdateCheckStatus
        data class Error(val message: String) : UpdateCheckStatus
    }

    /**
     * Endpoint API rilis GitHub BitChord-Desktop milik pengguna.
     */
    const val RELEASES_API_URL =
        "https://api.github.com/repos/efzynx/BitChord-Desktop/releases?per_page=20"

    val currentVersion: String = System.getProperty("bitchord.version") ?: "1.9.2-beta.2"

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    /**
     * Pengecekan otomatis saat aplikasi pertama kali dibuka (hanya mengembalikan jika ada update baru).
     */
    suspend fun check(): UpdateInfo? = runCatching {
        when (val res = checkManual()) {
            is UpdateCheckStatus.UpdateAvailable -> res.update
            else -> null
        }
    }.getOrNull()

    /**
     * Pengecekan manual untuk tampilan menu Updater di Settings.
     */
    suspend fun checkManual(channel: DesktopUpdateChannel = DesktopUpdateSettings.channel()): UpdateCheckStatus = runCatching {
        val response = http.get(RELEASES_API_URL) {
            header("Accept", "application/vnd.github+json")
        }

        if (response.status == HttpStatusCode.NotFound) {
            return UpdateCheckStatus.UpToDate(
                currentVersion = currentVersion,
                releaseUrl = "https://github.com/efzynx/BitChord-Desktop/releases",
            )
        }

        if (!response.status.isSuccess()) {
            return UpdateCheckStatus.Error("Gagal menghubungi server GitHub (${response.status.value}).")
        }

        val releases = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonArray }.getOrNull()
            ?: return UpdateCheckStatus.Error("Respon rilis GitHub tidak valid.")

        val targetRelease = releases.mapNotNull { it as? JsonObject }.firstOrNull { obj ->
            val draft = obj["draft"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
            val pre = obj["prerelease"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
            if (draft) return@firstOrNull false
            if (channel == DesktopUpdateChannel.STABLE) {
                !pre
            } else {
                true
            }
        } ?: return UpdateCheckStatus.UpToDate(currentVersion, "https://github.com/efzynx/BitChord-Desktop/releases")

        val tag = targetRelease["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: return UpdateCheckStatus.Error("Tag rilis tidak ditemukan.")
        val url = targetRelease["html_url"]?.jsonPrimitive?.contentOrNull
            ?: "https://github.com/efzynx/BitChord-Desktop/releases"
        val latest = tag.removePrefix("v")
        val isPre = targetRelease["prerelease"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

        val assets = targetRelease["assets"]?.jsonArray?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val dlUrl = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val size = obj["size"]?.jsonPrimitive?.longOrNull
            ReleaseAsset(name, dlUrl, size)
        }.orEmpty()

        val updateInfo = UpdateInfo(
            version = latest,
            releaseUrl = url,
            downloadUrl = preferredInstallerUrl(assets),
            notes = targetRelease["body"]?.jsonPrimitive?.contentOrNull,
            assets = assets,
            isPrerelease = isPre,
        )

        if (isNewer(latest, currentVersion)) {
            UpdateCheckStatus.UpdateAvailable(updateInfo)
        } else {
            UpdateCheckStatus.UpToDate(currentVersion, url)
        }
    }.getOrElse {
        UpdateCheckStatus.Error(it.message ?: "Terjadi kesalahan saat memeriksa pembaruan.")
    }

    /**
     * Memilih URL installer yang paling sesuai dengan metode instalasi dan OS pengguna.
     */
    private fun preferredInstallerUrl(assets: List<ReleaseAsset>): String? {
        if (assets.isEmpty()) return null
        val type = DesktopSystemInfo.installType

        // Cari berdasarkan jenis instalasi saat ini
        val preferred = when (type) {
            InstallType.APPIMAGE -> assets.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
            InstallType.DEB -> assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
            InstallType.RPM -> assets.firstOrNull { it.name.endsWith(".rpm", ignoreCase = true) }
            InstallType.WINDOWS_PORTABLE -> assets.firstOrNull { it.name.contains("windows-portable", ignoreCase = true) || it.name.endsWith(".zip", ignoreCase = true) }
            InstallType.WINDOWS_INSTALLED -> assets.firstOrNull { it.name.endsWith("-setup.exe", ignoreCase = true) || it.name.endsWith(".msi", ignoreCase = true) }
            else -> null
        }
        if (preferred != null) return preferred.downloadUrl

        // Fallback berdasarkan OS
        val fallback = when {
            DesktopPlatform.isWindows -> assets.firstOrNull { it.name.endsWith("-setup.exe", ignoreCase = true) || it.name.endsWith(".zip", ignoreCase = true) }
            DesktopPlatform.isLinux -> assets.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) || it.name.endsWith(".deb", ignoreCase = true) }
            else -> assets.firstOrNull()
        }
        return fallback?.downloadUrl
    }

    private class Parsed(val parts: List<Int>, val preReleaseSuffix: String?)

    private fun parse(raw: String): Parsed {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        val suffix = if (dash >= 0) raw.substring(dash + 1) else null
        return Parsed(base.split('.').map { it.toIntOrNull() ?: 0 }, suffix)
    }

    /** Perbandingan versi numerik dan pra-rilis. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        // Base numbers are equal (e.g. 1.9.2 vs 1.9.2)
        if (l.preReleaseSuffix == null && c.preReleaseSuffix != null) return true
        if (l.preReleaseSuffix != null && c.preReleaseSuffix == null) return false
        if (l.preReleaseSuffix != null && c.preReleaseSuffix != null) {
            val lNums = l.preReleaseSuffix.split('.').mapNotNull { it.toIntOrNull() }
            val cNums = c.preReleaseSuffix.split('.').mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(lNums.size, cNums.size)) {
                val a = lNums.getOrElse(i) { 0 }
                val b = cNums.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return l.preReleaseSuffix > c.preReleaseSuffix
        }
        return false
    }
}
