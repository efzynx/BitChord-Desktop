package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.metrolist.innertubex.extraction.StreamResolveException
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.MusicVideoQualityUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVIOContext
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int
import org.bytedeco.ffmpeg.avformat.Seek_Pointer_long_int
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_ALL
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_DEFAULT
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_NONREF
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_SIZE
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avformat.avio_alloc_context
import org.bytedeco.ffmpeg.global.avformat.avio_context_free
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_free
import org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.av_malloc
import org.bytedeco.ffmpeg.global.swscale.SWS_FAST_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * FFmpeg music-video decoder backed by bounded HTTP 206 range windows ([DesktopRangeStream])
 * so `googlevideo.com` video-only streams open reliably, seek accurately, and stay aligned
 * with the audio playhead.
 */
internal class DesktopMusicVideoDecoder : AutoCloseable {

    internal data class DecodedFrame(
        val decoded: Boolean,
        val ptsMs: Long,
        val scaled: Boolean,
    )

    private var format: AVFormatContext? = null
    private var codec: AVCodecContext? = null
    private var packet: AVPacket? = null
    private var frame: AVFrame? = null
    private var scaler: SwsContext? = null
    private var buffer: BytePointer? = null
    private var view: java.nio.ByteBuffer? = null
    private var planes: PointerPointer<BytePointer>? = null
    private var strides: IntPointer? = null
    private var scalerFormat = -1
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var streamIndex = -1
    private var streamTimeBase = 1.0
    private var streamStartUs = 0L

    private var rangeSource: DesktopRangeStream? = null
    private var avio: AVIOContext? = null
    private var readPacket: Read_packet_Pointer_BytePointer_int? = null
    private var seekPacket: Seek_Pointer_long_int? = null

    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var isHighFrameRate: Boolean = false
        private set

    fun open(
        url: String,
        headers: Map<String, String> = emptyMap(),
        initialMaxEdge: Int = WINDOWED_RENDER_EDGE,
        onHttpRefusal: (Int) -> Unit = {},
    ): Result<Unit> = runCatching {
        av_log_set_level(AV_LOG_ERROR)
        DesktopAudioDecoder.ensureNetworkReady()

        val opened = windowedContext(url, headers, onHttpRefusal)
        check(avformat_open_input(opened, null as String?, null, null as AVDictionary?) >= 0) {
            "could not open video stream"
        }
        format = opened
        check(avformat_find_stream_info(opened, null as AVDictionary?) >= 0) { "no video stream info" }

        streamIndex = av_find_best_stream(opened, AVMEDIA_TYPE_VIDEO, -1, -1, null as AVCodec?, 0)
        check(streamIndex >= 0) { "no video stream" }
        for (i in 0 until opened.nb_streams()) {
            if (i != streamIndex) opened.streams(i).discard(AVDISCARD_ALL)
        }

        val stream = opened.streams(streamIndex)
        val tb = stream.time_base()
        streamTimeBase = tb.num().toDouble() / tb.den().coerceAtLeast(1).toDouble()
        streamStartUs = stream.start_time()
            .takeIf { it != AV_NOPTS_VALUE }
            ?.let { (it * streamTimeBase * 1_000_000.0).toLong() }
            ?: 0L
        val rate = stream.avg_frame_rate()
        val fps = if (rate.num() > 0 && rate.den() > 0) rate.num().toDouble() / rate.den().toDouble() else 30.0
        isHighFrameRate = fps > HIGH_FPS_THRESHOLD

        val parameters = stream.codecpar()
        val decoder = avcodec_find_decoder(parameters.codec_id()) ?: error("no decoder for video codec")
        val context = avcodec_alloc_context3(decoder)
        check(avcodec_parameters_to_context(context, parameters) >= 0) { "bad video codec parameters" }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, MAX_DECODE_THREADS)
        context.thread_count(threads)
        context.thread_type(AVCodecContext.FF_THREAD_FRAME or AVCodecContext.FF_THREAD_SLICE)
        check(avcodec_open2(context, decoder, null as AVDictionary?) >= 0) { "video decoder refused to open" }
        codec = context

        sourceWidth = context.width()
        sourceHeight = context.height()
        check(sourceWidth > 0 && sourceHeight > 0) { "video stream has invalid size" }

