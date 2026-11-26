package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Analyzes audio waveforms/energy envelopes on desktop to find the exact time alignment
 * offset between two versions or renditions of the same track (e.g. YouTube music video with
 * an intro vs studio audio release or lossless addon stream).
 *
 * Mirrors mobile `VersionAudioAligner` signal-processing parameters and FFT correlation.
 */
internal object DesktopVersionAudioAligner {

    private const val HOP_MS = 10.0 // 100 frames per second (10ms resolution)
    private const val WIN_MS = 40.0 // 40ms analysis window
    private const val MAX_LAG_SECONDS = 40.0 // Search up to ±40s drift/intro
    private const val MIN_CONFIDENCE = 0.40 // Minimum normalized correlation
    private const val ANALYSIS_WINDOW_SECONDS = 45.0
    private const val ANALYSIS_SAMPLE_RATE = 22_050

    // (sourceRenditionKey, targetRenditionKey) -> offsetMs
    private val offsetCache = ConcurrentHashMap<Pair<String, String>, Long>()

    enum class CutPhase { FETCHING, MEASURING, ALIGNED, FAILED }

    data class AlignmentStatus(
        val sourceId: String,
        val targetId: String,
        val phase: CutPhase,
        val offsetMs: Long? = null,
        val fromCache: Boolean = false,
    )

    val status = MutableStateFlow<AlignmentStatus?>(null)

    private val attempt = AtomicLong()

    fun beginAttempt(sourceId: String, targetId: String): Long {
        val token = attempt.incrementAndGet()
        status.value = AlignmentStatus(sourceId, targetId, CutPhase.FETCHING)
        return token
    }

    fun publishPhase(
        token: Long,
        phase: CutPhase,
        offsetMs: Long? = null,
        fromCache: Boolean = false,
    ) {
        if (attempt.get() != token) return
        val current = status.value ?: return
        status.value = current.copy(phase = phase, offsetMs = offsetMs, fromCache = fromCache)
    }

    /** Stable cache key distinguishing both track identity and source rendition. */
    fun renditionKey(song: Song, stream: DesktopStream): String {
        val sourceTag = stream.sourceId?.takeIf(String::isNotBlank)
            ?: if (stream.url.startsWith("http", ignoreCase = true)) "direct" else "local"
        return "${song.videoId}#$sourceTag"
    }

    fun getCachedOffsetMs(sourceKey: String, targetKey: String): Long? {
        if (sourceKey == targetKey) return 0L
        return offsetCache[sourceKey to targetKey]
    }

    fun clearCache() {
        offsetCache.clear()
        status.value = null
    }

    /**
     * Computes or returns cached time shift in milliseconds from [sourceStream] to [targetStream].
     * Target timestamp = (source timestamp + offsetMs).
     * Returns `null` when measurement could not reach [MIN_CONFIDENCE] or stream could not decode.
     */
    suspend fun findOffsetMs(
        sourceSong: Song,
        sourceStream: DesktopStream,
        targetSong: Song,
        targetStream: DesktopStream,
    ): Long? = withContext(Dispatchers.IO) {
        val srcKey = renditionKey(sourceSong, sourceStream)
        val tgtKey = renditionKey(targetSong, targetStream)
        if (srcKey == tgtKey) return@withContext 0L

        val token = beginAttempt(srcKey, tgtKey)
        offsetCache[srcKey to tgtKey]?.let { cached ->
            publishPhase(token, CutPhase.ALIGNED, offsetMs = cached, fromCache = true)
            return@withContext cached
        }

        try {
            var sourceSamples: FloatArray? = null
            var targetSamples: FloatArray? = null
            val decodeMs = measureTimeMillis {
                coroutineScope {
                    val srcJob = async(Dispatchers.IO) { decodeOpeningMono(sourceStream) }
                    val tgtJob = async(Dispatchers.IO) { decodeOpeningMono(targetStream) }
                    sourceSamples = srcJob.await()
                    targetSamples = tgtJob.await()
                }
            }
            val src = sourceSamples
            val tgt = targetSamples
            if (src == null || tgt == null || src.isEmpty() || tgt.isEmpty()) {
                DesktopTrackLog.log("smart alignment skipped for '${sourceSong.title}': unable to decode stream window")
                publishPhase(token, CutPhase.FAILED)
                return@withContext null
            }

            publishPhase(token, CutPhase.MEASURING)

            var envelopes: Pair<FloatArray, FloatArray> = FloatArray(0) to FloatArray(0)
            val envelopeMs = measureTimeMillis {
                envelopes = computeEnergyEnvelope(src, ANALYSIS_SAMPLE_RATE.toDouble()) to
                    computeEnergyEnvelope(tgt, ANALYSIS_SAMPLE_RATE.toDouble())
            }

            var correlation = Correlation(0, 0.0, 0, 0)
            val correlateMs = measureTimeMillis {
                correlation = crossCorrelate(envelopes.first, envelopes.second)
            }

            DesktopTrackLog.log(
                "smart alignment '${sourceSong.title}' ($srcKey -> $tgtKey): " +
                    "decode=${decodeMs}ms env=${envelopeMs}ms corr=${correlateMs}ms " +
                    "lag=${correlation.lagFrames} conf=${"%.2f".format(correlation.confidence)}",
            )

            if (correlation.confidence >= MIN_CONFIDENCE) {
                if (correlation.lagFrames == correlation.floor || correlation.lagFrames == correlation.ceiling) {
                    DesktopTrackLog.log(
                        "smart alignment rejected edge-pinned lag (${correlation.lagFrames} in ${correlation.floor}..${correlation.ceiling})",
                    )
                    publishPhase(token, CutPhase.FAILED)
                    return@withContext null
                }
                val offsetMs = (correlation.lagFrames * HOP_MS).toLong()
                offsetCache[srcKey to tgtKey] = offsetMs
                offsetCache[tgtKey to srcKey] = -offsetMs
                publishPhase(token, CutPhase.ALIGNED, offsetMs = offsetMs, fromCache = false)
                return@withContext offsetMs
            }

            publishPhase(token, CutPhase.FAILED)
            null
        } catch (e: Throwable) {
            DesktopTrackLog.log("smart alignment failed for '${sourceSong.title}': ${e.message}")
            publishPhase(token, CutPhase.FAILED)
            null
        }
    }

