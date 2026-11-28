package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.sharedui.resources.Res as SharedRes
import com.music.bitchord.sharedui.resources.ic_dolby_atmos
import com.music.bitchord.ui.components.PillSegmentedControl
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private val DesktopGroupShape = RoundedCornerShape(14.dp)
private val DesktopRowPaddingH = 16.dp
private val DesktopIconSize = 22.dp
private val DesktopIconGap = 14.dp
private val DesktopDividerInset = DesktopRowPaddingH + DesktopIconSize + DesktopIconGap

private val LocalDesktopSubRowStandalone = staticCompositionLocalOf { false }

internal data class DesktopSettingsUiState(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val listState: LazyListState,
    val accountAvatar: String?,
    val accountSubtitle: String,
    val autoplay: Boolean,
    val onAutoplayChange: (Boolean) -> Unit,
    val automix: Boolean,
    val onAutomixChange: (Boolean) -> Unit,
    val automixOnSkip: Boolean,
    val onAutomixOnSkipChange: (Boolean) -> Unit,
    val automixPerformance: AutomixPerformanceMode,
    val onAutomixPerformanceChange: (AutomixPerformanceMode) -> Unit,
    val automixPhraseBars: Int,
    val onAutomixPhraseBarsChange: (Int) -> Unit,
    val autoMixArtTransition: Boolean,
    val onAutoMixArtTransitionChange: (Boolean) -> Unit,
    val autoMixMiniArtTransition: Boolean,
    val onAutoMixMiniArtTransitionChange: (Boolean) -> Unit,
    val showBestPart: Boolean,
    val onShowBestPartChange: (Boolean) -> Unit,
    val showTotalDuration: Boolean,
    val onShowTotalDurationChange: (Boolean) -> Unit,
    val shuffle: Boolean,
    val onShuffleChange: (Boolean) -> Unit,
    val repeatMode: DesktopRepeatMode,
    val onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    val playbackSpeed: Float,
    val onPlaybackSpeedChange: (Float) -> Unit,
    val crossfadeSeconds: Int,
    val onCrossfadeSecondsChange: (Int) -> Unit,
    val animatedCanvas: Boolean,
    val onAnimatedCanvasChange: (Boolean) -> Unit,
    val animatedArtistBanner: Boolean,
    val onAnimatedArtistBannerChange: (Boolean) -> Unit,
    val spotifyCanvasReady: Boolean,
    val dontRepeatSuggestions: Boolean,
    val onDontRepeatSuggestionsChange: (Boolean) -> Unit,
    val filterNonMusicAudio: Boolean,
    val onFilterNonMusicAudioChange: (Boolean) -> Unit,
    val onChooseLocalMusicFolder: (onChosen: () -> Unit) -> Unit,
    val onLocalMusicFolderChanged: () -> Unit,
    val syncedLyrics: Boolean,
    val onSyncedLyricsChange: (Boolean) -> Unit,
    val lyricsBlur: Boolean,
    val onLyricsBlurChange: (Boolean) -> Unit,
    val enabledLyricsSources: List<String>,
    val showNerdStats: Boolean,
    val onShowNerdStatsChange: (Boolean) -> Unit,
    val fullBleedArtwork: Boolean,
    val onFullBleedArtworkChange: (Boolean) -> Unit,
    val legacyMeshGradient: Boolean,
    val onLegacyMeshGradientChange: (Boolean) -> Unit,
    val trayIconEnabled: Boolean,
    val onTrayIconChange: (Boolean) -> Unit,
    val closeToTray: Boolean,
    val onCloseToTrayChange: (Boolean) -> Unit,
    val spatialAudio: Boolean,
    val onSpatialAudioChange: (Boolean) -> Unit,
    val dolbyAtmos: Boolean,
    val onDolbyAtmosChange: (Boolean) -> Unit,
    val skipSilence: Boolean,
    val onSkipSilenceChange: (Boolean) -> Unit,
    val smartVersionAlignment: Boolean,
    val onSmartVersionAlignmentChange: (Boolean) -> Unit,
    val outputPrecision: String,
    val onOutputPrecisionChange: (String) -> Unit,
    val outputSummary: String,
    val downloadQuality: String,
    val onDownloadQualityChange: (String) -> Unit,
    val autoDownloadLiked: Boolean,
    val onAutoDownloadLikedChange: (Boolean) -> Unit,
    val audioQuality: DesktopAudioQuality,
    val onAudioQualityChange: (DesktopAudioQuality) -> Unit,
    val sleepTimerMinutes: Int?,
    val sleepAfterTrack: Boolean,
    val sleepRemainingMs: Long?,
    val sourceConfigs: List<DesktopSourceConfig>,
    val sourceStatus: Map<String, String>,
    val onSourceEnabledChange: (DesktopSourceConfig, Boolean) -> Unit,
    val onSaveSource: (DesktopSourceConfig) -> Unit,
    val onRemoveSource: (DesktopSourceConfig) -> Unit,
    val onMoveSource: (DesktopSourceConfig, Int) -> Unit,
    val onTestSource: (DesktopSourceConfig) -> Unit,
    val onNavigate: (DesktopSettingsPage) -> Unit,
    val onBack: () -> Unit,
)

private enum class SettingsHubCategory(
    val page: DesktopSettingsPage,
    val icon: ImageVector,
    val key: String,
    val fallbackTitle: String,
) {
    DOWNLOADS(DesktopSettingsPage.DOWNLOADS, BitChordIcons.Download, "download_channel_name", "Downloads"),
    PLAYBACK(DesktopSettingsPage.PLAYBACK, Icons.Rounded.PlayCircle, "playback", "Playback"),
    APPEARANCE(DesktopSettingsPage.APPEARANCE, Icons.Rounded.Computer, "appearance", "Appearance"),
    PERFORMANCE(DesktopSettingsPage.PERFORMANCE, Icons.Rounded.Bolt, "performance", "Performance"),
    LYRICS(DesktopSettingsPage.LYRICS, BitChordIcons.LyricsQuote, "open_lyrics", "Lyrics"),
    LOCAL_MUSIC(DesktopSettingsPage.LOCAL_MUSIC, BitChordIcons.Library, "local_music", "Local Music"),
    STORAGE(DesktopSettingsPage.STORAGE, Icons.Rounded.Storage, "storage", "Storage"),
    BACKUP_AND_RESTORE(DesktopSettingsPage.BACKUP_AND_RESTORE, Icons.Rounded.Backup, "d_backup_restore", "Backup and Restore"),
    MISCELLANEOUS(DesktopSettingsPage.MISCELLANEOUS, Icons.Rounded.Tune, "miscellaneous", "Miscellaneous"),
    LANGUAGE(DesktopSettingsPage.LANGUAGE, Icons.Rounded.Language, "language", "Language"),
}

private class SettingsSearchFilter(
    query: String,
    val inPage: Boolean = false,
    val pageTitle: String? = null,
) {
    private val needle = query.trim()
    var anyMatch = false
        private set

    fun matches(vararg keywords: String?): Boolean {
        val hit = needle.isEmpty() || keywords.any { it?.contains(needle, ignoreCase = true) == true }
        if (hit) anyMatch = true
        return hit
    }
}

private class SettingsCardScope(
    private val search: SettingsSearchFilter,
    private val header: String?,
) {
    class Entry(val divided: Boolean, val content: @Composable () -> Unit)

    val entries = mutableListOf<Entry>()

    fun row(
        vararg keywords: String?,
        divided: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        if (search.matches(header, *keywords)) entries += Entry(divided, content)
    }
}

@Composable
private inline fun DesktopSearchableGroup(
    search: SettingsSearchFilter,
    header: String? = null,
    footer: String? = null,
    topSpacing: Dp = 20.dp,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    val scope = SettingsCardScope(search, header)
    scope.content()
    if (scope.entries.isEmpty()) return
    // On a category page, only suppress the header when it merely repeats the page title above.
    // Distinct secondary groups (e.g. Audio & transitions, Playback controls, Player & artwork) keep
    // their uppercase heading and spacing so tables never look stacked together without labels.
    val suppressHeader = header != null &&
        search.inPage &&
        search.pageTitle != null &&
        header.equals(search.pageTitle, ignoreCase = true)
    DesktopGroupedCard(
        header = header.takeUnless { suppressHeader },
        footer = footer,
        topSpacing = if (suppressHeader) 4.dp else topSpacing,
    ) {
        scope.entries.forEachIndexed { index, entry ->
            when {
                index > 0 && entry.divided -> DesktopGroupedDivider()
                index == 0 && !entry.divided -> Spacer(Modifier.height(10.dp))
            }
            CompositionLocalProvider(LocalDesktopSubRowStandalone provides entry.divided) {
                entry.content()
            }
        }
    }
}

