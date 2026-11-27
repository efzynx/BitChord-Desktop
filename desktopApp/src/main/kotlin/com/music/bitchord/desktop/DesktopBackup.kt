package com.music.bitchord.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Portable JSON backup & restore of desktop library, listening stats, and non-credential settings.
 */
internal object DesktopBackup {

    private const val APP_TAG = "bitchord"
    private const val PLATFORM_TAG = "desktop"
    private const val SCHEMA_VERSION = 1

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** Keys exported safely (excluding tokens, cookies, DPAPI secrets, and Drive grants). */
    private val BOOLEAN_KEYS = listOf(
        "autoplay",
        "automix",
        "automix_on_skip",
        "auto_download_liked",
        "shuffle",
        "tray_icon",
        "close_to_tray",
        "spatial_audio",
        "dolby_atmos",
        "skip_silence",
        "smart_version_alignment",
        "legacy_mesh_gradient",
        "animated_canvas",
        "animated_artist_banner",
        "show_nerd_stats",
        "full_bleed_artwork",
        "show_total_duration",
        "show_best_part",
        "replay_genres",
        "automix_art_transition",
        "automix_mini_art_transition",
        "dont_repeat_suggestions",
        "hide_lyrics_preview",
        "swipe_back_gesture",
        "reduce_animation",
        "smooth_transitions",
        "reduce_dynamic_blur",
        "disable_blend_effects",
        "liquid_glass",
        "glass_click_animations",
        "glass_performance",
        "high_performance_mode",
        "hide_volume_bar",
        "use_custom_title_bar",
        DesktopLyricsClient.KEY_SYNCED_LYRICS,
        DesktopLyricsClient.KEY_LYRICS_BLUR,
        DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES,
        DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO,
    )

    private val STRING_KEYS = listOf(
        "automix_performance",
        "automix_phrase_bars",
        "repeat_mode",
        "playback_speed",
        "crossfade_seconds",
        "download_quality",
        "output_precision",
        "audio_quality",
        "theme_mode",
        "liquid_glass_level",
        "ambient_backdrop",
        "performance_refresh_rate",
        "last_player_screen",
        "app_language",
        "translation_language",
        DesktopLyricsClient.KEY_LYRICS_ORDER,
        DesktopLyricsClient.KEY_LYRICS_SOURCES,
        DesktopLyricsClient.KEY_LYRICS_SOURCES_SEEN,
    )

    private val INT_KEYS = listOf(
        "lyrics_offset_ms",
        "liquid_glass_rainbow_scale",
        "liquid_glass_outline_reflection_scale",
    )

    @Serializable
    data class BackupPlaylist(
        val id: String,
        val title: String,
        val songsEncoded: List<String> = emptyList(),
    )

    @Serializable
    data class BackupFile(
        val app: String = APP_TAG,
        val platform: String = PLATFORM_TAG,
        val schemaVersion: Int = SCHEMA_VERSION,
        val versionName: String = "",
        val exportedAt: String = "",
        val booleans: Map<String, Boolean> = emptyMap(),
        val strings: Map<String, String> = emptyMap(),
        val ints: Map<String, Int> = emptyMap(),
        val likedIds: List<String> = emptyList(),
        val dislikedIds: List<String> = emptyList(),
        val historyEncoded: List<String> = emptyList(),
        val playlists: List<BackupPlaylist> = emptyList(),
        val originalVersionIds: List<String> = emptyList(),
        val listening: List<DesktopListeningStats.BackupBucket> = emptyList(),
    )

    data class Summary(
        val months: Int,
        val playlists: Int,
        val likedCount: Int,
        val settingsCount: Int,
        val fromVersion: String,
        val exportedAt: String,
    )

    fun suggestedFilename(): String {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .format(Instant.now().atZone(ZoneId.systemDefault()))
        return "bitchord-backup-$date.json"
    }

    fun captureSnapshot(
        persistence: DesktopPersistence = DesktopPersistence(),
        versionName: String = DesktopSystemInfo.appVersion,
    ): BackupFile {
        val prefs = persistence.preferences
        val booleans = LinkedHashMap<String, Boolean>()
        for (key in BOOLEAN_KEYS) {
            if (prefs.get(key, null) != null) {
                booleans[key] = prefs.getBoolean(key, false)
            }
        }
        val strings = LinkedHashMap<String, String>()
        for (key in STRING_KEYS) {
            val value = DesktopPreferenceChunks.read(prefs, key)
            if (value != null) strings[key] = value
        }
        val ints = LinkedHashMap<String, Int>()
        for (key in INT_KEYS) {
            if (prefs.get(key, null) != null) {
                ints[key] = prefs.getInt(key, 0)
            }
        }
        return BackupFile(
            app = APP_TAG,
            platform = PLATFORM_TAG,
            schemaVersion = SCHEMA_VERSION,
            versionName = versionName,
            exportedAt = Instant.now().toString(),
            booleans = booleans,
            strings = strings,
            ints = ints,
            likedIds = persistence.likedIds().toList(),
            dislikedIds = persistence.dislikedIds().toList(),
            historyEncoded = persistence.history().map(DesktopPersistence::encodeSongFields),
            playlists = persistence.playlists().map { pl ->
                BackupPlaylist(
                    id = pl.id,
                    title = pl.title,
                    songsEncoded = pl.songs.map(DesktopPersistence::encodeSongFields),
                )
            },
            originalVersionIds = persistence.originalVersionIds().toList(),
            listening = DesktopListeningStats.exportBackupBuckets(),
        )
    }

