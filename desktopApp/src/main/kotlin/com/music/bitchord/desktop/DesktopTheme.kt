package com.music.bitchord.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic desktop theme palette mirroring mobile's `DarkColors` / `LightColors`.
 * Provided at the root via [LocalDesktopPalette] so both standard Material components and custom
 * frosted/Liquid Glass surfaces adapt cleanly across Light and Dark modes.
 */
@Immutable
internal data class DesktopPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glass: Color,
    val glassStrong: Color,
    val barGlass: Color,
    val barEdge: Color,
    val accent: Color,
    val onAccent: Color,
    val ink: Color,
    val secondary: Color,
    val divider: Color,
    val cardFill: Color,
    val cardInsetFill: Color,
    val cardEdge: Color,
    val rowHover: Color,
    val scrim: Color,
    val settingsCardGlassBase: Color,
    val settingsCardSolid: Color,
    val liquidGlassTint: Color,
    val warningIcon: Color,
    val warningText: Color,
)

internal val DesktopDarkPalette = DesktopPalette(
    isDark = true,
    background = Color(0xFF202020),
    surface = Color(0xFF0D0D0F),
    surfaceRaised = Color(0xFF1C1C1E),
    glass = Color(0x661C1C1E),
    glassStrong = Color(0xCC0D0D0F),
    barGlass = Color(0xB324242A),
    barEdge = Color(0x26FFFFFF),
    accent = Color.White,
    onAccent = Color.Black,
    ink = Color.White,
    secondary = Color(0xFFA0A0A8),
    divider = Color(0xFF2C2C2E),
    cardFill = Color(0xFA1A1A1D),
    cardInsetFill = Color(0x18FFFFFF),
    cardEdge = Color(0x24FFFFFF),
    rowHover = Color(0x1AFFFFFF),
    scrim = Color(0xBA000000),
    settingsCardGlassBase = Color(0xFF141418),
    settingsCardSolid = Color(0xFF1C1C1E),
    liquidGlassTint = Color(0xFF141418),
    warningIcon = Color(0xFFFFCC80),
    warningText = Color(0xFFFFE0B2),
)

internal val DesktopLightPalette = DesktopPalette(
    isDark = false,
    background = Color.White,
    surface = Color(0xFFF8F8FA),
    surfaceRaised = Color(0xFFF1F2F6),
    glass = Color(0x5CEFF1F5),
    glassStrong = Color(0xD8FFFFFF),
    barGlass = Color(0x6EFFFFFF),
    barEdge = Color(0x18000000),
    accent = Color.Black,
    onAccent = Color.White,
    ink = Color(0xFF0A0A0C),
    secondary = Color(0xFF4C4C52),
    divider = Color(0xFFE5E5EA),
    cardFill = Color(0xEBFFFFFF),
    cardInsetFill = Color(0x10000000),
    cardEdge = Color(0x20000000),
    rowHover = Color(0x0A000000),
    scrim = Color(0x8A000000),
    settingsCardGlassBase = Color(0xFFFFFFFF),
    settingsCardSolid = Color(0xFFF2F2F7),
    liquidGlassTint = Color.White,
    warningIcon = Color(0xFFC45500),
    warningText = Color(0xFF7A3A00),
)

internal val LocalDesktopPalette = staticCompositionLocalOf { DesktopDarkPalette }

internal fun resolveDarkTheme(mode: DesktopThemeMode, systemDark: Boolean): Boolean = when (mode) {
    DesktopThemeMode.SYSTEM -> systemDark
    DesktopThemeMode.LIGHT -> false
    DesktopThemeMode.DARK -> true
}

internal fun desktopColorSchemeFor(isDark: Boolean): ColorScheme = if (isDark) {
    val p = DesktopDarkPalette
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = Color.White.copy(alpha = 0.16f),
        onPrimaryContainer = Color.White,
        background = p.background,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.secondary,
        outline = p.divider,
        outlineVariant = p.divider,
        surfaceTint = p.accent,
    )
} else {
    val p = DesktopLightPalette
    lightColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = Color.Black.copy(alpha = 0.08f),
        onPrimaryContainer = Color.Black,
        background = p.background,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.secondary,
        outline = p.divider,
        outlineVariant = p.divider,
        surfaceTint = p.accent,
    )
}

internal val DesktopInk: Color
    @Composable get() = LocalDesktopPalette.current.ink

internal val DesktopOnAccent: Color
    @Composable get() = LocalDesktopPalette.current.onAccent

internal val DesktopIsDark: Boolean
    @Composable get() = LocalDesktopPalette.current.isDark
