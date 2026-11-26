package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.NerdStats
import com.music.bitchord.ui.components.liveMenuFrost
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val PIPELINE_CARD_SHAPE = RoundedCornerShape(16.dp)
private val PIPELINE_ACTION_HEIGHT = 44.dp

internal fun pipelineNodeCoreColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF141418) else Color(0xFFE7E9ED)

internal fun pipelineScrimColor(isDark: Boolean): Color =
    Color.Black.copy(alpha = if (isDark) 0.72f else 0.56f)

/**
 * Full audio playback pipeline inspection dialog matching mobile `AudioPipelineDialog` +
 * `AudioPopupCard`: opaque dark alert card (no transparent liquid glass), animated signal bus,
 * and telemetry across Track Info -> Decoder -> Resampler -> DSP -> Output Device.
 */
@Composable
internal fun DesktopAudioPipelineDialog(
    format: DesktopStreamFormat?,
    sourceName: String?,
    pipeline: DesktopAudioPipeline,
    isPlaying: Boolean = false,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
) {
    val livePipeline by DesktopPlayerHost.pipeline.collectAsState()
    val effective = remember(livePipeline, pipeline) {
        if (livePipeline.sourceFormat != null ||
            livePipeline.outputSampleRateHz != null ||
            livePipeline.bitPerfect.active
        ) {
            livePipeline
        } else {
            pipeline
        }
    }
    val bp by DesktopBitPerfect.status.collectAsState()
    val dacVolume by DesktopDacVolume.snapshot.collectAsState()
    val playerVolume by DesktopPlayerHost.volume.level.collectAsState()
    val eqPreset by DesktopEqualizerSettings.preset.collectAsState()
    val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()

    val isSignalActive = isPlaying && !reduceAnimation
    val flowAlpha by animateFloatAsState(
        targetValue = if (isSignalActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSignalActive) 450 else 320,
            easing = if (isSignalActive) LinearOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "desktopPipelineFlowAlpha",
    )
    var pulseProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isSignalActive) {
        if (!isSignalActive) {
            pulseProgress = 0f
            return@LaunchedEffect
        }
        pulseProgress = 0f
        val anim = Animatable(0f)
        while (isActive) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
            ) {
                pulseProgress = value
            }
        }
    }

    val density = LocalDensity.current
    var columnCoordinates by remember { androidx.compose.runtime.mutableStateOf<LayoutCoordinates?>(null) }
    val stageCenters = remember { mutableStateMapOf<Int, Offset>() }
    val interactionRadiusPx = with(density) { 24.dp.toPx() }
    val stageActivation: (Int) -> Float = { idx ->
        calculateStageActivation(idx, pulseProgress, flowAlpha, stageCenters, interactionRadiusPx)
    }
    val onStageIconPositioned: (Int, Offset) -> Unit = { idx, center ->
        if (stageCenters[idx] != center) stageCenters[idx] = center
    }

    val palette = LocalDesktopPalette.current
    val isDark = palette.isDark
    val ink = palette.ink
    val secondary = palette.secondary
    val ruleColor = palette.cardEdge
    val nodeCoreColor = pipelineNodeCoreColor(isDark)

    val modalTint = desktopModalSurfaceTint(isDark)
    val rimAlpha = if (isDark) 0.22f else 0.30f
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassSurface = if (hazeState != null) {
        // When hosted inside NowPlayingScreen's playerBackdrop, blur the sibling playerHaze
        // rather than sampling playerBackdrop recursively on every 60fps pulse tick.
        if (reduceDynamicBlur) {
            Modifier.background(modalTint.copy(alpha = 0.94f), PIPELINE_CARD_SHAPE)
        } else {
            Modifier
                .liveMenuFrost(
                    state = hazeState,
                    fill = if (isDark) Color(0xFF0E0E12) else Color(0xFFECEEF2),
                    alpha = if (isDark) 0.80f else 0.84f,
                )
                .background(modalTint.copy(alpha = if (isDark) 0.34f else 0.42f))
        }.desktopLiquidGlassSpecularRim(PIPELINE_CARD_SHAPE, borderAlpha = rimAlpha)
    } else {
        Modifier.desktopBarGlass(
            shape = PIPELINE_CARD_SHAPE,
            surfaceTint = modalTint,
            borderAlpha = rimAlpha,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pipelineScrimColor(isDark))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .shadow(24.dp, PIPELINE_CARD_SHAPE)
                .clip(PIPELINE_CARD_SHAPE)
                .then(glassSurface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = DesktopStrings["audio_pipeline", "Audio Pipeline"],
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = DesktopStrings["audio_pipeline_subtitle", "Live signal path, decoder to output"],
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = secondary,
                    textAlign = TextAlign.Center,
                )
            }

            PipelineRule(Modifier, ruleColor)

            Box(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {}
                        .drawBehind {
                            drawSignalFlow(
                                stageCenters = stageCenters,
                                flowAlpha = flowAlpha,
                                pulseProgress = pulseProgress,
                                interactionRadiusPx = interactionRadiusPx,
                                signalColor = ink,
                                nodeCoreColor = nodeCoreColor,
                            )
                        },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { columnCoordinates = it },
                ) {
                    val src = effective.sourceFormat ?: format
                    val codecLabel = src?.codecLabel ?: "—"
                    val formatText = src?.container?.takeIf { it.isNotBlank() && !it.equals(codecLabel, ignoreCase = true) }
                        ?.let { "$codecLabel ($it)" } ?: codecLabel
                    val bitDepthText = if (src?.isLossless == true) {
                        src.bitDepth?.let { "$it-bit" } ?: "—"
                    } else {
                        "—"
                    }
                    val sampleRateText = src?.sampleRateHz?.let(::pipelineRateText) ?: "—"
                    val bitrateText = src?.kbps?.let { "$it kbps" } ?: "—"
                    val pcmDataRateText = if (src?.isLossless == true && src.bitDepth != null && src.sampleRateHz != null && src.channels != null) {
                        "${(src.sampleRateHz.toLong() * src.bitDepth * src.channels) / 1_000L} kbps"
                    } else {
                        "—"
                    }
                    val channelsText = channelLabel(src?.channels)

                    // 1. Track Info
                    PipelineRule()
                    PipelineSection(
                        stageIndex = 0,
                        icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        title = DesktopStrings["pipeline_track_info", "Track Info"],
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(DesktopStrings["pipeline_source", "Source"], sourceName ?: "—")
                        PipelineRow(DesktopStrings["pipeline_format", "Format"], formatText)
                        PipelineRow(DesktopStrings["pipeline_bit_depth", "Bit Depth"], bitDepthText)
                        PipelineRow(DesktopStrings["pipeline_sample_rate", "Sample Rate"], sampleRateText)
                        PipelineRow(DesktopStrings["pipeline_bitrate", "Bitrate"], bitrateText)
                        PipelineRow(DesktopStrings["pipeline_pcm_data_rate", "PCM Data Rate"], pcmDataRateText)
                        PipelineRow(DesktopStrings["pipeline_channels", "Channels"], channelsText)
                    }

                    // 2. Decoder
                    val decoderName = (effective.decoderLabel ?: src?.codecLabel)?.let { "FFmpeg $it" } ?: "—"
                    val decodedEncoding = when {
                        effective.decodedIsFloat == true -> "Float32"
                        effective.decodedBytesPerSample != null -> "PCM${effective.decodedBytesPerSample * 8}"
                        else -> null
                    }
                    val decodedRateText = effective.decodedSampleRateHz?.let(::pipelineRateText)
                    val decodedFmtText = when {
                        decodedEncoding != null && decodedRateText != null -> "$decodedEncoding / $decodedRateText"
                        decodedEncoding != null -> decodedEncoding
                        decodedRateText != null -> decodedRateText
                        else -> "—"
                    }

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 1,
                        icon = Icons.Rounded.Memory,
                        title = DesktopStrings["pipeline_decoder", "Decoder"],
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(DesktopStrings["pipeline_decoder_name", "Decoder Name"], decoderName)
                        PipelineRow(DesktopStrings["pipeline_format", "Format"], decodedFmtText)
                    }

                    // 3. Resampler
                    val inRate = effective.decodedSampleRateHz ?: src?.sampleRateHz
                    val outRate = if (bp.active && bp.sampleRateHz > 0) bp.sampleRateHz else effective.outputSampleRateHz
                    val isPassthrough = inRate != null && outRate != null && inRate == outRate
                    val ioRateText = when {
                        inRate != null && outRate != null -> "${pipelineRateText(inRate)} → ${pipelineRateText(outRate)}"
                        inRate != null -> "${pipelineRateText(inRate)} → —"
                        outRate != null -> "— → ${pipelineRateText(outRate)}"
                        else -> "—"
                    }
                    val resamplerType = when {
                        inRate == null || outRate == null -> "—"
                        isPassthrough -> DesktopStrings["source_none", "None"]
                        bp.active && bp.dacLimited -> "Windowed-sinc (Kaiser)"
                        else -> "FFmpeg Resampler"
                    }
                    val cutoffText = if (bp.active && bp.dacLimited && !isPassthrough) "0.96 Nyquist (TPDF)" else "—"
                    val qualityText = when {
                        inRate == null || outRate == null -> "—"
                        isPassthrough -> "Passthrough"
                        else -> "Resampled"
                    }

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 2,
                        icon = Icons.Rounded.Tune,
                        title = DesktopStrings["pipeline_resampler", "Resampler"],
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(DesktopStrings["pipeline_io_rate", "I/O Rate"], ioRateText)
                        PipelineRow(DesktopStrings["pipeline_type", "Type"], resamplerType)
                        PipelineRow(DesktopStrings["pipeline_cutoff", "Cutoff"], cutoffText)
                        PipelineRow(DesktopStrings["pipeline_quality", "Quality"], qualityText)
                    }

                    // 4. DSP
                    val activePcmFormat = when {
                        bp.active && bp.outputBits > 0 -> "PCM${bp.outputBits}"
                        effective.outputIsFloat == true -> "Float32"
                        effective.outputBytesPerSample != null -> "PCM${effective.outputBytesPerSample * 8}"
                        else -> "—"
                    }
                    val activeRate = if (bp.active && bp.sampleRateHz > 0) bp.sampleRateHz else effective.outputSampleRateHz
                    val activeRateText = activeRate?.let(::pipelineRateText) ?: "—"
                    val volumeText = when {
                        bp.active -> dacVolume?.takeIf { it.available }?.let {
                            "${it.controlName} (${String.format(Locale.ROOT, "%.1f", it.currentDb)} dB)"
                        } ?: DesktopStrings["pipeline_bypassed", "Bypassed"]
                        else -> "${(playerVolume * 100f).roundToInt()}%"
                    }
                    val eqPresetText = when {
                        bp.active -> DesktopStrings["pipeline_bypassed", "Bypassed"]
                        effective.equalizerEnabled -> eqPreset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                        else -> "Flat"
                    }
                    val stereoExpandText = when {
                        bp.active -> DesktopStrings["pipeline_bypassed", "Bypassed"]
                        effective.spatialEnabled -> DesktopStrings["on", "On"]
                        else -> "100%"
                    }
                    val buffersText = effective.bufferBytes.takeIf { it > 0 }?.let { bytes ->
                        val bps = when {
                            bp.active && bp.outputBits == 24 -> 4
                            bp.active && bp.outputBits > 0 -> bp.outputBits / 8
                            effective.outputBytesPerSample != null -> effective.outputBytesPerSample
                            else -> 2
                        }
                        val ch = (effective.outputChannels ?: 2).coerceAtLeast(1)
                        val frameBytes = bps * ch
                        val frames = if (frameBytes > 0) bytes / frameBytes else 0
                        val rate = activeRate ?: 0
                        if (rate > 0 && frames > 0) {
                            val ms = (frames * 1000L) / rate
                            "${bytes / 1024} KB (${ms}ms, $frames frames)"
                        } else {
                            "${bytes / 1024} KB"
                        }
                    } ?: "—"
                    val apiName = bp.driver ?: if (DesktopPlatform.isWindows) "WASAPI Shared" else "Java Sound"
                    val bitExactText = when {
                        bp.active && bp.exact -> DesktopStrings.format(
                            "pipeline_bit_exact_yes",
                            bp.deviceName ?: effective.deviceName ?: "DAC",
                            fallback = "Yes — bit-perfect to %1\$s",
                        )
                        bp.active && bp.dacLimited -> DesktopStrings.format(
                            "pipeline_bit_exact_limited",
                            bp.sourceBits,
                            formatRateKhz(bp.sourceRateHz),
                            bp.deviceName ?: "DAC",
                            fallback = "No — %1\$d-bit / %2\$s converted to %3\$s",
                        )
                        bp.fallbackReason != null -> "No — ${bp.fallbackReason}"
                        effective.equalizerEnabled -> DesktopStrings["pipeline_no_eq", "No — equalizer"]
                        else -> DesktopStrings["pipeline_no_mixer", "No — OS mixer"]
                    }

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 3,
                        icon = Icons.Rounded.GraphicEq,
                        title = DesktopStrings["pipeline_dsp", "DSP"],
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(DesktopStrings["pipeline_pcm_format", "PCM Format"], activePcmFormat)
                        PipelineRow(DesktopStrings["pipeline_sample_rate", "Sample Rate"], activeRateText)
                        PipelineRow(DesktopStrings["d_volume", "Volume"], volumeText)
                        PipelineRow(DesktopStrings["pipeline_eq_preset", "EQ Preset"], eqPresetText)
                        PipelineRow(DesktopStrings["pipeline_stereo_expand", "Stereo Expand"], stereoExpandText)
                        PipelineRow(DesktopStrings["pipeline_buffers", "Buffers"], buffersText)
                        PipelineRow(DesktopStrings["pipeline_output_api", "Output API"], apiName)
                        PipelineRow(DesktopStrings["pipeline_bit_exact", "Bit-exact"], bitExactText)
                    }

                    // 5. Output Device
                    val deviceName = bp.deviceName ?: effective.deviceName ?: DesktopStrings["d_system_default", "System default"]
                    val transportText = if (bp.active) (bp.driver ?: DesktopStrings["pipeline_direct", "Direct"]) else apiName
                    val directText = if (bp.active) {
                        "Active (${bp.driver}, bypasses mixer)"
                    } else {
                        "Not Supported (Mixed Path)"
                    }
                    val outputPair = when {
                        activePcmFormat != "—" && activeRateText != "—" -> "$activePcmFormat / $activeRateText"
                        activePcmFormat != "—" -> activePcmFormat
                        else -> activeRateText
                    }
                    val systemText = when {
                        bp.active && bp.dacLimited -> "Bypassed (DAC max)"
                        bp.active -> "Bypassed (bit-perfect)"
                        else -> "${if (DesktopPlatform.isWindows) "WASAPI" else "Desktop Mixer"} $activeRateText"
                    }

                    PipelineRule()
                    PipelineSection(
                        stageIndex = 4,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = DesktopStrings["pipeline_output_device", "Output Device"],
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                    ) {
                        PipelineRow(DesktopStrings["pipeline_device_name", "Device Name"], deviceName)
                        PipelineRow("Transport", transportText)
                        PipelineRow(DesktopStrings["pipeline_direct", "Direct"], directText)
                        PipelineRow(if (bp.active) "Output" else "Audio Stream", outputPair)
                        PipelineRow("System", systemText)
                    }
                }
            }

            PipelineRule(Modifier)
            PipelineDoneAction(
                label = DesktopStrings["done", "Done"],
                onClick = onDismiss,
            )
        }
    }
}

