package com.music.bitchord.desktop

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// The look every modal card in the app shares.

/** The card fill. */
internal val DesktopCardFill: Color
    @Composable get() = LocalDesktopPalette.current.cardFill

/** A block inside a card — a settings group — lifted just off the fill. */
internal val DesktopCardInsetFill: Color
    @Composable get() = LocalDesktopPalette.current.cardInsetFill

/** The hairline that bounds a card, and separates one row from the next. */
internal val DesktopCardEdge: Color
    @Composable get() = LocalDesktopPalette.current.cardEdge

/** What a row reads at while the pointer is on it. */
internal val DesktopRowHover: Color
    @Composable get() = LocalDesktopPalette.current.rowHover

/** The scrim a modal card sits on. */
internal val DesktopScrim: Color
    @Composable get() = LocalDesktopPalette.current.scrim

/** Card, hairline and all. */
internal fun Modifier.desktopCard(shape: Shape): Modifier = composed {
    clip(shape).background(DesktopCardFill).border(0.5.dp, DesktopCardEdge, shape)
}

/**
 * Shared smoked/frosted Settings container surface synced with Ambient artwork glow, Liquid Glass,
 * Reduce dynamic blur, Disable blend effects, and active Light/Dark theme.
 */
internal fun Modifier.desktopSettingsCardSurface(
    shape: Shape = RoundedCornerShape(14.dp),
): Modifier = composed {
    val palette = LocalDesktopPalette.current
    val ambient by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val disableBlend by DesktopAppearanceSettings.disableBlendEffects.collectAsState()
    val translucentCard = !reduceBlur && (liquidGlass || (ambient && !disableBlend))
    val cardAlpha = when {
        ambient && !disableBlend && !reduceBlur -> if (palette.isDark) 0.56f else 0.52f
        liquidGlass && !reduceBlur -> if (palette.isDark) 0.72f else 0.68f
        else -> 1f
    }
    this
        .clip(shape)
        .then(
            if (translucentCard) {
                Modifier
                    .background(palette.settingsCardGlassBase.copy(alpha = cardAlpha))
                    .background(
                        Brush.verticalGradient(
                            if (palette.isDark) {
                                listOf(
                                    Color.White.copy(alpha = 0.045f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.06f),
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.34f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.025f),
                                )
                            },
                        ),
                    )
                    .border(
                        width = 0.75.dp,
                        brush = Brush.linearGradient(
                            if (palette.isDark) {
                                listOf(
                                    Color.White.copy(alpha = if (liquidGlass) 0.16f else 0.11f),
                                    Color.White.copy(alpha = 0.04f),
                                )
                            } else {
                                listOf(
                                    Color.Black.copy(alpha = if (liquidGlass) 0.11f else 0.08f),
                                    Color.Black.copy(alpha = 0.05f),
                                )
                            },
                        ),
                        shape = shape,
                    )
            } else {
                Modifier
                    .background(palette.settingsCardSolid)
                    .border(0.5.dp, DesktopCardEdge, shape)
            },
        )
}

/** A block of rows inside a modal card or Settings sub-page. */
internal fun Modifier.desktopCardInset(shape: Shape): Modifier = composed {
    if (LocalDesktopPanelIsPage.current) {
        desktopSettingsCardSurface(shape)
    } else {
        clip(shape)
            .background(DesktopCardInsetFill)
            .border(0.65.dp, DesktopCardEdge, shape)
    }
}

/** The rule between two rows of a card. A page of Settings keeps grouped dividers when requested. */
@Composable
internal fun DesktopCardRule(
    modifier: Modifier = Modifier,
    alwaysVisible: Boolean = false,
) {
    if (!alwaysVisible && LocalDesktopPanelIsPage.current) return
    HorizontalDivider(modifier, thickness = 0.5.dp, color = DesktopCardEdge)
}

/**
 * Unified header bar for all Settings sub-pages (Audio output, Equalizer, Lyrics sources,
 * Translation language, Integrations, Spotify Canvas, Listen together, Updates, Licenses).
 */
