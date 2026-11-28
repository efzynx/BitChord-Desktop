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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
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
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_ALL
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_DEFAULT
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_NONREF
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_get_hw_config
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
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT709
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_UNSPECIFIED
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_D3D11VA
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_DXVA2
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_VAAPI
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_VDPAU
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_VIDEOTOOLBOX
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.av_buffer_ref
import org.bytedeco.ffmpeg.global.avutil.av_buffer_unref
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_copy_props
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_unref
import org.bytedeco.ffmpeg.global.avutil.av_free
import org.bytedeco.ffmpeg.global.avutil.av_hwdevice_ctx_create
import org.bytedeco.ffmpeg.global.avutil.av_hwframe_transfer_data
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
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rendered desktop video frame payload. YUV planar and NV12 frames are converted directly on the
 * GPU via Skia [RuntimeEffect] shaders, avoiding CPU color conversion and full BGRA pixel copies.
 */
internal sealed interface DesktopVideoFrame {
    val width: Int
    val height: Int

    class Rgba(val image: ImageBitmap) : DesktopVideoFrame {
        override val width: Int get() = image.width
        override val height: Int get() = image.height
    }

    class YuvPlanar(
        override val width: Int,
        override val height: Int,
        val yImage: Image,
        val uImage: Image,
        val vImage: Image,
        val fullRange: Boolean,
        val bt709: Boolean,
    ) : DesktopVideoFrame
}

/**
 * Compiles and executes SkSL YUV->RGB shaders on Skia's GPU canvas so video decoding stays free
 * of `sws_scale` BGRA CPU overhead on supported streams. Both YUV420P and NV12 feed planar
 * `GRAY_8` images because Skia's `makeRaster` rejects `R8G8_UNORM` across desktop backends.
 */
internal object DesktopVideoYuvShaders {

    private val yuvPlanarEffect: RuntimeEffect? by lazy {
        runCatching {
            RuntimeEffect.makeForShader(
                """
                uniform shader yTex;
                uniform shader uTex;
                uniform shader vTex;
                uniform float fullRange;
                uniform float bt709;
                half4 main(float2 coord) {
                    float y = yTex.eval(coord).r;
                    float2 uvCoord = coord * 0.5;
                    float u = uTex.eval(uvCoord).r;
                    float v = vTex.eval(uvCoord).r;
                    if (fullRange < 0.5) {
                        y = (y - 16.0 / 255.0) * (255.0 / 219.0);
                        u = (u - 128.0 / 255.0) * (255.0 / 224.0);
                        v = (v - 128.0 / 255.0) * (255.0 / 224.0);
                    } else {
                        u = u - 128.0 / 255.0;
                        v = v - 128.0 / 255.0;
                    }
                    float r;
                    float g;
                    float b;
                    if (bt709 > 0.5) {
                        r = y + 1.5748 * v;
                        g = y - 0.187324 * u - 0.468124 * v;
                        b = y + 1.8556 * u;
                    } else {
                        r = y + 1.402 * v;
                        g = y - 0.344136 * u - 0.714136 * v;
                        b = y + 1.772 * u;
                    }
                    return half4(clamp(r, 0.0, 1.0), clamp(g, 0.0, 1.0), clamp(b, 0.0, 1.0), 1.0);
                }
                """.trimIndent(),
            )
        }.getOrNull()
    }

    val supportsYuvPlanar: Boolean get() = yuvPlanarEffect != null

    fun drawYuvPlanar(
        canvas: org.jetbrains.skia.Canvas,
        frame: DesktopVideoFrame.YuvPlanar,
        left: Float,
        top: Float,
        drawW: Float,
        drawH: Float,
        alpha: Float,
    ): Boolean {
        val effect = yuvPlanarEffect ?: return false
        val yShader = frame.yImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
        val uShader = frame.uImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
        val vShader = frame.vImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
        val builder = RuntimeShaderBuilder(effect)
        val composite = try {
            builder.child("yTex", yShader)
            builder.child("uTex", uShader)
            builder.child("vTex", vShader)
            builder.uniform("fullRange", if (frame.fullRange) 1f else 0f)
            builder.uniform("bt709", if (frame.bt709) 1f else 0f)
            builder.makeShader()
        } finally {
            builder.close()
            yShader.close()
            uShader.close()
            vShader.close()
        }
        val paint = Paint().apply {
            shader = composite
            setAlphaf(alpha)
        }
        try {
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(drawW / frame.width.toFloat(), drawH / frame.height.toFloat())
            canvas.drawRect(SkRect.makeXYWH(0f, 0f, frame.width.toFloat(), frame.height.toFloat()), paint)
            canvas.restore()
            return true
        } finally {
            paint.close()
            composite.close()
        }
    }
}