        val (maxW, maxH) = scaledDimensions(sourceWidth, sourceHeight, FULLSCREEN_RENDER_EDGE)
        val bufBytes = av_image_get_buffer_size(AV_PIX_FMT_BGRA, maxW, maxH, 1)
        buffer = BytePointer(bufBytes.toLong())
        view = buffer?.asByteBuffer()
        planes = PointerPointer<BytePointer>(4)
        strides = IntPointer(4L)
        setTargetMaxEdge(initialMaxEdge)
        packet = av_packet_alloc()
        frame = av_frame_alloc()
    }.onFailure { close() }

    /**
     * Adjusts output dimensions for windowed vs fullscreen presentation without reopening the
     * stream or reallocating native FFmpeg frame backing memory.
     */
    fun setTargetMaxEdge(maxEdge: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val clamped = maxEdge.coerceIn(WINDOWED_RENDER_EDGE, FULLSCREEN_RENDER_EDGE)
        val (newW, newH) = scaledDimensions(sourceWidth, sourceHeight, clamped)
        if (newW == width && newH == height) return
        val targetBuffer = buffer ?: return
        val targetPlanes = planes ?: return
        val targetStrides = strides ?: return
        check(av_image_fill_arrays(targetPlanes, targetStrides, targetBuffer, AV_PIX_FMT_BGRA, newW, newH, 1) >= 0) {
            "could not allocate video frame buffer"
        }
        width = newW
        height = newH
        scaler?.let { sws_freeContext(it) }
        scaler = null
        scalerFormat = -1
    }

    fun seek(micros: Long): Boolean {
        val container = format ?: return false
        val context = codec ?: return false
        val clampedUs = micros.coerceAtLeast(0L)
        val target = ((clampedUs + streamStartUs) / 1_000_000.0 / streamTimeBase).toLong()
        if (av_seek_frame(container, streamIndex, target, AVSEEK_FLAG_BACKWARD) < 0) return false
        avcodec_flush_buffers(context)
        context.skip_frame(AVDISCARD_DEFAULT)
        return true
    }

    fun nextFrame(
        into: ByteArray,
        skipScaleBeforePtsMs: Long = -1L,
        minPresentIntervalMs: Long = 0L,
        lastPresentedPtsMs: Long = Long.MIN_VALUE,
        dropNonRef: Boolean = false,
    ): DecodedFrame {
        val container = format ?: return DecodedFrame(false, -1L, false)
        val context = codec ?: return DecodedFrame(false, -1L, false)
        val pkt = packet ?: return DecodedFrame(false, -1L, false)
        val decoded = frame ?: return DecodedFrame(false, -1L, false)

        val desiredDiscard = if (dropNonRef) AVDISCARD_NONREF else AVDISCARD_DEFAULT
        if (context.skip_frame() != desiredDiscard) {
            context.skip_frame(desiredDiscard)
        }

        while (true) {
            val rx = avcodec_receive_frame(context, decoded)
            if (rx == 0) {
                val rawPts = decoded.best_effort_timestamp().takeIf { it != AV_NOPTS_VALUE }
                    ?: decoded.pkt_dts().takeIf { it != AV_NOPTS_VALUE }
                    ?: 0L
                val ptsMs = (((rawPts * streamTimeBase * 1_000_000.0).toLong() - streamStartUs) / 1_000L).coerceAtLeast(0L)
                val cadenceSkip = minPresentIntervalMs > 0L &&
                    !DesktopMusicVideoEngine.shouldScaleForCadence(
                        highFrameRate = isHighFrameRate,
                        ptsMs = ptsMs,
                        lastPresentedPtsMs = lastPresentedPtsMs,
                        minIntervalMs = minPresentIntervalMs,
                    )
                if ((skipScaleBeforePtsMs >= 0L && ptsMs < skipScaleBeforePtsMs) || cadenceSkip) {
                    return DecodedFrame(decoded = true, ptsMs = ptsMs, scaled = false)
                }
                val converter = scalerFor(decoded.format()) ?: return DecodedFrame(false, ptsMs, false)
                sws_scale(converter, decoded.data(), decoded.linesize(), 0, sourceHeight, planes, strides)
                view?.let { pixels ->
                    pixels.rewind()
                    pixels.get(into, 0, minOf(into.size, width * height * 4))
                }
                return DecodedFrame(decoded = true, ptsMs = ptsMs, scaled = true)
            }
            if (av_read_frame(container, pkt) < 0) {
                return DecodedFrame(decoded = false, ptsMs = -1L, scaled = false)
            }
            if (pkt.stream_index() == streamIndex) {
                avcodec_send_packet(context, pkt)
            }
            av_packet_unref(pkt)
        }
    }

    private fun windowedContext(
        url: String,
        headers: Map<String, String>,
        onHttpRefusal: (Int) -> Unit,
    ): AVFormatContext {
        val source = DesktopRangeStream(
            url = url,
            headers = headers,
            windowBytes = VIDEO_WINDOW_BYTES,
            onHttpRefusal = onHttpRefusal,
            prefetchNextWindow = true,
        )
        val scratch = ByteArray(DesktopAudioDecoder.IO_BUFFER_BYTES)
        val read = object : Read_packet_Pointer_BytePointer_int() {
            override fun call(opaque: Pointer?, buffer: BytePointer?, size: Int): Int {
                if (buffer == null) return AVERROR_EOF
                val taken = source.read(scratch, minOf(size, scratch.size))
                if (taken <= 0) return AVERROR_EOF
                buffer.put(scratch, 0, taken)
                return taken
            }
        }
        val seek = object : Seek_Pointer_long_int() {
            override fun call(opaque: Pointer?, offset: Long, whence: Int): Long {
                if (whence == AVSEEK_SIZE) return source.length
                val target = when (whence) {
                    DesktopAudioDecoder.SEEK_CUR -> source.position() + offset
                    DesktopAudioDecoder.SEEK_END -> source.length + offset
                    else -> offset
                }
                source.seek(target)
                return target
            }
        }
        val ioBuf = BytePointer(av_malloc(DesktopAudioDecoder.IO_BUFFER_BYTES.toLong()))
        val io = avio_alloc_context(ioBuf, DesktopAudioDecoder.IO_BUFFER_BYTES, 0, null, read, null, seek)
        val context = avformat_alloc_context()
        context.pb(io)
        rangeSource = source
        readPacket = read
        seekPacket = seek
        avio = io
        return context
    }

    private fun scalerFor(pixelFormat: Int): SwsContext? {
        scaler?.let { if (pixelFormat == scalerFormat) return it }
        if (pixelFormat < 0) return null
        scaler?.let { sws_freeContext(it) }
        val built = sws_getContext(
            sourceWidth, sourceHeight, pixelFormat,
            width, height, AV_PIX_FMT_BGRA,
            SWS_FAST_BILINEAR, null, null, null as DoublePointer?,
        )
        if (built == null) {
            scaler = null
            return null
        }
        scaler = built
        scalerFormat = pixelFormat
        return built
    }

    override fun close() {
        rangeSource?.close()
        frame?.let { av_frame_free(it) }
        packet?.let { av_packet_free(it) }
        scaler?.let { sws_freeContext(it) }
        codec?.let { avcodec_free_context(it) }
        format?.let { avformat_close_input(it) }
        buffer?.deallocate()
        planes?.deallocate()
        strides?.deallocate()
        frame = null
        packet = null
        scaler = null
        codec = null
        format = null
        buffer = null
        view = null
        planes = null
        strides = null
        avio?.let {
            av_free(it.buffer())
            it.buffer(null as BytePointer?)
            avio_context_free(it)
        }
        avio = null
        readPacket?.deallocate()
        seekPacket?.deallocate()
        readPacket = null
        seekPacket = null
        rangeSource = null
    }

    internal companion object {
        const val WINDOWED_RENDER_EDGE = 768
        const val FULLSCREEN_RENDER_EDGE = 1280
        const val VIDEO_WINDOW_BYTES = 2 * 1024 * 1024
        private const val MAX_DECODE_THREADS = 8
        private const val HIGH_FPS_THRESHOLD = 35.0

        /** Scales source dimensions to even pixel sizes bounded by [maxEdge]. */
        internal fun scaledDimensions(sourceWidth: Int, sourceHeight: Int, maxEdge: Int): Pair<Int, Int> {
            val scale = minOf(1.0, maxEdge.coerceAtLeast(2).toDouble() / maxOf(sourceWidth, sourceHeight))
            val w = (((sourceWidth * scale).toInt() / 2) * 2).coerceAtLeast(2)
            val h = (((sourceHeight * scale).toInt() / 2) * 2).coerceAtLeast(2)
            return w to h
        }
    }
}

