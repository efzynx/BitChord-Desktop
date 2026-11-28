package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.music.bitchord.data.model.Song
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** Authenticity / bandwidth verdict derived from spectral & bit-depth telemetry. */
internal enum class DesktopSpectrumVerdict {
    NATIVE_HI_RES,
    NATIVE_LOSSLESS,
    POSSIBLE_UPSAMPLE,
    POSSIBLE_LOSSY_TRANSCODE,
    LOSSY_SOURCE,
}

/** Measured container, PCM bit-depth, frequency ceiling, and dynamic metrics for a track. */
internal data class DesktopSpectrumMetrics(
    val codecName: String,
    val containerLabel: String,
    val isLosslessCodec: Boolean,
    val sampleRateHz: Int,
    val nyquistHz: Double,
    val containerBitDepth: Int?,
    val effectiveBitDepth: Int?,
    val zeroPaddedBits: Boolean,
    val channels: Int,
    val bitrateKbps: Int?,
    val durationSeconds: Double,
    val estimatedCutoffHz: Int,
    val sharpCliffHz: Int?,
    val midBandMeanDb: Float,
    val presenceBandMeanDb: Float,
    val shelfBandMeanDb: Float,
    val ultrasonicBandMeanDb: Float?,
    val ultrasonicBandP95Db: Float?,
    val ultrasonicDropDb: Float?,
    val peakDbfs: Float,
    val rmsDbfs: Float,
    val crestFactorDb: Float,
)

/** Complete spectral inspection artifact for a local track, ready to render or compare. */
internal data class DesktopSpectrumResult(
    val song: Song,
    val filePath: String,
    val fileSizeBytes: Long,
    val metrics: DesktopSpectrumMetrics,
    val verdict: DesktopSpectrumVerdict,
    val hasAcousticCaveat: Boolean,
    val spectrogram: ImageBitmap,
    val spectrogramWidth: Int,
    val spectrogramHeight: Int,
    val spectrumFreqHz: FloatArray,
    val meanSpectrumDb: FloatArray,
    val peakSpectrumDb: FloatArray,
)

/** Bit-depth activity probe over raw left-aligned signed 32-bit integer PCM samples. */
internal data class DesktopBitDepthProbe(
    val effectiveBitDepth: Int?,
    val zeroPaddedBits: Boolean,
    val nonZeroSamples: Int,
)

internal object DesktopAudioSpectrumAnalyzer {

    const val FFT_SIZE = 4096
    const val FFT_BINS = FFT_SIZE / 2
    const val SPEC_WIDTH = 280
    const val SPEC_HEIGHT = 240
    const val CURVE_POINTS = 256
    private const val SILENT_WINDOW_DB = -72f
    private const val FLOOR_DB = -120f
    private const val MAX_DIRECT_DECODE_SECONDS = 480.0
    private const val SEEK_SEGMENTS = 96

