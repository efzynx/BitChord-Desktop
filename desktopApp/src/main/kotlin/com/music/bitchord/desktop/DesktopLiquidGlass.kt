package com.music.bitchord.desktop

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.PI
import kotlin.math.ceil

/** Backdrop layer sampled by floating overlays, popups, dialogs, and menus. */
internal val LocalDesktopBackdrop = staticCompositionLocalOf<DesktopLayerBackdrop?> { null }

/** Backdrop layer for content-area floating bars (bottom player bar and trailing side panel). */
internal val LocalDesktopContentBackdrop = staticCompositionLocalOf<DesktopLayerBackdrop?> { null }

/** Backdrop layer for edge window chrome (left sidebar over page ambient mesh). */
internal val LocalDesktopChromeBackdrop = staticCompositionLocalOf<DesktopLayerBackdrop?> { null }

/** Apple-matched Liquid Glass constants (ported cleanly to Skia/Skiko desktop rendering). */
private const val LIQUID_FROST_BLUR_DP = 16f
private const val LIQUID_FROST_SCALE = 0.26f
private const val LIQUID_SURFACE_ALPHA = 0.42f
private const val LIQUID_HIGHLIGHT_ANGLE_RAD = (-135.0 * PI / 180.0).toFloat()
private const val LIQUID_SECONDARY_HIGHLIGHT_ANGLE_RAD = (45.0 * PI / 180.0).toFloat()

internal object DesktopLiquidGlassShaders {
    private const val ROUNDED_RECT_SDF_SKSL = """
        float radiusAt(float2 coord, float4 radii) {
            if (coord.x >= 0.0) {
                if (coord.y <= 0.0) return radii.y;
                else return radii.z;
            } else {
                if (coord.y <= 0.0) return radii.x;
                else return radii.w;
            }
        }

        float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            float outside = length(max(cornerCoord, 0.0)) - radius;
            float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
            return outside + inside;
        }

        float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                float2 clamped = max(cornerCoord, 0.0);
                float len = length(clamped);
                if (len > 0.0001) {
                    return sign(coord) * (clamped / len);
                }
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            } else {
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            }
        }
    """

    private const val REFRACTION_DISPERSION_SKSL = """
        uniform shader content;
        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform float chromaticAberration;
        uniform float fresnelIntensity;

        $ROUNDED_RECT_SDF_SKSL

        float circleMap(float x) {
            return 1.0 - sqrt(clamp(1.0 - x * x, 0.0, 1.0));
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(centeredCoord, cornerRadii);

            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            float2 centerLens = -clamp(depthEffect, 0.0, 2.0) * 0.014 * centeredCoord;
            if (-sd >= refractionHeight) {
                return content.eval(coord + centerLens);
            }
            sd = min(sd, 0.0);

            float edgeT = clamp(1.0 - (-sd / max(refractionHeight, 0.001)), 0.0, 1.0);
            float curveT = mix(circleMap(edgeT), edgeT * edgeT, 0.25);
            float d = curveT * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float cLen = length(centeredCoord);
            float2 radial = cLen > 0.0001 ? (centeredCoord / cLen) : float2(0.0);
            float2 rawGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * radial;
            float gLen = length(rawGrad);
            float2 grad = gLen > 0.0001 ? (rawGrad / gLen) : float2(0.0);

            float2 refractedCoord = coord + centerLens * (1.0 - edgeT) + d * grad;
            float safeArea = max(halfSize.x * halfSize.y, 1.0);
            float2 normCoord = centeredCoord / max(halfSize, float2(1.0));
            float cornerFactor = (centeredCoord.x * centeredCoord.y) / safeArea;
            float rimGrazing = (normCoord.x + normCoord.y) * 0.45;
            float dispersionIntensity = chromaticAberration * mix(rimGrazing, cornerFactor, 0.62);
            float2 dispersedCoord = d * grad * dispersionIntensity;

            half4 color = half4(0.0);

            half4 red = content.eval(refractedCoord + dispersedCoord);
            color.r += red.r / 3.5;
            color.a += red.a / 7.0;

            half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
            color.r += orange.r / 3.5;
            color.g += orange.g / 7.0;
            color.a += orange.a / 7.0;

            half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
            color.r += yellow.r / 3.5;
            color.g += yellow.g / 3.5;
            color.a += yellow.a / 7.0;

            half4 green = content.eval(refractedCoord);
            color.g += green.g / 3.5;
            color.a += green.a / 7.0;

            half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
            color.g += cyan.g / 3.5;
            color.b += cyan.b / 3.0;
            color.a += cyan.a / 7.0;

            half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
            color.b += blue.b / 3.0;
            color.a += blue.a / 7.0;

            half4 purple = content.eval(refractedCoord - dispersedCoord);
            color.r += purple.r / 7.0;
            color.b += purple.b / 3.0;
            color.a += purple.a / 7.0;

            if (fresnelIntensity > 0.001) {
                float directional = dot(grad, float2(-0.7071, -0.7071));
                float topLight = clamp(directional, 0.0, 1.0);
                float bottomShadow = clamp(-directional, 0.0, 1.0);
                float embossBody = smoothstep(0.0, 0.85, edgeT) * (1.0 - 0.25 * edgeT);
                float fresnel = pow(edgeT, 2.6) * fresnelIntensity * 0.72;
                float lightCurve = 0.20 + 0.80 * smoothstep(-0.2, 0.9, directional);
                float bevelSheen = fresnel * lightCurve + fresnelIntensity * 0.16 * topLight * embossBody;
                color.rgb *= half(1.0 - min(0.16 * fresnelIntensity, 0.24) * bottomShadow * edgeT);
                color.rgb += half3(0.94, 0.97, 1.0) * half(bevelSheen * color.a);
            }

            return color;
        }
    """

