package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * What a card does when the pointer is over it: rises a little, and casts a little more shadow.
 *
 * A desktop app answers the pointer before it is clicked — it is most of what separates one from a
 * page in a browser, and the cards here answered nothing at all. The movement is small on purpose:
 * a shelf of them is a lot of motion if each one leaps.
 *
 * The scale is applied about the card's own centre, so a row of them does not shuffle sideways as
 * one grows, and the shadow is animated with it rather than switched on, which would read as a
 * flicker at the boundary.
 */
@Composable
internal fun Modifier.desktopHoverLift(
    shape: Shape,
    scale: Float = HOVER_SCALE,
    elevation: androidx.compose.ui.unit.Dp = HOVER_ELEVATION,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(HOVER_MS),
        label = "hoverLift",
    )
    this
        .hoverable(interaction)
        .graphicsLayer {
            val grown = 1f + (scale - 1f) * lift
            scaleX = grown
            scaleY = grown
        }
        .shadow(elevation * lift, shape)
}

/** Barely more than a nudge — enough to acknowledge the pointer, not enough to jostle the shelf. */
private const val HOVER_SCALE = 1.035f

private val HOVER_ELEVATION = 18.dp

/** Quick enough to feel like a response rather than an animation. */
private const val HOVER_MS = 140


/**
 * A round button's acknowledgement of the pointer: the faintest wash behind the glyph.
 *
 * The toolbar and player buttons were bare clickable boxes, so pointing at one did nothing until it
 * was pressed. On a desktop that reads as a web page. Kept very light — these sit over artwork and
 * a frosted bar, and anything stronger would look like a selected state rather than a hovered one.
 */
@Composable
internal fun Modifier.desktopHoverWash(
    restAlpha: Float = 0f,
    hoverAlpha: Float = WASH_ALPHA,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(WASH_MS),
        label = "hoverWash",
    )
    val ink = DesktopInk
    this
        .hoverable(interaction)
        .drawBehind {
            val alpha = restAlpha + (hoverAlpha - restAlpha) * wash
            if (alpha <= 0.001f) return@drawBehind
            drawCircle(
                color = ink.copy(alpha = alpha),
                radius = size.minDimension / 2f,
            )
        }
}

private const val WASH_MS = 120
private const val WASH_ALPHA = 0.12f

/**
 * A list row's acknowledgement of the pointer: a faint rounded wash behind the whole row.
 *
 * The sidebar rows answered the pointer only once selected, so pointing at an unselected one did
 * nothing until it was clicked. Apple Music lights a row the moment the cursor enters it; this is
 * that wash, kept lighter than the selected pill so hovering never reads as chosen. It draws its own
 * rounded rect rather than relying on the caller's clip, so it can sit anywhere in the chain.
 */
