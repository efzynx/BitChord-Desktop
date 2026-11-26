package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests for desktop bit-perfect PCM negotiation, packing, resampling, and ALSA device routing. */
class DesktopBitPerfectTest {

    @Test
    fun `rankRates prioritizes exact rate then same-family upsampling before cross-family`() {
        val offered = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000)

        assertEquals(
            listOf(44_100, 88_200, 176_400, 48_000, 96_000, 192_000),
            DesktopBitPerfectPcm.rankRates(44_100, offered),
        )
        assertEquals(
            listOf(96_000, 192_000, 48_000, 176_400, 88_200, 44_100),
            DesktopBitPerfectPcm.rankRates(96_000, offered),
        )
    }

    @Test
    fun `outputBits and dacBits select smallest lossless container or fall back cleanly`() {
        assertEquals(16, DesktopBitPerfectPcm.outputBits(16, listOf(16, 24, 32)))
        assertEquals(32, DesktopBitPerfectPcm.outputBits(24, listOf(16, 32)))
        assertNull(DesktopBitPerfectPcm.outputBits(24, listOf(16)))
        assertEquals(16, DesktopBitPerfectPcm.dacBits(24, listOf(16)))
    }

    @Test
    fun `packLeftAligned32 encodes 16-bit and all 24-bit or 32-bit container variants accurately`() {
        val samples = intArrayOf(0x12345600, -0x00800000)

        val out16 = ByteArray(4)
        assertEquals(4, DesktopBitPerfectPcm.packLeftAligned32(samples, 2, containerBits = 16, alsaLsb24 = false, out16))
        assertContentEquals(
            byteArrayOf(0x34, 0x12, 0x80.toByte(), 0xFF.toByte()),
            out16,
        )

        val out24Packed = ByteArray(6)
        assertEquals(6, DesktopBitPerfectPcm.packLeftAligned32(samples, 2, containerBits = 24, alsaLsb24 = false, out24Packed))
        assertContentEquals(
            byteArrayOf(0x56, 0x34, 0x12, 0x00, 0x80.toByte(), 0xFF.toByte()),
            out24Packed,
        )

        val outS24Lsb32 = ByteArray(8)
        assertEquals(8, DesktopBitPerfectPcm.packLeftAligned32(samples, 2, containerBits = 32, alsaLsb24 = true, outS24Lsb32))
        assertContentEquals(
            byteArrayOf(
                0x56, 0x34, 0x12, 0x00,
                0x00, 0x80.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            ),
            outS24Lsb32,
        )

        val outS32Msb = ByteArray(8)
        assertEquals(8, DesktopBitPerfectPcm.packLeftAligned32(samples, 2, containerBits = 32, alsaLsb24 = false, outS32Msb))
        assertContentEquals(
            byteArrayOf(
                0x00, 0x56, 0x34, 0x12,
                0x00, 0x00, 0x80.toByte(), 0xFF.toByte(),
            ),
            outS32Msb,
        )
    }

    @Test
    fun `DacFormatConverter preserves true digital silence and resamples tones without clipping`() {
        assertTrue(DesktopDacFormatConverter.canResample(96_000, 48_000))
        assertTrue(DesktopDacFormatConverter.canResample(44_100, 48_000))

        val silent = DesktopDacFormatConverter(
            channels = 2,
            inputRate = 96_000,
            outputRate = 48_000,
            validBits = 16,
            containerBits = 16,
        )
        val zeros = IntArray(512)
        val silentOut = ByteArray(silent.maxOutputBytesFor(zeros.size))
        val silentBytes = silent.process(zeros, zeros.size, silentOut)
        for (i in 0 until silentBytes) {
            assertEquals(0.toByte(), silentOut[i], "non-zero dither on silent byte $i")
        }

        val converter = DesktopDacFormatConverter(
            channels = 2,
            inputRate = 96_000,
            outputRate = 48_000,
            validBits = 16,
            containerBits = 16,
        )
        val inFrames = 2_048
        val sine = IntArray(inFrames * 2)
        for (f in 0 until inFrames) {
            val s = (sin(2.0 * PI * 1_000.0 * f / 96_000.0) * 1_800_000_000.0).toInt()
            sine[f * 2] = s
            sine[f * 2 + 1] = s
        }
        val out = ByteArray(converter.maxOutputBytesFor(sine.size))
        val written = converter.process(sine, sine.size, out)
        val (drained, done) = converter.drain(out.copyOf())
        assertTrue(done)
        assertTrue(written > 1_000)
        assertTrue(drained >= 0)

        var peak = 0
        for (at in 0 until written - 1 step 2) {
            val sample = ((out[at].toInt() and 0xFF) or (out[at + 1].toInt() shl 8)).toShort().toInt()
            peak = maxOf(peak, abs(sample))
        }
        assertTrue(peak in 20_000..32_767, "resampled peak out of expected range: $peak")
    }

    @Test
    fun `Linux ALSA card parsing and device resolution cover hardware and virtual defaults`() {
        assertEquals(0 to 0, DesktopLinuxAudio.parseAlsaCardAndDevice("hw:0,0"))
        assertEquals(2 to 3, DesktopLinuxAudio.parseAlsaCardAndDevice("plughw:2,3"))
        assertEquals(1 to 0, DesktopLinuxAudio.parseAlsaCardAndDevice("PCH [HDA Intel], hw:1"))
        assertNull(DesktopLinuxAudio.parseAlsaCardAndDevice("pipewire"))

        val hwDevices = listOf(
            DesktopAudioDevice("hw:0,0", "ALC897 Analog", "ALSA Direct (hw:0,0)"),
            DesktopAudioDevice("hw:1,0", "USB-C DAC", "ALSA Direct (hw:1,0)"),
        )
        assertEquals("hw:0,0", DesktopLinuxAudio.resolveHwTarget(DesktopAudioDevices.SYSTEM_DEFAULT, hwDevices)?.id)
        assertEquals("hw:1,0", DesktopLinuxAudio.resolveHwTarget("plughw:1,0", hwDevices)?.id)
        assertEquals("hw:1,0", DesktopLinuxAudio.resolveHwTarget("USB-C DAC", hwDevices)?.id)
        assertEquals("hw:0,0", DesktopLinuxAudio.resolveHwTarget("PipeWire Mixer", hwDevices)?.id)
        assertNull(DesktopLinuxAudio.resolveHwTarget("Bluetooth Headset", hwDevices))
    }

    @Test
    fun `DesktopBitPerfectStatus formats rate and depth summaries`() {
        val exact = DesktopBitPerfectStatus(
            active = true,
            driver = "ALSA Direct",
            deviceName = "ALC897 Analog",
            sampleRateHz = 44_100,
            outputBits = 16,
            sourceBits = 16,
            sourceRateHz = 44_100,
            exact = true,
        )
        assertEquals("44.1 kHz · 16-bit", exact.label)

        val limited = DesktopBitPerfectStatus(
            active = true,
            driver = "ALSA Direct",
            deviceName = "ALC897 Analog",
            sampleRateHz = 96_000,
            outputBits = 24,
            sourceBits = 24,
            sourceRateHz = 192_000,
            exact = false,
            dacLimited = true,
        )
        assertEquals("192 kHz -> 96 kHz · 24-bit", limited.label)

        assertFalse(DesktopBitPerfectStatus().active)
        assertNull(DesktopBitPerfectStatus().label)
    }

    @Test
    fun `DesktopDacVolume maps dB linearly across hardware range and falls back to null when fixed`() {
        val snap = DesktopHardwareVolumeSnapshot(
            controlName = "Master",
            minDb = -65.25,
            maxDb = 0.0,
            currentDb = -22.50,
            muted = false,
        )
        assertTrue(snap.available)
        val frac = DesktopDacVolume.fractionOf(snap)
        assertTrue(frac in 0.64f..0.67f, "expected ~0.655 for -22.5 dB, got $frac")
        assertEquals(0.0, DesktopDacVolume.dbOf(snap, 1f), 1e-6)
        assertEquals(-65.25, DesktopDacVolume.dbOf(snap, 0f), 1e-6)

        val wideSnap = DesktopHardwareVolumeSnapshot("USB DAC", minDb = -127.0, maxDb = 0.0, currentDb = -40.0)
        // Floors at -80 dB so wide-range USB DAC sliders stay responsive across the full travel.
        assertEquals(0.5f, DesktopDacVolume.fractionOf(wideSnap), 0.01f)

        var lastDb: Double? = null
        var lastMute: Boolean? = null
        try {
            DesktopDacVolume.onOpened(
                initial = snap,
                setter = { db, mute ->
                    lastDb = db
                    lastMute = mute
                    db
                },
            )
            assertEquals(frac, DesktopDacVolume.level.value!!, 0.01f)

            DesktopDacVolume.set(1.0f)
            assertEquals(1.0f, DesktopDacVolume.level.value!!, 0.001f)
            val deadline1 = System.currentTimeMillis() + 500
            while (lastDb == null && System.currentTimeMillis() < deadline1) Thread.sleep(10)
            assertEquals(0.0, lastDb!!, 0.05)
            assertEquals(false, lastMute)

            DesktopDacVolume.toggleMute()
            assertEquals(0f, DesktopDacVolume.level.value!!, 0.001f)
            val deadline2 = System.currentTimeMillis() + 500
            while (lastMute != true && System.currentTimeMillis() < deadline2) Thread.sleep(10)
            assertEquals(true, lastMute)

            DesktopDacVolume.onOpened(initial = null, setter = { _, _ -> null })
            assertNull(DesktopDacVolume.level.value)
        } finally {
            DesktopDacVolume.onClosed()
        }
    }
}
