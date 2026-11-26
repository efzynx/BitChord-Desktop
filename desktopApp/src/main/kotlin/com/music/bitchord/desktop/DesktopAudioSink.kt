package com.music.bitchord.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/** How a bit-perfect exclusive stream opened on hardware. */
internal data class DesktopExclusiveOpenResult(
    val driver: String,
    val deviceName: String,
    val sampleRateHz: Int,
    val outputBits: Int,
    val containerBits: Int,
    val alsaLsb24: Boolean,
    val exact: Boolean,
    val dacLimited: Boolean,
)

/** The speakers, and the negotiation to reach them. */
internal class DesktopAudioSink {

    private var line: SourceDataLine? = null

    /** True while the Windows shared-mode or exclusive-mode WASAPI sink owns the output. */
    private var windowsOutput = false
    private var windowsExclusive = false

    /** True while Linux ALSA direct (`hw:X,Y`) owns the output. */
    private var linuxExclusive = false

    private var windowsBufferBytes = 0
    private var exclusiveBufferBytes = 0

    var exclusiveDriver: String? = null
        private set
    var alsaLsb24: Boolean = false
        private set

    /** The selection for which [line] was opened, including the blank system-default choice. */
    private var openedForSelection: String? = null

    /** Reused conversion buffer; only the playback thread touches it. */
    private var staging = ByteArray(0)

    /** Kept short: enough to distinguish real samples from a pipeline producing silence. */
    private var diagnosticBlocks = 0

    /** The format the mixer accepted, valid once [open] or [openExclusive] has succeeded. */
    var format: DesktopPcmFormat = DesktopPcmFormat(44_100, 2, 2)
        private set

    /** Playback gain, applied to the samples rather than to a mixer control. */
    @Volatile
    var gain: Float = 1f

    val isExclusive: Boolean get() = windowsExclusive || linuxExclusive
    val isOpen: Boolean get() = windowsOutput || linuxExclusive || line != null

    /** Whether the live line already belongs to the choice currently shown in the player. */
    fun isUsingSelection(id: String): Boolean = isOpen && openedForSelection == id

    /** Opens the best line the mixer will give for [requested]. */
    fun open(requested: DesktopPcmFormat): Result<DesktopPcmFormat> = runCatching {
        DesktopDacVolume.onClosed()
        if (linuxExclusive) {
            DesktopLinuxAudio.close()
            linuxExclusive = false
            exclusiveBufferBytes = 0
            exclusiveDriver = null
            alsaLsb24 = false
        }
        windowsExclusive = false
        val selectedDevice = DesktopAudioDevices.selected.value
        if (DesktopPlatform.isWindows) {
            val native = DesktopWindowsAudio.open(selectedDevice, requested)
            if (native.isSuccess) {
                val previous = line
                line = null
                windowsOutput = true
                windowsExclusive = false
                exclusiveDriver = null
                alsaLsb24 = false
                windowsBufferBytes = native.getOrThrow()
                // Read live from the native side instead: it moves the stream itself when the
                // Windows default changes or a chosen device is unplugged.
                openedOn = null
                openedForSelection = selectedDevice
                format = requested
                diagnosticBlocks = 0
                closeLine(previous)
                DesktopTrackLog.log(
                    "audio output opened: ${deviceName ?: "Windows system default"} · " +
                        "${requested.sampleRate} Hz · ${requested.channels} ch · WASAPI shared",
                )
                return@runCatching requested
            }
            DesktopTrackLog.log("WASAPI output unavailable; trying Java Sound: ${native.exceptionOrNull()?.message}")
        }

        if (windowsOutput) {
            DesktopWindowsAudio.close()
            windowsOutput = false
            windowsExclusive = false
            windowsBufferBytes = 0
        }
        val ladder = buildList {
            add(requested)
            if (requested.isFloat) add(requested.copy(bytesPerSample = 2, isFloat = false))
            // A device that will not take the source's own rate is rare, but a 44.1k-only or
            // 48k-only card does exist and silence is not the right answer for it.
            for (rate in intArrayOf(48_000, 44_100)) {
                if (rate != requested.sampleRate) {
                    add(requested.copy(sampleRate = rate))
                    add(requested.copy(sampleRate = rate, bytesPerSample = 2, isFloat = false))
                }
            }
            if (requested.channels != 2) add(requested.copy(channels = 2, bytesPerSample = 2, isFloat = false))
        }

        // The chosen device, when there is one and it is still plugged in; otherwise whatever the
        // system calls the default.
        val mixer = DesktopAudioDevices.mixerFor(selectedDevice)
        val supports: (DesktopPcmFormat) -> Boolean = { candidate ->
            if (mixer == null) {
                AudioSystem.isLineSupported(infoFor(candidate))
            } else {
                runCatching { mixer.isLineSupported(infoFor(candidate)) }.getOrDefault(false)
            }
        }
        val accepted = ladder.firstOrNull(supports)
            ?: error("no audio line for any supported format")

        val opened = if (mixer == null) {
            AudioSystem.getLine(infoFor(accepted)) as SourceDataLine
        } else {
            mixer.getLine(infoFor(accepted)) as SourceDataLine
        }
        try {
            // Roughly a fifth of a second in the device's hands.
            opened.open(accepted.toAudioFormat(), accepted.byteRate / 5)
            opened.start()
        } catch (failure: Throwable) {
            runCatching { opened.close() }
            throw failure
        }

        // Do not tear down the working route until its replacement is known to have opened. A
        // Bluetooth device can disappear between enumeration and this call; that must not turn a
        // failed switch into silent playback on every other device too.
        val previous = line
        line = opened
        openedOn = mixer?.mixerInfo?.name
        openedForSelection = selectedDevice
        format = accepted
        diagnosticBlocks = 0
        closeLine(previous)
        DesktopTrackLog.log(
            "audio output opened: ${deviceName ?: "system default"} · " +
                "${accepted.sampleRate} Hz · ${accepted.channels} ch · " +
                (if (accepted.isFloat) "32-bit float" else "${accepted.bytesPerSample * 8}-bit PCM"),
        )
        accepted
    }

