package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * The phone's player sheet, for a window: the shared player rises over the page
 * the way Android's full-screen bottom sheet brings it up, and goes back down
 * the same ways — a downward drag anywhere the player does not claim for
 * itself, a click on the handle it draws at the top, or Escape (which the
 * window routes through [com.music.bitchord.ui.player.PlayerBack] first, so the
 * lyrics or the queue close before the sheet does).
 *
 * Drawn inside the window rather than as Material's modal sheet: that one is a
 * focusable popup on the desktop, and would take the media keys and the
 * window's shortcuts away for as long as the player was up.
 */
@Composable
internal fun DesktopPlayerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    playerHaze: HazeState = remember { HazeState() },
    playerBackdrop: DesktopLayerBackdrop = rememberDesktopLayerBackdrop(),
    content: @Composable BoxScope.(windowWidth: Dp, windowHeight: Dp) -> Unit,
) {
    // Mounted while it animates out, so the slide down is seen.
    var mounted by remember { mutableStateOf(visible) }
    if (visible) mounted = true
    if (!mounted) return

    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassRecordActive = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val videoFullscreen by DesktopPlayerHost.videoFullscreen.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val offset = remember { Animatable(heightPx) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(visible) {
            if (visible) {
                offset.animateTo(0f, tween(SHEET_OPEN_MS, easing = FastOutSlowInEasing))
            } else {
                offset.animateTo(heightPx, tween(SHEET_CLOSE_MS, easing = FastOutSlowInEasing))
                mounted = false
            }
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, heightPx)) }
        }
        CompositionLocalProvider(
            LocalDesktopHaze provides playerHaze,
            LocalDesktopBackdrop provides playerBackdrop,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = offset.value }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (offset.value > heightPx * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
                                onDismiss()
                            } else {
                                offset.animateTo(0f, tween(SHEET_SETTLE_MS, easing = FastOutSlowInEasing))
                            }
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .hazeSource(playerHaze)
                        .desktopLayerBackdrop(playerBackdrop, enabled = glassRecordActive),
                ) {
                    content(windowWidth, windowHeight)
                }
                if (!videoFullscreen) {
                    DesktopClosePlayerButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 22.dp, top = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DesktopClosePlayerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val isDark = LocalDesktopPalette.current.isDark
    val fill = if (isDark) {
        if (hovered) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
    } else {
        if (hovered) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.56f)
    }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
    val iconTint = if (isDark) Color.White else DesktopInk
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, borderColor, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        hovered = event.type == PointerEventType.Enter || event.type == PointerEventType.Move
                        if (event.type == PointerEventType.Exit) hovered = false
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Close player",
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val SHEET_OPEN_MS = 360
private const val SHEET_CLOSE_MS = 280
private const val SHEET_SETTLE_MS = 220
private const val DISMISS_FRACTION = 0.25f
private const val DISMISS_VELOCITY = 1_600f
private const val HANDLE_HIT_WIDTH_FRACTION = 0.2f
private val HANDLE_HIT_HEIGHT = 24.dp