    /** Decodes up to [ANALYSIS_WINDOW_SECONDS] of mono float audio from [stream] via FFmpeg. */
    private fun decodeOpeningMono(stream: DesktopStream): FloatArray? {
        if (stream.url.isBlank()) return null
        val decoder = DesktopAudioDecoder()
        return try {
            val format = DesktopPcmFormat(
                sampleRate = ANALYSIS_SAMPLE_RATE,
                channels = 1,
                bytesPerSample = 4,
                isFloat = true,
            )
            val opened = decoder.open(
                url = stream.url,
                headers = stream.headers,
                requested = format,
                windowed = stream.windowedReads,
                transport = stream.transport,
                preserveSourcePcm = false,
            )
            if (opened.isFailure) return null
            val actualRate = decoder.outputFormat.sampleRate.coerceAtLeast(8_000)
            val maxSamples = (actualRate * ANALYSIS_WINDOW_SECONDS).toInt()
            val out = FloatArray(maxSamples)
            var filled = 0
            while (filled < maxSamples) {
                val block = decoder.readSamples() ?: break
                val n = min(decoder.sampleCount, maxSamples - filled)
                if (n <= 0) continue
                System.arraycopy(block, 0, out, filled, n)
                filled += n
            }
            if (filled == 0) null else if (filled == maxSamples) out else out.copyOf(filled)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { decoder.close() }
        }
    }

