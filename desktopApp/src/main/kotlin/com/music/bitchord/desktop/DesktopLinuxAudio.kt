package com.music.bitchord.desktop

/**
 * Direct ALSA `hw:card,dev` bit-perfect output bridge for Linux.
 *
 * Uses `bitchord_linux_audio` (dynamically bound to `libasound.so.2` at runtime) and coordinates
 * device release with PipeWire / PulseAudio via [DesktopLinuxAudioReservation].
 */
internal object DesktopLinuxAudio {

    internal data class OpenResult(
        val bufferBytes: Int,
        val msbInS32: Boolean,
        val bufferFrames: Int,
        val deviceName: String,
    )

    private val libraryLoaded: Boolean by lazy {
        DesktopPlatform.isLinux &&
            runCatching { DesktopAnalysisRuntime.loadNative(LIBRARY) }
                .onFailure { DesktopTrackLog.log("native Linux ALSA audio unavailable: ${it.message}") }
                .isSuccess
    }

    val available: Boolean
        get() = libraryLoaded && runCatching { nativeAvailable() }.getOrDefault(false)

    fun devices(): List<DesktopAudioDevice> {
        if (!available) return emptyList()
        return runCatching {
            nativeDevices().toList().chunked(2).mapNotNull { pair ->
                val id = pair.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val name = pair.getOrNull(1)?.ifBlank { id } ?: id
                DesktopAudioDevice(id = id, name = name, description = "ALSA Direct ($id)")
            }
        }.getOrDefault(emptyList())
    }

    fun probeSupportedRates(hwDevice: String, channels: Int, candidateRates: IntArray): IntArray {
        if (!available || hwDevice.isBlank() || candidateRates.isEmpty()) return intArrayOf()
        val reserved = DesktopLinuxAudioReservation.acquire(hwDevice)
        return try {
            runCatching { nativeProbeSupportedRates(hwDevice, channels, candidateRates) }
                .getOrDefault(intArrayOf())
        } finally {
            if (reserved) DesktopLinuxAudioReservation.release()
        }
    }

    fun openExclusive(
        hwDevice: String,
        friendlyName: String,
        sampleRate: Int,
        channels: Int,
        validBits: Int,
        containerBits: Int,
        tryMsbS32For24: Boolean,
    ): Result<OpenResult> = runCatching {
        check(available) { "ALSA direct audio library is unavailable" }
        val reserved = DesktopLinuxAudioReservation.acquire(hwDevice)
        var info = nativeOpen(
            hwDevice,
            friendlyName,
            sampleRate,
            channels,
            validBits,
            containerBits,
            tryMsbS32For24,
        )
        // Some audio servers take slightly longer to finish closing their handle after ReleaseDevice1.
        if (info.isEmpty() && reserved) {
            Thread.sleep(RETRY_DELAY_MS)
            info = nativeOpen(
                hwDevice,
                friendlyName,
                sampleRate,
                channels,
                validBits,
                containerBits,
                tryMsbS32For24,
            )
        }
        if (info.size < 3 || info[0] <= 0) {
            if (reserved) DesktopLinuxAudioReservation.release()
            error(nativeLastError().ifBlank { "ALSA could not open $hwDevice exclusively" })
        }
        OpenResult(
            bufferBytes = info[0],
            msbInS32 = info[1] != 0,
            bufferFrames = info[2],
            deviceName = friendlyName.ifBlank { hwDevice },
        )
    }

    fun writePcm(bytes: ByteArray, length: Int): Int =
        if (available) nativeWritePcm(bytes, length) else -1

    fun framesPlayed(): Long =
        if (available) nativeFramesPlayed() else 0L

    fun pause() {
        if (available) nativePause()
    }

    fun resume() {
        if (available) nativeResume()
    }

    fun flush() {
        if (available) nativeFlush()
    }

    fun drain() {
        if (available) nativeDrain()
    }

    fun close() {
        if (available) nativeClose()
        DesktopLinuxAudioReservation.release()
    }

    fun lastError(): String? =
        if (available) nativeLastError().takeIf(String::isNotBlank) else null

