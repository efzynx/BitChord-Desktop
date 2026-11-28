package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SpectrumDialogTab {
    SINGLE,
    COMPARE,
}

@Composable
internal fun DesktopAudioSpectrumDialog(
    initialSong: Song,
    availableLocalSongs: List<Song>,
    onDismiss: () -> Unit,
) {
    var tab by remember(initialSong.videoId) { mutableStateOf(SpectrumDialogTab.SINGLE) }
    var primarySong by remember(initialSong.videoId) { mutableStateOf(initialSong) }
    var secondarySong by remember(initialSong.videoId) { mutableStateOf<Song?>(null) }

    var primaryResult by remember { mutableStateOf<DesktopSpectrumResult?>(null) }
    var primaryLoading by remember { mutableStateOf(true) }
    var primaryError by remember { mutableStateOf<String?>(null) }

    var secondaryResult by remember { mutableStateOf<DesktopSpectrumResult?>(null) }
    var secondaryLoading by remember { mutableStateOf(false) }
    var secondaryError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var pickingForPrimary by remember { mutableStateOf(false) }
    var pickerExpanded by remember(initialSong.videoId) { mutableStateOf(true) }
    var guideExpanded by remember { mutableStateOf(false) }
    var discoveredLocalSongs by remember { mutableStateOf(availableLocalSongs) }
    var scanningLibrary by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(availableLocalSongs) {
        val currentMap = linkedMapOf<String, Song>()
        (availableLocalSongs + discoveredLocalSongs).forEach { song ->
            val key = song.localPath ?: song.localUri ?: song.videoId
            currentMap.putIfAbsent(key, song)
        }
        discoveredLocalSongs = currentMap.values.toList()
        if (discoveredLocalSongs.size <= 1) {
            scanningLibrary = true
            val scanned = withContext(Dispatchers.IO) {
                runCatching { DesktopLocalMusic.scan() }.getOrDefault(emptyList())
            }
            val merged = linkedMapOf<String, Song>()
            (availableLocalSongs + discoveredLocalSongs + scanned).forEach { song ->
                val key = song.localPath ?: song.localUri ?: song.videoId
                merged.putIfAbsent(key, song)
            }
            discoveredLocalSongs = merged.values.toList()
            scanningLibrary = false
        }
    }

    LaunchedEffect(tab, pickerExpanded) {
        if (tab == SpectrumDialogTab.COMPARE && pickerExpanded) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    val isDark = DesktopIsDark

    LaunchedEffect(primarySong.localPath, primarySong.localUri) {
        primaryLoading = true
        primaryError = null
        val res = DesktopAudioSpectrumAnalyzer.analyze(primarySong)
        primaryResult = res.getOrNull()
        primaryError = res.exceptionOrNull()?.message
        primaryLoading = false
    }

    LaunchedEffect(secondarySong?.localPath, secondarySong?.localUri) {
        val target = secondarySong
        if (target == null) {
            secondaryResult = null
            secondaryError = null
            secondaryLoading = false
            return@LaunchedEffect
        }
        secondaryLoading = true
        secondaryError = null
        val res = DesktopAudioSpectrumAnalyzer.analyze(target)
        secondaryResult = res.getOrNull()
        secondaryError = res.exceptionOrNull()?.message
        secondaryLoading = false
    }

    DesktopDialogPanel(
        onDismiss = onDismiss,
        maxWidth = 960,
        popup = true,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top bar: title, mode toggle (Single | Compare), close icon.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DesktopAccent.copy(alpha = 0.16f))
                        .border(0.75.dp, DesktopAccent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = DesktopAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DesktopStrings["d_spectrum_title", "Audio Spectrum Inspector"],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DesktopInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DesktopStrings[
                            "d_spectrum_subtitle",
                            "Verify real Lossless & Hi-Res depth vs upsamples or lossy transcodes",
                        ],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SpectrumModeSwitch(
                    selected = tab,
                    onSelect = { tab = it },
                )
                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .desktopGlassPress(CircleShape, interactionSource = closeInteraction, pressScale = 0.92f)
                        .background(DesktopInk.copy(alpha = if (isDark) 0.10f else 0.07f))
                        .clickable(interactionSource = closeInteraction, indication = null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = DesktopStrings["close", "Close"],
                        tint = DesktopInk,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (tab == SpectrumDialogTab.SINGLE) {
                SingleSpectrumContent(
                    song = primarySong,
                    result = primaryResult,
                    loading = primaryLoading,
                    error = primaryError,
                    onOpenCompare = {
                        pickingForPrimary = false
                        pickerExpanded = true
                        tab = SpectrumDialogTab.COMPARE
                    },
                )
            } else {
                CompareSpectrumContent(
                    primarySong = primarySong,
                    primaryResult = primaryResult,
                    primaryLoading = primaryLoading,
                    primaryError = primaryError,
                    secondarySong = secondarySong,
                    secondaryResult = secondaryResult,
                    secondaryLoading = secondaryLoading,
                    secondaryError = secondaryError,
                    availableSongs = discoveredLocalSongs,
                    scanningLibrary = scanningLibrary,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchFocusRequester = searchFocusRequester,
                    pickingForPrimary = pickingForPrimary,
                    pickerExpanded = pickerExpanded,
                    onTogglePickerFor = { forPrimary ->
                        if (pickerExpanded && pickingForPrimary == forPrimary && secondarySong != null) {
                            pickerExpanded = false
                        } else {
                            pickingForPrimary = forPrimary
                            pickerExpanded = true
                        }
                    },
                    onSelectSong = { chosen ->
                        if (pickingForPrimary) {
                            primarySong = chosen
                            pickingForPrimary = false
                        } else {
                            secondarySong = chosen
                        }
                        pickerExpanded = false
                    },
                )
            }

            SpectrumReadingGuideCard(
                expanded = guideExpanded,
                onToggle = { guideExpanded = !guideExpanded },
            )
        }
    }
}

@Composable
private fun SpectrumModeSwitch(
    selected: SpectrumDialogTab,
    onSelect: (SpectrumDialogTab) -> Unit,
) {
    val isDark = DesktopIsDark
    val pillBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(pillBg)
            .border(0.6.dp, DesktopCardEdge, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SpectrumSwitchItem(
            label = DesktopStrings["d_spectrum_tab_single", "Inspect"],
            selected = selected == SpectrumDialogTab.SINGLE,
            onClick = { onSelect(SpectrumDialogTab.SINGLE) },
        )
        SpectrumSwitchItem(
            label = DesktopStrings["d_spectrum_tab_compare", "Compare"],
            selected = selected == SpectrumDialogTab.COMPARE,
            onClick = { onSelect(SpectrumDialogTab.COMPARE) },
        )
    }
}

@Composable
private fun SpectrumSwitchItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .desktopGlassPress(CircleShape, interactionSource = interaction, pressScale = 0.96f)
            .background(if (selected) DesktopAccent else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) DesktopOnAccent else DesktopSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SingleSpectrumContent(
    song: Song,
    result: DesktopSpectrumResult?,
    loading: Boolean,
    error: String?,
    onOpenCompare: () -> Unit,
) {
    val isDark = DesktopIsDark
    val cardBg = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)

    // Track header card + verdict badge.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = song.title.ifBlank { File(song.localPath.orEmpty()).name },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DesktopInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    song.artist.takeIf(String::isNotBlank),
                    song.albumName?.takeIf(String::isNotBlank),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result != null) {
                Text(
                    text = "${result.metrics.codecName} · ${formatHz(result.metrics.sampleRateHz)} · " +
                        "${result.metrics.channels}ch · ${formatBytes(result.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DesktopSecondary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (result != null) {
            VerdictBadge(verdict = result.verdict)
        }
        DesktopGlassPillButton(
            text = DesktopStrings["d_spectrum_compare_btn", "Compare"],
            onClick = onOpenCompare,
            accent = true,
        )
    }

    when {
        loading -> {
            SpectrumLoadingBox(songTitle = song.title)
        }
        error != null -> {
            SpectrumErrorBox(message = error)
        }
        result != null -> {
            SpectrogramCard(
                result = result,
                heightDp = 290,
            )

            // 4 technical readout cards.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = DesktopStrings["d_spectrum_metric_format", "FORMAT & RATE"],
                    value = "${result.metrics.codecName} · ${formatKhz(result.metrics.sampleRateHz)}",
                    sub = if (result.metrics.bitrateKbps != null) {
                        "${result.metrics.bitrateKbps} kbps · Nyquist ${formatKhzDouble(result.metrics.nyquistHz)}"
                    } else {
                        "Nyquist ${formatKhzDouble(result.metrics.nyquistHz)}"
                    },
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = DesktopStrings["d_spectrum_metric_depth", "BIT DEPTH"],
                    value = formatBitDepthValue(result.metrics),
                    sub = formatBitDepthSub(result.metrics),
                    warning = result.metrics.zeroPaddedBits,
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = DesktopStrings["d_spectrum_metric_ceiling", "SPECTRAL CEILING"],
                    value = "~${formatHz(result.metrics.estimatedCutoffHz)}",
                    sub = result.metrics.sharpCliffHz?.let { cliff ->
                        DesktopStrings.format("d_spectrum_cliff_at", formatHz(cliff), fallback = "Sharp cliff at %1\$s")
                    } ?: DesktopStrings["d_spectrum_gradual_rolloff", "Natural roll-off"],
                    warning = result.metrics.sharpCliffHz != null && result.metrics.sharpCliffHz < 19_600,
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = DesktopStrings["d_spectrum_metric_dynamics", "DYNAMICS"],
                    value = String.format(Locale.US, "DR %.1f dB", result.metrics.crestFactorDb),
                    sub = String.format(
                        Locale.US,
                        "Peak %.1f · RMS %.1f dBFS",
                        result.metrics.peakDbfs,
                        result.metrics.rmsDbfs,
                    ),
                )
            }

            VerdictExplanationBanner(result = result)
        }
    }
}

