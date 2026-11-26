package com.music.bitchord.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import bitchord.desktopapp.generated.resources.Res
import com.music.bitchord.ui.graphics.paletteSwatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.svg.SVGDOM
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a 1080×1920 (9:16) BitChord Replay poster on Skia for sharing and exporting on Desktop,
 * matching mobile `ReplayPoster.kt` visual hierarchy and palette tuning.
 */
internal object DesktopReplayPoster {

    private const val POSTER_W = 1080
    private const val POSTER_H = 1920
    private const val MARGIN = 72f
    private const val CONTENT_W = POSTER_W - MARGIN * 2
    private const val LOGO_W = 66f
    private const val LOGO_H = 44f
    private const val LOGO_GAP = 18f
    private const val POSTER_ROWS = 5
    private const val POSTER_ALBUMS = 3
    private const val COLUMN_GAP = 36f
    private const val ROW_HEIGHT = 116f
    private const val ART = 84f
    private const val ACCENT = 0xFFFA2D48.toInt()

    data class RenderedPoster(
        val preview: ImageBitmap,
        val pngBytes: ByteArray,
    )

    suspend fun render(
        summary: DesktopReplaySummary,
        holder: String,
        memberSince: String?,
    ): RenderedPoster = coroutineScope {
        val songs = summary.songRows(POSTER_ROWS)
        val artists = summary.artistRows(POSTER_ROWS)
        val albums = summary.albumRows(POSTER_ALBUMS)

        val covers = (songs + artists + albums)
            .mapNotNull { it.artworkUrl }
            .distinct()
            .associateWith { url -> async(Dispatchers.IO) { DesktopArtworkCache.load(url) } }
            .mapValues { it.value.await() }

        val fonts = loadFonts()
        val logoBytes = runCatching { Res.readBytes("drawable/logo_mark.svg") }.getOrNull()

        withContext(Dispatchers.Default) {
            renderOnSkia(summary, holder, memberSince, songs, artists, albums, covers, fonts, logoBytes)
        }
    }

    internal fun renderOnSkia(
        summary: DesktopReplaySummary,
        holder: String,
        memberSince: String?,
        songs: List<DesktopReplayRow> = summary.songRows(POSTER_ROWS),
        artists: List<DesktopReplayRow> = summary.artistRows(POSTER_ROWS),
        albums: List<DesktopReplayRow> = summary.albumRows(POSTER_ALBUMS),
        covers: Map<String, ImageBitmap?> = emptyMap(),
        fonts: Fonts = defaultSystemFonts(),
        logoSvgBytes: ByteArray? = null,
    ): RenderedPoster {
        val surface = Surface.makeRasterN32Premul(POSTER_W, POSTER_H)
        try {
            val canvas = surface.canvas
            val leadCover = songs.firstOrNull()?.artworkUrl?.let { covers[it] }
            drawBackdrop(canvas, leadCover)

            var y = drawHeader(canvas, fonts, summary, holder, memberSince, logoSvgBytes)
            y = drawTotals(canvas, fonts, summary, y)
            y = drawColumns(canvas, fonts, songs, artists, covers, y)
            y = drawAlbums(canvas, fonts, albums, covers, y)
            drawHabitsStrip(canvas, fonts, summary, y)
            drawFooter(canvas, fonts)

            val snapshot = surface.makeImageSnapshot()
            val pngData = snapshot.encodeToData(EncodedImageFormat.PNG)
                ?: error("Could not encode Replay poster PNG")
            return RenderedPoster(
                preview = snapshot.toComposeImageBitmap(),
                pngBytes = pngData.bytes,
            )
        } finally {
            surface.close()
        }
    }