/**
 * Coordinates desktop music-video stream resolution, decoding, quality selection, and
 * real-time A/V synchronization against [DesktopPlaybackEngine].
 */
internal object DesktopMusicVideoEngine {

    private const val MIN_VIDEO_ASPECT = 1.2f
    private const val EXPLICIT_MIN_ASPECT = 0.4f
    private const val AUTO_HEIGHT = 1080
    private const val KEY_QUALITY = "music_video_quality"
    private const val STREAM_TTL_MS = 60 * 60 * 1000L
    private const val SEEK_DRIFT_MS = 550L
    private const val LAG_DROP_SCALE_MS = 45L
    private const val LAG_DROP_NONREF_MS = 120L
    private const val HIGH_FPS_PRESENT_INTERVAL_MS = 28L
    private const val AHEAD_WAIT_CAP_MS = 220L
    private const val MAX_REFUSED_CLIENTS = 3
    private val REFUSAL_CODES = setOf(403, 404, 410)
    private val ESCALATED_REASONS = setOf(
        StreamResolveException.Reason.AGE_RESTRICTED,
        StreamResolveException.Reason.NO_PLAYABLE_STREAM,
    )

    private val persistence by lazy { DesktopPersistence() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _aspect = MutableStateFlow(16f / 9f)
    val aspect: StateFlow<Float> = _aspect.asStateFlow()

    private val _quality = MutableStateFlow(
        MusicVideoQualityUi(held = persistence.int(KEY_QUALITY, 0).takeIf { it > 0 }),
    )
    val quality: StateFlow<MusicVideoQualityUi> = _quality.asStateFlow()

    private val _shownVideoId = MutableStateFlow<String?>(null)
    val shownVideoId: StateFlow<String?> = _shownVideoId.asStateFlow()

    private val _frameBitmap = MutableStateFlow<ImageBitmap?>(null)
    val frameBitmap: StateFlow<ImageBitmap?> = _frameBitmap.asStateFlow()

    private val surfaceCount = MutableStateFlow(0)
    private val reloadNonce = MutableStateFlow(0)

    private val streams = Collections.synchronizedMap(LinkedHashMap<String, Triple<InnerTubeXResolver.VideoStream, Int, Long>>())
    private val noPicture = Collections.synchronizedSet(HashSet<String>())
    private val ageGated = Collections.synchronizedSet(HashSet<String>())
    private val refused = ConcurrentHashMap<String, MutableSet<String>>()
    private val stepDown = Collections.synchronizedSet(HashSet<String>())

    @Volatile
    private var attachedEngine: DesktopPlaybackEngine? = null
    private var engineJob: Job? = null

    fun attach(engine: DesktopPlaybackEngine) {
        if (attachedEngine === engine) return
        attachedEngine = engine
        engineJob?.cancel()
        engineJob = scope.launch {
            combine(
                engine.state,
                surfaceCount,
                DesktopVersionSwitcher.audioOnlyVideos,
                reloadNonce,
            ) { st, surfaces, audioOnly, nonce ->
                val song = st.song
                val targetId = if (surfaces > 0 && song != null && song.isVideo && song.videoId !in audioOnly && song.videoId !in noPicture) {
                    song.videoId
                } else {
                    null
                }
                Triple(targetId, quality.value.held, nonce)
            }.distinctUntilChanged().collectLatest { (videoId, _, _) ->
                if (videoId == null) {
                    clearSurfaceState()
                    return@collectLatest
                }
                runVideoLoop(engine, videoId)
            }
        }
    }

    fun claimSurface(): () -> Unit {
        surfaceCount.update { it + 1 }
        return {
            surfaceCount.update { (it - 1).coerceAtLeast(0) }
        }
    }

    fun setQuality(height: Int?) {
        val clean = height?.takeIf { it > 0 }
        if (clean == null) persistence.preferences.remove(KEY_QUALITY) else persistence.saveInt(KEY_QUALITY, clean)
        _quality.update { it.copy(held = clean) }
        val active = attachedEngine?.state?.value?.song?.videoId
        if (active != null) {
            streams.remove(active)
            stepDown.remove(active)
        }
        reloadNonce.update { it + 1 }
    }

    fun forgetVideoBlockers(videoId: String) {
        noPicture.remove(videoId)
        streams.remove(videoId)
        reloadNonce.update { it + 1 }
    }

    private fun clearSurfaceState() {
        _shownVideoId.value = null
        _frameBitmap.value = null
        _quality.update { it.copy(heights = emptyList(), activeHeight = null) }
    }

    private fun capFor(videoId: String): Int {
        val held = _quality.value.held ?: return AUTO_HEIGHT
        return if (videoId in stepDown) minOf(held, AUTO_HEIGHT) else held
    }

    internal fun renderMaxEdgeFor(fullscreen: Boolean): Int =
        if (fullscreen) DesktopMusicVideoDecoder.FULLSCREEN_RENDER_EDGE else DesktopMusicVideoDecoder.WINDOWED_RENDER_EDGE

    /** Decides whether high-fps streams (>35fps) should scale and upload the current frame. */
    internal fun shouldScaleForCadence(
        highFrameRate: Boolean,
        ptsMs: Long,
        lastPresentedPtsMs: Long,
        minIntervalMs: Long = HIGH_FPS_PRESENT_INTERVAL_MS,
    ): Boolean {
        if (!highFrameRate || lastPresentedPtsMs == Long.MIN_VALUE) return true
        if (ptsMs < lastPresentedPtsMs) return true
        return (ptsMs - lastPresentedPtsMs) >= minIntervalMs
    }

    internal fun liveAudioPositionMs(state: DesktopPlaybackState): Long {
        if (!state.isPlaying || state.awaitingAudio || state.positionSampledAtNanos <= 0L) {
            return state.positionMs.coerceAtLeast(0L)
        }
        val elapsedMs = ((System.nanoTime() - state.positionSampledAtNanos) / 1_000_000L).coerceIn(0L, 1_000L)
        return (state.positionMs + elapsedMs).coerceAtLeast(0L)
    }

    private suspend fun runVideoLoop(engine: DesktopPlaybackEngine, videoId: String) {
        clearSurfaceState()
        val cap = capFor(videoId)
        val stream = resolveWithRetries(videoId, cap) ?: return
        val minAspect = if (videoId in DesktopVersionSwitcher.explicitlySwitchedVideos) EXPLICIT_MIN_ASPECT else MIN_VIDEO_ASPECT
        if (stream.aspect > 0f && stream.aspect < minAspect) {
            noPicture.add(videoId)
            return
        }

        val initialAspect = stream.aspect.takeIf { it > 0f } ?: (16f / 9f)
        _aspect.value = initialAspect
        _quality.update { it.copy(heights = stream.heights, activeHeight = stream.height.takeIf { h -> h > 0 }) }

        var refusedHttp = false
        var targetEdge = renderMaxEdgeFor(DesktopPlayerHost.videoFullscreen.value)
        val decoder = DesktopMusicVideoDecoder()
        val opened = decoder.open(
            url = stream.url,
            headers = stream.headers,
            initialMaxEdge = targetEdge,
            onHttpRefusal = { code ->
                if (code in REFUSAL_CODES) refusedHttp = true
            },
        )
        if (opened.isFailure) {
            decoder.close()
            if (refusedHttp) {
                streams.remove(videoId)
                stream.profileId.takeIf(String::isNotBlank)?.let { profile ->
                    val set = refused.getOrPut(videoId) { Collections.synchronizedSet(HashSet()) }
                    if (set.add(profile) && set.size <= MAX_REFUSED_CLIENTS) {
                        InnerTubeXResolver.onVideoRefused()
                        reloadNonce.update { it + 1 }
                        return
                    }
                }
            }
            if (cap > AUTO_HEIGHT && stepDown.add(videoId)) {
                streams.remove(videoId)
                reloadNonce.update { it + 1 }
                return
            }
            noPicture.add(videoId)
            return
        }

        try {
            var w = decoder.width
            var h = decoder.height
            if (w > 0 && h > 0) {
                _aspect.value = w.toFloat() / h.toFloat()
            }
            var pixels = ByteArray(w * h * 4)
            var info = ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE)

            val startPosMs = liveAudioPositionMs(engine.state.value)
            if (startPosMs > 300L) {
                decoder.seek(startPosMs * 1_000L)
            }
            var lastSeeks = engine.state.value.seeks
            var lastPausedDrawnMs = -1L
            var lastPresentedPtsMs = Long.MIN_VALUE

            while (currentCoroutineContext().isActive) {
                val st = engine.state.value
                if (st.song?.videoId != videoId || surfaceCount.value <= 0 || videoId in DesktopVersionSwitcher.audioOnlyVideos.value) {
                    break
                }
                val desiredEdge = renderMaxEdgeFor(DesktopPlayerHost.videoFullscreen.value)
                if (desiredEdge != targetEdge) {
                    targetEdge = desiredEdge
                    decoder.setTargetMaxEdge(targetEdge)
                    val nw = decoder.width
                    val nh = decoder.height
                    if (nw > 0 && nh > 0 && (nw != w || nh != h)) {
                        w = nw
                        h = nh
                        pixels = ByteArray(w * h * 4)
                        info = ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE)
                    }
                    lastPresentedPtsMs = Long.MIN_VALUE
                    lastPausedDrawnMs = -1L
                }

                val livePosMs = liveAudioPositionMs(st)
                if (st.seeks != lastSeeks) {
                    lastSeeks = st.seeks
                    decoder.seek(livePosMs * 1_000L)
                    lastPausedDrawnMs = -1L
                    lastPresentedPtsMs = Long.MIN_VALUE
                }

                if (!st.isPlaying || st.awaitingAudio) {
                    if (_frameBitmap.value == null || abs(lastPausedDrawnMs - livePosMs) > 120L) {
                        if (lastPausedDrawnMs >= 0L && abs(lastPausedDrawnMs - livePosMs) > SEEK_DRIFT_MS) {
                            decoder.seek(livePosMs * 1_000L)
                        }
                        val res = decoder.nextFrame(
                            into = pixels,
                            skipScaleBeforePtsMs = (livePosMs - 60L).coerceAtLeast(0L),
                        )
                        if (res.decoded && res.scaled) {
                            _frameBitmap.value = Image.makeRaster(info, pixels, w * 4).toComposeImageBitmap()
                            _shownVideoId.value = videoId
                            lastPausedDrawnMs = livePosMs
                            lastPresentedPtsMs = res.ptsMs
                        } else if (!res.decoded) {
                            delay(80L)
                        }
                    } else {
                        delay(60L)
                    }
                    continue
                }

                val minInterval = if (decoder.isHighFrameRate) HIGH_FPS_PRESENT_INTERVAL_MS else 0L
                val lagMs = if (lastPresentedPtsMs != Long.MIN_VALUE) livePosMs - lastPresentedPtsMs else 0L
                val res = decoder.nextFrame(
                    into = pixels,
                    skipScaleBeforePtsMs = livePosMs - LAG_DROP_SCALE_MS,
                    minPresentIntervalMs = minInterval,
                    lastPresentedPtsMs = lastPresentedPtsMs,
                    dropNonRef = lagMs > LAG_DROP_NONREF_MS,
                )
                if (!res.decoded) {
                    val profile = stream.profileId
                    if (refusedHttp && profile.isNotBlank()) {
                        streams.remove(videoId)
                        val set = refused.getOrPut(videoId) { Collections.synchronizedSet(HashSet()) }
                        if (set.add(profile) && set.size <= MAX_REFUSED_CLIENTS) {
                            InnerTubeXResolver.onVideoRefused()
                            reloadNonce.update { it + 1 }
                            break
                        }
                    }
                    delay(120L)
                    continue
                }
                val currentPosMs = liveAudioPositionMs(engine.state.value)
                val drift = res.ptsMs - currentPosMs
                if (abs(drift) > SEEK_DRIFT_MS) {
                    decoder.seek(currentPosMs * 1_000L)
                    lastPresentedPtsMs = Long.MIN_VALUE
                    continue
                }
                if (res.scaled) {
                    if (drift > 8L) {
                        delay(drift.coerceAtMost(AHEAD_WAIT_CAP_MS))
                    }
                    _frameBitmap.value = Image.makeRaster(info, pixels, w * 4).toComposeImageBitmap()
                    _shownVideoId.value = videoId
                    lastPresentedPtsMs = res.ptsMs
                }
            }
        } finally {
            decoder.close()
        }
    }

    private suspend fun resolveWithRetries(videoId: String, cap: Int): InnerTubeXResolver.VideoStream? {
        cachedStream(videoId, cap)?.let { return it }
        val skip = refused[videoId]?.toSet().orEmpty()
        return runCatching {
            val stream = if (videoId !in ageGated) {
                val plain = try {
                    withContext(Dispatchers.IO) { InnerTubeXResolver.extractVideo(videoId, cap, skip) }
                } catch (e: StreamResolveException) {
                    if (e.reason !in ESCALATED_REASONS) throw e
                    null
                }
                plain ?: withContext(Dispatchers.IO) {
                    InnerTubeXResolver.extractVideo(videoId, cap, skip, ageRestricted = true)
                }?.also { ageGated.add(videoId) }
            } else {
                withContext(Dispatchers.IO) {
                    InnerTubeXResolver.extractVideo(videoId, cap, skip, ageRestricted = true)
                }
            }
            if (stream != null) {
                streams[videoId] = Triple(stream, cap, System.currentTimeMillis())
            } else {
                noPicture.add(videoId)
            }
            stream
        }.onFailure { e ->
            if (e is CancellationException) throw e
            if (e is StreamResolveException && e.reason != StreamResolveException.Reason.NETWORK) {
                noPicture.add(videoId)
            }
            DesktopTrackLog.log("video: resolve failed for $videoId (${e.message})")
        }.getOrNull()
    }

    private fun cachedStream(videoId: String, cap: Int): InnerTubeXResolver.VideoStream? {
        val entry = streams[videoId] ?: return null
        if (entry.second != cap || System.currentTimeMillis() - entry.third > STREAM_TTL_MS) {
            streams.remove(videoId)
            return null
        }
        return entry.first
    }
}

