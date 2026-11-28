package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Optical fidelity tier for Apple/iPhone-style Liquid Glass on desktop:
 * - [LOW]: Lightweight frosted blur without lens refraction.
 * - [MEDIUM]: Balanced curved refraction and subtle prismatic edge dispersion.
 * - [HIGH]: Standard Apple-style deep lens refraction, vibrancy, and crisp specular bevel.
 * - [IMPRESSIVE]: High-res crystal refraction, strong 7-band chromatic dispersion, Fresnel gloss,
 *   and dual-lobe specular catchlight rims.
 */
internal enum class DesktopLiquidGlassLevel(
    val resolutionScale: Float,
    val blurDp: Float,
    val saturation: Float,
    val useRefraction: Boolean,
    val lensHeightDp: Float,
    val lensAmountDp: Float,
    val depthEffect: Float,
    val chromaticAberration: Float,
    val fresnelIntensity: Float,
    val rimBoost: Float,
    val secondaryRimAlpha: Float,
    val surfaceAlphaScale: Float,
) {
    LOW(
        resolutionScale = 0.30f,
        blurDp = 18f,
        saturation = 1.18f,
        useRefraction = false,
        lensHeightDp = 0f,
        lensAmountDp = 0f,
        depthEffect = 0f,
        chromaticAberration = 0f,
        fresnelIntensity = 0f,
        rimBoost = 0.68f,
        secondaryRimAlpha = 0f,
        surfaceAlphaScale = 1.15f,
    ),
    MEDIUM(
        resolutionScale = 0.44f,
        blurDp = 10.5f,
        saturation = 1.38f,
        useRefraction = true,
        lensHeightDp = 24f,
        lensAmountDp = 24f,
        depthEffect = 1.0f,
        chromaticAberration = 0.38f,
        fresnelIntensity = 0.14f,
        rimBoost = 0.85f,
        secondaryRimAlpha = 0.10f,
        surfaceAlphaScale = 1.02f,
    ),
    HIGH(
        resolutionScale = 0.58f,
        blurDp = 9.0f,
        saturation = 1.56f,
        useRefraction = true,
        lensHeightDp = 30f,
        lensAmountDp = 32f,
        depthEffect = 1.35f,
        chromaticAberration = 0.72f,
        fresnelIntensity = 0.20f,
        rimBoost = 1.0f,
        secondaryRimAlpha = 0.16f,
        surfaceAlphaScale = 0.94f,
    ),
    IMPRESSIVE(
        resolutionScale = 0.78f,
        blurDp = 7.5f,
        saturation = 1.80f,
        useRefraction = true,
        lensHeightDp = 38f,
        lensAmountDp = 42f,
        depthEffect = 1.75f,
        chromaticAberration = 1.25f,
        fresnelIntensity = 0.28f,
        rimBoost = 1.15f,
        secondaryRimAlpha = 0.22f,
        surfaceAlphaScale = 0.86f,
    );

    companion object {
        fun stored(raw: String): DesktopLiquidGlassLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HIGH
    }
}

/**
 * Window color appearance mode on desktop (mirrors mobile `ThemeMode`).
 * - [SYSTEM]: Follows the operating system light/dark appearance.
 * - [LIGHT]: Bright Apple Music light theme.
 * - [DARK]: Classic dark theme.
 */
internal enum class DesktopThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun stored(raw: String): DesktopThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

/**
 * Backdrop rendering mode when [DesktopAppearanceSettings.ambientBackdrop] is active:
 * - [MESH]: Diffused multi-cell artwork color mesh.
 * - [FLAT_DOMINANT]: Single flat solid color extracted from the cover's dominant tone.
 */
internal enum class DesktopAmbientBackdropStyle {
    MESH,
    FLAT_DOMINANT;

    companion object {
        fun stored(raw: String): DesktopAmbientBackdropStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MESH
    }
}

/**
 * The toggles read far from the Settings sheet that sets them.
 *
 * Mirrors Android's `AppSettings` for these few: a composable deep in the player or the lyrics
 * pane can collect one without it being threaded down through every caller.
 */
internal object DesktopAppearanceSettings {

    internal const val MIN_LIQUID_GLASS_SCALE = 1
    internal const val MAX_LIQUID_GLASS_SCALE = 10
    internal const val DEFAULT_LIQUID_GLASS_RAINBOW_SCALE = 6
    internal const val DEFAULT_LIQUID_GLASS_OUTLINE_REFLECTION_SCALE = 6

    private fun flag(key: String, default: Boolean) =
        MutableStateFlow(DesktopPersistence().boolean(key, default))