    /** Opens an exclusive bit-perfect hardware path on Windows (WASAPI) or Linux (ALSA Direct). */
    fun openExclusive(
        requested: DesktopPcmFormat,
        validBits: Int,
        convertForLimitedDac: Boolean,
    ): Result<DesktopExclusiveOpenResult> = runCatching {
        val selectedDevice = DesktopAudioDevices.selected.value
        val bitCandidates = exclusiveBitCandidates(validBits, convertForLimitedDac)

        if (DesktopPlatform.isWindows) {
            val candidateRates = (listOf(requested.sampleRate) + DesktopBitPerfectPcm.STANDARD_RATES).distinct().toIntArray()
            val probeBits = if (validBits <= 16) 16 else validBits.coerceAtMost(32)
            val probeContainer = if (probeBits == 16) 16 else 32
            val probed = DesktopWindowsAudio.probeExclusiveRates(
                selectedDevice,
                requested.channels,
                probeBits,
                probeContainer,
                candidateRates,
            ).toList()
            val offered = probed.ifEmpty {
                if (convertForLimitedDac) DesktopBitPerfectPcm.STANDARD_RATES else listOf(requested.sampleRate)
            }
            val rankedRates = if (convertForLimitedDac) {
                DesktopBitPerfectPcm.rankRates(requested.sampleRate, offered)
                    .ifEmpty { listOf(requested.sampleRate) }
            } else {
                listOf(requested.sampleRate)
            }

            var lastError: Throwable? = null
            for (rate in rankedRates) {
                for ((vb, cb, _) in bitCandidates) {
                    val attempt = DesktopWindowsAudio.openExclusive(
                        device = selectedDevice,
                        sampleRate = rate,
                        channels = requested.channels,
                        validBits = vb,
                        containerBits = cb,
                    )
                    if (attempt.isSuccess) {
                        val previous = line
                        line = null
                        if (linuxExclusive) {
                            DesktopLinuxAudio.close()
                            linuxExclusive = false
                        }
                        windowsOutput = true
                        windowsExclusive = true
                        val buf = attempt.getOrThrow()
                        windowsBufferBytes = buf
                        exclusiveBufferBytes = buf
                        exclusiveDriver = "WASAPI Exclusive"
                        alsaLsb24 = false
                        openedOn = DesktopWindowsAudio.deviceName() ?: "Windows Audio"
                        openedForSelection = selectedDevice
                        format = DesktopPcmFormat(rate, requested.channels, cb / 8, isFloat = false, validBitsPerSample = vb)
                        diagnosticBlocks = 0
                        closeLine(previous)
                        val exact = rate == requested.sampleRate && vb >= validBits
                        val name = openedOn ?: "Windows Audio"
                        DesktopDacVolume.onOpened(
                            DesktopWindowsAudio.hardwareVolumeInfo(),
                            DesktopWindowsAudio::setHardwareVolumeDb,
                            DesktopWindowsAudio::hardwareVolumeInfo,
                        )
                        DesktopTrackLog.log(
                            "bit-perfect opened: $name · $rate Hz · $vb-bit ($cb-bit container) · WASAPI Exclusive",
                        )
                        return@runCatching DesktopExclusiveOpenResult(
                            driver = "WASAPI Exclusive",
                            deviceName = name,
                            sampleRateHz = rate,
                            outputBits = vb,
                            containerBits = cb,
                            alsaLsb24 = false,
                            exact = exact,
                            dacLimited = !exact,
                        )
                    } else {
                        lastError = attempt.exceptionOrNull()
                    }
                }
            }
            error(lastError?.message ?: "WASAPI exclusive could not open $validBits-bit/${requested.sampleRate}Hz")
        }

        if (DesktopPlatform.isLinux) {
            check(DesktopLinuxAudio.available) { "ALSA direct library is unavailable" }
            val hwDevices = DesktopLinuxAudio.devices()
            val hwTarget = DesktopLinuxAudio.resolveHwTarget(selectedDevice, hwDevices)
                ?: error("Selected output is not an ALSA hardware device")
            // Close any open shared PipeWire/JavaSound line before requesting D-Bus card ownership.
            close()

            val probeList = (listOf(requested.sampleRate) + DesktopBitPerfectPcm.STANDARD_RATES).distinct().toIntArray()
            val probed = DesktopLinuxAudio.probeSupportedRates(hwTarget.id, requested.channels, probeList).toList()
            val offered = probed.ifEmpty {
                if (convertForLimitedDac) DesktopBitPerfectPcm.STANDARD_RATES else listOf(requested.sampleRate)
            }
            val rankedRates = if (convertForLimitedDac) {
                DesktopBitPerfectPcm.rankRates(requested.sampleRate, offered)
                    .ifEmpty { listOf(requested.sampleRate) }
            } else {
                listOf(requested.sampleRate)
            }

            var lastError: Throwable? = null
            for (rate in rankedRates) {
                for ((vb, cb, msbS32) in bitCandidates) {
                    val attempt = DesktopLinuxAudio.openExclusive(
                        hwDevice = hwTarget.id,
                        friendlyName = hwTarget.name,
                        sampleRate = rate,
                        channels = requested.channels,
                        validBits = vb,
                        containerBits = cb,
                        tryMsbS32For24 = msbS32,
                    )
                    if (attempt.isSuccess) {
                        val openRes = attempt.getOrThrow()
                        linuxExclusive = true
                        windowsOutput = false
                        windowsExclusive = false
                        exclusiveBufferBytes = openRes.bufferBytes
                        exclusiveDriver = "ALSA Direct (${hwTarget.id})"
                        alsaLsb24 = vb == 24 && cb == 32 && !openRes.msbInS32
                        openedOn = openRes.deviceName
                        openedForSelection = selectedDevice
                        format = DesktopPcmFormat(rate, requested.channels, cb / 8, isFloat = false, validBitsPerSample = vb)
                        diagnosticBlocks = 0
                        val exact = rate == requested.sampleRate && vb >= validBits
                        DesktopDacVolume.onOpened(
                            DesktopLinuxAudio.hardwareVolumeInfo(),
                            DesktopLinuxAudio::setHardwareVolumeDb,
                            DesktopLinuxAudio::hardwareVolumeInfo,
                        )
                        DesktopTrackLog.log(
                            "bit-perfect opened: ${openRes.deviceName} (${hwTarget.id}) · $rate Hz · " +
                                "$vb-bit ($cb-bit ${if (openRes.msbInS32) "S32_LE" else "ALSA"})",
                        )
                        return@runCatching DesktopExclusiveOpenResult(
                            driver = "ALSA Direct (${hwTarget.id})",
                            deviceName = openRes.deviceName,
                            sampleRateHz = rate,
                            outputBits = vb,
                            containerBits = cb,
                            alsaLsb24 = alsaLsb24,
                            exact = exact,
                            dacLimited = !exact,
                        )
                    } else {
                        lastError = attempt.exceptionOrNull()
                    }
                }
            }
            error(lastError?.message ?: "ALSA ${hwTarget.id} rejected $validBits-bit / ${requested.sampleRate} Hz")
        }

        error("Bit-Perfect exclusive output requires Windows WASAPI or Linux ALSA Direct")
    }

