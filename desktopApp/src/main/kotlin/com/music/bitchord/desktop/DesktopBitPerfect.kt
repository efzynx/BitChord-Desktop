package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class DesktopBitPerfectMode {
    OFF,
    EXCLUSIVE,
    ;

    val isEnabled: Boolean get() = this != OFF
}

internal data class DesktopBitPerfectStatus(
    val active: Boolean = false,
    val driver: String? = null,
    val deviceName: String? = null,
    val sampleRateHz: Int = 0,
    val outputBits: Int = 0,
    val sourceBits: Int = 0,
    val sourceRateHz: Int = 0,
    val exact: Boolean = false,
    val dacLimited: Boolean = false,
    val fallbackReason: String? = null,
) {
    val label: String?
        get() = when {
            active && dacLimited && sourceRateHz > 0 && sampleRateHz > 0 ->
                "${formatRateKhz(sourceRateHz)} -> ${formatRateKhz(sampleRateHz)} · $outputBits-bit"
            active && outputBits > 0 && sampleRateHz > 0 ->
                "${formatRateKhz(sampleRateHz)} · $outputBits-bit"
            else -> null
        }
}

internal object DesktopBitPerfect {

    private const val KEY_MODE = "bit_perfect_mode"
    private const val KEY_CONVERT = "bit_perfect_convert_dac"

    private val persistence = DesktopPersistence()

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<DesktopBitPerfectMode> = _mode

    private val _convertForLimitedDac = MutableStateFlow(persistence.boolean(KEY_CONVERT, true))
    val convertForLimitedDac: StateFlow<Boolean> = _convertForLimitedDac

    private val _status = MutableStateFlow(DesktopBitPerfectStatus())
    val status: StateFlow<DesktopBitPerfectStatus> = _status

    private val _ready = MutableStateFlow(computeReady(_mode.value))
    /** True when Bit-Perfect is enabled and the host has a supported output backend/device. */
    val ready: StateFlow<Boolean> = _ready

    fun setMode(mode: DesktopBitPerfectMode) {
        persistence.saveString(KEY_MODE, mode.name)
        _mode.value = mode
        recomputeReady()
        if (!mode.isEnabled) {
            _status.value = DesktopBitPerfectStatus()
        }
    }

    fun setConvertForLimitedDac(enabled: Boolean) {
        persistence.saveBoolean(KEY_CONVERT, enabled)
        _convertForLimitedDac.value = enabled
    }

    fun publishStatus(status: DesktopBitPerfectStatus) {
        _status.value = status
    }

    fun recomputeReady() {
        _ready.value = computeReady(_mode.value)
    }

    fun isSupportedOnHost(): Boolean = DesktopAudioDevices.hasBitPerfectOutput()

    private fun computeReady(mode: DesktopBitPerfectMode): Boolean =
        mode.isEnabled && isSupportedOnHost()

    private fun readMode(): DesktopBitPerfectMode {
        val raw = persistence.string(KEY_MODE, DesktopBitPerfectMode.OFF.name)
        return DesktopBitPerfectMode.entries.firstOrNull { it.name == raw } ?: DesktopBitPerfectMode.OFF
    }
}

internal fun formatRateKhz(rateHz: Int): String =
    if (rateHz % 1_000 == 0) {
        "${rateHz / 1_000} kHz"
    } else {
        String.format(java.util.Locale.ROOT, "%.1f kHz", rateHz / 1_000.0)
    }

/**
 * Pure JVM lossless PCM helpers mirroring mobile `BitPerfectPcm`.
 */
internal object DesktopBitPerfectPcm {

    private val INTEGER_BITS = listOf(16, 24, 32)

    val STANDARD_RATES = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000, 352_800, 384_000)

    fun outputBits(sourceBits: Int, supported: Collection<Int>): Int? =
        supported.filter { it in INTEGER_BITS && it >= sourceBits }.minOrNull()

    fun dacBits(sourceBits: Int, supported: Collection<Int>): Int? =
        outputBits(sourceBits, supported) ?: supported.filter { it in INTEGER_BITS }.maxOrNull()

    fun rankRates(source: Int, offered: Collection<Int>): List<Int> {
        fun family(rate: Int) = rate % 11_025 == 0
        return offered.distinct()
            .filter { it == source || DesktopDacFormatConverter.canResample(source, it) }
            .sortedWith(
                compareBy<Int> {
                    when {
                        it == source -> 0
                        family(it) == family(source) && it > source -> 1
                        family(it) == family(source) -> 2
                        it > source -> 3
                        else -> 4
                    }
                }.thenBy { if (it > source) it else -it },
            )
    }

    /**
     * Packs [count] left-aligned 32-bit signed samples into little-endian hardware bytes,
     * returning the number of bytes written to [out].
     */
    fun packLeftAligned32(
        samples: IntArray,
        count: Int,
        containerBits: Int,
        alsaLsb24: Boolean,
        out: ByteArray,
    ): Int {
        var at = 0
        when (containerBits) {
            16 -> for (i in 0 until count) {
                val v = samples[i] shr 16
                out[at++] = v.toByte()
                out[at++] = (v shr 8).toByte()
            }
            24 -> for (i in 0 until count) {
                val v = samples[i] shr 8
                out[at++] = v.toByte()
                out[at++] = (v shr 8).toByte()
                out[at++] = (v shr 16).toByte()
            }
            else -> if (alsaLsb24) {
                // ALSA S24_LE stores sign-extended 24-bit in the lower 24 bits of a 32-bit word.
                for (i in 0 until count) {
                    val v = samples[i] shr 8
                    out[at++] = v.toByte()
                    out[at++] = (v shr 8).toByte()
                    out[at++] = (v shr 16).toByte()
                    out[at++] = (v shr 24).toByte()
                }
            } else {
                for (i in 0 until count) {
                    val v = samples[i]
                    out[at++] = v.toByte()
                    out[at++] = (v shr 8).toByte()
                    out[at++] = (v shr 16).toByte()
                    out[at++] = (v shr 24).toByte()
                }
            }
        }
        return at
    }

    fun writeSample(out: ByteArray, offset: Int, leftAligned32: Int, containerBits: Int, alsaLsb24: Boolean): Int =
        when (containerBits) {
            16 -> {
                val v = leftAligned32 shr 16
                out[offset] = v.toByte()
                out[offset + 1] = (v shr 8).toByte()
                offset + 2
            }
            24 -> {
                val v = leftAligned32 shr 8
                out[offset] = v.toByte()
                out[offset + 1] = (v shr 8).toByte()
                out[offset + 2] = (v shr 16).toByte()
                offset + 3
            }
            else -> {
                val v = if (alsaLsb24) leftAligned32 shr 8 else leftAligned32
                out[offset] = v.toByte()
                out[offset + 1] = (v shr 8).toByte()
                out[offset + 2] = (v shr 16).toByte()
                out[offset + 3] = (v shr 24).toByte()
                offset + 4
            }
        }
}