    private val _themeMode = MutableStateFlow(
        DesktopThemeMode.stored(DesktopPersistence().string(KEY_THEME_MODE, DesktopThemeMode.DARK.name)),
    )
    private val _reduceAnimation = flag(KEY_REDUCE_ANIMATION, false)
    private val _reduceDynamicBlur = flag(KEY_REDUCE_DYNAMIC_BLUR, false)
    private val _disableBlendEffects = flag(KEY_DISABLE_BLEND_EFFECTS, false)
    private val _liquidGlass = flag(KEY_LIQUID_GLASS, false)
    private val _liquidGlassLevel = MutableStateFlow(
        DesktopLiquidGlassLevel.stored(DesktopPersistence().string(KEY_LIQUID_GLASS_LEVEL, DesktopLiquidGlassLevel.HIGH.name)),
    )
    private val _liquidGlassRainbowScale = MutableStateFlow(
        DesktopPersistence().int(KEY_LIQUID_GLASS_RAINBOW, DEFAULT_LIQUID_GLASS_RAINBOW_SCALE)
            .coerceIn(MIN_LIQUID_GLASS_SCALE, MAX_LIQUID_GLASS_SCALE),
    )
    private val _liquidGlassOutlineReflectionScale = MutableStateFlow(
        DesktopPersistence().int(KEY_LIQUID_GLASS_OUTLINE_REFLECTION, DEFAULT_LIQUID_GLASS_OUTLINE_REFLECTION_SCALE)
            .coerceIn(MIN_LIQUID_GLASS_SCALE, MAX_LIQUID_GLASS_SCALE),
    )
    private val _glassPerformance = flag(KEY_GLASS_PERFORMANCE, false)
    private val _highPerformanceMode = flag(KEY_HIGH_PERFORMANCE_MODE, false)
    private val _performanceRefreshRate = MutableStateFlow(
        DesktopDisplayPerformance.resolveRefreshRate(
            DesktopPersistence().int(
                KEY_PERFORMANCE_REFRESH_RATE,
                DesktopDisplayPerformance.defaultPreferredRefreshRate(),
            ),
        ),
    )
    private val _hideVolumeBar = flag(KEY_HIDE_VOLUME_BAR, false)
    private val _hideLyricsPreview = flag(KEY_HIDE_LYRICS_PREVIEW, false)
    private val _swipeBackGesture = flag(KEY_SWIPE_BACK_GESTURE, true)
    private val _ambientBackdrop = flag(KEY_AMBIENT_BACKDROP, false)
    private val _ambientBackdropStyle = MutableStateFlow(
        DesktopAmbientBackdropStyle.stored(
            DesktopPersistence().string(KEY_AMBIENT_BACKDROP_STYLE, DesktopAmbientBackdropStyle.MESH.name),
        ),
    )
    private val _smoothTransitions = flag(KEY_SMOOTH_TRANSITIONS, true)
    private val _glassClickAnimations = flag(KEY_GLASS_CLICK_ANIMATIONS, true)

    /** App-wide appearance mode (`System`, `Light`, `Dark`). */
    val themeMode: StateFlow<DesktopThemeMode> = _themeMode

    /** Freezes the main player's gradient instead of drifting. */
    val reduceAnimation: StateFlow<Boolean> = _reduceAnimation

    /** Swaps frosted glass for solid fills across the app. */
    val reduceDynamicBlur: StateFlow<Boolean> = _reduceDynamicBlur

    /** Draws player icons, text, and dark menus in plain white without artwork colour blending. */
    val disableBlendEffects: StateFlow<Boolean> = _disableBlendEffects

    /** Apple-style refracting glass on floating bars and panels (Windows 10+ and Linux/macOS). */
    val liquidGlass: StateFlow<Boolean> = _liquidGlass

    /** Optical fidelity tier while [liquidGlass] is active. */
    val liquidGlassLevel: StateFlow<DesktopLiquidGlassLevel> = _liquidGlassLevel

    /** Custom 1..10 chromatic rainbow dispersion scale while refraction is active. */
    val liquidGlassRainbowScale: StateFlow<Int> = _liquidGlassRainbowScale

    /** Custom 1..10 outline specular catchlight & Fresnel bevel reflection scale. */
    val liquidGlassOutlineReflectionScale: StateFlow<Int> = _liquidGlassOutlineReflectionScale

    /** Uses frosted blur without edge lens refraction to reduce GPU load while [liquidGlass] is on. */
    val glassPerformance: StateFlow<Boolean> = _glassPerformance

    /** Enables full animations & blur and targets the display's higher refresh rate. */
    val highPerformanceMode: StateFlow<Boolean> = _highPerformanceMode