/**
 * Shared player video surface backed by [DesktopMusicVideoEngine].
 */
@Composable
internal fun DesktopMusicVideoSurface(
    videoId: String,
    fit: Boolean,
    onRenderedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(videoId) {
        val release = DesktopMusicVideoEngine.claimSurface()
        onDispose { release() }
    }

    val shownId by DesktopMusicVideoEngine.shownVideoId.collectAsState()
    val bitmap by DesktopMusicVideoEngine.frameBitmap.collectAsState()
    val aspect by DesktopMusicVideoEngine.aspect.collectAsState()
    val rendered = shownId == videoId && bitmap != null

    val report by rememberUpdatedState(onRenderedChanged)
    LaunchedEffect(rendered) { report(rendered) }
    DisposableEffect(Unit) { onDispose { report(false) } }

    val alpha by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "desktopVideoAlpha",
    )

    Box(modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
        ) {
            val img = bitmap ?: return@Canvas
            val videoAspect = (img.width.toFloat() / img.height.coerceAtLeast(1)).takeIf { it > 0f } ?: aspect
            val boundsAspect = size.width / size.height.coerceAtLeast(1f)
            val wider = boundsAspect > videoAspect
            val (drawW, drawH) = if (wider == fit) {
                size.height * videoAspect to size.height
            } else {
                size.width to size.width / videoAspect
            }
            val left = ((size.width - drawW) / 2f).roundToInt()
            val top = ((size.height - drawH) / 2f).roundToInt()
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
            )
        }
    }
}