/**
 * FFmpeg music-video decoder backed by bounded HTTP 206 range windows ([DesktopRangeStream])
 * with GPU hardware decoding (D3D11VA/DXVA2/VA-API/VideoToolbox) + CPU software fallback, and
 * direct YUV420P/NV12 plane extraction for Skia GPU shader rendering.
 */
internal class DesktopMusicVideoDecoder : AutoCloseable {

    internal data class DecodedFrame(
        val decoded: Boolean,
        val ptsMs: Long,
        val frame: DesktopVideoFrame? = null,
        val hwError: Boolean = false,
    )

    private var format: AVFormatContext? = null
    private var codec: AVCodecContext? = null
    private var packet: AVPacket? = null
    private var frame: AVFrame? = null
    private var swFrame: AVFrame? = null
    private var hwDeviceCtx: AVBufferRef? = null
    private var hwPixFmt: Int = AV_PIX_FMT_NONE
    private var hwGetFormat: AVCodecContext.Get_format_AVCodecContext_IntPointer? = null

    private var scaler: SwsContext? = null
    private var bgraBuffer: BytePointer? = null
    private var bgraView: java.nio.ByteBuffer? = null
    private var bgraPlanes: PointerPointer<BytePointer>? = null
    private var bgraStrides: IntPointer? = null
    private var bgraPixels: ByteArray = ByteArray(0)
    private var bgraInfo: ImageInfo? = null
    private var scalerFormat = -1

    private var yScratch: ByteArray = ByteArray(0)
    private var uScratch: ByteArray = ByteArray(0)
    private var vScratch: ByteArray = ByteArray(0)
    private var uvScratch: ByteArray = ByteArray(0)

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
    val isHardwareAccelerated: Boolean
        get() = hwDeviceCtx != null

    fun open(
        url: String,
        headers: Map<String, String> = emptyMap(),
        initialMaxEdge: Int = WINDOWED_RENDER_EDGE,
        enableHardwareAccel: Boolean = true,
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

        val openedCodec = openCodecContext(decoder, parameters, enableHardwareAccel)
        codec = openedCodec

        sourceWidth = openedCodec.width().takeIf { it > 0 } ?: parameters.width()
        sourceHeight = openedCodec.height().takeIf { it > 0 } ?: parameters.height()
        check(sourceWidth > 0 && sourceHeight > 0) { "video stream has invalid size" }

        val (maxW, maxH) = scaledDimensions(sourceWidth, sourceHeight, FULLSCREEN_RENDER_EDGE)
        val bufBytes = av_image_get_buffer_size(AV_PIX_FMT_BGRA, maxW, maxH, 1)
        bgraBuffer = BytePointer(bufBytes.toLong())
        bgraView = bgraBuffer?.asByteBuffer()
        bgraPlanes = PointerPointer<BytePointer>(4)
        bgraStrides = IntPointer(4L)
        setTargetMaxEdge(initialMaxEdge)
        packet = av_packet_alloc()
        frame = av_frame_alloc()
    }.onFailure { close() }