    private fun drawBackdrop(canvas: Canvas, lead: ImageBitmap?) {
        val colors = paletteOf(lead)
        canvas.clear(dimmed(colors.first()))

        val anchors = listOf(
            0.20f to 0.16f,
            0.84f to 0.22f,
            0.76f to 0.66f,
            0.18f to 0.78f,
        )
        val paint = Paint().apply { isAntiAlias = true }
        try {
            colors.forEachIndexed { index, color ->
                val (fx, fy) = anchors[index]
                val cx = POSTER_W * fx
                val cy = POSTER_H * fy
                val radius = POSTER_W * 0.95f
                paint.shader = Shader.makeRadialGradient(
                    x = cx,
                    y = cy,
                    radius = radius,
                    gradient = Gradient(
                        Gradient.Colors(
                            colors = arrayOf(Color4f(withAlpha(color, 210)), Color4f(withAlpha(color, 0))),
                            positions = floatArrayOf(0f, 1f),
                            tileMode = FilterTileMode.CLAMP,
                        ),
                    ),
                )
                canvas.drawCircle(cx, cy, radius, paint)
            }

            paint.shader = Shader.makeLinearGradient(
                x0 = 0f,
                y0 = 0f,
                x1 = 0f,
                y1 = POSTER_H.toFloat(),
                gradient = Gradient(
                    Gradient.Colors(
                        colors = arrayOf(
                            Color4f(0x8C000000.toInt()),
                            Color4f(0x59000000),
                            Color4f(0xCC000000.toInt()),
                        ),
                        positions = floatArrayOf(0f, 0.42f, 1f),
                        tileMode = FilterTileMode.CLAMP,
                    ),
                ),
            )
            canvas.drawRect(Rect.makeWH(POSTER_W.toFloat(), POSTER_H.toFloat()), paint)
        } finally {
            paint.close()
        }
    }

    private fun drawHeader(
        canvas: Canvas,
        fonts: Fonts,
        summary: DesktopReplaySummary,
        holder: String,
        memberSince: String?,
        logoSvgBytes: ByteArray?,
    ): Float {
        val label = summary.label.ifBlank { summary.period.chip }
        val title = if (label.length == 4 && label.all { it.isDigit() }) {
            "Replay ${label.takeLast(2)}"
        } else {
            "Replay · $label"
        }
        drawText(canvas, title, MARGIN, 132f, fonts.heading(46f), 0xFFFFFFFF.toInt())

        val brandFont = fonts.heading(46f)
        val brandWidth = brandFont.measureTextWidth("BitChord")
        drawText(canvas, "BitChord", POSTER_W - MARGIN - brandWidth, 132f, brandFont, 0xE6FFFFFF.toInt())

        if (logoSvgBytes != null) {
            runCatching {
                val dom = SVGDOM(Data.makeFromBytes(logoSvgBytes))
                val logoLeft = POSTER_W - MARGIN - brandWidth - LOGO_GAP - LOGO_W
                val logoTop = 132f - LOGO_H
                canvas.save()
                canvas.translate(logoLeft, logoTop)
                dom.setContainerSize(LOGO_W, LOGO_H)
                dom.render(canvas)
                canvas.restore()
            }
        }

        val defaultHolder = DesktopStrings["default_replay_holder", "BitChord listener"]
        val credit = listOfNotNull(
            holder.ifBlank { defaultHolder },
            memberSince?.let { DesktopStrings.format("member_since_value", it, fallback = "Member since %1\$s") },
        ).joinToString(" · ")
        drawText(canvas, credit.uppercase(Locale.ROOT), MARGIN, 182f, fonts.body(24f, bold = true), 0x8CFFFFFF.toInt())
        return 300f
    }

    private fun drawTotals(
        canvas: Canvas,
        fonts: Fonts,
        summary: DesktopReplaySummary,
        top: Float,
    ): Float {
        drawText(canvas, grouped(summary.minutes), MARGIN, top + 120f, fonts.heading(148f), 0xFFFFFFFF.toInt())
        drawText(
            canvas,
            DesktopStrings["minutes_listened", "Minutes listened"].uppercase(Locale.getDefault()),
            MARGIN,
            top + 176f,
            fonts.body(30f, bold = true),
            0xB3FFFFFF.toInt(),
        )
        val playsText = if (summary.totalPlays == 1) "1 play" else "${grouped(summary.totalPlays.toLong())} plays"
        val songsText = if (summary.distinctSongs == 1) "1 song" else "${grouped(summary.distinctSongs.toLong())} songs"
        val artistsText = if (summary.distinctArtists == 1) "1 artist" else "${grouped(summary.distinctArtists.toLong())} artists"
        drawText(
            canvas,
            "$playsText · $songsText · $artistsText",
            MARGIN,
            top + 232f,
            fonts.body(30f),
            0x99FFFFFF.toInt(),
        )
        return top + 320f
    }