    private const val SPECULAR_HIGHLIGHT_SKSL = """
        uniform float2 size;
        uniform float4 cornerRadii;
        uniform float4 color;
        uniform float angle;
        uniform float falloff;

        $ROUNDED_RECT_SDF_SKSL

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = coord - halfSize;
            float radius = radiusAt(centeredCoord, cornerRadii);

            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
            float2 normal = float2(cos(angle), sin(angle));
            float d = clamp(dot(grad, normal), 0.0, 1.0);
            float intensity = pow(smoothstep(0.05, 1.0, d), falloff);
            return half4(color) * intensity;
        }
    """

    val refractionDispersionEffect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(REFRACTION_DISPERSION_SKSL) }.getOrNull()
    }

    val specularHighlightEffect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(SPECULAR_HIGHLIGHT_SKSL) }.getOrNull()
    }
}

@Composable
internal fun rememberDesktopLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = { drawContent() },
): DesktopLayerBackdrop = remember(graphicsLayer) {
    DesktopLayerBackdrop(graphicsLayer, onDraw)
}.also {
    it.onDraw = onDraw
}

@Stable
internal class DesktopLayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal var onDraw: ContentDrawScope.() -> Unit,
) {
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var hostNode: DesktopLayerBackdropNode? = null
    @kotlin.jvm.Volatile
    internal var hasRecorded: Boolean = false
    @kotlin.jvm.Volatile
    internal var isRecording: Boolean = false
    private val consumers = LinkedHashSet<DesktopLiquidGlassNode>()

    internal val hasActiveConsumers: Boolean
        get() = consumers.isNotEmpty()

    internal fun registerConsumer(node: DesktopLiquidGlassNode) {
        val wasEmpty = consumers.isEmpty()
        consumers.add(node)
        if (wasEmpty) {
            hostNode?.invalidateDraw()
        }
    }

    internal fun unregisterConsumer(node: DesktopLiquidGlassNode) {
        consumers.remove(node)
        if (consumers.isEmpty()) {
            hasRecorded = false
            hostNode?.invalidateDraw()
        }
    }

    internal fun notifyConsumers(firstFrame: Boolean) {
        if (consumers.isEmpty()) return
        for (consumer in consumers) {
            if (!consumer.isSelfContained() && (firstFrame || consumer.isDetachedFromBackdrop(this))) {
                consumer.invalidateDraw()
            }
        }
    }
}

internal fun Modifier.desktopLayerBackdrop(
    backdrop: DesktopLayerBackdrop,
    enabled: Boolean = true,
): Modifier = if (!enabled) this else this then DesktopLayerBackdropElement(backdrop)