@Composable
internal fun DesktopSettingsSubPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopToolbarButton(
                onClick = onBack,
                size = 36.dp,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = DesktopStrings["back", "Back"],
                    tint = DesktopInk,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesktopInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        trailing()
    }
}

/**
 * Tracks optimistic `TextFieldValue` edits against parent `String` props so delayed portal
 * or `StateFlow` updates never clamp the caret back to an earlier offset mid-typing.
 */
internal class DesktopTextFieldSyncState(
    initialValue: String,
) {
    var lastPropValue: String = initialValue
    var lastEmittedValue: String = initialValue
    private val unconfirmed = ArrayDeque<String>()

    fun syncFromProp(
        propValue: String,
        current: TextFieldValue,
    ): TextFieldValue {
        if (propValue == lastPropValue) return current
        lastPropValue = propValue
        if (propValue == current.text) {
            unconfirmed.clear()
            return current
        }
        val matchedIndex = unconfirmed.indexOf(propValue)
        if (matchedIndex >= 0) {
            repeat(matchedIndex + 1) { unconfirmed.removeFirst() }
            return current
        }
        val wasTyping = unconfirmed.isNotEmpty()
        val wasAtEnd = current.selection.collapsed && current.selection.start == current.text.length
        val wasEmpty = current.text.isEmpty()
        unconfirmed.clear()
        lastEmittedValue = propValue
        val nextSelection = if (!wasTyping && (wasAtEnd || wasEmpty)) {
            TextRange(propValue.length)
        } else {
            TextRange(
                current.selection.start.coerceIn(0, propValue.length),
                current.selection.end.coerceIn(0, propValue.length),
            )
        }
        return current.copy(text = propValue, selection = nextSelection, composition = null)
    }

    fun onUserChanged(
        next: TextFieldValue,
        emit: (String) -> Unit,
    ): TextFieldValue {
        if (next.text != lastEmittedValue) {
            lastEmittedValue = next.text
            unconfirmed.addLast(next.text)
            if (unconfirmed.size > 16) unconfirmed.removeFirst()
            emit(next.text)
        }
        return next
    }

    fun forceReconcile(
        propValue: String,
        current: TextFieldValue,
    ): TextFieldValue {
        unconfirmed.clear()
        lastPropValue = propValue
        lastEmittedValue = propValue
        if (current.text == propValue) return current
        return current.copy(
            text = propValue,
            selection = TextRange(
                current.selection.start.coerceIn(0, propValue.length),
                current.selection.end.coerceIn(0, propValue.length),
            ),
            composition = null,
        )
    }
}

/** Clamps a [TextFieldValue] to [maxLength] while keeping caret selection within bounds. */
internal fun limitTextFieldValue(
    value: TextFieldValue,
    maxLength: Int,
): TextFieldValue {
    if (value.text.length <= maxLength) return value
    val clampedText = value.text.take(maxLength)
    return value.copy(
        text = clampedText,
        selection = TextRange(
            value.selection.start.coerceIn(0, clampedText.length),
            value.selection.end.coerceIn(0, clampedText.length),
        ),
        composition = null,
    )
}

/**
 * Frosted translucent text input field synced with Liquid Glass, Ambient artwork glow,
 * Reduce dynamic blur, and interactive glass press animations.
 */
@Composable
internal fun DesktopGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val sync = remember { DesktopTextFieldSyncState(value) }
    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }
    val displayValue = sync.syncFromProp(value, textFieldValueState)
    SideEffect {
        if (displayValue != textFieldValueState) {
            textFieldValueState = displayValue
        }
    }
    LaunchedEffect(value, textFieldValueState.text) {
        if (value != textFieldValueState.text) {
            delay(120)
            if (value != textFieldValueState.text) {
                textFieldValueState = sync.forceReconcile(value, textFieldValueState)
            }
        }
    }

    DesktopGlassTextField(
        value = displayValue,
        onValueChange = { next ->
            textFieldValueState = sync.onUserChanged(next, onValueChange)
        },
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        enabled = enabled,
        isPassword = isPassword,
        imeAction = imeAction,
        onSubmit = onSubmit,
        focusRequester = focusRequester,
    )
}

