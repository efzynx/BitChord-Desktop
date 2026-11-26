package com.music.bitchord.ui.components

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Configures Liquid Glass surface and press-ripple styling for shared [SearchField] widgets on desktop.
 */
@Immutable
data class SearchFieldGlassStyle(
    val enabled: Boolean = false,
    val clickAnimation: Boolean = false,
)

val LocalSearchFieldGlassStyle = compositionLocalOf { SearchFieldGlassStyle() }

/**
 * True where a search field sits on a surface its host draws — the top bar's
 * pill — so the field draws no fill of its own and takes that surface's height
 * rather than its own 46dp.
 */
val LocalSearchFieldOnSurface = staticCompositionLocalOf { false }

/**
 * The pill-shaped search field used at the top of the search page — a
 * magnifier that doubles as the submit button, a hint that gives way to typed
 * text, and a clear button once there is something to clear. Reused wherever
 * else the app needs the same "search this" affordance, such as filtering the
 * settings list.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    /** What the empty field says it is for — the one part that changes per screen. */
    placeholder: String = stringResource(Res.string.search_hint),
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val onSurface = LocalSearchFieldOnSurface.current
    val glassStyle = LocalSearchFieldGlassStyle.current
    val glassActive = !onSurface && glassStyle.enabled
    val glassClickAnim = glassActive && glassStyle.clickAnimation
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val rippleInk = if (light) Color.Black else Color.White
    val fieldShape = RoundedCornerShape(11.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    var pointerPressed by remember { mutableStateOf(false) }
    var tapOffset by remember { mutableStateOf(Offset.Unspecified) }
    var pulseNonce by remember { mutableStateOf(0) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(pulseNonce, glassClickAnim) {
        if (!glassClickAnim || pulseNonce == 0) return@LaunchedEffect
        ripple.snapTo(0f)
        ripple.animateTo(1f, animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing))
    }
    val scale by animateFloatAsState(
        targetValue = if (glassClickAnim && (pressed || pointerPressed)) 0.988f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "searchFieldScale",
    )
    // Both ways of saying "search this" do the same two things, so they're
    // written once here rather than twice.
    val submit = {
        onSubmit()
        focusManager.clearFocus()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onSurface) {
                    Modifier.fillMaxHeight()
                } else if (glassActive) {
                    Modifier
                        .height(46.dp)
                        .hoverable(interactionSource)
                        .then(
                            if (glassClickAnim) {
                                Modifier.pointerInput(Unit) {
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
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(fieldShape)
                        .background(
                            if (light) {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (focused || hovered) 0.90f else 0.76f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f),
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (focused || hovered) 0.13f else 0.095f),
                                        Color(0xFF0E0E13).copy(alpha = 0.36f),
                                    ),
                                )
                            },
                        )
                        .drawWithContent {
                            drawContent()
                            val t = if (glassClickAnim) ripple.value else 0f
                            val wave = if (t > 0.001f && t < 0.999f) sin(t * Math.PI).toFloat() else 0f
                            if (wave > 0.004f) {
                                val center = if (tapOffset == Offset.Unspecified) {
                                    Offset(size.width * 0.5f, size.height * 0.5f)
                                } else {
                                    tapOffset
                                }
                                val maxR = hypot(size.width, size.height).coerceAtLeast(24f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            rippleInk.copy(alpha = 0.16f * wave),
                                            Color.Transparent,
                                        ),
                                        center = center,
                                        radius = maxR * (0.25f + 0.8f * t),
                                    ),
                                    center = center,
                                    radius = maxR * (0.25f + 0.8f * t),
                                )
                            }
                        }
                        .border(
                            width = if (focused) 1.dp else 0.85.dp,
                            brush = if (light) {
                                Brush.linearGradient(
                                    listOf(
                                        Color.Black.copy(alpha = if (focused) 0.28f else if (hovered) 0.20f else 0.14f),
                                        Color.Black.copy(alpha = if (focused) 0.14f else 0.06f),
                                    ),
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (focused) 0.34f else if (hovered) 0.26f else 0.19f),
                                        Color.White.copy(alpha = if (focused) 0.14f else 0.06f),
                                    ),
                                )
                            },
                            shape = fieldShape,
                        )
                } else {
                    // Fixed height prevents the row from growing when text is entered
                    Modifier
                        .height(46.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, fieldShape)
                },
            )
            // Asymmetric: the magnifier is a button now and wants a real touch
            // target, so it's given the room by pulling the field's own start
            // padding in rather than by pushing the glyph and the text along.
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The search button. It reads as one — a magnifier at the head of a
        // text field is the search affordance on every platform — and now that
        // pressing it is the only thing that runs a search, leaving it
        // decorative would mean the keyboard's own key was the single way in.
        Icon(
            Icons.Rounded.Search,
            contentDescription = stringResource(Res.string.search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = query.isNotBlank(), onClick = submit)
                .padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // A narrow field (the phone's, beside the account photo)
                    // must cut the hint short, not wrap it out of the pill.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        // Emptying the field is also how the recent searches are got back to,
        // so it needs to be one tap rather than a held backspace.
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        onQueryChange("")
                        // Clearing is an edit, not a dismissal: leave the user
                        // ready to immediately type their next search.
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
