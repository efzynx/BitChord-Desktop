package com.music.bitchord.desktop

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.playingRowMatcher
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVInputFormat
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry
import org.bytedeco.ffmpeg.global.avcodec.av_get_bits_per_sample
import org.bytedeco.ffmpeg.global.avcodec.avcodec_get_name
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_ATTACHED_PIC
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AV_DICT_IGNORE_SUFFIX
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_NONE
import org.bytedeco.ffmpeg.global.avutil.av_dict_get
import org.bytedeco.ffmpeg.global.avutil.av_get_bytes_per_sample
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class ProbedAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val durationText: String? = null,
    val artworkPath: String? = null,
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
)

internal data class ResolvedAudioQuality(
    val sourceQuality: String?,
    val sourceHiRes: Boolean,
)

internal data class ParsedFileName(val title: String, val artist: String)

internal enum class DesktopLocalGroupKind {
    ARTIST,
    ALBUM,
    FOLDER,
}

internal data class DesktopLocalGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val tracks: List<Song>,
    val kind: DesktopLocalGroupKind,
)

/**
 * Container tag reader, embedded cover art extractor, filename parser, download enricher, and
 * mobile-parity grouper (`Artists`, `Albums`, `Folders`) for local desktop audio libraries.
 */
internal object DesktopLocalMetadata {

    private const val MAX_ARTWORK_BYTES = 20 * 1024 * 1024
    private const val MIN_ARTWORK_BYTES = 64
    private const val CACHE_VERSION = "v3"

    private val SIDECAR_BASE_NAMES = setOf("cover", "folder", "album", "front", "artwork", "albumart")
    private val SIDECAR_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val MULTI_ARTIST_SEMICOLON = Regex("\\s*;\\s*")

    /**
     * Common parenthetical suffixes in filenames that describe the edition or video type rather than
     * the performing artist (e.g. `Track (Official Music Video)`, `Song (2024)`).
     */
    private val NON_ARTIST_PARENS = Regex(
        """^(?:""" +
            """official(?:\s+.*)?|""" +
            """lyrics?(?:\s+.*)?|""" +
            """audio|video|mv|m/v|visualizer|topic|""" +
            """live(?:\s+.*)?|acoustic(?:\s+.*)?|""" +
            """remix|edit|mix|version|ver\.?|cover|""" +
            """remaster(?:ed)?(?:\s+.*)?|""" +
            """slowed(?:\s+.*)?|reverb(?:\s+.*)?|sped\s+up|nightcore|""" +
            """instrumental|karaoke|clean|explicit|""" +
            """(?:feat|ft)\.?\s+.*|""" +
            """\d{1,4}""" +
            """)$""",
        RegexOption.IGNORE_CASE,
    )

    private data class CachedEntry(
        val mtimeSec: Long,
        val sizeBytes: Long,
        val metadata: ProbedAudioMetadata,
    )

    private val lock = Any()
    private val cacheByFile = ConcurrentHashMap<Path, MutableMap<String, CachedEntry>>()

    @Volatile
    private var cacheDirty = false

    fun defaultDataDirectory(): Path {
        val base = if (DesktopPlatform.isWindows) {
            System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: Paths.get(System.getProperty("user.home"), "AppData", "Local").toString()
        } else {
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.local/share"
        }
        return Path.of(base, "bitchord")
    }

    fun defaultArtDirectory(): Path = defaultDataDirectory().resolve("local_art")

    fun defaultCacheFile(): Path = defaultDataDirectory().resolve("local_tags_$CACHE_VERSION.tsv")

