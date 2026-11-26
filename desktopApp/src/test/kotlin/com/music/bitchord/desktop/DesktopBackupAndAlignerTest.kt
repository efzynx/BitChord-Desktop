package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBackupAndAlignerTest {

    private fun buildPulsedSignal(
        durationSec: Double,
        sampleRate: Double,
        shiftSec: Double,
    ): FloatArray {
        val n = (durationSec * sampleRate).toInt()
        val out = FloatArray(n)
        val anchorTimes = listOf(4.0, 5.2, 6.8, 8.1, 9.5, 11.3, 13.0, 14.4)
        for (i in 0 until n) {
            val t = i / sampleRate
            var amp = 0.0
            for ((idx, anchor) in anchorTimes.withIndex()) {
                val pulseStart = anchor + shiftSec
                val local = t - pulseStart
                if (local in 0.0..0.32) {
                    val carrier = 220.0 + idx * 55.0
                    val burstEnv = sin(PI * local / 0.32)
                    amp += 0.75 * burstEnv * sin(2.0 * PI * carrier * local)
                }
            }
            out[i] = amp.toFloat()
        }
        return out
    }

    @Test
    fun `smart audio aligner recovers positive and negative time offsets via FFT cross correlation`() {
        val sr = 8000.0
        val sourcePcm = buildPulsedSignal(durationSec = 20.0, sampleRate = sr, shiftSec = 0.0)
        // Target events occur +1.50s later -> lagFrames should be ~+150 (at 10ms hop).
        val targetLaterPcm = buildPulsedSignal(durationSec = 22.0, sampleRate = sr, shiftSec = 1.50)
        // Target events occur -2.20s earlier -> lagFrames should be ~-220 (at 10ms hop).
        val targetEarlierPcm = buildPulsedSignal(durationSec = 20.0, sampleRate = sr, shiftSec = -2.20)

        val srcEnv = DesktopVersionAudioAligner.computeEnergyEnvelope(sourcePcm, sr)
        val laterEnv = DesktopVersionAudioAligner.computeEnergyEnvelope(targetLaterPcm, sr)
        val earlierEnv = DesktopVersionAudioAligner.computeEnergyEnvelope(targetEarlierPcm, sr)

        val pos = DesktopVersionAudioAligner.crossCorrelate(srcEnv, laterEnv)
        assertTrue(pos.confidence >= 0.85, "expected high confidence for shifted match, got ${pos.confidence}")
        assertTrue(abs(pos.lagFrames - 150) <= 2, "expected lag ~150 frames (+1.5s), got ${pos.lagFrames}")

        val neg = DesktopVersionAudioAligner.crossCorrelate(srcEnv, earlierEnv)
        assertTrue(neg.confidence >= 0.85, "expected high confidence for negative shift, got ${neg.confidence}")
        assertTrue(abs(neg.lagFrames + 220) <= 2, "expected lag ~-220 frames (-2.2s), got ${neg.lagFrames}")
    }

    @Test
    fun `smart audio aligner handles silence and identical rendition cache keys cleanly`() {
        val emptyCorr = DesktopVersionAudioAligner.crossCorrelate(FloatArray(0), FloatArray(100))
        assertEquals(0, emptyCorr.lagFrames)
        assertEquals(0.0, emptyCorr.confidence)

        val flat = FloatArray(500) { 0.05f }
        val flatCorr = DesktopVersionAudioAligner.crossCorrelate(flat, flat)
        assertTrue(flatCorr.confidence < 0.40, "expected low confidence for flat envelope, got ${flatCorr.confidence}")

        val song = Song(videoId = "abc123", title = "Test", artist = "Artist", thumbnailUrl = null)
        val yt = DesktopStream(url = "https://yt.example/stream", sourceId = "youtube")
        val addon = DesktopStream(url = "https://addon.example/flac", sourceId = "tidal")
        assertEquals("abc123#youtube", DesktopVersionAudioAligner.renditionKey(song, yt))
        assertEquals("abc123#tidal", DesktopVersionAudioAligner.renditionKey(song, addon))
        assertEquals(0L, DesktopVersionAudioAligner.getCachedOffsetMs("abc123#youtube", "abc123#youtube"))
    }

    @Test
    fun `backup snapshot gzip roundtrip preserves library and settings while excluding secrets`() {
        val sourcePrefs = Preferences.userRoot().node("com.music.bitchord.desktop.test.src.${UUID.randomUUID()}")
        val targetPrefs = Preferences.userRoot().node("com.music.bitchord.desktop.test.dst.${UUID.randomUUID()}")
        try {
            val src = DesktopPersistence(sourcePrefs)
            val trackA = Song(videoId = "v1", title = "Song One", artist = "Artist A", thumbnailUrl = null, albumName = "Album A")
            val trackB = Song(videoId = "v2", title = "Song Two", artist = "Artist B", thumbnailUrl = null, durationText = "3:21")

            src.saveBoolean("smart_version_alignment", false)
            src.saveBoolean("skip_silence", true)
            src.saveString("audio_quality", "LOSSLESS")
            src.saveInt("lyrics_offset_ms", -250)
            src.saveLikedIds(setOf("v1", "v2"))
            src.saveDislikedIds(setOf("v9"))
            src.saveHistory(listOf(trackA, trackB))
            src.savePlaylists(listOf(DesktopPlaylist(id = "pl1", title = "Favorites", songs = listOf(trackA))))
            src.saveOriginalVersionIds(setOf("v2"))
            // Simulate a sensitive token stored in the same preferences node; it must not be exported.
            sourcePrefs.put("spotify_access_token", "secret-token-should-not-leak")

            val snapshot = DesktopBackup.captureSnapshot(persistence = src, versionName = "1.10.2-test")
            assertFalse(snapshot.strings.containsKey("spotify_access_token"))
            assertEquals("1.10.2-test", snapshot.versionName)

            val compressed = DesktopBackup.gzip(DesktopBackup.encodeJsonBytes(snapshot, pretty = false))
            val decoded = DesktopBackup.decodeJsonBytes(DesktopBackup.gunzip(compressed))
            val dst = DesktopPersistence(targetPrefs)
            val summary = DesktopBackup.applySnapshot(decoded, persistence = dst)

            assertEquals(1, summary.playlists)
            assertEquals(2, summary.likedCount)
            assertFalse(dst.boolean("smart_version_alignment", true))
            assertTrue(dst.boolean("skip_silence", false))
            assertEquals("LOSSLESS", dst.string("audio_quality"))
            assertEquals(-250, dst.int("lyrics_offset_ms", 0))
            assertEquals(setOf("v1", "v2"), dst.likedIds())
            assertEquals(setOf("v9"), dst.dislikedIds())
            assertEquals(setOf("v2"), dst.originalVersionIds())
            assertEquals(2, dst.history().size)
            assertEquals("v1", dst.playlists().single().songs.single().videoId)
            assertEquals(null, targetPrefs.get("spotify_access_token", null))
        } finally {
            runCatching { sourcePrefs.removeNode() }
            runCatching { targetPrefs.removeNode() }
        }
    }

    @Test
    fun `backup decoder rejects unknown app tag and future schema versions`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopBackup.decodeJsonBytes("""{"app":"other-app","schemaVersion":1}""".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopBackup.decodeJsonBytes("""{"app":"bitchord","schemaVersion":999}""".encodeToByteArray())
        }
    }

    @Test
    fun `drive pkce challenge query parser error extractor and auto backup schedule match spec`() {
        // RFC 7636 Appendix B test vector
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            DesktopDriveAuth.pkceS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
        val parsed = DesktopDriveAuth.parseQuery("code=abc%2F123&state=xyz+1")
        assertEquals("abc/123", parsed["code"])
        assertEquals("xyz 1", parsed["state"])

        val apiMsg = DesktopDriveApi.extractErrorMessage(
            """{"error":{"code":401,"message":"Invalid Credentials"}}""",
            401,
        )
        assertEquals("Invalid Credentials", apiMsg)

        val now = 10_000_000_000L
        val connectedDaily = DesktopDriveBackup.Settings(
            accountEmail = "user@example.com",
            frequency = DesktopBackupFrequency.DAILY,
            lastBackupAt = now - DesktopBackupFrequency.DAILY.intervalMs - 1L,
        )
        assertTrue(DesktopDriveBackup.isAutoBackupDue(connectedDaily, now, hasToken = true))
        assertFalse(DesktopDriveBackup.isAutoBackupDue(connectedDaily, now, hasToken = false))
        assertFalse(DesktopDriveBackup.isAutoBackupDue(connectedDaily.copy(needsSignIn = true), now, hasToken = true))
        assertFalse(
            DesktopDriveBackup.isAutoBackupDue(
                connectedDaily.copy(frequency = DesktopBackupFrequency.OFF),
                now,
                hasToken = true,
            ),
        )
        assertFalse(
            DesktopDriveBackup.isAutoBackupDue(
                connectedDaily.copy(lastBackupAt = now - 60_000L),
                now,
                hasToken = true,
            ),
        )
    }

    @Test
    fun `built in oauth client property and replay multi-device bucket summarization work cleanly`() {
        val prevId = System.getProperty("bitchord.gdrive.clientId")
        val prevSecret = System.getProperty("bitchord.gdrive.clientSecret")
        try {
            System.setProperty("bitchord.gdrive.clientId", "maintainer-client-id.apps.googleusercontent.com")
            System.setProperty("bitchord.gdrive.clientSecret", "maintainer-secret")
            assertTrue(DesktopDriveAuth.hasBuiltInClient())
            assertEquals("maintainer-client-id.apps.googleusercontent.com", DesktopDriveAuth.builtInClientId())
            assertEquals("maintainer-secret", DesktopDriveAuth.builtInClientSecret())
        } finally {
            if (prevId == null) System.clearProperty("bitchord.gdrive.clientId") else System.setProperty("bitchord.gdrive.clientId", prevId)
            if (prevSecret == null) System.clearProperty("bitchord.gdrive.clientSecret") else System.setProperty("bitchord.gdrive.clientSecret", prevSecret)
        }

        val song = Song(videoId = "s1", title = "Track", artist = "Artist", thumbnailUrl = null)
        val encoded = DesktopPersistence.encodeSongFields(song)
        val local = DesktopListeningStats.parseBackupBuckets(
            listOf(
                DesktopListeningStats.BackupBucket(
                    month = "2026-11",
                    tracks = listOf(DesktopListeningStats.BackupTrack(encoded, 120_000L, 2)),
                ),
            ),
        )
        val peer = DesktopListeningStats.parseBackupBuckets(
            listOf(
                DesktopListeningStats.BackupBucket(
                    month = "2026-11",
                    tracks = listOf(DesktopListeningStats.BackupTrack(encoded, 180_000L, 3)),
                ),
            ),
        )
        val merged = DesktopListeningStats.summarise(local + peer, DesktopReplayPeriod.ALL_TIME, java.time.LocalDate.of(2026, 11, 26))
        assertEquals(300_000L, merged.totalMs)
        assertEquals(5, merged.totalPlays)
    }

    @Test
    fun `replay share poster renders 9x16 PNG image cleanly on skia`() {
        val song = Song(
            videoId = "r1",
            title = "Neon Skyline",
            artist = "Test Artist",
            thumbnailUrl = null,
            albumName = "Night Drives",
        )
        val encoded = DesktopPersistence.encodeSongFields(song)
        val buckets = DesktopListeningStats.parseBackupBuckets(
            listOf(
                DesktopListeningStats.BackupBucket(
                    month = "2026-11",
                    tracks = listOf(DesktopListeningStats.BackupTrack(encoded, 960_000L, 4)),
                    hours = List(24) { if (it == 21) 960_000L else 0L },
                    days = mapOf("2026-11-26" to 960_000L),
                ),
            ),
        )
        val summary = DesktopListeningStats.summarise(
            buckets,
            DesktopReplayPeriod.ALL_TIME,
            java.time.LocalDate.of(2026, 11, 26),
        )
        val poster = DesktopReplayPoster.renderOnSkia(
            summary = summary,
            holder = "efzyn",
            memberSince = summary.memberSince(),
        )
        assertEquals(1080, poster.preview.width)
        assertEquals(1920, poster.preview.height)
        assertTrue(poster.pngBytes.size > 1024, "expected non-empty PNG output")
        // Verify PNG magic signature: 89 50 4E 47 0D 0A 1A 0A
        assertEquals(0x89.toByte(), poster.pngBytes[0])
        assertEquals(0x50.toByte(), poster.pngBytes[1])
        assertEquals(0x4E.toByte(), poster.pngBytes[2])
        assertEquals(0x47.toByte(), poster.pngBytes[3])
        assertEquals("bitchord-replay-all-time.png", DesktopReplayPoster.suggestedFilename(summary))
    }
}
