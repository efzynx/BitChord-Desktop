package com.music.bitchord.desktop

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** What [DesktopDecodeAhead] reads from: a decoder, or a stand-in for one in tests. */
internal interface DesktopSampleSource {
    /** The next block as interleaved floats, or null at the end. The array may be reused. */
    fun readSamples(): FloatArray?

    /** The next block as left-aligned 32-bit signed integers, or null when reading floats / EOF. */
    fun readIntSamples(): IntArray? = null

    /** How much of the array [readSamples] / [readIntSamples] returned is actually this block. */
    val sampleCount: Int

    fun seek(micros: Long): Boolean

    fun close()
}

/**
 * Decodes a track a few seconds ahead of the speakers, on a thread of its own.
 *
 * The audio thread used to call the decoder directly, and the decoder reads straight from the
 * network: a YouTube stream stops for a whole 512 KiB range request every half-minute or so, and a
 * lossless stream waits on its socket whenever the connection hiccups. WASAPI holds only a fifth
 * of a second, so any read slower than that was heard as a gap of a few milliseconds. Here those
 * waits land on this thread while the audio thread plays what was decoded earlier.
 *
 * Only this thread touches [source] once constructed — seeks and the close are handed to it — so
 * the native decoder never sees two threads.
 */
internal class DesktopDecodeAhead(
    private val source: DesktopSampleSource,
    val outputFormat: DesktopPcmFormat,
    val durationUs: Long?,
    val measuredFormat: DesktopStreamFormat?,
    /** How far ahead to decode, in samples across all channels. */
    private val aheadSamples: Int = outputFormat.sampleRate * outputFormat.channels * AHEAD_SECONDS,
    name: String = "BitChord-Decode",
) {

    private val lock = ReentrantLock()
    /** Signalled when there is something for the audio thread to take. */
    private val ready = lock.newCondition()
    /** Signalled when the worker has room, a seek, or a close to act on. */
    private val wake = lock.newCondition()

    private val queue = ArrayDeque<FloatArray>()
    private val intQueue = ArrayDeque<IntArray>()
    private val integerMode = !outputFormat.isFloat
    private var queuedSamples = 0
    private var ended = false
    private var closed = false
    private var pendingSeekUs: Long? = null
    /** Bumped by every seek, so a block decoded before it is never played after it. */
    private var generation = 0
    /** Whether a block has been handed out since the last seek; a wait before the first is a load. */
    private var started = false

    /** How much of the array [readSamples] returned is actually this block. */
    var sampleCount: Int = 0
        private set

    private val worker = Thread(::decodeLoop, name).apply {
        isDaemon = true
        // Above the UI, below the audio thread itself.
        priority = Thread.NORM_PRIORITY + 1
        start()
    }

    /** The next decoded block, waiting for the decoder only if it has fallen behind. */
    fun readSamples(): FloatArray? = lock.withLock {
        if (queue.isEmpty() && intQueue.isEmpty() && !ended && !closed) {
            val waitedFrom = System.nanoTime()
            while (queue.isEmpty() && intQueue.isEmpty() && !ended && !closed) ready.await()
            val waitedMs = (System.nanoTime() - waitedFrom) / 1_000_000
            if (started && waitedMs >= STARVED_LOG_MS) {
                DesktopTrackLog.log("decode-ahead ran dry: the audio thread waited ${waitedMs}ms for the decoder")
            }
        }
        val block = queue.removeFirstOrNull() ?: intQueue.removeFirstOrNull()?.let { ints ->
            FloatArray(ints.size) { index -> (ints[index] / 2_147_483_648.0).toFloat() }
        }
        if (block == null) {
            sampleCount = 0
            return null
        }
        queuedSamples -= block.size
        started = true
        wake.signal()
        sampleCount = block.size
        block
    }

    /** The next integer PCM block for bit-perfect output, or null at EOF. */
    fun readIntSamples(): IntArray? = lock.withLock {
        if (!integerMode) return@withLock null
        if (intQueue.isEmpty() && !ended && !closed) {
            val waitedFrom = System.nanoTime()
            while (intQueue.isEmpty() && !ended && !closed) ready.await()
            val waitedMs = (System.nanoTime() - waitedFrom) / 1_000_000
            if (started && waitedMs >= STARVED_LOG_MS) {
                DesktopTrackLog.log("decode-ahead ran dry: the audio thread waited ${waitedMs}ms for the decoder")
            }
        }
        val block = intQueue.removeFirstOrNull()
        if (block == null) {
            sampleCount = 0
            return null
        }
        queuedSamples -= block.size
        started = true
        wake.signal()
        sampleCount = block.size
        block
    }

    /** Moves to [micros]; whatever was decoded ahead of the old position is thrown away. */
    fun seek(micros: Long): Boolean {
        lock.withLock {
            generation++
            queue.clear()
            intQueue.clear()
            queuedSamples = 0
            ended = false
            started = false
            pendingSeekUs = micros
            wake.signal()
        }
        return true
    }

    /** Stops decoding; the decoder itself is closed by the worker, once any read in flight returns. */
    fun close() {
        lock.withLock {
            closed = true
            queue.clear()
            intQueue.clear()
            queuedSamples = 0
            wake.signal()
            ready.signalAll()
        }
    }

    private fun decodeLoop() {
        try {
            while (true) {
                var seekTo: Long? = null
                val decodingFor = lock.withLock {
                    while (!closed && pendingSeekUs == null && (ended || queuedSamples >= aheadSamples)) {
                        wake.await()
                    }
                    if (closed) return
                    seekTo = pendingSeekUs
                    pendingSeekUs = null
                    generation
                }
                val micros = seekTo
                if (micros != null) {
                    if (!source.seek(micros)) DesktopTrackLog.log("decode-ahead: seek to ${micros / 1_000}ms failed")
                    continue
                }

                if (integerMode) {
                    val decodedInt = try {
                        source.readIntSamples()?.copyOf(source.sampleCount)
                    } catch (failure: Exception) {
                        DesktopTrackLog.log("decode-ahead: decoder failed, ending the track: ${failure.message}")
                        null
                    }
                    lock.withLock {
                        if (decodingFor != generation) return@withLock
                        if (closed) return@withLock
                        if (decodedInt == null) {
                            ended = true
                        } else if (decodedInt.isNotEmpty()) {
                            intQueue.addLast(decodedInt)
                            queuedSamples += decodedInt.size
                        }
                        ready.signalAll()
                    }
                } else {
                    val decoded = try {
                        source.readSamples()?.copyOf(source.sampleCount)
                    } catch (failure: Exception) {
                        DesktopTrackLog.log("decode-ahead: decoder failed, ending the track: ${failure.message}")
                        null
                    }
                    lock.withLock {
                        // A seek came in while this block was being read; it belongs to the old position.
                        if (decodingFor != generation) return@withLock
                        // Close came in while this block was being read. Committing it would leave the
                        // queue non-empty after close, and the caller's very next readSamples() — which
                        // the contract says is null — would return this block instead. Seen on Windows
                        // CI, where scheduling lets the caller's close win this race consistently;
                        // Linux's timing hides it. The block dies with the closed reader.
                        if (closed) return@withLock
                        if (decoded == null) {
                            ended = true
                        } else if (decoded.isNotEmpty()) {
                            queue.addLast(decoded)
                            queuedSamples += decoded.size
                        }
                        ready.signalAll()
                    }
                }
            }
        } catch (_: InterruptedException) {
            // Closing the app; the decoder goes with the process.
        } finally {
            runCatching { source.close() }
        }
    }

    private companion object {
        /** Long enough to ride out a slow range request or a reconnect. */
        const val AHEAD_SECONDS = 8

        /** Waits shorter than this are ordinary scheduling, not a network stall worth logging. */
        const val STARVED_LOG_MS = 20L
    }
}