private fun DrawScope.drawSignalFlow(
    stageCenters: Map<Int, Offset>,
    flowAlpha: Float,
    pulseProgress: Float,
    interactionRadiusPx: Float,
    signalColor: Color,
    nodeCoreColor: Color,
) {
    if (stageCenters.size < 5) return
    val p0 = stageCenters[0] ?: return
    val p1 = stageCenters[1] ?: return
    val p2 = stageCenters[2] ?: return
    val p3 = stageCenters[3] ?: return
    val p4 = stageCenters[4] ?: return

    val xCenter = p0.x
    val y0 = p0.y
    val y4 = p4.y
    val totalHeight = y4 - y0
    if (totalHeight <= 0f) return

    val stagePoints = listOf(p0, p1, p2, p3, p4)
    val nodeRadius = 11.5.dp.toPx()
    val busAlpha = if (flowAlpha > 0f) 0.22f + 0.06f * flowAlpha else 0.18f
    val busStroke = 2.dp.toPx()

    for (i in 0 until stagePoints.size - 1) {
        val yStart = stagePoints[i].y + nodeRadius
        val yEnd = stagePoints[i + 1].y - nodeRadius
        if (yEnd > yStart) {
            drawLine(
                color = signalColor.copy(alpha = busAlpha),
                start = Offset(xCenter, yStart),
                end = Offset(xCenter, yEnd),
                strokeWidth = busStroke,
                cap = StrokeCap.Butt,
            )
            if (flowAlpha > 0f) {
                drawLine(
                    color = signalColor.copy(alpha = 0.05f * flowAlpha),
                    start = Offset(xCenter, yStart),
                    end = Offset(xCenter, yEnd),
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
        }
    }

    if (flowAlpha > 0f && pulseProgress <= 0.95f) {
        val pulseVisibility = if (pulseProgress <= 0.85f) {
            1f
        } else {
            (1f - (pulseProgress - 0.85f) / 0.10f).coerceIn(0f, 1f)
        } * flowAlpha

        if (pulseVisibility > 0.01f) {
            val s = (pulseProgress / 0.85f).coerceIn(0f, 1f)
            val yPulse = y0 + s * totalHeight
            val tailLength = 38.dp.toPx()
            val leadLength = 8.dp.toPx()

            for (i in 0 until stagePoints.size - 1) {
                val yStart = stagePoints[i].y + nodeRadius
                val yEnd = stagePoints[i + 1].y - nodeRadius
                if (yEnd <= yStart) continue

                val pStart = max(yStart, yPulse - tailLength)
                val pEnd = min(yEnd, yPulse + leadLength)
                if (pEnd > pStart) {
                    val haloBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.60f to signalColor.copy(alpha = 0.14f * pulseVisibility),
                            0.88f to signalColor.copy(alpha = 0.28f * pulseVisibility),
                            1.0f to Color.Transparent,
                        ),
                        startY = yPulse - tailLength,
                        endY = yPulse + leadLength,
                    )
                    drawLine(
                        brush = haloBrush,
                        start = Offset(xCenter, pStart),
                        end = Offset(xCenter, pEnd),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )

                    val coreBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.50f to signalColor.copy(alpha = 0.45f * pulseVisibility),
                            0.86f to signalColor.copy(alpha = 0.98f * pulseVisibility),
                            1.0f to Color.Transparent,
                        ),
                        startY = yPulse - tailLength,
                        endY = yPulse + leadLength,
                    )
                    drawLine(
                        brush = coreBrush,
                        start = Offset(xCenter, pStart),
                        end = Offset(xCenter, pEnd),
                        strokeWidth = 2.25.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )

                    if (yPulse in yStart..yEnd) {
                        val headCenter = Offset(xCenter, yPulse)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    signalColor.copy(alpha = 0.35f * pulseVisibility),
                                    Color.Transparent,
                                ),
                                center = headCenter,
                                radius = 7.dp.toPx(),
                            ),
                            radius = 7.dp.toPx(),
                            center = headCenter,
                        )
                        drawCircle(
                            color = signalColor.copy(alpha = 0.98f * pulseVisibility),
                            radius = 1.75.dp.toPx(),
                            center = headCenter,
                        )
                    }
                }
            }
        }
    }

    for (i in stagePoints.indices) {
        val stageCenter = Offset(xCenter, stagePoints[i].y)
        val act = calculateStageActivation(i, pulseProgress, flowAlpha, stageCenters, interactionRadiusPx)
        drawCircle(color = nodeCoreColor, radius = nodeRadius, center = stageCenter)
        val haloAlpha = 0.10f + 0.24f * act * flowAlpha
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to signalColor.copy(alpha = haloAlpha * 0.40f),
                    0.45f to signalColor.copy(alpha = haloAlpha),
                    1.0f to Color.Transparent,
                ),
                center = stageCenter,
                radius = nodeRadius + 5.dp.toPx(),
            ),
            radius = nodeRadius + 5.dp.toPx(),
            center = stageCenter,
        )
        if (flowAlpha > 0f && act > 0.02f) {
            drawCircle(
                color = signalColor.copy(alpha = 0.20f * act * flowAlpha),
                radius = nodeRadius + 1.2.dp.toPx(),
                center = stageCenter,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        val ringAlpha = (0.35f + 0.55f * act * flowAlpha).coerceIn(0f, 1f)
        drawCircle(
            color = signalColor.copy(alpha = ringAlpha),
            radius = nodeRadius,
            center = stageCenter,
            style = Stroke(width = 1.dp.toPx()),
        )
        if (flowAlpha > 0f && act > 0.05f) {
            drawCircle(
                color = signalColor.copy(alpha = 0.60f * act * flowAlpha),
                radius = nodeRadius,
                center = stageCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private fun calculateStageActivation(
    stageIndex: Int,
    progress: Float,
    flowAlpha: Float,
    stageCenters: Map<Int, Offset>,
    interactionRadiusPx: Float,
): Float {
    if (flowAlpha <= 0.001f || stageCenters.size < 5) return 0f
    val p0 = stageCenters[0] ?: return 0f
    val p4 = stageCenters[4] ?: return 0f
    val totalHeight = p4.y - p0.y
    if (totalHeight <= 0f) return 0f
    val targetY = stageCenters[stageIndex]?.y ?: return 0f

    val yPulse = if (progress <= 0.85f) {
        val s = (progress / 0.85f).coerceIn(0f, 1f)
        p0.y + s * totalHeight
    } else {
        p4.y
    }

    val dy = abs(yPulse - targetY)
    if (dy > interactionRadiusPx) return 0f

    val p = 1f - (dy / interactionRadiusPx)
    val raw = (0.5f - 0.5f * cos(p * Math.PI.toFloat())).coerceIn(0f, 1f)
    val absorption = if (stageIndex == 4 && progress > 0.85f) {
        if (progress >= 0.95f) 0f else 1f - ((progress - 0.85f) / 0.10f)
    } else 1f

    return (raw * absorption * flowAlpha).coerceIn(0f, 1f)
}

@Composable
private fun PipelineSection(
    stageIndex: Int,
    icon: ImageVector,
    title: String,
    columnCoordinates: LayoutCoordinates?,
    stageActivationProvider: (Int) -> Float,
    onStageIconPositioned: (Int, Offset) -> Unit,
    content: @Composable () -> Unit,
) {
    var iconCoordinates by remember { androidx.compose.runtime.mutableStateOf<LayoutCoordinates?>(null) }

    fun reportCenter(child: LayoutCoordinates?, parent: LayoutCoordinates?) {
        if (child == null || parent == null || !child.isAttached || !parent.isAttached) return
        val center = parent.localPositionOf(
            child,
            Offset(child.size.width / 2f, child.size.height / 2f),
        )
        onStageIconPositioned(stageIndex, center)
    }

    LaunchedEffect(iconCoordinates, columnCoordinates) {
        reportCenter(iconCoordinates, columnCoordinates)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalDesktopPalette.current.ink,
                modifier = Modifier
                    .size(15.dp)
                    .onGloballyPositioned { coords ->
                        iconCoordinates = coords
                        reportCenter(coords, columnCoordinates)
                    }
                    .graphicsLayer {
                        val act = stageActivationProvider(stageIndex)
                        alpha = (0.80f + 0.20f * act).coerceIn(0f, 1f)
                    },
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.5.sp,
                ),
                color = LocalDesktopPalette.current.ink,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .graphicsLayer {
                        val act = stageActivationProvider(stageIndex)
                        alpha = (0.65f + 0.32f * act).coerceIn(0f, 1f)
                    },
            )
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun PipelineRow(label: String, value: String) {
    val ink = LocalDesktopPalette.current.ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) {
                append("$label: ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = ink.copy(alpha = 0.82f))) {
                append(value)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            ),
        )
    }
}

@Composable
private fun PipelineRule(
    modifier: Modifier = Modifier.padding(start = 52.dp, end = 16.dp),
    color: Color = LocalDesktopPalette.current.cardEdge,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color),
    )
}

@Composable
private fun PipelineDoneAction(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val ink = LocalDesktopPalette.current.ink
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PIPELINE_ACTION_HEIGHT)
            .background(if (pressed) ink.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
            ),
            color = ink,
        )
    }
}

private fun pipelineRateText(hz: Int): String =
    NerdStats.megahertzLabel(hz) ?: "${"%.1f".format(Locale.ROOT, hz / 1000f).removeSuffix(".0")} kHz"

@Composable
private fun channelLabel(channels: Int?): String = when (channels) {
    null -> "—"
    1 -> DesktopStrings["mono", "Mono"]
    2 -> DesktopStrings["stereo", "Stereo"]
    else -> "$channels (Surround)"
}
