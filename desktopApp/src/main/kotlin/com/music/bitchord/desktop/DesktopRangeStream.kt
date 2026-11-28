package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.StreamResolver
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Reads a remote file in bounded windows rather than as one open-ended request. */
internal class DesktopRangeStream(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val windowBytes: Int = DEFAULT_WINDOW,
    private val onHttpRefusal: (Int) -> Unit = { status -> StreamResolver.onPlaybackRefused(url, status) },
    private val prefetchNextWindow: Boolean = false,
) : AutoCloseable {

    private data class FetchedWindow(
        val windowStart: Long,
        val body: ByteArray,
        val totalLength: Long,
    )

    /** Total length in bytes, or -1 until the first response has stated one. */
    var length: Long = -1L
        private set

    private var position = 0L
    private var window = ByteArray(0)

    /** Where [window] starts in the file; -1 when nothing is held. */
    private var windowStart = -1L

    private val prefetchLock = Any()

    @Volatile
    private var closed = false
    private var generation = 0L
    private var prefetchInFlightStart = -1L
    private var prefetched: FetchedWindow? = null

    /** Where the next read will come from. */
    fun position(): Long = position

    fun seek(offset: Long) {
        val next = offset.coerceAtLeast(0)
        position = next
        if (!holds(next)) {
            synchronized(prefetchLock) {
                val cached = prefetched
                if (cached == null || next < cached.windowStart || next >= cached.windowStart + cached.body.size) {
                    generation++
                    prefetched = null
                    prefetchInFlightStart = -1L
                }
            }
        }
    }

    /**
     * Fills up to [count] bytes at the current position, returning how many — or -1 at the end of
     * the file.
     */
    fun read(into: ByteArray, count: Int): Int {
        if (count <= 0) return 0
        if (length >= 0 && position >= length) return -1
        if (!holds(position) && !fetchWindowFor(position)) return -1
        val offset = (position - windowStart).toInt()
        val available = window.size - offset
        if (available <= 0) return -1
        val taken = minOf(count, available)
        System.arraycopy(window, offset, into, 0, taken)
        position += taken
        if (prefetchNextWindow && !closed) {
            maybePrefetchNextWindow()
        }
        return taken
    }

    override fun close() {
        closed = true
        synchronized(prefetchLock) {
            generation++
            prefetched = null
            prefetchInFlightStart = -1L
        }
    }

    private fun holds(at: Long): Boolean =
        windowStart >= 0 && at >= windowStart && at < windowStart + window.size

    private fun maybePrefetchNextWindow() {
        val wStart = windowStart
        val wLen = window.size
        if (wStart < 0L || wLen <= 0) return
        if (position - wStart < wLen / 2) return
        val nextStart = wStart + wLen
        if (length >= 0L && nextStart >= length) return
        val activeGen = synchronized(prefetchLock) {
            val cached = prefetched
            if (cached != null && nextStart >= cached.windowStart && nextStart < cached.windowStart + cached.body.size) {
                return
            }
            if (prefetchInFlightStart >= 0L) return
            prefetchInFlightStart = nextStart
            generation
        }
        prefetchExecutor.execute {
            try {
                if (closed) return@execute
                val stale = synchronized(prefetchLock) { generation != activeGen }
                if (stale) return@execute
                val fetched = fetchRange(nextStart, reportRefusal = false) ?: return@execute
                if (closed || fetched.body.isEmpty()) return@execute
                synchronized(prefetchLock) {
                    if (!closed && generation == activeGen && prefetchInFlightStart == nextStart) {
                        prefetched = fetched
                    }
                }
            } finally {
                synchronized(prefetchLock) {
                    if (prefetchInFlightStart == nextStart) {
                        prefetchInFlightStart = -1L
                    }
                }
            }
        }
    }

    /** Fetches the window containing [at], reusing a prefetched next window when ready. */
    private fun fetchWindowFor(at: Long): Boolean {
        val hit = synchronized(prefetchLock) {
            val cached = prefetched
            if (cached != null && at >= cached.windowStart && at < cached.windowStart + cached.body.size) {
                prefetched = null
                cached
            } else {
                null
            }
        }
        if (hit != null) {
            windowStart = hit.windowStart
            window = hit.body
            if (hit.totalLength >= 0L) length = hit.totalLength
            if (length < 0L) length = windowStart + hit.body.size
            return hit.body.isNotEmpty()
        }

        val start = at - (at % windowBytes)
        val fetched = fetchRange(start, reportRefusal = true) ?: return false
        windowStart = fetched.windowStart
        window = fetched.body
        if (fetched.totalLength >= 0L) length = fetched.totalLength
        if (length < 0L) length = windowStart + fetched.body.size
        return fetched.body.isNotEmpty()
    }

    private fun fetchRange(start: Long, reportRefusal: Boolean): FetchedWindow? {
        val end = start + windowBytes - 1
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Range", "bytes=$start-$end")
            .header("Accept", "*/*")
            // Anything else would arrive compressed and the byte offsets the caller is counting in
            // would stop meaning anything.
            .header("Accept-Encoding", "identity")
        headers.forEach { (name, value) -> runCatching { request.header(name, value) } }

        val response = runCatching {
            client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        }.getOrNull() ?: return null
        if (response.statusCode() !in 200..299) {
            if (reportRefusal) onHttpRefusal(response.statusCode())
            return null
        }

        val body = response.body()
        // A server that honoured the range says where the bytes came from and how many there are in
        // total.
        val stated = response.headers().firstValue("content-range").orElse(null)
        val actualStart: Long
        val totalLen: Long
        if (stated != null) {
            actualStart = stated.substringAfter("bytes ").substringBefore('-').trim().toLongOrNull() ?: start
            totalLen = stated.substringAfterLast('/').trim().toLongOrNull() ?: -1L
        } else {
            actualStart = 0L
            totalLen = body.size.toLong()
        }
        return FetchedWindow(
            windowStart = actualStart,
            body = body,
            totalLength = totalLen,
        )
    }

    private companion object {
        /** Comfortably inside what Google will serve. */
        const val DEFAULT_WINDOW = 512 * 1024

        val client: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()

        private val prefetchThreadIndex = AtomicInteger(1)
        val prefetchExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "bitchord-range-prefetch-${prefetchThreadIndex.getAndIncrement()}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }
}