/**
 * Handles switching the active playing track between audio-only (song) and video (audio-visual)
 * modes on desktop, preserving playback position and queue metadata.
 */
internal object DesktopVersionSwitcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    private val _missingVersions = MutableStateFlow<Set<String>>(emptySet())
    val missingVersions: StateFlow<Set<String>> = _missingVersions.asStateFlow()

    private val _audioOnlyVideos = MutableStateFlow<Set<String>>(emptySet())
    val audioOnlyVideos: StateFlow<Set<String>> = _audioOnlyVideos.asStateFlow()

    internal val explicitlySwitchedVideos: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private val pairedVideoToAudio = ConcurrentHashMap<String, Song>()
    private val pairedAudioToVideo = ConcurrentHashMap<String, Song>()

    private var switchJob: Job? = null

    fun showsVideoPicture(videoId: String): Boolean = videoId !in _audioOnlyVideos.value

    fun isShowingVideo(song: Song): Boolean = song.isVideo && song.videoId !in _audioOnlyVideos.value

    fun isVersionMissing(song: Song): Boolean = song.videoId in _missingVersions.value

    fun canOfferSwitch(song: Song): Boolean =
        song.localUri == null && DesktopSearchClient.isVideoId(song.videoId)

    fun toggleCurrentTrack(
        song: Song,
        engine: DesktopPlaybackEngine,
        onReplaced: (oldVideoId: String, newSong: Song) -> Unit,
    ) {
        if (!canOfferSwitch(song)) return
        if (DesktopListenTogether.state.value.inParty) {
            DesktopPlayerHost.showMessage(
                DesktopStrings["d_version_switch_in_party", "Everyone in Listen Together hears the same version"],
            )
            return
        }
        if (_switching.value) return

        // Fast toggle when staying on a video track whose picture was suppressed locally.
        if (song.isVideo && song.videoId in _audioOnlyVideos.value) {
            explicitlySwitchedVideos.add(song.videoId)
            _audioOnlyVideos.update { it - song.videoId }
            DesktopMusicVideoEngine.forgetVideoBlockers(song.videoId)
            return
        }

        switchJob?.cancel()
        switchJob = scope.launch {
            _switching.value = true
            try {
                if (song.isVideo) {
                    val audio = pairedVideoToAudio[song.videoId]
                        ?: YtMusicRepository.cachedAudioVersion(song.videoId)
                        ?: runCatching { YtMusicRepository.resolveAudio(song) }.getOrNull()
                    if (audio != null && audio.videoId != song.videoId) {
                        val target = audio.copy(
                            isVideo = false,
                            isVideoOrigin = true,
                            queueTier = song.queueTier,
                            queueEntryId = song.queueEntryId,
                            radioName = song.radioName,
                            playbackSource = song.playbackSource,
                            playbackSourceType = song.playbackSourceType,
                            playbackSourceId = song.playbackSourceId,
                        )
                        pairedAudioToVideo[target.videoId] = song
                        pairedVideoToAudio[song.videoId] = target
                        val result = engine.swapToVersion(sourceSong = song, targetSong = target)
                        if (result.isSuccess) {
                            withContext(Dispatchers.Main) { onReplaced(song.videoId, target) }
                        } else {
                            DesktopPlayerHost.showMessage(
                                result.exceptionOrNull()?.message ?: "Could not switch to audio version",
                            )
                        }
                    } else {
                        // Standalone music videos with no catalogue audio row switch smoothly to audio-only view.
                        _audioOnlyVideos.update { it + song.videoId }
                    }
                } else {
                    val video = pairedAudioToVideo[song.videoId]
                        ?: YtMusicRepository.cachedVideoVersion(song.videoId)
                        ?: runCatching { YtMusicRepository.resolveVideo(song) }.getOrNull()
                    if (video == null || video.videoId == song.videoId) {
                        _missingVersions.update { it + song.videoId }
                        DesktopPlayerHost.showMessage(
                            DesktopStrings["d_no_video_version", "No video version found for this song"],
                        )
                        return@launch
                    }
                    val target = video.copy(
                        isVideo = true,
                        isVideoOrigin = false,
                        queueTier = song.queueTier,
                        queueEntryId = song.queueEntryId,
                        radioName = song.radioName,
                        playbackSource = song.playbackSource,
                        playbackSourceType = song.playbackSourceType,
                        playbackSourceId = song.playbackSourceId,
                    )
                    pairedAudioToVideo[song.videoId] = target
                    pairedVideoToAudio[target.videoId] = song
                    explicitlySwitchedVideos.add(target.videoId)
                    _audioOnlyVideos.update { it - target.videoId }
                    DesktopMusicVideoEngine.forgetVideoBlockers(target.videoId)
                    val result = engine.swapToVersion(sourceSong = song, targetSong = target)
                    if (result.isSuccess) {
                        withContext(Dispatchers.Main) { onReplaced(song.videoId, target) }
                    } else {
                        DesktopPlayerHost.showMessage(
                            result.exceptionOrNull()?.message ?: "Could not switch to video version",
                        )
                    }
                }
            } finally {
                _switching.value = false
            }
        }
    }
}