    /** Selected refresh rate target while [highPerformanceMode] is active. */
    val performanceRefreshRate: StateFlow<Int> = _performanceRefreshRate

    /** Removes the volume slider from the main player. */
    val hideVolumeBar: StateFlow<Boolean> = _hideVolumeBar

    /** Hides the live lyric preview strip on the main player screen. */
    val hideLyricsPreview: StateFlow<Boolean> = _hideLyricsPreview

    /** Enables navigating back with mouse Back button or left-to-right two-finger trackpad swipe. */
    val swipeBackGesture: StateFlow<Boolean> = _swipeBackGesture

    /** Diffuses current artwork colors under the window glass chrome like Apple Music. */
    val ambientBackdrop: StateFlow<Boolean> = _ambientBackdrop

    /** Active ambient glow rendering style (`MESH` or `FLAT_DOMINANT`). */
    val ambientBackdropStyle: StateFlow<DesktopAmbientBackdropStyle> = _ambientBackdropStyle

    /** Smooth crossfade and directional slide transitions across desktop pages and segmented tabs. */
    val smoothTransitions: StateFlow<Boolean> = _smoothTransitions

    /** Tactile Liquid Glass squish and catchlight ripple animations when clicking controls. */
    val glassClickAnimations: StateFlow<Boolean> = _glassClickAnimations

    init {
        if (_highPerformanceMode.value) {
            if (_reduceAnimation.value) setReduceAnimation(false)
            if (_reduceDynamicBlur.value) setReduceDynamicBlur(false)
        }
    }

    fun setThemeMode(mode: DesktopThemeMode) {
        DesktopPersistence().saveString(KEY_THEME_MODE, mode.name)
        _themeMode.value = mode
    }

    fun setReduceAnimation(value: Boolean) {
        write(KEY_REDUCE_ANIMATION, value, _reduceAnimation)
        if (value && _highPerformanceMode.value) {
            write(KEY_HIGH_PERFORMANCE_MODE, false, _highPerformanceMode)
        }
    }

    fun setReduceDynamicBlur(value: Boolean) {
        write(KEY_REDUCE_DYNAMIC_BLUR, value, _reduceDynamicBlur)
        if (value && _highPerformanceMode.value) {
            write(KEY_HIGH_PERFORMANCE_MODE, false, _highPerformanceMode)
        }
    }

    fun setDisableBlendEffects(value: Boolean) =
        write(KEY_DISABLE_BLEND_EFFECTS, value, _disableBlendEffects)

    fun setLiquidGlass(value: Boolean) = write(KEY_LIQUID_GLASS, value, _liquidGlass)

    fun setLiquidGlassLevel(level: DesktopLiquidGlassLevel) {
        DesktopPersistence().saveString(KEY_LIQUID_GLASS_LEVEL, level.name)
        _liquidGlassLevel.value = level
    }

    fun setLiquidGlassRainbowScale(scale: Int) {
        val clamped = scale.coerceIn(MIN_LIQUID_GLASS_SCALE, MAX_LIQUID_GLASS_SCALE)
        DesktopPersistence().saveInt(KEY_LIQUID_GLASS_RAINBOW, clamped)
        _liquidGlassRainbowScale.value = clamped
    }

    fun setLiquidGlassOutlineReflectionScale(scale: Int) {
        val clamped = scale.coerceIn(MIN_LIQUID_GLASS_SCALE, MAX_LIQUID_GLASS_SCALE)
        DesktopPersistence().saveInt(KEY_LIQUID_GLASS_OUTLINE_REFLECTION, clamped)
        _liquidGlassOutlineReflectionScale.value = clamped
    }

    fun resetLiquidGlassAdvanced() {
        setLiquidGlassRainbowScale(DEFAULT_LIQUID_GLASS_RAINBOW_SCALE)
        setLiquidGlassOutlineReflectionScale(DEFAULT_LIQUID_GLASS_OUTLINE_REFLECTION_SCALE)
    }

    /**
     * Maps the user's 1..10 rainbow distortion scale and selected fidelity [level] into the SkSL
     * `chromaticAberration` uniform (7-band corner prism split).
     */
    internal fun effectiveChromaticAberration(
        level: DesktopLiquidGlassLevel,
        rainbowScale: Int,
    ): Float {
        if (!level.useRefraction) return 0f
        val clamped = rainbowScale.coerceIn(MIN_LIQUID_GLASS_SCALE, MAX_LIQUID_GLASS_SCALE)
        val norm = (clamped - 1) / 9f
        val curve = 0.08f + Math.pow(norm.toDouble(), 1.25).toFloat() * 1.92f
        val levelWeight = when (level) {
            DesktopLiquidGlassLevel.LOW -> 0f
            DesktopLiquidGlassLevel.MEDIUM -> 0.72f
            DesktopLiquidGlassLevel.HIGH -> 1.0f
            DesktopLiquidGlassLevel.IMPRESSIVE -> 1.32f
        }
        return curve * levelWeight
    }