@Composable
private fun CompareSpectrumContent(
    primarySong: Song,
    primaryResult: DesktopSpectrumResult?,
    primaryLoading: Boolean,
    primaryError: String?,
    secondarySong: Song?,
    secondaryResult: DesktopSpectrumResult?,
    secondaryLoading: Boolean,
    secondaryError: String?,
    availableSongs: List<Song>,
    scanningLibrary: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    pickingForPrimary: Boolean,
    pickerExpanded: Boolean,
    onTogglePickerFor: (forPrimary: Boolean) -> Unit,
    onSelectSong: (Song) -> Unit,
) {
    // Track A and Track B selector slots — click either card to search/pick a track for that slot.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompareSlotCard(
            modifier = Modifier.weight(1f),
            slotTag = DesktopStrings["d_spectrum_track_a", "TRACK A"],
            accent = Color(0xFF38BDF8),
            song = primarySong,
            result = primaryResult,
            activeTarget = pickerExpanded && pickingForPrimary,
            onSelectSlot = { onTogglePickerFor(true) },
        )
        CompareSlotCard(
            modifier = Modifier.weight(1f),
            slotTag = DesktopStrings["d_spectrum_track_b", "TRACK B"],
            accent = Color(0xFFF59E0B),
            song = secondarySong,
            result = secondaryResult,
            activeTarget = pickerExpanded && !pickingForPrimary,
            onSelectSlot = { onTogglePickerFor(false) },
        )
    }

    // Inline auto-search track picker (shows matching tracks first, plus filename/format details).
    val isDark = DesktopIsDark
    val pickerBg = if (isDark) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.03f)
    val referenceSong = if (pickingForPrimary) (secondarySong ?: primarySong) else primarySong
    val candidates = remember(availableSongs, searchQuery, referenceSong.localPath, referenceSong.title) {
        DesktopAudioSpectrumAnalyzer.rankCompareCandidates(
            candidates = availableSongs,
            referenceSong = referenceSong,
            query = searchQuery,
        ).take(40)
    }

    if (pickerExpanded || secondarySong == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(pickerBg)
                .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                    contentDescription = null,
                    tint = if (pickingForPrimary) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (pickingForPrimary) {
                        DesktopStrings["d_spectrum_pick_for_a", "Search & pick Track A"]
                    } else {
                        DesktopStrings["d_spectrum_pick_for_b", "Search & pick Track B to compare"]
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                    modifier = Modifier.weight(1f),
                )
                if (scanningLibrary) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp, color = DesktopAccent)
                }
                Row(
                    modifier = Modifier
                        .width(260.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                        .border(0.6.dp, DesktopCardEdge, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = DesktopSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = DesktopStrings["d_spectrum_search_local", "Filter local tracks…"],
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = DesktopInk),
                            cursorBrush = SolidColor(DesktopAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        val clearInteraction = remember { MutableInteractionSource() }
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = DesktopSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable(interactionSource = clearInteraction, indication = null) {
                                    onSearchQueryChange("")
                                },
                        )
                    }
                }
            }

            if (candidates.isEmpty()) {
                Text(
                    text = if (scanningLibrary) {
                        DesktopStrings["d_searching", "Searching…"]
                    } else {
                        DesktopStrings[
                            "d_spectrum_no_local_matches",
                            "No matching local tracks found. Try typing a song title, artist, or format (flac, mp3, wav).",
                        ]
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 172.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    candidates.forEach { entry ->
                        val candidate = entry.song
                        val isTrackA = candidate.localPath != null && candidate.localPath == primarySong.localPath
                        val isTrackB = candidate.localPath != null && candidate.localPath == secondarySong?.localPath
                        val fileName = candidate.localPath?.let { File(it).name }.orEmpty()
                        val ext = fileName.substringAfterLast('.', "").uppercase()
                        val rowInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .desktopGlassPress(RoundedCornerShape(10.dp), interactionSource = rowInteraction, pressScale = 0.99f)
                                .background(
                                    when {
                                        isTrackA -> Color(0xFF38BDF8).copy(alpha = 0.14f)
                                        isTrackB -> Color(0xFFF59E0B).copy(alpha = 0.14f)
                                        entry.isSuggestedMatch -> DesktopAccent.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable(interactionSource = rowInteraction, indication = null) {
                                    onSelectSong(candidate)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1.4f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = DesktopInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (entry.isSuggestedMatch && searchQuery.isBlank()) {
                                        Text(
                                            text = DesktopStrings["d_spectrum_suggested_match", "Match"],
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DesktopAccent,
                                        )
                                    }
                                }
                                if (fileName.isNotBlank()) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DesktopSecondary.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = candidate.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.9f),
                            )
                            val tag = when {
                                isTrackA -> "A"
                                isTrackB -> "B"
                                candidate.sourceHiRes == true -> "Hi-Res${if (ext.isNotBlank()) " · $ext" else ""}"
                                candidate.sourceQuality != null -> "${candidate.sourceQuality}${if (ext.isNotBlank() && ext !in candidate.sourceQuality.orEmpty()) " · $ext" else ""}"
                                else -> ext
                            }
                            if (tag.isNotBlank()) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isTrackA -> Color(0xFF38BDF8)
                                        isTrackB -> Color(0xFFF59E0B)
                                        candidate.sourceHiRes == true -> DesktopAccent
                                        else -> DesktopSecondary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Side-by-side spectrograms.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                primaryLoading -> SpectrumLoadingBox(primarySong.title)
                primaryError != null -> SpectrumErrorBox(primaryError)
                primaryResult != null -> SpectrogramCard(result = primaryResult, heightDp = 210, labelPrefix = "A · ")
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                secondarySong == null -> EmptyComparePlaceholder(onPickTrack = { onTogglePickerFor(false) })
                secondaryLoading -> SpectrumLoadingBox(secondarySong.title)
                secondaryError != null -> SpectrumErrorBox(secondaryError)
                secondaryResult != null -> SpectrogramCard(result = secondaryResult, heightDp = 210, labelPrefix = "B · ")
            }
        }
    }

    // Frequency response overlay graph & metric delta when both tracks are analyzed.
    if (primaryResult != null && secondaryResult != null) {
        FrequencyOverlayCard(
            resultA = primaryResult,
            resultB = secondaryResult,
        )
        CompareDeltaSummary(
            resultA = primaryResult,
            resultB = secondaryResult,
        )
    }
}

