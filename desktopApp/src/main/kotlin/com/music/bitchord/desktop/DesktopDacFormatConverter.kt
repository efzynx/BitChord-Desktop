package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fits a lossless track to a DAC that cannot accept its exact rate or bit depth on an exclusive path.
 * Uses a polyphase windowed-sinc resampler and TPDF dither (ported from mobile `DacFormatConverter`).
 */
internal class DesktopDacFormatConverter(
    private val channels: Int,
    private val inputRate: Int,
    private val outputRate: Int,
    validBits: Int,
    private val containerBits: Int,
    private val alsaLsb24: Boolean = false,
) {
    private val resampler = if (inputRate != outputRate) Resampler(inputRate, outputRate, channels) else null
    private val frame = DoubleArray(channels)
    private val random = Random(0)
    private val scale = (1L shl (validBits - 1)).toDouble()
    private val leftShift = 32 - validBits
    private val outFrameBytes = (containerBits / 8) * channels

    fun maxOutputBytesFor(inputSamples: Int): Int {
        val inFrames = inputSamples / channels
        val outFrames = ceil(inFrames.toDouble() * outputRate / inputRate).toInt() + 128
        return outFrames * outFrameBytes
    }

    /** Converts [inputSampleCount] left-aligned 32-bit interleaved samples into [output] and returns byte count. */
    fun process(input: IntArray, inputSampleCount: Int, output: ByteArray): Int {
        val totalFrames = inputSampleCount / channels
        var inFrame = 0
        var outOffset = 0
        val resampler = resampler

        if (resampler == null) {
            while (inFrame < totalFrames && outOffset + outFrameBytes <= output.size) {
                readFrame(input, inFrame++)
                outOffset = writeFrame(output, outOffset)
            }
            return outOffset
        }

        while (true) {
            while (resampler.ready) {
                if (outOffset + outFrameBytes > output.size) return outOffset
                resampler.pull(frame)
                outOffset = writeFrame(output, outOffset)
            }
            if (inFrame >= totalFrames) return outOffset
            readFrame(input, inFrame++)
            resampler.push(frame)
        }
    }

    fun drain(output: ByteArray): Pair<Int, Boolean> {
        val resampler = resampler ?: return 0 to true
        var outOffset = 0
        while (!resampler.drained) {
            while (!resampler.ready) resampler.pushSilence()
            if (outOffset + outFrameBytes > output.size) return outOffset to false
            resampler.pull(frame)
            outOffset = writeFrame(output, outOffset)
        }
        return outOffset to true
    }

    fun reset() {
        resampler?.reset()
    }

    private fun readFrame(input: IntArray, frameIndex: Int) {
        val base = frameIndex * channels
        for (channel in 0 until channels) {
            frame[channel] = input[base + channel] / 2_147_483_648.0
        }
    }

    private fun writeFrame(output: ByteArray, offset: Int): Int {
        var at = offset
        for (channel in 0 until channels) {
            val sample = frame[channel]
            val scaled = if (sample == 0.0) 0.0 else sample * scale + random.nextDouble() - random.nextDouble()
            val rounded = floor(scaled + 0.5).coerceIn(-scale, scale - 1)
            val packed32 = (rounded.toLong() shl leftShift).toInt()
            at = DesktopBitPerfectPcm.writeSample(output, at, packed32, containerBits, alsaLsb24)
        }
        return at
    }

    private class Resampler(inputRate: Int, outputRate: Int, private val channels: Int) {
        private val divisor = gcd(inputRate, outputRate)
        private val upFactor = outputRate / divisor
        private val downFactor = inputRate / divisor
        private val halfTaps = ceil(ZERO_CROSSINGS * maxOf(1.0, downFactor.toDouble() / upFactor)).toInt()
        private val taps = halfTaps * 2
        private val table = buildTable()
        private val history = DoubleArray(taps * 2 * channels)
        private var filled = 0
        private var start = 0
        private var phase = 0
        private var pushed = 0L
        private var pulled = 0L

        init {
            reset()
        }

        val ready: Boolean get() = start + taps <= filled
        val drained: Boolean get() = pulled >= (pushed * upFactor + downFactor - 1) / downFactor

        fun push(frame: DoubleArray) {
            pushed++
            makeRoom()
            frame.copyInto(history, filled * channels)
            filled++
        }

        fun pushSilence() {
            makeRoom()
            history.fill(0.0, filled * channels, (filled + 1) * channels)
            filled++
        }

        fun pull(into: DoubleArray) {
            val row = phase * taps
            for (channel in 0 until channels) {
                var sum = 0.0
                var at = start * channels + channel
                for (tap in 0 until taps) {
                    sum += table[row + tap] * history[at]
                    at += channels
                }
                into[channel] = sum * HEADROOM
            }
            phase += downFactor
            start += phase / upFactor
            phase %= upFactor
            pulled++
        }

        fun reset() {
            history.fill(0.0)
            filled = halfTaps - 1
            start = 0
            phase = 0
            pushed = 0
            pulled = 0
        }

        private fun makeRoom() {
            if ((filled + 1) * channels <= history.size) return
            history.copyInto(history, 0, start * channels, filled * channels)
            filled -= start
            start = 0
        }

        private fun buildTable(): DoubleArray {
            val result = DoubleArray(upFactor * taps)
            val cutoff = 0.5 * CUTOFF * min(1.0, upFactor.toDouble() / downFactor)
            val windowDivisor = besselI0(KAISER_BETA)
            for (row in 0 until upFactor) {
                val offset = row.toDouble() / upFactor
                var sum = 0.0
                for (tap in 0 until taps) {
                    val distance = offset - (tap - (halfTaps - 1))
                    val position = distance / halfTaps
                    val window = besselI0(KAISER_BETA * sqrt(maxOf(0.0, 1 - position * position))) / windowDivisor
                    val value = sinc(2 * cutoff * distance) * window
                    result[row * taps + tap] = value
                    sum += value
                }
                for (tap in 0 until taps) result[row * taps + tap] /= sum
            }
            return result
        }

        private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            var k = 1
            while (term > sum * 1e-12) {
                term *= (x / (2 * k)) * (x / (2 * k))
                sum += term
                k++
            }
            return sum
        }
    }

    companion object {
        private const val ZERO_CROSSINGS = 64
        private const val CUTOFF = 0.96
        private const val KAISER_BETA = 10.06
        private const val HEADROOM = 0.891
        private const val MAX_FILTER_PHASES = 4096

        fun canResample(inputRate: Int, outputRate: Int): Boolean =
            inputRate > 0 && outputRate > 0 && outputRate / gcd(inputRate, outputRate) <= MAX_FILTER_PHASES

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
