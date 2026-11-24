package com.music.bitchord.desktop

import com.music.bitchord.data.AppleCatalogue
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder
import java.util.LinkedHashMap

data class DesktopArtistArt(
    val heroUrl: String? = null,
    val logoUrl: String? = null,
    val videoUrl: String? = null,
)

/**
 * Finds motion artwork and high-resolution hero media for artists from Apple Music.
 *
 * Like the mobile app, this searches Apple Music for the artist, verifies they match the YouTube
 * artist using catalogue track/album titles via [AppleCatalogue], and extracts motion video banners
 * (HLS .m3u8 or MP4) as well as backdrop artwork.
 */
internal object DesktopArtistArtRepository {

    private const val CACHE_SIZE = 64
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"

    private class Entry(val art: DesktopArtistArt?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > CACHE_SIZE
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun cached(browseId: String): DesktopArtistArt? =
        synchronized(cache) { cache[browseId] }?.art

    suspend fun artFor(
        browseId: String,
        artistName: String,
        titles: Collection<String>,
    ): DesktopArtistArt? {
        val key = AppleCatalogue.matchKey(artistName)
        if (key.isEmpty()) return null
        synchronized(cache) { cache[browseId] }?.let { return it.art }

        val keys = AppleCatalogue.keys(titles)
        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, key, keys) }
                .onFailure { DesktopTrackLog.log("artist art: lookup failed for $artistName: ${it.message}") }
                .getOrNull()
        } ?: return null

        synchronized(cache) { cache[browseId] = entry }
        return entry.art
    }

    private fun lookup(artistName: String, key: String, titles: Set<String>): Entry? {
        val searchUrl = "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}&entity=musicArtist&limit=5"
        val searchBody = httpGet(searchUrl) ?: return null
        val searchJson = runCatching { json.parseToJsonElement(searchBody).jsonObject }.getOrNull() ?: return null
        val results = searchJson["results"]?.jsonArray ?: return null

        var pageUrl: String? = null
        for (item in results) {
            val r = item.jsonObject
            val candidateName = r["artistName"]?.jsonPrimitive?.content ?: continue
            if (AppleCatalogue.matchKey(candidateName) != key) continue
            val id = r["artistId"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
            if (titles.isNotEmpty() && !AppleCatalogue.sharesTitles(id, titles)) {
                continue
            }
            pageUrl = r["artistLinkUrl"]?.jsonPrimitive?.content?.substringBefore('?')
                ?: "https://music.apple.com/us/artist/$id"
            break
        }

        if (pageUrl == null) return Entry(null)

        val html = httpGet(pageUrl) ?: return null
        return Entry(parse(html))
    }

    private fun httpGet(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body.string() else null
        }
    }.getOrNull()

    private fun parse(html: String): DesktopArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val raw = html.substring(start, end)
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val header = findHeader(root) ?: return null

        // Try wide video first (16:9 banner), then standard square (1:1) video, or any video in videoArtwork
        val wideVideo = header["wideVideoArtwork"]?.findVideoUrl()
        val squareVideo = header["videoArtwork"]?.findVideoUrl()
        val rawVideo = wideVideo ?: squareVideo
        val videoUrl = rawVideo?.let { DesktopHlsResolver.resolve(it) }

        val hero = (header["wideArtwork"] as? JsonObject)?.get("dictionary") as? JsonObject
            ?: (header["artwork"] as? JsonObject)?.get("dictionary") as? JsonObject
            ?: (header["colorBackdropArtwork"] as? JsonObject)?.get("dictionary") as? JsonObject
        val rawHeroUrl = hero?.get("url")?.jsonPrimitive?.content
        val heroUrl = rawHeroUrl?.replace("{w}x{h}{c}.{f}", "1920x1080sr.jpg")
            ?.replace("{w}x{h}", "1920x1080")

        val logo = (header["artistLogo"] as? JsonObject)?.get("dictionary") as? JsonObject
        val logoUrl = logo?.get("url")?.jsonPrimitive?.content
            ?.replace("{w}x{h}{c}.{f}", "1000x500sr.png")

        if (heroUrl == null && videoUrl == null) return null

        return DesktopArtistArt(
            heroUrl = heroUrl,
            logoUrl = logoUrl,
            videoUrl = videoUrl,
        )
    }

    private fun JsonElement.findVideoUrl(): String? {
        if (this is JsonObject) {
            val dict = this["dictionary"] as? JsonObject ?: this
            val preferredKeys = listOf(
                "motionArtistWide16x9",
                "motionArtistFullscreen16x9",
                "motionArtistSquare1x1",
            )
            for (k in preferredKeys) {
                val clip = dict[k] as? JsonObject
                val video = clip?.get("video")?.jsonPrimitive?.content
                if (!video.isNullOrBlank() && video.startsWith("http")) return video
            }
            for ((_, v) in dict) {
                if (v is JsonObject) {
                    val video = v["video"]?.jsonPrimitive?.content
                    if (!video.isNullOrBlank() && video.startsWith("http")) return video
                }
            }
        }
        return null
    }

    private fun findHeader(node: JsonElement): JsonObject? {
        if (node is JsonObject) {
            if (node.containsKey("artistLogo") || node.containsKey("videoArtwork") || node.containsKey("wideVideoArtwork")) {
                return node
            }
            for ((_, v) in node) {
                val res = findHeader(v)
                if (res != null) return res
            }
        } else if (node is JsonArray) {
            for (item in node) {
                val res = findHeader(item)
                if (res != null) return res
            }
        }
        return null
    }
}