    /** Ordered `(validBits, containerBits, tryMsbS32For24)` candidates from most exact downward. */
    private fun exclusiveBitCandidates(validBits: Int, allowLimited: Boolean): List<Triple<Int, Int, Boolean>> =
        when {
            validBits <= 16 -> listOf(
                Triple(16, 16, false),
                Triple(24, 32, true),
                Triple(24, 32, false),
                Triple(32, 32, false),
            )
            validBits <= 24 -> buildList {
                add(Triple(24, 32, true))
                add(Triple(24, 32, false))
                add(Triple(24, 24, false))
                add(Triple(32, 32, false))
                if (allowLimited) add(Triple(16, 16, false))
            }
            else -> buildList {
                add(Triple(32, 32, false))
                if (allowLimited) {
                    add(Triple(24, 32, true))
                    add(Triple(24, 32, false))
                    add(Triple(16, 16, false))
                }
            }
        }

    /**
     * What the samples are actually being written to.
     *
     * The mixer's own name, not `lineInfo`, which describes the line's capabilities — "interface
     * SourceDataLine supporting 36 audio formats" is not a device.
     */
    val deviceName: String?
        get() = runCatching {
            when {
                linuxExclusive -> openedOn
                windowsOutput -> DesktopWindowsAudio.deviceName()
                else -> line?.let { openedOn ?: AudioSystem.getMixer(null).mixerInfo?.name?.takeIf(String::isNotBlank) }
            }
        }.getOrNull()