/**
 * [TextFieldValue] overload of [DesktopGlassTextField] preserving explicit caret selection
 * and IME composition ranges without string round-tripping.
 */
@Composable
internal fun DesktopGlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val palette = LocalDesktopPalette.current
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassSurface = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    var isFocused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val fieldShape = RoundedCornerShape(11.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = DesktopSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .desktopGlassPress(
                    shape = fieldShape,
                    interactionSource = interaction,
                    enabled = enabled,
                    pressScale = 0.988f,
                    hoverScale = 1f,
                    rimBoostAlpha = 0.28f,
                )
                .clip(fieldShape)
                .then(
                    if (glassSurface) {
                        val bgBrush = if (palette.isDark) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isFocused) 0.13f else 0.095f),
                                    Color(0xFF0E0E13).copy(alpha = 0.34f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isFocused) 0.92f else 0.78f),
                                    palette.surfaceRaised.copy(alpha = 0.80f),
                                ),
                            )
                        }
                        val borderBrush = if (palette.isDark) {
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isFocused) 0.36f else 0.20f),
                                    Color.White.copy(alpha = if (isFocused) 0.14f else 0.06f),
                                ),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color.Black.copy(alpha = if (isFocused) 0.30f else 0.14f),
                                    Color.Black.copy(alpha = if (isFocused) 0.16f else 0.07f),
                                ),
                            )
                        }
                        Modifier
                            .background(bgBrush)
                            .border(
                                width = if (isFocused) 1.dp else 0.85.dp,
                                brush = borderBrush,
                                shape = fieldShape,
                            )
                    } else {
                        Modifier
                            .background(
                                if (palette.isDark) {
                                    Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)
                                } else {
                                    palette.surfaceRaised.copy(alpha = if (enabled) 1f else 0.6f)
                                },
                            )
                            .border(
                                width = if (isFocused) 1.dp else 0.5.dp,
                                color = if (isFocused) DesktopAccent.copy(alpha = 0.65f) else DesktopCardEdge,
                                shape = fieldShape,
                            )
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DesktopSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = DesktopInk),
                    cursorBrush = SolidColor(DesktopAccent),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }
            if (value.text.isNotEmpty() && enabled) {
                val clearInteraction = remember { MutableInteractionSource() }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .desktopGlassPress(CircleShape, interactionSource = clearInteraction, pressScale = 0.90f)
                        .background(DesktopInk.copy(alpha = 0.12f))
                        .clickable(interactionSource = clearInteraction, indication = null) {
                            onValueChange(TextFieldValue(text = "", selection = TextRange.Zero))
                            runCatching { focusRequester?.requestFocus() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        DesktopStrings["clear", "Clear"],
                        tint = DesktopInk.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Interactive pill button synced with Liquid Glass press effects and Settings card styling.
 */
@Composable
internal fun DesktopGlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    destructive: Boolean = false,
) {
    val palette = LocalDesktopPalette.current
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassSurface = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    val bgBrush = when {
        accent && enabled -> Brush.verticalGradient(listOf(DesktopAccent, DesktopAccent.copy(alpha = 0.90f)))
        accent -> Brush.verticalGradient(listOf(DesktopInk.copy(alpha = 0.12f), DesktopInk.copy(alpha = 0.08f)))
        destructive -> Brush.verticalGradient(listOf(DesktopDestructive.copy(alpha = 0.16f), DesktopDestructive.copy(alpha = 0.08f)))
        glassSurface && !palette.isDark -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.74f), Color(0xFFF3F4F8).copy(alpha = 0.56f)))
        glassSurface -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f)))
        !palette.isDark -> Brush.verticalGradient(listOf(palette.surfaceRaised, palette.surfaceRaised))
        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.08f)))
    }
    val textColor = when {
        !enabled -> DesktopSecondary.copy(alpha = 0.55f)
        accent -> DesktopOnAccent
        destructive -> DesktopDestructive
        else -> DesktopInk
    }
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .desktopGlassPress(
                shape = shape,
                interactionSource = interaction,
                enabled = enabled,
                pressScale = 0.95f,
                sheenColor = if (accent) DesktopOnAccent else DesktopInk,
            )
            .clip(shape)
            .background(bgBrush)
            .then(
                if (!accent && (glassSurface || destructive || !palette.isDark)) {
                    Modifier.border(
                        0.75.dp,
                        if (destructive) DesktopDestructive.copy(alpha = 0.30f) else DesktopInk.copy(alpha = if (palette.isDark) 0.20f else 0.14f),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
        )
    }
}