    private fun drawColumns(
        canvas: Canvas,
        fonts: Fonts,
        songs: List<DesktopReplayRow>,
        artists: List<DesktopReplayRow>,
        covers: Map<String, ImageBitmap?>,
        top: Float,
    ): Float {
        val columnWidth = (POSTER_W - MARGIN * 2 - COLUMN_GAP) / 2f
        val right = MARGIN + columnWidth + COLUMN_GAP
        val headerFont = fonts.body(28f, bold = true)
        drawText(
            canvas,
            DesktopStrings["top_songs", "Top songs"].uppercase(Locale.getDefault()),
            MARGIN,
            top,
            headerFont,
            0xB3FFFFFF.toInt(),
        )
        drawText(
            canvas,
            DesktopStrings["top_artists", "Top artists"].uppercase(Locale.getDefault()),
            right,
            top,
            headerFont,
            0xB3FFFFFF.toInt(),
        )

        val rows = maxOf(songs.size, artists.size)
        var y = top + 54f
        repeat(rows) { index ->
            songs.getOrNull(index)?.let { drawRow(canvas, fonts, it, covers, MARGIN, y, columnWidth, circular = false) }
            artists.getOrNull(index)?.let { drawRow(canvas, fonts, it, covers, right, y, columnWidth, circular = true) }
            y += ROW_HEIGHT
        }
        return y + 40f
    }

    private fun drawRow(
        canvas: Canvas,
        fonts: Fonts,
        row: DesktopReplayRow,
        covers: Map<String, ImageBitmap?>,
        x: Float,
        y: Float,
        width: Float,
        circular: Boolean,
    ) {
        drawText(
            canvas,
            row.rank.toString(),
            x,
            y + ART * 0.68f,
            fonts.heading(34f),
            if (row.rank == 1) ACCENT else 0x73FFFFFF,
        )
        val artX = x + 44f
        drawArtwork(canvas, row.artworkUrl?.let { covers[it] }, row.title, artX, y, ART, circular)

        val textX = artX + ART + 20f
        val textWidth = (width - (textX - x)).coerceAtLeast(40f)
        val titleFont = fonts.body(30f, bold = true)
        drawText(canvas, ellipsised(row.title, titleFont, textWidth), textX, y + 36f, titleFont, 0xFFFFFFFF.toInt())

        val subFont = fonts.body(25f)
        val detail = listOfNotNull(row.subtitle, formatListening(row.ms)).joinToString(" · ")
        drawText(canvas, ellipsised(detail, subFont, textWidth), textX, y + 72f, subFont, 0x99FFFFFF.toInt())
    }

    private fun drawAlbums(
        canvas: Canvas,
        fonts: Fonts,
        albums: List<DesktopReplayRow>,
        covers: Map<String, ImageBitmap?>,
        top: Float,
    ): Float {
        if (albums.isEmpty()) return top
        drawText(
            canvas,
            DesktopStrings["top_albums", "Top albums"].uppercase(Locale.getDefault()),
            MARGIN,
            top,
            fonts.body(28f, bold = true),
            0xB3FFFFFF.toInt(),
        )
        val size = 200f
        val gap = if (POSTER_ALBUMS > 1) (POSTER_W - MARGIN * 2 - size * POSTER_ALBUMS) / (POSTER_ALBUMS - 1) else 0f
        val y = top + 40f
        val nameFont = fonts.body(25f, bold = true)
        val subFont = fonts.body(22f)
        albums.take(POSTER_ALBUMS).forEachIndexed { index, album ->
            val x = MARGIN + index * (size + gap)
            drawArtwork(canvas, album.artworkUrl?.let { covers[it] }, album.title, x, y, size, circular = false)
            drawText(canvas, ellipsised(album.title, nameFont, size), x, y + size + 38f, nameFont, 0xFFFFFFFF.toInt())
            val detail = album.subtitle ?: formatListening(album.ms)
            drawText(canvas, ellipsised(detail, subFont, size), x, y + size + 70f, subFont, 0x8CFFFFFF.toInt())
        }
        return y + size + 130f
    }