    /** Computes RMS energy envelope over sliding windows. */
    internal fun computeEnergyEnvelope(
        samples: FloatArray,
        sampleRate: Double,
        hopMs: Double = HOP_MS,
        winMs: Double = WIN_MS,
    ): FloatArray {
        val hopSamples = max(1, (sampleRate * (hopMs / 1000.0)).toInt())
        val winSamples = max(hopSamples, (sampleRate * (winMs / 1000.0)).toInt())
        val numFrames = max(0, (samples.size - winSamples) / hopSamples)
        if (numFrames <= 0) return FloatArray(0)

        val env = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val start = i * hopSamples
            val end = min(samples.size, start + winSamples)
            var sumSq = 0.0
            for (j in start until end) {
                val s = samples[j]
                sumSq += s * s
            }
            env[i] = sqrt(sumSq / (end - start)).toFloat()
        }
        return env
    }

    data class Correlation(val lagFrames: Int, val confidence: Double, val floor: Int, val ceiling: Int)

    /**
     * Normalized FFT cross-correlation between [sourceEnv] and [targetEnv].
     * Positive lag means events in target occur later than source (target = source + lag).
     */
    internal fun crossCorrelate(
        sourceEnv: FloatArray,
        targetEnv: FloatArray,
        hopMs: Double = HOP_MS,
        maxLagSec: Double = MAX_LAG_SECONDS,
    ): Correlation {
        if (sourceEnv.isEmpty() || targetEnv.isEmpty()) return Correlation(0, 0.0, 0, 0)

        val maxLagFrames = (maxLagSec * 1000.0 / hopMs).toInt()
        val minOverlapFrames = (3.0 * 1000.0 / hopMs).toInt()

        var bestLag = 0
        var bestCorrelation = -1.0

        val minLag = max(-maxLagFrames, -(sourceEnv.size - minOverlapFrames))
        val maxLag = min(maxLagFrames, targetEnv.size - minOverlapFrames)
        if (minLag > maxLag) return Correlation(0, 0.0, minLag, maxLag)

        val crossTerms = crossTermsByLag(sourceEnv, targetEnv)

        val srcPrefix = DoubleArray(sourceEnv.size + 1)
        val srcSqPrefix = DoubleArray(sourceEnv.size + 1)
        for (i in sourceEnv.indices) {
            val v = sourceEnv[i].toDouble()
            srcPrefix[i + 1] = srcPrefix[i] + v
            srcSqPrefix[i + 1] = srcSqPrefix[i] + v * v
        }
        val tgtPrefix = DoubleArray(targetEnv.size + 1)
        val tgtSqPrefix = DoubleArray(targetEnv.size + 1)
        for (i in targetEnv.indices) {
            val v = targetEnv[i].toDouble()
            tgtPrefix[i + 1] = tgtPrefix[i] + v
            tgtSqPrefix[i + 1] = tgtSqPrefix[i] + v * v
        }

        for (lag in minLag..maxLag) {
            val srcStart = max(0, -lag)
            val tgtStart = max(0, lag)
            val overlapLen = min(sourceEnv.size - srcStart, targetEnv.size - tgtStart)
            if (overlapLen < minOverlapFrames) continue

            val sumSrc = srcPrefix[srcStart + overlapLen] - srcPrefix[srcStart]
            val sumTgt = tgtPrefix[tgtStart + overlapLen] - tgtPrefix[tgtStart]
            val sumSrcSq = srcSqPrefix[srcStart + overlapLen] - srcSqPrefix[srcStart]
            val sumTgtSq = tgtSqPrefix[tgtStart + overlapLen] - tgtSqPrefix[tgtStart]

            val num = crossTerms[lag + sourceEnv.size - 1] - sumSrc * sumTgt / overlapLen
            val denSrc = sumSrcSq - sumSrc * sumSrc / overlapLen
            val denTgt = sumTgtSq - sumTgt * sumTgt / overlapLen

            val denom = sqrt(denSrc * denTgt)
            if (denom > 1e-6) {
                val r = num / denom
                if (r > bestCorrelation) {
                    bestCorrelation = r
                    bestLag = lag
                }
            }
        }

        return Correlation(bestLag, bestCorrelation, minLag, maxLag)
    }

    private fun crossTermsByLag(source: FloatArray, target: FloatArray): DoubleArray {
        val n = source.size
        val m = target.size
        var fftLen = 1
        while (fftLen < n + m) fftLen = fftLen shl 1

        val srcRe = DoubleArray(fftLen)
        val srcIm = DoubleArray(fftLen)
        for (i in 0 until n) srcRe[i] = source[i].toDouble()
        val tgtRe = DoubleArray(fftLen)
        val tgtIm = DoubleArray(fftLen)
        for (i in 0 until m) tgtRe[i] = target[i].toDouble()

        fft(srcRe, srcIm)
        fft(tgtRe, tgtIm)

        for (i in 0 until fftLen) {
            val a = srcRe[i]
            val b = -srcIm[i]
            val c = tgtRe[i]
            val d = tgtIm[i]
            srcRe[i] = a * c - b * d
            srcIm[i] = a * d + b * c
        }
        fft(srcRe, srcIm, inverse = true)

        val result = DoubleArray(n + m - 1)
        for (lag in -(n - 1) until m) {
            val bin = ((lag % fftLen) + fftLen) % fftLen
            result[lag + n - 1] = srcRe[bin]
        }
        return result
    }

    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= n) {
            val angle = 2.0 * PI / len * (if (inverse) 1.0 else -1.0)
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            for (start in 0 until n step len) {
                var twiddleRe = 1.0
                var twiddleIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[start + k]
                    val evenIm = im[start + k]
                    val oddRe = re[start + k + len / 2] * twiddleRe - im[start + k + len / 2] * twiddleIm
                    val oddIm = re[start + k + len / 2] * twiddleIm + im[start + k + len / 2] * twiddleRe
                    re[start + k] = evenRe + oddRe
                    im[start + k] = evenIm + oddIm
                    re[start + k + len / 2] = evenRe - oddRe
                    im[start + k + len / 2] = evenIm - oddIm
                    val nextRe = twiddleRe * stepRe - twiddleIm * stepIm
                    twiddleIm = twiddleRe * stepIm + twiddleIm * stepRe
                    twiddleRe = nextRe
                }
            }
            len = len shl 1
        }

        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