    fun probeCached(
        path: Path,
        mtimeSec: Long,
        sizeBytes: Long,
        artDirectory: Path = defaultArtDirectory(),
        cacheFile: Path = defaultCacheFile(),
    ): ProbedAudioMetadata {
        val normalized = path.toAbsolutePath().normalize()
        val key = normalized.toString()
        val cache = cacheFor(cacheFile)
        val cached = cache[key]
        if (cached != null && cached.mtimeSec == mtimeSec && cached.sizeBytes == sizeBytes) {
            val artPath = cached.metadata.artworkPath
            if (artPath == null || Files.isRegularFile(Path.of(artPath))) {
                return cached.metadata
            }
        }
        val fresh = probeFile(normalized, artDirectory)
        synchronized(lock) {
            cache[key] = CachedEntry(mtimeSec, sizeBytes, fresh)
            cacheDirty = true
        }
        return fresh
    }

    fun flushCache(cacheFile: Path = defaultCacheFile()) {
        if (!cacheDirty) return
        val snapshot = synchronized(lock) {
            if (!cacheDirty) return
            cacheDirty = false
            cacheFor(cacheFile).entries.map { (path, entry) ->
                listOf(
                    escapeField(path),
                    entry.mtimeSec.toString(),
                    entry.sizeBytes.toString(),
                    escapeField(entry.metadata.title.orEmpty()),
                    escapeField(entry.metadata.artist.orEmpty()),
                    escapeField(entry.metadata.album.orEmpty()),
                    escapeField(entry.metadata.albumArtist.orEmpty()),
                    entry.metadata.trackNumber?.toString().orEmpty(),
                    escapeField(entry.metadata.durationText.orEmpty()),
                    escapeField(entry.metadata.artworkPath.orEmpty()),
                    escapeField(entry.metadata.codec.orEmpty()),
                    entry.metadata.sampleRateHz?.toString().orEmpty(),
                    entry.metadata.bitDepth?.toString().orEmpty(),
                ).joinToString("\t")
            }
        }
        runCatching {
            Files.createDirectories(cacheFile.parent)
            val tmp = Files.createTempFile(cacheFile.parent, "local-tags-", ".tmp")
            Files.write(tmp, snapshot, StandardCharsets.UTF_8)
            runCatching {
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun probeFile(
        path: Path,
        artDirectory: Path = defaultArtDirectory(),
    ): ProbedAudioMetadata {
        var format: AVFormatContext? = avformat_alloc_context()
        var openedSuccessfully = false
        try {
            val context = format ?: return ProbedAudioMetadata()
            val status = avformat_open_input(
                context,
                path.toAbsolutePath().toString(),
                null as AVInputFormat?,
                null as AVDictionary?,
            )
            if (status < 0) {
                // avformat_open_input frees `context` on failure.
                format = null
                return ProbedAudioMetadata()
            }
            openedSuccessfully = true
            avformat_find_stream_info(context, null as AVDictionary?)

            val tags = LinkedHashMap<String, String>()
            collectDictionary(context.metadata(), tags, overwrite = true)

            val streamCount = context.nb_streams()
            var audioStreamDurationSec: Double? = null
            var artworkPath: String? = null
            var probedCodec: String? = null
            var probedSampleRateHz: Int? = null
            var probedBitDepth: Int? = null

            for (i in 0 until streamCount) {
                val stream = context.streams(i) ?: continue
                val parameters = stream.codecpar()
                val codecType = parameters?.codec_type()
                if (codecType == AVMEDIA_TYPE_AUDIO) {
                    // OGG/Opus/FLAC Vorbis comments commonly attach tags to the audio stream.
                    collectDictionary(stream.metadata(), tags, overwrite = false)
                    if (parameters != null && probedCodec == null) {
                        val codecId = parameters.codec_id()
                        probedCodec = avcodec_get_name(codecId)?.string
                            ?.lowercase(Locale.ROOT)
                            ?.takeIf(String::isNotBlank)
                        probedSampleRateHz = parameters.sample_rate().takeIf { it > 0 }
                        val rawBits = parameters.bits_per_raw_sample().takeIf { it > 0 }
                        val codecBits = av_get_bits_per_sample(codecId).takeIf { it > 0 }
                        val codedBits = parameters.bits_per_coded_sample().takeIf { it > 0 }
                        val sampleFmtBits = parameters.format()
                            .takeIf { it != AV_SAMPLE_FMT_NONE }
                            ?.let { fmt -> av_get_bytes_per_sample(fmt).takeIf { it > 0 }?.times(8) }
                        // Prefer exact encoded bit depth (`bits_per_raw_sample` for FLAC/ALAC,
                        // `av_get_bits_per_sample` / `bits_per_coded_sample` for PCM) before sample_fmt.
                        probedBitDepth = rawBits ?: codecBits ?: codedBits ?: sampleFmtBits
                    }
                    if (audioStreamDurationSec == null && stream.duration() > 0) {
                        val tb = stream.time_base()
                        if (tb.den() > 0) {
                            val sec = stream.duration() * (tb.num().toDouble() / tb.den().toDouble())
                            if (sec > 0.0) audioStreamDurationSec = sec
                        }
                    }
                }
                if (artworkPath == null && (stream.disposition() and AV_DISPOSITION_ATTACHED_PIC) != 0) {
                    artworkPath = extractAttachedPicture(stream.attached_pic(), artDirectory)
                }
            }

            val containerDurationUs = context.duration()
            val durationText = when {
                containerDurationUs != AV_NOPTS_VALUE && containerDurationUs > 0 ->
                    formatDurationSeconds(((containerDurationUs + 500_000L) / 1_000_000L))
                audioStreamDurationSec != null && audioStreamDurationSec > 0.0 ->
                    formatDurationSeconds(kotlin.math.round(audioStreamDurationSec).toLong())
                else -> null
            }

            val albumArtist = firstTag(tags, "album_artist", "albumartist", "album artist", "tpe2")
                ?.replace(MULTI_ARTIST_SEMICOLON, ", ")
            val trackArtist = (firstTag(tags, "artist", "tpe1", "author", "performer") ?: albumArtist)
                ?.replace(MULTI_ARTIST_SEMICOLON, ", ")
            return ProbedAudioMetadata(
                title = firstTag(tags, "title", "tit2"),
                artist = trackArtist,
                album = firstTag(tags, "album", "talb"),
                albumArtist = albumArtist,
                trackNumber = parseTrackNumber(firstTag(tags, "track", "trck", "tracknumber")),
                durationText = durationText,
                artworkPath = artworkPath,
                codec = probedCodec,
                sampleRateHz = probedSampleRateHz,
                bitDepth = probedBitDepth,
            )
        } catch (_: Throwable) {
            return ProbedAudioMetadata()
        } finally {
            if (openedSuccessfully && format != null) {
                runCatching { avformat_close_input(format) }
            }
        }
    }

    fun findSidecarArtwork(audioFile: Path): String? {
        val parent = audioFile.parent ?: return null
        if (!Files.isDirectory(parent)) return null
        return runCatching {
            Files.list(parent).use { entries ->
                entries
                    .filter { Files.isRegularFile(it) }
                    .filter { candidate ->
                        val name = candidate.fileName?.toString().orEmpty()
                        val dot = name.lastIndexOf('.')
                        if (dot <= 0) return@filter false
                        val base = name.substring(0, dot).lowercase(Locale.ROOT)
                        val ext = name.substring(dot + 1).lowercase(Locale.ROOT)
                        base in SIDECAR_BASE_NAMES && ext in SIDECAR_EXTENSIONS
                    }
                    .sorted()
                    .findFirst()
                    .orElse(null)
                    ?.toAbsolutePath()
                    ?.normalize()
                    ?.toUri()
                    ?.toString()
            }
        }.getOrNull()
    }

    /**
     * Parses track title and artist from an untagged filename stem.
     * Supports both `"Artist - Title"` (BitChord downloads) and `"Title (Artist)"`.
     */
    fun parseFileName(
        name: String,
        unknownArtist: String = DEFAULT_UNKNOWN_ARTIST,
    ): ParsedFileName {
        val clean = name.trim()
        if (clean.isEmpty()) return ParsedFileName(title = name, artist = unknownArtist)

        val dashParts = clean.split(" - ", limit = 2)
        if (dashParts.size == 2 && dashParts[0].isNotBlank() && dashParts[1].isNotBlank()) {
            return ParsedFileName(
                title = dashParts[1].trim(),
                artist = dashParts[0].trim(),
            )
        }

        if (clean.endsWith(')')) {
            val openIndex = findMatchingOpenParen(clean, clean.length - 1)
            if (openIndex > 0) {
                val prefix = clean.substring(0, openIndex).trim()
                val inside = clean.substring(openIndex + 1, clean.length - 1).trim()
                if (prefix.isNotEmpty() && inside.isNotEmpty() && !NON_ARTIST_PARENS.matches(inside)) {
                    return ParsedFileName(title = prefix, artist = inside)
                }
            }
        }

        return ParsedFileName(title = clean, artist = unknownArtist)
    }

    /**
     * Merges container-less downloads (e.g. YouTube `.webm` files in `~/Music/BitChord`) with
     * their stored download catalog record so artwork, albums, artists, and durations still show.
     */
    fun enrichWithDownloads(songs: List<Song>, downloads: List<Song>): List<Song> {
        if (songs.isEmpty() || downloads.isEmpty()) return songs
        val byPath = HashMap<Path, Song>(downloads.size)
        for (dl in downloads) {
            val resolved = DesktopDownloadManager.savedFile(dl)
                ?: dl.localPath?.let { runCatching { Path.of(it) }.getOrNull() }
                ?: continue
            byPath.putIfAbsent(resolved.toAbsolutePath().normalize(), dl)
        }
        if (byPath.isEmpty()) return songs
        return songs.map { local ->
            val path = local.localPath?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
            val dl = if (path != null) byPath[path] else null
            if (dl == null) return@map local

            val keepLocalArtist = !local.artist.isPlaceholderArtist()
            val mergedArtist = when {
                keepLocalArtist -> local.artist
                !dl.artist.isPlaceholderArtist() -> dl.artist
                else -> local.artist
            }
            val mergedArtists = when {
                local.artists.isNotEmpty() -> local.artists
                !keepLocalArtist || mergedArtist.equals(dl.artist, ignoreCase = true) -> dl.artists
                else -> emptyList()
            }
            local.copy(
                title = if (local.title.isNotBlank()) local.title else dl.title,
                artist = mergedArtist,
                artists = mergedArtists,
                thumbnailUrl = local.thumbnailUrl ?: dl.thumbnailUrl,
                durationText = local.durationText ?: dl.durationText,
                albumId = local.albumId ?: dl.albumId,
                albumName = local.albumName?.takeIf(String::isNotBlank) ?: dl.albumName,
                trackNumber = local.trackNumber ?: dl.trackNumber,
            )
        }
    }

    /**
     * Derives the list badge [Song.sourceQuality] and [Song.sourceHiRes] from probed FFmpeg stream
     * properties, matching [DesktopStreamFormat.isHiRes] (`bitDepth > 16 || sampleRateHz > 48_000`)
     * so list rows and the Now Playing screen agree on `Lossless` vs `Hi-Res Lossless`.
     */
    fun resolveAudioQuality(
        probed: ProbedAudioMetadata,
        extension: String,
        fallbackFormat: DesktopStreamFormat? = null,
    ): ResolvedAudioQuality {
        val extLower = extension.lowercase(Locale.ROOT)
        val extCodec = when (extLower) {
            "flac" -> "FLAC"
            "wav" -> "WAV"
            "alac" -> "ALAC"
            "aiff", "aif" -> "AIFF"
            "aac", "m4a" -> "AAC"
            "ogg" -> "OGG"
            "webm" -> "OPUS"
            else -> extLower.uppercase(Locale.ROOT).takeIf(String::isNotBlank)
        }
        val format = DesktopStreamFormat(
            codec = probed.codec ?: fallbackFormat?.codec ?: extCodec,
            sampleRateHz = probed.sampleRateHz ?: fallbackFormat?.sampleRateHz,
            bitDepth = probed.bitDepth ?: fallbackFormat?.bitDepth,
        )
        val sourceQuality = when {
            extLower == "wav" && format.isLossless -> "WAV"
            extLower in AIFF_EXTENSIONS && format.isLossless -> "AIFF"
            else -> format.codecLabel ?: extCodec
        }
        return ResolvedAudioQuality(
            sourceQuality = sourceQuality,
            sourceHiRes = format.isHiRes,
        )
    }

    /**
     * Refreshes stored `history` / `playlists` entries backed by local files using freshly probed
     * [localCatalog] metadata so Hi-Res / Lossless quality and cover updates propagate everywhere.
     */
    fun enrichWithLocalCatalog(songs: List<Song>, localCatalog: List<Song>): List<Song> {
        if (songs.isEmpty() || localCatalog.isEmpty()) return songs
        val byVideoId = HashMap<String, Song>(localCatalog.size)
        val byPath = HashMap<String, Song>(localCatalog.size)
        for (local in localCatalog) {
            byVideoId.putIfAbsent(local.videoId, local)
            local.localPath?.let { byPath.putIfAbsent(it, local) }
        }
        return songs.map { item ->
            val match = item.localPath?.let(byPath::get) ?: byVideoId[item.videoId] ?: return@map item
            val quality = match.sourceQuality ?: item.sourceQuality
            val hiRes = match.sourceHiRes || (quality == item.sourceQuality && item.sourceHiRes)
            if (item.sourceQuality == quality && item.sourceHiRes == hiRes &&
                (item.thumbnailUrl != null || match.thumbnailUrl == null)
            ) {
                item
            } else {
                item.copy(
                    sourceQuality = quality,
                    sourceHiRes = hiRes,
                    thumbnailUrl = item.thumbnailUrl ?: match.thumbnailUrl,
                )
            }
        }
    }

    private val AIFF_EXTENSIONS = setOf("aiff", "aif")

    private fun cacheFor(cacheFile: Path): MutableMap<String, CachedEntry> {
        cacheByFile[cacheFile]?.let { return it }
        val loaded = loadCache(cacheFile)
        val existing = cacheByFile.putIfAbsent(cacheFile, loaded)
        return existing ?: loaded
    }

    private fun loadCache(cacheFile: Path): MutableMap<String, CachedEntry> {
        val map = LinkedHashMap<String, CachedEntry>()
        if (!Files.isRegularFile(cacheFile)) return map
        runCatching {
            Files.readAllLines(cacheFile, StandardCharsets.UTF_8).forEach { line ->
                if (line.isBlank()) return@forEach
                val cols = line.split('\t')
                if (cols.size < 13) return@forEach
                val path = unescapeField(cols[0])
                val mtime = cols[1].toLongOrNull() ?: return@forEach
                val size = cols[2].toLongOrNull() ?: return@forEach
                map[path] = CachedEntry(
                    mtimeSec = mtime,
                    sizeBytes = size,
                    metadata = ProbedAudioMetadata(
                        title = unescapeField(cols[3]).takeIf(String::isNotBlank),
                        artist = unescapeField(cols[4]).takeIf(String::isNotBlank),
                        album = unescapeField(cols[5]).takeIf(String::isNotBlank),
                        albumArtist = unescapeField(cols[6]).takeIf(String::isNotBlank),
                        trackNumber = cols[7].toIntOrNull(),
                        durationText = unescapeField(cols[8]).takeIf(String::isNotBlank),
                        artworkPath = unescapeField(cols[9]).takeIf(String::isNotBlank),
                        codec = unescapeField(cols[10]).takeIf(String::isNotBlank),
                        sampleRateHz = cols[11].toIntOrNull(),
                        bitDepth = cols[12].toIntOrNull(),
                    ),
                )
            }
        }
        return map
    }

    private fun collectDictionary(
        dict: AVDictionary?,
        into: MutableMap<String, String>,
        overwrite: Boolean,
    ) {
        if (dict == null || dict.isNull) return
        var prev: AVDictionaryEntry? = null
        while (true) {
            val entry = av_dict_get(dict, "", prev, AV_DICT_IGNORE_SUFFIX) ?: break
            if (entry.isNull) break
            val key = entry.key()?.string?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val value = entry.value()?.string?.trim().orEmpty()
            if (key.isNotEmpty() && value.isNotEmpty() && (overwrite || key !in into)) {
                into[key] = value
            }
            prev = entry
        }
    }

    private fun firstTag(tags: Map<String, String>, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> tags[k]?.takeIf(String::isNotBlank) }

    private fun extractAttachedPicture(
        packet: org.bytedeco.ffmpeg.avcodec.AVPacket?,
        artDirectory: Path,
    ): String? {
        if (packet == null || packet.isNull) return null
        val size = packet.size()
        val data = packet.data()
        if (size !in MIN_ARTWORK_BYTES..MAX_ARTWORK_BYTES || data == null || data.isNull) return null
        val bytes = ByteArray(size)
        data.get(bytes, 0, size)
        val ext = imageExtension(bytes)
        val hash = sha256Prefix16(bytes)
        return runCatching {
            Files.createDirectories(artDirectory)
            val target = artDirectory.resolve("$hash.$ext")
            if (!Files.isRegularFile(target) || Files.size(target) != size.toLong()) {
                val tmp = Files.createTempFile(artDirectory, "art-", ".$ext.tmp")
                Files.write(tmp, bytes)
                runCatching {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            target.toAbsolutePath().normalize().toString()
        }.getOrNull()
    }

    private fun imageExtension(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "webp"
        else -> "img"
    }

    private fun sha256Prefix16(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(32) {
            for (i in 0 until 16) {
                val b = digest[i].toInt() and 0xFF
                if (b < 0x10) append('0')
                append(b.toString(16))
            }
        }
    }

    private fun findMatchingOpenParen(text: String, closeIndex: Int): Int {
        var depth = 0
        for (i in closeIndex downTo 0) {
            when (text[i]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    internal fun parseTrackNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val leading = raw.trim().takeWhile(Char::isDigit)
        return leading.toIntOrNull()?.takeIf { it > 0 }
    }

    internal fun formatDurationSeconds(totalSeconds: Long): String? {
        if (totalSeconds <= 0L) return null
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    private fun escapeField(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun unescapeField(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> out.append('\\')
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> {
                        out.append(ch).append(value[i + 1])
                    }
                }
                i += 2
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private const val DEFAULT_UNKNOWN_ARTIST = "Unknown artist"
}

internal fun String.isPlaceholderArtist(): Boolean {
    val clean = trim()
    return clean.isEmpty() ||
        clean.equals("unknown", ignoreCase = true) ||
        clean.equals("unknown artist", ignoreCase = true) ||
        clean.equals("unknown artist(s)", ignoreCase = true) ||
        clean.equals("on this computer", ignoreCase = true) ||
        clean.equals("artis tidak diketahui", ignoreCase = true)
}

/**
 * Grouping helpers matching Android's `LocalGroups` (`artistsOf`, `albumsOf`, `foldersOf`),
 * avoiding fake `"Unknown Album"` buckets for untagged singles and sorting albums by track number.
 */
internal object DesktopLocalGroups {

    fun artistsOf(
        songs: List<Song>,
        songCountLabel: (Int) -> String,
        unknownArtist: String = "Unknown artist",
    ): List<DesktopLocalGroup> {
        val grouped = LinkedHashMap<String, MutableList<Song>>()
        val labels = LinkedHashMap<String, String>()
        for (song in songs) {
            val display = song.artist.takeIf { !it.isPlaceholderArtist() }?.trim() ?: unknownArtist
            val key = if (display == unknownArtist) "\uFFFF" else display.lowercase(Locale.ROOT)
            grouped.getOrPut(key) { mutableListOf() }.add(song)
            labels.putIfAbsent(key, display)
        }
        return grouped.entries
            .sortedWith(compareBy { if (it.key == "\uFFFF") "\uFFFF" else it.key })
            .map { (key, tracks) ->
                val title = labels.getValue(key)
                DesktopLocalGroup(
                    id = "artist:$key",
                    title = title,
                    subtitle = songCountLabel(tracks.size),
                    artworkUrl = tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                    tracks = tracks,
                    kind = DesktopLocalGroupKind.ARTIST,
                )
            }
    }

    fun albumsOf(
        songs: List<Song>,
        songCountLabel: (Int) -> String,
    ): List<DesktopLocalGroup> {
        val tagged = songs.filter { !it.albumName.isNullOrBlank() }
        if (tagged.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<Song>>()
        for (song in tagged) {
            val albumName = song.albumName!!.trim()
            val key = song.albumId?.takeIf { it.isNotBlank() }?.let { "id:$it" }
                ?: "name:${albumName.lowercase(Locale.ROOT)}"
            grouped.getOrPut(key) { mutableListOf() }.add(song)
        }
        return grouped.entries
            .map { (key, rawTracks) ->
                val sorted = rawTracks.sortedWith(
                    compareBy<Song> { it.trackNumber ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER, Song::title),
                )
                val first = sorted.first()
                val title = first.albumName!!.trim()
                val artistNames = sorted
                    .flatMap { it.artist.split(';', ',') }
                    .map(String::trim)
                    .filter { it.isNotBlank() && !it.isPlaceholderArtist() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                val subtitle = when {
                    artistNames.size == 1 -> "${artistNames.first()} · ${songCountLabel(sorted.size)}"
                    artistNames.size > 1 -> "${artistNames.take(2).joinToString(", ")} · ${songCountLabel(sorted.size)}"
                    else -> songCountLabel(sorted.size)
                }
                DesktopLocalGroup(
                    id = key,
                    title = title,
                    subtitle = subtitle,
                    artworkUrl = sorted.firstNotNullOfOrNull { it.thumbnailUrl },
                    tracks = sorted,
                    kind = DesktopLocalGroupKind.ALBUM,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, DesktopLocalGroup::title))
    }

    fun foldersOf(
        songs: List<Song>,
        songCountLabel: (Int) -> String,
    ): List<DesktopLocalGroup> {
        val grouped = LinkedHashMap<Path, MutableList<Song>>()
        for (song in songs) {
            val path = song.localPath?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
                ?: continue
            val parent = path.parent ?: continue
            grouped.getOrPut(parent) { mutableListOf() }.add(song)
        }
        return grouped.entries
            .map { (dir, tracks) ->
                val folderName = dir.fileName?.toString()?.takeIf(String::isNotBlank) ?: dir.toString()
                DesktopLocalGroup(
                    id = "folder:$dir",
                    title = folderName,
                    subtitle = "${readablePath(dir)} · ${songCountLabel(tracks.size)}",
                    artworkUrl = tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                    tracks = tracks,
                    kind = DesktopLocalGroupKind.FOLDER,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, DesktopLocalGroup::title))
    }

    fun readablePath(directory: Path): String {
        val full = directory.toAbsolutePath().normalize().toString()
        val home = System.getProperty("user.home")?.takeIf(String::isNotBlank)?.let {
            runCatching { Path.of(it).toAbsolutePath().normalize().toString() }.getOrNull()
        }
        return when {
            home == null -> full
            full == home -> "~"
            full.startsWith("$home${directory.fileSystem.separator}") ->
                "~${full.removePrefix(home)}"
            else -> full
        }
    }
}

/**
 * Returns whether [currentSong] belongs to the specific collection/playlist/group identified by
 * [sourceId]. When [currentSong] carries a `playbackSourceId`, it must match [sourceId] strictly
 * so opening a different playlist or group that shares the same song keeps showing Play instead of
 * Pause. When [currentSong] carries no source id, falls back to [tracks] membership only if it did
 * not come from an unrelated explicit surface (like Search or History).
 */
internal fun isContextSourceMatch(
    currentSong: Song?,
    sourceId: String?,
    tracks: List<Song>,
    isCurrentRow: (Song) -> Boolean = playingRowMatcher(tracks, currentSong),
): Boolean {
    currentSong ?: return false
    if (tracks.none(isCurrentRow)) return false
    val activeSourceId = currentSong.playbackSourceId?.takeIf(String::isNotBlank)
    if (activeSourceId != null) {
        return !sourceId.isNullOrBlank() && activeSourceId == sourceId
    }
    val type = currentSong.playbackSourceType
    return type == null || type == PlaybackSourceType.QUEUE
}

/**
 * Global Play/Pause state matcher for the Local Music / Downloads main header.
 * Returns true whenever any local song, artist/album/folder group, liked-local view, or local
 * playlist under [persistenceKey] is currently the active playback source.
 */
internal fun isGlobalLocalMusicMatch(
    currentSong: Song?,
    librarySongs: List<Song>,
    localPlaylistIds: Set<String> = emptySet(),
    persistenceKey: String = "local",
    includePlaylists: Boolean = true,
): Boolean {
    currentSong ?: return false
    val sourceId = currentSong.playbackSourceId?.takeIf(String::isNotBlank)
    if (sourceId != null) {
        if (sourceId == "$persistenceKey:all" || sourceId.startsWith("$persistenceKey:")) {
            return true
        }
        if (includePlaylists && sourceId in localPlaylistIds) {
            return true
        }
        // Explicitly originated from an unrelated non-local collection/radio/search/browse context.
        return false
    }
    if (currentSong.videoId.startsWith("local:") || currentSong.localPath != null) {
        val matcher = playingRowMatcher(librarySongs, currentSong)
        return librarySongs.any(matcher)
    }
    return false
}

/**
 * Returns whether [group] (an Artist, Album, or Folder in Local Music / Downloads) should show the
 * active playing indicator for [currentSong].
 *
 * If playback was explicitly started from a specific sibling group of the *same* kind (for instance,
 * playing Album A while also inspecting Album B), only the active group ID matches. Otherwise
 * (e.g. playing from Songs, a playlist, or another tab), any group containing [currentSong] matches
 * so the active artist avatar, album cover, and folder cover all indicate playback.
 */
internal fun isGroupActiveInLocalTab(
    currentSong: Song?,
    group: DesktopLocalGroup,
    persistenceKey: String = "local",
): Boolean {
    currentSong ?: return false
    val sourceId = currentSong.playbackSourceId?.takeIf(String::isNotBlank)
    val groupSourceId = "$persistenceKey:${group.id}"
    if (sourceId == groupSourceId) return true
    val sameKindSourceActive = when (group.kind) {
        DesktopLocalGroupKind.ALBUM ->
            sourceId != null && (sourceId.startsWith("$persistenceKey:id:") || sourceId.startsWith("$persistenceKey:name:"))
        DesktopLocalGroupKind.ARTIST ->
            sourceId != null && sourceId.startsWith("$persistenceKey:artist:")
        DesktopLocalGroupKind.FOLDER ->
            sourceId != null && sourceId.startsWith("$persistenceKey:folder:")
    }
    if (sameKindSourceActive) return false
    val matcher = playingRowMatcher(group.tracks, currentSong)
    return group.tracks.any(matcher)
}