    private fun drawHabitsStrip(
        canvas: Canvas,
        fonts: Fonts,
        summary: DesktopReplaySummary,
        top: Float,
    ) {
        if (top > POSTER_H - 190f) return
        val chips = buildList {
            summary.peakHour?.let { add("Peak hour · ${formatHourChip(it)}") }
            summary.busiestDay?.let { add("Biggest day · $it") }
            if (summary.distinctAlbums > 0) add("${summary.distinctAlbums} albums")
        }
        if (chips.isEmpty()) return
        val chipPaint = Paint().apply {
            isAntiAlias = true
            color = 0x24FFFFFF
        }
        val chipFont = fonts.body(26f, bold = true)
        var x = MARGIN
        try {
            chips.forEach { label ->
                val width = chipFont.measureTextWidth(label) + 40f
                if (x + width > POSTER_W - MARGIN) return
                canvas.drawRRect(RRect.makeXYWH(x, top, width, 54f, 27f), chipPaint)
                drawText(canvas, label, x + 20f, top + 36f, chipFont, 0xE6FFFFFF.toInt())
                x += width + 14f
            }
        } finally {
            chipPaint.close()
        }
    }

    private fun drawFooter(canvas: Canvas, fonts: Fonts) {
        drawText(
            canvas,
            DesktopStrings["counted_on_device_with_bitchord", "Counted on this device with BitChord"].uppercase(Locale.getDefault()),
            MARGIN,
            POSTER_H - 64f,
            fonts.body(22f, bold = true),
            0x73FFFFFF,
        )
    }