    fun hardwareVolumeInfo(): DesktopHardwareVolumeSnapshot? {
        if (!available) return null
        val name = runCatching { nativeHardwareVolumeName() }.getOrDefault("").trim()
        if (name.isEmpty()) return null
        val range = runCatching { nativeHardwareVolumeRange() }.getOrDefault(DoubleArray(0))
        if (range.size < 4 || range[1] <= range[0]) return null
        return DesktopHardwareVolumeSnapshot(
            controlName = name,
            minDb = range[0],
            maxDb = range[1],
            currentDb = range[2],
            muted = range[3] != 0.0,
        )
    }

    fun setHardwareVolumeDb(db: Double, mute: Boolean): Double? {
        if (!available) return null
        val settled = runCatching { nativeSetHardwareVolumeDb(db, mute) }.getOrDefault(Double.NaN)
        return settled.takeIf { it.isFinite() }
    }

    /**
     * Maps any desktop device selection (ALSA `hw:X,Y`, `plughw:X,Y`, JavaSound `plughw:X,Y` labels,
     * or friendly device name) to a direct ALSA `hw:X,Y` id present in [hwDevices].
     */
    fun resolveHwTarget(selectedId: String, hwDevices: List<DesktopAudioDevice>): DesktopAudioDevice? {
        if (hwDevices.isEmpty()) return null
        if (selectedId == DesktopAudioDevices.SYSTEM_DEFAULT) return hwDevices.first()
        parseAlsaCardAndDevice(selectedId)?.let { (card, dev) ->
            val targetId = "hw:$card,$dev"
            hwDevices.firstOrNull { it.id == targetId }?.let { return it }
            return DesktopAudioDevice(targetId, targetId, "ALSA Direct ($targetId)")
        }
        val lower = selectedId.lowercase()
        hwDevices.firstOrNull { it.id.equals(selectedId, ignoreCase = true) }?.let { return it }
        hwDevices.firstOrNull { device ->
            val devLower = device.name.lowercase()
            devLower == lower || devLower in lower || lower in devLower
        }?.let { return it }
        // Only fall back to card 0 when the user asked for a pulse/pipewire virtual default mixer.
        return if ("pulse" in lower || "pipewire" in lower || "default" in lower) {
            hwDevices.first()
        } else {
            null
        }
    }

    fun parseAlsaCard(spec: String): Int? =
        parseAlsaCardAndDevice(spec)?.first

    fun parseAlsaCardAndDevice(spec: String): Pair<Int, Int>? {
        val match = ALSA_HW_PATTERN.find(spec) ?: return null
        val card = match.groupValues[1].toIntOrNull() ?: return null
        val dev = match.groupValues[2].toIntOrNull() ?: 0
        return card to dev
    }

    @JvmStatic private external fun nativeAvailable(): Boolean
    @JvmStatic private external fun nativeDevices(): Array<String>
    @JvmStatic private external fun nativeProbeSupportedRates(
        hwDevice: String,
        channels: Int,
        candidateRates: IntArray,
    ): IntArray
    @JvmStatic private external fun nativeOpen(
        hwDevice: String,
        friendlyName: String,
        sampleRate: Int,
        channels: Int,
        validBits: Int,
        containerBits: Int,
        tryMsbS32For24: Boolean,
    ): IntArray
    @JvmStatic private external fun nativeWritePcm(bytes: ByteArray, length: Int): Int
    @JvmStatic private external fun nativeFramesPlayed(): Long
    @JvmStatic private external fun nativePause()
    @JvmStatic private external fun nativeResume()
    @JvmStatic private external fun nativeFlush()
    @JvmStatic private external fun nativeDrain()
    @JvmStatic private external fun nativeClose()
    @JvmStatic private external fun nativeLastError(): String
    @JvmStatic private external fun nativeHardwareVolumeName(): String
    @JvmStatic private external fun nativeHardwareVolumeRange(): DoubleArray
    @JvmStatic private external fun nativeSetHardwareVolumeDb(db: Double, mute: Boolean): Double

    private const val LIBRARY = "bitchord_linux_audio"
    private const val RETRY_DELAY_MS = 90L
    private val ALSA_HW_PATTERN = Regex("""(?:plug)?hw:(\d+)(?:,(\d+))?""", RegexOption.IGNORE_CASE)
}
