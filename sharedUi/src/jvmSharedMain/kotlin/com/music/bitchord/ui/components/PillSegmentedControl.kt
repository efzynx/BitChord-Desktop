package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.graphics.fadingEdges
import com.music.bitchord.ui.player.PlayerSettings
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Configures Liquid Glass styling and tactile click/slide animations for [PillSegmentedControl].
 * Desktop hosts provide this via [LocalPillSegmentedGlassStyle] while mobile targets keep the
 * default flat representation.
 */
@Immutable
data class PillSegmentedGlassStyle(
    val enabled: Boolean = false,
    val clickAnimation: Boolean = false,
)

val LocalPillSegmentedGlassStyle = compositionLocalOf { PillSegmentedGlassStyle() }

/**
 * Apple's segmented control: a squircle track in the tertiary fill, and the
 * selected segment a squircle pill that slides between the others.
 *
 * The proportions are the Liquid Glass mode picker's, but both shapes are flat
 * fills — no backdrop is sampled — so it looks and costs the same whether
 * Liquid Glass is on or off.
 *
 * Every "pick one of these" in the app — Settings' choices, a folder's
 * Songs / Albums / Artists tabs, Search's sources and its YouTube filters.
 *
 * Segments share the track evenly while every label fits its share, take what
 * their labels need and split what is left while the labels fit together, and
 * past that keep their own widths and scroll, faded out at whichever edge has
 * more beyond it. The selection is kept in view.
 */