private class DesktopLayerBackdropElement(
    val backdrop: DesktopLayerBackdrop,
) : ModifierNodeElement<DesktopLayerBackdropNode>() {
    override fun create() = DesktopLayerBackdropNode(backdrop)
    override fun update(node: DesktopLayerBackdropNode) {
        if (node.backdrop != backdrop) {
            if (node.backdrop.hostNode === node) node.backdrop.hostNode = null
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
            backdrop.hostNode = node
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "desktopLayerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is DesktopLayerBackdropElement && other.backdrop == backdrop)

    override fun hashCode(): Int = backdrop.hashCode()
}

internal class DesktopLayerBackdropNode(
    var backdrop: DesktopLayerBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {
    override fun onAttach() {
        backdrop.hostNode = this
    }

    override fun ContentDrawScope.draw() {
        if (size.width < 1f || size.height < 1f || !backdrop.hasActiveConsumers) {
            backdrop.hasRecorded = false
            drawContent()
            return
        }
        val density: Density = this
        val firstFrame = !backdrop.hasRecorded
        backdrop.isRecording = true
        try {
            backdrop.graphicsLayer.record(size.toIntSize()) {
                val prevDensity = drawContext.density
                drawContext.density = density
                try {
                    backdrop.onDraw(this@draw)
                } finally {
                    drawContext.density = prevDensity
                }
            }
        } finally {
            backdrop.isRecording = false
        }
        backdrop.hasRecorded = true
        drawLayer(backdrop.graphicsLayer)
        backdrop.notifyConsumers(firstFrame)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        if (backdrop.hostNode === this) backdrop.hostNode = null
        backdrop.layerCoordinates = null
        backdrop.hasRecorded = false
    }
}

/**
 * Renders an Apple/iPhone-style Liquid Glass surface over [backdrop]:
 * 1) Captures underlying backdrop pixels into a downscaled layer.
 * 2) Applies vibrancy saturation boost, Gaussian blur, and SkSL curved-edge refraction with chromatic dispersion.
 * 3) Composites a translucent glass tint and directional specular edge catchlight.
 */
@Composable
internal fun Modifier.desktopLiquidGlass(
    shape: Shape,
    backdrop: DesktopLayerBackdrop? = LocalDesktopBackdrop.current,
    underlayBackdrop: DesktopLayerBackdrop? = null,
    surfaceTint: Color? = null,
    tintAlpha: Float = LIQUID_SURFACE_ALPHA,
    borderAlpha: Float = 0.16f,
): Modifier {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val level by DesktopAppearanceSettings.liquidGlassLevel.collectAsState()
    val rainbowScale by DesktopAppearanceSettings.liquidGlassRainbowScale.collectAsState()
    val outlineReflectionScale by DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassPerformance by DesktopAppearanceSettings.glassPerformance.collectAsState()
    val disableBlendEffects by DesktopAppearanceSettings.disableBlendEffects.collectAsState()
    val palette = LocalDesktopPalette.current
    val isDark = palette.isDark

    if (!liquidGlass || !DesktopPlatform.supportsLiquidGlass || reduceDynamicBlur) {
        return this
    }
    val hasRefraction = !glassPerformance && level.useRefraction
    val themeAlphaFactor = when {
        isDark -> 0.92f
        hasRefraction -> 0.38f
        else -> 0.54f
    }
    val effectiveAlpha = (tintAlpha * level.surfaceAlphaScale * themeAlphaFactor).coerceIn(0f, 0.92f)
    val tint = surfaceTint?.copy(alpha = (surfaceTint.alpha * level.surfaceAlphaScale).coerceIn(0f, 0.92f))
        ?: palette.liquidGlassTint.copy(alpha = effectiveAlpha)
    return this then DesktopLiquidGlassElement(
        backdrop = backdrop,
        underlayBackdrop = underlayBackdrop,
        shape = shape,
        surfaceTint = tint,
        borderAlpha = borderAlpha,
        level = level,
        rainbowScale = rainbowScale,
        outlineReflectionScale = outlineReflectionScale,
        isDark = isDark,
        glassPerformance = glassPerformance,
        disableBlendEffects = disableBlendEffects,
    )
}

/**
 * Specular rim highlight + subtle glass border for detached popup windows where [LocalDesktopBackdrop]
 * belongs to a different coordinate tree.
 */
@Composable
internal fun Modifier.desktopLiquidGlassSpecularRim(
    shape: Shape,
    borderAlpha: Float = 0.16f,
): Modifier {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val level by DesktopAppearanceSettings.liquidGlassLevel.collectAsState()
    val outlineReflectionScale by DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val isDark = DesktopIsDark
    if (!liquidGlass || !DesktopPlatform.supportsLiquidGlass || reduceDynamicBlur) return this
    return this then DesktopSpecularRimElement(
        shape = shape,
        borderAlpha = borderAlpha,
        level = level,
        outlineReflectionScale = outlineReflectionScale,
        isDark = isDark,
    )
}

private class DesktopSpecularRimElement(
    val shape: Shape,
    val borderAlpha: Float,
    val level: DesktopLiquidGlassLevel,
    val outlineReflectionScale: Int,
    val isDark: Boolean,
) : ModifierNodeElement<DesktopSpecularRimNode>() {
    override fun create() = DesktopSpecularRimNode(shape, borderAlpha, level, outlineReflectionScale, isDark)
    override fun update(node: DesktopSpecularRimNode) {
        node.shape = shape
        node.borderAlpha = borderAlpha
        node.level = level
        node.outlineReflectionScale = outlineReflectionScale
        node.isDark = isDark
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "desktopLiquidGlassSpecularRim"
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is DesktopSpecularRimElement &&
                other.shape == shape &&
                other.borderAlpha == borderAlpha &&
                other.level == level &&
                other.outlineReflectionScale == outlineReflectionScale &&
                other.isDark == isDark
            )

    override fun hashCode(): Int {
        var r = shape.hashCode()
        r = 31 * r + borderAlpha.hashCode()
        r = 31 * r + level.hashCode()
        r = 31 * r + outlineReflectionScale
        r = 31 * r + isDark.hashCode()
        return r
    }
}

private class DesktopSpecularRimNode(
    var shape: Shape,
    var borderAlpha: Float,
    var level: DesktopLiquidGlassLevel,
    var outlineReflectionScale: Int,
    var isDark: Boolean,
) : DrawModifierNode, Modifier.Node() {
    override fun ContentDrawScope.draw() {
        drawContent()
        drawLiquidGlassRim(
            shape = shape,
            borderAlpha = borderAlpha,
            level = level,
            outlineReflectionScale = outlineReflectionScale,
            isDark = isDark,
        )
    }
}

private class DesktopLiquidGlassElement(
    val backdrop: DesktopLayerBackdrop?,
    val underlayBackdrop: DesktopLayerBackdrop?,
    val shape: Shape,
    val surfaceTint: Color,
    val borderAlpha: Float,
    val level: DesktopLiquidGlassLevel,
    val rainbowScale: Int,
    val outlineReflectionScale: Int,
    val isDark: Boolean,
    val glassPerformance: Boolean,
    val disableBlendEffects: Boolean,
) : ModifierNodeElement<DesktopLiquidGlassNode>() {
    override fun create() = DesktopLiquidGlassNode(
        backdrop = backdrop,
        underlayBackdrop = underlayBackdrop,
        shape = shape,
        surfaceTint = surfaceTint,
        borderAlpha = borderAlpha,
        level = level,
        rainbowScale = rainbowScale,
        outlineReflectionScale = outlineReflectionScale,
        isDark = isDark,
        glassPerformance = glassPerformance,
        disableBlendEffects = disableBlendEffects,
    )

    override fun update(node: DesktopLiquidGlassNode) {
        node.updateBackdrops(backdrop, underlayBackdrop)
        node.shape = shape
        node.surfaceTint = surfaceTint
        node.borderAlpha = borderAlpha
        node.level = level
        node.rainbowScale = rainbowScale
        node.outlineReflectionScale = outlineReflectionScale
        node.isDark = isDark
        node.glassPerformance = glassPerformance
        node.disableBlendEffects = disableBlendEffects
        node.invalidateFilter()
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "desktopLiquidGlass"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DesktopLiquidGlassElement) return false
        return backdrop == other.backdrop &&
            underlayBackdrop == other.underlayBackdrop &&
            shape == other.shape &&
            surfaceTint == other.surfaceTint &&
            borderAlpha == other.borderAlpha &&
            level == other.level &&
            rainbowScale == other.rainbowScale &&
            outlineReflectionScale == other.outlineReflectionScale &&
            isDark == other.isDark &&
            glassPerformance == other.glassPerformance &&
            disableBlendEffects == other.disableBlendEffects
    }

    override fun hashCode(): Int {
        var r = backdrop?.hashCode() ?: 0
        r = 31 * r + (underlayBackdrop?.hashCode() ?: 0)
        r = 31 * r + shape.hashCode()
        r = 31 * r + surfaceTint.hashCode()
        r = 31 * r + borderAlpha.hashCode()
        r = 31 * r + level.hashCode()
        r = 31 * r + rainbowScale
        r = 31 * r + outlineReflectionScale
        r = 31 * r + isDark.hashCode()
        r = 31 * r + glassPerformance.hashCode()
        r = 31 * r + disableBlendEffects.hashCode()
        return r
    }
}

internal class DesktopLiquidGlassNode(
    var backdrop: DesktopLayerBackdrop?,
    var underlayBackdrop: DesktopLayerBackdrop?,
    var shape: Shape,
    var surfaceTint: Color,
    var borderAlpha: Float,
    var level: DesktopLiquidGlassLevel,
    var rainbowScale: Int,
    var outlineReflectionScale: Int,
    var isDark: Boolean,
    var glassPerformance: Boolean,
    var disableBlendEffects: Boolean,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private var glassLayer: GraphicsLayer? = null
    private var coords: LayoutCoordinates? = null
    private var clipPath: Path? = null
    private var retainedBackdrop: DesktopLayerBackdrop? = null
    private var retainedUnderlay: DesktopLayerBackdrop? = null
    private var selfContainedBackdrop: DesktopLayerBackdrop? = null
    private var selfContainedUnderlay: DesktopLayerBackdrop? = null
    private var detachedTree = false
    private var blockedBySelfRecursion = false

    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private var cachedLevel: DesktopLiquidGlassLevel? = null
    private var cachedRainbow = -1
    private var cachedReflection = -1
    private var cachedIsDark = true
    private var cachedPerf = false
    private var cachedPlain = false
    private var cachedShape: Shape? = null
    private var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun invalidateFilter() {
        cachedWidth = -1f
        cachedEffect = null
    }

    fun updateBackdrops(next: DesktopLayerBackdrop?, nextUnderlay: DesktopLayerBackdrop?) {
        if (backdrop !== next || retainedBackdrop !== next) {
            retainedBackdrop?.unregisterConsumer(this)
            backdrop = next
            retainedBackdrop = next
            if (selfContainedBackdrop !== next) selfContainedBackdrop = null
            if (isAttached) next?.registerConsumer(this)
        }
        val effectiveUnderlay = nextUnderlay?.takeIf { it !== next }
        if (underlayBackdrop !== nextUnderlay || retainedUnderlay !== effectiveUnderlay) {
            retainedUnderlay?.unregisterConsumer(this)
            underlayBackdrop = nextUnderlay
            retainedUnderlay = effectiveUnderlay
            if (selfContainedUnderlay !== effectiveUnderlay) selfContainedUnderlay = null
            if (isAttached) effectiveUnderlay?.registerConsumer(this)
        }
    }

    internal fun isSelfContained(): Boolean =
        blockedBySelfRecursion ||
            (backdrop != null && backdrop === selfContainedBackdrop) ||
            (underlayBackdrop != null && underlayBackdrop === selfContainedUnderlay)

    internal fun isDetachedFromBackdrop(target: DesktopLayerBackdrop): Boolean =
        !isSelfContained() && (target === backdrop || target === underlayBackdrop) && detachedTree

    override fun onAttach() {
        glassLayer = requireGraphicsContext().createGraphicsLayer()
        backdrop?.let {
            retainedBackdrop = it
            it.registerConsumer(this)
        }
        underlayBackdrop?.takeIf { it !== backdrop }?.let {
            retainedUnderlay = it
            it.registerConsumer(this)
        }
    }

    override fun onDetach() {
        coords = null
        detachedTree = false
        blockedBySelfRecursion = false
        selfContainedBackdrop = null
        selfContainedUnderlay = null
        retainedBackdrop?.unregisterConsumer(this)
        retainedBackdrop = null
        retainedUnderlay?.unregisterConsumer(this)
        retainedUnderlay = null
        glassLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        glassLayer = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            coords = coordinates
        }
    }

    private fun resolveOffset(activeBackdrop: DesktopLayerBackdrop): Offset? {
        val layerCoords = activeBackdrop.layerCoordinates ?: return null
        val myCoords = coords ?: return null
        if (!layerCoords.isAttached || !myCoords.isAttached) return null
        val inSameTree = runCatching {
            detachedTree = false
            layerCoords.localPositionOf(myCoords, Offset.Zero)
        }.getOrNull()
        if (inSameTree != null) return inSameTree
        detachedTree = true
        return runCatching {
            myCoords.positionInWindow() - layerCoords.positionInWindow()
        }.getOrNull()
    }

    private fun DrawScope.ensureRenderEffect(scale: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (cachedWidth == size.width &&
            cachedHeight == size.height &&
            cachedLevel == level &&
            cachedRainbow == rainbowScale &&
            cachedReflection == outlineReflectionScale &&
            cachedIsDark == isDark &&
            cachedPerf == glassPerformance &&
            cachedPlain == disableBlendEffects &&
            cachedShape == shape &&
            cachedEffect != null
        ) {
            return cachedEffect
        }
        val density: Density = this
        val useRefraction = !glassPerformance && level.useRefraction
        val blurDp = if (glassPerformance) LIQUID_FROST_BLUR_DP else level.blurDp
        val blurSigma = with(density) { blurDp.dp.toPx() } * scale * 0.5f

        val saturationFilter: ImageFilter? = if (!disableBlendEffects) {
            val matrix = ColorMatrix().apply { setToSaturation(level.saturation) }
            val skiaColorFilter = ColorMatrixColorFilter(matrix).asSkiaColorFilter()
            ImageFilter.makeColorFilter(skiaColorFilter, null, null)
        } else {
            null
        }

        val blurFilter = ImageFilter.makeBlur(
            sigmaX = blurSigma,
            sigmaY = blurSigma,
            mode = FilterTileMode.CLAMP,
            input = saturationFilter,
            crop = null,
        )

        val effectiveChromatic = DesktopAppearanceSettings.effectiveChromaticAberration(level, rainbowScale)
        val reflectionFactor = DesktopAppearanceSettings.effectiveOutlineReflectionFactor(outlineReflectionScale)
        val effectiveFresnel = if (disableBlendEffects) {
            0f
        } else {
            val fresnelBase = if (level.fresnelIntensity > 0.001f) level.fresnelIntensity else 0.08f
            (fresnelBase * reflectionFactor).coerceIn(0f, 0.65f)
        }

        val finalFilter = if (useRefraction) {
            val effect = DesktopLiquidGlassShaders.refractionDispersionEffect
            if (effect != null) {
                val scaledW = size.width * scale
                val scaledH = size.height * scale
                val radii = extractCornerRadii(shape, size, this, layoutDirection)
                    .map { it * scale }
                    .toFloatArray()
                val minHalfDp = with(density) { (minOf(size.width, size.height) * 0.5f).toDp().value }
                val adaptiveHeightDp = maxOf(
                    level.lensHeightDp,
                    minOf(minHalfDp * 0.36f, level.lensHeightDp * 1.75f),
                )
                val heightRatio = adaptiveHeightDp / level.lensHeightDp.coerceAtLeast(1f)
                val adaptiveAmountDp = level.lensAmountDp * (1f + 0.14f * (heightRatio - 1f))
                val lensH = with(density) { adaptiveHeightDp.dp.toPx() } * scale
                val lensA = with(density) { adaptiveAmountDp.dp.toPx() } * scale
                runCatching {
                    val builder = RuntimeShaderBuilder(effect).apply {
                        uniform("size", scaledW, scaledH)
                        uniform("offset", 0f, 0f)
                        uniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                        uniform("refractionHeight", lensH)
                        uniform("refractionAmount", -lensA)
                        uniform("depthEffect", level.depthEffect)
                        uniform("chromaticAberration", effectiveChromatic)
                        uniform("fresnelIntensity", effectiveFresnel)
                    }
                    ImageFilter.makeRuntimeShader(builder, "content", blurFilter)
                }.getOrDefault(blurFilter)
            } else {
                blurFilter
            }
        } else {
            blurFilter
        }

        return finalFilter.asComposeRenderEffect().also {
            cachedWidth = size.width
            cachedHeight = size.height
            cachedLevel = level
            cachedRainbow = rainbowScale
            cachedReflection = outlineReflectionScale
            cachedIsDark = isDark
            cachedPerf = glassPerformance
            cachedPlain = disableBlendEffects
            cachedShape = shape
            cachedEffect = it
        }
    }

    override fun ContentDrawScope.draw() {
        val activeBackdrop = backdrop
        val underBackdrop = underlayBackdrop
        // Guard against re-entrant drawLayer() when a consumer sits inside a currently recording
        // backdrop layer, and remember that relationship so follow-up invalidations never form a
        // recursive GraphicsLayer/SkPicture cycle.
        if (activeBackdrop?.isRecording == true) selfContainedBackdrop = activeBackdrop
        if (underBackdrop?.isRecording == true) selfContainedUnderlay = underBackdrop
        blockedBySelfRecursion =
            (activeBackdrop != null && (activeBackdrop.isRecording || activeBackdrop === selfContainedBackdrop)) ||
            (underBackdrop != null && (underBackdrop.isRecording || underBackdrop === selfContainedUnderlay))

        val safeActive = activeBackdrop?.takeIf {
            !it.isRecording && it.hasRecorded && it !== selfContainedBackdrop
        }
        val safeBase = underBackdrop?.takeIf {
            it !== safeActive && !it.isRecording && it.hasRecorded && it !== selfContainedUnderlay
        }
        val drawBackdrop = safeActive ?: safeBase
        val secondaryBackdrop = if (safeActive != null) safeBase else null

        val layer = glassLayer
        val offset = if (drawBackdrop != null && layer != null) resolveOffset(drawBackdrop) else null

        val outline = shape.createOutline(size, layoutDirection, this)
        val path = if (outline is Outline.Rounded || outline is Outline.Generic) {
            clipPath ?: Path().also { clipPath = it }
        } else {
            null
        }

        val secondaryOffset = if (secondaryBackdrop != null && layer != null) resolveOffset(secondaryBackdrop) else null

        if (drawBackdrop != null && layer != null && offset != null && size.width >= 1f && size.height >= 1f) {
            val scale = if (glassPerformance) LIQUID_FROST_SCALE else level.resolutionScale
            val recSize = IntSize(
                ceil(size.width * scale).toInt().coerceAtLeast(1),
                ceil(size.height * scale).toInt().coerceAtLeast(1),
            )
            layer.renderEffect = ensureRenderEffect(scale)
            val density: Density = this
            layer.record(recSize) {
                val prevDensity = drawContext.density
                drawContext.density = density
                try {
                    val canvas = drawContext.canvas
                    canvas.save()
                    canvas.scale(scale, scale)
                    if (secondaryBackdrop != null && secondaryOffset != null) {
                        canvas.save()
                        canvas.translate(-secondaryOffset.x, -secondaryOffset.y)
                        drawLayer(secondaryBackdrop.graphicsLayer)
                        canvas.restore()
                    }
                    canvas.translate(-offset.x, -offset.y)
                    drawLayer(drawBackdrop.graphicsLayer)
                    canvas.restore()
                } finally {
                    drawContext.density = prevDensity
                }
            }

            val canvas = drawContext.canvas
            canvas.save()
            clipToOutline(outline, path)
            scale(1f / scale, pivot = Offset.Zero) {
                drawLayer(layer)
            }
            drawOutline(outline = outline, color = surfaceTint)
            drawEmbossVolume(outline)
            canvas.restore()
        } else {
            val canvas = drawContext.canvas
            canvas.save()
            clipToOutline(outline, path)
            drawOutline(outline = outline, color = surfaceTint)
            drawEmbossVolume(outline)
            canvas.restore()
        }

        drawContent()
        drawLiquidGlassRim(
            shape = shape,
            borderAlpha = borderAlpha,
            level = level,
            outlineReflectionScale = outlineReflectionScale,
            isDark = isDark,
            precomputedOutline = outline,
            clipPath = path,
        )
    }

    private fun DrawScope.drawEmbossVolume(outline: Outline) {
        if (size.width < 1f || size.height < 1f) return
        val reflectionFactor = DesktopAppearanceSettings.effectiveOutlineReflectionFactor(outlineReflectionScale)
        val baseSheen = when (level) {
            DesktopLiquidGlassLevel.LOW -> 0.045f
            DesktopLiquidGlassLevel.MEDIUM -> 0.06f
            DesktopLiquidGlassLevel.HIGH -> 0.075f
            DesktopLiquidGlassLevel.IMPRESSIVE -> 0.09f
        }
        val topSheen = if (isDark) {
            (baseSheen * (0.55f + 0.45f * reflectionFactor)).coerceIn(0f, 0.19f)
        } else {
            // Subtle glossy specular glaze in Light mode without chalky opaque wash.
            (baseSheen * 0.85f * (0.55f + 0.45f * reflectionFactor)).coerceIn(0f, 0.11f)
        }
        val bottomShadow = if (isDark) {
            (0.11f * (0.65f + 0.35f * reflectionFactor)).coerceAtMost(0.20f)
        } else {
            (0.06f * (0.65f + 0.35f * reflectionFactor)).coerceIn(0.03f, 0.10f)
        }
        val bottomEdgeTint = if (isDark) Color.Black else Color(0xFF6B768A)
        val volumeBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = topSheen),
                0.35f to Color.Transparent,
                0.76f to Color.Transparent,
                1.0f to bottomEdgeTint.copy(alpha = bottomShadow),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        drawOutline(outline = outline, brush = volumeBrush)
    }

    private fun DrawScope.clipToOutline(outline: Outline, path: Path?) {
        val canvas = drawContext.canvas
        when (outline) {
            is Outline.Rectangle -> canvas.clipRect(outline.rect)
            is Outline.Rounded -> {
                val p = path ?: Path()
                p.reset()
                p.addRoundRect(outline.roundRect)
                canvas.clipPath(p)
            }
            is Outline.Generic -> canvas.clipPath(outline.path)
        }
    }
}