@Composable
internal fun Modifier.desktopHoverFill(
    corner: androidx.compose.ui.unit.Dp = ROW_HOVER_CORNER,
    alpha: Float = ROW_HOVER_ALPHA,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(WASH_MS),
        label = "hoverFill",
    )
    val ink = DesktopInk
    this
        .hoverable(interaction)
        .drawBehind {
            if (wash <= 0.01f) return@drawBehind
            val radius = corner.toPx()
            drawRoundRect(
                color = ink.copy(alpha = alpha * wash),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
}

private val ROW_HOVER_CORNER = 7.dp
private const val ROW_HOVER_ALPHA = 0.07f

/**
 * Adds Apple-style Liquid Glass tactile press compression, hover catchlight, radial liquid
 * expansion bloom anchored at the tap point, and a brief specular edge flash on click/press.
 * Automatically disables motion when Liquid Glass, dynamic blur, animations, or glass click
 * animations are turned off.
 */
@Composable
internal fun Modifier.desktopGlassPress(
    shape: Shape,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    pressScale: Float = 0.968f,
    hoverScale: Float = 1.012f,
    sheenColor: Color = Color.Unspecified,
    rimBoostAlpha: Float = 0.26f,
): Modifier = composed {
    val resolvedSheen = if (sheenColor != Color.Unspecified) sheenColor else DesktopInk
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()
    val glassClickAnimations by DesktopAppearanceSettings.glassClickAnimations.collectAsState()
    val active = enabled &&
        liquidGlass &&
        DesktopPlatform.supportsLiquidGlass &&
        !reduceDynamicBlur &&
        !reduceAnimation &&
        glassClickAnimations
    if (!active) return@composed this

    val resolvedInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val interactionPressed by resolvedInteraction.collectIsPressedAsState()
    val hovered by resolvedInteraction.collectIsHoveredAsState()
    var pointerPressed by remember { mutableStateOf(false) }
    var tapOffset by remember { mutableStateOf(Offset.Unspecified) }
    var pulseNonce by remember { mutableStateOf(0) }
    val pressed = interactionPressed || pointerPressed

    val ripple = remember { Animatable(0f) }
    LaunchedEffect(pulseNonce) {
        if (pulseNonce == 0) return@LaunchedEffect
        ripple.snapTo(0f)
        ripple.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 390, easing = FastOutSlowInEasing),
        )
    }

    val targetScale = when {
        pressed -> pressScale
        hovered -> hoverScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "glassPressScale",
    )
    val hoverGlow by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "glassHoverCatchlight",
    )

    this
        .hoverable(resolvedInteraction)
        .pointerInput(resolvedInteraction) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Press -> {
                            tapOffset = event.changes.firstOrNull()?.position ?: Offset.Unspecified
                            pointerPressed = true
                            pulseNonce++
                        }
                        PointerEventType.Release, PointerEventType.Exit -> {
                            pointerPressed = false
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            val t = ripple.value
            val waveEnvelope = if (t > 0.001f && t < 0.999f) sin(t * Math.PI).toFloat() else 0f
            val baseHover = hoverGlow * 0.055f
            if (baseHover <= 0.002f && waveEnvelope <= 0.002f && !pressed) return@drawWithContent

            val outline = shape.createOutline(size, layoutDirection, this)
            if (hovered && baseHover > 0.002f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            resolvedSheen.copy(alpha = baseHover * 1.4f),
                            Color.Transparent,
                        ),
                    ),
                )
            }
            if (waveEnvelope > 0.002f || pressed) {
                val center = if (tapOffset == Offset.Unspecified) {
                    Offset(size.width * 0.5f, size.height * 0.5f)
                } else {
                    tapOffset
                }
                val maxR = hypot(size.width, size.height).coerceAtLeast(24f)
                val currentR = (maxR * (0.22f + 0.85f * t)).coerceAtLeast(16f)
                val radialAlpha = if (pressed) 0.14f + 0.14f * waveEnvelope else 0.22f * waveEnvelope
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            resolvedSheen.copy(alpha = radialAlpha),
                            resolvedSheen.copy(alpha = radialAlpha * 0.42f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = currentR,
                    ),
                    center = center,
                    radius = currentR,
                )
                val sweepCenter = -size.width * 0.25f + size.width * 1.5f * t
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            resolvedSheen.copy(alpha = 0.16f * waveEnvelope),
                            Color.Transparent,
                        ),
                        start = Offset(sweepCenter - size.width * 0.35f, 0f),
                        end = Offset(sweepCenter + size.width * 0.35f, size.height),
                    ),
                )
                val rimGlow = (rimBoostAlpha * waveEnvelope + if (pressed) rimBoostAlpha * 0.45f else 0f)
                    .coerceIn(0f, 0.5f)
                if (rimGlow > 0.005f) {
                    drawOutline(
                        outline = outline,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                resolvedSheen.copy(alpha = rimGlow),
                                resolvedSheen.copy(alpha = rimGlow * 0.32f),
                                resolvedSheen.copy(alpha = rimGlow * 0.65f),
                            ),
                        ),
                        style = Stroke(width = 1.1.dp.toPx()),
                    )
                }
            }
        }
}
