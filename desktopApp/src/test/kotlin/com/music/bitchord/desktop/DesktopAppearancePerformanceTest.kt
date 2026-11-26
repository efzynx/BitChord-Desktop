package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopAppearancePerformanceTest {

    @Test
    fun `Skia Liquid Glass SkSL shaders compile and chain into ImageFilter`() {
        val refraction = DesktopLiquidGlassShaders.refractionDispersionEffect
        val highlight = DesktopLiquidGlassShaders.specularHighlightEffect
        assertNotNull(refraction, "Refraction dispersion SkSL failed to compile")
        assertNotNull(highlight, "Specular highlight SkSL failed to compile")

        val blurFilter = ImageFilter.makeBlur(4f, 4f, FilterTileMode.CLAMP, null, null)
        val builder = RuntimeShaderBuilder(refraction).apply {
            uniform("size", 320f, 64f)
            uniform("offset", 0f, 0f)
            uniform("cornerRadii", 16f, 16f, 16f, 16f)
            uniform("refractionHeight", 30f)
            uniform("refractionAmount", -38f)
            uniform("depthEffect", DesktopLiquidGlassLevel.IMPRESSIVE.depthEffect)
            uniform("chromaticAberration", DesktopLiquidGlassLevel.IMPRESSIVE.chromaticAberration)
            uniform("fresnelIntensity", DesktopLiquidGlassLevel.IMPRESSIVE.fresnelIntensity)
        }
        val lensFilter = ImageFilter.makeRuntimeShader(builder, "content", blurFilter)
        assertNotNull(lensFilter)

        val highlightShader = RuntimeShaderBuilder(highlight).apply {
            uniform("size", 320f, 64f)
            uniform("cornerRadii", 16f, 16f, 16f, 16f)
            uniform("color", 1f, 1f, 1f, 0.5f)
            uniform("angle", 0.7853982f)
            uniform("falloff", 1.1f)
        }.makeShader()
        assertNotNull(highlightShader)
    }

    @Test
    fun `Liquid Glass requires Windows 10+ on Windows and works on Linux without version limit`() {
        assertFalse(
            DesktopPlatform.liquidGlassSupportedFor(
                isWindows = true,
                isLinux = false,
                isMac = false,
                windowsBuildNumber = 7601,
                osVersion = "6.1",
                osName = "Windows 7",
            ),
        )
        assertFalse(
            DesktopPlatform.liquidGlassSupportedFor(
                isWindows = true,
                isLinux = false,
                isMac = false,
                windowsBuildNumber = 9600,
                osVersion = "6.3",
                osName = "Windows 8.1",
            ),
        )
        assertTrue(
            DesktopPlatform.liquidGlassSupportedFor(
                isWindows = true,
                isLinux = false,
                isMac = false,
                windowsBuildNumber = 19045,
                osVersion = "10.0",
                osName = "Windows 10",
            ),
        )
        assertTrue(
            DesktopPlatform.liquidGlassSupportedFor(
                isWindows = true,
                isLinux = false,
                isMac = false,
                windowsBuildNumber = -1,
                osVersion = "10.0",
                osName = "Windows 10",
            ),
        )
        assertTrue(
            DesktopPlatform.liquidGlassSupportedFor(
                isWindows = true,
                isLinux = false,
                isMac = false,
                windowsBuildNumber = 22631,
                osVersion = "10.0",
                osName = "Windows 11",
            ),
        )
        assertTrue(
            DesktopPlatform.liquidGlassSupportedFor(
                isWindows = false,
                isLinux = true,
                isMac = false,
                windowsBuildNumber = -1,
                osVersion = "4.19.0",
                osName = "Linux",
            ),
        )
    }

    @Test
    fun `display refresh rate resolution picks closest supported mode`() {
        val rates = listOf(60, 120, 144)
        assertEquals(144, DesktopDisplayPerformance.defaultPreferredRefreshRate(rates))
        assertEquals(120, DesktopDisplayPerformance.resolveRefreshRate(120, rates))
        assertEquals(144, DesktopDisplayPerformance.resolveRefreshRate(165, rates))
        assertEquals(60, DesktopDisplayPerformance.resolveRefreshRate(48, rates))
    }

    @Test
    fun `high performance mode syncs cleanly with reduce animation, blur, blend, and liquid glass`() {
        val prevReduceAnim = DesktopAppearanceSettings.reduceAnimation.value
        val prevReduceBlur = DesktopAppearanceSettings.reduceDynamicBlur.value
        val prevDisableBlend = DesktopAppearanceSettings.disableBlendEffects.value
        val prevLiquidGlass = DesktopAppearanceSettings.liquidGlass.value
        val prevGlassPerf = DesktopAppearanceSettings.glassPerformance.value
        val prevHighPerf = DesktopAppearanceSettings.highPerformanceMode.value
        try {
            DesktopAppearanceSettings.setReduceAnimation(true)
            DesktopAppearanceSettings.setReduceDynamicBlur(true)
            DesktopAppearanceSettings.setHighPerformanceMode(true)
            assertTrue(DesktopAppearanceSettings.highPerformanceMode.value)
            assertFalse(DesktopAppearanceSettings.reduceAnimation.value)
            assertFalse(DesktopAppearanceSettings.reduceDynamicBlur.value)

            DesktopAppearanceSettings.setReduceAnimation(true)
            assertFalse(DesktopAppearanceSettings.highPerformanceMode.value)
            assertTrue(DesktopAppearanceSettings.reduceAnimation.value)

            DesktopAppearanceSettings.setHighPerformanceMode(true)
            DesktopAppearanceSettings.setReduceDynamicBlur(true)
            assertFalse(DesktopAppearanceSettings.highPerformanceMode.value)
            assertTrue(DesktopAppearanceSettings.reduceDynamicBlur.value)

            DesktopAppearanceSettings.setDisableBlendEffects(true)
            assertTrue(DesktopAppearanceSettings.disableBlendEffects.value)
            assertTrue(DesktopPlayerSettings.disableBlendEffects.value)

            DesktopAppearanceSettings.setLiquidGlass(true)
            DesktopAppearanceSettings.setGlassPerformance(true)
            assertTrue(DesktopAppearanceSettings.liquidGlass.value)
            assertTrue(DesktopAppearanceSettings.glassPerformance.value)

            val prevLevel = DesktopAppearanceSettings.liquidGlassLevel.value
            try {
                DesktopAppearanceSettings.setLiquidGlassLevel(DesktopLiquidGlassLevel.LOW)
                assertEquals(DesktopLiquidGlassLevel.LOW, DesktopAppearanceSettings.liquidGlassLevel.value)
                assertFalse(DesktopLiquidGlassLevel.LOW.useRefraction)

                DesktopAppearanceSettings.setLiquidGlassLevel(DesktopLiquidGlassLevel.IMPRESSIVE)
                assertEquals(DesktopLiquidGlassLevel.IMPRESSIVE, DesktopAppearanceSettings.liquidGlassLevel.value)
                assertTrue(DesktopLiquidGlassLevel.IMPRESSIVE.resolutionScale > DesktopLiquidGlassLevel.HIGH.resolutionScale)
                assertTrue(DesktopLiquidGlassLevel.IMPRESSIVE.chromaticAberration > DesktopLiquidGlassLevel.HIGH.chromaticAberration)
                assertTrue(DesktopLiquidGlassLevel.IMPRESSIVE.fresnelIntensity > DesktopLiquidGlassLevel.HIGH.fresnelIntensity)
                assertTrue(DesktopLiquidGlassLevel.IMPRESSIVE.secondaryRimAlpha > DesktopLiquidGlassLevel.HIGH.secondaryRimAlpha)
            } finally {
                DesktopAppearanceSettings.setLiquidGlassLevel(prevLevel)
            }

            val prevAmbient = DesktopAppearanceSettings.ambientBackdrop.value
            try {
                DesktopAppearanceSettings.setAmbientBackdrop(true)
                DesktopAppearanceSettings.setDisableBlendEffects(true)
                DesktopAppearanceSettings.setReduceDynamicBlur(true)
                assertTrue(DesktopAppearanceSettings.ambientBackdrop.value)
                assertTrue(DesktopAppearanceSettings.disableBlendEffects.value)
                assertTrue(DesktopAppearanceSettings.reduceDynamicBlur.value)

                DesktopAppearanceSettings.setDisableBlendEffects(false)
                DesktopAppearanceSettings.setReduceDynamicBlur(false)
                DesktopAppearanceSettings.setLiquidGlass(true)
                assertTrue(DesktopAppearanceSettings.ambientBackdrop.value)
                assertTrue(DesktopAppearanceSettings.liquidGlass.value)
            } finally {
                DesktopAppearanceSettings.setAmbientBackdrop(prevAmbient)
            }

            val prevSmooth = DesktopAppearanceSettings.smoothTransitions.value
            val prevGlassClick = DesktopAppearanceSettings.glassClickAnimations.value
            try {
                DesktopAppearanceSettings.setSmoothTransitions(false)
                DesktopAppearanceSettings.setGlassClickAnimations(false)
                assertFalse(DesktopAppearanceSettings.smoothTransitions.value)
                assertFalse(DesktopAppearanceSettings.glassClickAnimations.value)

                DesktopAppearanceSettings.setSmoothTransitions(true)
                DesktopAppearanceSettings.setGlassClickAnimations(true)
                assertTrue(DesktopAppearanceSettings.smoothTransitions.value)
                assertTrue(DesktopAppearanceSettings.glassClickAnimations.value)
            } finally {
                DesktopAppearanceSettings.setSmoothTransitions(prevSmooth)
                DesktopAppearanceSettings.setGlassClickAnimations(prevGlassClick)
            }
        } finally {
            DesktopAppearanceSettings.setHighPerformanceMode(prevHighPerf)
            DesktopAppearanceSettings.setReduceAnimation(prevReduceAnim)
            DesktopAppearanceSettings.setReduceDynamicBlur(prevReduceBlur)
            DesktopAppearanceSettings.setDisableBlendEffects(prevDisableBlend)
            DesktopAppearanceSettings.setLiquidGlass(prevLiquidGlass)
            DesktopAppearanceSettings.setGlassPerformance(prevGlassPerf)
        }
    }

    @Test
    fun `Liquid Glass descendant inside recording backdrop renders safely without Skia crash`() {
        val prevLiquid = DesktopAppearanceSettings.liquidGlass.value
        val prevReduceBlur = DesktopAppearanceSettings.reduceDynamicBlur.value
        val prevPerf = DesktopAppearanceSettings.glassPerformance.value
        val prevHighPerf = DesktopAppearanceSettings.highPerformanceMode.value
        val prevLevel = DesktopAppearanceSettings.liquidGlassLevel.value
        try {
            DesktopAppearanceSettings.setHighPerformanceMode(false)
            DesktopAppearanceSettings.setReduceDynamicBlur(false)
            DesktopAppearanceSettings.setGlassPerformance(false)
            DesktopAppearanceSettings.setLiquidGlass(true)
            DesktopAppearanceSettings.setLiquidGlassLevel(DesktopLiquidGlassLevel.IMPRESSIVE)

            lateinit var backdrop: DesktopLayerBackdrop
            val scene = ImageComposeScene(width = 640, height = 480) {
                backdrop = rememberDesktopLayerBackdrop()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141418))
                        .desktopLayerBackdrop(backdrop, enabled = true),
                ) {
                    Box(
                        Modifier
                            .size(240.dp, 140.dp)
                            .desktopLiquidGlass(
                                shape = RoundedCornerShape(20.dp),
                                backdrop = backdrop,
                            ),
                    )
                }
            }
            try {
                scene.render(0L).close()
                scene.render(16_000_000L).close()
                scene.render(32_000_000L).close()
                scene.render(48_000_000L).close()
                assertTrue(backdrop.hasRecorded)
                assertFalse(backdrop.isRecording)
            } finally {
                scene.close()
            }
        } finally {
            DesktopAppearanceSettings.setHighPerformanceMode(prevHighPerf)
            DesktopAppearanceSettings.setReduceDynamicBlur(prevReduceBlur)
            DesktopAppearanceSettings.setGlassPerformance(prevPerf)
            DesktopAppearanceSettings.setLiquidGlass(prevLiquid)
            DesktopAppearanceSettings.setLiquidGlassLevel(prevLevel)
        }
    }

    @Test
    fun `DesktopDialogPanel popup portals out of captured backdrop and unregisters cleanly`() {
        val prevLiquid = DesktopAppearanceSettings.liquidGlass.value
        val prevReduceBlur = DesktopAppearanceSettings.reduceDynamicBlur.value
        val prevPerf = DesktopAppearanceSettings.glassPerformance.value
        val prevHighPerf = DesktopAppearanceSettings.highPerformanceMode.value
        try {
            DesktopAppearanceSettings.setHighPerformanceMode(false)
            DesktopAppearanceSettings.setReduceDynamicBlur(false)
            DesktopAppearanceSettings.setGlassPerformance(false)
            DesktopAppearanceSettings.setLiquidGlass(true)

            var showPopup by mutableStateOf(true)
            var modalHostRef: DesktopModalHostState? = null
            lateinit var overlayBackdropRef: DesktopLayerBackdrop
            val scene = ImageComposeScene(width = 800, height = 600) {
                val modalHost = remember { DesktopModalHostState() }.also { modalHostRef = it }
                val overlayBackdrop = rememberDesktopLayerBackdrop().also { overlayBackdropRef = it }
                CompositionLocalProvider(
                    LocalDesktopBackdrop provides overlayBackdrop,
                    LocalDesktopModalHost provides modalHost,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF101014))
                                .desktopLayerBackdrop(overlayBackdrop, enabled = true),
                        ) {
                            CompositionLocalProvider(LocalDesktopPanelIsPage provides true) {
                                if (showPopup) {
                                    DesktopDialogPanel(
                                        onDismiss = { showPopup = false },
                                        maxWidth = 480,
                                        popup = true,
                                    ) {
                                        Text("Add a source")
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                            DesktopModalPortalHost(modalHost)
                        }
                    }
                }
            }
            try {
                scene.render(0L).close()
                scene.render(16_000_000L).close()
                scene.render(32_000_000L).close()
                assertEquals(1, modalHostRef?.entries?.size)
                assertTrue(overlayBackdropRef.hasRecorded)
                assertFalse(overlayBackdropRef.isRecording)

                Snapshot.withMutableSnapshot { showPopup = false }
                Snapshot.sendApplyNotifications()
                scene.render(48_000_000L).close()
                scene.render(64_000_000L).close()
                assertEquals(0, modalHostRef?.entries?.size)
            } finally {
                scene.close()
            }
        } finally {
            DesktopAppearanceSettings.setHighPerformanceMode(prevHighPerf)
            DesktopAppearanceSettings.setReduceDynamicBlur(prevReduceBlur)
            DesktopAppearanceSettings.setGlassPerformance(prevPerf)
            DesktopAppearanceSettings.setLiquidGlass(prevLiquid)
        }
    }

    @Test
    fun `Theme mode resolution and palette tokens distinguish Light and Dark`() {
        val prevMode = DesktopAppearanceSettings.themeMode.value
        try {
            assertTrue(resolveDarkTheme(DesktopThemeMode.DARK, systemDark = false))
            assertTrue(resolveDarkTheme(DesktopThemeMode.DARK, systemDark = true))
            assertFalse(resolveDarkTheme(DesktopThemeMode.LIGHT, systemDark = false))
            assertFalse(resolveDarkTheme(DesktopThemeMode.LIGHT, systemDark = true))
            assertFalse(resolveDarkTheme(DesktopThemeMode.SYSTEM, systemDark = false))
            assertTrue(resolveDarkTheme(DesktopThemeMode.SYSTEM, systemDark = true))

            val lightScheme = desktopColorSchemeFor(isDark = false)
            val darkScheme = desktopColorSchemeFor(isDark = true)
            assertEquals(DesktopLightPalette.background, lightScheme.background)
            assertEquals(DesktopLightPalette.ink, lightScheme.onBackground)
            assertEquals(DesktopDarkPalette.background, darkScheme.background)
            assertEquals(DesktopDarkPalette.ink, darkScheme.onBackground)

            DesktopAppearanceSettings.setThemeMode(DesktopThemeMode.LIGHT)
            assertEquals(DesktopThemeMode.LIGHT, DesktopAppearanceSettings.themeMode.value)
            DesktopAppearanceSettings.setThemeMode(DesktopThemeMode.SYSTEM)
            assertEquals(DesktopThemeMode.SYSTEM, DesktopAppearanceSettings.themeMode.value)
        } finally {
            DesktopAppearanceSettings.setThemeMode(prevMode)
        }
    }

    @Test
    fun `Liquid Glass rainbow and outline reflection scales clamp 1 to 10 and scale shader factors monotonically`() {
        val prevRainbow = DesktopAppearanceSettings.liquidGlassRainbowScale.value
        val prevReflection = DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.value
        try {
            DesktopAppearanceSettings.setLiquidGlassRainbowScale(-5)
            assertEquals(DesktopAppearanceSettings.MIN_LIQUID_GLASS_SCALE, DesktopAppearanceSettings.liquidGlassRainbowScale.value)
            DesktopAppearanceSettings.setLiquidGlassRainbowScale(42)
            assertEquals(DesktopAppearanceSettings.MAX_LIQUID_GLASS_SCALE, DesktopAppearanceSettings.liquidGlassRainbowScale.value)

            DesktopAppearanceSettings.setLiquidGlassOutlineReflectionScale(0)
            assertEquals(DesktopAppearanceSettings.MIN_LIQUID_GLASS_SCALE, DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.value)
            DesktopAppearanceSettings.setLiquidGlassOutlineReflectionScale(99)
            assertEquals(DesktopAppearanceSettings.MAX_LIQUID_GLASS_SCALE, DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.value)

            val lowRainbow = DesktopAppearanceSettings.effectiveChromaticAberration(DesktopLiquidGlassLevel.IMPRESSIVE, 1)
            val midRainbow = DesktopAppearanceSettings.effectiveChromaticAberration(DesktopLiquidGlassLevel.IMPRESSIVE, 6)
            val highRainbow = DesktopAppearanceSettings.effectiveChromaticAberration(DesktopLiquidGlassLevel.IMPRESSIVE, 10)
            assertTrue(lowRainbow in 0f..<midRainbow && midRainbow < highRainbow, "Expected monotonic dispersion: $lowRainbow < $midRainbow < $highRainbow")
            assertEquals(0f, DesktopAppearanceSettings.effectiveChromaticAberration(DesktopLiquidGlassLevel.LOW, 10))

            val lowRim = DesktopAppearanceSettings.effectiveOutlineReflectionFactor(1)
            val midRim = DesktopAppearanceSettings.effectiveOutlineReflectionFactor(6)
            val highRim = DesktopAppearanceSettings.effectiveOutlineReflectionFactor(10)
            assertTrue(lowRim < midRim && midRim < highRim)

            DesktopAppearanceSettings.resetLiquidGlassAdvanced()
            assertEquals(DesktopAppearanceSettings.DEFAULT_LIQUID_GLASS_RAINBOW_SCALE, DesktopAppearanceSettings.liquidGlassRainbowScale.value)
            assertEquals(DesktopAppearanceSettings.DEFAULT_LIQUID_GLASS_OUTLINE_REFLECTION_SCALE, DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.value)
        } finally {
            DesktopAppearanceSettings.setLiquidGlassRainbowScale(prevRainbow)
            DesktopAppearanceSettings.setLiquidGlassOutlineReflectionScale(prevReflection)
        }
    }

    @Test
    fun `Light theme Liquid Glass with max advanced scales renders offscreen Skia frames cleanly`() {
        val prevLiquid = DesktopAppearanceSettings.liquidGlass.value
        val prevReduceBlur = DesktopAppearanceSettings.reduceDynamicBlur.value
        val prevPerf = DesktopAppearanceSettings.glassPerformance.value
        val prevHighPerf = DesktopAppearanceSettings.highPerformanceMode.value
        val prevLevel = DesktopAppearanceSettings.liquidGlassLevel.value
        val prevRainbow = DesktopAppearanceSettings.liquidGlassRainbowScale.value
        val prevReflection = DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.value
        try {
            DesktopAppearanceSettings.setHighPerformanceMode(false)
            DesktopAppearanceSettings.setReduceDynamicBlur(false)
            DesktopAppearanceSettings.setGlassPerformance(false)
            DesktopAppearanceSettings.setLiquidGlass(true)
            DesktopAppearanceSettings.setLiquidGlassLevel(DesktopLiquidGlassLevel.IMPRESSIVE)
            DesktopAppearanceSettings.setLiquidGlassRainbowScale(10)
            DesktopAppearanceSettings.setLiquidGlassOutlineReflectionScale(10)

            lateinit var backdrop: DesktopLayerBackdrop
            val scene = ImageComposeScene(width = 640, height = 480) {
                CompositionLocalProvider(LocalDesktopPalette provides DesktopLightPalette) {
                    backdrop = rememberDesktopLayerBackdrop()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(DesktopLightPalette.background)
                            .desktopLayerBackdrop(backdrop, enabled = true),
                    ) {
                        Box(
                            Modifier
                                .size(260.dp, 120.dp)
                                .desktopLiquidGlass(
                                    shape = RoundedCornerShape(22.dp),
                                    backdrop = backdrop,
                                ),
                        )
                    }
                }
            }
            try {
                scene.render(0L).close()
                scene.render(16_000_000L).close()
                scene.render(32_000_000L).close()
                assertTrue(backdrop.hasRecorded)
                assertFalse(backdrop.isRecording)
            } finally {
                scene.close()
            }
        } finally {
            DesktopAppearanceSettings.setLiquidGlassRainbowScale(prevRainbow)
            DesktopAppearanceSettings.setLiquidGlassOutlineReflectionScale(prevReflection)
            DesktopAppearanceSettings.setLiquidGlassLevel(prevLevel)
            DesktopAppearanceSettings.setHighPerformanceMode(prevHighPerf)
            DesktopAppearanceSettings.setReduceDynamicBlur(prevReduceBlur)
            DesktopAppearanceSettings.setGlassPerformance(prevPerf)
            DesktopAppearanceSettings.setLiquidGlass(prevLiquid)
        }
    }

    @Test
    fun `DesktopArtworkMesh lightTheme lifts dark earthy artwork into luminous high-key pastels`() {
        val w = 16
        val h = 16
        val skiaBitmap = Bitmap().apply {
            allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
            val bytes = ByteArray(w * h * 4)
            for (i in 0 until w * h) {
                val at = i * 4
                // Dark olive-green / gold earthy cover pixel (BGRA): B=24, G=72, R=64, A=255
                bytes[at] = 24.toByte()
                bytes[at + 1] = 72.toByte()
                bytes[at + 2] = 64.toByte()
                bytes[at + 3] = 255.toByte()
            }
            installPixels(bytes)
            setImmutable()
        }
        val bmp = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap).toComposeImageBitmap()

        val darkMesh = assertNotNull(DesktopArtworkMesh.of(bmp, seed = 42, lightTheme = false))
        val lightMesh = assertNotNull(DesktopArtworkMesh.of(bmp, seed = 42, lightTheme = true))

        val darkPixels = IntArray(darkMesh.width * darkMesh.height)
        val lightPixels = IntArray(lightMesh.width * lightMesh.height)
        darkMesh.readPixels(darkPixels)
        lightMesh.readPixels(lightPixels)

        val avgDarkLum = darkPixels.map { Color(it).luminance() }.average()
        val avgLightLum = lightPixels.map { Color(it).luminance() }.average()
        assertTrue(avgLightLum > 0.50, "Expected luminous Light mesh pastel (>0.50), was $avgLightLum")
        assertTrue(avgLightLum < 0.82, "Expected rich Light mesh without chalky white washout (<0.82), was $avgLightLum")
        assertTrue(avgLightLum > avgDarkLum + 0.40, "Expected Light mesh luminance ($avgLightLum) well above Dark ($avgDarkLum)")

        // Neutral black silhouette pixels must not turn into near-pure-white blotches (>0.86)
        val neutralBlackBitmap = Bitmap().apply {
            allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
            val bytes = ByteArray(w * h * 4)
            for (i in 0 until w * h) {
                val at = i * 4
                bytes[at] = 16.toByte()
                bytes[at + 1] = 16.toByte()
                bytes[at + 2] = 16.toByte()
                bytes[at + 3] = 255.toByte()
            }
            installPixels(bytes)
            setImmutable()
        }
        val neutralLightMesh = assertNotNull(
            DesktopArtworkMesh.of(
                org.jetbrains.skia.Image.makeFromBitmap(neutralBlackBitmap).toComposeImageBitmap(),
                seed = 42,
                lightTheme = true,
            ),
        )
        val neutralPixels = IntArray(neutralLightMesh.width * neutralLightMesh.height)
        neutralLightMesh.readPixels(neutralPixels)
        val avgNeutralLum = neutralPixels.map { Color(it).luminance() }.average()
        assertTrue(avgNeutralLum in 0.40..0.68, "Expected soft neutral silhouette depth (0.40..0.68), was $avgNeutralLum")

        // Mixed olive + dark-silhouette cover should carry rich olive tint everywhere rather than white fog
        val mixedBitmap = Bitmap().apply {
            allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
            val bytes = ByteArray(w * h * 4)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val at = (y * w + x) * 4
                    if (y < h / 2) {
                        // Olive cover BGRA: B=72, G=174, R=164
                        bytes[at] = 72.toByte()
                        bytes[at + 1] = 174.toByte()
                        bytes[at + 2] = 164.toByte()
                    } else {
                        // Black silhouette BGRA: B=15, G=18, R=18
                        bytes[at] = 15.toByte()
                        bytes[at + 1] = 18.toByte()
                        bytes[at + 2] = 18.toByte()
                    }
                    bytes[at + 3] = 255.toByte()
                }
            }
            installPixels(bytes)
            setImmutable()
        }
        val mixedLightMesh = assertNotNull(
            DesktopArtworkMesh.of(
                org.jetbrains.skia.Image.makeFromBitmap(mixedBitmap).toComposeImageBitmap(),
                seed = 7,
                lightTheme = true,
            ),
        )
        val mixedPixels = IntArray(mixedLightMesh.width * mixedLightMesh.height)
        mixedLightMesh.readPixels(mixedPixels)
        val avgChromaSpread = mixedPixels.map { px ->
            val c = Color(px)
            maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
        }.average()
        assertTrue(avgChromaSpread > 0.16, "Expected mixed cover mesh to keep rich chroma ($avgChromaSpread) instead of grey washout")
    }

    @Test
    fun `shouldKeepLocalHeaderPinnedOnTabSwitch preserves pinned state across tabs`() {
        assertFalse(shouldKeepLocalHeaderPinnedOnTabSwitch(isPinned = false, alreadyLocked = false))
        assertTrue(shouldKeepLocalHeaderPinnedOnTabSwitch(isPinned = true, alreadyLocked = false))
        assertTrue(shouldKeepLocalHeaderPinnedOnTabSwitch(isPinned = false, alreadyLocked = true))
        assertTrue(shouldKeepLocalHeaderPinnedOnTabSwitch(isPinned = true, alreadyLocked = true))
    }

    @Test
    fun `ArtworkMeshBackdrop renders bright canvas when LocalPlayerLightTheme is active`() {
        com.music.bitchord.ui.player.PlayerPlatform.install(DesktopPlayerHost)
        val scene = ImageComposeScene(width = 320, height = 480) {
            CompositionLocalProvider(
                com.music.bitchord.ui.player.LocalPlayerLightTheme provides true,
            ) {
                com.music.bitchord.ui.player.ArtworkMeshBackdrop(
                    mesh = null,
                    modifier = Modifier.fillMaxSize(),
                    seam = { 0.dp },
                )
            }
        }
        try {
            val image = scene.render(16_000_000L)
            try {
                val composeBitmap = image.toComposeImageBitmap()
                val pixel = IntArray(1)
                composeBitmap.readPixels(pixel, startX = 160, startY = 240, width = 1, height = 1)
                val centerLuminance = Color(pixel[0]).luminance()
                assertTrue(centerLuminance > 0.80f, "Expected bright Light player backdrop (>0.80), got $centerLuminance")
            } finally {
                image.close()
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun `resolveSharedPageTopPadding provides breathing room aligned with sidebar`() {
        assertEquals(14.dp, resolveSharedPageTopPadding(inlineCaption = false))
        assertEquals(4.dp, resolveSharedPageTopPadding(inlineCaption = true))
    }

    @Test
    fun `resolveTrackSkipDirection follows next and previous direction hints`() {
        assertEquals(-1f, resolveTrackSkipDirection(previousId = "songA", nextId = "songB", hint = -1f))
        assertEquals(1f, resolveTrackSkipDirection(previousId = "songA", nextId = "songB", hint = 1f))
        assertEquals(1f, resolveTrackSkipDirection(previousId = "songA", nextId = "songA", hint = -1f))
    }

    @Test
    fun `desktopPopupMotion renders inside Skia ImageComposeScene without errors`() {
        val scene = ImageComposeScene(width = 320, height = 240) {
            Box(
                Modifier
                    .fillMaxSize()
                    .desktopPopupMotion(pivotX = 0.5f, pivotY = 0f),
            )
        }
        try {
            scene.render(16_000_000L).close()
            scene.render(180_000_000L).close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun `shouldHandleBackPointerGesture honors swipeBackGesture toggle`() {
        assertTrue(shouldHandleBackPointerGesture(gestureEnabled = true, isBackPressed = true))
        assertFalse(shouldHandleBackPointerGesture(gestureEnabled = true, isBackPressed = false))
        assertFalse(shouldHandleBackPointerGesture(gestureEnabled = false, isBackPressed = true))
        assertFalse(shouldHandleBackPointerGesture(gestureEnabled = false, isBackPressed = false))
    }

    @Test
    fun `DesktopPlayerSettings hideLyricsPreview tracks DesktopAppearanceSettings toggle`() {
        val prev = DesktopAppearanceSettings.hideLyricsPreview.value
        try {
            DesktopPlayerSettings.setHideLyricsPreview(true)
            assertTrue(DesktopPlayerSettings.hideLyricsPreview.value)
            assertTrue(DesktopAppearanceSettings.hideLyricsPreview.value)
            DesktopPlayerSettings.setHideLyricsPreview(false)
            assertFalse(DesktopPlayerSettings.hideLyricsPreview.value)
        } finally {
            DesktopAppearanceSettings.setHideLyricsPreview(prev)
        }
    }

    @Test
    fun `AudioOutput player drawer uses dark ink and deepened scrim in Light theme`() {
        val lightRow = com.music.bitchord.ui.player.playerDrawerRowFill(lightTheme = true, selected = true)
        val darkRow = com.music.bitchord.ui.player.playerDrawerRowFill(lightTheme = false, selected = true)
        assertTrue(lightRow.luminance() < 0.05f, "Light drawer row fill should tint with dark ink")
        assertTrue(darkRow.luminance() > 0.90f, "Dark drawer row fill should tint with white ink")

        val lightScrim = com.music.bitchord.ui.player.playerDrawerScrimColor(lightTheme = true)
        val darkScrim = com.music.bitchord.ui.player.playerDrawerScrimColor(lightTheme = false)
        assertTrue(lightScrim.alpha >= 0.55f, "Light drawer scrim should noticeably darken backdrop")
        assertTrue(darkScrim.alpha >= 0.70f, "Dark drawer scrim should noticeably darken backdrop")
    }

    @Test
    fun `AudioPipeline dialog and modal dialog glass adapt luminance to Light and Dark themes`() {
        val lightPipelineLuminance = pipelineNodeCoreColor(isDark = false).luminance()
        val darkPipelineLuminance = pipelineNodeCoreColor(isDark = true).luminance()
        assertTrue(lightPipelineLuminance > 0.75f, "Light Audio Pipeline surface should sync to light theme")
        assertTrue(darkPipelineLuminance < 0.05f, "Dark Audio Pipeline surface should stay dark")
        assertTrue(pipelineScrimColor(isDark = false).alpha >= 0.55f)
        assertTrue(pipelineScrimColor(isDark = true).alpha >= 0.70f)

        val lightModalTint = desktopModalSurfaceTint(isDark = false)
        val darkModalTint = desktopModalSurfaceTint(isDark = true)
        assertTrue(lightModalTint.alpha >= 0.80f, "Light modal glass tint should prevent harsh artwork bleed-through")
        assertTrue(darkModalTint.alpha >= 0.78f, "Dark modal glass tint should keep dark surface legible")
        assertTrue(DesktopLightPalette.scrim.alpha >= 0.50f, "Light modal scrim should darken colorful players")
        assertTrue(DesktopDarkPalette.scrim.alpha >= 0.70f, "Dark modal scrim should darken colorful players")

        val sceneLight = ImageComposeScene(width = 480, height = 640) {
            CompositionLocalProvider(LocalDesktopPalette provides DesktopLightPalette) {
                DesktopAudioPipelineDialog(
                    format = null,
                    sourceName = "YouTube Music",
                    pipeline = DesktopAudioPipeline(),
                    isPlaying = false,
                    onDismiss = {},
                )
            }
        }
        try {
            val image = sceneLight.render(16_000_000L)
            try {
                val bmp = image.toComposeImageBitmap()
                val centerPixel = IntArray(1)
                bmp.readPixels(centerPixel, startX = 240, startY = 320, width = 1, height = 1)
                assertTrue(
                    Color(centerPixel[0]).luminance() > 0.45f,
                    "Expected Light Audio Pipeline card center pixel to reflect light theme",
                )
            } finally {
                image.close()
            }
        } finally {
            sceneLight.close()
        }
    }

    @Test
    fun `AudioPipeline dialog playing animation inside self backdrop renders multi-frame Skia cleanly without recursion`() {
        val prevLiquid = DesktopAppearanceSettings.liquidGlass.value
        val prevReduceBlur = DesktopAppearanceSettings.reduceDynamicBlur.value
        val prevPerf = DesktopAppearanceSettings.glassPerformance.value
        val prevReduceAnim = DesktopAppearanceSettings.reduceAnimation.value
        try {
            DesktopAppearanceSettings.setLiquidGlass(true)
            DesktopAppearanceSettings.setReduceDynamicBlur(false)
            DesktopAppearanceSettings.setGlassPerformance(false)
            DesktopAppearanceSettings.setReduceAnimation(false)

            val scene = ImageComposeScene(width = 640, height = 640) {
                CompositionLocalProvider(LocalDesktopPalette provides DesktopLightPalette) {
                    val backdrop = rememberDesktopLayerBackdrop()
                    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
                    CompositionLocalProvider(LocalDesktopBackdrop provides backdrop) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(DesktopLightPalette.background)
                                .desktopLayerBackdrop(backdrop, enabled = true),
                        ) {
                            // Verify self-contained Liquid Glass guard never crashes or enters SkPicture cycle
                            Box(
                                Modifier
                                    .size(120.dp, 60.dp)
                                    .desktopLiquidGlass(
                                        shape = RoundedCornerShape(14.dp),
                                        backdrop = backdrop,
                                    ),
                            )
                            DesktopAudioPipelineDialog(
                                format = null,
                                sourceName = "YouTube Music",
                                pipeline = DesktopAudioPipeline(),
                                isPlaying = true,
                                hazeState = hazeState,
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
            try {
                listOf(0L, 16_000_000L, 32_000_000L, 64_000_000L, 128_000_000L, 256_000_000L).forEach { ts ->
                    scene.render(ts).close()
                }
            } finally {
                scene.close()
            }
        } finally {
            DesktopAppearanceSettings.setReduceAnimation(prevReduceAnim)
            DesktopAppearanceSettings.setGlassPerformance(prevPerf)
            DesktopAppearanceSettings.setReduceDynamicBlur(prevReduceBlur)
            DesktopAppearanceSettings.setLiquidGlass(prevLiquid)
        }
    }

    @Test
    fun `resolveHomeMoods expands Find Your Mood shelf across wide desktop screens`() {
        val compactMoods = com.music.bitchord.ui.screens.resolveHomeMoods(400.dp)
        val wideMoods = com.music.bitchord.ui.screens.resolveHomeMoods(1440.dp)

        assertEquals(7, compactMoods.size)
        assertEquals(com.music.bitchord.data.model.TopPickMood.entries.size, wideMoods.size)
        assertTrue(wideMoods.size > compactMoods.size)
    }

    @Test
    fun `Hero section gradients and icon ink adapt between Dark and Light themes`() {
        val heroes = listOf(HERO_HISTORY_COLORS, HERO_DOWNLOADS_COLORS, HERO_LOCAL_MUSIC_COLORS)
        heroes.forEach { darkHero ->
            val lightHero = resolveHeroColors(darkHero, isDark = false)
            val resolvedDark = resolveHeroColors(darkHero, isDark = true)
            assertEquals(darkHero, resolvedDark)
            val lightLum = lightHero.map { it.luminance() }.average()
            val darkLum = darkHero.map { it.luminance() }.average()
            assertTrue(lightLum > 0.50, "Light hero card gradient should be bright pastel, was $lightLum")
            assertTrue(darkLum < 0.25, "Dark hero card gradient should stay deep, was $darkLum")
            assertTrue(lightLum > darkLum + 0.35, "Light hero gradient ($lightLum) should exceed Dark ($darkLum)")
        }
        assertTrue(resolveHeroIconInk(isDark = false).luminance() < 0.05f)
        assertTrue(resolveHeroIconInk(isDark = true).luminance() > 0.90f)
    }
}