    private fun openCodecContext(
        decoder: AVCodec,
        parameters: org.bytedeco.ffmpeg.avcodec.AVCodecParameters,
        enableHardwareAccel: Boolean,
    ): AVCodecContext {
        if (enableHardwareAccel) {
            val hwCandidate = findHardwareConfig(decoder)
            if (hwCandidate != null) {
                val (deviceType, targetHwPixFmt) = hwCandidate
                val ctx = avcodec_alloc_context3(decoder)
                val hwRef = AVBufferRef()
                if (avcodec_parameters_to_context(ctx, parameters) >= 0 &&
                    av_hwdevice_ctx_create(hwRef, deviceType, null as String?, null as AVDictionary?, 0) == 0 &&
                    !hwRef.isNull
                ) {
                    val getFmt = object : AVCodecContext.Get_format_AVCodecContext_IntPointer() {
                        override fun call(codecCtx: AVCodecContext?, pixFmts: IntPointer?): Int {
                            if (pixFmts == null) return AV_PIX_FMT_NONE
                            var idx = 0L
                            var fallback = AV_PIX_FMT_NONE
                            while (true) {
                                val fmt = pixFmts.get(idx++)
                                if (fmt == AV_PIX_FMT_NONE) break
                                if (fmt == targetHwPixFmt) return fmt
                                if (fallback == AV_PIX_FMT_NONE) fallback = fmt
                            }
                            return fallback
                        }
                    }
                    ctx.hw_device_ctx(av_buffer_ref(hwRef))
                    ctx.get_format(getFmt)
                    if (avcodec_open2(ctx, decoder, null as AVDictionary?) >= 0) {
                        hwDeviceCtx = hwRef
                        hwPixFmt = targetHwPixFmt
                        hwGetFormat = getFmt
                        DesktopTrackLog.log("video: opened hwaccel type=$deviceType")
                        return ctx
                    }
                    getFmt.deallocate()
                }
                if (!hwRef.isNull) av_buffer_unref(hwRef)
                avcodec_free_context(ctx)
            }
        }

        val cpuCtx = avcodec_alloc_context3(decoder)
        check(avcodec_parameters_to_context(cpuCtx, parameters) >= 0) { "bad video codec parameters" }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, MAX_DECODE_THREADS)
        cpuCtx.thread_count(threads)
        cpuCtx.thread_type(AVCodecContext.FF_THREAD_FRAME or AVCodecContext.FF_THREAD_SLICE)
        check(avcodec_open2(cpuCtx, decoder, null as AVDictionary?) >= 0) { "video decoder refused to open" }
        return cpuCtx
    }

    private fun findHardwareConfig(decoder: AVCodec): Pair<Int, Int>? {
        val supported = HashMap<Int, Int>()
        var idx = 0
        while (true) {
            val cfg = avcodec_get_hw_config(decoder, idx++) ?: break
            if ((cfg.methods() and AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX) != 0) {
                supported.putIfAbsent(cfg.device_type(), cfg.pix_fmt())
            }
        }
        for (type in candidateHwDeviceTypes()) {
            val fmt = supported[type]
            if (fmt != null) return type to fmt
        }
        return null
    }

    /**
     * Adjusts fallback BGRA scaling dimensions for windowed vs fullscreen without reopening
     * the stream. Shader-rendered YUV/NV12 frames already scale directly on the GPU.
     */
    fun setTargetMaxEdge(maxEdge: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val clamped = maxEdge.coerceIn(WINDOWED_RENDER_EDGE, FULLSCREEN_RENDER_EDGE)
        val (newW, newH) = scaledDimensions(sourceWidth, sourceHeight, clamped)
        if (newW == width && newH == height) return
        val targetBuffer = bgraBuffer ?: return
        val targetPlanes = bgraPlanes ?: return
        val targetStrides = bgraStrides ?: return
        check(av_image_fill_arrays(targetPlanes, targetStrides, targetBuffer, AV_PIX_FMT_BGRA, newW, newH, 1) >= 0) {
            "could not allocate video frame buffer"
        }
        width = newW
        height = newH
        bgraPixels = ByteArray(newW * newH * 4)
        bgraInfo = ImageInfo.makeN32(newW, newH, ColorAlphaType.OPAQUE)
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
        skipPresentBeforePtsMs: Long = -1L,
        minPresentIntervalMs: Long = 0L,
        lastPresentedPtsMs: Long = Long.MIN_VALUE,
        dropNonRef: Boolean = false,
    ): DecodedFrame {
        val container = format ?: return DecodedFrame(false, -1L)
        val context = codec ?: return DecodedFrame(false, -1L)
        val pkt = packet ?: return DecodedFrame(false, -1L)
        val decoded = frame ?: return DecodedFrame(false, -1L)

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
                if ((skipPresentBeforePtsMs >= 0L && ptsMs < skipPresentBeforePtsMs) || cadenceSkip) {
                    av_frame_unref(decoded)
                    return DecodedFrame(decoded = true, ptsMs = ptsMs, frame = null)
                }
                val activeFrame = if (hwDeviceCtx != null && decoded.format() == hwPixFmt) {
                    val sw = swFrame ?: av_frame_alloc().also { swFrame = it }
                    av_frame_unref(sw)
                    if (av_hwframe_transfer_data(sw, decoded, 0) < 0) {
                        av_frame_unref(decoded)
                        return DecodedFrame(decoded = false, ptsMs = -1L, frame = null, hwError = true)
                    }
                    av_frame_copy_props(sw, decoded)
                    sw
                } else {
                    decoded
                }
                val rendered = extractVideoFrame(activeFrame)
                av_frame_unref(decoded)
                if (activeFrame === swFrame) av_frame_unref(activeFrame)
                return DecodedFrame(decoded = rendered != null, ptsMs = ptsMs, frame = rendered)
            }
            if (av_read_frame(container, pkt) < 0) {
                return DecodedFrame(decoded = false, ptsMs = -1L)
            }
            if (pkt.stream_index() == streamIndex) {
                val sendRes = avcodec_send_packet(context, pkt)
                if (sendRes < 0 && sendRes != -11 && hwDeviceCtx != null) {
                    DesktopTrackLog.log("video: hwaccel send_packet failed ($sendRes), falling back to CPU")
                    av_packet_unref(pkt)
                    return DecodedFrame(decoded = false, ptsMs = -1L, frame = null, hwError = true)
                }
            }
            av_packet_unref(pkt)
        }
    }

    private fun extractVideoFrame(src: AVFrame): DesktopVideoFrame? {
        val pixFmt = src.format()
        val w = src.width().takeIf { it > 0 } ?: sourceWidth
        val h = src.height().takeIf { it > 0 } ?: sourceHeight
        val fullRange = src.color_range() == AVCOL_RANGE_JPEG
        val cs = src.colorspace()
        val bt709 = cs == AVCOL_SPC_BT709 || (cs == AVCOL_SPC_UNSPECIFIED && h >= 720)

        if (pixFmt == AV_PIX_FMT_NV12 && DesktopVideoYuvShaders.supportsYuvPlanar) {
            val yImg = copySingleChannelImage(src, plane = 0, w = w, h = h, isY = true)
            val uvW = (w + 1) / 2
            val uvH = (h + 1) / 2
            val chroma = extractNv12Chroma(src, plane = 1, uvW = uvW, uvH = uvH)
            if (yImg != null && chroma != null) {
                return DesktopVideoFrame.YuvPlanar(
                    width = w,
                    height = h,
                    yImage = yImg,
                    uImage = chroma.first,
                    vImage = chroma.second,
                    fullRange = fullRange,
                    bt709 = bt709,
                )
            }
            yImg?.close()
            chroma?.first?.close()
            chroma?.second?.close()
        }

        if (pixFmt == AV_PIX_FMT_YUV420P && DesktopVideoYuvShaders.supportsYuvPlanar) {
            val uvW = (w + 1) / 2
            val uvH = (h + 1) / 2
            val yImg = copySingleChannelImage(src, plane = 0, w = w, h = h, isY = true)
            val uImg = copySingleChannelImage(src, plane = 1, w = uvW, h = uvH, isY = false, isU = true)
            val vImg = copySingleChannelImage(src, plane = 2, w = uvW, h = uvH, isY = false, isU = false)
            if (yImg != null && uImg != null && vImg != null) {
                return DesktopVideoFrame.YuvPlanar(
                    width = w,
                    height = h,
                    yImage = yImg,
                    uImage = uImg,
                    vImage = vImg,
                    fullRange = fullRange,
                    bt709 = bt709,
                )
            }
            yImg?.close()
            uImg?.close()
            vImg?.close()
        }

        val converter = scalerFor(pixFmt) ?: return null
        val info = bgraInfo ?: return null
        sws_scale(converter, src.data(), src.linesize(), 0, sourceHeight, bgraPlanes, bgraStrides)
        bgraView?.let { pixels ->
            pixels.rewind()
            pixels.get(bgraPixels, 0, minOf(bgraPixels.size, width * height * 4))
        }
        return runCatching {
            DesktopVideoFrame.Rgba(Image.makeRaster(info, bgraPixels, width * 4).toComposeImageBitmap())
        }.getOrNull()
    }

    private fun copySingleChannelImage(
        src: AVFrame,
        plane: Int,
        w: Int,
        h: Int,
        isY: Boolean,
        isU: Boolean = false,
    ): Image? {
        val ptr = src.data(plane) ?: return null
        if (ptr.isNull) return null
        val linesize = src.linesize(plane)
        if (linesize < w || w <= 0 || h <= 0) return null
        val size = w * h
        val scratch = when {
            isY -> {
                if (yScratch.size != size) yScratch = ByteArray(size)
                yScratch
            }
            isU -> {
                if (uScratch.size != size) uScratch = ByteArray(size)
                uScratch
            }
            else -> {
                if (vScratch.size != size) vScratch = ByteArray(size)
                vScratch
            }
        }
        copyPlaneRows(ptr, linesize, rowBytes = w, rows = h, dst = scratch)
        return makeGray8Image(scratch, w, h)
    }

    private fun extractNv12Chroma(src: AVFrame, plane: Int, uvW: Int, uvH: Int): Pair<Image, Image>? {
        val ptr = src.data(plane) ?: return null
        if (ptr.isNull) return null
        val rowBytes = uvW * 2
        val linesize = src.linesize(plane)
        if (linesize < rowBytes || uvW <= 0 || uvH <= 0) return null
        val rawSize = rowBytes * uvH
        if (uvScratch.size != rawSize) uvScratch = ByteArray(rawSize)
        copyPlaneRows(ptr, linesize, rowBytes = rowBytes, rows = uvH, dst = uvScratch)
        val planeSize = uvW * uvH
        if (uScratch.size != planeSize) uScratch = ByteArray(planeSize)
        if (vScratch.size != planeSize) vScratch = ByteArray(planeSize)
        deinterleaveNv12Plane(uvScratch, uScratch, vScratch, planeSize)
        val uImg = makeGray8Image(uScratch, uvW, uvH) ?: return null
        val vImg = makeGray8Image(vScratch, uvW, uvH)
        if (vImg == null) {
            uImg.close()
            return null
        }
        return uImg to vImg
    }

    private fun makeGray8Image(bytes: ByteArray, w: Int, h: Int): Image? = runCatching {
        Image.makeRaster(ImageInfo(w, h, ColorType.GRAY_8, ColorAlphaType.OPAQUE), bytes, w)
    }.getOrNull()

    private fun copyPlaneRows(ptr: BytePointer, linesize: Int, rowBytes: Int, rows: Int, dst: ByteArray) {
        val buf = BytePointer(ptr).capacity(linesize.toLong() * rows).asByteBuffer()
        if (linesize == rowBytes) {
            buf.position(0)
            buf.get(dst, 0, rowBytes * rows)
            return
        }
        for (row in 0 until rows) {
            buf.position(row * linesize)
            buf.get(dst, row * rowBytes, rowBytes)
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
        swFrame?.let { av_frame_free(it) }
        frame?.let { av_frame_free(it) }
        packet?.let { av_packet_free(it) }
        scaler?.let { sws_freeContext(it) }
        codec?.let { avcodec_free_context(it) }
        hwDeviceCtx?.let { ref ->
            if (!ref.isNull) av_buffer_unref(ref)
        }
        hwGetFormat?.deallocate()
        format?.let { avformat_close_input(it) }
        bgraBuffer?.deallocate()
        bgraPlanes?.deallocate()
        bgraStrides?.deallocate()
        swFrame = null
        frame = null
        packet = null
        scaler = null
        codec = null
        hwDeviceCtx = null
        hwPixFmt = AV_PIX_FMT_NONE
        hwGetFormat = null
        format = null
        bgraBuffer = null
        bgraView = null
        bgraPlanes = null
        bgraStrides = null
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

        private fun candidateHwDeviceTypes(): List<Int> {
            val os = System.getProperty("os.name").lowercase()
            return when {
                "win" in os -> listOf(AV_HWDEVICE_TYPE_D3D11VA, AV_HWDEVICE_TYPE_DXVA2)
                "mac" in os || "darwin" in os -> listOf(AV_HWDEVICE_TYPE_VIDEOTOOLBOX)
                else -> listOf(AV_HWDEVICE_TYPE_VAAPI, AV_HWDEVICE_TYPE_VDPAU)
            }
        }

        /** Scales source dimensions to even pixel sizes bounded by [maxEdge]. */
        internal fun scaledDimensions(sourceWidth: Int, sourceHeight: Int, maxEdge: Int): Pair<Int, Int> {
            val scale = minOf(1.0, maxEdge.coerceAtLeast(2).toDouble() / maxOf(sourceWidth, sourceHeight))
            val w = (((sourceWidth * scale).toInt() / 2) * 2).coerceAtLeast(2)
            val h = (((sourceHeight * scale).toInt() / 2) * 2).coerceAtLeast(2)
            return w to h
        }

        /** Splits packed interleaved NV12 `[U0, V0, U1, V1, ...]` chroma pairs into separate U/V planes. */
        internal fun deinterleaveNv12Plane(
            nv12Interleaved: ByteArray,
            uOut: ByteArray,
            vOut: ByteArray,
            pixelCount: Int,
        ) {
            for (i in 0 until pixelCount) {
                val pairOffset = i shl 1
                uOut[i] = nv12Interleaved[pairOffset]
                vOut[i] = nv12Interleaved[pairOffset + 1]
            }
        }
    }
}

