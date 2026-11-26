package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal data class DesktopHardwareVolumeSnapshot(
    val controlName: String,
    val minDb: Double,
    val maxDb: Double,
    val currentDb: Double,
    val muted: Boolean = false,
) {
    val available: Boolean get() = maxDb > minDb
}

/**
 * Maps BitChord Desktop's 0..1 volume bar and keyboard volume shortcuts directly onto the DAC or
 * audio codec's hardware attenuator while Bit-Perfect exclusive output is open, without modifying
 * a single PCM sample in software (parallels mobile `DacVolume`).
 */
internal object DesktopDacVolume {

    /**
     * Step granularity for keyboard Up/Down adjustments (20 steps = 5% per press, matching desktop
     * shared mode).
     */
    const val STEPS = 20

    internal const val FLOOR_DB = -80.0

    private val _snapshot = MutableStateFlow<DesktopHardwareVolumeSnapshot?>(null)
    val snapshot: StateFlow<DesktopHardwareVolumeSnapshot?> = _snapshot.asStateFlow()

    private val _level = MutableStateFlow<Float?>(null)
    val level: StateFlow<Float?> = _level.asStateFlow()

    private var writer: ((db: Double, mute: Boolean) -> Double?)? = null
    private var reader: (() -> DesktopHardwareVolumeSnapshot?)? = null
    private var lastNonZeroLevel = 0.7f
    private val pendingTarget = AtomicReference<Pair<Double, Boolean>?>(null)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BitChord-DAC-volume").apply { isDaemon = true }
    }

    @Synchronized
    fun onOpened(
        initial: DesktopHardwareVolumeSnapshot?,
        setter: (db: Double, mute: Boolean) -> Double?,
        infoReader: (() -> DesktopHardwareVolumeSnapshot?)? = null,
    ) {
        if (initial == null || !initial.available) {
            onClosed()
            return
        }
        writer = setter
        reader = infoReader
        _snapshot.value = initial
        val frac = if (initial.muted) 0f else fractionOf(initial)
        if (frac > 0.01f) lastNonZeroLevel = frac
        _level.value = frac
    }

    @Synchronized
    fun onClosed() {
        writer = null
        reader = null
        pendingTarget.set(null)
        _snapshot.value = null
        _level.value = null
    }

    fun refresh() {
        val currentReader = synchronized(this) { reader } ?: return
        executor.execute {
            if (pendingTarget.get() != null) return@execute
            val latest = runCatching { currentReader() }.getOrNull()?.takeIf { it.available } ?: return@execute
            synchronized(this@DesktopDacVolume) {
                if (reader == null || pendingTarget.get() != null) return@synchronized
                _snapshot.value = latest
                _level.value = if (latest.muted) 0f else fractionOf(latest)
            }
        }
    }

    @Synchronized
    fun set(fraction: Float) {
        val snap = _snapshot.value?.takeIf { it.available } ?: return
        val setter = writer ?: return
        val target = fraction.coerceIn(0f, 1f)
        val mute = target <= 0.0001f
        if (!mute) lastNonZeroLevel = target
        val targetDb = dbOf(snap, target)
        _level.value = target
        _snapshot.value = snap.copy(currentDb = targetDb, muted = mute)

        if (pendingTarget.getAndSet(targetDb to mute) != null) return
        executor.execute {
            while (true) {
                val request = pendingTarget.getAndSet(null) ?: break
                val settledDb = runCatching { setter(request.first, request.second) }.getOrNull()
                if (pendingTarget.get() == null && settledDb != null && settledDb.isFinite()) {
                    synchronized(this@DesktopDacVolume) {
                        val current = _snapshot.value ?: return@synchronized
                        val updated = current.copy(currentDb = settledDb, muted = request.second)
                        _snapshot.value = updated
                        _level.value = if (request.second) 0f else fractionOf(updated)
                    }
                }
            }
        }
    }

    @Synchronized
    fun step(deltaFraction: Float): Float? {
        val current = _level.value ?: return null
        val stepIndex = (current * STEPS + deltaFraction * STEPS).roundToInt().coerceIn(0, STEPS)
        val next = stepIndex.toFloat() / STEPS
        set(next)
        return next
    }

    @Synchronized
    fun toggleMute(): Float? {
        val current = _level.value ?: return null
        val next = if (current > 0.001f) {
            lastNonZeroLevel = current
            0f
        } else {
            lastNonZeroLevel.coerceIn(0.05f, 1f)
        }
        set(next)
        return next
    }

    internal fun fractionOf(volume: DesktopHardwareVolumeSnapshot): Float {
        val floor = maxOf(volume.minDb, FLOOR_DB)
        if (volume.maxDb <= floor || volume.currentDb <= floor) return 0f
        return ((volume.currentDb - floor) / (volume.maxDb - floor)).toFloat().coerceIn(0f, 1f)
    }

    internal fun dbOf(volume: DesktopHardwareVolumeSnapshot, fraction: Float): Double {
        if (fraction <= 0f) return volume.minDb
        val floor = maxOf(volume.minDb, FLOOR_DB)
        return floor + fraction.coerceIn(0f, 1f) * (volume.maxDb - floor)
    }
}