    fun applySnapshot(
        file: BackupFile,
        persistence: DesktopPersistence = DesktopPersistence(),
    ): Summary {
        require(file.app == APP_TAG) { "That backup is not from BitChord" }
        require(file.schemaVersion <= SCHEMA_VERSION) {
            "That backup was created by a newer version of BitChord"
        }
        val prefs = persistence.preferences
        file.booleans.forEach { (key, value) ->
            if (key in BOOLEAN_KEYS) prefs.putBoolean(key, value)
        }
        file.strings.forEach { (key, value) ->
            if (key in STRING_KEYS) DesktopPreferenceChunks.write(prefs, key, value)
        }
        file.ints.forEach { (key, value) ->
            if (key in INT_KEYS) prefs.putInt(key, value)
        }

        persistence.saveLikedIds(file.likedIds.filter(String::isNotBlank).toSet())
        persistence.saveDislikedIds(file.dislikedIds.filter(String::isNotBlank).toSet())
        persistence.saveHistory(file.historyEncoded.mapNotNull(DesktopPersistence::decodeSongFields))
        persistence.savePlaylists(
            file.playlists.mapNotNull { pl ->
                if (pl.id.isBlank() || pl.title.isBlank()) null
                else DesktopPlaylist(
                    id = pl.id,
                    title = pl.title,
                    songs = pl.songsEncoded.mapNotNull(DesktopPersistence::decodeSongFields),
                )
            },
        )
        persistence.saveOriginalVersionIds(file.originalVersionIds.filter(String::isNotBlank).toSet())
        DesktopListeningStats.importBackupBuckets(file.listening)
        runCatching { prefs.flush() }

        // Refresh observable singleton settings right away where applicable.
        refreshSingletonSettings(persistence)

        return Summary(
            months = file.listening.size,
            playlists = file.playlists.size,
            likedCount = file.likedIds.size,
            settingsCount = file.booleans.size + file.strings.size + file.ints.size,
            fromVersion = file.versionName,
            exportedAt = file.exportedAt,
        )
    }

    /**
     * Applies compatible non-credential preferences exported from a BitChord Mobile device
     * manifest, returning how many settings matched desktop's allowlist.
     */
    fun applyMobilePreferences(
        booleans: Map<String, Boolean>,
        strings: Map<String, String>,
        ints: Map<String, Int>,
        persistence: DesktopPersistence = DesktopPersistence(),
    ): Int {
        val prefs = persistence.preferences
        var applied = 0
        booleans.forEach { (key, value) ->
            if (key in BOOLEAN_KEYS) {
                prefs.putBoolean(key, value)
                applied++
            }
        }
        strings.forEach { (key, value) ->
            if (key in STRING_KEYS) {
                DesktopPreferenceChunks.write(prefs, key, value)
                applied++
            }
        }
        ints.forEach { (key, value) ->
            if (key in INT_KEYS) {
                prefs.putInt(key, value)
                applied++
            }
        }
        runCatching { prefs.flush() }
        refreshSingletonSettings(persistence)
        return applied
    }

    private fun refreshSingletonSettings(persistence: DesktopPersistence) {
        DesktopAppearanceSettings.setSwipeBackGesture(
            persistence.boolean("swipe_back_gesture", true),
        )
        DesktopAppearanceSettings.setHideLyricsPreview(
            persistence.boolean("hide_lyrics_preview", false),
        )
    }

    fun encodeJsonBytes(snapshot: BackupFile, pretty: Boolean): ByteArray {
        val serializer = BackupFile.serializer()
        val text = (if (pretty) prettyJson else compactJson).encodeToString(serializer, snapshot)
        return text.encodeToByteArray()
    }

    fun decodeJsonBytes(bytes: ByteArray): BackupFile {
        val text = bytes.decodeToString()
        val parsed = runCatching { prettyJson.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { error("That file does not look like a BitChord backup") }
        require(parsed.app == APP_TAG) { "That backup is from another app" }
        require(parsed.schemaVersion <= SCHEMA_VERSION) {
            "That backup was written by a newer version of BitChord"
        }
        return parsed
    }

    fun gzip(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        return out.toByteArray()
    }

    fun gunzip(compressed: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }

    suspend fun exportToFile(target: File): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = captureSnapshot()
            val bytes = encodeJsonBytes(snapshot, pretty = true)
            target.writeBytes(bytes)
            Summary(
                months = snapshot.listening.size,
                playlists = snapshot.playlists.size,
                likedCount = snapshot.likedIds.size,
                settingsCount = snapshot.booleans.size + snapshot.strings.size + snapshot.ints.size,
                fromVersion = snapshot.versionName,
                exportedAt = snapshot.exportedAt,
            )
        }
    }

    suspend fun importFromFile(source: File): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = source.readBytes()
            val jsonBytes = if (raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
                gunzip(raw)
            } else {
                raw
            }
            val parsed = decodeJsonBytes(jsonBytes)
            applySnapshot(parsed)
        }
    }

    fun chooseExportFile(): File? = runCatching {
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = "Export BitChord Backup"
            selectedFile = File(System.getProperty("user.home"), suggestedFilename())
            fileFilter = FileNameExtensionFilter("BitChord Backup (*.json)", "json")
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val picked = chooser.selectedFile ?: return null
        if (picked.name.endsWith(".json", ignoreCase = true)) picked else File(picked.parentFile, "${picked.name}.json")
    }.getOrNull()

    fun chooseImportFile(): File? = runCatching {
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = "Import BitChord Backup"
            fileFilter = FileNameExtensionFilter("BitChord Backup (*.json, *.json.gz)", "json", "gz")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        chooser.selectedFile
    }.getOrNull()
}