    /**
     * Maps the user's 1..10 outline reflection scale into a catchlight/Fresnel multiplier.
     */
    internal fun effectiveOutlineReflectionFactor(scale: Int): Float {
        val clamped = scale.coerceIn(MIN_LIQUID_GLASS_SCALE, MAX_LIQUID_GLASS_SCALE)
        val norm = (clamped - 1) / 9f
        return 0.22f + norm * 1.98f
    }

    fun setGlassPerformance(value: Boolean) = write(KEY_GLASS_PERFORMANCE, value, _glassPerformance)

    fun setHighPerformanceMode(value: Boolean) {
        write(KEY_HIGH_PERFORMANCE_MODE, value, _highPerformanceMode)
        if (value) {
            if (_reduceAnimation.value) write(KEY_REDUCE_ANIMATION, false, _reduceAnimation)
            if (_reduceDynamicBlur.value) write(KEY_REDUCE_DYNAMIC_BLUR, false, _reduceDynamicBlur)
        }
    }

    fun setPerformanceRefreshRate(value: Int) {
        val normalized = DesktopDisplayPerformance.resolveRefreshRate(value)
        DesktopPersistence().saveInt(KEY_PERFORMANCE_REFRESH_RATE, normalized)
        _performanceRefreshRate.value = normalized
    }

    fun setHideVolumeBar(value: Boolean) = write(KEY_HIDE_VOLUME_BAR, value, _hideVolumeBar)

    fun setHideLyricsPreview(value: Boolean) = write(KEY_HIDE_LYRICS_PREVIEW, value, _hideLyricsPreview)

    fun setSwipeBackGesture(value: Boolean) = write(KEY_SWIPE_BACK_GESTURE, value, _swipeBackGesture)

    fun setAmbientBackdrop(value: Boolean) = write(KEY_AMBIENT_BACKDROP, value, _ambientBackdrop)

    fun setAmbientBackdropStyle(style: DesktopAmbientBackdropStyle) {
        DesktopPersistence().saveString(KEY_AMBIENT_BACKDROP_STYLE, style.name)
        _ambientBackdropStyle.value = style
    }

    fun setSmoothTransitions(value: Boolean) = write(KEY_SMOOTH_TRANSITIONS, value, _smoothTransitions)

    fun setGlassClickAnimations(value: Boolean) = write(KEY_GLASS_CLICK_ANIMATIONS, value, _glassClickAnimations)

    private fun write(key: String, value: Boolean, into: MutableStateFlow<Boolean>) {
        DesktopPersistence().saveBoolean(key, value)
        into.value = value
    }

    internal const val KEY_THEME_MODE = "theme_mode"
    internal const val KEY_REDUCE_ANIMATION = "reduce_animation"
    internal const val KEY_REDUCE_DYNAMIC_BLUR = "reduce_dynamic_blur"
    internal const val KEY_DISABLE_BLEND_EFFECTS = "disable_blend_effects"
    internal const val KEY_LIQUID_GLASS = "liquid_glass"
    internal const val KEY_LIQUID_GLASS_LEVEL = "liquid_glass_level"
    internal const val KEY_LIQUID_GLASS_RAINBOW = "liquid_glass_rainbow_scale"
    internal const val KEY_LIQUID_GLASS_OUTLINE_REFLECTION = "liquid_glass_outline_reflection_scale"
    internal const val KEY_GLASS_PERFORMANCE = "glass_performance"
    internal const val KEY_HIGH_PERFORMANCE_MODE = "high_performance_mode"
    internal const val KEY_PERFORMANCE_REFRESH_RATE = "performance_refresh_rate"
    internal const val KEY_HIDE_VOLUME_BAR = "hide_volume_bar"
    internal const val KEY_HIDE_LYRICS_PREVIEW = "hide_lyrics_preview"
    internal const val KEY_SWIPE_BACK_GESTURE = "swipe_back_gesture"
    internal const val KEY_AMBIENT_BACKDROP = "ambient_backdrop"
    internal const val KEY_AMBIENT_BACKDROP_STYLE = "ambient_backdrop_style"
    internal const val KEY_SMOOTH_TRANSITIONS = "smooth_transitions"
    internal const val KEY_GLASS_CLICK_ANIMATIONS = "glass_click_animations"
}