@Composable
private fun CompareSlotCard(
    slotTag: String,
    accent: Color,
    song: Song?,
    result: DesktopSpectrumResult?,
    activeTarget: Boolean,
    onSelectSlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = DesktopIsDark
    val bg = if (activeTarget) accent.copy(alpha = if (isDark) 0.10f else 0.08f)
    else if (isDark) Color.White.copy(alpha = 0.045f)
    else Color.Black.copy(alpha = 0.03f)
    val border = if (activeTarget) accent.copy(alpha = 0.55f) else DesktopCardEdge
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (activeTarget) 1.dp else 0.75.dp, border, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onSelectSlot)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
                Text(
                    text = slotTag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                if (result != null) {
                    Text(
                        text = verdictShortLabel(result.verdict),
                        style = MaterialTheme.typography.labelSmall,
                        color = verdictColor(result.verdict, isDark),
                    )
                }
            }
            Text(
                text = song?.title ?: DesktopStrings["d_spectrum_select_track_b", "Select Track B"],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (song != null) DesktopInk else DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result != null) {
                Text(
                    text = "${result.metrics.codecName} · ${formatKhz(result.metrics.sampleRateHz)} · " +
                        formatBitDepthValue(result.metrics),
                    style = MaterialTheme.typography.labelSmall,
                    color = DesktopSecondary,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(DesktopInk.copy(alpha = if (isDark) 0.10f else 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = DesktopInk,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SpectrogramCard(
    result: DesktopSpectrumResult,
    heightDp: Int,
    labelPrefix: String = "",
) {
    val isDark = DesktopIsDark
    val textMeasurer = rememberTextMeasurer()
    val metrics = result.metrics
    val nyquist = metrics.nyquistHz

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF090A10))
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${labelPrefix}${DesktopStrings["d_spectrum_spectrogram", "Spectrogram"]} " +
                    "(0 – ${formatKhzDouble(nyquist)})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (metrics.sharpCliffHz != null) {
                    "Cutoff ~${formatHz(metrics.sharpCliffHz)}"
                } else {
                    "Ceiling ~${formatHz(metrics.estimatedCutoffHz)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (metrics.sharpCliffHz != null && metrics.sharpCliffHz < 19_600) {
                    Color(0xFFFCA5A5)
                } else {
                    Color(0xFF93C5FD)
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Heatmap + calibrated frequency & cutoff overlay.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Y-axis gutter = 48.dp, bottom X-axis gutter = 20.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 46.dp, bottom = 18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
                ) {
                    Image(
                        bitmap = result.spectrogram,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftGutter = 46.dp.toPx()
                    val bottomGutter = 18.dp.toPx()
                    val plotW = (size.width - leftGutter).coerceAtLeast(1f)
                    val plotH = (size.height - bottomGutter).coerceAtLeast(1f)
                    val axisStyle = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)

                    val stepKhz = when {
                        nyquist >= 80_000 -> 20
                        nyquist >= 36_000 -> 10
                        else -> 5
                    }
                    var kHz = 0
                    while (kHz * 1000.0 <= nyquist + 100.0) {
                        val frac = (kHz * 1000.0 / nyquist).toFloat().coerceIn(0f, 1f)
                        val y = plotH * (1f - frac)
                        drawLine(
                            color = Color.White.copy(alpha = 0.10f),
                            start = Offset(leftGutter, y),
                            end = Offset(leftGutter + plotW, y),
                            strokeWidth = 1f,
                        )
                        val label = "${kHz}k"
                        val measured = textMeasurer.measure(label, axisStyle)
                        val ty = (y - measured.size.height / 2f).coerceIn(0f, plotH - measured.size.height)
                        drawText(measured, topLeft = Offset(leftGutter - measured.size.width - 6f, ty))
                        kHz += stepKhz
                    }

                    // 22.05 kHz CD Nyquist reference line on >48 kHz files.
                    if (nyquist > 23_500.0) {
                        val y22 = plotH * (1f - (22_050.0 / nyquist).toFloat())
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                            start = Offset(leftGutter, y22),
                            end = Offset(leftGutter + plotW, y22),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                        )
                    }

                    // Sharp cliff or estimated ceiling line.
                    val markerHz = (metrics.sharpCliffHz ?: metrics.estimatedCutoffHz).toDouble()
                    if (markerHz in 2_000.0..(nyquist * 0.98)) {
                        val yCut = plotH * (1f - (markerHz / nyquist).toFloat())
                        val lineColor = if (metrics.sharpCliffHz != null && metrics.sharpCliffHz < 19_600) {
                            Color(0xFFFB7185)
                        } else {
                            Color(0xFFFBBF24)
                        }
                        drawLine(
                            color = lineColor.copy(alpha = 0.90f),
                            start = Offset(leftGutter, yCut),
                            end = Offset(leftGutter + plotW, yCut),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f),
                        )
                    }

                    // Time ticks on X axis.
                    val dur = metrics.durationSeconds.coerceAtLeast(1.0)
                    val timePoints = listOf(0.0, dur * 0.5, dur)
                    timePoints.forEachIndexed { idx, tSec ->
                        val frac = idx * 0.5f
                        val label = formatDurationClock(tSec)
                        val measured = textMeasurer.measure(label, axisStyle)
                        val x = when (idx) {
                            0 -> leftGutter
                            2 -> (leftGutter + plotW - measured.size.width).coerceAtLeast(leftGutter)
                            else -> leftGutter + plotW * frac - measured.size.width / 2f
                        }
                        drawText(measured, topLeft = Offset(x, plotH + 3.dp.toPx()))
                    }
                }
            }

            // Perceptual dBFS color legend bar.
            Column(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0dB", fontSize = 9.sp, color = Color.White.copy(alpha = 0.65f))
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFF8C6),
                                    Color(0xFFF8A822),
                                    Color(0xFFD4483E),
                                    Color(0xFF7E1C72),
                                    Color(0xFF241052),
                                    Color(0xFF07080E),
                                ),
                            ),
                        ),
                )
                Text("-110", fontSize = 9.sp, color = Color.White.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun FrequencyOverlayCard(
    resultA: DesktopSpectrumResult,
    resultB: DesktopSpectrumResult,
) {
    val textMeasurer = rememberTextMeasurer()
    val maxNyquist = maxOf(resultA.metrics.nyquistHz, resultB.metrics.nyquistHz).coerceAtLeast(22_050.0)
    val colorA = Color(0xFF38BDF8)
    val colorB = Color(0xFFF59E0B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF090A10))
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DesktopStrings["d_spectrum_overlay_title", "95th-Percentile Frequency Response Overlay"],
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.90f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendPill(color = colorA, label = "A: ${resultA.metrics.codecName} ${formatKhz(resultA.metrics.sampleRateHz)}")
                LegendPill(color = colorB, label = "B: ${resultB.metrics.codecName} ${formatKhz(resultB.metrics.sampleRateHz)}")
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp),
        ) {
            val leftGutter = 42.dp.toPx()
            val bottomGutter = 20.dp.toPx()
            val plotW = (size.width - leftGutter).coerceAtLeast(1f)
            val plotH = (size.height - bottomGutter).coerceAtLeast(1f)
            val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)

            // Horizontal dB gridlines: 0 down to -110 dB.
            for (db in listOf(0, -20, -40, -60, -80, -100)) {
                val frac = ((db + 112f) / 112f).coerceIn(0f, 1f)
                val y = plotH * (1f - frac)
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(leftGutter, y),
                    end = Offset(leftGutter + plotW, y),
                    strokeWidth = 1f,
                )
                val m = textMeasurer.measure("$db", labelStyle)
                drawText(m, topLeft = Offset(leftGutter - m.size.width - 5f, (y - m.size.height / 2f).coerceAtLeast(0f)))
            }

            // Vertical kHz gridlines + CD 22.05 kHz marker.
            val stepKhz = if (maxNyquist >= 40_000) 10 else 5
            var kHz = 0
            while (kHz * 1000.0 <= maxNyquist + 100.0) {
                val x = leftGutter + plotW * (kHz * 1000.0 / maxNyquist).toFloat().coerceIn(0f, 1f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, plotH),
                    strokeWidth = 1f,
                )
                val m = textMeasurer.measure("${kHz}k", labelStyle)
                val tx = (x - m.size.width / 2f).coerceIn(leftGutter, leftGutter + plotW - m.size.width)
                drawText(m, topLeft = Offset(tx, plotH + 3.dp.toPx()))
                kHz += stepKhz
            }

            if (maxNyquist > 23_500.0) {
                val xCd = leftGutter + plotW * (22_050.0 / maxNyquist).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(xCd, 0f),
                    end = Offset(xCd, plotH),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f),
                )
            }

            fun drawCurve(result: DesktopSpectrumResult, color: Color, usePeak: Boolean, strokeDp: Float) {
                val freqs = result.spectrumFreqHz
                val values = if (usePeak) result.peakSpectrumDb else result.meanSpectrumDb
                if (freqs.isEmpty() || values.isEmpty()) return
                val path = Path()
                for (i in freqs.indices) {
                    val x = leftGutter + plotW * (freqs[i].toDouble() / maxNyquist).toFloat().coerceIn(0f, 1f)
                    val normY = ((values[i] + 112f) / 112f).coerceIn(0f, 1f)
                    val y = plotH * (1f - normY)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeDp, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            drawCurve(resultA, colorA.copy(alpha = 0.32f), usePeak = false, strokeDp = 1.2.dp.toPx())
            drawCurve(resultB, colorB.copy(alpha = 0.32f), usePeak = false, strokeDp = 1.2.dp.toPx())
            drawCurve(resultA, colorA, usePeak = true, strokeDp = 2.0.dp.toPx())
            drawCurve(resultB, colorB, usePeak = true, strokeDp = 2.0.dp.toPx())
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp, 3.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun CompareDeltaSummary(
    resultA: DesktopSpectrumResult,
    resultB: DesktopSpectrumResult,
) {
    val isDark = DesktopIsDark
    val bg = if (isDark) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.03f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompareMetricRow(
            label = DesktopStrings["d_spectrum_metric_format", "FORMAT & RATE"],
            valA = "${resultA.metrics.codecName} · ${formatKhz(resultA.metrics.sampleRateHz)}",
            valB = "${resultB.metrics.codecName} · ${formatKhz(resultB.metrics.sampleRateHz)}",
        )
        CompareMetricRow(
            label = DesktopStrings["d_spectrum_metric_depth", "BIT DEPTH"],
            valA = formatBitDepthValue(resultA.metrics),
            valB = formatBitDepthValue(resultB.metrics),
        )
        CompareMetricRow(
            label = DesktopStrings["d_spectrum_metric_ceiling", "SPECTRAL CEILING"],
            valA = "~${formatHz(resultA.metrics.estimatedCutoffHz)}",
            valB = "~${formatHz(resultB.metrics.estimatedCutoffHz)}",
        )
        CompareMetricRow(
            label = DesktopStrings["d_spectrum_metric_dynamics", "DYNAMICS"],
            valA = String.format(Locale.US, "DR %.1f dB · %s", resultA.metrics.crestFactorDb, formatBytes(resultA.fileSizeBytes)),
            valB = String.format(Locale.US, "DR %.1f dB · %s", resultB.metrics.crestFactorDb, formatBytes(resultB.fileSizeBytes)),
        )
    }
}

@Composable
private fun CompareMetricRow(label: String, valA: String, valB: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DesktopSecondary, modifier = Modifier.weight(1f))
        Text(valA, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Color(0xFF38BDF8), modifier = Modifier.weight(1.2f))
        Text(valB, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Color(0xFFF59E0B), modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    val isDark = DesktopIsDark
    val bg = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(0.75.dp, if (warning) DesktopDestructive.copy(alpha = 0.45f) else DesktopCardEdge, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (warning) DesktopDestructive else DesktopInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = DesktopSecondary.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VerdictBadge(verdict: DesktopSpectrumVerdict) {
    val isDark = DesktopIsDark
    val tint = verdictColor(verdict, isDark)
    val icon = when (verdict) {
        DesktopSpectrumVerdict.NATIVE_HI_RES,
        DesktopSpectrumVerdict.NATIVE_LOSSLESS -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.WarningAmber
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .border(0.75.dp, tint.copy(alpha = 0.40f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            text = verdictShortLabel(verdict),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun VerdictExplanationBanner(result: DesktopSpectrumResult) {
    val isDark = DesktopIsDark
    val tint = verdictColor(result.verdict, isDark)
    val body = when (result.verdict) {
        DesktopSpectrumVerdict.NATIVE_HI_RES -> DesktopStrings[
            "d_spectrum_explain_hires",
            "Genuine ultrasonic energy above 24 kHz and full integer bit depth are present in the file.",
        ]
        DesktopSpectrumVerdict.NATIVE_LOSSLESS -> if (result.hasAcousticCaveat) {
            DesktopStrings[
                "d_spectrum_explain_lossless_caveat",
                "Lossless container verified cleanly through 20 kHz. Minimal energy above 24 kHz can occur naturally in acoustic, jazz, or vintage masters without necessarily being an upsample.",
            ]
        } else {
            DesktopStrings[
                "d_spectrum_explain_lossless",
                "Full CD-band frequency response (~20–22 kHz) without an artificial brickwall transcode shelf.",
            ]
        }
        DesktopSpectrumVerdict.POSSIBLE_UPSAMPLE -> if (result.metrics.zeroPaddedBits) {
            DesktopStrings.format(
                "d_spectrum_explain_zeropad",
                result.metrics.containerBitDepth ?: 24,
                fallback = "Container declares %1\$d-bit PCM, but sample LSBs are strictly zero-padded from 16-bit audio.",
            )
        } else {
            DesktopStrings[
                "d_spectrum_explain_upsample",
                "High sample rate container, but ultrasonic bandwidth above ~22 kHz drops steeply or sits near the noise floor—indicative of a resampled 44.1/48 kHz master (note: acoustic recordings can also roll off naturally).",
            ]
        }
        DesktopSpectrumVerdict.POSSIBLE_LOSSY_TRANSCODE -> DesktopStrings.format(
            "d_spectrum_explain_transcode",
            formatHz(result.metrics.sharpCliffHz ?: result.metrics.estimatedCutoffHz),
            fallback = "Lossless codec container, but spectrum ends sharply around %1\$s—typical of an audio file transcoded from a lossy MP3/AAC source.",
        )
        DesktopSpectrumVerdict.LOSSY_SOURCE -> DesktopStrings.format(
            "d_spectrum_explain_lossy",
            result.metrics.codecName,
            formatHz(result.metrics.estimatedCutoffHz),
            fallback = "Encoded with lossy codec (%1\$s), which discards high-frequency perceptual bands around %2\$s to reduce size.",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(0.75.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp).padding(top = 1.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = DesktopInk.copy(alpha = 0.90f),
        )
    }
}

@Composable
private fun SpectrumReadingGuideCard(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val isDark = DesktopIsDark
    val cardBg = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = DesktopAccent,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DesktopStrings["d_spectrum_guide_title", "How to read the spectrogram"],
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                )
                if (!expanded) {
                    Text(
                        text = DesktopStrings[
                            "d_spectrum_guide_hint",
                            "Tap to see how to tell real Lossless & Hi-Res apart from converted MP3s or upsamples",
                        ],
                        style = MaterialTheme.typography.labelSmall,
                        color = DesktopSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = DesktopSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideBulletRow(
                    dotColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                    title = DesktopStrings["d_spectrum_guide_lossless_title", "Real CD Lossless (fades smoothly around 20–22 kHz)"],
                    body = DesktopStrings[
                        "d_spectrum_guide_lossless_body",
                        "Cymbals and bright details gently fade near the top edge (20–22 kHz) without a straight horizontal black border below 20 kHz.",
                    ],
                )
                GuideBulletRow(
                    dotColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                    title = DesktopStrings["d_spectrum_guide_hires_title", "Real Hi-Res vs Upsample (blue 22.05 kHz dashed line)"],
                    body = DesktopStrings[
                        "d_spectrum_guide_hires_body",
                        "On 88.2–192 kHz files, genuine Hi-Res keeps active vertical detail above the blue 22.05 kHz line and reads \"24 active PCM bits\" (not \"Zero-padded\").",
                    ],
                )
                GuideBulletRow(
                    dotColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                    title = DesktopStrings["d_spectrum_guide_transcode_title", "Converted / Transcoded Lossless (flat razor wall at 16–19 kHz)"],
                    body = DesktopStrings[
                        "d_spectrum_guide_transcode_body",
                        "If colors stop along a flat straight line around 16–19 kHz with solid black above it, the track likely started as a lossy MP3 or AAC file.",
                    ],
                )
                GuideBulletRow(
                    dotColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                    title = DesktopStrings["d_spectrum_guide_caveat_title", "Compare curve & Acoustic recordings"],
                    body = DesktopStrings[
                        "d_spectrum_guide_caveat_body",
                        "In Compare, a higher curve between 16–22+ kHz retains more high-frequency detail. Acoustic, jazz, or vintage analog tracks naturally roll off softly even when genuine.",
                    ],
                )
            }
        }
    }
}

@Composable
private fun GuideBulletRow(
    dotColor: Color,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DesktopInk,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
            )
        }
    }
}

@Composable
private fun SpectrumLoadingBox(songTitle: String) {
    val isDark = DesktopIsDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DesktopAccent)
        Spacer(Modifier.width(12.dp))
        Text(
            text = DesktopStrings.format(
                "d_spectrum_analyzing",
                songTitle,
                fallback = "Analyzing FFT spectrum for \"%1\$s\"…",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpectrumErrorBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DesktopDestructive.copy(alpha = 0.10f))
            .border(0.75.dp, DesktopDestructive.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = DesktopDestructive,
        )
    }
}

@Composable
private fun EmptyComparePlaceholder(onPickTrack: () -> Unit) {
    val isDark = DesktopIsDark
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(242.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onPickTrack)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = DesktopStrings["d_spectrum_empty_compare", "Pick a second song from the search list above to compare spectrograms & curves"],
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
        )
    }
}