private fun DrawScope.clipRimOutline(outline: Outline, path: Path?) {
    val canvas = drawContext.canvas
    when (outline) {
        is Outline.Rectangle -> canvas.clipRect(outline.rect)
        is Outline.Rounded -> {
            val p = path ?: Path()
            p.reset()
            p.addRoundRect(outline.roundRect)
            canvas.clipPath(p)
        }
        is Outline.Generic -> canvas.clipPath(outline.path)
    }
}

private fun DrawScope.drawLiquidGlassRim(
    shape: Shape,
    borderAlpha: Float,
    level: DesktopLiquidGlassLevel,
    outlineReflectionScale: Int = DesktopAppearanceSettings.DEFAULT_LIQUID_GLASS_OUTLINE_REFLECTION_SCALE,
    isDark: Boolean = true,
    precomputedOutline: Outline? = null,
    clipPath: Path? = null,
) {
    if (size.width < 1f || size.height < 1f || borderAlpha <= 0.002f) return
    val reflectionFactor = DesktopAppearanceSettings.effectiveOutlineReflectionFactor(outlineReflectionScale)
    val outline = precomputedOutline ?: shape.createOutline(size, layoutDirection, this)
    val rimWidthPx = (if (level == DesktopLiquidGlassLevel.IMPRESSIVE) 1.4.dp else 1.2.dp).toPx()
    val bevelGlowWidthPx = (if (level == DesktopLiquidGlassLevel.IMPRESSIVE) 3.0.dp else 2.4.dp).toPx()
    val effect = DesktopLiquidGlassShaders.specularHighlightEffect
    val radii = extractCornerRadii(shape, size, this, layoutDirection)

    fun specularBrush(angleRad: Float, alpha: Float, falloff: Float): ShaderBrush? {
        if (effect == null || alpha <= 0.004f) return null
        return runCatching {
            val builder = RuntimeShaderBuilder(effect).apply {
                uniform("size", size.width, size.height)
                uniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
                uniform("color", 1f, 1f, 1f, alpha.coerceIn(0f, 1f))
                uniform("angle", angleRad)
                uniform("falloff", falloff)
            }
            ShaderBrush(builder.makeShader().asComposeShader())
        }.getOrNull()
    }

    val canvas = drawContext.canvas
    canvas.save()
    clipRimOutline(outline, clipPath)

    if (!isDark) {
        // Subtle cool outer hairline on Light surfaces so glass edges read sharply against white.
        drawOutline(
            outline = outline,
            color = Color.Black.copy(alpha = (borderAlpha * 0.58f).coerceIn(0f, 0.14f)),
            style = Stroke(width = 0.85.dp.toPx()),
        )
    }

    // Subtle uniform neutral highlight so edges away from light still read cleanly without glaring.
    drawOutline(
        outline = outline,
        color = Color.White.copy(
            alpha = if (isDark) {
                (borderAlpha * 0.28f * reflectionFactor).coerceIn(0f, 0.16f)
            } else {
                (borderAlpha * 0.55f * reflectionFactor).coerceIn(0f, 0.22f)
            },
        ),
        style = Stroke(width = 1.0.dp.toPx()),
    )

    val primaryFalloff = if (level == DesktopLiquidGlassLevel.IMPRESSIVE) 1.75f else 2.0f
    val primaryAlpha = (borderAlpha * level.rimBoost * reflectionFactor * if (isDark) 1f else 1.12f)
        .coerceIn(0f, if (isDark) 0.62f else 0.72f)
    val primaryBrush = specularBrush(LIQUID_HIGHLIGHT_ANGLE_RAD, primaryAlpha, primaryFalloff)

    if (primaryBrush != null) {
        // Wide soft inner bevel lip that gives physical embossed glass thickness without masking refraction.
        specularBrush(
            LIQUID_HIGHLIGHT_ANGLE_RAD,
            (primaryAlpha * 0.36f).coerceAtMost(if (isDark) 0.24f else 0.30f),
            1.5f,
        )?.let { bevelBrush ->
            drawOutline(
                outline = outline,
                brush = bevelBrush,
                style = Stroke(width = bevelGlowWidthPx),
                blendMode = BlendMode.SrcOver,
            )
        }
        drawOutline(
            outline = outline,
            brush = primaryBrush,
            style = Stroke(width = rimWidthPx),
            blendMode = BlendMode.SrcOver,
        )
        if (level.secondaryRimAlpha > 0.01f || reflectionFactor > 1.25f) {
            val secBase = if (level.secondaryRimAlpha > 0.01f) level.secondaryRimAlpha else 0.075f
            specularBrush(
                LIQUID_SECONDARY_HIGHLIGHT_ANGLE_RAD,
                (borderAlpha * secBase * reflectionFactor * if (isDark) 1f else 1.15f)
                    .coerceIn(0f, if (isDark) 0.36f else 0.44f),
                2.1f,
            )?.let { secondaryBrush ->
                drawOutline(
                    outline = outline,
                    brush = secondaryBrush,
                    style = Stroke(width = rimWidthPx * 0.9f),
                    blendMode = BlendMode.SrcOver,
                )
            }
        }
    } else {
        drawOutline(
            outline = outline,
            color = Color.White.copy(
                alpha = (borderAlpha * 0.65f * reflectionFactor).coerceIn(0f, if (isDark) 0.36f else 0.72f),
            ),
            style = Stroke(width = 1.0.dp.toPx()),
        )
    }

    canvas.restore()
}

private fun extractCornerRadii(
    shape: Shape,
    size: androidx.compose.ui.geometry.Size,
    density: Density,
    layoutDirection: LayoutDirection,
): FloatArray {
    val maxRadius = size.minDimension / 2f
    val cornerShape = shape as? CornerBasedShape ?: return FloatArray(4) { maxRadius }
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val tl = if (isLtr) cornerShape.topStart.toPx(size, density) else cornerShape.topEnd.toPx(size, density)
    val tr = if (isLtr) cornerShape.topEnd.toPx(size, density) else cornerShape.topStart.toPx(size, density)
    val br = if (isLtr) cornerShape.bottomEnd.toPx(size, density) else cornerShape.bottomStart.toPx(size, density)
    val bl = if (isLtr) cornerShape.bottomStart.toPx(size, density) else cornerShape.bottomEnd.toPx(size, density)
    return floatArrayOf(
        tl.coerceAtMost(maxRadius),
        tr.coerceAtMost(maxRadius),
        br.coerceAtMost(maxRadius),
        bl.coerceAtMost(maxRadius),
    )
}