/**
 * Whether the panels drawn here are pages of Settings rather than cards over the page. Provided by
 * the Settings host, so the same screen is a card from the player and a page from Settings.
 */
internal val LocalDesktopPanelIsPage = staticCompositionLocalOf { false }

/** The margin either side of a page of Settings: every title, heading and row starts on it. */
internal val DesktopSettingsPageGutter = 28.dp

/**
 * How wide Settings and its pages run: the width the Settings card had, so a page in a wide window
 * reads as a column of rows rather than rows stretched to the window's edges.
 */
internal val DesktopSettingsPageWidth = 840.dp

/** A page of Settings: a column down the middle of the page area, as tall as the area. */
@Composable
internal fun DesktopSettingsPageFrame(
    maxWidth: Dp = DesktopSettingsPageWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = DesktopSettingsPageGutter),
            content = content,
        )
    }
}

/**
 * The part of a panel that scrolls. On a card it stops at [cardMax] so the card stays a card; on a
 * page it takes whatever the page has left below the heading and above the actions, which stay
 * where they are rather than scrolling away with the rows. [fill] gives a page the whole of it even
 * while its rows are few.
 */
@Composable
internal fun ColumnScope.desktopPanelBody(
    cardMax: Dp,
    cardMin: Dp = 0.dp,
    fill: Boolean = false,
): Modifier = if (LocalDesktopPanelIsPage.current) {
    Modifier.weight(1f, fill = fill)
} else {
    Modifier.heightIn(min = cardMin, max = cardMax)
}

/**
 * A panel's own side inset: [card] on a card, none on a page, where the page's gutter is the inset
 * and the rows line up under the title.
 */
@Composable
internal fun panelInset(card: Dp): Dp = if (LocalDesktopPanelIsPage.current) 0.dp else card

/**
 * A row's tap. On a page the row does not light up under the pointer — only its switch, slider or
 * chip does; on a card it keeps the usual wash.
 */
@Composable
internal fun Modifier.desktopRowClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return if (LocalDesktopPanelIsPage.current) {
        clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    } else {
        clickable(enabled = enabled, onClick = onClick)
    }
}

@Stable
internal class DesktopModalEntry(
    val id: Any,
    onDismiss: () -> Unit,
    maxWidth: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    var onDismiss by mutableStateOf(onDismiss)
    var maxWidth by mutableStateOf(maxWidth)
    var content by mutableStateOf(content)
}

/**
 * Window-level host for popup panels opened from inside scrollers or captured backdrop trees.
 * Rendering modals outside the window's backdrop recorder avoids Skia self-recording crashes
 * and ensures the backdrop dim covers the entire window chrome.
 */
@Stable
internal class DesktopModalHostState {
    val entries = mutableStateListOf<DesktopModalEntry>()

    fun register(entry: DesktopModalEntry) {
        if (entries.none { it.id === entry.id }) {
            entries.add(entry)
        }
    }

    fun unregister(entry: DesktopModalEntry) {
        entries.removeAll { it.id === entry.id }
    }
}

internal val LocalDesktopModalHost = staticCompositionLocalOf<DesktopModalHostState?> { null }
internal val LocalDesktopInRootOverlay = staticCompositionLocalOf { false }

@Composable
internal fun DesktopModalPortalHost(state: DesktopModalHostState) {
    for (entry in state.entries) {
        key(entry.id) {
            DesktopDialogModalFrame(
                onDismiss = entry.onDismiss,
                maxWidth = entry.maxWidth,
                content = entry.content,
            )
        }
    }
}