/**
 * Coordinates desktop music-video stream resolution, decoding, adaptive quality selection, and
 * real-time A/V synchronization against [DesktopPlaybackEngine].
 */
internal object DesktopMusicVideoEngine {

    private const val MIN_VIDEO_ASPECT = 1.2f
    private const val EXPLICIT_MIN_ASPECT = 0.4f
    private const val WINDOWED_AUTO_HEIGHT = 720
    private const val FULLSCREEN_AUTO_HEIGHT = 1080
    private const val MIN_AUTO_STEP_DOWN_HEIGHT = 480
    private const val KEY_QUALITY = "music_video_quality"
    private const val STREAM_TTL_MS = 60 * 60 * 1000L
    private const val SEEK_DRIFT_MS = 550L
    private const val LAG_DROP_SCALE_MS = 45L
    private const val LAG_DROP_NONREF_MS = 120L
    private const val SUSTAINED_LAG_DROP_COUNT = 18
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

    private val _videoFrame = MutableStateFlow<DesktopVideoFrame?>(null)
    val videoFrame: StateFlow<DesktopVideoFrame?> = _videoFrame.asStateFlow()

    private val surfaceCount = MutableStateFlow(0)
    private val reloadNonce = MutableStateFlow(0)
    private val autoCapByVideo = ConcurrentHashMap<String, Int>()
    private val stepDownCap = ConcurrentHashMap<String, Int>()