    /** The mixer this line was actually opened on, when it was not the system default. */
    private var openedOn: String? = null

    /** How much audio the device is holding, in bytes; 0 before the line is open. */
    val bufferBytes: Int
        get() = when {
            linuxExclusive -> exclusiveBufferBytes
            windowsOutput -> windowsBufferBytes
            else -> runCatching { line?.bufferSize ?: 0 }.getOrDefault(0)
        }

    /** Writes [count] interleaved samples, blocking until the device takes them. */
    fun write(samples: FloatArray, count: Int): Int {
        if (windowsOutput) {
            val written = DesktopWindowsAudio.write(samples, count, gain)
            diagnose(samples, count, written)
            return written
        }
        val target = line ?: return 0
        if (count <= 0) return 0
        val bytes = convert(samples, count)
        val written = target.write(bytes, 0, count * format.bytesPerSample)
        diagnose(samples, count, written)
        return written
    }

    /** Writes [byteCount] pre-packed little-endian PCM bytes with zero scaling or DSP. */
    fun writePcmBytes(bytes: ByteArray, byteCount: Int): Int {
        if (byteCount <= 0) return 0
        return when {
            windowsExclusive -> DesktopWindowsAudio.writePcm(bytes, byteCount)
            linuxExclusive -> DesktopLinuxAudio.writePcm(bytes, byteCount)
            else -> line?.write(bytes, 0, byteCount) ?: -1
        }
    }

    private fun diagnose(samples: FloatArray, count: Int, written: Int) {
        if (diagnosticBlocks < DIAGNOSTIC_BLOCK_LIMIT) {
            diagnosticBlocks++
            val peak = (0 until minOf(count, samples.size)).maxOfOrNull { kotlin.math.abs(samples[it]) } ?: 0f
            if (peak > AUDIBLE_SIGNAL_FLOOR || diagnosticBlocks == DIAGNOSTIC_BLOCK_LIMIT) {
                DesktopTrackLog.log(
                    "audio output signal: peak=${"%.4f".format(java.util.Locale.ROOT, peak)} " +
                        "gain=${"%.2f".format(java.util.Locale.ROOT, gain)} wrote=$written bytes",
                )
                diagnosticBlocks = DIAGNOSTIC_BLOCK_LIMIT
            }
        }
    }

