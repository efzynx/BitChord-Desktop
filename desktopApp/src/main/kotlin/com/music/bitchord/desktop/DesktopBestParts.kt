package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.ui.player.BestPart
import com.music.bitchord.ui.player.BestPartStart
import com.music.bitchord.ui.player.Heatmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache and resolution for songs' best parts on desktop.
 *
 * Keeps heatmap curves on disk under the media cache directory so future playback
 * doesn't need to ask YouTube repeatedly.
 */
internal object DesktopBestParts {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private var file: File? = null
    private val stored = ConcurrentHashMap<String, StoredHeat>()
    private val starts = ConcurrentHashMap<String, BestPartStart>()

    fun known(videoId: String): BestPartStart? = starts[videoId]

    suspend fun startFor(song: Song): BestPartStart {
        starts[song.videoId]?.let { return it }
        ensureLoaded()

        val heat = heatmapFor(song)
        val durationMs = song.durationText.durationMillis().takeIf { it > 0 } ?: heat?.durationMs ?: 0L
        val lines = syncedLines(song, durationMs).orEmpty()
        val result = BestPart.pick(heat, lines, durationMs)

        if (result.source != BestPartStart.Source.FALLBACK) {
            starts[song.videoId] = result
        }
        return result
    }

    private suspend fun heatmapFor(song: Song): Heatmap? {
        val id = song.videoId
        if (song.localUri != null || !isYouTubeId(id)) return null
        val now = System.currentTimeMillis()
        stored[id]?.let { kept ->
            val heat = kept.heatmap()
            val fresh = now - kept.fetchedAt < if (heat != null) MONTH_MS else WEEK_MS
            if (fresh) return heat
        }

        val answer = withTimeoutOrNull(HEATMAP_TIMEOUT_MS) {
            try {
                Innertube.webNext(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return stored[id]?.heatmap()

        val heat = BestPart.parseHeatmap(answer)
        stored[id] = StoredHeat(heat?.segmentMs ?: 0L, heat?.intensity.orEmpty(), now)
        save()
        return heat
    }

    private suspend fun syncedLines(song: Song, durationMs: Long): List<LyricLine>? {
        if (!DesktopPlayerSettings.syncedLyrics.value) return null
        return withTimeoutOrNull(LYRICS_TIMEOUT_MS) {
            try {
                val res = DesktopLyricsClient.lookup(song, durationMs)
                res.getOrNull()?.lines?.takeIf { lines -> lines.any { it.timeMs > 0 } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    fun isYouTubeId(id: String): Boolean = YOUTUBE_ID.matches(id)

    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

    private suspend fun ensureLoaded() = lock.withLock {
        if (file != null) return@withLock
        val target = DesktopMediaCache.directory.resolve("heatmaps.json").toFile()
        file = target
        withContext(Dispatchers.IO) {
            if (!target.exists()) return@withContext
            runCatching { json.decodeFromString(Stored.serializer(), target.readText()) }
                .onSuccess { kept -> stored.putAll(kept.heats) }
                .onFailure { target.delete() }
        }
    }

    private suspend fun save() = lock.withLock {
        val target = file ?: return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                val kept = stored.entries
                    .sortedByDescending { it.value.fetchedAt }
                    .take(MAX_KEPT)
                    .associate { it.key to it.value }
                target.writeText(json.encodeToString(Stored.serializer(), Stored(heats = kept)))
            }
        }
    }

    private const val HEATMAP_TIMEOUT_MS = 6000L
    private const val LYRICS_TIMEOUT_MS = 4000L
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_KEPT = 1000
}

@Serializable
private data class Stored(val heats: Map<String, StoredHeat> = emptyMap())

@Serializable
private data class StoredHeat(
    val segmentMs: Long = 0L,
    val intensity: List<Float> = emptyList(),
    val fetchedAt: Long = 0L,
) {
    fun heatmap(): Heatmap? =
        if (segmentMs > 0 && intensity.isNotEmpty()) Heatmap(segmentMs, intensity) else null
}