    private val streams = Collections.synchronizedMap(LinkedHashMap<String, Triple<InnerTubeXResolver.VideoStream, Int, Long>>())
    private val noPicture = Collections.synchronizedSet(HashSet<String>())
    private val ageGated = Collections.synchronizedSet(HashSet<String>())
    private val refused = ConcurrentHashMap<String, MutableSet<String>>()

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
                DesktopPlayerHost.videoFullscreen,
                reloadNonce,
            ) { st, surfaces, audioOnly, fullscreen, nonce ->
                val song = st.song
                val targetId = if (surfaces > 0 && song != null && song.isVideo && song.videoId !in audioOnly && song.videoId !in noPicture) {
                    song.videoId
                } else {
                    null
                }
                val autoCap = if (targetId != null && quality.value.held == null) {
                    // Only upgrade Auto from 720p -> 1080p on entering fullscreen; keep current stream
                    // on exit to avoid rebuffer flashes while leaving playback smooth.
                    val existing = autoCapByVideo[targetId]
                    val target = autoHeightFor(fullscreen)
                    val resolved = if (existing == null) target else maxOf(existing, target)
                    autoCapByVideo[targetId] = resolved
                    resolved
                } else {
                    0
                }
                listOf(targetId, quality.value.held, autoCap, nonce)
            }.distinctUntilChanged().collectLatest { key ->
                val videoId = key[0] as String?
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
            stepDownCap.remove(active)
            autoCapByVideo.remove(active)
        }
        reloadNonce.update { it + 1 }
    }

    fun forgetVideoBlockers(videoId: String) {
        noPicture.remove(videoId)
        streams.remove(videoId)
        stepDownCap.remove(videoId)
        autoCapByVideo.remove(videoId)
        reloadNonce.update { it + 1 }
    }

    private fun clearSurfaceState() {
        _shownVideoId.value = null
        _videoFrame.value = null
        _quality.update { it.copy(heights = emptyList(), activeHeight = null) }
    }

    private fun capFor(videoId: String): Int {
        val base = _quality.value.held ?: autoCapByVideo[videoId] ?: autoHeightFor(DesktopPlayerHost.videoFullscreen.value)
        val stepped = stepDownCap[videoId]
        return if (stepped != null) minOf(base, stepped) else base
    }

    internal fun autoHeightFor(fullscreen: Boolean): Int =
        if (fullscreen) FULLSCREEN_AUTO_HEIGHT else WINDOWED_AUTO_HEIGHT

    internal fun nextLowerQualityHeight(currentHeight: Int, availableHeights: List<Int>): Int? =
        availableHeights.firstOrNull { it in MIN_AUTO_STEP_DOWN_HEIGHT until currentHeight }

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
        var decoder = DesktopMusicVideoDecoder()
        val opened = decoder.open(
            url = stream.url,
            headers = stream.headers,
            initialMaxEdge = targetEdge,
            enableHardwareAccel = true,
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
            val nextLower = nextLowerQualityHeight(stream.height.takeIf { it > 0 } ?: cap, stream.heights)
            if (nextLower != null) {
                stepDownCap[videoId] = nextLower
                streams.remove(videoId)
                reloadNonce.update { it + 1 }
                return
            }
            noPicture.add(videoId)
            return
        }

        try {
            if (decoder.width > 0 && decoder.height > 0) {
                _aspect.value = decoder.width.toFloat() / decoder.height.toFloat()
            }

            val startPosMs = liveAudioPositionMs(engine.state.value)
            if (startPosMs > 300L) {
                decoder.seek(startPosMs * 1_000L)
            }
            var lastSeeks = engine.state.value.seeks
            var lastPausedDrawnMs = -1L
            var lastPresentedPtsMs = Long.MIN_VALUE
            var deliveredAnyFrame = false
            var cpuFallbackUsed = !decoder.isHardwareAccelerated
            var consecutiveLagDrops = 0

            while (currentCoroutineContext().isActive) {
                val st = engine.state.value
                if (st.song?.videoId != videoId || surfaceCount.value <= 0 || videoId in DesktopVersionSwitcher.audioOnlyVideos.value) {
                    break
                }
                val desiredEdge = renderMaxEdgeFor(DesktopPlayerHost.videoFullscreen.value)
                if (desiredEdge != targetEdge) {
                    targetEdge = desiredEdge
                    decoder.setTargetMaxEdge(targetEdge)
                    lastPresentedPtsMs = Long.MIN_VALUE
                    lastPausedDrawnMs = -1L
                }

                val livePosMs = liveAudioPositionMs(st)
                if (st.seeks != lastSeeks) {
                    lastSeeks = st.seeks
                    decoder.seek(livePosMs * 1_000L)
                    lastPausedDrawnMs = -1L
                    lastPresentedPtsMs = Long.MIN_VALUE
                    consecutiveLagDrops = 0
                }

                if (!st.isPlaying || st.awaitingAudio) {
                    if (_videoFrame.value == null || abs(lastPausedDrawnMs - livePosMs) > 120L) {
                        if (lastPausedDrawnMs >= 0L && abs(lastPausedDrawnMs - livePosMs) > SEEK_DRIFT_MS) {
                            decoder.seek(livePosMs * 1_000L)
                        }
                        val res = decoder.nextFrame(
                            skipPresentBeforePtsMs = (livePosMs - 60L).coerceAtLeast(0L),
                        )
                        if (res.hwError && !deliveredAnyFrame && !cpuFallbackUsed) {
                            cpuFallbackUsed = true
                            decoder.close()
                            decoder = DesktopMusicVideoDecoder()
                            val cpuOpened = decoder.open(
                                url = stream.url,
                                headers = stream.headers,
                                initialMaxEdge = targetEdge,
                                enableHardwareAccel = false,
                                onHttpRefusal = { code -> if (code in REFUSAL_CODES) refusedHttp = true },
                            )
                            if (cpuOpened.isFailure) break
                            if (livePosMs > 300L) decoder.seek(livePosMs * 1_000L)
                            continue
                        }
                        if (res.decoded && res.frame != null) {
                            deliveredAnyFrame = true
                            _videoFrame.value = res.frame
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
                val droppingNonRef = lagMs > LAG_DROP_NONREF_MS
                if (droppingNonRef) {
                    consecutiveLagDrops++
                    if (consecutiveLagDrops >= SUSTAINED_LAG_DROP_COUNT && _quality.value.held == null) {
                        val lower = nextLowerQualityHeight(stream.height.takeIf { it > 0 } ?: cap, stream.heights)
                        if (lower != null) {
                            DesktopTrackLog.log("video: auto step-down $videoId ${stream.height}p -> ${lower}p after sustained lag")
                            stepDownCap[videoId] = lower
                            streams.remove(videoId)
                            reloadNonce.update { it + 1 }
                            break
                        }
                    }
                } else if (lagMs <= 60L) {
                    consecutiveLagDrops = 0
                }

                val res = decoder.nextFrame(
                    skipPresentBeforePtsMs = livePosMs - LAG_DROP_SCALE_MS,
                    minPresentIntervalMs = minInterval,
                    lastPresentedPtsMs = lastPresentedPtsMs,
                    dropNonRef = droppingNonRef,
                )
                if (res.hwError && !deliveredAnyFrame && !cpuFallbackUsed) {
                    cpuFallbackUsed = true
                    decoder.close()
                    decoder = DesktopMusicVideoDecoder()
                    val cpuOpened = decoder.open(
                        url = stream.url,
                        headers = stream.headers,
                        initialMaxEdge = targetEdge,
                        enableHardwareAccel = false,
                        onHttpRefusal = { code -> if (code in REFUSAL_CODES) refusedHttp = true },
                    )
                    if (cpuOpened.isFailure) break
                    if (livePosMs > 300L) decoder.seek(livePosMs * 1_000L)
                    continue
                }
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
                    consecutiveLagDrops = 0
                    continue
                }
                val renderedFrame = res.frame
                if (renderedFrame != null) {
                    deliveredAnyFrame = true
                    if (drift > 8L) {
                        delay(drift.coerceAtMost(AHEAD_WAIT_CAP_MS))
                    }
                    _videoFrame.value = renderedFrame
                    _shownVideoId.value = videoId
                    lastPresentedPtsMs = res.ptsMs
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            DesktopTrackLog.log("video: loop failed for $videoId (${e.message})")
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
    val frame by DesktopMusicVideoEngine.videoFrame.collectAsState()
    val aspect by DesktopMusicVideoEngine.aspect.collectAsState()
    val rendered = shownId == videoId && frame != null

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
            val active = frame ?: return@Canvas
            val videoAspect = (active.width.toFloat() / active.height.coerceAtLeast(1)).takeIf { it > 0f } ?: aspect
            val boundsAspect = size.width / size.height.coerceAtLeast(1f)
            val wider = boundsAspect > videoAspect
            val (drawW, drawH) = if (wider == fit) {
                size.height * videoAspect to size.height
            } else {
                size.width to size.width / videoAspect
            }
            val left = ((size.width - drawW) / 2f).roundToInt()
            val top = ((size.height - drawH) / 2f).roundToInt()
            val dstW = drawW.roundToInt().coerceAtLeast(1)
            val dstH = drawH.roundToInt().coerceAtLeast(1)
            when (active) {
                is DesktopVideoFrame.Rgba -> drawImage(
                    image = active.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(active.width, active.height),
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(dstW, dstH),
                )
                is DesktopVideoFrame.YuvPlanar -> drawIntoCanvas { canvas ->
                    DesktopVideoYuvShaders.drawYuvPlanar(
                        canvas = canvas.skiaCanvas,
                        frame = active,
                        left = left.toFloat(),
                        top = top.toFloat(),
                        drawW = dstW.toFloat(),
                        drawH = dstH.toFloat(),
                        alpha = 1f,
                    )
                }
            }
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
