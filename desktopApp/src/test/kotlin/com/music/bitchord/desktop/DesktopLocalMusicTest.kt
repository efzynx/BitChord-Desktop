package com.music.bitchord.desktop

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.SourceQuality
import com.music.bitchord.ui.components.sourceQualityBadge
import kotlin.test.assertFalse
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopLocalMusicTest {

    private val scratch: Path = Files.createTempDirectory("bitchord-local-music-test")

    @AfterTest
    fun cleanUp() {
        scratch.toFile().deleteRecursively()
    }

    @Test
    fun `parseFileName handles artist-title, title-paren-artist, and ignores edition tags`() {
        assertEquals(
            ParsedFileName(title = "Tentang Cinta", artist = "Ipang"),
            DesktopLocalMetadata.parseFileName("Ipang - Tentang Cinta"),
        )
        assertEquals(
            ParsedFileName(title = "Alamak", artist = "Rizky Febian, Adrian Khalif"),
            DesktopLocalMetadata.parseFileName("Alamak (Rizky Febian, Adrian Khalif)"),
        )
        assertEquals(
            ParsedFileName(title = "8", artist = "Billie Eilish"),
            DesktopLocalMetadata.parseFileName("8 (Billie Eilish)"),
        )
        assertEquals(
            ParsedFileName(title = "Track (Official Music Video)", artist = "Unknown artist"),
            DesktopLocalMetadata.parseFileName("Track (Official Music Video)", unknownArtist = "Unknown artist"),
        )
    }

    @Test
    fun `probeFile extracts ID3 tags, track number, duration, and attached JPEG cover art via FFmpeg`() {
        val jpeg = createSampleJpeg(8, 8)
        val mp3File = scratch.resolve("tagged-track.mp3")
        Files.write(
            mp3File,
            buildTaggedMp3(
                title = "Midnight City",
                artist = "M83",
                album = "Hurry Up, We're Dreaming",
                track = "6/22",
                coverJpeg = jpeg,
            ),
        )
        val artDir = scratch.resolve("art")

        val probed = DesktopLocalMetadata.probeFile(mp3File, artDirectory = artDir)
        assertEquals("Midnight City", probed.title)
        assertEquals("M83", probed.artist)
        assertEquals("Hurry Up, We're Dreaming", probed.album)
        assertEquals(6, probed.trackNumber)
        assertNotNull(probed.durationText)
        val extractedPath = assertNotNull(probed.artworkPath)
        assertTrue(Files.isRegularFile(Path.of(extractedPath)))
        assertEquals(jpeg.size.toLong(), Files.size(Path.of(extractedPath)))

        val decoded = ImageIO.read(Path.of(extractedPath).toFile())
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }

    @Test
    fun `enrichWithDownloads fills metadata for untagged downloaded files on disk`() {
        val trackFile = scratch.resolve("BitChord/Ipang - Tentang Cinta.webm")
        Files.createDirectories(trackFile.parent)
        Files.write(trackFile, ByteArray(1024))

        val local = Song(
            videoId = "local:${trackFile.toAbsolutePath().normalize()}",
            title = "Tentang Cinta",
            artist = "Ipang",
            thumbnailUrl = null,
            localUri = trackFile.toUri().toString(),
            localPath = trackFile.toAbsolutePath().normalize().toString(),
        )
        val dl = Song(
            videoId = "yt123456789",
            title = "Tentang Cinta",
            artist = "Ipang Lazuardi",
            thumbnailUrl = "https://lh3.googleusercontent.com/cover=w544",
            durationText = "3:51",
            albumName = "Ost. Laskar Pelangi",
            localPath = trackFile.toAbsolutePath().normalize().toString(),
        )

        val enriched = DesktopLocalMetadata.enrichWithDownloads(listOf(local), listOf(dl))
        assertEquals(1, enriched.size)
        assertEquals("https://lh3.googleusercontent.com/cover=w544", enriched.single().thumbnailUrl)
        assertEquals("Ost. Laskar Pelangi", enriched.single().albumName)
        assertEquals("3:51", enriched.single().durationText)
        // Local file kept its non-placeholder artist, while untagged file gets album & art from record.
        assertEquals("Ipang", enriched.single().artist)
    }

    @Test
    fun `groups omit untagged albums, sort album tracks by trackNumber, and group folders cleanly`() {
        val dirA = scratch.resolve("Albums/Departer")
        val dirB = scratch.resolve("Singles")
        Files.createDirectories(dirA)
        Files.createDirectories(dirB)

        val s1 = Song("1", "Second", "Artist A", "file:///art2.jpg", albumName = "Album X", trackNumber = 2, localPath = dirA.resolve("2.m4a").toString())
        val s2 = Song("2", "First", "Artist A", "file:///art1.jpg", albumName = "Album X", trackNumber = 1, localPath = dirA.resolve("1.m4a").toString())
        val s3 = Song("3", "Loose Single", "Unknown artist", null, albumName = null, localPath = dirB.resolve("3.webm").toString())

        val albums = DesktopLocalGroups.albumsOf(listOf(s1, s2, s3)) { "$it songs" }
        assertEquals(1, albums.size)
        assertEquals("Album X", albums.single().title)
        assertEquals("Artist A · 2 songs", albums.single().subtitle)
        assertEquals(listOf("First", "Second"), albums.single().tracks.map(Song::title))

        val multiArtistAlbum = DesktopLocalGroups.albumsOf(
            listOf(
                Song("m1", "Solo", "Billie Eilish", null, albumName = "dont smile at me"),
                Song("m2", "Collab", "Billie Eilish;Vince Staples", null, albumName = "dont smile at me"),
            ),
        ) { "$it songs" }
        assertEquals("Billie Eilish, Vince Staples · 2 songs", multiArtistAlbum.single().subtitle)

        val artists = DesktopLocalGroups.artistsOf(listOf(s1, s2, s3), { "$it songs" }, unknownArtist = "Unknown artist")
        assertEquals(listOf("Artist A", "Unknown artist"), artists.map(DesktopLocalGroup::title))

        val folders = DesktopLocalGroups.foldersOf(listOf(s1, s2, s3)) { "$it songs" }
        assertEquals(listOf("Departer", "Singles"), folders.map(DesktopLocalGroup::title))
        assertNull(folders.last().artworkUrl)
    }

    @Test
    fun `context source matching keeps other playlists on Play even when sharing the same song`() {
        val sharedSong = Song("yt-shared", "Shared Song", "Artist", null)
        val playlistASong = Song("yt-a-only", "Only A", "Artist", null)
        val playlistBSong = Song("yt-b-only", "Only B", "Artist", null)

        val playlistA = listOf(playlistASong, sharedSong)
        val playlistB = listOf(sharedSong, playlistBSong)

        val playingFromA = sharedSong.copy(
            playbackSource = "Playlist A",
            playbackSourceType = PlaybackSourceType.BROWSE,
            playbackSourceId = "pl_A",
        )

        assertTrue(isContextSourceMatch(playingFromA, "pl_A", playlistA))
        assertFalse(isContextSourceMatch(playingFromA, "pl_B", playlistB))

        // Playing from Search with the same song still keeps playlists in Play state.
        val playingFromSearch = sharedSong.copy(
            playbackSource = "Search",
            playbackSourceType = PlaybackSourceType.SEARCH,
            playbackSourceId = null,
        )
        assertFalse(isContextSourceMatch(playingFromSearch, "pl_A", playlistA))
    }

    @Test
    fun `global local music header matcher reflects songs, artists, albums, folders, and local playlists`() {
        val localTrack = Song("local:/Music/a.m4a", "Song A", "Artist A", null, localPath = "/Music/a.m4a")
        val ytTrack = Song("yt-online", "Online Track", "Artist B", null)
        val localLibrary = listOf(localTrack)
        val localPlaylistIds = setOf("local-pl-1")

        assertTrue(
            isGlobalLocalMusicMatch(
                localTrack.copy(playbackSourceId = "local:all"),
                localLibrary,
                localPlaylistIds,
            ),
        )
        assertTrue(
            isGlobalLocalMusicMatch(
                localTrack.copy(playbackSourceId = "local:artist:artist a"),
                localLibrary,
                localPlaylistIds,
            ),
        )
        assertTrue(
            isGlobalLocalMusicMatch(
                localTrack.copy(playbackSourceId = "local:folder:/Music"),
                localLibrary,
                localPlaylistIds,
            ),
        )
        assertTrue(
            isGlobalLocalMusicMatch(
                ytTrack.copy(playbackSourceId = "local-pl-1"),
                localLibrary,
                localPlaylistIds,
            ),
        )
        assertFalse(
            isGlobalLocalMusicMatch(
                ytTrack.copy(playbackSourceId = "VL_YT_PLAYLIST"),
                localLibrary,
                localPlaylistIds,
            ),
        )
    }

    @Test
    fun `local tab group indicator highlights active artist, album, and folder cleanly`() {
        val songA = Song("local:/A/1.m4a", "Track 1", "Billie Eilish", null, albumName = "Album A", localPath = "/A/1.m4a")
        val songB = Song("local:/B/2.m4a", "Track 2", "Billie Eilish", null, albumName = "Album B", localPath = "/B/2.m4a")

        val albumA = DesktopLocalGroup("name:album a", "Album A", "1 song", null, listOf(songA), DesktopLocalGroupKind.ALBUM)
        val albumB = DesktopLocalGroup("name:album b", "Album B", "1 song", null, listOf(songB), DesktopLocalGroupKind.ALBUM)
        val artistBillie = DesktopLocalGroup("artist:billie eilish", "Billie Eilish", "2 songs", null, listOf(songA, songB), DesktopLocalGroupKind.ARTIST)
        val folderA = DesktopLocalGroup("folder:/A", "A", "~/A · 1 song", null, listOf(songA), DesktopLocalGroupKind.FOLDER)
        val folderB = DesktopLocalGroup("folder:/B", "B", "~/B · 1 song", null, listOf(songB), DesktopLocalGroupKind.FOLDER)

        // Playing songA from Album A: Album A, Artist Billie Eilish, and Folder A light up; Album B and Folder B do not.
        val playingFromAlbumA = songA.copy(playbackSourceId = "local:name:album a")
        assertTrue(isGroupActiveInLocalTab(playingFromAlbumA, albumA))
        assertFalse(isGroupActiveInLocalTab(playingFromAlbumA, albumB))
        assertTrue(isGroupActiveInLocalTab(playingFromAlbumA, artistBillie))
        assertTrue(isGroupActiveInLocalTab(playingFromAlbumA, folderA))
        assertFalse(isGroupActiveInLocalTab(playingFromAlbumA, folderB))
    }

    @Test
    fun `resolveAudioQuality distinguishes standard Lossless from Hi-Res Lossless for list badges`() {
        val flac16 = DesktopLocalMetadata.resolveAudioQuality(
            ProbedAudioMetadata(codec = "flac", sampleRateHz = 44_100, bitDepth = 16),
            extension = "flac",
        )
        assertEquals("FLAC", flac16.sourceQuality)
        assertFalse(flac16.sourceHiRes)
        assertEquals(
            SourceQuality.LOSSLESS,
            Song("1", "CD FLAC", "Artist", null, sourceQuality = flac16.sourceQuality, sourceHiRes = flac16.sourceHiRes).sourceQualityBadge(),
        )

        val flac24At48k = DesktopLocalMetadata.resolveAudioQuality(
            ProbedAudioMetadata(codec = "flac", sampleRateHz = 48_000, bitDepth = 24),
            extension = "flac",
        )
        assertEquals("FLAC", flac24At48k.sourceQuality)
        assertTrue(flac24At48k.sourceHiRes)
        assertEquals(
            SourceQuality.HI_RES_LOSSLESS,
            Song("2", "Teh Hijau", "Tulus", null, sourceQuality = flac24At48k.sourceQuality, sourceHiRes = flac24At48k.sourceHiRes).sourceQualityBadge(),
        )

        val flac16At96k = DesktopLocalMetadata.resolveAudioQuality(
            ProbedAudioMetadata(codec = "flac", sampleRateHz = 96_000, bitDepth = 16),
            extension = "flac",
        )
        assertEquals("FLAC", flac16At96k.sourceQuality)
        assertTrue(flac16At96k.sourceHiRes)
        assertEquals(
            SourceQuality.HI_RES_LOSSLESS,
            Song("3", "96k FLAC", "Artist", null, sourceQuality = flac16At96k.sourceQuality, sourceHiRes = flac16At96k.sourceHiRes).sourceQualityBadge(),
        )

        val alac24InM4a = DesktopLocalMetadata.resolveAudioQuality(
            ProbedAudioMetadata(codec = "alac", sampleRateHz = 48_000, bitDepth = 24),
            extension = "m4a",
        )
        assertEquals("ALAC", alac24InM4a.sourceQuality)
        assertTrue(alac24InM4a.sourceHiRes)
        assertEquals(
            SourceQuality.HI_RES_LOSSLESS,
            Song("4", "Master", "Artist", null, sourceQuality = alac24InM4a.sourceQuality, sourceHiRes = alac24InM4a.sourceHiRes).sourceQualityBadge(),
        )

        val aacInM4a = DesktopLocalMetadata.resolveAudioQuality(
            ProbedAudioMetadata(codec = "aac", sampleRateHz = 48_000, bitDepth = 32),
            extension = "m4a",
        )
        assertEquals("AAC", aacInM4a.sourceQuality)
        assertFalse(aacInM4a.sourceHiRes)
        assertNull(
            Song("5", "Lossy", "Artist", null, sourceQuality = aacInM4a.sourceQuality, sourceHiRes = aacInM4a.sourceHiRes).sourceQualityBadge(),
        )

        assertEquals(
            SourceQuality.HI_RES_LOSSLESS,
            Song("6", "Addon Track", "Artist", null, sourceQuality = "LOSSLESS", sourceHiRes = true).sourceQualityBadge(),
        )
        assertEquals(
            SourceQuality.HI_RES_LOSSLESS,
            Song("7", "Named Tier", "Artist", null, sourceQuality = "HI_RES_LOSSLESS").sourceQualityBadge(),
        )
    }

    @Test
    fun `FFmpeg probes bit depth and sample rate and caches Hi-Res metadata across scans`() {
        val wav16 = scratch.resolve("cd-track.wav")
        val wav24 = scratch.resolve("hires-24-48.wav")
        val wav96 = scratch.resolve("hires-16-96.wav")
        Files.write(wav16, buildPcmWav(sampleRateHz = 44_100, bitsPerSample = 16))
        Files.write(wav24, buildPcmWav(sampleRateHz = 48_000, bitsPerSample = 24))
        Files.write(wav96, buildPcmWav(sampleRateHz = 96_000, bitsPerSample = 16))

        val cacheFile = scratch.resolve("local_tags_v3.tsv")
        val artDir = scratch.resolve("art")

        val meta16 = DesktopLocalMetadata.probeCached(wav16, 100L, Files.size(wav16), artDir, cacheFile)
        val meta24 = DesktopLocalMetadata.probeCached(wav24, 100L, Files.size(wav24), artDir, cacheFile)
        val meta96 = DesktopLocalMetadata.probeCached(wav96, 100L, Files.size(wav96), artDir, cacheFile)
        DesktopLocalMetadata.flushCache(cacheFile)

        assertEquals(44_100, meta16.sampleRateHz)
        assertEquals(16, meta16.bitDepth)
        assertEquals(48_000, meta24.sampleRateHz)
        assertEquals(24, meta24.bitDepth)
        assertEquals(96_000, meta96.sampleRateHz)
        assertEquals(16, meta96.bitDepth)

        assertFalse(DesktopLocalMetadata.resolveAudioQuality(meta16, "wav").sourceHiRes)
        assertTrue(DesktopLocalMetadata.resolveAudioQuality(meta24, "wav").sourceHiRes)
        assertTrue(DesktopLocalMetadata.resolveAudioQuality(meta96, "wav").sourceHiRes)

        val encodedHiRes = DesktopPersistence.encodeSongFields(
            Song(
                videoId = "local:${wav24.toAbsolutePath()}",
                title = "Teh Hijau",
                artist = "Tulus",
                thumbnailUrl = null,
                localPath = wav24.toAbsolutePath().toString(),
                sourceQuality = "FLAC",
                sourceHiRes = true,
            ),
        )
        // Verify sourceHiRes survives persistence and local catalog enrichment.
        val fields = encodedHiRes.split(DesktopPersistence.DELIMITER)
        assertTrue(fields.size >= 23)
        val decodedHiRes = DesktopPersistence.decodeSongFields(encodedHiRes)
        assertNotNull(decodedHiRes)
        assertTrue(decodedHiRes.sourceHiRes)
        assertEquals(SourceQuality.HI_RES_LOSSLESS, decodedHiRes.sourceQualityBadge())

        val staleHistory = listOf(decodedHiRes.copy(sourceHiRes = false, sourceQuality = "FLAC"))
        val refreshedHistory = DesktopLocalMetadata.enrichWithLocalCatalog(
            staleHistory,
            listOf(decodedHiRes),
        )
        assertTrue(refreshedHistory.single().sourceHiRes)
        assertEquals(SourceQuality.HI_RES_LOSSLESS, refreshedHistory.single().sourceQualityBadge())
    }

    private fun buildPcmWav(sampleRateHz: Int, bitsPerSample: Int): ByteArray {
        val channels = 2
        val byteRate = sampleRateHz * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataBytes = blockAlign * 64
        val chunkSize = 36 + dataBytes
        val out = ByteArrayOutputStream(44 + dataBytes)
        fun writeAscii(text: String) = out.write(text.toByteArray(StandardCharsets.US_ASCII))
        fun writeIntLe(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 24) and 0xFF)
        }
        fun writeShortLe(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }
        writeAscii("RIFF")
        writeIntLe(chunkSize)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLe(16)
        writeShortLe(1) // PCM
        writeShortLe(channels)
        writeIntLe(sampleRateHz)
        writeIntLe(byteRate)
        writeShortLe(blockAlign)
        writeShortLe(bitsPerSample)
        writeAscii("data")
        writeIntLe(dataBytes)
        out.write(ByteArray(dataBytes))
        return out.toByteArray()
    }

    private fun createSampleJpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun buildTaggedMp3(
        title: String,
        artist: String,
        album: String,
        track: String,
        coverJpeg: ByteArray,
    ): ByteArray {
        fun id3Frame(id: String, payload: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(id.toByteArray(StandardCharsets.US_ASCII))
            val len = payload.size
            out.write((len ushr 24) and 0xFF)
            out.write((len ushr 16) and 0xFF)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
            out.write(0)
            out.write(0)
            out.write(payload)
            return out.toByteArray()
        }

        fun textPayload(value: String): ByteArray =
            byteArrayOf(0x00) + value.toByteArray(StandardCharsets.ISO_8859_1)

        val apic = ByteArrayOutputStream().apply {
            write(0x00) // ISO-8859-1
            write("image/jpeg".toByteArray(StandardCharsets.US_ASCII))
            write(0x00)
            write(0x03) // front cover
            write(0x00) // empty description
            write(coverJpeg)
        }.toByteArray()

        val frames = ByteArrayOutputStream().apply {
            write(id3Frame("TIT2", textPayload(title)))
            write(id3Frame("TPE1", textPayload(artist)))
            write(id3Frame("TALB", textPayload(album)))
            write(id3Frame("TRCK", textPayload(track)))
            write(id3Frame("APIC", apic))
        }.toByteArray()

        val tagSize = frames.size
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        out.write(3) // ID3v2.3
        out.write(0)
        out.write(0)
        // Synchsafe 28-bit size
        out.write((tagSize ushr 21) and 0x7F)
        out.write((tagSize ushr 14) and 0x7F)
        out.write((tagSize ushr 7) and 0x7F)
        out.write(tagSize and 0x7F)
        out.write(frames)

        // Append enough valid MPEG-1 Layer III 128kbps 44.1kHz frames (~1.04s) for duration measurement.
        val mp3Frame = ByteArray(417).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xFB.toByte()
            this[2] = 0x90.toByte()
            this[3] = 0x00.toByte()
        }
        repeat(40) { out.write(mp3Frame) }
        return out.toByteArray()
    }
}
