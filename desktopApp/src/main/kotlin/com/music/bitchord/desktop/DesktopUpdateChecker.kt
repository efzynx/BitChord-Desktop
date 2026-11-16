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
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/efzynx/BitChord-Desktop/releases/latest"

    val currentVersion: String = System.getProperty("bitchord.version") ?: "1.9.1"

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
    suspend fun checkManual(): UpdateCheckStatus = runCatching {
        val response = http.get(LATEST_RELEASE_URL) {
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

        val release = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: return UpdateCheckStatus.Error("Respon rilis GitHub tidak valid.")

        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull
            ?: return UpdateCheckStatus.Error("Tag rilis tidak ditemukan.")
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull
            ?: "https://github.com/efzynx/BitChord-Desktop/releases"
        val latest = tag.removePrefix("v")

        val assets = release["assets"]?.jsonArray?.mapNotNull { el ->
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
            notes = release["body"]?.jsonPrimitive?.contentOrNull,
            assets = assets,
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

    private class Parsed(val parts: List<Int>, val preRelease: Boolean)

    private fun parse(raw: String): Parsed {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return Parsed(base.split('.').map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Perbandingan versi numerik. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.preRelease && !l.preRelease
    }
}