    private val cache = ConcurrentHashMap<String, DesktopSpectrumResult>()

    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }
    private val bitReversed = IntArray(FFT_SIZE).also { table ->
        val bits = Integer.numberOfTrailingZeros(FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            var x = i
            var r = 0
            for (b in 0 until bits) {
                r = (r shl 1) or (x and 1)
                x = x ushr 1
            }
            table[i] = r
        }
    }
    private val cosTable = DoubleArray(FFT_SIZE / 2) { k -> cos(-2.0 * PI * k / FFT_SIZE) }
    private val sinTable = DoubleArray(FFT_SIZE / 2) { k -> sin(-2.0 * PI * k / FFT_SIZE) }

    fun clearCache() {
        cache.clear()
    }

    fun resolveLocalPath(song: Song): Path? {
        song.localPath?.takeIf(String::isNotBlank)?.let { raw ->
            val p = runCatching { Paths.get(raw).toAbsolutePath().normalize() }.getOrNull()
            if (p != null && Files.isRegularFile(p)) return p
        }
        song.localUri?.takeIf { it.startsWith("file:", ignoreCase = true) }?.let { uri ->
            val p = runCatching { Paths.get(URI(uri)).toAbsolutePath().normalize() }.getOrNull()
            if (p != null && Files.isRegularFile(p)) return p
        }
        return null
    }

    suspend fun analyze(song: Song, forceRefresh: Boolean = false): Result<DesktopSpectrumResult> =
        withContext(Dispatchers.Default) {
            runCatching {
                val path = resolveLocalPath(song)
                    ?: error("Local audio file not found")
                val sizeBytes = runCatching { Files.size(path) }.getOrDefault(0L)
                val mtime = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
                val cacheKey = "${path}|$sizeBytes|$mtime"
                if (!forceRefresh) {
                    cache[cacheKey]?.let { return@runCatching it }
                }
                val computed = computeSpectrum(song, path, sizeBytes)
                cache[cacheKey] = computed
                computed
            }
        }

    private fun computeSpectrum(song: Song, path: Path, sizeBytes: Long): DesktopSpectrumResult {
        val decoder = DesktopAudioDecoder()
        val opened = decoder.open(
            url = path.toString(),
            requested = DesktopPcmFormat(
                sampleRate = 48_000,
                channels = 2,
                bytesPerSample = 4,
                isFloat = true,
                validBitsPerSample = 32,
            ),
            preserveSourcePcm = true,
        )
        opened.getOrThrow()

        return try {
            val outFmt = decoder.outputFormat
            val measured = decoder.measuredFormat
            val sampleRate = outFmt.sampleRate.coerceAtLeast(8_000)
            val channels = outFmt.channels.coerceIn(1, 8)
            val isIntPcm = !outFmt.isFloat
            val isLosslessCodec = measured?.isLossless == true || isIntPcm
            val durationUs = decoder.durationUs?.takeIf { it > 0L }
            val durationSec = durationUs?.let { it / 1_000_000.0 } ?: 0.0
            val probed = DesktopLocalMetadata.probeFile(path)

            val bitTracker = IntBitDepthTracker(
                enabled = isIntPcm,
                containerBits = measured?.bitDepth ?: outFmt.validBitsPerSample,
            )

            val specMatrix = FloatArray(SPEC_WIDTH * SPEC_HEIGHT) { FLOOR_DB }
            val binMeanAccum = DoubleArray(FFT_BINS)
            val activeBinsHist = IntArray(FFT_BINS)
            val perWindowBinDb = ArrayList<FloatArray>(320)

            var peakAmp = 0.0
            var sumSquares = 0.0
            var totalFrameCount = 0L

            val leftBuf = FloatArray(FFT_SIZE)
            val rightBuf = FloatArray(FFT_SIZE)
            var bufFill = 0

            val estimatedFrames = (durationSec * sampleRate).toLong().coerceAtLeast(FFT_SIZE.toLong())
            val useSeekSampling = durationUs != null && durationSec > MAX_DIRECT_DECODE_SECONDS

            fun processFrameBlock(stereoPairCount: Int) {
                if (stereoPairCount < FFT_SIZE) return
                val magDb = fftStereoPowerDb(leftBuf, rightBuf)
                val colIndex = if (useSeekSampling) {
                    // Handled by caller updating progressColumn
                    0
                } else {
                    val approxFrame = totalFrameCount - FFT_SIZE / 2
                    ((approxFrame.toDouble() / estimatedFrames.toDouble()) * SPEC_WIDTH)
                        .roundToInt()
                        .coerceIn(0, SPEC_WIDTH - 1)
                }
                accumulateWindow(
                    magDb = magDb,
                    colIndex = colIndex,
                    specMatrix = specMatrix,
                    binMeanAccum = binMeanAccum,
                    activeBinsHist = activeBinsHist,
                    perWindowBinDb = perWindowBinDb,
                )
            }

            if (durationUs != null && durationSec > MAX_DIRECT_DECODE_SECONDS) {
                for (seg in 0 until SEEK_SEGMENTS) {
                    val targetUs = (seg.toLong() * durationUs) / SEEK_SEGMENTS
                    decoder.seek(targetUs)
                    bufFill = 0
                    var segFrames = 0
                    val targetFrames = FFT_SIZE * 2
                    val col = ((seg.toFloat() / SEEK_SEGMENTS) * SPEC_WIDTH).toInt().coerceIn(0, SPEC_WIDTH - 1)
                    while (segFrames < targetFrames) {
                        val filled = readChunkIntoBuffers(
                            decoder = decoder,
                            isIntPcm = isIntPcm,
                            channels = channels,
                            bitTracker = bitTracker,
                            onFrame = { l, r ->
                                val absL = abs(l.toDouble())
                                val absR = abs(r.toDouble())
                                if (absL > peakAmp) peakAmp = absL
                                if (absR > peakAmp) peakAmp = absR
                                sumSquares += 0.5 * (l.toDouble() * l + r.toDouble() * r)
                                totalFrameCount++
                                segFrames++
                                if (bufFill < FFT_SIZE) {
                                    leftBuf[bufFill] = l
                                    rightBuf[bufFill] = r
                                    bufFill++
                                }
                                if (bufFill == FFT_SIZE) {
                                    val magDb = fftStereoPowerDb(leftBuf, rightBuf)
                                    accumulateWindow(
                                        magDb = magDb,
                                        colIndex = col,
                                        specMatrix = specMatrix,
                                        binMeanAccum = binMeanAccum,
                                        activeBinsHist = activeBinsHist,
                                        perWindowBinDb = perWindowBinDb,
                                    )
                                    bufFill = 0
                                }
                            },
                        )
                        if (!filled) break
                    }
                }
            } else {
                val strideFrames = (estimatedFrames / 420L).toInt().coerceAtLeast(FFT_SIZE)
                var skipRemaining = 0
                while (true) {
                    val got = readChunkIntoBuffers(
                        decoder = decoder,
                        isIntPcm = isIntPcm,
                        channels = channels,
                        bitTracker = bitTracker,
                        onFrame = { l, r ->
                            val absL = abs(l.toDouble())
                            val absR = abs(r.toDouble())
                            if (absL > peakAmp) peakAmp = absL
                            if (absR > peakAmp) peakAmp = absR
                            sumSquares += 0.5 * (l.toDouble() * l + r.toDouble() * r)
                            totalFrameCount++
                            if (skipRemaining > 0) {
                                skipRemaining--
                            } else {
                                leftBuf[bufFill] = l
                                rightBuf[bufFill] = r
                                bufFill++
                                if (bufFill == FFT_SIZE) {
                                    processFrameBlock(FFT_SIZE)
                                    bufFill = 0
                                    skipRemaining = (strideFrames - FFT_SIZE).coerceAtLeast(0)
                                }
                            }
                        },
                    )
                    if (!got) break
                }
                if (bufFill >= FFT_SIZE / 2) {
                    for (i in bufFill until FFT_SIZE) {
                        leftBuf[i] = 0f
                        rightBuf[i] = 0f
                    }
                    processFrameBlock(FFT_SIZE)
                }
            }

            val finalDurationSec = if (durationSec > 0.0) {
                durationSec
            } else {
                totalFrameCount.toDouble() / sampleRate.toDouble()
            }

            val windowCount = perWindowBinDb.size.coerceAtLeast(1)
            val meanDbBins = FloatArray(FFT_BINS) { b ->
                val activeCount = activeBinsHist[b]
                if (activeCount > 0) (binMeanAccum[b] / activeCount).toFloat()
                else (binMeanAccum[b] / windowCount).toFloat()
            }
            val p95DbBins = computePercentileBins(perWindowBinDb, percentile = 0.95f)
            val nyquistHz = sampleRate / 2.0
            val containerDepth = measured?.bitDepth ?: probed.bitDepth ?: outFmt.validBitsPerSample.takeIf { isLosslessCodec }
            val probeResult = bitTracker.result()

            val peakDbfs = if (peakAmp > 1e-6) (20.0 * log10(peakAmp)).toFloat() else FLOOR_DB
            val rmsAmp = if (totalFrameCount > 0L) sqrt(sumSquares / totalFrameCount.toDouble()) else 0.0
            val rmsDbfs = if (rmsAmp > 1e-6) (20.0 * log10(rmsAmp)).toFloat() else FLOOR_DB
            val crestDb = (peakDbfs - rmsDbfs).coerceAtLeast(0f)

            val analysis = analyzeSpectralProfile(
                sampleRateHz = sampleRate,
                isLosslessCodec = isLosslessCodec,
                containerBitDepth = containerDepth,
                effectiveBitDepth = probeResult.effectiveBitDepth,
                zeroPaddedBits = probeResult.zeroPaddedBits,
                meanDbBins = meanDbBins,
                p95DbBins = p95DbBins,
            )

            val curveFreqs = FloatArray(CURVE_POINTS) { idx ->
                ((idx.toDouble() / (CURVE_POINTS - 1)) * nyquistHz).toFloat()
            }
            val meanCurve = resampleBinsMax(meanDbBins, CURVE_POINTS)
            val peakCurve = resampleBinsMax(p95DbBins, CURVE_POINTS)

            val bitmap = renderSpectrogramBitmap(specMatrix, SPEC_WIDTH, SPEC_HEIGHT)
            val codecDisplay = (measured?.codec ?: probed.codec ?: path.fileName.toString().substringAfterLast('.', ""))
                .uppercase()
                .ifBlank { "AUDIO" }
            val containerDisplay = (measured?.container ?: path.fileName.toString().substringAfterLast('.', ""))
                .uppercase()
                .ifBlank { codecDisplay }

            DesktopSpectrumResult(
                song = song,
                filePath = path.toString(),
                fileSizeBytes = sizeBytes,
                metrics = DesktopSpectrumMetrics(
                    codecName = codecDisplay,
                    containerLabel = containerDisplay,
                    isLosslessCodec = isLosslessCodec,
                    sampleRateHz = sampleRate,
                    nyquistHz = nyquistHz,
                    containerBitDepth = containerDepth,
                    effectiveBitDepth = probeResult.effectiveBitDepth,
                    zeroPaddedBits = probeResult.zeroPaddedBits,
                    channels = channels,
                    bitrateKbps = measured?.kbps,
                    durationSeconds = finalDurationSec,
                    estimatedCutoffHz = analysis.estimatedCutoffHz,
                    sharpCliffHz = analysis.sharpCliffHz,
                    midBandMeanDb = analysis.midBandMeanDb,
                    presenceBandMeanDb = analysis.presenceBandMeanDb,
                    shelfBandMeanDb = analysis.shelfBandMeanDb,
                    ultrasonicBandMeanDb = analysis.ultrasonicBandMeanDb,
                    ultrasonicBandP95Db = analysis.ultrasonicBandP95Db,
                    ultrasonicDropDb = analysis.ultrasonicDropDb,
                    peakDbfs = peakDbfs,
                    rmsDbfs = rmsDbfs,
                    crestFactorDb = crestDb,
                ),
                verdict = analysis.verdict,
                hasAcousticCaveat = analysis.hasAcousticCaveat,
                spectrogram = bitmap,
                spectrogramWidth = SPEC_WIDTH,
                spectrogramHeight = SPEC_HEIGHT,
                spectrumFreqHz = curveFreqs,
                meanSpectrumDb = meanCurve,
                peakSpectrumDb = peakCurve,
            )
        } finally {
            decoder.close()
        }
    }

    private inline fun readChunkIntoBuffers(
        decoder: DesktopAudioDecoder,
        isIntPcm: Boolean,
        channels: Int,
        bitTracker: IntBitDepthTracker,
        onFrame: (left: Float, right: Float) -> Unit,
    ): Boolean {
        if (isIntPcm) {
            val ints = decoder.readIntSamples() ?: return false
            val n = decoder.sampleCount
            if (n <= 0) return false
            bitTracker.observe(ints, n)
            val scale = 1.0f / 2147483648.0f
            var i = 0
            while (i + channels <= n) {
                val l = ints[i] * scale
                val r = if (channels > 1) ints[i + 1] * scale else l
                onFrame(l, r)
                i += channels
            }
            return true
        } else {
            val floats = decoder.readSamples() ?: return false
            val n = decoder.sampleCount
            if (n <= 0) return false
            var i = 0
            while (i + channels <= n) {
                val l = floats[i]
                val r = if (channels > 1) floats[i + 1] else l
                onFrame(l, r)
                i += channels
            }
            return true
        }
    }

    private fun accumulateWindow(
        magDb: FloatArray,
        colIndex: Int,
        specMatrix: FloatArray,
        binMeanAccum: DoubleArray,
        activeBinsHist: IntArray,
        perWindowBinDb: MutableList<FloatArray>,
    ) {
        // Skip dead-silent fade-ins/gaps so silence does not drag averages or percentiles down.
        var midMax = FLOOR_DB
        val startMid = (FFT_BINS * 0.02f).toInt().coerceAtLeast(2)
        val endMid = (FFT_BINS * 0.40f).toInt()
        for (b in startMid..endMid) {
            if (magDb[b] > midMax) midMax = magDb[b]
        }
        val isActive = midMax > SILENT_WINDOW_DB

        for (y in 0 until SPEC_HEIGHT) {
            // y = 0 is top (Nyquist), y = SPEC_HEIGHT - 1 is bottom (0 Hz).
            val normBottom = (SPEC_HEIGHT - 1 - y).toDouble() / (SPEC_HEIGHT - 1)
            val normTop = (SPEC_HEIGHT - y).toDouble() / SPEC_HEIGHT
            val b0 = (normBottom * (FFT_BINS - 1)).toInt().coerceIn(0, FFT_BINS - 1)
            val b1 = (normTop * (FFT_BINS - 1)).roundToInt().coerceIn(b0, FFT_BINS - 1)
            var maxRowDb = FLOOR_DB
            for (b in b0..b1) {
                if (magDb[b] > maxRowDb) maxRowDb = magDb[b]
            }
            val cell = y * SPEC_WIDTH + colIndex
            if (maxRowDb > specMatrix[cell]) {
                specMatrix[cell] = maxRowDb
            }
        }

        if (isActive || perWindowBinDb.isEmpty()) {
            perWindowBinDb.add(magDb)
            for (b in 0 until FFT_BINS) {
                binMeanAccum[b] += magDb[b]
                activeBinsHist[b]++
            }
        }
    }

    /**
     * Computes stereo power spectrum (dBFS per FFT bin) from [FFT_SIZE] time-domain samples.
     * Encodes `z[n] = L[n] + j * R[n]` so both L and R power come out of one complex FFT without
     * phase cancellation across stereo wide mixes, while full-scale 0 dBFS sine peaks at ~0.0 dBFS.
     */
    internal fun fftStereoPowerDb(left: FloatArray, right: FloatArray = left): FloatArray {
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            val w = hannWindow[i].toDouble()
            real[i] = left[i].toDouble() * w
            imag[i] = right[i].toDouble() * w
        }
        fftInPlace(real, imag)

        // With Hann coherent gain = N/2 and conjugate-split stereo sum 0.25 * (|Z[k]|^2 + |Z[N-k]|^2):
        // normalization divisor on squared magnitude is (N / 4)^2.
        val normSq = (FFT_SIZE * 0.25) * (FFT_SIZE * 0.25)
        val out = FloatArray(FFT_BINS)
        val dcPower = (real[0] * real[0] + imag[0] * imag[0]) * 0.5 / normSq
        out[0] = if (dcPower > 1e-12) (10.0 * log10(dcPower)).toFloat().coerceIn(FLOOR_DB, 0f) else FLOOR_DB
        for (k in 1 until FFT_BINS) {
            val nk = FFT_SIZE - k
            val pA = real[k] * real[k] + imag[k] * imag[k]
            val pB = real[nk] * real[nk] + imag[nk] * imag[nk]
            val stereoPower = 0.25 * (pA + pB) / normSq
            out[k] = if (stereoPower > 1e-12) {
                (10.0 * log10(stereoPower)).toFloat().coerceIn(FLOOR_DB, 0f)
            } else {
                FLOOR_DB
            }
        }
        return out
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = FFT_SIZE
        for (i in 0 until n) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val halfLen = len ushr 1
            val step = n / len
            var start = 0
            while (start < n) {
                var twIndex = 0
                for (k in 0 until halfLen) {
                    val wr = cosTable[twIndex]
                    val wi = sinTable[twIndex]
                    val idx1 = start + k
                    val idx2 = idx1 + halfLen
                    val r2 = real[idx2]
                    val i2 = imag[idx2]
                    val tRe = wr * r2 - wi * i2
                    val tIm = wr * i2 + wi * r2
                    real[idx2] = real[idx1] - tRe
                    imag[idx2] = imag[idx1] - tIm
                    real[idx1] += tRe
                    imag[idx1] += tIm
                    twIndex += step
                }
                start += len
            }
            len = len shl 1
        }
    }

    internal data class SpectralClassification(
        val estimatedCutoffHz: Int,
        val sharpCliffHz: Int?,
        val midBandMeanDb: Float,
        val presenceBandMeanDb: Float,
        val shelfBandMeanDb: Float,
        val ultrasonicBandMeanDb: Float?,
        val ultrasonicBandP95Db: Float?,
        val ultrasonicDropDb: Float?,
        val verdict: DesktopSpectrumVerdict,
        val hasAcousticCaveat: Boolean,
    )

    internal fun analyzeSpectralProfile(
        sampleRateHz: Int,
        isLosslessCodec: Boolean,
        containerBitDepth: Int?,
        effectiveBitDepth: Int?,
        zeroPaddedBits: Boolean,
        meanDbBins: FloatArray,
        p95DbBins: FloatArray,
    ): SpectralClassification {
        val nyquistHz = sampleRateHz / 2.0
        val hzPerBin = nyquistHz / FFT_BINS

        fun binForHz(hz: Double): Int = (hz / hzPerBin).roundToInt().coerceIn(0, FFT_BINS - 1)
        fun avgRange(bins: FloatArray, loHz: Double, hiHz: Double): Float {
            if (loHz >= nyquistHz) return FLOOR_DB
            val lo = binForHz(loHz)
            val hi = binForHz(min(hiHz, nyquistHz - hzPerBin)).coerceAtLeast(lo)
            var sum = 0.0
            for (i in lo..hi) sum += bins[i]
            return (sum / (hi - lo + 1)).toFloat()
        }

        val midMean = avgRange(meanDbBins, 1_000.0, 14_000.0)
        val midP95 = avgRange(p95DbBins, 1_000.0, 12_000.0)
        val presenceMean = avgRange(meanDbBins, 16_000.0, 20_000.0)
        val presenceP95 = avgRange(p95DbBins, 16_000.0, 19_500.0)
        val shelfMean = avgRange(meanDbBins, 20_000.0, min(22_050.0, nyquistHz))

        val isHiResRate = sampleRateHz > 48_000
        val ultrasonicMean = if (isHiResRate && nyquistHz > 25_000.0) {
            avgRange(meanDbBins, 24_000.0, min(nyquistHz - 500.0, 46_000.0))
        } else null
        val ultrasonicP95 = if (isHiResRate && nyquistHz > 25_000.0) {
            avgRange(p95DbBins, 24_000.0, min(nyquistHz - 500.0, 46_000.0))
        } else null
        val ultrasonicDrop = ultrasonicP95?.let { midP95 - it }

        // Smooth p95 bins (~350 Hz neighborhood) to avoid single harmonic tones distorting shelf search.
        val halfKernel = (175.0 / hzPerBin).roundToInt().coerceIn(2, 18)
        val smoothed = FloatArray(FFT_BINS) { i ->
            val a = (i - halfKernel).coerceAtLeast(0)
            val b = (i + halfKernel).coerceAtMost(FFT_BINS - 1)
            var s = 0.0
            for (j in a..b) s += p95DbBins[j]
            (s / (b - a + 1)).toFloat()
        }

        // Sharp brickwall cliff detection (typical of lossy low-pass filters or naive upsampling).
        val stepBins = (900.0 / hzPerBin).roundToInt().coerceAtLeast(4)
        val bandSpan = (500.0 / hzPerBin).roundToInt().coerceAtLeast(3)
        val searchLo = binForHz(12_000.0)
        val searchHi = binForHz(min(nyquistHz - 1_500.0, 42_000.0))
        var maxDrop = 0f
        var cliffBin: Int? = null
        if (searchHi > searchLo + stepBins) {
            for (center in (searchLo + bandSpan)..(searchHi - stepBins - bandSpan)) {
                var belowSum = 0.0
                for (j in (center - bandSpan)..center) belowSum += smoothed[j]
                val belowAvg = (belowSum / (bandSpan + 1)).toFloat()

                var aboveSum = 0.0
                val aboveStart = center + stepBins
                val aboveEnd = (aboveStart + bandSpan).coerceAtMost(FFT_BINS - 1)
                for (j in aboveStart..aboveEnd) aboveSum += smoothed[j]
                val aboveAvg = (aboveSum / (aboveEnd - aboveStart + 1)).toFloat()

                val drop = belowAvg - aboveAvg
                if (belowAvg > -76f && drop >= 18f && drop > maxDrop) {
                    // A genuine low-pass transcode or upsample cliff keeps the entire upper band
                    // suppressed; a tonal notch between two real high harmonics is not a cutoff.
                    val tailEnd = binForHz(min(nyquistHz - 600.0, 44_000.0)).coerceAtLeast(aboveStart)
                    var tailMax = FLOOR_DB
                    for (j in aboveStart..tailEnd) {
                        if (smoothed[j] > tailMax) tailMax = smoothed[j]
                    }
                    if (tailMax <= belowAvg - 14f) {
                        maxDrop = drop
                        cliffBin = center + stepBins / 2
                    }
                }
            }
        }
        val sharpCliffHz = cliffBin?.let { (it * hzPerBin).roundToInt() }

        // Sustained activity threshold: highest frequency before smoothed peak drops below usable signal.
        val activeThreshold = max(-92f, midP95 - 50f)
        val sustainSpan = (450.0 / hzPerBin).roundToInt().coerceAtLeast(3)
        var lastActiveBin = binForHz(4_000.0)
        for (b in binForHz(4_000.0) until (FFT_BINS - sustainSpan)) {
            var localSum = 0.0
            for (j in b until (b + sustainSpan)) localSum += smoothed[j]
            val localMean = (localSum / sustainSpan).toFloat()
            if (localMean > activeThreshold) {
                lastActiveBin = b
            }
        }
        val estimatedCutoffHz = (lastActiveBin * hzPerBin).roundToInt().coerceAtLeast(4_000)

        val quietHighsNearFloor = presenceP95 < -90f && midP95 > -58f
        val (verdict, acousticCaveat) = when {
            !isLosslessCodec -> DesktopSpectrumVerdict.LOSSY_SOURCE to false

            (sharpCliffHz != null && sharpCliffHz < 19_600) ||
                (estimatedCutoffHz < 19_100 && quietHighsNearFloor) -> {
                DesktopSpectrumVerdict.POSSIBLE_LOSSY_TRANSCODE to false
            }

            zeroPaddedBits && containerBitDepth != null && containerBitDepth >= 24 -> {
                DesktopSpectrumVerdict.POSSIBLE_UPSAMPLE to false
            }

            isHiResRate -> {
                val hasGenuineUltrasonics = ultrasonicP95 != null &&
                    ultrasonicP95 > -84f &&
                    estimatedCutoffHz >= 24_500 &&
                    (sharpCliffHz == null || sharpCliffHz > 26_000)
                val cdCliffDetected = sharpCliffHz != null && sharpCliffHz in 19_500..24_500
                val deadUltrasonicBand = ultrasonicP95 != null && ultrasonicP95 < -92f && midP95 > -58f

                when {
                    hasGenuineUltrasonics && (effectiveBitDepth == null || effectiveBitDepth >= 20) ->
                        DesktopSpectrumVerdict.NATIVE_HI_RES to false
                    cdCliffDetected || deadUltrasonicBand ->
                        DesktopSpectrumVerdict.POSSIBLE_UPSAMPLE to (!cdCliffDetected)
                    hasGenuineUltrasonics ->
                        DesktopSpectrumVerdict.NATIVE_HI_RES to true
                    else ->
                        DesktopSpectrumVerdict.NATIVE_LOSSLESS to true
                }
            }

            else -> DesktopSpectrumVerdict.NATIVE_LOSSLESS to false
        }

        return SpectralClassification(
            estimatedCutoffHz = estimatedCutoffHz,
            sharpCliffHz = sharpCliffHz,
            midBandMeanDb = midMean,
            presenceBandMeanDb = presenceMean,
            shelfBandMeanDb = shelfMean,
            ultrasonicBandMeanDb = ultrasonicMean,
            ultrasonicBandP95Db = ultrasonicP95,
            ultrasonicDropDb = ultrasonicDrop,
            verdict = verdict,
            hasAcousticCaveat = acousticCaveat,
        )
    }

    /** Probes raw 32-bit left-aligned integer PCM samples to detect zero-padded containers. */
    internal fun analyzeIntPcmBitDepth(
        samples: IntArray,
        sampleCount: Int,
        containerBits: Int?,
    ): DesktopBitDepthProbe {
        val tracker = IntBitDepthTracker(enabled = true, containerBits = containerBits)
        tracker.observe(samples, sampleCount)
        return tracker.result()
    }

    private class IntBitDepthTracker(
        private val enabled: Boolean,
        private val containerBits: Int?,
    ) {
        private var nonZero = 0
        private var activeLow16 = false // any bit in [15..0] non-zero -> beyond 16-bit
        private var activeLow12 = false // any bit in [11..0] non-zero -> beyond 20-bit
        private var activeLow8 = false  // any bit in [7..0] non-zero -> full 32-bit

        fun observe(samples: IntArray, sampleCount: Int) {
            if (!enabled) return
            val limit = min(sampleCount, samples.size)
            for (i in 0 until limit) {
                val s = samples[i]
                if (s != 0) {
                    nonZero++
                    if ((s and 0x0000FFFF) != 0) {
                        activeLow16 = true
                        if ((s and 0x00000FFF) != 0) {
                            activeLow12 = true
                            if ((s and 0x000000FF) != 0) {
                                activeLow8 = true
                            }
                        }
                    }
                }
            }
        }

        fun result(): DesktopBitDepthProbe {
            if (!enabled) {
                return DesktopBitDepthProbe(effectiveBitDepth = null, zeroPaddedBits = false, nonZeroSamples = 0)
            }
            val effective = when {
                nonZero < 256 -> containerBits
                activeLow8 && (containerBits ?: 0) > 24 -> 32
                activeLow12 -> 24
                activeLow16 -> 20
                else -> 16
            }
            val zeroPadded = nonZero >= 1024 &&
                containerBits != null &&
                containerBits >= 24 &&
                effective != null &&
                effective <= 16
            return DesktopBitDepthProbe(
                effectiveBitDepth = effective,
                zeroPaddedBits = zeroPadded,
                nonZeroSamples = nonZero,
            )
        }
    }

    private fun computePercentileBins(windows: List<FloatArray>, percentile: Float): FloatArray {
        val out = FloatArray(FFT_BINS)
        if (windows.isEmpty()) return out
        val col = FloatArray(windows.size)
        val idx = ((windows.size - 1) * percentile).roundToInt().coerceIn(0, windows.size - 1)
        for (b in 0 until FFT_BINS) {
            for (w in windows.indices) col[w] = windows[w][b]
            col.sort()
            out[b] = col[idx]
        }
        return out
    }

    private fun resampleBinsMax(bins: FloatArray, points: Int): FloatArray {
        val out = FloatArray(points)
        val n = bins.size
        for (i in 0 until points) {
            val f0 = ((i.toDouble() / points) * n).toInt().coerceIn(0, n - 1)
            val f1 = (((i + 1).toDouble() / points) * n).toInt().coerceIn(f0, n - 1)
            var sum = 0f
            for (b in f0..f1) sum += bins[b]
            out[i] = sum / (f1 - f0 + 1)
        }
        return out
    }

    private fun renderSpectrogramBitmap(matrix: FloatArray, width: Int, height: Int): ImageBitmap {
        val bgra = ByteArray(width * height * 4)
        for (i in 0 until (width * height)) {
            val db = matrix[i]
            val offset = i * 4
            writeHeatBgra(db, bgra, offset)
        }
        val image = Image.makeRaster(ImageInfo.makeN32(width, height, ColorAlphaType.OPAQUE), bgra, width * 4)
        return image.toComposeImageBitmap()
    }

    /** Perceptual Magma/Spek heat gradient in BGRA byte order (-112 dBFS -> 0 dBFS). */
    private fun writeHeatBgra(db: Float, out: ByteArray, offset: Int) {
        val norm = ((db + 112f) / 108f).coerceIn(0f, 1f)
        val r: Int
        val g: Int
        val b: Int
        when {
            norm < 0.20f -> {
                val t = norm / 0.20f
                r = lerpByte(7, 36, t)
                g = lerpByte(8, 16, t)
                b = lerpByte(14, 82, t)
            }
            norm < 0.45f -> {
                val t = (norm - 0.20f) / 0.25f
                r = lerpByte(36, 126, t)
                g = lerpByte(16, 28, t)
                b = lerpByte(82, 114, t)
            }
            norm < 0.70f -> {
                val t = (norm - 0.45f) / 0.25f
                r = lerpByte(126, 212, t)
                g = lerpByte(28, 72, t)
                b = lerpByte(114, 62, t)
            }
            norm < 0.88f -> {
                val t = (norm - 0.70f) / 0.18f
                r = lerpByte(212, 248, t)
                g = lerpByte(72, 168, t)
                b = lerpByte(62, 34, t)
            }
            else -> {
                val t = (norm - 0.88f) / 0.12f
                r = lerpByte(248, 255, t)
                g = lerpByte(168, 248, t)
                b = lerpByte(34, 198, t)
            }
        }
        out[offset] = b.toByte()
        out[offset + 1] = g.toByte()
        out[offset + 2] = r.toByte()
        out[offset + 3] = 0xFF.toByte()
    }

    private fun lerpByte(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    private val BRACKET_SUFFIX_RE = Regex("""\s*[\(\[].*?[\)\]]""")
    private val TOKEN_SPLIT_RE = Regex("""[^a-z0-9]+""")

    internal fun rankCompareCandidates(
        candidates: List<Song>,
        referenceSong: Song,
        query: String,
    ): List<DesktopCompareCandidate> {
        val normalizedRefTitle = normalizeTitleForMatch(referenceSong.title)
        val refTokens = titleTokens(normalizedRefTitle)
        val refArtist = referenceSong.artist.trim().lowercase()
        val refPath = referenceSong.localPath
        val qTokens = query.trim().lowercase().split(TOKEN_SPLIT_RE).filter { it.isNotEmpty() }

        return candidates
            .asSequence()
            .filter { it.localUri != null || it.localPath != null }
            .filter { song ->
                if (qTokens.isEmpty()) return@filter true
                val fileName = song.localPath?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
                val ext = fileName.substringAfterLast('.', "")
                val haystack = listOfNotNull(
                    song.title,
                    song.artist,
                    song.albumName,
                    song.sourceQuality,
                    fileName,
                    ext,
                ).joinToString(" ").lowercase()
                qTokens.all { haystack.contains(it) }
            }
            .map { song ->
                val isSameFile = refPath != null && song.localPath == refPath
                val candNorm = normalizeTitleForMatch(song.title)
                val candTokens = titleTokens(candNorm)
                val overlapCount = refTokens.count { it in candTokens }
                val sameBaseTitle = normalizedRefTitle.isNotBlank() && candNorm == normalizedRefTitle
                val sameArtist = refArtist.isNotBlank() && song.artist.trim().lowercase() == refArtist
                val suggested = !isSameFile && (sameBaseTitle || overlapCount >= 2 || (overlapCount >= 1 && sameArtist))

                var score = 0
                if (!isSameFile) score += 20
                if (sameBaseTitle) score += 200
                score += overlapCount * 35
                if (sameArtist) score += 15
                if (song.sourceHiRes == true) score += 4
                score to DesktopCompareCandidate(song = song, isSuggestedMatch = suggested)
            }
            .sortedWith(compareByDescending<Pair<Int, DesktopCompareCandidate>> { it.first }.thenBy { it.second.song.title.lowercase() })
            .map { it.second }
            .toList()
    }

    private fun normalizeTitleForMatch(raw: String): String =
        BRACKET_SUFFIX_RE.replace(raw.lowercase(), "")
            .replace(TOKEN_SPLIT_RE, " ")
            .trim()

    private fun titleTokens(normalized: String): Set<String> =
        normalized.split(' ').filter { it.length >= 3 }.toSet()
}

internal data class DesktopCompareCandidate(
    val song: Song,
    val isSuggestedMatch: Boolean,
)