@Composable
private fun verdictShortLabel(verdict: DesktopSpectrumVerdict): String = when (verdict) {
    DesktopSpectrumVerdict.NATIVE_HI_RES -> DesktopStrings["d_spectrum_verdict_hires", "Native Hi-Res"]
    DesktopSpectrumVerdict.NATIVE_LOSSLESS -> DesktopStrings["d_spectrum_verdict_lossless", "Lossless"]
    DesktopSpectrumVerdict.POSSIBLE_UPSAMPLE -> DesktopStrings["d_spectrum_verdict_upsample", "Possible Upsample"]
    DesktopSpectrumVerdict.POSSIBLE_LOSSY_TRANSCODE -> DesktopStrings["d_spectrum_verdict_transcode", "Possible Transcode"]
    DesktopSpectrumVerdict.LOSSY_SOURCE -> DesktopStrings["d_spectrum_verdict_lossy", "Lossy Codec"]
}

private fun verdictColor(verdict: DesktopSpectrumVerdict, isDark: Boolean): Color = when (verdict) {
    DesktopSpectrumVerdict.NATIVE_HI_RES -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    DesktopSpectrumVerdict.NATIVE_LOSSLESS -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    DesktopSpectrumVerdict.POSSIBLE_UPSAMPLE -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    DesktopSpectrumVerdict.POSSIBLE_LOSSY_TRANSCODE -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
    DesktopSpectrumVerdict.LOSSY_SOURCE -> if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
}