    private fun drawArtwork(
        canvas: Canvas,
        bitmap: ImageBitmap?,
        fallback: String,
        x: Float,
        y: Float,
        size: Float,
        circular: Boolean,
    ) {
        val radius = if (circular) size / 2f else size * 0.10f
        val rrect = RRect.makeXYWH(x, y, size, size, radius)
        val paint = Paint().apply { isAntiAlias = true }
        try {
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                val skiaImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
                val srcSide = min(bitmap.width, bitmap.height).toFloat()
                val srcX = (bitmap.width - srcSide) / 2f
                val srcY = (bitmap.height - srcSide) / 2f
                val src = Rect.makeXYWH(srcX, srcY, srcSide, srcSide)
                val dst = Rect.makeXYWH(x, y, size, size)
                canvas.save()
                canvas.clipRRect(rrect, ClipMode.INTERSECT, true)
                canvas.drawImageRect(skiaImage, src, dst, SamplingMode.LINEAR, paint, false)
                canvas.restore()
            } else {
                val hue = ((fallback.hashCode() % 360) + 360) % 360
                paint.color = hslToArgb(hue.toFloat(), 0.55f, 0.45f)
                canvas.drawRRect(rrect, paint)
            }
        } finally {
            paint.close()
        }
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        font: Font,
        color: Int,
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
        }
        try {
            canvas.drawString(text, x, baselineY, font, paint)
        } finally {
            paint.close()
        }
    }

    private fun ellipsised(text: String, font: Font, width: Float): String {
        if (font.measureTextWidth(text) <= width) return text
        var end = text.length
        while (end > 1 && font.measureTextWidth(text.take(end) + "…") > width) end--
        return text.take(end).trimEnd() + "…"
    }

    private fun paletteOf(bitmap: ImageBitmap?): List<Int> {
        val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
        val source = bitmap ?: return fallback
        val swatches = runCatching {
            paletteSwatches(source, 24, clearFilters = false)
                .sortedByDescending { it.population }
                .map { it.rgb }
        }.getOrNull().orEmpty()
        if (swatches.isEmpty()) return fallback
        return (swatches + fallback).take(4).map(::tuned)
    }

    private fun tuned(argb: Int): Int {
        val hsl = rgbToHsl(argb)
        hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
        hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
        return hslToArgb(hsl[0], hsl[1], hsl[2])
    }

    private fun dimmed(argb: Int): Int {
        val hsl = rgbToHsl(argb)
        return hslToArgb(hsl[0], hsl[1], 0.10f)
    }

    private fun withAlpha(argb: Int, alpha: Int): Int =
        ((alpha.coerceIn(0, 255)) shl 24) or (argb and 0x00FFFFFF)

    private fun rgbToHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val d = maxC - minC
        val l = (maxC + minC) / 2f
        val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
        val h = when {
            d == 0f -> 0f
            maxC == r -> 60f * (((g - b) / d) % 6f)
            maxC == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, s, l)
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (rp, gp, bp) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((rp + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gp + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bp + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun formatHourChip(hour: Int): String = when (hour) {
        0 -> "12 AM"
        12 -> "12 PM"
        in 1..11 -> "$hour AM"
        else -> "${hour - 12} PM"
    }

    internal class Fonts(
        private val heavy: Typeface,
        private val semibold: Typeface,
        private val regular: Typeface,
    ) {
        fun heading(size: Float): Font = Font(heavy, size)
        fun body(size: Float, bold: Boolean = false): Font = Font(if (bold) semibold else regular, size)
    }

    private suspend fun loadFonts(): Fonts {
        val mgr = FontMgr.default
        val heavy = loadResourceFont("font/sf_pro_display_heavy.otf")
            ?: mgr.matchFamilyStyle(null, FontStyle.BOLD)
            ?: Typeface.makeEmpty()
        val semibold = loadResourceFont("font/sf_pro_display_semibold.otf")
            ?: mgr.matchFamilyStyle(null, FontStyle.BOLD)
            ?: heavy
        val regular = loadResourceFont("font/sf_pro_display_regular.otf")
            ?: mgr.matchFamilyStyle(null, FontStyle.NORMAL)
            ?: Typeface.makeEmpty()
        return Fonts(heavy, semibold, regular)
    }

    private suspend fun loadResourceFont(path: String): Typeface? = runCatching {
        val bytes = Res.readBytes(path)
        FontMgr.default.makeFromData(Data.makeFromBytes(bytes))
    }.getOrNull()

    private fun defaultSystemFonts(): Fonts {
        val mgr = FontMgr.default
        val bold = mgr.matchFamilyStyle(null, FontStyle.BOLD) ?: Typeface.makeEmpty()
        val normal = mgr.matchFamilyStyle(null, FontStyle.NORMAL) ?: Typeface.makeEmpty()
        return Fonts(bold, bold, normal)
    }

    fun suggestedFilename(summary: DesktopReplaySummary): String {
        val slug = summary.label.ifBlank { summary.period.name }
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "replay" }
        return "bitchord-replay-$slug.png"
    }

    fun chooseSaveFile(suggestedName: String): File? = runCatching {
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = DesktopStrings["share_my_replay", "Share my Replay"]
            fileFilter = FileNameExtensionFilter("PNG Image (*.png)", "png")
            selectedFile = File(currentDirectory, suggestedName)
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val picked = chooser.selectedFile ?: return null
        if (picked.name.endsWith(".png", ignoreCase = true)) picked else File(picked.parentFile, "${picked.name}.png")
    }.getOrNull()

    suspend fun savePng(file: File, pngBytes: ByteArray): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(pngBytes)
            file
        }
    }

    suspend fun copyPngToClipboard(pngBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val buffered = ImageIO.read(ByteArrayInputStream(pngBytes))
                ?: error("Could not decode PNG for clipboard")
            val transferable = object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
                override fun getTransferData(flavor: DataFlavor?): Any {
                    if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
                    return buffered
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
        }
    }
}
