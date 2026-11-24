package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import okhttp3.Request
import java.util.LinkedHashMap
import kotlin.math.abs

/**
 * Resolves HLS master playlists into single-variant video streams.
 *
 * Apple Music serves motion artwork (both artist banner clips and track canvases) as multi-variant
 * HLS master playlists (.m3u8) that bundle dozens of alternative streams (such as HEVC/H.265 up to 4K,
 * trick play streams, and multiple audio language feeds).
 *
 * Opening such master playlists directly in FFmpeg causes intense network probing and stream negotiation
 * stalls or timeouts. By resolving the master playlist into a dedicated AVC1/H.264 ~720p sub-variant,
 * FFmpeg opens and decodes video frames in milliseconds with low CPU usage.
 */
internal object DesktopHlsResolver {

    private const val CACHE_SIZE = 64
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val cache = object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > CACHE_SIZE
    }

    /**
     * If [url] is an HLS master playlist, resolves and returns the direct URL of the best single-stream
     * variant (AVC1/H.264 at ~720p). If [url] is already a sub-variant or not an HLS playlist,
     * returns [url] as is.
     */
    fun resolve(url: String): String {
        if (!url.contains(".m3u8", ignoreCase = true)) return url
        synchronized(cache) { cache[url] }?.let { return it }

        val playlistText = fetchText(url) ?: return url
        if (!playlistText.contains("#EXT-X-STREAM-INF")) {
            // Already a media playlist (single stream)
            synchronized(cache) { cache[url] = url }
            return url
        }

        val resolved = parseBestVariant(url, playlistText) ?: url
        synchronized(cache) { cache[url] = resolved }
        return resolved
    }

    private data class Variant(
        val codecs: String,
        val width: Int,
        val height: Int,
        val uri: String,
    )

    private fun parseBestVariant(masterUrl: String, playlistText: String): String? {
        val lines = playlistText.lines()
        val variants = ArrayList<Variant>()

        var currentInf: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                currentInf = trimmed
            } else if (currentInf != null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val codecsMatch = Regex("""CODECS="([^"]+)"""").find(currentInf)
                val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(currentInf)
                val codecs = codecsMatch?.groupValues?.get(1)?.lowercase() ?: ""
                val width = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

                val absoluteUri = when {
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                    trimmed.startsWith("/") -> {
                        val origin = masterUrl.substringBefore('/', "https://").let { proto ->
                            val withoutProto = masterUrl.substringAfter("://")
                            val host = withoutProto.substringBefore('/')
                            "${masterUrl.substringBefore("://")}://$host"
                        }
                        "$origin$trimmed"
                    }
                    else -> {
                        val base = masterUrl.substringBeforeLast('/')
                        "$base/$trimmed"
                    }
                }
                variants.add(Variant(codecs, width, height, absoluteUri))
                currentInf = null
            }
        }

        if (variants.isEmpty()) return null

        // Filter for AVC1 / H.264 video streams
        val avcVariants = variants.filter { it.codecs.contains("avc1") || it.codecs.contains("h264") }
        val candidates = if (avcVariants.isNotEmpty()) avcVariants else variants

        // Prefer resolution closest to 720p height (ideal sweet spot for desktop banner clarity & performance)
        val best = candidates.minByOrNull { v ->
            val targetHeight = 720
            val h = if (v.height > 0) v.height else targetHeight
            abs(h - targetHeight)
        } ?: candidates.first()

        return best.uri
    }

    private fun fetchText(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body.string() else null
        }
    }.getOrNull()
}
