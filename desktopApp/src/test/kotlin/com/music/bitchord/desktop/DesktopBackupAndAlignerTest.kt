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

    @Test
    fun `drive multi-snapshot property builder clamps utf8 length and pruning keeps newest N backups`() {
        val longName = "Laptop-Kantor-Super-Panjang-" + "é".repeat(70)
        val props = DesktopDriveBackup.buildBackupProperties(
            deviceId = "dev-1234",
            deviceName = longName,
            backupId = "snap0001",
            updatedAtMs = 1_795_000_000_000L,
            versionName = "1.10.1-beta.3",
            automatic = false,
            playlists = 4,
            liked = 29,
            months = 7,
        )
        props.forEach { (k, v) ->
            val utf8Len = k.toByteArray(Charsets.UTF_8).size + v.toByteArray(Charsets.UTF_8).size
            assertTrue(utf8Len <= 124, "Drive appProperty '$k' exceeds 124 bytes ($utf8Len)")
        }
        assertEquals("4p,29l,7m", props["summary"])
        assertEquals("manual", props["trigger"])

        val nameA = DesktopDriveBackup.buildBackupFilename("dev-1234", 1_795_000_000_000L, "aaaa1111")
        val nameB = DesktopDriveBackup.buildBackupFilename("dev-1234", 1_795_000_000_000L, "bbbb2222")
        assertTrue(nameA != nameB && nameA.startsWith("bitchord-desktop-backup-dev-1234-"))

        val snapshots = (1L..5L).map { idx ->
            DesktopDriveBackup.RemoteBackup(
                fileId = "file-$idx",
                name = "snap-$idx",
                backupId = "b$idx",
                deviceId = "dev-1234",
                deviceName = "My PC",
                updatedAtMs = idx * 1_000L,
                sizeBytes = 1024L,
                versionName = "1.10.1",
            )
        }
        val toPrune = DesktopDriveBackup.selectSnapshotsToPrune(snapshots, keepCount = 3)
        assertEquals(listOf("file-2", "file-1"), toPrune.map { it.fileId })
        assertTrue(DesktopDriveBackup.selectSnapshotsToPrune(snapshots, keepCount = 0).isEmpty())
    }

    @Test
    fun `drive groups combine multi-device desktop snapshots and mobile replay manifests cleanly`() {
        val currentId = "11111111-1111-1111-1111-111111111111"
        val otherId = "22222222-2222-2222-2222-222222222222"
        val mobileId = "android-pixel-8"

        val files = listOf(
            DesktopDriveApi.DriveFile(
                id = "cur-old",
                name = "bitchord-desktop-backup-$currentId-old.json.gz",
                md5 = "m1",
                modifiedAt = "2026-11-26T10:00:00Z",
                sizeBytes = 2000L,
                properties = mapOf(
                    "kind" to "desktop_backup",
                    "deviceId" to currentId,
                    "deviceName" to "Old PC Name",
                    "backupId" to "old1",
                    "updatedAt" to "1000",
                    "summary" to "2p,5l,1m",
                    "trigger" to "auto",
                ),
            ),
            DesktopDriveApi.DriveFile(
                id = "cur-new",
                name = "bitchord-desktop-backup-$currentId-new.json.gz",
                md5 = "m2",
                modifiedAt = "2026-11-27T10:00:00Z",
                sizeBytes = 2500L,
                properties = mapOf(
                    "kind" to "desktop_backup",
                    "deviceId" to currentId,
                    "deviceName" to "Workstation",
                    "backupId" to "new1",
                    "updatedAt" to "3000",
                    "summary" to "3p,10l,2m",
                    "trigger" to "manual",
                ),
            ),
            DesktopDriveApi.DriveFile(
                id = "other-1",
                name = "bitchord-desktop-backup-$otherId.json.gz",
                md5 = "m3",
                modifiedAt = "2026-11-27T12:00:00Z",
                sizeBytes = 4000L,
                properties = mapOf(
                    "kind" to "desktop_backup",
                    "deviceId" to otherId,
                    "deviceName" to "Home Laptop",
                    "updatedAt" to "5000",
                ),
            ),
            DesktopDriveApi.DriveFile(
                id = "mob-manifest",
                name = "device-$mobileId.json.gz",
                md5 = "mm1",
                modifiedAt = "2026-11-27T14:00:00Z",
                sizeBytes = 500L,
                properties = mapOf("kind" to "device", "deviceId" to mobileId),
            ),
            DesktopDriveApi.DriveFile(
                id = "mob-month",
                name = "replay-$mobileId-2026-11.json.gz",
                md5 = "mr1",
                modifiedAt = "2026-11-27T14:00:00Z",
                sizeBytes = 1500L,
                properties = mapOf("kind" to "replay", "deviceId" to mobileId, "month" to "2026-11"),
            ),
        )
        val manifests = mapOf(
            mobileId to DesktopDriveBackup.MobileDeviceManifest(
                deviceId = mobileId,
                name = "Pixel 8 Pro",
                updatedAt = 4000L,
                versionName = "1.9.4",
                months = mapOf("2026-11" to "hash"),
            ),
        )
        val groups = DesktopDriveBackup.buildRemoteDeviceGroups(files, manifests, currentId, "Workstation")
        assertEquals(3, groups.size)
        // Current computer comes first even if another device synced slightly more recently.
        assertEquals(currentId, groups[0].deviceId)
        assertEquals("Workstation", groups[0].deviceName)
        assertEquals(2, groups[0].snapshots.size)
        assertEquals("cur-new", groups[0].latestSnapshot?.fileId)
        assertEquals(3, groups[0].latestSnapshot?.playlistsCount)
        assertFalse(groups[0].latestSnapshot!!.isAutoBackup)
        assertTrue(groups[0].snapshots[1].isAutoBackup)

        assertEquals(otherId, groups[1].deviceId)
        assertEquals(RemoteDeviceKind.DESKTOP, groups[1].kind)

        assertEquals(mobileId, groups[2].deviceId)
        assertEquals(RemoteDeviceKind.MOBILE, groups[2].kind)
        assertEquals("Pixel 8 Pro", groups[2].deviceName)
        assertEquals(1, groups[2].mobileMonthsCount)

        val peers = DesktopDriveBackup.selectLatestPeerDevices(groups, currentId)
        assertEquals(setOf(otherId, mobileId), peers.map { it.deviceId }.toSet())
    }

    @Test
    fun `mobile stored bucket and compatible preferences convert cleanly for desktop restore`() {
        val rawBucket = """
            {
              "version": 1,
              "month": "2026-11",
              "tracks": [
                {"id": "trk1", "title": "Night Drive", "artist": "Synth", "album": "Neon", "ms": 240000, "plays": 3}
              ],
              "hours": [0, 0, 240000],
              "days": {"9": 240000}
            }
        """.trimIndent().encodeToByteArray()
        val converted = DesktopDriveBackup.parseMobileStoredBucket(rawBucket)
        assertTrue(converted != null)
        assertEquals("2026-11", converted.month)
        assertEquals(1, converted.tracks.size)
        assertEquals(240_000L, converted.days["2026-11-09"])

        val summary = DesktopListeningStats.summarise(
            DesktopListeningStats.parseBackupBuckets(listOf(converted)),
            DesktopReplayPeriod.ALL_TIME,
            java.time.LocalDate.of(2026, 11, 28),
        )
        assertEquals(240_000L, summary.totalMs)
        assertEquals(3, summary.totalPlays)
        assertEquals("2026-11-09", summary.busiestDay)

        val targetPrefs = Preferences.userRoot().node("com.music.bitchord.desktop.test.mob.${UUID.randomUUID()}")
        try {
            val persistence = DesktopPersistence(targetPrefs)
            val applied = DesktopDriveBackup.applyMobileSettingsToDesktop(
                settings = mapOf(
                    "skip_silence" to DesktopDriveBackup.MobilePrefValue(type = "bool", value = "true"),
                    "audio_quality" to DesktopDriveBackup.MobilePrefValue(type = "string", value = "HIGH"),
                    "lyrics_offset_ms" to DesktopDriveBackup.MobilePrefValue(type = "int", value = "-150"),
                    "unknown_mobile_secret" to DesktopDriveBackup.MobilePrefValue(type = "string", value = "ignored"),
                ),
                persistence = persistence,
            )
            assertEquals(3, applied)
            assertTrue(persistence.boolean("skip_silence", false))
            assertEquals("HIGH", persistence.string("audio_quality"))
            assertEquals(-150, persistence.int("lyrics_offset_ms", 0))
            assertEquals(null, targetPrefs.get("unknown_mobile_secret", null))
        } finally {
            runCatching { targetPrefs.removeNode() }
        }
    }

    @Test
    fun `text field sync preserves cursor during async portal lag and honors external coercions`() {
        val sync = DesktopTextFieldSyncState(initialValue = "")
        var tfv = androidx.compose.ui.text.input.TextFieldValue("")

        // Fast typing while parent still supplies stale "" over a delayed SideEffect frame:
        // cursor must advance cleanly rather than resetting back to index 0.
        val emitted = mutableListOf<String>()
        tfv = sync.onUserChanged(
            androidx.compose.ui.text.input.TextFieldValue("c", androidx.compose.ui.text.TextRange(1)),
        ) { emitted.add(it) }
        tfv = sync.syncFromProp("", tfv)
        assertEquals("c", tfv.text)
        assertEquals(androidx.compose.ui.text.TextRange(1), tfv.selection)

        tfv = sync.onUserChanged(
            androidx.compose.ui.text.input.TextFieldValue("ca", androidx.compose.ui.text.TextRange(2)),
        ) { emitted.add(it) }
        // Stale echo of first keystroke "c" arrives before "ca": must not drop "a" or move cursor.
        tfv = sync.syncFromProp("c", tfv)
        assertEquals("ca", tfv.text)
        assertEquals(androidx.compose.ui.text.TextRange(2), tfv.selection)

        // Final echo "ca" arrives cleanly.
        tfv = sync.syncFromProp("ca", tfv)
        assertEquals(listOf("c", "ca"), emitted)
        assertEquals("ca", tfv.text)
        assertEquals(androidx.compose.ui.text.TextRange(2), tfv.selection)

        // Caller rejects/truncates an over-limit edit silently without prop change: forceReconcile snaps cleanly.
        val limited = limitTextFieldValue(
            androidx.compose.ui.text.input.TextFieldValue(
                "abcdef",
                androidx.compose.ui.text.TextRange(6),
            ),
            maxLength = 4,
        )
        assertEquals("abcd", limited.text)
        assertEquals(androidx.compose.ui.text.TextRange(4), limited.selection)

        val overLimit = sync.onUserChanged(
            androidx.compose.ui.text.input.TextFieldValue("cax", androidx.compose.ui.text.TextRange(3)),
        ) { }
        val reconciled = sync.forceReconcile("ca", overLimit)
        assertEquals("ca", reconciled.text)
        assertEquals(androidx.compose.ui.text.TextRange(2), reconciled.selection)
    }

    @Test
    fun `ambient flat dominant color adapts saturation and lightness for dark and light themes`() {
        val redBluePixels = IntArray(24 * 24) { idx ->
            if (idx % 3 == 0) 0xFF202020.toInt() else 0xFFE53935.toInt()
        }
        val darkArgb = DesktopArtworkMesh.dominantFlatArgbFromPixels(redBluePixels, 24, 24, lightTheme = false)
        val lightArgb = DesktopArtworkMesh.dominantFlatArgbFromPixels(redBluePixels, 24, 24, lightTheme = true)

        val darkRed = (darkArgb shr 16) and 0xFF
        val darkBlue = darkArgb and 0xFF
        assertTrue(darkRed > darkBlue, "expected warm red hue to dominate in dark flat color")
        assertTrue(darkRed in 40..130, "expected calibrated dark-mode red channel, got $darkRed")

        val lightRed = (lightArgb shr 16) and 0xFF
        val lightGreen = (lightArgb shr 8) and 0xFF
        assertTrue(lightRed > lightGreen, "expected warm hue preserved in light pastel mode")
        assertTrue(lightRed >= 200, "expected readable pastel light channel, got $lightRed")

        val grayPixels = IntArray(16 * 16) { 0xFF808080.toInt() }
        val grayDark = DesktopArtworkMesh.dominantFlatArgbFromPixels(grayPixels, 16, 16, lightTheme = false)
        val grayLight = DesktopArtworkMesh.dominantFlatArgbFromPixels(grayPixels, 16, 16, lightTheme = true)
        assertTrue(((grayDark shr 16) and 0xFF) < 80)
        assertTrue(((grayLight shr 16) and 0xFF) >= 195)

        assertEquals(DesktopAmbientBackdropStyle.MESH, DesktopAmbientBackdropStyle.stored("unknown"))
        assertEquals(DesktopAmbientBackdropStyle.FLAT_DOMINANT, DesktopAmbientBackdropStyle.stored("flat_dominant"))
    }

    @Test
    fun `queue replaceCurrentVersion swaps audio and video versions without disturbing upcoming queue`() {
        val a = Song(videoId = "aud-1", title = "Track A", artist = "Artist", thumbnailUrl = null, isVideo = false)
        val b = Song(videoId = "aud-2", title = "Track B", artist = "Artist", thumbnailUrl = null, isVideo = false)
        val c = Song(videoId = "aud-3", title = "Track C", artist = "Artist", thumbnailUrl = null, isVideo = false)
        val vidB = b.copy(videoId = "vid-2", isVideo = true)

        val q = DesktopQueue(songs = listOf(a, b, c), index = 1)
        val swapped = q.replaceCurrentVersion("aud-2", vidB)
        assertEquals(1, swapped.index)
        assertEquals(listOf("aud-1", "vid-2", "aud-3"), swapped.songs.map { it.videoId })
        assertTrue(swapped.current?.isVideo == true)
    }

    @Test
    fun `video live audio position holds steady when paused or awaiting audio`() {
        val paused = DesktopPlaybackState(
            isPlaying = false,
            positionMs = 12_500L,
            positionSampledAtNanos = System.nanoTime() - 500_000_000L,
        )
        assertEquals(12_500L, DesktopMusicVideoEngine.liveAudioPositionMs(paused))

        val awaiting = DesktopPlaybackState(
            isPlaying = true,
            awaitingAudio = true,
            positionMs = 18_000L,
            positionSampledAtNanos = System.nanoTime() - 400_000_000L,
        )
        assertEquals(18_000L, DesktopMusicVideoEngine.liveAudioPositionMs(awaiting))

        val playing = DesktopPlaybackState(
            isPlaying = true,
            awaitingAudio = false,
            positionMs = 20_000L,
            positionSampledAtNanos = System.nanoTime() - 150_000_000L,
        )
        val live = DesktopMusicVideoEngine.liveAudioPositionMs(playing)
        assertTrue(live in 20_100L..20_600L, "expected live position ~20150ms, got $live")
    }

    @Test
    fun `video decoder adaptive scaling and high-fps cadence behave predictably`() {
        assertEquals(768 to 432, DesktopMusicVideoDecoder.scaledDimensions(1920, 1080, 768))
        assertEquals(1280 to 720, DesktopMusicVideoDecoder.scaledDimensions(3840, 2160, 1280))
        assertEquals(768, DesktopMusicVideoEngine.renderMaxEdgeFor(fullscreen = false))
        assertEquals(1280, DesktopMusicVideoEngine.renderMaxEdgeFor(fullscreen = true))

        assertTrue(DesktopMusicVideoEngine.shouldScaleForCadence(highFrameRate = false, ptsMs = 16L, lastPresentedPtsMs = 0L))
        assertTrue(DesktopMusicVideoEngine.shouldScaleForCadence(highFrameRate = true, ptsMs = 0L, lastPresentedPtsMs = Long.MIN_VALUE))
        assertTrue(!DesktopMusicVideoEngine.shouldScaleForCadence(highFrameRate = true, ptsMs = 16L, lastPresentedPtsMs = 0L))
        assertTrue(DesktopMusicVideoEngine.shouldScaleForCadence(highFrameRate = true, ptsMs = 33L, lastPresentedPtsMs = 0L))
        assertTrue(DesktopMusicVideoEngine.shouldScaleForCadence(highFrameRate = true, ptsMs = 5L, lastPresentedPtsMs = 1200L))
    }

    @Test
    fun `video auto quality prefers 720p windowed 1080p fullscreen and steps down smoothly`() {
        assertEquals(720, DesktopMusicVideoEngine.autoHeightFor(fullscreen = false))
        assertEquals(1080, DesktopMusicVideoEngine.autoHeightFor(fullscreen = true))
        val heights = listOf(2160, 1440, 1080, 720, 480, 360)
        assertEquals(1440, DesktopMusicVideoEngine.nextLowerQualityHeight(2160, heights))
        assertEquals(720, DesktopMusicVideoEngine.nextLowerQualityHeight(1080, heights))
        assertEquals(480, DesktopMusicVideoEngine.nextLowerQualityHeight(720, heights))
        assertEquals(null, DesktopMusicVideoEngine.nextLowerQualityHeight(480, heights))
        assertTrue(DesktopVideoYuvShaders.supportsYuvPlanar)

        val interleaved = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)
        val uOut = ByteArray(4)
        val vOut = ByteArray(4)
        DesktopMusicVideoDecoder.deinterleaveNv12Plane(interleaved, uOut, vOut, 4)
        assertEquals(listOf<Byte>(10, 30, 50, 70), uOut.toList())
        assertEquals(listOf<Byte>(20, 40, 60, 80), vOut.toList())

        val yImg = org.jetbrains.skia.Image.makeRaster(
            org.jetbrains.skia.ImageInfo(4, 4, org.jetbrains.skia.ColorType.GRAY_8, org.jetbrains.skia.ColorAlphaType.OPAQUE),
            ByteArray(16) { 64 },
            4,
        )
        val uImg = org.jetbrains.skia.Image.makeRaster(
            org.jetbrains.skia.ImageInfo(2, 2, org.jetbrains.skia.ColorType.GRAY_8, org.jetbrains.skia.ColorAlphaType.OPAQUE),
            uOut,
            2,
        )
        val vImg = org.jetbrains.skia.Image.makeRaster(
            org.jetbrains.skia.ImageInfo(2, 2, org.jetbrains.skia.ColorType.GRAY_8, org.jetbrains.skia.ColorAlphaType.OPAQUE),
            vOut,
            2,
        )
        assertEquals(4, yImg.width)
        assertEquals(2, uImg.width)
        assertEquals(2, vImg.width)
        yImg.close()
        uImg.close()
        vImg.close()
    }

    @Test
    fun `video mode auto-hides lyric strip by default without mutating audio preference`() {
        assertFalse(
            com.music.bitchord.ui.player.effectiveHideLyricsPreview(
                hideLyricsPreview = false,
                autoHideLyricsInVideo = true,
                showingVideo = false,
                revealedVideoId = null,
                songVideoId = "vid-1",
            ),
        )
        assertTrue(
            com.music.bitchord.ui.player.effectiveHideLyricsPreview(
                hideLyricsPreview = false,
                autoHideLyricsInVideo = true,
                showingVideo = true,
                revealedVideoId = null,
                songVideoId = "vid-1",
            ),
        )
        assertFalse(
            com.music.bitchord.ui.player.effectiveHideLyricsPreview(
                hideLyricsPreview = false,
                autoHideLyricsInVideo = true,
                showingVideo = true,
                revealedVideoId = "vid-1",
                songVideoId = "vid-1",
            ),
        )
        assertTrue(
            com.music.bitchord.ui.player.effectiveHideLyricsPreview(
                hideLyricsPreview = false,
                autoHideLyricsInVideo = true,
                showingVideo = true,
                revealedVideoId = "vid-1",
                songVideoId = "vid-2",
            ),
        )
        assertTrue(
            com.music.bitchord.ui.player.effectiveHideLyricsPreview(
                hideLyricsPreview = true,
                autoHideLyricsInVideo = true,
                showingVideo = true,
                revealedVideoId = "vid-1",
                songVideoId = "vid-1",
            ),
        )

        val prev = DesktopAppearanceSettings.autoHideLyricsInVideo.value
        try {
            DesktopAppearanceSettings.setAutoHideLyricsInVideo(false)
            assertFalse(DesktopAppearanceSettings.autoHideLyricsInVideo.value)
            DesktopAppearanceSettings.setAutoHideLyricsInVideo(true)
            assertTrue(DesktopAppearanceSettings.autoHideLyricsInVideo.value)
        } finally {
            DesktopAppearanceSettings.setAutoHideLyricsInVideo(prev)
        }
    }
}