    /** Floats to whatever the device accepted, scaled by [gain]. */
    private fun convert(samples: FloatArray, count: Int): ByteArray {
        val current = format
        val volume = gain
        val bytes = count * current.bytesPerSample
        if (staging.size < bytes) staging = ByteArray(bytes)
        val out = staging

        when {
            current.isFloat -> for (index in 0 until count) {
                writeIntLe(out, index * 4, (samples[index] * volume).toRawBits())
            }
            current.bytesPerSample == 4 -> for (index in 0 until count) {
                val scaled = (samples[index] * volume).coerceIn(-1f, 1f)
                writeIntLe(out, index * 4, (scaled * Int.MAX_VALUE).toInt())
            }
            else -> for (index in 0 until count) {
                val scaled = (samples[index] * volume).coerceIn(-1f, 1f)
                val value = (scaled * 32_767f).toInt()
                val at = index * 2
                out[at] = value.toByte()
                out[at + 1] = (value shr 8).toByte()
            }
        }
        return out
    }

    private fun writeIntLe(buffer: ByteArray, at: Int, value: Int) {
        buffer[at] = value.toByte()
        buffer[at + 1] = (value shr 8).toByte()
        buffer[at + 2] = (value shr 16).toByte()
        buffer[at + 3] = (value shr 24).toByte()
    }

    /** Frames the device has actually rendered — the honest playback position. */
    fun framesPlayed(): Long = when {
        linuxExclusive -> DesktopLinuxAudio.framesPlayed()
        windowsOutput -> DesktopWindowsAudio.framesPlayed()
        else -> line?.longFramePosition ?: 0L
    }

    fun pause() {
        when {
            linuxExclusive -> DesktopLinuxAudio.pause()
            windowsOutput -> DesktopWindowsAudio.pause()
            else -> line?.stop()
        }
    }

    fun resume() {
        when {
            linuxExclusive -> DesktopLinuxAudio.resume()
            windowsOutput -> DesktopWindowsAudio.resume()
            else -> line?.start()
        }
    }

    /** Throws away what is queued, so a seek is heard at once. */
    fun flush() {
        when {
            linuxExclusive -> DesktopLinuxAudio.flush()
            windowsOutput -> DesktopWindowsAudio.flush()
            else -> line?.flush()
        }
    }

    /** Waits for the queue to finish, so the end of a track is not clipped. */
    fun drain() {
        when {
            linuxExclusive -> DesktopLinuxAudio.drain()
            windowsOutput -> DesktopWindowsAudio.drain()
            else -> runCatching { line?.drain() }
        }
    }

    fun close() {
        DesktopDacVolume.onClosed()
        if (linuxExclusive) {
            DesktopLinuxAudio.close()
            linuxExclusive = false
            exclusiveBufferBytes = 0
            exclusiveDriver = null
            alsaLsb24 = false
            openedOn = null
            openedForSelection = null
            diagnosticBlocks = 0
            return
        }
        if (windowsOutput) {
            DesktopWindowsAudio.close()
            windowsOutput = false
            windowsExclusive = false
            windowsBufferBytes = 0
            exclusiveBufferBytes = 0
            exclusiveDriver = null
            alsaLsb24 = false
            openedOn = null
            openedForSelection = null
            diagnosticBlocks = 0
            return
        }
        val target = line ?: return
        line = null
        openedOn = null
        openedForSelection = null
        diagnosticBlocks = 0
        closeLine(target)
    }

    private fun closeLine(target: SourceDataLine?) {
        if (target == null) return
        runCatching {
            target.stop()
            target.flush()
            target.close()
        }
    }

    private fun infoFor(candidate: DesktopPcmFormat) =
        DataLine.Info(SourceDataLine::class.java, candidate.toAudioFormat())

    private companion object {
        const val DIAGNOSTIC_BLOCK_LIMIT = 50
        const val AUDIBLE_SIGNAL_FLOOR = 0.001f
    }
}

/** The same format in JavaSound's vocabulary. */
internal fun DesktopPcmFormat.toAudioFormat(): AudioFormat = AudioFormat(
    if (isFloat) AudioFormat.Encoding.PCM_FLOAT else AudioFormat.Encoding.PCM_SIGNED,
    sampleRate.toFloat(),
    bytesPerSample * 8,
    channels,
    frameBytes,
    sampleRate.toFloat(),
    false,
)