/**
 * The dialog shell: the same card the song menu is drawn on — or, inside Settings, a page of it.
 *
 * [popup] keeps it a card even there, for a prompt a page opens over itself. Popups portal into
 * [LocalDesktopModalHost] so they render outside `overlayBackdrop` across the full window.
 */
@Composable
internal fun DesktopDialogPanel(
    onDismiss: () -> Unit,
    maxWidth: Int,
    popup: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!popup && LocalDesktopPanelIsPage.current) {
        DesktopSettingsPageFrame(
            maxWidth = maxOf(maxWidth.dp, DesktopSettingsPageWidth),
            content = content,
        )
        return
    }
    val modalHost = LocalDesktopModalHost.current
    val inRootOverlay = LocalDesktopInRootOverlay.current
    if (modalHost != null && !inRootOverlay) {
        val entry = remember(modalHost) {
            DesktopModalEntry(
                id = Any(),
                onDismiss = onDismiss,
                maxWidth = maxWidth,
                content = content,
            )
        }
        SideEffect {
            entry.onDismiss = onDismiss
            entry.maxWidth = maxWidth
            entry.content = content
        }
        DisposableEffect(modalHost, entry) {
            modalHost.register(entry)
            onDispose { modalHost.unregister(entry) }
        }
        return
    }
    DesktopDialogModalFrame(
        onDismiss = onDismiss,
        maxWidth = maxWidth,
        content = content,
    )
}

/**
 * Spring entrance motion for anchored popups (context menus, volume pill, sidebar flyouts).
 */
internal fun Modifier.desktopPopupMotion(
    pivotX: Float = 0.5f,
    pivotY: Float = 0f,
    slideXDp: Dp = 0.dp,
    slideYDp: Dp = (-6).dp,
): Modifier = composed {
    val reduceMotion by DesktopAppearanceSettings.reduceAnimation.collectAsState()
    var shown by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(reduceMotion) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMedium)
        },
        label = "desktopPopupMotion",
    )
    val density = LocalDensity.current
    val slideXPx = with(density) { slideXDp.toPx() }
    val slideYPx = with(density) { slideYDp.toPx() }
    graphicsLayer {
        val p = progress.coerceIn(0f, 1.04f)
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.92f + 0.08f * p
        scaleX = scale
        scaleY = scale
        translationX = slideXPx * (1f - p)
        translationY = slideYPx * (1f - p)
        transformOrigin = TransformOrigin(pivotX, pivotY)
    }
}

internal fun desktopModalSurfaceTint(isDark: Boolean): Color = if (isDark) {
    Color(0xFF0C0C10).copy(alpha = 0.84f)
} else {
    Color(0xFFE6E9EF).copy(alpha = 0.88f)
}

@Composable
private fun DesktopDialogModalFrame(
    onDismiss: () -> Unit,
    maxWidth: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion by DesktopAppearanceSettings.reduceAnimation.collectAsState()
    var visible by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(reduceMotion) { visible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 180),
        label = "dialogScrimAlpha",
    )
    val cardProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMedium)
        },
        label = "dialogCardProgress",
    )
    val liftPx = with(LocalDensity.current) { 10.dp.toPx() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(DesktopScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = maxWidth.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = cardProgress.coerceIn(0f, 1.04f)
                    alpha = p.coerceIn(0f, 1f)
                    val s = 0.94f + 0.06f * p
                    scaleX = s
                    scaleY = s
                    translationY = liftPx * (1f - p)
                }
                .shadow(24.dp, shape)
                .clip(shape)
                .desktopBarGlass(
                    shape = shape,
                    surfaceTint = desktopModalSurfaceTint(DesktopIsDark),
                    borderAlpha = if (DesktopIsDark) 0.22f else 0.30f,
                ),
        ) {
            // Behind the content rather than around it, so it swallows clicks on the panel's own
            // background without eating the rows' own.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            Column(
                Modifier.padding(bottom = 4.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                // A prompt opened from a page is a card, and so is anything it opens in turn.
                CompositionLocalProvider(LocalDesktopPanelIsPage provides false) {
                    content()
                }
            }
        }
    }
}
