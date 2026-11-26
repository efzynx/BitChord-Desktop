package com.music.bitchord.desktop

import java.awt.GraphicsEnvironment
import kotlin.math.abs

/**
 * Queries physical display refresh rates and applies desktop frame-pacing preferences for High
 * Performance Mode (desktop equivalent of mobile's Display refresh rate mode).
 */
internal object DesktopDisplayPerformance {

    private const val MIN_REFRESH_RATE = 60
    private const val MAX_REFRESH_RATE = 360

    fun supportedRefreshRates(): List<Int> = runCatching {
        if (GraphicsEnvironment.isHeadless()) return@runCatching listOf(MIN_REFRESH_RATE)
        val rates = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .flatMap { device ->
                val current = device.displayMode?.refreshRate ?: 0
                val modes = runCatching { device.displayModes.map { it.refreshRate } }.getOrDefault(emptyList())
                modes + current
            }
            .filter { it in MIN_REFRESH_RATE..MAX_REFRESH_RATE }
            .distinct()
            .sorted()
        rates.ifEmpty { listOf(MIN_REFRESH_RATE) }
    }.getOrDefault(listOf(MIN_REFRESH_RATE))

    fun defaultPreferredRefreshRate(rates: List<Int> = supportedRefreshRates()): Int =
        rates.maxOrNull() ?: MIN_REFRESH_RATE

    fun resolveRefreshRate(preferred: Int, rates: List<Int> = supportedRefreshRates()): Int =
        rates.minByOrNull { abs(it - preferred) } ?: MIN_REFRESH_RATE

    fun applyFramePacing(enabled: Boolean, refreshRate: Int) {
        // Skiko reads vsync & frame-limit hints safely across Linux/Windows/macOS without throwing on headless.
        runCatching {
            if (enabled) {
                System.setProperty("skiko.vsync.enabled", "true")
                System.setProperty("compose.swing.render.on.graphics", "true")
            } else {
                System.setProperty("skiko.vsync.enabled", "true")
            }
        }
    }
}