@Composable
fun PillSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Segments shown but not offered: their labels dimmed, taps ignored. */
    disabled: Set<Int> = emptySet(),
) {
    if (options.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val glassStyle = LocalPillSegmentedGlassStyle.current
    val glassEnabled = glassStyle.enabled
    val trackColor = TRACK_FILL.copy(alpha = if (light) LIGHT_TRACK_ALPHA else DARK_TRACK_ALPHA)
    val pillColor = if (light) Color.White else DARK_PILL
    val labelColor = if (light) Color.Black else Color.White
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val animateMotion = !reduceAnimation
    val glassClickAnim = glassEnabled && glassStyle.clickAnimation && animateMotion
    val selected = selectedIndex.takeIf { it in options.indices }
    var glassPulseNonce by remember { mutableStateOf(0) }
    val glassPulse = remember { Animatable(0f) }
    LaunchedEffect(selected, glassPulseNonce, glassClickAnim) {
        if (!glassClickAnim || (selected == null && glassPulseNonce == 0)) return@LaunchedEffect
        glassPulse.snapTo(0f)
        glassPulse.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
    }

    val trackModifier = if (glassEnabled) {
        val trackBrush = if (light) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.48f),
                    Color.White.copy(alpha = 0.30f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.085f),
                    Color(0xFF0E0E13).copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.045f),
                ),
            )
        }
        val trackRim = if (light) {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.24f)))
        } else {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.055f),
                    Color.White.copy(alpha = 0.14f),
                ),
            )
        }
        Modifier
            .clip(TrackShape)
            .background(trackBrush)
            .border(0.85.dp, trackRim, TrackShape)
    } else {
        Modifier
            .clip(TrackShape)
            .background(trackColor)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(trackModifier),
    ) {
        val inset = with(density) { TRACK_PADDING.toPx() }
        val labelPadding = with(density) { LABEL_PADDING.toPx() }
        val viewport = constraints.maxWidth.toFloat()
        val room = viewport - inset * 2f
        val natural = remember(options, style, measurer, density) {
            options.map { label ->
                measurer.measure(label, style, maxLines = 1, softWrap = false).size.width + labelPadding * 2f
            }
        }
        val needed = natural.sum()
        val scrolls = needed > room
        val widths = when {
            scrolls -> natural
            natural.max() * options.size <= room -> List(options.size) { room / options.size }
            else -> natural.map { it + (room - needed) / options.size }
        }
        val starts = widths.runningFold(inset) { start, width -> start + width }
        val contentWidth = if (scrolls) needed + inset * 2f else viewport

        val motion: AnimationSpec<Float> = if (reduceAnimation) snap() else spring(PILL_DAMPING, Spring.StiffnessMediumLow)
        val pillStart by animateFloatAsState(selected?.let(starts::get) ?: 0f, motion, label = "segmentStart")
        val pillWidth by animateFloatAsState(selected?.let(widths::get) ?: 0f, motion, label = "segmentWidth")

        val scroll = rememberScrollState()
        // The selection centred, as far as the ends allow: there at once the
        // first time, slid there after.
        val placed = remember { booleanArrayOf(false) }
        LaunchedEffect(selected, natural, viewport) {
            if (!scrolls || selected == null) return@LaunchedEffect
            val max = snapshotFlow { scroll.maxValue }.first { it > 0 }
            val centred = (starts[selected] + widths[selected] / 2f - viewport / 2f).roundToInt().coerceIn(0, max)
            if (placed[0] && !reduceAnimation) scroll.animateScrollTo(centred) else scroll.scrollTo(centred)
            placed[0] = true
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (scrolls) {
                        // Out at whichever edge has more beyond it, deepening as
                        // that edge's overflow grows to a fade's width.
                        val fadePx = with(density) { EDGE_FADE.toPx() }
                        Modifier
                            .fadingEdges(
                                EDGE_FADE,
                                start = { scroll.value / fadePx },
                                end = { (scroll.maxValue - scroll.value) / fadePx },
                            )
                            .horizontalScroll(scroll)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                Modifier
                    .width(with(density) { contentWidth.toDp() })
                    .padding(vertical = TRACK_PADDING),
            ) {
                if (selected != null) {
                    val pulseValue = if (glassClickAnim) glassPulse.value else 0f
                    val pulseArc = if (pulseValue in 0.001f..0.999f) sin(pulseValue * PI).toFloat() else 0f
                    val pillScaleX = if (glassClickAnim) 1f + 0.025f * pulseArc else 1f
                    val pillScaleY = if (glassClickAnim) 1f + 0.045f * pulseArc else 1f
                    val pillGlassMod = if (glassEnabled) {
                        val lensFill = if (light) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.92f),
                                    Color.White.copy(alpha = 0.74f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.25f + 0.06f * pulseArc),
                                    Color.White.copy(alpha = 0.13f + 0.04f * pulseArc),
                                    Color.White.copy(alpha = 0.19f + 0.05f * pulseArc),
                                ),
                            )
                        }
                        val pillRim = if (light) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.95f),
                                    Color.White.copy(alpha = 0.45f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.46f + 0.20f * pulseArc),
                                    Color.White.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.26f + 0.10f * pulseArc),
                                ),
                            )
                        }
                        Modifier
                            .graphicsLayer {
                                scaleX = pillScaleX
                                scaleY = pillScaleY
                            }
                            .clip(PillShape)
                            .background(lensFill)
                            .drawWithContent {
                                drawContent()
                                if (pulseArc > 0.005f) {
                                    val sweepX = -size.width * 0.2f + size.width * 1.4f * pulseValue
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.28f * pulseArc),
                                                Color.Transparent,
                                            ),
                                            start = Offset(sweepX - size.width * 0.35f, 0f),
                                            end = Offset(sweepX + size.width * 0.35f, size.height),
                                        ),
                                    )
                                }
                            }
                            .border(0.9.dp, pillRim, PillShape)
                    } else {
                        Modifier
                            .clip(PillShape)
                            .background(pillColor)
                    }
                    Box(
                        Modifier
                            .matchParentSize()
                            // Read here rather than in composition, so the slide
                            // relays this box out and recomposes nothing.
                            .layout { measurable, constraints ->
                                val width = pillWidth.roundToInt().coerceAtLeast(0)
                                val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(pillStart.roundToInt(), 0)
                                }
                            }
                            .then(pillGlassMod),
                    )
                }
                options.forEachIndexed { index, label ->
                    val segInteraction = remember(index) { MutableInteractionSource() }
                    val segHovered by segInteraction.collectIsHoveredAsState()
                    val segPressed by segInteraction.collectIsPressedAsState()
                    val isDisabled = index in disabled
                    val isActive = index == selected
                    val segScale by animateFloatAsState(
                        targetValue = when {
                            !glassClickAnim || isDisabled -> 1f
                            segPressed -> 0.96f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
                        label = "segmentScale",
                    )
                    val hoverHighlight = glassEnabled && animateMotion && !isDisabled && !isActive && (segHovered || segPressed)
                    Box(
                        modifier = Modifier
                            .atX(starts[index])
                            .width(with(density) { widths[index].toDp() })
                            .then(if (glassEnabled) Modifier.hoverable(segInteraction) else Modifier)
                            .graphicsLayer {
                                scaleX = segScale
                                scaleY = segScale
                            }
                            .clip(PillShape)
                            .then(
                                if (hoverHighlight) {
                                    Modifier.background(
                                        Color.White.copy(alpha = if (segPressed && glassClickAnim) 0.11f else 0.055f),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(
                                enabled = enabled && !isDisabled,
                                interactionSource = segInteraction,
                                indication = null,
                            ) {
                                if (glassClickAnim) {
                                    glassPulseNonce++
                                }
                                if (index != selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(index)
                                }
                            }
                            .padding(vertical = LABEL_VERTICAL_PADDING),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = style,
                            color = when {
                                isDisabled -> labelColor.copy(alpha = DISABLED_LABEL_ALPHA)
                                glassEnabled && !isActive -> labelColor.copy(alpha = if (segHovered) 0.94f else 0.78f)
                                else -> labelColor
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Placed [x] pixels along. */
private fun Modifier.atX(x: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(IntOffset(x.roundToInt(), 0)) }
}

private val TrackShape = SquirclePillShape()
private val PillShape = SquirclePillShape()

/** Apple's tertiary fill: a grey at a quarter on dark and an eighth on light. */
private val TRACK_FILL = Color(0xFF767680)
private const val DARK_TRACK_ALPHA = 0.24f
private const val LIGHT_TRACK_ALPHA = 0.12f

/** The selected segment on dark, Apple's lifted grey; on light it is white. */
private val DARK_PILL = Color(0xFF636366)

private val TRACK_PADDING = 4.dp
/** Air either side of a label: past this a segment reads as a word, not a cramped tab. */
private val LABEL_PADDING = 18.dp
private val LABEL_VERTICAL_PADDING = 9.dp
private val EDGE_FADE = 28.dp
private const val PILL_DAMPING = 0.8f
private const val DISABLED_LABEL_ALPHA = 0.3f
