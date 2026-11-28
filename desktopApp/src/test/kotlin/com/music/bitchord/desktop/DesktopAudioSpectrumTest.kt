package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopAudioSpectrumTest {

    @Test
    fun `fft resolves full-scale sine wave peak near 0 dBFS at exact frequency bin`() {
        val sampleRate = 48_000.0
        val targetHz = 10_000.0
        val left = FloatArray(DesktopAudioSpectrumAnalyzer.FFT_SIZE) { i ->
            sin(2.0 * PI * targetHz * i / sampleRate).toFloat()
        }
        val db = DesktopAudioSpectrumAnalyzer.fftStereoPowerDb(left, left)
        val expectedBin = ((targetHz / (sampleRate / 2.0)) * DesktopAudioSpectrumAnalyzer.FFT_BINS).roundToInt()

        var peakBin = 0
        var peakVal = -200f
        for (b in db.indices) {
            if (db[b] > peakVal) {
                peakVal = db[b]
                peakBin = b
            }
        }
        assertTrue(kotlin.math.abs(peakBin - expectedBin) <= 1, "expected bin ~$expectedBin, got $peakBin")
        assertTrue(peakVal in -1.0f..0.2f, "expected 0 dBFS peak, got $peakVal")
        assertTrue(db[100] < -75f, "expected silent low bin, got ${db[100]}")
    }

    @Test
    fun `bit-depth probe detects 16-bit audio zero-padded inside a 24-bit container`() {
        val count = 2048
        val padded24 = IntArray(count) { i -> ((i * 7919) and 0xFFFF) shl 16 }
        val probePadded = DesktopAudioSpectrumAnalyzer.analyzeIntPcmBitDepth(padded24, count, containerBits = 24)
        assertEquals(16, probePadded.effectiveBitDepth)
        assertTrue(probePadded.zeroPaddedBits)

        val real24 = IntArray(count) { i -> ((i * 104_729) and 0xFFFFFF) shl 8 }
        val probeReal24 = DesktopAudioSpectrumAnalyzer.analyzeIntPcmBitDepth(real24, count, containerBits = 24)
        assertEquals(24, probeReal24.effectiveBitDepth)
        assertFalse(probeReal24.zeroPaddedBits)

        val real20 = IntArray(count) { i -> ((i * 15_485_863) and 0xFFFFF) shl 12 }
        val probeReal20 = DesktopAudioSpectrumAnalyzer.analyzeIntPcmBitDepth(real20, count, containerBits = 24)
        assertEquals(20, probeReal20.effectiveBitDepth)
        assertFalse(probeReal20.zeroPaddedBits)
    }

    @Test
    fun `classifier separates native hi-res, cd lossless, upsample, transcode, and lossy sources`() {
        fun makeBins(sampleRateHz: Int, profile: (hz: Double) -> Float): FloatArray {
            val nyquist = sampleRateHz / 2.0
            return FloatArray(DesktopAudioSpectrumAnalyzer.FFT_BINS) { b ->
                profile((b.toDouble() / DesktopAudioSpectrumAnalyzer.FFT_BINS) * nyquist)
            }
        }

        // 1. Native Lossy
        val lossyBins = makeBins(44_100) { hz -> if (hz < 18_000) -35f else -112f }
        val lossy = DesktopAudioSpectrumAnalyzer.analyzeSpectralProfile(
            sampleRateHz = 44_100,
            isLosslessCodec = false,
            containerBitDepth = null,
            effectiveBitDepth = null,
            zeroPaddedBits = false,
            meanDbBins = lossyBins,
            p95DbBins = lossyBins,
        )
        assertEquals(DesktopSpectrumVerdict.LOSSY_SOURCE, lossy.verdict)

        // 2. FLAC transcoded from 128 kbps MP3 (brickwall shelf at 16 kHz)
        val transcodeBins = makeBins(44_100) { hz -> if (hz < 16_000) -36f else -108f }
        val transcode = DesktopAudioSpectrumAnalyzer.analyzeSpectralProfile(
            sampleRateHz = 44_100,
            isLosslessCodec = true,
            containerBitDepth = 16,
            effectiveBitDepth = 16,
            zeroPaddedBits = false,
            meanDbBins = transcodeBins,
            p95DbBins = transcodeBins,
        )
        assertEquals(DesktopSpectrumVerdict.POSSIBLE_LOSSY_TRANSCODE, transcode.verdict)
        val cliff = transcode.sharpCliffHz
        assertNotNull(cliff)
        assertTrue(cliff in 15_000..17_000)

        // 3. Authentic 16/44.1 CD Lossless
        val cdBins = makeBins(44_100) { hz -> (-28.0 - (hz / 22_050.0) * 18.0).toFloat() }
        val cdLossless = DesktopAudioSpectrumAnalyzer.analyzeSpectralProfile(
            sampleRateHz = 44_100,
            isLosslessCodec = true,
            containerBitDepth = 16,
            effectiveBitDepth = 16,
            zeroPaddedBits = false,
            meanDbBins = cdBins,
            p95DbBins = cdBins,
        )
        assertEquals(DesktopSpectrumVerdict.NATIVE_LOSSLESS, cdLossless.verdict)

        // 4. 96/24 Upsampled from 44.1 kHz (steep wall at 22 kHz)
        val upsampleBins = makeBins(96_000) { hz -> if (hz < 21_500) -34f else -108f }
        val upsample = DesktopAudioSpectrumAnalyzer.analyzeSpectralProfile(
            sampleRateHz = 96_000,
            isLosslessCodec = true,
            containerBitDepth = 24,
            effectiveBitDepth = 24,
            zeroPaddedBits = false,
            meanDbBins = upsampleBins,
            p95DbBins = upsampleBins,
        )
        assertEquals(DesktopSpectrumVerdict.POSSIBLE_UPSAMPLE, upsample.verdict)

        // 5. Native 96/24 Hi-Res with real energy through 38 kHz
        val hiResBins = makeBins(96_000) { hz ->
            if (hz < 39_000) (-28.0 - (hz / 48_000.0) * 28.0).toFloat() else -92f
        }
        val hiRes = DesktopAudioSpectrumAnalyzer.analyzeSpectralProfile(
            sampleRateHz = 96_000,
            isLosslessCodec = true,
            containerBitDepth = 24,
            effectiveBitDepth = 24,
            zeroPaddedBits = false,
            meanDbBins = hiResBins,
            p95DbBins = hiResBins,
        )
        assertEquals(DesktopSpectrumVerdict.NATIVE_HI_RES, hiRes.verdict)
    }

    @Test
    fun `analyzer decodes local wav file and renders calibrated spectrogram`() = runBlocking {
        val sampleRate = 44_100
        val seconds = 1
        val totalFrames = sampleRate * seconds
        val pcmBytes = ByteBuffer.allocate(totalFrames * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalFrames) {
            val t = i.toDouble() / sampleRate
            // Full-band multi-tone from 1 kHz up to 20.5 kHz so lossless ceiling hits >20 kHz cleanly.
            val mix = 0.25 * sin(2.0 * PI * 1_000.0 * t) +
                0.20 * sin(2.0 * PI * 10_000.0 * t) +
                0.20 * sin(2.0 * PI * 18_500.0 * t) +
                0.20 * sin(2.0 * PI * 20_500.0 * t)
            val s16 = (mix * 30_000.0).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            pcmBytes.putShort(s16)
            pcmBytes.putShort(s16)
        }
        val data = pcmBytes.array()
        val wavHeader = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + data.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(2) // stereo
            putInt(sampleRate)
            putInt(sampleRate * 4)
            putShort(4)
            putShort(16)
            put("data".toByteArray())
            putInt(data.size)
        }.array()

        val tmpWav = Files.createTempFile("bitchord-spectrum-test", ".wav")
        try {
            Files.write(tmpWav, wavHeader + data)
            val song = Song(
                videoId = "local:test",
                title = "Calibration Sweep",
                artist = "Test",
                thumbnailUrl = null,
                localUri = tmpWav.toUri().toString(),
                localPath = tmpWav.toAbsolutePath().toString(),
            )
            val result = DesktopAudioSpectrumAnalyzer.analyze(song, forceRefresh = true).getOrThrow()
            assertEquals(44_100, result.metrics.sampleRateHz)
            assertEquals(DesktopSpectrumVerdict.NATIVE_LOSSLESS, result.verdict)
            assertTrue(result.metrics.estimatedCutoffHz >= 19_500, "expected >=19.5kHz cutoff, got ${result.metrics.estimatedCutoffHz}")
            assertEquals(DesktopAudioSpectrumAnalyzer.SPEC_WIDTH, result.spectrogram.width)
            assertEquals(DesktopAudioSpectrumAnalyzer.SPEC_HEIGHT, result.spectrogram.height)
        } finally {
            Files.deleteIfExists(tmpWav)
        }
    }

    @Test
    fun `compare candidate ranker puts matching song titles first and filters by query or extension`() {
        val trackA = Song(
            videoId = "local:a",
            title = "Get Lucky",
            artist = "Daft Punk",
            thumbnailUrl = null,
            localPath = "/music/Get Lucky (24-96).flac",
        )
        val sameSongMp3 = Song(
            videoId = "local:b",
            title = "Get Lucky (Radio Edit)",
            artist = "Daft Punk",
            thumbnailUrl = null,
            localPath = "/downloads/Get Lucky.mp3",
        )
        val unrelated = Song(
            videoId = "local:c",
            title = "Midnight City",
            artist = "M83",
            thumbnailUrl = null,
            localPath = "/music/Midnight City.flac",
        )

        val defaultOrder = DesktopAudioSpectrumAnalyzer.rankCompareCandidates(
            candidates = listOf(unrelated, trackA, sameSongMp3),
            referenceSong = trackA,
            query = "",
        )
        assertEquals(sameSongMp3.videoId, defaultOrder.first().song.videoId)
        assertTrue(defaultOrder.first().isSuggestedMatch)
        assertFalse(defaultOrder.last().isSuggestedMatch)

        val mp3Filtered = DesktopAudioSpectrumAnalyzer.rankCompareCandidates(
            candidates = listOf(unrelated, trackA, sameSongMp3),
            referenceSong = trackA,
            query = "mp3",
        )
        assertEquals(1, mp3Filtered.size)
        assertEquals(sameSongMp3.videoId, mp3Filtered.first().song.videoId)
    }
}