/** Handles `MAIN`, category pages, and choice sub-pages using mobile 1.10.0 grouped layout. */
@Composable
internal fun DesktopSettingsRouter(
    page: DesktopSettingsPage,
    state: DesktopSettingsUiState,
) {
    val bitPerfectMode by DesktopBitPerfect.mode.collectAsState()
    val bitPerfectStatus by DesktopBitPerfect.status.collectAsState()
    val bitPerfectReady by DesktopBitPerfect.ready.collectAsState()
    val convertForLimitedDac by DesktopBitPerfect.convertForLimitedDac.collectAsState()
    val party by DesktopListenTogether.state.collectAsState()
    val changes by DesktopAudioDevices.changes.collectAsState()
    val bitPerfectSupported = remember(changes) { DesktopBitPerfect.isSupportedOnHost() }

    var editingSource by remember { mutableStateOf<DesktopSourceConfig?>(null) }
    var pendingConfirmBitPerfect by remember { mutableStateOf<DesktopBitPerfectMode?>(null) }
    var pendingConfirmPerformance by remember { mutableStateOf(false) }

    val sourceProbeKey = state.sourceConfigs
        .filter { it.kind.needsServer && it.isComplete }
        .joinToString { "${it.id}@${it.baseUrl}" }
    LaunchedEffect(sourceProbeKey) {
        state.sourceConfigs
            .filter { it.kind.needsServer && it.isComplete }
            .forEach(state.onTestSource)
    }

    val isMain = page == DesktopSettingsPage.MAIN
    val pageTitle = settingsPageTitle(page)
    val search = SettingsSearchFilter(
        query = if (isMain) state.query else "",
        inPage = !isMain,
        pageTitle = if (isMain) null else pageTitle,
    )
    val searching = isMain && state.query.isNotBlank()
    fun showCategory(target: DesktopSettingsPage) = page == target || searching

    val mainListState = state.listState
    val subListState = rememberLazyListState()
    LaunchedEffect(page) {
        if (!isMain) subListState.scrollToItem(0)
    }

    DesktopSettingsPageFrame {
        Column(Modifier.fillMaxSize()) {
            DesktopSettingsTopHeader(
                title = settingsPageTitle(page),
                isMain = isMain,
                query = state.query,
                onQueryChange = state.onQueryChange,
                onBack = state.onBack,
            )

            LazyColumn(
                state = if (isMain) mainListState else subListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                if (isMain) {
                    item(key = "main_account") {
                        DesktopSearchableGroup(search, topSpacing = 4.dp) {
                            val accountTitle = DesktopStrings["account_integrations", "Account & integrations"]
                            row(accountTitle, "scrobbling", "last.fm", "listenbrainz", "spotify", "discord") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Person,
                                    iconContent = state.accountAvatar?.let { avatarUrl ->
                                        {
                                            DesktopArtwork(
                                                url = avatarUrl,
                                                modifier = Modifier.size(DesktopIconSize).clip(CircleShape),
                                                px = 64,
                                            )
                                        }
                                    },
                                    title = accountTitle,
                                    subtitle = state.accountSubtitle,
                                    onClick = { state.onNavigate(DesktopSettingsPage.INTEGRATIONS) },
                                )
                            }
                            val listenTitle = DesktopStrings["listen_together", "Listen together"]
                            row(listenTitle, "jam", "party", "sync", "tunnel", "cloudflare") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Groups,
                                    title = listenTitle,
                                    subtitle = party.code?.let {
                                        DesktopStrings.format("listen_together_in_party", it, fallback = "In party · %1\$s")
                                    } ?: DesktopStrings["listen_together_create_subtitle", "Start a party and share the code."],
                                    badge = party.members.takeIf { party.inParty && it.size > 1 }?.size?.toString(),
                                    onClick = { state.onNavigate(DesktopSettingsPage.LISTEN_TOGETHER) },
                                )
                            }
                        }
                    }

                    item(key = "main_quality") {
                        val activeSourcesSummary = remember(state.sourceConfigs, state.audioQuality) {
                            val active = state.sourceConfigs.inSourceOrder()
                                .filter { it.enabled && state.audioQuality.permits(it.kind) }
                                .map { it.displayName }
                            if (active.isEmpty()) DesktopStrings["source_none", "None"]
                            else active.joinToString(" · ")
                        }
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["audio_quality", "Audio quality"],
                        ) {
                            val sourceTitle = DesktopStrings["source", "Source"]
                            row(sourceTitle, "sources", "addon", "module", "jiosaavn", "lossless") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Extension,
                                    title = sourceTitle,
                                    subtitle = activeSourcesSummary,
                                    onClick = { state.onNavigate(DesktopSettingsPage.SOURCES) },
                                )
                            }
                            val qualityTitle = DesktopStrings["audio_quality", "Audio quality"]
                            row(qualityTitle, "bitrate", "lossless", "high", "medium", "low") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.HighQuality,
                                    title = qualityTitle,
                                    subtitle = state.audioQuality.detail,
                                    value = state.audioQuality.label,
                                    onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_AUDIO_QUALITY) },
                                )
                            }
                            val atmosTitle = DesktopStrings["dolby_atmos", "Dolby Atmos"]
                            row(atmosTitle, "atmos", "surround", "eac3") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        iconPainter = painterResource(SharedRes.drawable.ic_dolby_atmos),
                                        title = atmosTitle,
                                        subtitle = if (DesktopCodecs.supportsDolbyAtmos) {
                                            DesktopStrings["d_dolby_atmos_subtitle", "Prefer immersive mix and decode E-AC-3 with FFmpeg"]
                                        } else {
                                            DesktopStrings["dolby_atmos_unavailable", "This device can't play Atmos, so these songs play in their usual version"]
                                        },
                                        enabled = DesktopCodecs.supportsDolbyAtmos,
                                        trailing = {
                                            Switch(
                                                checked = state.dolbyAtmos && DesktopCodecs.supportsDolbyAtmos,
                                                onCheckedChange = state.onDolbyAtmosChange,
                                                enabled = DesktopCodecs.supportsDolbyAtmos,
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = {
                                            if (DesktopCodecs.supportsDolbyAtmos) state.onDolbyAtmosChange(!state.dolbyAtmos)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (!searching) {
                        item(key = "main_categories") {
                            DesktopGroupedCard {
                                SettingsHubCategory.entries.forEachIndexed { index, cat ->
                                    if (index > 0) DesktopGroupedDivider()
                                    DesktopSettingsRow(
                                        icon = cat.icon,
                                        title = DesktopStrings[cat.key, cat.fallbackTitle],
                                        value = when (cat) {
                                            SettingsHubCategory.PLAYBACK ->
                                                if (bitPerfectReady && bitPerfectStatus.active) "Bit-Perfect" else null
                                            SettingsHubCategory.PERFORMANCE ->
                                                if (DesktopAppearanceSettings.highPerformanceMode.collectAsState().value) {
                                                    "${DesktopAppearanceSettings.performanceRefreshRate.collectAsState().value} Hz"
                                                } else null
                                            SettingsHubCategory.BACKUP_AND_RESTORE -> {
                                                val drive by DesktopDriveBackup.settings.collectAsState()
                                                if (drive.connected) drive.accountEmail else null
                                            }
                                            SettingsHubCategory.LANGUAGE -> DesktopStrings.languageLabel()
                                            else -> null
                                        },
                                        onClick = { state.onNavigate(cat.page) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Category: Sources -------------------------------------------------
                if (showCategory(DesktopSettingsPage.SOURCES)) {
                    item(key = "cat_sources") {
                        val header = DesktopStrings["sources_order_header", "Sources · tried in this order"]
                        val userAddedIds = state.sourceConfigs.inSourceOrder()
                            .filter(DesktopSourceConfig::isUserAdded)
                            .map(DesktopSourceConfig::id)
                        DesktopSearchableGroup(
                            search = search,
                            header = header,
                            footer = DesktopStrings["d_sources_footer", "Lossless add-ons are tried in order above JioSaavn and YouTube Music when quality allows."],
                        ) {
                            state.sourceConfigs.inSourceOrder().forEach { config ->
                                row(config.displayName, config.kind.detail, "source", "addon", "module") {
                                    Box(Modifier.padding(horizontal = DesktopRowPaddingH)) {
                                        DesktopSourceSettingsRow(
                                            position = state.sourceConfigs.inSourceOrder().indexOf(config) + 1,
                                            config = config,
                                            status = state.sourceStatus[config.id],
                                            skippedByQuality = config.enabled && !state.audioQuality.permits(config.kind),
                                            onEdit = if (config.kind.needsServer) {
                                                { editingSource = config }
                                            } else null,
                                            onToggle = if (config.kind == DesktopSourceKind.YOUTUBE) {
                                                null
                                            } else {
                                                { state.onSourceEnabledChange(config, it) }
                                            },
                                            onMoveUp = if (userAddedIds.indexOf(config.id) > 0) {
                                                { state.onMoveSource(config, -1) }
                                            } else null,
                                            onMoveDown = if (userAddedIds.indexOf(config.id) in 0 until userAddedIds.lastIndex) {
                                                { state.onMoveSource(config, 1) }
                                            } else null,
                                        )
                                    }
                                }
                            }
                            row(DesktopStrings["d_add_source", "Add a source"], "addon", "module") {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.Plus,
                                    title = DesktopStrings["d_add_source", "Add a source"],
                                    subtitle = DesktopSourceKind.ADDON.detail,
                                    onClick = {
                                        editingSource = DesktopSourceConfig(
                                            id = UUID.randomUUID().toString(),
                                            kind = DesktopSourceKind.ADDON,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // ---- Category: Downloads -----------------------------------------------
                if (showCategory(DesktopSettingsPage.DOWNLOADS)) {
                    item(key = "cat_downloads") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["download_channel_name", "Downloads"],
                        ) {
                            val dlTitle = DesktopStrings["download_quality", "Download quality"]
                            row(dlTitle, "offline", "lossless", "download") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Download,
                                    title = dlTitle,
                                    value = downloadQualityLabel(state.downloadQuality),
                                    onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_DOWNLOAD_QUALITY) },
                                )
                            }
                            val autoDlTitle = DesktopStrings["d_auto_download_liked_title", "Auto-download liked songs"]
                            row(autoDlTitle, "liked", "offline") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Favorite,
                                    title = autoDlTitle,
                                    subtitle = DesktopStrings["d_auto_download_liked_desc", "Automatically download songs when added to your liked songs"],
                                    trailing = {
                                        Switch(
                                            checked = state.autoDownloadLiked,
                                            onCheckedChange = state.onAutoDownloadLikedChange,
                                            colors = desktopSwitchColors(),
                                        )
                                    },
                                    onClick = { state.onAutoDownloadLikedChange(!state.autoDownloadLiked) },
                                )
                            }
                        }
                    }
                }

                // ---- Category: Playback ------------------------------------------------
                if (showCategory(DesktopSettingsPage.PLAYBACK)) {
                    item(key = "cat_playback_output") {
                        val footer = if (bitPerfectReady) {
                            DesktopStrings[
                                "bit_perfect_bypassed_footer",
                                "Bit-perfect output is ready, so the faded settings are bypassed and Dolby Atmos versions aren't requested. They work again when the DAC is disconnected or the mode is off.",
                            ]
                        } else {
                            DesktopStrings.format("d_playing_at_summary", state.outputSummary, fallback = "Playing at %1\$s")
                        }
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["playback", "Playback"],
                            footer = footer,
                        ) {
                            val bpTitle = DesktopStrings["bit_perfect_output", "Bit-perfect output"]
                            row(bpTitle, "bit perfect", "bit-perfect", "dac", "alsa", "wasapi", "exclusive", "usb") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.GraphicEq,
                                    title = bpTitle,
                                    subtitle = desktopBitPerfectSubtitle(bitPerfectMode, bitPerfectStatus, bitPerfectSupported),
                                    value = desktopBitPerfectModeLabel(bitPerfectMode),
                                    badge = if (bitPerfectStatus.active && bitPerfectStatus.exact) "EXACT" else null,
                                    onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_BIT_PERFECT) },
                                )
                            }
                            if (bitPerfectMode.isEnabled && bitPerfectSupported) {
                                val convTitle = DesktopStrings["convert_for_limited_dac", "Convert for lower-spec DACs"]
                                row(convTitle, "convert", "resample", "dac", "bitperfect", divided = false) {
                                    DesktopSettingsSubRow(
                                        title = convTitle,
                                        subtitle = DesktopStrings[
                                            "convert_for_limited_dac_subtitle",
                                            "Converts tracks above your DAC's limit to its highest format. Tracks may take longer to start and won't be bit-perfect to the original.",
                                        ],
                                        checked = convertForLimitedDac,
                                        onCheckedChange = DesktopBitPerfect::setConvertForLimitedDac,
                                    )
                                }
                            }
                            val deviceTitle = DesktopStrings["pipeline_output_device", "Output device"]
                            row(deviceTitle, "audio output", "speakers", "headphones", "alsa", "wasapi") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Headphones,
                                    title = deviceTitle,
                                    subtitle = DesktopAudioDevices.label(),
                                    onClick = { state.onNavigate(DesktopSettingsPage.AUDIO_OUTPUT) },
                                )
                            }
                            val precTitle = DesktopStrings["d_output_precision", "Output precision"]
                            row(precTitle, "pcm", "float", "16-bit", "32-bit") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Tune,
                                        title = precTitle,
                                        value = if (state.outputPrecision.uppercase() == "FLOAT_32") "32-bit float" else "16-bit PCM",
                                        onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_OUTPUT_PRECISION) },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "cat_playback_dsp") {
                        val eqOn by DesktopEqualizerSettings.enabled.collectAsState()
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["d_effects_transitions", "Audio & transitions"],
                        ) {
                            if (!state.automix) {
                                val cfTitle = DesktopStrings["crossfade", "Crossfade"]
                                row(cfTitle, "fade", "gapless") {
                                    DesktopBypassedByBitPerfect(bitPerfectReady) {
                                        DesktopSettingsSliderRow(
                                            icon = Icons.Rounded.GraphicEq,
                                            title = cfTitle,
                                            valueText = if (state.crossfadeSeconds == 0) DesktopStrings["d_off", "Off"] else "${state.crossfadeSeconds}s",
                                            value = state.crossfadeSeconds.toFloat(),
                                            onValueChange = { state.onCrossfadeSecondsChange(it.roundToInt()) },
                                            valueRange = 0f..12f,
                                            steps = 11,
                                        )
                                    }
                                }
                            }
                            val amTitle = DesktopStrings["automix", "Automix [BETA]"]
                            row(amTitle, "crossfade", "smart fade", "mix", "automix") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Bolt,
                                        title = amTitle,
                                        subtitle = if (state.automix) {
                                            "Calculates timing and blends transitions from the tracks"
                                        } else {
                                            "Automatically calculates transition timing without a slider"
                                        },
                                        trailing = {
                                            Switch(
                                                checked = state.automix,
                                                onCheckedChange = state.onAutomixChange,
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = { state.onAutomixChange(!state.automix) },
                                    )
                                    if (state.automix) {
                                        DesktopSettingsSubRow(
                                            title = DesktopStrings["d_automix_art_transition", "Art transition"],
                                            subtitle = DesktopStrings["d_automix_art_transition_desc", "Smoothly blends cover art between tracks during AutoMix"],
                                            checked = state.autoMixArtTransition,
                                            onCheckedChange = state.onAutoMixArtTransitionChange,
                                            enabled = !bitPerfectReady,
                                        )
                                        DesktopSettingsSubRow(
                                            title = DesktopStrings["d_automix_mini_art_transition", "Mini-player art transition"],
                                            subtitle = DesktopStrings["d_automix_mini_art_transition_desc", "Shows artwork transitions in the mini-player during AutoMix"],
                                            checked = state.autoMixMiniArtTransition,
                                            onCheckedChange = state.onAutoMixMiniArtTransitionChange,
                                            enabled = !bitPerfectReady,
                                        )
                                    }
                                }
                            }
                            if (state.automix) {
                                val phraseTitle = DesktopStrings["automix_phrase_length", "Automix phrase length"]
                                row(phraseTitle, "bars", "8 bars", "16 bars") {
                                    DesktopBypassedByBitPerfect(bitPerfectReady) {
                                        Column(Modifier.fillMaxWidth().padding(horizontal = DesktopRowPaddingH, vertical = 12.dp)) {
                                            Text(phraseTitle, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                phraseBarsDetail(state.automixPhraseBars),
                                                color = DesktopSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            val phraseOptions = listOf(0, 8, 16)
                                            PillSegmentedControl(
                                                options = phraseOptions.map(::phraseBarsLabel),
                                                selectedIndex = phraseOptions.indexOf(state.automixPhraseBars).coerceAtLeast(0),
                                                onSelect = { state.onAutomixPhraseBarsChange(phraseOptions[it]) },
                                                enabled = !bitPerfectReady,
                                            )
                                        }
                                    }
                                }
                            }
                            val skipTitle = DesktopStrings["automix_on_skip", "Automix on skip"]
                            row(skipTitle, "skip", "mix") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Bolt,
                                        title = skipTitle,
                                        subtitle = DesktopStrings["automix_on_skip_subtitle", "When you skip ahead, mix into the next song over ~15s instead of cutting"],
                                        trailing = {
                                            Switch(
                                                checked = state.automixOnSkip,
                                                onCheckedChange = state.onAutomixOnSkipChange,
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = { state.onAutomixOnSkipChange(!state.automixOnSkip) },
                                    )
                                }
                            }
                            val perfTitle = DesktopStrings["automix_performance", "Automix performance"]
                            row(perfTitle, "cpu", "analysis") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Bolt,
                                        title = perfTitle,
                                        subtitle = DesktopStrings["automix_performance_subtitle", "Sets how much CPU background analysis may use"],
                                        value = state.automixPerformance.label(),
                                        onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_AUTOMIX_PERFORMANCE) },
                                    )
                                }
                            }
                            val silenceTitle = DesktopStrings["skip_silence", "Skip silence"]
                            row(silenceTitle, "gaps", "silence") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.GraphicEq,
                                        title = silenceTitle,
                                        subtitle = DesktopStrings["d_shorten_long_gaps_rather_than_playing_them_out", "Shorten long gaps rather than playing them out"],
                                        trailing = {
                                            Switch(
                                                checked = state.skipSilence,
                                                onCheckedChange = state.onSkipSilenceChange,
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = { state.onSkipSilenceChange(!state.skipSilence) },
                                    )
                                }
                            }
                            val alignTitle = DesktopStrings["d_smart_version_alignment", "Smart audio alignment"]
                            row(alignTitle, "align", "sync", "version", "source", "offset") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Sync,
                                    title = alignTitle,
                                    subtitle = DesktopStrings[
                                        "d_smart_version_alignment_subtitle",
                                        "Align playback position when switching versions or sources",
                                    ],
                                    trailing = {
                                        Switch(
                                            checked = state.smartVersionAlignment,
                                            onCheckedChange = state.onSmartVersionAlignmentChange,
                                            colors = desktopSwitchColors(),
                                        )
                                    },
                                    onClick = { state.onSmartVersionAlignmentChange(!state.smartVersionAlignment) },
                                )
                            }
                            val spatialTitle = DesktopStrings["spatial_audio", "Spatial audio"]
                            row(spatialTitle, "surround", "3d") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Speaker,
                                        title = spatialTitle,
                                        subtitle = DesktopStrings["spatial_audio_subtitle", "Widens stereo tracks for a more immersive feel"],
                                        trailing = {
                                            Switch(
                                                checked = state.spatialAudio,
                                                onCheckedChange = state.onSpatialAudioChange,
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = { state.onSpatialAudioChange(!state.spatialAudio) },
                                    )
                                }
                            }
                            val eqTitle = DesktopStrings["equalizer", "Equalizer"]
                            row(eqTitle, "eq", "bass", "treble", "balance") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Tune,
                                        title = eqTitle,
                                        subtitle = DesktopStrings["equalizer_subtitle", "Tone, seven bands and balance"],
                                        value = if (eqOn) DesktopStrings["on", "On"] else DesktopStrings["d_off", "Off"],
                                        onClick = { state.onNavigate(DesktopSettingsPage.EQUALIZER) },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "cat_playback_controls") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["d_playback_controls", "Playback controls"],
                        ) {
                            row(DesktopStrings["autoplay", "Autoplay"]) {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.PlayCircle,
                                    title = DesktopStrings["autoplay", "Autoplay"],
                                    subtitle = DesktopStrings["d_keep_the_music_going_with_similar_songs", "Keep the music going with similar songs"],
                                    trailing = {
                                        Switch(checked = state.autoplay, onCheckedChange = state.onAutoplayChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onAutoplayChange(!state.autoplay) },
                                )
                            }
                            row(DesktopStrings["shuffle", "Shuffle"]) {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.Shuffle,
                                    title = DesktopStrings["shuffle", "Shuffle"],
                                    subtitle = DesktopStrings["d_mix_the_order_of_the_current_queue", "Mix the order of the current queue"],
                                    trailing = {
                                        Switch(checked = state.shuffle, onCheckedChange = state.onShuffleChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onShuffleChange(!state.shuffle) },
                                )
                            }
                            row(DesktopStrings["d_repeat", "Repeat"]) {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.Repeat,
                                    title = DesktopStrings["d_repeat", "Repeat"],
                                    value = state.repeatMode.label(),
                                    onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_REPEAT_MODE) },
                                )
                            }
                            val speedTitle = DesktopStrings["d_playback_speed", "Playback speed"]
                            row(speedTitle, "speed", "tempo") {
                                DesktopBypassedByBitPerfect(bitPerfectReady) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Tune,
                                        title = speedTitle,
                                        value = "${"%.2f".format(state.playbackSpeed)}×",
                                        onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_PLAYBACK_SPEED) },
                                    )
                                }
                            }
                            val sleepTitle = DesktopStrings["sleep_timer", "Sleep timer"]
                            row(sleepTitle, "sleep", "timer") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Bedtime,
                                    title = sleepTitle,
                                    subtitle = when {
                                        state.sleepRemainingMs != null -> DesktopStrings.format(
                                            "pause_countdown",
                                            formatTimeText(state.sleepRemainingMs),
                                            fallback = "${formatTimeText(state.sleepRemainingMs)} until playback pauses",
                                        )
                                        state.sleepAfterTrack -> DesktopStrings["pause_after_song", "Pausing when this song ends"]
                                        else -> DesktopStrings["pause_after_a_while", "Pause playback after a while"]
                                    },
                                    value = when {
                                        state.sleepAfterTrack -> DesktopStrings["d_sleep_timer_end_of_track", "End of track"]
                                        state.sleepTimerMinutes != null -> "${state.sleepTimerMinutes} min"
                                        else -> DesktopStrings["d_sleep_timer_off", "Off"]
                                    },
                                    onClick = { state.onNavigate(DesktopSettingsPage.CHOICE_SLEEP_TIMER) },
                                )
                            }
                            row(DesktopStrings["d_show_best_part_title", "Best Part button"]) {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Bolt,
                                    title = DesktopStrings["d_show_best_part_title", "Best Part button"],
                                    subtitle = DesktopStrings["d_show_best_part_desc", "A button on the artwork that skips to the part of the song people replay most"],
                                    trailing = {
                                        Switch(checked = state.showBestPart, onCheckedChange = state.onShowBestPartChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onShowBestPartChange(!state.showBestPart) },
                                )
                            }
                            row(DesktopStrings["d_show_total_duration_title", "Show total duration"]) {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.Clock,
                                    title = DesktopStrings["d_show_total_duration_title", "Show total duration"],
                                    subtitle = DesktopStrings["d_show_total_duration_desc", "Shows the song's full length instead of the time remaining"],
                                    trailing = {
                                        Switch(checked = state.showTotalDuration, onCheckedChange = state.onShowTotalDurationChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onShowTotalDurationChange(!state.showTotalDuration) },
                                )
                            }
                        }
                    }
                }

                // ---- Category: Appearance ----------------------------------------------
                if (showCategory(DesktopSettingsPage.APPEARANCE)) {
                    item(key = "cat_appearance_ui") {
                        val themeMode by DesktopAppearanceSettings.themeMode.collectAsState()
                        val reduceAnim by DesktopAppearanceSettings.reduceAnimation.collectAsState()
                        val smoothTransitions by DesktopAppearanceSettings.smoothTransitions.collectAsState()
                        val reduceBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
                        val disableBlend by DesktopAppearanceSettings.disableBlendEffects.collectAsState()
                        val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
                        val glassClickAnimations by DesktopAppearanceSettings.glassClickAnimations.collectAsState()
                        val glassPerformance by DesktopAppearanceSettings.glassPerformance.collectAsState()
                        val rainbowScale by DesktopAppearanceSettings.liquidGlassRainbowScale.collectAsState()
                        val outlineReflectionScale by DesktopAppearanceSettings.liquidGlassOutlineReflectionScale.collectAsState()
                        val ambient by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
                        val liquidGlassSupported = DesktopPlatform.supportsLiquidGlass
                        val hideVolume by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
                        var glassAdvancedOpen by remember { mutableStateOf(false) }
                        val themeTitle = DesktopStrings["theme", "Theme"]
                        val themeOptions = listOf(
                            DesktopStrings["system", "System"],
                            DesktopStrings["light", "Light"],
                            DesktopStrings["dark", "Dark"],
                        )
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["appearance", "Appearance"],
                        ) {
                            row(themeTitle, "theme", "dark mode", "light mode", "system") {
                                Column(Modifier.fillMaxWidth()) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Brightness4,
                                        title = themeTitle,
                                        subtitle = DesktopStrings[
                                            "d_theme_subtitle",
                                            "Switch between Light, Dark, or follow your system appearance.",
                                        ],
                                    )
                                    PillSegmentedControl(
                                        options = themeOptions,
                                        selectedIndex = DesktopThemeMode.entries.indexOf(themeMode).coerceAtLeast(0),
                                        onSelect = { index ->
                                            DesktopAppearanceSettings.setThemeMode(DesktopThemeMode.entries[index])
                                        },
                                        modifier = Modifier.padding(
                                            start = DesktopDividerInset,
                                            end = DesktopRowPaddingH,
                                            bottom = 14.dp,
                                        ),
                                    )
                                }
                            }
                            row(DesktopStrings["reduce_animation", "Reduce animation"], "animation", "motion", "performance") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Tune,
                                    title = DesktopStrings["reduce_animation", "Reduce animation"],
                                    subtitle = DesktopStrings[
                                        "d_reduce_animation_hint",
                                        "Freezes player gradient drift and hover scale animations. Turning this on automatically turns off High performance mode.",
                                    ],
                                    trailing = {
                                        Switch(checked = reduceAnim, onCheckedChange = DesktopAppearanceSettings::setReduceAnimation, colors = desktopSwitchColors())
                                    },
                                    onClick = { DesktopAppearanceSettings.setReduceAnimation(!reduceAnim) },
                                )
                            }
                            row(DesktopStrings["d_smooth_transitions", "Smooth page & tab transitions"], "animation", "tab", "page", "slide", "transition", "downloads", "local") {
                                Column(Modifier.fillMaxWidth()) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Tune,
                                        title = DesktopStrings["d_smooth_transitions", "Smooth page & tab transitions"],
                                        subtitle = DesktopStrings[
                                            "d_smooth_transitions_hint",
                                            "Animates smooth crossfades when switching pages and directional slides between Local Music or Downloads tabs. Off swaps instantly to save GPU.",
                                        ],
                                        trailing = {
                                            Switch(checked = smoothTransitions, onCheckedChange = DesktopAppearanceSettings::setSmoothTransitions, colors = desktopSwitchColors())
                                        },
                                        onClick = { DesktopAppearanceSettings.setSmoothTransitions(!smoothTransitions) },
                                    )
                                    if (smoothTransitions && reduceAnim) {
                                        DesktopSettingsEffectNote(
                                            text = DesktopStrings[
                                                "d_smooth_transitions_blocked_by_reduce_anim",
                                                "Paused while Reduce animation is on. Turn off Reduce animation to play smooth page and tab transitions.",
                                            ],
                                            isWarning = true,
                                            actionLabel = DesktopStrings["d_turn_off", "Turn off"],
                                            onAction = { DesktopAppearanceSettings.setReduceAnimation(false) },
                                        )
                                    }
                                }
                            }
                            row(DesktopStrings["reduce_dynamic_blur", "Reduce dynamic blur"], "glass", "blur", "solid", "performance") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Computer,
                                    title = DesktopStrings["reduce_dynamic_blur", "Reduce dynamic blur"],
                                    subtitle = DesktopStrings[
                                        "d_reduce_dynamic_blur_hint",
                                        "Replaces Liquid Glass and frosted blur with solid dark fills to save GPU. While active, Liquid Glass is paused and High performance mode turns off.",
                                    ],
                                    trailing = {
                                        Switch(checked = reduceBlur, onCheckedChange = DesktopAppearanceSettings::setReduceDynamicBlur, colors = desktopSwitchColors())
                                    },
                                    onClick = { DesktopAppearanceSettings.setReduceDynamicBlur(!reduceBlur) },
                                )
                            }
                            row(DesktopStrings["d_disable_blend_effects", "Disable blend effects"], "blend", "tint", "white", "player", "menu", "ambient") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Computer,
                                    title = DesktopStrings["d_disable_blend_effects", "Disable blend effects"],
                                    subtitle = DesktopStrings[
                                        "d_disable_blend_effects_hint",
                                        "Draws player icons, text, and dark menus in plain white without artwork tinting. Turning this on also turns off Ambient artwork glow and glass color vibrancy.",
                                    ],
                                    trailing = {
                                        Switch(checked = disableBlend, onCheckedChange = DesktopAppearanceSettings::setDisableBlendEffects, colors = desktopSwitchColors())
                                    },
                                    onClick = { DesktopAppearanceSettings.setDisableBlendEffects(!disableBlend) },
                                )
                            }
                            row(DesktopStrings["d_liquid_glass", "Liquid Glass"], "liquid glass", "apple", "iphone", "refraction", "blur") {
                                Column(Modifier.fillMaxWidth()) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Computer,
                                        title = DesktopStrings["d_liquid_glass", "Liquid Glass"],
                                        subtitle = DesktopStrings[
                                            if (liquidGlassSupported) "d_liquid_glass_subtitle" else "d_liquid_glass_unavailable",
                                            if (liquidGlassSupported) {
                                                "Apple-style refracting glass on floating bars and panels. Requires Windows 10+ or Linux; uses more GPU."
                                            } else {
                                                "Requires Windows 10 or newer, or Linux"
                                            },
                                        ],
                                        enabled = liquidGlassSupported,
                                        trailing = {
                                            Switch(
                                                checked = liquidGlass && liquidGlassSupported,
                                                onCheckedChange = DesktopAppearanceSettings::setLiquidGlass,
                                                enabled = liquidGlassSupported,
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = {
                                            if (liquidGlassSupported) DesktopAppearanceSettings.setLiquidGlass(!liquidGlass)
                                        },
                                    )
                                    if (liquidGlass && liquidGlassSupported) {
                                        when {
                                            reduceBlur -> DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_liquid_glass_blocked_by_blur",
                                                    "Paused while Reduce dynamic blur is on. Turn off Reduce dynamic blur so Liquid Glass renders.",
                                                ],
                                                isWarning = true,
                                                actionLabel = DesktopStrings["d_turn_off", "Turn off"],
                                                onAction = { DesktopAppearanceSettings.setReduceDynamicBlur(false) },
                                            )
                                            disableBlend -> DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_liquid_glass_plain_blend_note",
                                                    "Disable blend effects is on: glass runs in neutral monochrome without artwork vibrancy. Turn off Disable blend effects for full color.",
                                                ],
                                                isWarning = false,
                                                actionLabel = DesktopStrings["d_turn_off", "Turn off"],
                                                onAction = { DesktopAppearanceSettings.setDisableBlendEffects(false) },
                                            )
                                            !ambient -> DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_liquid_glass_pair_ambient_note",
                                                    "Tip: Turn on Ambient artwork glow below so album colors glow behind the glass; enable Better performance for glass if frame rate feels heavy.",
                                                ],
                                            )
                                        }
                                    }
                                }
                            }
                            if (liquidGlass && liquidGlassSupported) {
                                val glassLevel by DesktopAppearanceSettings.liquidGlassLevel.collectAsState()
                                val levelLabels = listOf(
                                    DesktopStrings["low", "Low"],
                                    DesktopStrings["medium", "Medium"],
                                    DesktopStrings["high", "High"],
                                    DesktopStrings["d_liquid_glass_level_impressive", "Impressive"],
                                )
                                row(DesktopStrings["d_liquid_glass_level", "Liquid Glass level"], "glass", "level", "low", "medium", "high", "impressive", divided = false) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopRowPaddingH, vertical = 10.dp)) {
                                        Text(
                                            text = DesktopStrings["d_liquid_glass_level", "Liquid Glass level"],
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = when (glassLevel) {
                                                DesktopLiquidGlassLevel.LOW -> DesktopStrings[
                                                    "d_liquid_glass_level_low_desc",
                                                    "Soft frosted blur without lens refraction; lowest GPU use",
                                                ]
                                                DesktopLiquidGlassLevel.MEDIUM -> DesktopStrings[
                                                    "d_liquid_glass_level_medium_desc",
                                                    "Balanced edge refraction and subtle specular catchlight",
                                                ]
                                                DesktopLiquidGlassLevel.HIGH -> DesktopStrings[
                                                    "d_liquid_glass_level_high_desc",
                                                    "Apple-style curved refraction, vivid color boost, and crisp bevels",
                                                ]
                                                DesktopLiquidGlassLevel.IMPRESSIVE -> DesktopStrings[
                                                    "d_liquid_glass_level_impressive_desc",
                                                    "High-res crystal lens refraction, 7-band chromatic dispersion, Fresnel gloss, and dual specular rims",
                                                ]
                                            },
                                            color = DesktopSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        PillSegmentedControl(
                                            options = levelLabels,
                                            selectedIndex = DesktopLiquidGlassLevel.entries.indexOf(glassLevel).coerceAtLeast(0),
                                            onSelect = { index ->
                                                DesktopAppearanceSettings.setLiquidGlassLevel(DesktopLiquidGlassLevel.entries[index])
                                            },
                                        )
                                    }
                                }
                                if (glassLevel != DesktopLiquidGlassLevel.LOW) {
                                    row(DesktopStrings["d_glass_performance", "Better performance for glass"], "glass", "performance", "refraction", "frosted", divided = false) {
                                        DesktopSettingsSubRow(
                                            title = DesktopStrings["d_glass_performance", "Better performance for glass"],
                                            subtitle = DesktopStrings[
                                                "d_glass_performance_hint",
                                                "When on, switches from curved lens refraction to lighter frosted blur to lower GPU load; when off, full Skia refraction is active.",
                                            ],
                                            checked = glassPerformance,
                                            onCheckedChange = DesktopAppearanceSettings::setGlassPerformance,
                                        )
                                    }
                                }
                                row(DesktopStrings["d_glass_click_animations", "Liquid Glass click animations"], "glass", "click", "press", "ripple", "search", "sidebar", "selector", "animation", divided = false) {
                                    Column(Modifier.fillMaxWidth()) {
                                        DesktopSettingsSubRow(
                                            title = DesktopStrings["d_glass_click_animations", "Liquid Glass click animations"],
                                            subtitle = DesktopStrings[
                                                "d_glass_click_animations_hint",
                                                "Adds subtle press bounce and catchlight ripples when clicking Search, sidebar buttons, and tab selectors. Disabled automatically when Liquid Glass or Reduce animation is active.",
                                            ],
                                            checked = glassClickAnimations,
                                            onCheckedChange = DesktopAppearanceSettings::setGlassClickAnimations,
                                        )
                                        if (glassClickAnimations && reduceAnim && !reduceBlur) {
                                            DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_glass_click_animations_blocked_by_anim",
                                                    "Paused while Reduce animation is on. Turn off Reduce animation to play interactive glass click animations.",
                                                ],
                                                isWarning = true,
                                                actionLabel = DesktopStrings["d_turn_off", "Turn off"],
                                                onAction = { DesktopAppearanceSettings.setReduceAnimation(false) },
                                            )
                                        }
                                    }
                                }
                                row(
                                    DesktopStrings["d_liquid_glass_advanced", "Advanced settings"],
                                    "advanced",
                                    "rainbow",
                                    "distortion",
                                    "chromatic",
                                    "dispersion",
                                    "outline",
                                    "reflection",
                                    "specular",
                                    "fresnel",
                                    "scale",
                                    divided = false,
                                ) {
                                    val expanded = glassAdvancedOpen || searching
                                    val isDefaultAdvanced =
                                        rainbowScale == DesktopAppearanceSettings.DEFAULT_LIQUID_GLASS_RAINBOW_SCALE &&
                                            outlineReflectionScale == DesktopAppearanceSettings.DEFAULT_LIQUID_GLASS_OUTLINE_REFLECTION_SCALE
                                    Column(Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .desktopRowClickable { glassAdvancedOpen = !glassAdvancedOpen }
                                                .padding(
                                                    start = DesktopDividerInset,
                                                    end = DesktopRowPaddingH,
                                                    top = 8.dp,
                                                    bottom = 12.dp,
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = DesktopStrings["d_liquid_glass_advanced", "Advanced settings"],
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = DesktopInk,
                                                )
                                                Text(
                                                    text = DesktopStrings.format(
                                                        "d_liquid_glass_advanced_summary",
                                                        rainbowScale,
                                                        outlineReflectionScale,
                                                        fallback = "Rainbow distortion %1\$d/10 · Outline reflection %2\$d/10",
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = DesktopSecondary,
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Icon(
                                                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = DesktopSecondary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        AnimatedVisibility(
                                            visible = expanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        start = DesktopDividerInset,
                                                        end = DesktopRowPaddingH,
                                                        bottom = 12.dp,
                                                    )
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(DesktopInk.copy(alpha = if (DesktopIsDark) 0.045f else 0.04f))
                                                    .border(0.5.dp, DesktopCardEdge, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                DesktopLiquidGlassScaleSlider(
                                                    title = DesktopStrings["d_liquid_glass_rainbow", "Rainbow distortion"],
                                                    subtitle = DesktopStrings[
                                                        "d_liquid_glass_rainbow_hint",
                                                        "Adjusts 7-band chromatic edge dispersion inside refracted glass corners (1–10).",
                                                    ],
                                                    value = rainbowScale,
                                                    onValueChange = DesktopAppearanceSettings::setLiquidGlassRainbowScale,
                                                )
                                                if (!glassLevel.useRefraction || glassPerformance) {
                                                    DesktopSettingsEffectNote(
                                                        text = DesktopStrings[
                                                            "d_liquid_glass_rainbow_needs_refraction",
                                                            "Rainbow distortion requires Medium, High, or Impressive level with Better performance for glass off.",
                                                        ],
                                                        isWarning = false,
                                                        insetStart = 0.dp,
                                                        insetEnd = 0.dp,
                                                        bottomPad = 2.dp,
                                                    )
                                                }
                                                HorizontalDivider(thickness = 0.5.dp, color = DesktopCardEdge)
                                                DesktopLiquidGlassScaleSlider(
                                                    title = DesktopStrings["d_liquid_glass_outline_reflection", "Outline light reflection"],
                                                    subtitle = DesktopStrings[
                                                        "d_liquid_glass_outline_reflection_hint",
                                                        "Adjusts directional specular catchlight, bevel sheen, and Fresnel rim intensity (1–10).",
                                                    ],
                                                    value = outlineReflectionScale,
                                                    onValueChange = DesktopAppearanceSettings::setLiquidGlassOutlineReflectionScale,
                                                )
                                                if (!isDefaultAdvanced) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End,
                                                    ) {
                                                        DesktopGlassPillButton(
                                                            text = DesktopStrings["reset_to_default", "Reset to default"],
                                                            onClick = DesktopAppearanceSettings::resetLiquidGlassAdvanced,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            row(DesktopStrings["d_ambient_backdrop", "Ambient artwork glow"], "ambient", "backdrop", "glow", "artwork", "mesh", "flat", "dominant", "color") {
                                val ambientStyle by DesktopAppearanceSettings.ambientBackdropStyle.collectAsState()
                                Column(Modifier.fillMaxWidth()) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Computer,
                                        title = DesktopStrings["d_ambient_backdrop", "Ambient artwork glow"],
                                        subtitle = DesktopStrings[
                                            "d_ambient_backdrop_hint",
                                            "Diffuses colors from the playing song's cover art under the window glass. When off, the window uses a plain dark background.",
                                        ],
                                        trailing = {
                                            Switch(checked = ambient, onCheckedChange = DesktopAppearanceSettings::setAmbientBackdrop, colors = desktopSwitchColors())
                                        },
                                        onClick = { DesktopAppearanceSettings.setAmbientBackdrop(!ambient) },
                                    )
                                    if (ambient) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = DesktopCardEdge,
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = DesktopStrings["d_ambient_style_label", "Glow style"],
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = DesktopInk,
                                            )
                                            val styles = listOf(
                                                DesktopAmbientBackdropStyle.MESH,
                                                DesktopAmbientBackdropStyle.FLAT_DOMINANT,
                                            )
                                            PillSegmentedControl(
                                                options = listOf(
                                                    DesktopStrings["d_ambient_style_mesh", "Cover mesh"],
                                                    DesktopStrings["d_ambient_style_flat", "Flat color"],
                                                ),
                                                selectedIndex = styles.indexOf(ambientStyle).coerceAtLeast(0),
                                                onSelect = { idx -> DesktopAppearanceSettings.setAmbientBackdropStyle(styles[idx]) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(CircleShape)
                                                    .background(DesktopInk.copy(alpha = 0.06f)),
                                            )
                                            Text(
                                                text = if (ambientStyle == DesktopAmbientBackdropStyle.FLAT_DOMINANT) {
                                                    DesktopStrings[
                                                        "d_ambient_style_flat_hint",
                                                        "Uses one flat color extracted from the current cover's dominant tone.",
                                                    ]
                                                } else {
                                                    DesktopStrings[
                                                        "d_ambient_style_mesh_hint",
                                                        "Blends a soft multi-color mesh sampled directly from the cover artwork.",
                                                    ]
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = DesktopSecondary,
                                            )
                                        }
                                        when {
                                            disableBlend -> DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_ambient_blocked_by_blend",
                                                    "Paused while Disable blend effects is on. Turn off Disable blend effects so ambient artwork colors can appear.",
                                                ],
                                                isWarning = true,
                                                actionLabel = DesktopStrings["d_turn_off", "Turn off"],
                                                onAction = { DesktopAppearanceSettings.setDisableBlendEffects(false) },
                                            )
                                            reduceBlur -> DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_ambient_with_solid_blur",
                                                    "Reduce dynamic blur is on: ambient colors glow behind pages, while bars and menus stay solid instead of frosted.",
                                                ],
                                                isWarning = false,
                                                actionLabel = DesktopStrings["d_turn_off", "Turn off"],
                                                onAction = { DesktopAppearanceSettings.setReduceDynamicBlur(false) },
                                            )
                                            !liquidGlass && liquidGlassSupported -> DesktopSettingsEffectNote(
                                                text = DesktopStrings[
                                                    "d_ambient_needs_glass_tip",
                                                    "Active on the window background. Turn on Liquid Glass above (with Reduce dynamic blur off) so panels refract this glow.",
                                                ],
                                                isWarning = false,
                                                actionLabel = DesktopStrings["d_turn_on", "Turn on"],
                                                onAction = { DesktopAppearanceSettings.setLiquidGlass(true) },
                                            )
                                        }
                                    }
                                }
                            }
                            if (DesktopPlatform.drawsOwnWindowFrame) {
                                val titleBar by DesktopTitleBarSetting.enabled.collectAsState()
                                row(DesktopStrings["d_title_bar", "Title bar"], "window", "chrome") {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Computer,
                                        title = DesktopStrings["d_title_bar", "Title bar"],
                                        subtitle = DesktopStrings["d_a_slim_bar_above_the_toolbar", "A slim bar above the toolbar with the window's own buttons. Off, the window has no title bar at all."],
                                        trailing = {
                                            Switch(checked = titleBar, onCheckedChange = DesktopTitleBarSetting::set, colors = desktopSwitchColors())
                                        },
                                        onClick = { DesktopTitleBarSetting.set(!titleBar) },
                                    )
                                }
                            }
                            if (DesktopWindowBackdrop.available) {
                                val backdropChoice by DesktopWindowBackdrop.selected.collectAsState()
                                val backdropActive by DesktopWindowBackdrop.active.collectAsState()
                                val appBg by DesktopWindowBackdrop.appBackground.collectAsState()
                                row(DesktopStrings["d_window_material", "Window material"], "mica", "acrylic") {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopRowPaddingH, vertical = 12.dp)) {
                                        Text(DesktopStrings["d_window_material", "Window material"], style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            when {
                                                backdropChoice != DesktopBackdrop.OFF && backdropActive == DesktopBackdrop.OFF ->
                                                    if (DesktopPlatform.isMac) "Native vibrancy could not be attached" else "Needs Windows 11 version 22H2 or later"
                                                backdropChoice == DesktopBackdrop.MICA ->
                                                    if (DesktopPlatform.isMac) "A subtle blur of your desktop wallpaper" else "A soft tint taken from your wallpaper"
                                                backdropChoice == DesktopBackdrop.ACRYLIC -> "A frosted blur of whatever is behind the window"
                                                else -> DesktopStrings["d_window_material_subtitle", "Sets the material for the sidebar and top bar"]
                                            },
                                            color = DesktopSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        PillSegmentedControl(
                                            options = DesktopBackdrop.entries.map { it.label },
                                            selectedIndex = DesktopBackdrop.entries.indexOf(backdropChoice).coerceAtLeast(0),
                                            onSelect = { DesktopWindowBackdrop.set(DesktopBackdrop.entries[it]) },
                                        )
                                    }
                                }
                                row(DesktopStrings["d_app_background", "App background"]) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Computer,
                                        title = DesktopStrings["d_app_background", "App background"],
                                        subtitle = DesktopStrings["d_app_background_subtitle", "Use the window material instead of a solid gray background"],
                                        trailing = {
                                            Switch(checked = appBg, onCheckedChange = DesktopWindowBackdrop::setAppBackground, colors = desktopSwitchColors())
                                        },
                                        onClick = { DesktopWindowBackdrop.setAppBackground(!appBg) },
                                    )
                                }
                            }
                            row(DesktopStrings["hide_volume_bar", "Hide volume bar"], "volume", "slider") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Tune,
                                    title = DesktopStrings["hide_volume_bar", "Hide volume bar"],
                                    subtitle = DesktopStrings["hide_volume_bar_subtitle", "Removes the volume slider from the main player"],
                                    trailing = {
                                        Switch(checked = hideVolume, onCheckedChange = DesktopAppearanceSettings::setHideVolumeBar, colors = desktopSwitchColors())
                                    },
                                    onClick = { DesktopAppearanceSettings.setHideVolumeBar(!hideVolume) },
                                )
                            }
                        }
                    }

                    item(key = "cat_appearance_artwork") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["d_player_artwork", "Player & artwork"],
                        ) {
                            row(DesktopStrings["full_screen_cover_art", "Full-screen cover art"]) {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Album,
                                    title = DesktopStrings["full_screen_cover_art", "Full-screen cover art"],
                                    subtitle = DesktopStrings["full_screen_cover_art_subtitle", "Runs the cover to the edges of the player instead of a square sleeve"],
                                    trailing = {
                                        Switch(checked = state.fullBleedArtwork, onCheckedChange = state.onFullBleedArtworkChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onFullBleedArtworkChange(!state.fullBleedArtwork) },
                                )
                            }
                            row(DesktopStrings["legacy_mesh_gradient", "Legacy mesh gradient"]) {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Album,
                                    title = DesktopStrings["legacy_mesh_gradient", "Legacy mesh gradient"],
                                    subtitle = DesktopStrings["d_use_the_older_blob_backdrop_behind_the_player", "Use the older blob backdrop behind the player"],
                                    trailing = {
                                        Switch(checked = state.legacyMeshGradient, onCheckedChange = state.onLegacyMeshGradientChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onLegacyMeshGradientChange(!state.legacyMeshGradient) },
                                )
                            }
                            row(DesktopStrings["d_animated_canvas", "Animated canvas"], "spotify", "canvas") {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.Play,
                                    title = DesktopStrings["d_animated_canvas", "Animated canvas"],
                                    subtitle = DesktopStrings["d_show_motion_artwork_when_it_is_available", "Show motion artwork when it is available"],
                                    trailing = {
                                        Switch(checked = state.animatedCanvas, onCheckedChange = state.onAnimatedCanvasChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onAnimatedCanvasChange(!state.animatedCanvas) },
                                )
                            }
                            row(DesktopStrings["d_animated_artist_banner", "Animated artist banner"]) {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Person,
                                    title = DesktopStrings["d_animated_artist_banner", "Animated artist banner"],
                                    subtitle = DesktopStrings["d_animated_artist_banner_desc", "Show motion video and animated artwork in artist headers when available"],
                                    trailing = {
                                        Switch(checked = state.animatedArtistBanner, onCheckedChange = state.onAnimatedArtistBannerChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onAnimatedArtistBannerChange(!state.animatedArtistBanner) },
                                )
                            }
                            if (state.animatedCanvas) {
                                row(DesktopStrings["spotify_canvas_setup", "Spotify Canvas setup"], "spotify", "cookie", divided = false) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Extension,
                                        title = DesktopStrings["spotify_canvas_setup", "Spotify Canvas setup"],
                                        subtitle = if (state.spotifyCanvasReady) {
                                            DesktopStrings["d_connected", "Connected"]
                                        } else {
                                            "To use Spotify Canvas, provide your Spotify sp_dc cookie."
                                        },
                                        onClick = { state.onNavigate(DesktopSettingsPage.SPOTIFY_CANVAS) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Category: Performance ---------------------------------------------
                if (showCategory(DesktopSettingsPage.PERFORMANCE)) {
                    item(key = "cat_performance") {
                        val highPerformanceMode by DesktopAppearanceSettings.highPerformanceMode.collectAsState()
                        val selectedRefreshRate by DesktopAppearanceSettings.performanceRefreshRate.collectAsState()
                        val supportedRefreshRates = remember { DesktopDisplayPerformance.supportedRefreshRates() }
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["performance", "Performance"],
                        ) {
                            val hpTitle = DesktopStrings["high_performance_mode", "High performance mode"]
                            row(hpTitle, "performance", "refresh rate", "hz", "fps", "smooth", "blur", "animation") {
                                Column(Modifier.fillMaxWidth()) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Bolt,
                                        title = hpTitle,
                                        subtitle = if (highPerformanceMode) {
                                            DesktopStrings.format(
                                                "d_high_performance_active_hint",
                                                selectedRefreshRate,
                                                fallback = "Targeting %1\$d Hz with full animations and dynamic blur active. Turning on Reduce animation or Reduce dynamic blur will disable this mode.",
                                            )
                                        } else {
                                            DesktopStrings[
                                                "d_high_performance_hint",
                                                "Enables full animations, dynamic blur, and your display's higher refresh rate. Turning this on automatically disables Reduce animation and Reduce dynamic blur.",
                                            ]
                                        },
                                        trailing = {
                                            Switch(
                                                checked = highPerformanceMode,
                                                onCheckedChange = { enabled ->
                                                    if (enabled) {
                                                        pendingConfirmPerformance = true
                                                    } else {
                                                        DesktopAppearanceSettings.setHighPerformanceMode(false)
                                                    }
                                                },
                                                colors = desktopSwitchColors(),
                                            )
                                        },
                                        onClick = {
                                            if (highPerformanceMode) {
                                                DesktopAppearanceSettings.setHighPerformanceMode(false)
                                            } else {
                                                pendingConfirmPerformance = true
                                            }
                                        },
                                    )
                                    DesktopSettingsEffectNote(
                                        text = DesktopStrings[
                                            "d_performance_effects_summary",
                                            "If frame rate feels heavy on integrated GPUs, lower Liquid Glass level to Medium/Low or turn on Better performance for glass in Appearance.",
                                        ],
                                    )
                                }
                            }
                            if (highPerformanceMode) {
                                val refreshTitle = DesktopStrings["refresh_rate", "Refresh rate"]
                                row(refreshTitle, "hz", "fps", "frame rate") {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = DesktopRowPaddingH, vertical = 12.dp)) {
                                        Text(refreshTitle, style = MaterialTheme.typography.bodyLarge)
                                        Spacer(Modifier.height(10.dp))
                                        PillSegmentedControl(
                                            options = supportedRefreshRates.map { "$it Hz" },
                                            selectedIndex = supportedRefreshRates.indexOf(selectedRefreshRate).coerceAtLeast(0),
                                            onSelect = { index ->
                                                DesktopAppearanceSettings.setPerformanceRefreshRate(supportedRefreshRates[index])
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Category: Lyrics --------------------------------------------------
                if (showCategory(DesktopSettingsPage.LYRICS)) {
                    item(key = "cat_lyrics") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["open_lyrics", "Lyrics"],
                        ) {
                            row(DesktopStrings["synced_lyrics", "Synced lyrics"]) {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.LyricsQuote,
                                    title = DesktopStrings["synced_lyrics", "Synced lyrics"],
                                    subtitle = DesktopStrings["synced_lyrics_subtitle", "Lights up the words on the player as they're sung"],
                                    trailing = {
                                        Switch(checked = state.syncedLyrics, onCheckedChange = state.onSyncedLyricsChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onSyncedLyricsChange(!state.syncedLyrics) },
                                )
                            }
                            if (state.syncedLyrics) {
                                row(DesktopStrings["d_blur_unfocused_lyrics", "Blur unfocused lyrics"]) {
                                    DesktopSettingsSubRow(
                                        title = DesktopStrings["d_blur_unfocused_lyrics", "Blur unfocused lyrics"],
                                        subtitle = DesktopStrings["d_keeps_the_spotlight_on_the_current_line", "Keeps the spotlight on the current line"],
                                        checked = state.lyricsBlur,
                                        onCheckedChange = state.onLyricsBlurChange,
                                    )
                                }
                                val hideLyricsPreview by DesktopAppearanceSettings.hideLyricsPreview.collectAsState()
                                row(
                                    DesktopStrings["d_hide_player_lyrics", "Hide lyrics preview in player"],
                                    DesktopStrings["d_hide_player_lyrics_subtitle", "Hides the synced lyric line above the scrubber in the main player"],
                                    divided = false,
                                ) {
                                    DesktopSettingsSubRow(
                                        title = DesktopStrings["d_hide_player_lyrics", "Hide lyrics preview in player"],
                                        subtitle = DesktopStrings["d_hide_player_lyrics_subtitle", "Hides the synced lyric line above the scrubber in the main player"],
                                        checked = hideLyricsPreview,
                                        onCheckedChange = DesktopAppearanceSettings::setHideLyricsPreview,
                                    )
                                }
                                row(DesktopStrings["lyrics_sources", "Lyrics sources"]) {
                                    DesktopSettingsRow(
                                        icon = BitChordIcons.LyricsQuote,
                                        title = DesktopStrings["lyrics_sources", "Lyrics sources"],
                                        subtitle = state.enabledLyricsSources.joinToString(", ").ifEmpty { "None. Lyrics will not be fetched." },
                                        onClick = { state.onNavigate(DesktopSettingsPage.LYRICS_SOURCES) },
                                    )
                                }
                                val translationLang by DesktopTranslationSetting.language.collectAsState()
                                row(DesktopStrings["translation_language", "Translation language"]) {
                                    DesktopSettingsRow(
                                        icon = Icons.Rounded.Language,
                                        title = DesktopStrings["translation_language", "Translation language"],
                                        subtitle = DesktopTranslationSetting.describe(translationLang),
                                        onClick = { state.onNavigate(DesktopSettingsPage.TRANSLATION_LANGUAGE) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Category: Local Music ---------------------------------------------
                if (showCategory(DesktopSettingsPage.LOCAL_MUSIC)) {
                    item(key = "cat_local_music") {
                        var folderLabel by remember { mutableStateOf(DesktopLocalMusic.folderLabel()) }
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["local_music", "Local Music"],
                        ) {
                            row(DesktopStrings["d_local_music_folder", "Local music folder"], folderLabel) {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Folder,
                                    title = DesktopStrings["d_local_music_folder", "Local music folder"],
                                    subtitle = folderLabel,
                                    onClick = {
                                        state.onChooseLocalMusicFolder { folderLabel = DesktopLocalMusic.folderLabel() }
                                    },
                                )
                            }
                            if (DesktopLocalMusic.folder() != null) {
                                row(DesktopStrings["use_all_audio_folders", "Use all audio folders"]) {
                                    DesktopSettingsRow(
                                        icon = BitChordIcons.Library,
                                        title = DesktopStrings["use_all_audio_folders", "Use all audio folders"],
                                        subtitle = DesktopStrings["use_all_audio_folders_subtitle", "Remove the folder limit and scan music across the device"],
                                        onClick = {
                                            DesktopLocalMusic.setFolder(null)
                                            folderLabel = DesktopLocalMusic.folderLabel()
                                            state.onLocalMusicFolderChanged()
                                        },
                                    )
                                }
                            }
                            row(DesktopStrings["filter_non_music_audio", "Filter non-music audio"], "wav", "short clips") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Tune,
                                    title = DesktopStrings["filter_non_music_audio", "Filter non-music audio"],
                                    subtitle = DesktopStrings["filter_non_music_audio_subtitle", "Hides clips under 30 seconds, WAV files, voice notes, recordings and system sounds"],
                                    trailing = {
                                        Switch(checked = state.filterNonMusicAudio, onCheckedChange = state.onFilterNonMusicAudioChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onFilterNonMusicAudioChange(!state.filterNonMusicAudio) },
                                )
                            }
                        }
                    }
                }

                // ---- Category: Storage -------------------------------------------------
                if (showCategory(DesktopSettingsPage.STORAGE)) {
                    item(key = "cat_storage") {
                        var limitMb by remember { mutableStateOf(DesktopMediaCache.limitMb()) }
                        var cleared by remember { mutableStateOf<String?>(null) }
                        var imagesCleared by remember { mutableStateOf(false) }
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["storage", "Storage"],
                        ) {
                            row(DesktopStrings["song_cache_limit", "Song cache limit"], "cache", "storage") {
                                DesktopSettingsSliderRow(
                                    icon = Icons.Rounded.Storage,
                                    title = DesktopStrings["song_cache_limit", "Song cache limit"],
                                    subtitle = if (limitMb > DesktopMediaCache.WARNING_MB) {
                                        "Keeps up to ${formatCacheSize(limitMb)} on disk. This can take significant free space."
                                    } else {
                                        "Keeps downloaded audio on disk for instant seeking and replays"
                                    },
                                    valueText = formatCacheSize(limitMb),
                                    value = limitMb.toFloat(),
                                    onValueChange = { limitMb = it.roundToInt() },
                                    onValueChangeFinished = { DesktopMediaCache.setLimitMb(limitMb) },
                                    valueRange = DesktopMediaCache.MIN_LIMIT_MB.toFloat()..DesktopMediaCache.MAX_LIMIT_MB.toFloat(),
                                    steps = 18,
                                )
                            }
                            row(DesktopStrings["d_clear_song_cache", "Clear song cache"], "cache") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.DeleteSweep,
                                    title = DesktopStrings["d_clear_song_cache", "Clear song cache"],
                                    subtitle = cleared ?: "Frees space used by downloaded audio",
                                    onClick = {
                                        DesktopMediaCache.clear()
                                        cleared = DesktopStrings["d_song_cache_cleared", "Song cache cleared"]
                                    },
                                )
                            }
                            row(DesktopStrings["d_clear_image_cache", "Clear image cache"], "artwork", "cache") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.DeleteSweep,
                                    title = DesktopStrings["d_clear_image_cache", "Clear image cache"],
                                    subtitle = if (imagesCleared) {
                                        DesktopStrings["d_image_cache_cleared", "Image cache cleared"]
                                    } else {
                                        "Frees space used by album artwork"
                                    },
                                    onClick = {
                                        DesktopArtworkCache.clear()
                                        imagesCleared = true
                                    },
                                )
                            }
                        }
                    }
                }

                // ---- Search shortcuts: Backup, Restore & Replay ------------------------
                if (searching) {
                    item(key = "search_backup_restore") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["d_backup_restore", "Backup and Restore"],
                        ) {
                            val replayTitle = DesktopStrings["replay", "Replay"]
                            row(replayTitle, "stats", "history", "wrapped", "genres", "backup", "restore") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.BarChart,
                                    title = replayTitle,
                                    subtitle = DesktopStrings["replay_subtitle", "Your top songs, artists, albums and genres"],
                                    onClick = { state.onNavigate(DesktopSettingsPage.BACKUP_AND_RESTORE) },
                                )
                            }
                            val driveTitle = DesktopStrings["d_drive_backup_title", "Google Drive backup"]
                            row(driveTitle, "backup", "restore", "google", "drive", "cloud", "byo", "oauth", "replay") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Cloud,
                                    title = driveTitle,
                                    subtitle = DesktopStrings["d_drive_backup_row_subtitle", "Manual and automatic backups saved in your Google Drive app folder"],
                                    onClick = { state.onNavigate(DesktopSettingsPage.GOOGLE_DRIVE_BACKUP) },
                                )
                            }
                            val exportTitle = DesktopStrings["d_export_backup", "Export backup to file"]
                            row(exportTitle, "backup", "json", "import", "restore") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Backup,
                                    title = exportTitle,
                                    subtitle = DesktopStrings["d_backup_restore_subtitle", "Save and restore your library, playlists, listening history, and settings"],
                                    onClick = { state.onNavigate(DesktopSettingsPage.BACKUP_AND_RESTORE) },
                                )
                            }
                        }
                    }
                }

                // ---- Category: Miscellaneous -------------------------------------------
                if (showCategory(DesktopSettingsPage.MISCELLANEOUS)) {
                    item(key = "cat_misc_general") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["miscellaneous", "Miscellaneous"],
                        ) {
                            row(DesktopStrings["d_dont_repeat_songs_in_current_session", "Don’t repeat songs in current session"]) {
                                DesktopSettingsRow(
                                    icon = BitChordIcons.Repeat,
                                    title = DesktopStrings["d_dont_repeat_songs_in_current_session", "Don’t repeat songs in current session"],
                                    subtitle = DesktopStrings["d_autoplay_wont_suggest_a_song_already_played_or_suggested", "AutoPlay won’t suggest a song already played or suggested this session"],
                                    trailing = {
                                        Switch(checked = state.dontRepeatSuggestions, onCheckedChange = state.onDontRepeatSuggestionsChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onDontRepeatSuggestionsChange(!state.dontRepeatSuggestions) },
                                )
                            }
                            row(DesktopStrings["show_nerd_stats", "Show stats for nerds"], "stats", "codec", "bitrate") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Info,
                                    title = DesktopStrings["show_nerd_stats", "Show stats for nerds"],
                                    subtitle = DesktopStrings["show_nerd_stats_subtitle", "Codec, bitrate and sample rate on the player"],
                                    trailing = {
                                        Switch(checked = state.showNerdStats, onCheckedChange = state.onShowNerdStatsChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onShowNerdStatsChange(!state.showNerdStats) },
                                )
                            }
                            val swipeBackGesture by DesktopAppearanceSettings.swipeBackGesture.collectAsState()
                            row(
                                DesktopStrings["d_swipe_back_gesture", "Two-finger swipe back gesture"],
                                DesktopStrings["d_swipe_back_gesture_subtitle", "Go back using the mouse back button or left-to-right two-finger swipe"],
                                "gesture",
                                "swipe",
                            ) {
                                DesktopSettingsRow(
                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                    title = DesktopStrings["d_swipe_back_gesture", "Two-finger swipe back gesture"],
                                    subtitle = DesktopStrings["d_swipe_back_gesture_subtitle", "Go back using the mouse back button or left-to-right two-finger swipe"],
                                    trailing = {
                                        Switch(
                                            checked = swipeBackGesture,
                                            onCheckedChange = DesktopAppearanceSettings::setSwipeBackGesture,
                                            colors = desktopSwitchColors(),
                                        )
                                    },
                                    onClick = { DesktopAppearanceSettings.setSwipeBackGesture(!swipeBackGesture) },
                                )
                            }
                            row(DesktopStrings["d_tray_icon", "Tray icon"], "tray", "background") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Computer,
                                    title = DesktopStrings["d_tray_icon", "Tray icon"],
                                    subtitle = DesktopStrings["d_show_bitchord_in_the_system_tray_with_playback_controls", "Show BitChord in the system tray, with playback controls"],
                                    trailing = {
                                        Switch(checked = state.trayIconEnabled, onCheckedChange = state.onTrayIconChange, colors = desktopSwitchColors())
                                    },
                                    onClick = { state.onTrayIconChange(!state.trayIconEnabled) },
                                )
                            }
                            if (state.trayIconEnabled) {
                                row(DesktopStrings["d_keep_playing_when_closed", "Keep playing when closed"], "tray", "close", divided = false) {
                                    DesktopSettingsSubRow(
                                        title = DesktopStrings["d_keep_playing_when_closed", "Keep playing when closed"],
                                        subtitle = DesktopStrings["d_closing_the_window_leaves_bitchord_in_the_tray_instead_o", "Closing the window leaves BitChord in the tray instead of quitting"],
                                        checked = state.closeToTray,
                                        onCheckedChange = state.onCloseToTrayChange,
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Category: Language ------------------------------------------------
                if (page == DesktopSettingsPage.LANGUAGE) {
                    item(key = "cat_language") {
                        val chosen by DesktopStrings.language.collectAsState()
                        DesktopChoiceGroup(
                            options = DesktopStrings.available,
                            selected = DesktopStrings.available.firstOrNull { it.tag == chosen } ?: DesktopStrings.available.first(),
                            label = { it.label },
                            detail = { if (it.tag.isBlank()) DesktopStrings["d_follow_os_language", "Follow operating system language"] else it.tag },
                            footer = DesktopStrings["d_language_footer", "Applies immediately across menus, player labels and notifications."],
                            onSelect = { DesktopStrings.setLanguage(it.tag) },
                        )
                    }
                } else if (searching) {
                    item(key = "search_language") {
                        DesktopSearchableGroup(search, header = DesktopStrings["language", "Language"]) {
                            row(DesktopStrings["app_language", "App language"], DesktopStrings.languageLabel(), "locale") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.Language,
                                    title = DesktopStrings["app_language", "App language"],
                                    value = DesktopStrings.languageLabel(),
                                    onClick = { state.onNavigate(DesktopSettingsPage.LANGUAGE) },
                                )
                            }
                        }
                    }
                }

                // ---- Choice Sub-Pages --------------------------------------------------
                when (page) {
                    DesktopSettingsPage.CHOICE_AUDIO_QUALITY -> item(key = "choice_aq") {
                        DesktopChoiceGroup(
                            options = DesktopAudioQuality.entries.reversed(),
                            selected = state.audioQuality,
                            label = { it.label },
                            detail = { it.detail },
                            footer = DesktopStrings["d_audio_quality_footer", "Controls which source tiers may answer when starting or upgrading a track."],
                            onSelect = {
                                state.onAudioQualityChange(it)
                                state.onBack()
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_DOWNLOAD_QUALITY -> item(key = "choice_dq") {
                        val options = listOf("LOSSLESS", "HIGH", "STANDARD")
                        DesktopChoiceGroup(
                            options = options,
                            selected = state.downloadQuality,
                            label = { downloadQualityLabel(it) },
                            detail = { downloadQualityDetail(it) },
                            footer = DesktopStrings["download_quality_dialog_subtitle", "Higher quality takes more storage space per downloaded track."],
                            onSelect = {
                                state.onDownloadQualityChange(it)
                                state.onBack()
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_BIT_PERFECT -> item(key = "choice_bp") {
                        DesktopChoiceGroup(
                            options = DesktopBitPerfectMode.entries,
                            selected = bitPerfectMode,
                            label = { desktopBitPerfectModeTitle(it) },
                            detail = { desktopBitPerfectModeDescription(it, bitPerfectSupported) },
                            footer = DesktopStrings[
                                "bit_perfect_sheet_warning",
                                "Sends the track to the DAC unchanged. The equalizer, loudness normalization, crossfade, Automix, playback speed and BitChord's volume are bypassed.",
                            ],
                            enabled = { it == DesktopBitPerfectMode.OFF || bitPerfectSupported },
                            onSelect = { picked ->
                                if (picked == DesktopBitPerfectMode.OFF) {
                                    DesktopBitPerfect.setMode(DesktopBitPerfectMode.OFF)
                                    state.onBack()
                                } else if (bitPerfectMode != picked) {
                                    pendingConfirmBitPerfect = picked
                                } else {
                                    state.onBack()
                                }
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_AUTOMIX_PERFORMANCE -> item(key = "choice_am_perf") {
                        DesktopChoiceGroup(
                            options = AutomixPerformanceMode.entries,
                            selected = state.automixPerformance,
                            label = { it.label() },
                            detail = { it.detail() },
                            footer = DesktopStrings["automix_performance_warning", "Higher performance analyses tracks faster, but can use more CPU while playing."],
                            onSelect = {
                                state.onAutomixPerformanceChange(it)
                                state.onBack()
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_OUTPUT_PRECISION -> item(key = "choice_prec") {
                        val options = listOf("PCM_16", "FLOAT_32")
                        DesktopChoiceGroup(
                            options = options,
                            selected = state.outputPrecision,
                            label = { if (it == "FLOAT_32") "32-bit float" else "16-bit PCM" },
                            detail = {
                                if (it == "FLOAT_32") "Best headroom for EQ, Spatial Audio and mixing" else "Standard 16-bit integer output"
                            },
                            footer = DesktopStrings.format("d_playing_at_summary", state.outputSummary, fallback = "Playing at %1\$s"),
                            onSelect = {
                                state.onOutputPrecisionChange(it)
                                state.onBack()
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_REPEAT_MODE -> item(key = "choice_repeat") {
                        DesktopChoiceGroup(
                            options = DesktopRepeatMode.entries,
                            selected = state.repeatMode,
                            label = { it.label() },
                            footer = null,
                            onSelect = {
                                state.onRepeatModeChange(it)
                                state.onBack()
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_PLAYBACK_SPEED -> item(key = "choice_speed") {
                        val speeds = listOf(0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 2.00f)
                        DesktopChoiceGroup(
                            options = speeds,
                            selected = speeds.minByOrNull { abs(it - state.playbackSpeed) } ?: 1.0f,
                            label = {
                                if (it == 1.0f) DesktopStrings["d_normal_speed", "1.00× (Normal)"] else "${"%.2f".format(it)}×"
                            },
                            footer = null,
                            onSelect = {
                                state.onPlaybackSpeedChange(it)
                                state.onBack()
                            },
                        )
                    }
                    DesktopSettingsPage.CHOICE_SLEEP_TIMER -> item(key = "choice_sleep") {
                        val sleepOptions = listOf(0, 15, 30, 45, 60, -1)
                        val selectedSleep = when {
                            state.sleepAfterTrack -> -1
                            state.sleepTimerMinutes != null -> state.sleepTimerMinutes
                            else -> 0
                        }
                        DesktopChoiceGroup(
                            options = sleepOptions,
                            selected = selectedSleep,
                            label = { option ->
                                when (option) {
                                    0 -> DesktopStrings["d_sleep_timer_off", "Off"]
                                    -1 -> DesktopStrings["d_sleep_timer_end_of_track", "End of track"]
                                    else -> DesktopStrings.format("d_sleep_timer_minutes", option, fallback = "$option min")
                                }
                            },
                            footer = null,
                            onSelect = { option ->
                                when (option) {
                                    0 -> DesktopSleepTimer.cancel()
                                    -1 -> DesktopSleepTimer.startAfterTrack()
                                    else -> DesktopSleepTimer.start(option)
                                }
                                state.onBack()
                            },
                        )
                    }
                    else -> Unit
                }

                if (isMain) {
                    item(key = "main_about") {
                        DesktopSearchableGroup(
                            search = search,
                            header = DesktopStrings["d_about", "About & Updates"],
                        ) {
                            val updTitle = DesktopStrings["d_updates_system_info", "Updates & System Info"]
                            row(updTitle, "version", "updates", "system info", "install") {
                                DesktopSettingsRow(
                                    icon = Icons.Rounded.SystemUpdate,
                                    title = updTitle,
                                    subtitle = DesktopStrings.format(
                                        "d_settings_version_summary",
                                        DesktopSystemInfo.appVersion,
                                        DesktopSystemInfo.installType.label,
                                        fallback = "Version %1\$s (%2\$s) · Check updates & install method",
                                    ),
                                    onClick = { state.onNavigate(DesktopSettingsPage.UPDATES) },
                                )
                            }
                        }
                    }

                    if (searching && !search.anyMatch) {
                        item(key = "search_empty") {
                            Text(
                                text = DesktopStrings.format(
                                    "settings_search_empty",
                                    state.query.trim(),
                                    fallback = "No settings match “%1\$s”",
                                ),
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                            )
                        }
                    } else if (!searching) {
                        item(key = "main_footer") {
                            DesktopSettingsFooter(
                                onLicenses = { state.onNavigate(DesktopSettingsPage.LICENSES) },
                                onUpdates = { state.onNavigate(DesktopSettingsPage.UPDATES) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingConfirmBitPerfect?.let { picked ->
        DesktopDialogPanel(
            onDismiss = { pendingConfirmBitPerfect = null },
            maxWidth = 440,
            popup = true,
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(
                    text = DesktopStrings["bit_perfect_confirm_title", "Turn on bit-perfect output?"],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = DesktopStrings[
                        "bit_perfect_confirm_message",
                        "The DAC gets the music at full scale, and BitChord's volume bar is bypassed while active. Lower your amplifier, speakers, or headphones before playing.",
                    ],
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopSecondary,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    DesktopGlassPillButton(
                        text = DesktopStrings["cancel", "Cancel"],
                        onClick = { pendingConfirmBitPerfect = null },
                    )
                    DesktopGlassPillButton(
                        text = DesktopStrings["d_turn_on", "Turn on"],
                        accent = true,
                        onClick = {
                            DesktopBitPerfect.setMode(picked)
                            pendingConfirmBitPerfect = null
                            state.onBack()
                        },
                    )
                }
            }
        }
    }

    if (pendingConfirmPerformance) {
        DesktopDialogPanel(
            onDismiss = { pendingConfirmPerformance = false },
            maxWidth = 440,
            popup = true,
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(
                    text = DesktopStrings["d_enable_high_performance_title", "Enable high performance mode?"],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = DesktopStrings[
                        "d_enable_high_performance_confirmation",
                        "High performance mode enables full animations, dynamic blur, and higher frame pacing. This uses more CPU and GPU power on laptops.",
                    ],
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopSecondary,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    DesktopGlassPillButton(
                        text = DesktopStrings["not_yet", "Not yet"],
                        onClick = { pendingConfirmPerformance = false },
                    )
                    DesktopGlassPillButton(
                        text = DesktopStrings["enable", "Enable"],
                        accent = true,
                        onClick = {
                            DesktopAppearanceSettings.setHighPerformanceMode(true)
                            pendingConfirmPerformance = false
                        },
                    )
                }
            }
        }
    }

    editingSource?.let { config ->
        DesktopSourceEditorDialog(
            config = config,
            configuredSources = state.sourceConfigs,
            isNew = state.sourceConfigs.none { it.id == config.id },
            status = state.sourceStatus[config.id],
            onDismiss = { editingSource = null },
            onSave = {
                state.onSaveSource(it)
                editingSource = null
            },
            onRemove = {
                state.onRemoveSource(config)
                editingSource = null
            },
            onTest = state.onTestSource,
        )
    }
}

@Composable
private fun DesktopSettingsTopHeader(
    title: String,
    isMain: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (!isMain) {
                DesktopToolbarButton(onClick = onBack, size = 36.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        DesktopStrings["back", "Back"],
                        tint = DesktopInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isMain) {
            DesktopSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                placeholder = DesktopStrings["settings_search_hint", "Search settings"],
                modifier = Modifier.padding(start = 24.dp).width(280.dp),
            )
        }
    }
}

@Composable
internal fun DesktopGroupedCard(
    header: String? = null,
    footer: String? = null,
    topSpacing: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    if (header != null) {
        Text(
            text = header.uppercase(Locale.ROOT),
            color = DesktopSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, top = topSpacing, bottom = 8.dp),
        )
    } else if (topSpacing > 0.dp) {
        Spacer(Modifier.height(topSpacing))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .desktopSettingsCardSurface(DesktopGroupShape),
    ) {
        content()
    }
    if (footer != null) {
        Text(
            text = footer,
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
        )
    }
}

@Composable
internal fun DesktopGroupedDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = DesktopDividerInset),
        thickness = 0.5.dp,
        color = DesktopCardEdge,
    )
}

@Composable
internal fun DesktopSettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    iconPainter: Painter? = null,
    iconContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = onClick != null && enabled) { onClick?.invoke() }
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .padding(horizontal = DesktopRowPaddingH, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            iconContent != null -> iconContent()
            iconPainter != null -> Icon(iconPainter, null, tint = DesktopInk, modifier = Modifier.size(DesktopIconSize))
            icon != null -> Icon(icon, null, tint = DesktopInk, modifier = Modifier.size(DesktopIconSize))
        }
        Spacer(Modifier.width(DesktopIconGap))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DesktopInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = badge.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        color = DesktopAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(DesktopInk.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else if (value != null || onClick != null) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                BitChordIcons.ChevronRight,
                null,
                tint = DesktopSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DesktopSettingsEffectNote(
    text: String,
    isWarning: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    insetStart: androidx.compose.ui.unit.Dp = DesktopDividerInset,
    insetEnd: androidx.compose.ui.unit.Dp = DesktopRowPaddingH,
    bottomPad: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val palette = LocalDesktopPalette.current
    val noteShape = RoundedCornerShape(10.dp)
    val bgColor = if (isWarning) {
        Color(0xFFFFB74D).copy(alpha = if (palette.isDark) 0.10f else 0.18f)
    } else {
        palette.ink.copy(alpha = 0.05f)
    }
    val borderColor = if (isWarning) {
        Color(0xFFFFB74D).copy(alpha = if (palette.isDark) 0.28f else 0.42f)
    } else {
        DesktopCardEdge
    }
    val iconTint = if (isWarning) palette.warningIcon else DesktopSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = insetStart, end = insetEnd, bottom = bottomPad)
            .clip(noteShape)
            .background(bgColor)
            .border(0.5.dp, borderColor, noteShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) palette.warningText else DesktopSecondary,
            modifier = Modifier.weight(1f),
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 28.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopAccent,
                )
            }
        }
    }
}

@Composable
private fun DesktopLiquidGlassScaleSlider(
    title: String,
    subtitle: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val clamped = value.coerceIn(
        DesktopAppearanceSettings.MIN_LIQUID_GLASS_SCALE,
        DesktopAppearanceSettings.MAX_LIQUID_GLASS_SCALE,
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = DesktopInk,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DesktopInk.copy(alpha = 0.10f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$clamped / 10",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        DesktopBareSlider(
            value = clamped.toFloat(),
            onValueChange = { raw -> onValueChange(raw.roundToInt()) },
            valueRange = DesktopAppearanceSettings.MIN_LIQUID_GLASS_SCALE.toFloat()..
                DesktopAppearanceSettings.MAX_LIQUID_GLASS_SCALE.toFloat(),
            steps = 8,
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = DesktopSecondary)
            Text("5", style = MaterialTheme.typography.labelSmall, color = DesktopSecondary)
            Text("10", style = MaterialTheme.typography.labelSmall, color = DesktopSecondary)
        }
    }
}

@Composable
private fun DesktopSettingsSubRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = enabled) { onCheckedChange(!checked) }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(
                start = DesktopDividerInset,
                end = DesktopRowPaddingH,
                top = if (LocalDesktopSubRowStandalone.current) 10.dp else 2.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = DesktopInk)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, maxLines = 4)
            }
        }
        Spacer(Modifier.width(14.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, colors = desktopSwitchColors())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopSettingsSliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    subtitle: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(Modifier.padding(start = DesktopRowPaddingH, end = DesktopRowPaddingH, top = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DesktopInk, modifier = Modifier.size(DesktopIconSize))
            Spacer(Modifier.width(DesktopIconGap))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = DesktopInk)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = DesktopSecondary)
        }
        DesktopBareSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().padding(start = DesktopIconSize + DesktopIconGap),
        )
    }
}

@Composable
private fun <T> DesktopChoiceGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    footer: String?,
    detail: (@Composable (T) -> String)? = null,
    enabled: (T) -> Boolean = { true },
    onSelect: (T) -> Unit,
) {
    DesktopGroupedCard(footer = footer, topSpacing = 4.dp) {
        options.forEachIndexed { index, option ->
            if (index > 0) DesktopGroupedDivider()
            val available = enabled(option)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .desktopRowClickable(enabled = available) { onSelect(option) }
                    .alpha(if (available) 1f else 0.45f)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = DesktopRowPaddingH, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label(option), style = MaterialTheme.typography.bodyLarge, color = DesktopInk)
                    detail?.let { desc ->
                        Text(desc(option), style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
                    }
                }
                if (option == selected) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        BitChordIcons.Check,
                        DesktopStrings["selected", "Selected"],
                        tint = DesktopAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopBypassedByBitPerfect(bypassed: Boolean, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(if (bypassed) 0.38f else 1f, label = "bpBypass")
    Column(
        Modifier
            .alpha(alpha)
            .then(
                if (bypassed) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                            var sideways: Boolean? = null
                            var travel = Offset.Zero
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { change ->
                                    when {
                                        change.changedToUpIgnoreConsumed() -> change.consume()
                                        change.positionChanged() -> {
                                            travel += change.positionChange()
                                            if (sideways == null && travel.getDistance() > viewConfiguration.touchSlop / 2) {
                                                sideways = abs(travel.x) > abs(travel.y)
                                            }
                                            if (sideways == true) change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

@Composable
private fun DesktopSettingsFooter(onLicenses: () -> Unit, onUpdates: () -> Unit) {
    val version = DesktopSystemInfo.appVersion
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = DesktopAccent, textDecoration = TextDecoration.Underline),
    )
    Text(
        text = buildAnnotatedString {
            append("BitChord Desktop v$version  ")
            withLink(LinkAnnotation.Url("https://github.com/efzynx/BitChord-Desktop", linkStyles)) {
                append("GitHub")
            }
            append("  ")
            withLink(LinkAnnotation.Clickable("updates", linkStyles) { onUpdates() }) {
                append(DesktopStrings["d_updates", "Updates"])
            }
            append("  ")
            withLink(LinkAnnotation.Clickable("licenses", linkStyles) { onLicenses() }) {
                append(DesktopStrings["d_licenses", "Licenses"])
            }
            append("\n~YouTube Music Client")
        },
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun DesktopSourceSettingsRow(
    position: Int,
    config: DesktopSourceConfig,
    status: String?,
    skippedByQuality: Boolean = false,
    onEdit: (() -> Unit)?,
    onToggle: ((Boolean) -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val enabledAlpha = if (config.enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = onEdit != null) { onEdit?.invoke() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$position", color = DesktopSecondary, modifier = Modifier.width(20.dp).alpha(enabledAlpha))
        Icon(
            when (config.kind) {
                DesktopSourceKind.ADDON, DesktopSourceKind.CUSTOM_MODULE, DesktopSourceKind.MODULE -> Icons.Rounded.Extension
                DesktopSourceKind.JIOSAAVN -> Icons.Rounded.GraphicEq
                DesktopSourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
            },
            contentDescription = null,
            tint = DesktopAccent,
            modifier = Modifier.size(21.dp).alpha(enabledAlpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(enabledAlpha)) {
            Text(config.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    skippedByQuality -> "Skipped at this audio quality"
                    status != null -> status
                    !config.isComplete -> "Setup required"
                    config.kind.needsServer -> "Checking…"
                    else -> config.kind.detail
                } + if (!config.allowDownloads) " · " + DesktopStrings["source_downloads_off", "Streaming only"] else "",
                color = if (skippedByQuality) DesktopAccent else DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onMoveUp != null) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Move source earlier")
            }
        }
        if (onMoveDown != null) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Move source later")
            }
        }
        if (onToggle == null) {
            Text(DesktopStrings["always_on", "Always on"], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Switch(checked = config.enabled, onCheckedChange = onToggle, colors = desktopSwitchColors())
        }
    }
}

@Composable
private fun DesktopSourceEditorDialog(
    config: DesktopSourceConfig,
    configuredSources: List<DesktopSourceConfig>,
    isNew: Boolean,
    status: String?,
    onDismiss: () -> Unit,
    onSave: (DesktopSourceConfig) -> Unit,
    onRemove: () -> Unit,
    onTest: (DesktopSourceConfig) -> Unit,
) {
    var label by remember(config.id) { mutableStateOf(config.label) }
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var checking by remember(config.id) { mutableStateOf(false) }
    var message by remember(config.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val candidate = config.copy(label = label.trim(), baseUrl = baseUrl.trim())

    fun identify(thenSave: Boolean) {
        if (candidate.baseUrl.isBlank() || checking) return
        checking = true
        message = null
        scope.launch {
            val outcome = DesktopSourceFormats.identify(candidate.baseUrl)
            checking = false
            outcome.fold(
                onSuccess = { detected ->
                    when (detected) {
                        is DesktopDetectedFormat.Addon -> {
                            val named = candidate.copy(
                                kind = DesktopSourceKind.ADDON,
                                baseUrl = detected.baseUrl,
                                label = candidate.label.ifBlank { detected.manifest.displayName },
                                allowDownloads = detected.manifest.downloadsAllowed,
                            )
                            val duplicate = configuredSources.duplicateOf(named.baseUrl, exceptId = named.id)
                            if (duplicate != null) {
                                message = "Already added as ${duplicate.displayName}"
                            } else {
                                message = "Addon · ${detected.manifest.displayName}"
                                if (thenSave) onSave(named)
                            }
                        }
                        is DesktopDetectedFormat.ModuleIndex -> {
                            val asIndex = candidate.copy(
                                kind = DesktopSourceKind.CUSTOM_MODULE,
                                baseUrl = detected.url.ifBlank { candidate.baseUrl },
                            )
                            message = "Module index · ${detected.moduleCount} modules"
                            if (thenSave) onSave(asIndex)
                        }
                        is DesktopDetectedFormat.Unsupported -> message = detected.reason
                    }
                },
                onFailure = { message = it.message ?: "Nothing answered at that address" },
            )
        }
    }

    DesktopDialogPanel(
        onDismiss = onDismiss,
        maxWidth = 480,
        popup = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isNew) DesktopStrings["d_add_source", "Add a source"] else config.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DesktopInk,
            )
            DesktopGlassTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    message = null
                },
                label = DesktopStrings["d_addon_url", "Addon URL"],
                placeholder = "https://example.com/addon",
            )
            Text(
                DesktopStrings[
                    "d_an_addon_server",
                    "An addon server — BitChord asks it over plain HTTP and runs no code from it. A compatible module index also works and is stored as one.",
                ],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            DesktopGlassTextField(
                value = label,
                onValueChange = { label = it },
                label = DesktopStrings["d_name_optional", "Name (optional)"],
                placeholder = DesktopStrings["d_my_music_source", "My music source"],
                onSubmit = { identify(thenSave = true) },
            )
            (message ?: status)?.let {
                Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isNew) {
                        DesktopGlassPillButton(
                            text = DesktopStrings["remove", "Remove"],
                            destructive = true,
                            onClick = onRemove,
                        )
                    }
                    DesktopGlassPillButton(
                        text = DesktopStrings["cancel", "Cancel"],
                        onClick = onDismiss,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopGlassPillButton(
                        text = if (checking) DesktopStrings["d_checking", "Checking…"] else DesktopStrings["test", "Test"],
                        enabled = candidate.baseUrl.isNotBlank() && !checking,
                        onClick = {
                            onTest(candidate)
                            identify(thenSave = false)
                        },
                    )
                    DesktopGlassPillButton(
                        text = DesktopStrings["save", "Save"],
                        accent = true,
                        enabled = candidate.baseUrl.isNotBlank() && !checking,
                        onClick = { identify(thenSave = true) },
                    )
                }
            }
        }
    }
}

private fun settingsPageTitle(page: DesktopSettingsPage): String = when (page) {
    DesktopSettingsPage.MAIN -> DesktopStrings["settings", "Settings"]
    DesktopSettingsPage.SOURCES -> DesktopStrings["source", "Source"]
    DesktopSettingsPage.DOWNLOADS -> DesktopStrings["download_channel_name", "Downloads"]
    DesktopSettingsPage.PLAYBACK -> DesktopStrings["playback", "Playback"]
    DesktopSettingsPage.APPEARANCE -> DesktopStrings["appearance", "Appearance"]
    DesktopSettingsPage.PERFORMANCE -> DesktopStrings["performance", "Performance"]
    DesktopSettingsPage.LYRICS -> DesktopStrings["open_lyrics", "Lyrics"]
    DesktopSettingsPage.LOCAL_MUSIC -> DesktopStrings["local_music", "Local Music"]
    DesktopSettingsPage.STORAGE -> DesktopStrings["storage", "Storage"]
    DesktopSettingsPage.BACKUP_AND_RESTORE -> DesktopStrings["d_backup_restore", "Backup and Restore"]
    DesktopSettingsPage.GOOGLE_DRIVE_BACKUP -> DesktopStrings["d_drive_backup_title", "Google Drive backup"]
    DesktopSettingsPage.MISCELLANEOUS -> DesktopStrings["miscellaneous", "Miscellaneous"]
    DesktopSettingsPage.LANGUAGE -> DesktopStrings["language", "Language"]
    DesktopSettingsPage.CHOICE_AUDIO_QUALITY -> DesktopStrings["audio_quality", "Audio quality"]
    DesktopSettingsPage.CHOICE_DOWNLOAD_QUALITY -> DesktopStrings["download_quality", "Download quality"]
    DesktopSettingsPage.CHOICE_BIT_PERFECT -> DesktopStrings["bit_perfect_output", "Bit-perfect output"]
    DesktopSettingsPage.CHOICE_AUTOMIX_PERFORMANCE -> DesktopStrings["automix_performance", "Automix performance"]
    DesktopSettingsPage.CHOICE_OUTPUT_PRECISION -> DesktopStrings["d_output_precision", "Output precision"]
    DesktopSettingsPage.CHOICE_REPEAT_MODE -> DesktopStrings["d_repeat", "Repeat"]
    DesktopSettingsPage.CHOICE_PLAYBACK_SPEED -> DesktopStrings["d_playback_speed", "Playback speed"]
    DesktopSettingsPage.CHOICE_SLEEP_TIMER -> DesktopStrings["sleep_timer", "Sleep timer"]
    else -> DesktopStrings["settings", "Settings"]
}

private fun desktopBitPerfectModeLabel(mode: DesktopBitPerfectMode): String = when (mode) {
    DesktopBitPerfectMode.OFF -> DesktopStrings["d_off", "Off"]
    DesktopBitPerfectMode.EXCLUSIVE -> DesktopStrings["bit_perfect_mode_desktop", "Exclusive"]
}

private fun desktopBitPerfectModeTitle(mode: DesktopBitPerfectMode): String = when (mode) {
    DesktopBitPerfectMode.OFF -> DesktopStrings["d_off", "Off"]
    DesktopBitPerfectMode.EXCLUSIVE -> when {
        DesktopPlatform.isLinux -> DesktopStrings["bit_perfect_mode_linux", "ALSA Direct (Exclusive)"]
        DesktopPlatform.isWindows -> DesktopStrings["bit_perfect_mode_windows", "WASAPI Exclusive"]
        else -> DesktopStrings["bit_perfect_mode_desktop", "Exclusive DAC Output"]
    }
}

private fun desktopBitPerfectModeDescription(mode: DesktopBitPerfectMode, supported: Boolean): String = when {
    mode == DesktopBitPerfectMode.EXCLUSIVE && !supported -> DesktopStrings[
        "bit_perfect_desktop_unavailable",
        "Requires ALSA hardware output on Linux or WASAPI endpoints on Windows.",
    ]
    mode == DesktopBitPerfectMode.OFF -> DesktopStrings[
        "bit_perfect_mode_off_desktop_subtitle",
        "Audio goes through your desktop mixer and BitChord's effects.",
    ]
    DesktopPlatform.isLinux -> DesktopStrings[
        "bit_perfect_mode_linux_subtitle",
        "Requests hardware ownership from PipeWire/PulseAudio over D-Bus and sends untouched PCM directly to ALSA hw:card,dev.",
    ]
    DesktopPlatform.isWindows -> DesktopStrings[
        "bit_perfect_mode_windows_subtitle",
        "Opens your Windows audio endpoint in exclusive WASAPI mode at each track's native sample rate and bit depth.",
    ]
    else -> DesktopStrings[
        "bit_perfect_mode_off_desktop_subtitle",
        "Requires Linux ALSA or Windows WASAPI hardware output.",
    ]
}

private fun desktopBitPerfectSubtitle(
    mode: DesktopBitPerfectMode,
    status: DesktopBitPerfectStatus,
    supported: Boolean,
): String = when {
    mode == DesktopBitPerfectMode.OFF || !supported -> desktopBitPerfectModeDescription(mode, supported)
    status.active && status.dacLimited -> DesktopStrings.format(
        "bit_perfect_status_dac_limited",
        status.deviceName.orEmpty(),
        "%.1f".format(status.sampleRateHz / 1000.0),
        status.outputBits,
        fallback = "%1\$s · %2\$s kHz · %3\$d-bit · DAC max, converted from this track's format",
    )
    status.active && !status.exact -> DesktopStrings.format(
        "bit_perfect_status_inexact",
        status.deviceName.orEmpty(),
        fallback = "%1\$s · not exact: this source isn't lossless PCM",
    )
    status.active -> DesktopStrings.format(
        "bit_perfect_status_active",
        status.deviceName.orEmpty(),
        "%.1f".format(status.sampleRateHz / 1000.0),
        status.outputBits,
        fallback = "%1\$s · %2\$s kHz · %3\$d-bit",
    )
    status.fallbackReason != null -> DesktopStrings.format(
        "bit_perfect_status_unavailable",
        status.fallbackReason,
        fallback = "Not available for this track (%1\$s)",
    )
    else -> DesktopStrings[
        "bit_perfect_status_waiting",
        "Waiting for a lossless track on a supported output",
    ]
}

private fun downloadQualityLabel(value: String): String = when (value.uppercase()) {
    "LOSSLESS" -> DesktopStrings["quality_lossless", "Lossless"]
    "HIGH" -> DesktopStrings["quality_high", "High"]
    else -> DesktopStrings["quality_standard", "Standard"]
}

private fun downloadQualityDetail(value: String): String = when (value.uppercase()) {
    "LOSSLESS" -> "FLAC / ALAC lossless · ~30 MB per track"
    "HIGH" -> "320 kbps AAC / Opus · ~7 MB per track"
    else -> "128–160 kbps · smallest download"
}

private fun AutomixPerformanceMode.label(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT -> DesktopStrings["automix_mode_efficient", "Efficient"]
    AutomixPerformanceMode.BALANCED -> DesktopStrings["automix_mode_balanced", "Balanced"]
    AutomixPerformanceMode.PERFORMANCE -> DesktopStrings["automix_mode_performance", "Performance"]
}

private fun AutomixPerformanceMode.detail(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT ->
        "1 thread · lowest CPU and battery use · analysis may take longer"
    AutomixPerformanceMode.BALANCED ->
        "2 threads · recommended balance of speed and efficiency"
    AutomixPerformanceMode.PERFORMANCE ->
        "4 threads · fastest analysis · higher CPU use"
}

private fun phraseBarsLabel(bars: Int): String = when (bars) {
    8 -> DesktopStrings["automix_phrase_8", "8 bars"]
    16 -> DesktopStrings["automix_phrase_16", "16 bars"]
    else -> DesktopStrings["automix_phrase_auto", "Auto"]
}

private fun phraseBarsDetail(bars: Int): String = when (bars) {
    8 -> DesktopStrings["automix_phrase_8_detail", "Mixes over exactly 8 bars, both edges on the beat grid"]
    16 -> DesktopStrings["automix_phrase_16_detail", "Mixes over exactly 16 bars, both edges on the beat grid"]
    else -> DesktopStrings["automix_phrase_auto_detail", "Chooses each transition's length from the two tracks"]
}

private fun formatTimeText(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