private fun formatHz(hz: Int): String = if (hz >= 10_000) {
    String.format(Locale.US, "%.1f kHz", hz / 1000.0)
} else {
    "$hz Hz"
}

private fun formatKhz(hz: Int): String {
    val khz = hz / 1000.0
    return if (khz == khz.roundToInt().toDouble()) "${khz.roundToInt()} kHz"
    else String.format(Locale.US, "%.1f kHz", khz)
}

private fun formatKhzDouble(hz: Double): String {
    val khz = hz / 1000.0
    return String.format(Locale.US, "%.1f kHz", khz)
}

private fun formatDurationClock(sec: Double): String {
    val s = sec.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

@Composable
private fun formatBitDepthValue(m: DesktopSpectrumMetrics): String = when {
    !m.isLosslessCodec -> DesktopStrings["d_spectrum_lossy_float", "Lossy (32f decode)"]
    m.effectiveBitDepth != null && m.containerBitDepth != null && m.effectiveBitDepth < m.containerBitDepth ->
        "${m.effectiveBitDepth}-bit (${m.containerBitDepth}-bit box)"
    m.containerBitDepth != null -> "${m.containerBitDepth}-bit"
    else -> "PCM"
}

@Composable
private fun formatBitDepthSub(m: DesktopSpectrumMetrics): String = when {
    m.zeroPaddedBits -> DesktopStrings["d_spectrum_zeropad_tag", "Zero-padded LSBs"]
    m.effectiveBitDepth != null && m.isLosslessCodec ->
        DesktopStrings.format(
            "d_spectrum_active_bits",
            m.effectiveBitDepth,
            fallback = "%1\$d active PCM bits",
        )
    m.bitrateKbps != null -> "${m.bitrateKbps} kbps"
    else -> m.containerLabel
}
