package com.music.bitchord.desktop

import com.music.bitchord.data.AppleCatalogue
import androidx.compose.foundation.lazy.LazyListState
import com.music.bitchord.ui.components.LocalShelfRowChrome
import com.music.bitchord.ui.components.ShelfRowChrome
import com.music.bitchord.ui.components.ShelfRow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import com.music.bitchord.ui.components.LocalPillSegmentedGlassStyle
import com.music.bitchord.ui.components.LocalPlayingBarsMotion
import com.music.bitchord.ui.components.LocalSearchFieldGlassStyle
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PillSegmentedControl
import com.music.bitchord.ui.components.PillSegmentedGlassStyle
import com.music.bitchord.ui.components.SearchFieldGlassStyle
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.SourceQualityBadge
import com.music.bitchord.ui.components.sourceQualityBadge
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.trackColumnWidth
import com.music.bitchord.ui.replay.ReplayCreditCard
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.ui.player.QueueBulkAction
import com.music.bitchord.ui.LocalPullToRefreshEnabled
import com.music.bitchord.data.AppleArtistPhotos
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.KeyedMix
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.CompactTrackRow
import com.music.bitchord.ui.screens.LibraryGridPage
import com.music.bitchord.ui.screens.LibraryLink
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.MixesScreen
import com.music.bitchord.ui.screens.MoodGenrePlaylistsScreen
import com.music.bitchord.ui.screens.MoodGridPage
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.data.NerdStats
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.QueueTimeline.asQueueEntry
import com.music.bitchord.data.listentogether.partyQueueIndexOf
import com.music.bitchord.data.listentogether.partyUpcomingAfter
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.ui.player.LyricsSidePanel
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.MeshPalette
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.QueueSidePanel
import com.music.bitchord.ui.player.PlayerBack
import com.music.bitchord.ui.player.RepeatModes
import com.music.bitchord.ui.player.rememberMixPulse
import androidx.compose.runtime.SideEffect
import com.music.bitchord.data.model.QueueTier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.ui.unit.DpOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import com.music.bitchord.ui.player.artistCredits
import com.music.bitchord.data.model.creditSegments
import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.CreditSegment
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo
import bitchord.desktopapp.generated.resources.logo_mark
import bitchord.desktopapp.generated.resources.sf_pro_display_bold
import bitchord.desktopapp.generated.resources.sf_pro_display_heavy
import bitchord.desktopapp.generated.resources.sf_pro_display_medium
import bitchord.desktopapp.generated.resources.sf_pro_display_regular
import bitchord.desktopapp.generated.resources.sf_pro_display_semibold
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isMatchMissing
import com.music.bitchord.data.model.isMatchPending
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.model.playingRowMatcher
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.sharedui.resources.Res as SharedRes
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.data.model.withoutRepeatsOf
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.BestPartStart
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.Font as composeFont
import org.jetbrains.compose.resources.painterResource

/**
 * The margin every page keeps from the window's edge.
 *
 * Desktop-only pages share Library's edge inset so switching destinations does not move the page
 * content sideways.
 */
internal val DesktopPageGutter = PAGE_GUTTER

internal val DesktopBackground: Color
    @Composable get() = LocalDesktopPalette.current.background
internal val DesktopSurface: Color
    @Composable get() = LocalDesktopPalette.current.surface
private val DesktopSurfaceRaised: Color
    @Composable get() = LocalDesktopPalette.current.surfaceRaised
internal val DesktopGlass: Color
    @Composable get() = LocalDesktopPalette.current.glass
internal val DesktopGlassStrong: Color
    @Composable get() = LocalDesktopPalette.current.glassStrong

/** The floating bars in the narrow layout. */
private val DesktopBarGlass: Color
    @Composable get() = LocalDesktopPalette.current.barGlass

/** The backdrop the floating bars blur. */
internal val LocalDesktopHaze = staticCompositionLocalOf<HazeState?> { null }

/** How much a page's scrollable should keep clear of the floating bars. */
private val LocalDesktopBottomInset = staticCompositionLocalOf { 0.dp }

/** Specular rim / border hairline separating floating glass surfaces and chrome edges. */
internal val DesktopBarEdge: Color
    @Composable get() = LocalDesktopPalette.current.barEdge
internal val DesktopAccent: Color
    @Composable get() = LocalDesktopPalette.current.accent

/** Deleting, signing out, and anything that failed — the one place a colour survives, as on Android. */
internal val DesktopDestructive = Color(0xFFFF453A)

/** A chip the way Android draws a chosen segment: solid accent with contrasting ink on it, or clear. */
@Composable
internal fun desktopChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = DesktopSecondary,
    selectedContainerColor = DesktopAccent,
    selectedLabelColor = DesktopOnAccent,
)

/** Switch styling adapting cleanly between Dark and Light modes. */
@Composable
internal fun desktopSwitchColors(): SwitchColors {
    val palette = LocalDesktopPalette.current
    return SwitchDefaults.colors(
        checkedTrackColor = palette.accent,
        checkedThumbColor = palette.onAccent,
        checkedBorderColor = Color.Transparent,
        uncheckedTrackColor = palette.ink.copy(alpha = if (palette.isDark) 0.10f else 0.14f),
        uncheckedThumbColor = if (palette.isDark) Color.White.copy(alpha = 0.35f) else Color.White,
        uncheckedBorderColor = Color.Transparent,
    )
}
internal val DesktopSecondary: Color
    @Composable get() = LocalDesktopPalette.current.secondary
internal val DesktopDivider: Color
    @Composable get() = LocalDesktopPalette.current.divider
private const val STATS_SAMPLE_MS = 5_000L

/** How often the playhead is offered to the history tracker. */
private const val HISTORY_SAMPLE_MS = 5_000L

/** How long the search field is left alone before its typeahead is asked for. */
private const val SUGGESTION_DEBOUNCE_MS = 220L

/** How many tracks a station is built with when one is started explicitly. */
/** A search costs more than a completion, so the live rows wait for a longer pause. */
private const val TYPEAHEAD_MEDIA_DEBOUNCE_MS = 400L

/** How many playable rows the dropdown shows, matching Android's own limit. */
private const val TYPEAHEAD_MEDIA_LIMIT = 8

private const val INITIAL_RADIO_TRACKS = 24

/** An artist's top songs, capped so the release shelves are not buried. */
private const val MAX_ARTIST_SONGS = 20
private const val ARTIST_SONGS_PER_COLUMN = 4

/** The artist photograph's height. */
private val ARTIST_BANNER_HEIGHT = 340.dp

/** The library shelf the account's own playlists arrive on, and the one edits are spliced into. */
private const val PLAYLISTS_SHELF = "Playlists"
private const val STATS_MAX_DELTA_MS = 15_000L
private const val SCROBBLE_THRESHOLD_MS = 180_000L

/** Used for tracking restart of the current song when previous button is pressed. */
private const val BACK_RESTARTS_AFTER_MS = 10_000L

/** How long a manual skip takes to blend into the next track when Automix on skip is on. */
private const val SKIP_MIX_ON_SKIP_SECONDS = 15

/** How long the supplier waits for the server to echo an AutoPlay queue addition. */
private const val PARTY_AUTOPLAY_ECHO_TIMEOUT_MS = 5_000L

/** The phone gives one empty station response a delayed second chance. */
private const val AUTOPLAY_EMPTY_REFRESH_DELAY_MS = 2_000L

/** Desktop theme color scheme matching mobile `DarkColors` / `LightColors`. */
@Composable
internal fun desktopColorScheme() = desktopColorSchemeFor(DesktopIsDark)

@Composable
internal fun desktopTypography(): Typography {
    val sfProDisplay = FontFamily(
        composeFont(Res.font.sf_pro_display_regular, FontWeight.W400),
        composeFont(Res.font.sf_pro_display_medium, FontWeight.W500),
        composeFont(Res.font.sf_pro_display_semibold, FontWeight.W600),
        composeFont(Res.font.sf_pro_display_bold, FontWeight.W700),
        composeFont(Res.font.sf_pro_display_heavy, FontWeight.W800),
    )
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = sfProDisplay),
        displaySmall = defaults.displaySmall.copy(fontFamily = sfProDisplay),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = sfProDisplay),
        titleLarge = defaults.titleLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = sfProDisplay),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 16.sp),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 14.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = sfProDisplay),
        labelLarge = defaults.labelLarge.copy(fontFamily = sfProDisplay),
        labelMedium = defaults.labelMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 12.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 11.sp),
    )
}

private enum class DesktopDestination(val id: String, val label: String) {
    LISTEN_NOW("listen-now", "Home"),
    EXPLORE("explore", "Explore"),
    RADIO("radio", "Radio"),
    LIBRARY("library", "Library"),
    SEARCH("search", "Search"),
    HISTORY("history", "History"),
    DOWNLOADS("downloads", "Downloads"),
    LOCAL_MUSIC("local-music", "Local Music"),
    SETTINGS("settings", "Settings"),
}

internal enum class DesktopRepeatMode {
    OFF,
    ALL,
    ONE,
    ;

    fun next(): DesktopRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    fun label(): String = when (this) {
        OFF -> DesktopStrings["d_off", "Off"]
        ALL -> DesktopStrings["repeat_all", "All"]
        ONE -> DesktopStrings["repeat_one", "One"]
    }
}

@Composable
fun BitChordDesktopApp() {
    val scope = rememberCoroutineScope()
    // Filled once the audio engine exists. Local playback helpers use this single hook so every
    // transport and queue gesture reaches Listen Together without duplicating protocol logic.
    val partySyncHolder = remember { arrayOfNulls<DesktopPartySync>(1) }
    val partyState by DesktopListenTogether.state.collectAsState()
    var personalQueueStash by remember { mutableStateOf<DesktopQueue?>(null) }
    var personalPositionStash by remember { mutableStateOf(0L) }
    var personalPlayingStash by remember { mutableStateOf(false) }
    val persistence = remember { DesktopPersistence() }
    var availableUpdate by remember { mutableStateOf<DesktopUpdateChecker.UpdateInfo?>(null) }
    LaunchedEffect(Unit) { availableUpdate = DesktopUpdateChecker.check() }
    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text(DesktopStrings["d_update_available", "Update available"]) },
            text = {
                Text(
                    "BitChord ${update.version} is out — you have ${DesktopUpdateChecker.currentVersion}." +
                        (update.notes?.takeIf { it.isNotBlank() }?.let { "\n\n${it.take(600)}" } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DesktopExternalLinks.open(update.downloadUrl ?: update.releaseUrl)
                    availableUpdate = null
                }) { Text(DesktopStrings["d_download", "Download"]) }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) {
                    Text(DesktopStrings["d_later", "Later"])
                }
            },
        )
    }
    var destination by remember { mutableStateOf(DesktopDestination.LISTEN_NOW) }
    var query by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var searchRows by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // What has been searched before, and what YouTube thinks is being typed.
    val searchHistory by DesktopSearchHistory.recent.collectAsState()
    // Whether anything has been searched for since the field was last emptied: until then the
    // page shows recent searches rather than a result set.
    var searchCommitted by remember { mutableStateOf(false) }
    var searchScrollReset by remember { mutableStateOf(0) }
    var searchFocusRequested by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Playable rows for the half-typed query, shown under the text completions.
    var searchTypeahead by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // False while the query is being set *by* the app.
    var searchTyping by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var homeState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    // The home feed is paged: `FEmusic_home` answers with a couple of shelves and a token, and
    // everything else arrives by following it.
    var homeContinuation by remember { mutableStateOf<String?>(null) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    // Recently Played has its own request and its own skeleton at the head of the page, as on the
    // phone; it is only asked for with an account to have played on.
    var homeRecentsLoading by remember { mutableStateOf(false) }
    // Held out here so a page keeps its place when another is visited and it is come back to.
    val homeListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val moodGenreListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    /** A Library shelf's "Show all", open as a grid in place of the page. */
    var libraryShowAll by remember { mutableStateOf<HomeShelf?>(null) }
    /** What the Spotify grid is waiting on, or why it is empty; null once its playlists are in. */
    var spotifyLibraryStatus by remember { mutableStateOf<String?>(null) }
    var spotifyImportOpen by remember { mutableStateOf(false) }
    /** Where the one back button in the top bar goes: every place visited, oldest first. */
    val navHistory = remember { mutableStateListOf<DesktopNavEntry>() }
    /** Set while [goBack] puts a place back, so that move is not itself recorded as a visit. */
    val navRestoring = remember { booleanArrayOf(false) }
    /** Opens a song's menu at the pointer; set by the pages' menu host once it is in place. */
    var pageMenu by remember { mutableStateOf<(Song) -> Unit>({}) }
    val homeSeenTitles = remember { HashSet<String>() }
    var homeGeneration by remember { mutableStateOf(0) }
    var homeIdentity by remember { mutableStateOf<String?>(null) }
    var exploreState by remember { mutableStateOf<UiState<List<MoodGenreSection>>>(UiState.Loading) }
    var selectedMoodGenre by remember { mutableStateOf<MoodGenre?>(null) }
    var homeMoodsAllOpen by remember { mutableStateOf(false) }
    val moodsGridState = rememberLazyGridState()
    var mixesState by remember { mutableStateOf<UiState<List<KeyedMix>>>(UiState.Loading) }
    var mixesReloads by remember { mutableStateOf(0) }
    var mixesIdentity by remember { mutableStateOf<String?>(null) }
    val mixesListState = rememberLazyListState()
    var topPicks by remember { mutableStateOf<List<TopPick>?>(null) }
    var topPicksLoading by remember { mutableStateOf(false) }
    var topPicksSlotShown by remember { mutableStateOf<String?>(null) }
    var topPicksIdentity by remember { mutableStateOf<String?>(null) }
    var topPicksJob by remember { mutableStateOf<Job?>(null) }
    // Bumped by a retry so the loaders below re-run without the state they are keyed on having to
    // change to something and back again.
    var exploreReloads by remember { mutableStateOf(0) }
    var moodGenreReloads by remember { mutableStateOf(0) }
    var moodGenreShelves by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    // The live queue: what is playing, what played before it, what is next.
    var liveQueue by remember {
        mutableStateOf(
            DesktopQueue.restored(
                persistence.queue(),
                persistence.string("queue_index", "0").toIntOrNull() ?: 0,
            ),
        )
    }
    // The order the queue was in before shuffle rearranged it, so the toggle can be undone.
    var preShuffleOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    // One party AutoPlay top-up at a time; see topUpPartyAutoplay.
    val partyAutoplayLock = remember { Mutex() }
    val queue = liveQueue.songs
    // What was played on this computer. The account's own history replaces it
    // while signed in — see [remoteHistory] — because that is what Android's
    // History screen is and what makes a phone and a desktop agree.
    var history by remember { mutableStateOf(persistence.history()) }
    var remoteHistory by remember { mutableStateOf<List<Song>>(emptyList()) }
    var likedIds by remember { mutableStateOf(persistence.likedIds()) }
    // In-flight tail of the Liked Music chain; replaced whenever the library is fetched again.
    var likedSyncJob by remember { mutableStateOf<Job?>(null) }
    var dislikedIds by remember { mutableStateOf(persistence.dislikedIds()) }
    val overlays = remember { DesktopOverlays() }
    var downloads by remember { mutableStateOf(persistence.downloads()) }
    var localSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val downloadQueue by DesktopDownloadQueue.active.collectAsState()
    val downloadInProgress = downloadQueue.keys
    var playlists by remember { mutableStateOf(persistence.playlists()) }
    var statsRevision by remember { mutableStateOf(0L) }
    var replayPeriod by remember { mutableStateOf(DesktopReplayPeriod.ALL_TIME) }
    val replayHolder = remember { DesktopAccounts.active()?.name.orEmpty() }
    var replaySummary by remember { mutableStateOf(DesktopListeningStats.summary()) }
    var replayGenres by remember { mutableStateOf(persistence.boolean("replay_genres", true)) }
    var statsCountedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var playlistTargets by remember { mutableStateOf<List<Song>>(emptyList()) }
    var autoDownloadLiked by remember { mutableStateOf(persistence.autoDownloadLiked()) }
    // One busy flag and one message for every playlist edit: only one of them can be in flight,
    // because each is driven by a dialog that is modal.
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    // The album and artist pages the playing track belongs to, looked up while
    // the player is open. Whatever started a track knew its title and artwork
    // but rarely its ids — a home-feed tile carries neither — so without this
    // the credit under the title is not a link at all. Android does the same
    // thing, and for the same reason; see its `links` in MainActivity.
    var trackLinks by remember { mutableStateOf<Song?>(null) }
    // Settings is a modal over whatever page is open, the way Music puts its own preferences in a
    // sheet rather than a navigation destination.
    var autoplay by remember { mutableStateOf(persistence.boolean("autoplay", true)) }
    var dontRepeatSuggestions by remember {
        mutableStateOf(persistence.boolean(KEY_DONT_REPEAT_SUGGESTIONS, false))
    }
    /** Everything AutoPlay has played or offered this run — see [KEY_DONT_REPEAT_SUGGESTIONS]. */
    val sessionSongHistory = remember { mutableListOf<Song>() }
    var filterNonMusicAudio by remember {
        mutableStateOf(persistence.boolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, false))
    }
    var localMusicRevision by remember { mutableStateOf(0) }
    // The track the queued station was built around, so a top-up is asked for once per song rather
    // than once per recomposition.
    var autoplaySeed by remember { mutableStateOf<String?>(null) }
    var autoplayJob by remember { mutableStateOf<Job?>(null) }
    var automix by remember { mutableStateOf(persistence.boolean("automix", false)) }
    // A manual skip blends into the already-prepared next track over ~15s instead of cutting.
    var automixOnSkip by remember { mutableStateOf(persistence.boolean("automix_on_skip", false)) }
    // Apple-style Automix phrase length in bars: 0 = adaptive (default), 8 or 16 = fixed.
    var automixPhraseBars by remember {
        mutableStateOf(persistence.string("automix_phrase_bars", "0").toIntOrNull()?.let { if (it == 8 || it == 16) it else 0 } ?: 0)
    }
    var automixPerformance by remember {
        mutableStateOf(
            runCatching { AutomixPerformanceMode.valueOf(persistence.string("automix_performance", "BALANCED")) }
                .getOrDefault(AutomixPerformanceMode.BALANCED),
        )
    }
    var autoMixArtTransition by remember { mutableStateOf(persistence.boolean("automix_art_transition", false)) }
    var autoMixMiniArtTransition by remember { mutableStateOf(persistence.boolean("automix_mini_art_transition", false)) }
    var showBestPart by remember { mutableStateOf(persistence.boolean("show_best_part", true)) }
    var showTotalDuration by remember { mutableStateOf(persistence.boolean("show_total_duration", false)) }
    var shuffle by remember { mutableStateOf(persistence.boolean("shuffle", false)) }
    var repeatMode by remember {
        mutableStateOf(
            runCatching { DesktopRepeatMode.valueOf(persistence.string("repeat_mode", "OFF")) }
                .getOrDefault(DesktopRepeatMode.OFF),
        )
    }
    var playbackSpeed by remember { mutableStateOf(persistence.string("playback_speed", "1.0").toFloatOrNull() ?: 1.0f) }
    var volume by remember { mutableStateOf(persistence.string("volume", "1.0").toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 1.0f) }
    var crossfadeSeconds by remember { mutableStateOf(persistence.string("crossfade_seconds", "0").toIntOrNull()?.coerceIn(0, 12) ?: 0) }
    var downloadQuality by remember { mutableStateOf(persistence.string("download_quality", "LOSSLESS")) }
    var trayIconEnabled by remember { mutableStateOf(persistence.boolean("tray_icon", true)) }
    var closeToTray by remember { mutableStateOf(persistence.boolean("close_to_tray", true)) }
    // The three the FFmpeg engine unlocked: none of them could exist while JavaFX owned the decode,
    // because none of them can be done without the samples themselves.
    var spatialAudio by remember { mutableStateOf(persistence.boolean("spatial_audio", false)) }
    var dolbyAtmos by remember { mutableStateOf(persistence.boolean("dolby_atmos", true)) }
    var skipSilence by remember { mutableStateOf(persistence.boolean("skip_silence", false)) }
    var smartVersionAlignment by remember { mutableStateOf(persistence.boolean("smart_version_alignment", true)) }
    var outputPrecision by remember { mutableStateOf(persistence.string("output_precision", "PCM_16")) }
    // The blob backdrop the mesh replaced, kept as an opt-out.
    var legacyMeshGradient by remember { mutableStateOf(persistence.boolean("legacy_mesh_gradient", false)) }
    var animatedCanvas by remember { mutableStateOf(persistence.boolean("animated_canvas", true)) }
    var animatedArtistBanner by remember { mutableStateOf(persistence.boolean("animated_artist_banner", true)) }
    var spotifyCanvasCookie by remember { mutableStateOf(DesktopSpotifyToken.cookie()) }
    // Held here rather than by the Integrations page, so leaving the page does not close the
    // sign-in window.
    var spotifySignInJob by remember { mutableStateOf<Job?>(null) }
    var spotifySignInError by remember { mutableStateOf<String?>(null) }
    var showNerdStats by remember { mutableStateOf(persistence.boolean("show_nerd_stats", false)) }
    var syncedLyrics by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, true))
    }
    var lyricsBlur by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_LYRICS_BLUR, true))
    }
    var prioritizeSyllables by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false))
    }
    var lyricsOrder by remember { mutableStateOf(persistence.lyricsSourceOrder()) }
    var lyricsOn by remember { mutableStateOf(persistence.lyricsEnabledSources()) }
    var fullBleedArtwork by remember { mutableStateOf(persistence.boolean("full_bleed_artwork", false)) }
    var accounts by remember { mutableStateOf(DesktopAccounts.accounts()) }
    var activeAccountId by remember { mutableStateOf(DesktopAccounts.activeAccountId()) }
    var activeProfileId by remember { mutableStateOf(DesktopAccounts.activeProfileId()) }
    var signInBusy by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var webSignInJob by remember { mutableStateOf<Job?>(null) }
    val activeAccount = accounts.firstOrNull { it.accountId == activeAccountId } ?: accounts.firstOrNull()
    // The account's own library, fetched once a session is in force.
    var libraryState by remember { mutableStateOf<UiState<LibraryPage>>(UiState.Loading) }
    // Whether YouTube is actually accepting the session, which is not the same as having an account
    // saved.
    var youtubeSignedIn by remember { mutableStateOf(false) }
    // Set by an edit that the library feed has not caught up with yet.
    var libraryStale by remember { mutableStateOf(false) }
    // Bumped when the session changes underneath, so the loader below re-runs without anything it
    // is keyed on having to change to something and back.
    var sessionRevision by remember { mutableStateOf(0) }
    // Read off the library rather than fetched again: the Playlists shelf is the same feed the
    // picker wants, minus the entries no edit can be aimed at.
    val accountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.let(DesktopSearchClient::userPlaylists)
            .orEmpty()
    }
    // The add-to-playlist dialog intentionally keeps only playlists owned by the account. The
    // sidebar is a library browser instead, so it shows the complete paged shelf: owned, saved,
    // and YouTube's built-in playlists such as Liked Music.
    val sidebarAccountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.filter { it.browseId != null }
            .orEmpty()
    }

    fun reloadLibrary() {
        libraryState = UiState.Loading
        sessionRevision++
    }

    /** Edits the Playlists shelf in place, without re-fetching it. */
    fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (libraryState as? UiState.Success)?.data ?: return
        val index = page.shelves.indexOfFirst { it.title == PLAYLISTS_SHELF }
        val shelves = if (index >= 0) {
            page.shelves.toMutableList().apply {
                this[index] = this[index].copy(items = edit(this[index].items))
            }
        } else {
            // A fresh account has no Playlists shelf at all, and it is exactly the account most
            // likely to be making its first one.
            val items = edit(emptyList())
            if (items.isEmpty()) return
            listOf(HomeShelf(PLAYLISTS_SHELF, items)) + page.shelves
        }
        libraryState = UiState.Success(page.copy(shelves = shelves.toList()))
    }

    // Whoever was signed in last is signed in again, before anything asks YouTube for something
    // that depends on it.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { DesktopAccounts.activate() }
        sessionRevision++
    }
    var audioQuality by remember { mutableStateOf(persistence.audioQuality()) }
    var sourceConfigs by remember { mutableStateOf(persistence.sourceConfigs()) }
    var sourceStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val youtubeSourceEnabled = sourceConfigs.any {
        it.kind == DesktopSourceKind.YOUTUBE && it.enabled
    }
    val sleepTimerMinutes by DesktopSleepTimer.minutes.collectAsState()
    val sleepAfterTrack by DesktopSleepTimer.afterTrack.collectAsState()
    var sleepTimerTick by remember { mutableStateOf(0L) }
    LaunchedEffect(sleepTimerMinutes) {
        while (sleepTimerMinutes != null && currentCoroutineContext().isActive) {
            delay(1_000L)
            sleepTimerTick++
        }
    }
    val sleepRemainingMs = remember(sleepTimerMinutes, sleepTimerTick) {
        DesktopSleepTimer.remainingMs()
    }
    var openedCollection by remember { mutableStateOf<DesktopCollection?>(null) }
    var collectionLoadingMore by remember { mutableStateOf(false) }
    var collectionError by remember { mutableStateOf<String?>(null) }
    var collectionReloads by remember { mutableStateOf(0) }
    // The artist page is its own destination rather than a collection with a different header.
    var openedArtist by remember { mutableStateOf<DesktopArtistTarget?>(null) }
    var artistState by remember { mutableStateOf<UiState<ArtistPage>>(UiState.Loading) }
    var artistReloads by remember { mutableStateOf(0) }

    fun canonicalSong(song: Song): Song {
        val homeSong = (homeState as? UiState.Success)?.data.orEmpty()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.videoId == song.videoId }
            .map(ShelfItem::toSong)
            .firstOrNull()
        return if (homeSong != null && song.artist.isUnknownArtist()) {
            song.copy(
                artist = homeSong.artist,
                durationText = song.durationText ?: homeSong.durationText,
                thumbnailUrl = song.thumbnailUrl ?: homeSong.thumbnailUrl,
            )
        } else {
            song
        }
    }

    lateinit var startSong: (Song, Boolean) -> Unit
    lateinit var playbackEngine: DesktopPlaybackEngine

    fun saveQueue() {
        // A party queue is temporary. The phone keeps the listener's own queue aside and so does
        // desktop; never let a shared running order overwrite the queue restored after leaving or
        // after a process restart.
        if (DesktopListenTogether.state.value.inParty) return
        persistence.saveQueue(liveQueue.songs)
        persistence.saveString("queue_index", liveQueue.index.toString())
    }

    /** Opens whatever the queue is currently pointing at. */
    fun playCurrent(startPlaying: Boolean = true) {
        val song = liveQueue.current ?: return
        selectedSong = song
        history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
        saveQueue()
        persistence.saveHistory(history)
        playbackEngine.load(song, startPlaying)
        partySyncHolder[0]?.onLocalIntent(song.videoId)
        scope.launch { DesktopScrobbling.updateNowPlaying(song) }
    }

    fun partyTrackChangeBlocked(): Boolean {
        if (!DesktopListenTogether.state.value.controlsLocked) return false
        DesktopPlayerHost.showMessage("Only the party host can change playback")
        return true
    }

    /**
     * The phone's party queue for a track picked here: that track, then what the party's members
     * queued by hand. Never the album around it, and never the last track's AutoPlay — the party
     * shares one running order, and the phone builds it exactly this way.
     */
    fun partyPlaybackQueue(tapped: Song): List<Song> {
        val party = DesktopListenTogether.state.value
        val upcoming = partyUpcomingAfter(party.queue, party.playback, party.playback.track?.videoId)
        return QueueTimeline.buildPartyPlaybackQueue(
            tapped,
            tapped.queueSource(),
            DesktopQueue.adopt(liveQueue.upcoming, upcoming.map { it.toDesktopSong() }),
        )
    }

    /** A song played on its own — from a search row, a shelf card, history. */
    fun playSong(song: Song, startPlaying: Boolean = true, source: DesktopQueueSource? = null) {
        if (partyTrackChangeBlocked()) return
        val tapped = canonicalSong(song).withSource(source)
        val songs = if (DesktopListenTogether.state.value.inParty) {
            partyPlaybackQueue(tapped)
        } else {
            // The phone's one-off queue: the song, then whatever the listener had queued by hand.
            QueueTimeline.buildOneOffQueue(liveQueue.songs, liveQueue.index, tapped, tapped.queueSource())
        }
        liveQueue = DesktopQueue(songs, index = 0)
        preShuffleOrder = emptyList()
        playCurrent(startPlaying)
    }

    /** Opens the Spotify sign-in window for an `sp_dc`, or closes the one already open. */
    fun signInToSpotify() {
        spotifySignInJob?.let {
            it.cancel()
            return
        }
        spotifySignInError = null
        spotifySignInJob = scope.launch {
            try {
                val cookie = DesktopWebSignIn.captureSpotify()
                DesktopSpotifyToken.setCookie(cookie)
                spotifyCanvasCookie = cookie
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                DesktopTrackLog.log("sign-in: Spotify failed: ${failure.message}")
                spotifySignInError = failure.message ?: "Could not complete the Spotify sign-in."
            } finally {
                spotifySignInJob = null
            }
        }
    }

    /** Takes a captured cookie all the way to a saved account. */
    suspend fun signIn(cookie: String, label: String, sourceProfile: String? = null) {
        signInError = null
        signInBusy = label
        try {
            val scope = withContext(Dispatchers.IO) { DesktopYouTubeSession.adoptSessionScope(cookie) }
            if (scope == null) {
                signInError = "$label is not signed in to YouTube Music."
                return
            }
            DesktopYouTubeAuth.adopt(scope)
            // Asked with the session in force, so they describe the account that was just captured
            // rather than nobody in particular.
            val details = DesktopSearchClient.accountMenu().getOrNull()?.let(DesktopAccountParser::account)
            val channels = DesktopSearchClient.accountsList().getOrNull()
                ?.let(DesktopAccountParser::channels)
                .orEmpty()
            // Off the UI thread: saving reaches the platform keyring over D-Bus, and a blocking
            // call to another process is not something to do on the thread that is drawing.
            withContext(Dispatchers.IO) { DesktopAccounts.save(cookie, scope, details, channels, sourceProfile) }
            accounts = withContext(Dispatchers.IO) { DesktopAccounts.accounts() }
            activeAccountId = DesktopAccounts.activeAccountId()
            activeProfileId = DesktopAccounts.activeProfileId()
            overlays.signIn = false
            overlays.accounts = false
        } finally {
            signInBusy = null
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, source: DesktopQueueSource? = null) {
        // A Spotify playlist's rows still being matched to YouTube Music, or with no match, have
        // nothing to play: they stay out of the queue, and the pick moves to the next playable row.
        val resolved = songs.withIndex().filterNot { it.value.isUnresolvedSpotify }
        if (resolved.isEmpty()) return
        if (partyTrackChangeBlocked()) return
        val playable = resolved.map { canonicalSong(it.value).withSource(source) }
        // Past the end still means the last row, as the clamp here always did.
        val at = resolved.indexOfFirst { it.index >= startIndex }.takeIf { it >= 0 } ?: resolved.lastIndex
        if (DesktopListenTogether.state.value.inParty) {
            liveQueue = DesktopQueue(partyPlaybackQueue(playable[at]), index = 0)
            preShuffleOrder = emptyList()
            playCurrent()
            return
        }
        // The phone's context queue: the album up to the pick, the pick, what the listener had
        // queued by hand, then the rest of the album.
        val built = QueueTimeline.buildContextQueue(
            currentTimeline = liveQueue.songs,
            currentIndex = liveQueue.index,
            newContextSongs = playable,
            selectedIndex = at,
            contextSource = playable[at].queueSource(),
        )
        liveQueue = if (shuffle) {
            DesktopQueue.shuffledStartingAt(built.timeline, built.startIndex)
        } else {
            // Keep the complete queue so Previous can navigate to the tracks before the selected one.
            DesktopQueue.startingAt(built.timeline, built.startIndex)
        }
        preShuffleOrder = if (shuffle) built.timeline.map(DesktopQueue::orderKey) else emptyList()
        playCurrent()
    }

    /** Moves within the queue, dropping whatever a forward jump passed over. */
    fun playQueueIndex(target: Int) {
        if (target !in liveQueue.songs.indices) return
        if (partyTrackChangeBlocked()) return
        liveQueue = liveQueue.jumpTo(target)
        playCurrent()
    }

    fun cycleSleepTimer() {
        when {
            sleepAfterTrack -> DesktopSleepTimer.cancel()
            sleepTimerMinutes == null -> DesktopSleepTimer.start(DesktopSleepTimer.presets.first())
            sleepTimerMinutes == DesktopSleepTimer.presets.last() -> DesktopSleepTimer.startAfterTrack()
            else -> {
                val current = DesktopSleepTimer.presets.indexOf(sleepTimerMinutes)
                DesktopSleepTimer.start(DesktopSleepTimer.presets.getOrElse(current + 1) { DesktopSleepTimer.presets.last() })
            }
        }
    }
    startSong = ::playSong

    fun downloadSong(song: Song) {
        // A second tap on something already queued or running calls it off.
        if (song.videoId in downloadInProgress) {
            DesktopDownloadQueue.cancel(song.videoId)
            return
        }
        if (song.videoId in downloads.map(Song::videoId) || song.localPath != null) return
        DesktopDownloadQueue.enqueue(song)
    }

    /** Deletes the file a download saved and forgets the record. */
    fun removeDownload(song: Song) {
        scope.launch {
            withContext(Dispatchers.IO) { DesktopDownloadManager.delete(song) }
            downloads = downloads.filterNot { it.videoId == song.videoId }
            persistence.saveDownloads(downloads)
        }
    }

    /** Everything on a page, in the order it is listed. */
    fun downloadAll(songs: List<Song>) {
        val have = downloads.map(Song::videoId).toSet()
        DesktopDownloadQueue.enqueueAll(
            songs.filterNot { it.videoId in have || it.localPath != null || it.isUnresolvedSpotify },
        )
    }

    /** Applies a rating locally and carries it to the account. */
    fun rate(song: Song, status: LikeStatus) {
        val wasLiked = song.videoId in likedIds
        likedIds = if (status == LikeStatus.LIKE) likedIds + song.videoId else likedIds - song.videoId
        dislikedIds = if (status == LikeStatus.DISLIKE) {
            dislikedIds + song.videoId
        } else {
            dislikedIds - song.videoId
        }
        persistence.saveLikedIds(likedIds)
        persistence.saveDislikedIds(dislikedIds)
        if (DesktopSearchClient.isVideoId(song.videoId)) {
            scope.launch {
                DesktopSearchClient.rate(song.videoId, status)
                    .onFailure { DesktopTrackLog.log("youtube: rating ${song.videoId} failed: ${it.message}") }
            }
        }
        if (status == LikeStatus.LIKE && !wasLiked && autoDownloadLiked && song.localPath == null) {
            downloadSong(song)
        }
    }

    fun toggleLike(song: Song) =
        rate(song, if (song.videoId in likedIds) LikeStatus.INDIFFERENT else LikeStatus.LIKE)

    fun toggleDislike(song: Song) =
        rate(song, if (song.videoId in dislikedIds) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE)

    /** The origin the live queue is carrying, for anything appended to it. */
    fun currentQueueSource(): DesktopQueueSource? = liveQueue.current?.playbackSource?.let {
        DesktopQueueSource(
            it,
            liveQueue.current?.playbackSourceType ?: PlaybackSourceType.QUEUE,
            liveQueue.current?.playbackSourceId,
        )
    }

    /**
     * Queues a track by hand, into the listener's own section — "Play next" at its head, "Add to
     * queue" at its end, above the album and AutoPlay either way. In a party, held to the same
     * twenty-five upcoming songs the phone and the server allow.
     */
    fun enqueue(song: Song, next: Boolean) {
        val party = DesktopListenTogether.state.value
        if (party.controlsLocked) return
        if (party.inParty && liveQueue.upcoming.size >= DesktopPartySync.MAX_PARTY_UPCOMING_QUEUE) {
            DesktopPlayerHost.showMessage(
                "Queue is full (maximum ${DesktopPartySync.MAX_PARTY_UPCOMING_QUEUE} songs in party)",
            )
            return
        }
        val queued = canonicalSong(song).copy(radioName = liveQueue.current?.radioName)
            .withSource(currentQueueSource())
        liveQueue = liveQueue.enqueue(queued, playNext = next)
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    /** Slots a track in right after the one playing. */
    fun playNext(song: Song) = enqueue(song, next = true)

    /** Puts a track at the end of what the listener queued — not the end of the queue. */
    fun addToQueue(song: Song) = enqueue(song, next = false)

    /** Starts the station YouTube Music builds around one track. */
    fun startRadio(song: Song) {
        if (partyTrackChangeBlocked()) return
        val seed = canonicalSong(song).copy(
            radioName = song.title,
            playbackSource = song.title,
            playbackSourceType = PlaybackSourceType.SHARED_LINK,
            playbackSourceId = song.videoId,
        )
        scope.launch {
            // AutoPlay's section behind the seed, as the phone queues a station — which is also
            // what lets a party share it: a context tail is never published.
            val related = DesktopAutoplay.tracksFor(listOf(seed), seed, INITIAL_RADIO_TRACKS)
                .getOrNull()
                .orEmpty()
            if (related.isEmpty()) {
                DesktopTrackLog.log("radio: nothing to build a station on for '${song.title}'")
                DesktopPlayerHost.showMessage(DesktopStrings["couldnt_load_tracks", "Could not load these tracks"])
                return@launch
            }
            if (partyTrackChangeBlocked()) return@launch
            if (liveQueue.current?.videoId == seed.videoId) {
                val updatedCurrent = liveQueue.current!!.copy(radioName = song.title)
                liveQueue = DesktopQueue(listOf(updatedCurrent) + related, index = 0)
                saveQueue()
                partySyncHolder[0]?.onLocalIntent()
            } else {
                val station = listOf(seed.asQueueEntry(QueueTier.CONTEXT)) + related
                liveQueue = if (shuffle) DesktopQueue.shuffledStartingAt(station, 0) else DesktopQueue(station, 0)
                preShuffleOrder = if (shuffle) station.map(DesktopQueue::orderKey) else emptyList()
                playCurrent()
            }
            DesktopPlayerHost.showMessage(
                DesktopStrings.format("radio_started", song.title, fallback = "%1\$s radio started"),
            )
            DesktopTrackLog.log("radio: started a station on '${song.title}' with ${related.size} tracks")
        }
    }

    /**
     * Opens an album or playlist in place of whatever page was open. The page it replaces is kept
     * in the history, not underneath: an artist page left standing under an album would be drawn
     * over it, which is how an album opened from an artist once went nowhere.
     */
    fun showCollection(collection: DesktopCollection) {
        openedArtist = null
        homeMoodsAllOpen = false
        overlays.replay = false
        overlays.settingsPage = null
        collectionError = null
        openedCollection = collection
    }

    /**
     * Opens an album or playlist by id straight away, with a loader where the tracks go, the way an
     * artist's page opens — rather than leaving the click with nothing to show until YouTube answers.
     * [fallback] is what the card that was clicked already knew.
     */
    fun openCollection(browseId: String, fallback: BrowseItem? = null) {
        val current = openedCollection
        if (current?.browseId == browseId && !current.loading) {
            showCollection(current)
            return
        }
        showCollection(
            DesktopCollection(
                browseId = browseId,
                title = fallback?.title.orEmpty(),
                subtitle = fallback?.subtitle.orEmpty(),
                thumbnailUrl = fallback?.thumbnailUrl,
                type = fallback?.type?.takeIf { it != BrowseType.OTHER } ?: browseTypeOf(browseId),
                songs = emptyList(),
                loading = true,
            ),
        )
        collectionReloads++
    }

    // Keyed on the id, so a second page opened before the first answered cancels the first rather
    // than racing it; and a loading page restored by Back fetches again.
    LaunchedEffect(openedCollection?.browseId, openedCollection?.loading, collectionReloads) {
        val pending = openedCollection
            ?.takeIf { it.loading && !it.browseId.startsWith(SPOTIFY_PAGE_PREFIX) }
            ?: return@LaunchedEffect
        collectionError = null
        DesktopSearchClient.browse(
            browseId = pending.browseId,
            fallback = BrowseItem(
                browseId = pending.browseId,
                title = pending.title,
                subtitle = pending.subtitle,
                thumbnailUrl = pending.thumbnailUrl,
                type = pending.type,
            ),
        ).fold(
            onSuccess = { loaded ->
                if (openedCollection?.browseId == pending.browseId) openedCollection = loaded
            },
            onFailure = {
                if (pending.browseId == "VLLM") {
                    val allKnown = (downloads + history + localSongs).distinctBy { it.videoId }
                    val liked = allKnown.filter { it.videoId in likedIds }
                    if (liked.isNotEmpty()) {
                        openedCollection = pending.copy(
                            songs = liked,
                            loading = false,
                            subtitle = "${liked.size} songs",
                        )
                        return@fold
                    }
                }
                collectionError = it.message ?: "Could not open ${pending.title.ifBlank { "this page" }}"
            },
        )
    }

    // A Spotify playlist: its tracks, then each one's YouTube Music match. Keyed like the browse
    // above, so opening another page cancels it, and Back to a page left half matched (or Retry
    // on one that failed) reads it again.
    LaunchedEffect(openedCollection?.browseId, collectionReloads) {
        val page = openedCollection?.takeIf { it.browseId.startsWith(SPOTIFY_PAGE_PREFIX) }
            ?: return@LaunchedEffect
        if (!page.loading && page.songs.none { it.isMatchPending }) return@LaunchedEffect
        collectionError = null
        fun update(change: (DesktopCollection) -> DesktopCollection) {
            openedCollection?.takeIf { it.browseId == page.browseId }?.let { openedCollection = change(it) }
        }
        runCatching { loadSpotifyPlaylist(page.browseId.removePrefix(SPOTIFY_PAGE_PREFIX), ::update) }
            .onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                DesktopTrackLog.log("spotify: playlist ${page.title} failed: ${failure.message}")
                collectionError = failure.message ?: "Could not open ${page.title}"
                // The error page stands in for the list only while there is no list.
                update { if (it.songs.isEmpty()) it.copy(loading = true) else it }
            }
    }

    fun openAlbum(browseId: String) = openCollection(browseId)

    fun openAlbumByName(name: String) {
        scope.launch {
            val match = DesktopSearchClient.search(name, SearchFilter.ALBUMS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.browseId.isNotBlank() }
                ?: return@launch
            openAlbum(match.browseId)
        }
    }

    /** Opens Liked Music playlist (VLLM), or the local liked collection when offline/not signed in. */
    fun openLikedMusic() {
        if (youtubeSignedIn) {
            openCollection(
                browseId = "VLLM",
                fallback = BrowseItem(
                    browseId = "VLLM",
                    title = DesktopStrings["auto_liked", "Liked songs"],
                    subtitle = DesktopStrings["auto_playlist", "Auto playlist"],
                    thumbnailUrl = null,
                    type = BrowseType.PLAYLIST,
                ),
            )
        } else {
            val allKnown = (downloads + history + localSongs).distinctBy { it.videoId }
            val liked = allKnown.filter { it.videoId in likedIds }
            showCollection(
                DesktopCollection(
                    browseId = "VLLM",
                    title = DesktopStrings["auto_liked", "Liked songs"],
                    subtitle = "${liked.size} songs",
                    thumbnailUrl = null,
                    type = BrowseType.PLAYLIST,
                    songs = liked,
                ),
            )
        }
    }

    /** Copies the track's YouTube Music link. */
    fun shareSong(song: Song) {
        DesktopExternalLinks.copy("https://music.youtube.com/watch?v=${song.videoId}")
        DesktopTrackLog.log("copied a link to '${song.title}'")
        DesktopPlayerHost.messages.value = DesktopStrings["link_copied", "Link copied to clipboard"]
    }

    /**
     * The link YouTube Music's own overflow shares for a release, built from the browse id rather
     * than fetched — nothing about it depends on the tracks or the account. Albums and playlists
     * only: an artist's is a channel link, not a release.
     */
    fun shareCollection(collection: DesktopCollection) {
        val id = collection.browseId
        val url = when (collection.type) {
            BrowseType.PLAYLIST -> "https://music.youtube.com/playlist?list=${id.removePrefix("VL")}"
            BrowseType.ALBUM -> "https://music.youtube.com/browse/$id"
            else -> return
        }
        DesktopExternalLinks.copy(url)
        DesktopTrackLog.log("copied a link to '${collection.title}'")
        DesktopPlayerHost.messages.value = DesktopStrings["link_copied", "Link copied to clipboard"]
    }

    /** Keeps a station queued ahead of whatever is playing. */

    /**
     * How many AutoPlay tracks the party already has waiting after [videoId], or null outside a
     * party or while its queue does not hold that track yet.
     */
    fun partyAutoplayWaiting(party: DesktopListenTogether.State, videoId: String): Int? {
        if (!party.inParty) return null
        if (partyQueueIndexOf(party.queue, party.playback, videoId) < 0) return null
        return partyUpcomingAfter(party.queue, party.playback, videoId).count { it.fromAutoplay }
    }

    /**
     * Sends [suggestions] to the party and waits for them to come back; the phone's
     * `topUpPartyAutoplay`.
     *
     * Serialised, and the send-and-wait not cancellable, because a track change cancels the load in
     * flight and starts another: the second used to read the party before the first batch had
     * echoed, find nothing waiting, and send the same station again — every suggestion twice, on
     * every device. Holding the lock until the echo means the next top-up reads a queue that
     * already has these in it, and filters against it.
     */
    suspend fun topUpPartyAutoplay(currentId: String, suggestions: List<Song>): Boolean =
        partyAutoplayLock.withLock {
            withContext(NonCancellable) {
                val party = DesktopListenTogether.state.value
                val waiting = partyUpcomingAfter(party.queue, party.playback, currentId)
                val waitingIds = waiting.mapTo(HashSet()) { it.videoId }
                val room = MAX_QUEUED_AUTOPLAY - waiting.count { it.fromAutoplay }
                val fresh = suggestions.filterNot { it.videoId in waitingIds }.take(room.coerceAtLeast(0))
                if (fresh.isEmpty()) return@withContext true
                DesktopListenTogether.queueAdd(fresh.map { it.toPartyTrack(0L) })
                val added = fresh.first().videoId
                withTimeoutOrNull(PARTY_AUTOPLAY_ECHO_TIMEOUT_MS) {
                    DesktopListenTogether.state.first { state -> state.queue.items.any { it.videoId == added } }
                } != null
            }
        }

    fun loadAutoplaySongs(playFirst: Boolean = false) {
        val current = selectedSong ?: return
        val party = DesktopListenTogether.state.value
        if (!DesktopAutoplay.enabled(party, autoplay) ||
            repeatMode == DesktopRepeatMode.ALL ||
            (party.inParty && DesktopAutoplay.supplierId(party) != party.you?.memberId)
        ) return

        if (dontRepeatSuggestions) sessionSongHistory += current

        // In a party the server's queue is the one being topped up, and this computer's copy of it
        // lags by however long reconcile is held off. Counted from the copy, a batch that had
        // already landed read as missing and the same station went out again.
        val queued = partyAutoplayWaiting(party, current.videoId)
            ?: liveQueue.songs.drop(liveQueue.index + 1).count { it.fromAutoplay }
        val needed = MAX_QUEUED_AUTOPLAY - queued
        if (needed <= 0 && !playFirst) return
        // One request per seed.
        if (!playFirst && autoplaySeed == current.videoId) return
        autoplaySeed = current.videoId

        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            var remaining = needed.coerceAtLeast(1)
            var emptyRefreshesRemaining = 1
            while (isActive) {
                DesktopTrackLog.log("autoplay: building a station from '${current.title}'")
                val at = liveQueue.songs.size
                // What the queue holds — the party's too — plus everything this session has
                // already offered.
                val partyQueue = DesktopListenTogether.state.value
                    .takeIf { it.inParty }?.queue?.items.orEmpty().map { it.toDesktopSong() }
                val suggestions = DesktopAutoplay.tracksFor(
                    existing = if (dontRepeatSuggestions) {
                        liveQueue.songs + partyQueue + sessionSongHistory
                    } else {
                        liveQueue.songs + partyQueue
                    },
                    seedSong = current,
                    limit = remaining,
                ).getOrElse { failure ->
                    DesktopTrackLog.log("autoplay: could not build a station: ${failure.message}")
                    emptyList()
                }
                // The listener may have moved on while the station was being fetched; appending
                // then would attach it to the wrong seed.
                val latestParty = DesktopListenTogether.state.value
                if (selectedSong?.videoId != current.videoId ||
                    !DesktopAutoplay.enabled(latestParty, autoplay) ||
                    (latestParty.inParty && DesktopAutoplay.supplierId(latestParty) != latestParty.you?.memberId)
                ) return@launch
                if (suggestions.isEmpty()) {
                    DesktopTrackLog.log("autoplay: no station came back for '${current.title}'")
                    // Match the phone's bounded recovery: retry once after a short delay, but only
                    // while the current track genuinely has no tail to play next.
                    if (liveQueue.index < 0 || liveQueue.index != liveQueue.songs.lastIndex) return@launch
                    if (emptyRefreshesRemaining-- <= 0) return@launch
                    delay(AUTOPLAY_EMPTY_REFRESH_DELAY_MS)
                    remaining = MAX_QUEUED_AUTOPLAY
                    continue
                }
                // The station carries on from what was playing, so it keeps that queue's origin —
                // Android holds the source on every queue item, not just the ones picked by hand.
                val inherited = current.playbackSource?.let {
                    DesktopQueueSource(it, current.playbackSourceType ?: PlaybackSourceType.QUEUE, current.playbackSourceId)
                }
                val queuedSuggestions = suggestions.map { it.withSource(inherited) }
                if (latestParty.inParty) {
                    // Match the phone: the server owns the party queue. Do not mutate this one
                    // device first; queueAdd is echoed back and all members apply it atomically.
                    val landed = topUpPartyAutoplay(current.videoId, queuedSuggestions)
                    if (!landed) {
                        autoplaySeed = null
                        DesktopTrackLog.log("autoplay: party refused the queue top-up; retry is armed")
                        return@launch
                    }
                    if (playFirst) DesktopListenTogether.next()
                } else {
                    liveQueue = liveQueue.append(queuedSuggestions)
                    saveQueue()
                    // The mix continues from where it was added rather than starting a queue of
                    // its own.
                    if (playFirst) playQueueIndex(at)
                }
                if (dontRepeatSuggestions) sessionSongHistory += suggestions
                DesktopTrackLog.log(
                    "autoplay: queued ${suggestions.size} after '${current.title}'",
                )
                return@launch
            }
        }
    }

    /**
     * Turning AutoPlay on or off, and what that does to the queue.
     *
     * Android runs one path for this — `toggleAutoplayFromNotification` — whether the switch was
     * thrown in the player or anywhere else, and it does more than set a flag. Off drops the
     * suggestions it had already queued, because switching it off is the listener saying they do
     * not want them. On clears the seed first, so the station is fetched again for the track
     * already playing rather than being refused as one this seed has been loaded for.
     */
    fun setAutoplay(enabled: Boolean) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        autoplay = enabled
        persistence.saveBoolean("autoplay", enabled)
        autoplayJob?.cancel()
        autoplayJob = null
        autoplaySeed = null
        if (DesktopListenTogether.state.value.inParty) {
            // As on the phone, the server owns the effective setting and queue while connected.
            // Keep the personal preference for after leaving, but do not trim or top up this one
            // device ahead of the server's state broadcast.
            DesktopListenTogether.setAutoplay(enabled)
            return
        }
        if (enabled) {
            loadAutoplaySongs()
        } else {
            val trimmed = liveQueue.withoutAutoplay()
            if (trimmed !== liveQueue) {
                liveQueue = trimmed
                saveQueue()
            }
        }
    }

    fun playNext() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        // Automix on skip: when enabled and the next deck is already prepared, blend into it over a
        // short crossfade instead of cutting. The blend's own hand-off advances the queue, so a
        // successful mix returns here without touching liveQueue; anything else falls through to the
        // ordinary skip below.
        if (automixOnSkip && repeatMode != DesktopRepeatMode.ONE) {
            val currentId = selectedSong?.videoId
            val upcoming = currentId?.let {
                liveQueue.followingFor(it, repeatAll = repeatMode == DesktopRepeatMode.ALL)
            }
            if (upcoming != null && playbackEngine.beginSkipMix(upcoming.videoId, SKIP_MIX_ON_SKIP_SECONDS)) {
                return
            }
        }
        when {
            liveQueue.hasNext -> {
                liveQueue = liveQueue.next()
                playCurrent()
            }
            repeatMode == DesktopRepeatMode.ALL && liveQueue.songs.isNotEmpty() -> {
                liveQueue = liveQueue.copy(index = 0)
                playCurrent()
            }
            DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay) ->
                loadAutoplaySongs(playFirst = true)
        }
    }

    fun playPrevious() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val positionMs = playbackEngine.state.value.positionMs

        // After 10 seconds, Previous restarts the current song.
        if (positionMs > BACK_RESTARTS_AFTER_MS) {
            playbackEngine.seekTo(0L)
            partySyncHolder[0]?.onLocalIntent()
            return
        }

        // Within 10 seconds, Previous goes to the previous song in queue.
        if (!liveQueue.hasPrevious) return
        liveQueue = liveQueue.previous()
        playCurrent()
    }

    /**
     * Shuffle rearranges the queue rather than switching playback to a hidden random order, so what
     * the queue shows stays what plays.
     */
    fun setShuffle(enabled: Boolean) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        shuffle = enabled
        persistence.saveBoolean("shuffle", enabled)
        liveQueue = if (enabled) {
            preShuffleOrder = liveQueue.orderKeys()
            liveQueue.shuffledAhead()
        } else {
            liveQueue.inOrderOf(preShuffleOrder).also { preShuffleOrder = emptyList() }
        }
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    fun closePlaylistDialogs() {
        overlays.playlistDialog = false
        overlays.localPlaylistDialog = false
        playlistTarget = null
        playlistTargets = emptyList()
        overlays.rename = false
        overlays.delete = false
        overlays.reorder = false
        playlistError = null
        playlistBusy = false
    }

    /** Creates a playlist — on the account when there is one, otherwise here. */
    fun createPlaylist(title: String, privacy: PlaylistPrivacy) {
        val name = title.trim()
        if (name.isBlank()) return
        val seeds = if (playlistTargets.isNotEmpty()) playlistTargets else listOfNotNull(playlistTarget)
        // A track from another source (or a playlist opened from Local Music) cannot go in an
        // account playlist, so the playlist made to hold it is kept here.
        if (overlays.localPlaylistDialog || !DesktopYouTubeAuth.isSignedIn || seeds.any { !DesktopSearchClient.isVideoId(it.videoId) }) {
            val cleanSeeds = seeds.map(Song::withoutPlaybackOrigin).distinctBy(Song::videoId)
            playlists = playlists + DesktopPlaylist(title = name, songs = cleanSeeds)
            persistence.savePlaylists(playlists)
            closePlaylistDialogs()
            return
        }
        playlistBusy = true
        playlistError = null
        val seed = seeds.firstOrNull()
        scope.launch {
            DesktopSearchClient.createPlaylist(name, privacy, seeds.map(Song::videoId))
                .onSuccess { playlistId ->
                    closePlaylistDialogs()
                    editPlaylistShelf { items ->
                        val card = ShelfItem(
                            title = name,
                            // Only what this request itself establishes: an unseeded playlist gets
                            // a card of just its name rather than a guess at what the feed will
                            // call it.
                            subtitle = if (seeds.size == 1) "1 song" else if (seeds.size > 1) "${seeds.size} songs" else "",
                            thumbnailUrl = seed?.thumbnailUrl,
                            videoId = null,
                            browseId = "VL$playlistId",
                        )
                        // Leads the shelf because it is the newest, which is the order the feed
                        // itself comes in.
                        listOf(card) + items.filterNot { it.browseId == card.browseId }
                    }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not create the playlist"
                }
        }
    }

    fun openPlaylist(playlist: DesktopPlaylist) {
        showCollection(
            DesktopCollection(
                browseId = playlist.id,
                title = playlist.title,
                subtitle = "Playlist • ${playlist.songs.size} songs",
                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                type = BrowseType.PLAYLIST,
                songs = playlist.songs,
                owned = true,
            ),
        )
    }

    /**
     * Keeps an imported Spotify playlist where the phone does: on the account when signed in,
     * otherwise on this computer, which is also the fallback if the account refuses it. Returns a
     * note only for that fallback.
     */
    suspend fun importSpotifyPlaylist(title: String, songs: List<Song>): String? {
        val signedIn = DesktopYouTubeAuth.isSignedIn
        if (signedIn) {
            val created = DesktopSearchClient.createPlaylist(title, PlaylistPrivacy.PRIVATE, songs.map(Song::videoId))
            created.getOrNull()?.let { playlistId ->
                editPlaylistShelf { items ->
                    val card = ShelfItem(
                        title = title,
                        subtitle = "${songs.size} songs",
                        thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                        videoId = null,
                        browseId = "VL$playlistId",
                    )
                    listOf(card) + items.filterNot { it.browseId == card.browseId }
                }
                libraryStale = true
                openCollection("VL$playlistId")
                return null
            }
            DesktopTrackLog.log("spotify import: account playlist failed: ${created.exceptionOrNull()?.message}")
        }
        val playlist = DesktopPlaylist(title = title, songs = songs)
        playlists = playlists + playlist
        persistence.savePlaylists(playlists)
        openPlaylist(playlist)
        return if (signedIn) {
            DesktopStrings.format(
                "spotify_import_local_fallback",
                title,
                fallback = "Couldn't create it on YouTube Music, so '%1\$s' was saved on this device instead.",
            )
        } else {
            null
        }
    }

    fun saveCollectionAsPlaylist(collection: DesktopCollection) {
        val songsToSave = collection.songs
        if (songsToSave.isEmpty()) return
        val title = collection.title.ifBlank { "Playlist" }
        val newPlaylist = DesktopPlaylist(title = title, songs = songsToSave)
        playlists = playlists + newPlaylist
        persistence.savePlaylists(playlists)
        DesktopPlayerHost.showMessage(
            DesktopStrings.format(
                "d_playlist_saved",
                title,
                songsToSave.size,
                fallback = "Saved \"$title\" to your playlists (${songsToSave.size} songs)"
            )
        )
    }

    fun addToPlaylist(playlist: DesktopPlaylist) {
        val songsToAdd = (if (playlistTargets.isNotEmpty()) playlistTargets else listOfNotNull(playlistTarget))
            .map(Song::withoutPlaybackOrigin)
        if (songsToAdd.isEmpty()) return
        playlists = playlists.map { current ->
            if (current.id == playlist.id) current.copy(songs = (current.songs + songsToAdd).distinctBy(Song::videoId)) else current
        }
        persistence.savePlaylists(playlists)
        closePlaylistDialogs()
    }

    fun addToAccountPlaylist(playlist: UserPlaylist) {
        val songsToAdd = if (playlistTargets.isNotEmpty()) playlistTargets else listOfNotNull(playlistTarget)
        if (songsToAdd.isEmpty()) return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.addToPlaylist(playlist.playlistId, songsToAdd.map { it.videoId })
                .onSuccess { closePlaylistDialogs() }
                .onFailure {
                    DesktopTrackLog.log("playlist: could not add: ${it.message}")
                    playlistBusy = false
                    playlistError = it.message ?: "Could not add to ${playlist.title}"
                }
        }
    }

    playbackEngine = remember {
        DesktopPlaybackEngine(
            onEnded = {
                selectedSong?.let(DesktopScrobbling::onPlaybackEnded)
                if (DesktopSleepTimer.afterTrack.value) {
                    DesktopSleepTimer.cancel()
                } else if (repeatMode == DesktopRepeatMode.ONE) {
                    // Replayed in place. [startSong] makes the track a queue of one, which threw
                    // the rest of the queue away the first time it repeated.
                    val song = selectedSong
                    if (song != null && liveQueue.current?.videoId == song.videoId) {
                        playCurrent()
                    } else {
                        song?.let { startSong(it, true) }
                    }
                } else {
                    playNext()
                }
            },
            onCrossfaded = { song ->
                scope.launch {
                    // The queue moves with the audio. Only the selection used to, which left the
                    // queue on the track that had just finished — so Next "advanced" onto the song
                    // already playing, and every later skip landed one behind.
                    liveQueue = liveQueue.afterHandoffTo(song.videoId)
                    saveQueue()
                    selectedSong = song
                    history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
                    persistence.saveHistory(history)
                    DesktopScrobbling.updateNowPlaying(song)
                    partySyncHolder[0]?.onLocalIntent()
                }
            },
        )
    }
    val partySync = remember(playbackEngine) {
        DesktopPartySync(
            scope = scope,
            engine = playbackEngine,
            playTrack = { shared, at ->
                // The phone's PartySync.load: the party's running order, standing on its track —
                // so the queue on screen moves with the song rather than keeping the old one as
                // "now playing" and the new one as the first thing still to come.
                val songs = DesktopQueue.adopt(liveQueue.songs, shared.map { it.toDesktopSong() })
                liveQueue = DesktopQueue(songs, at.coerceIn(songs.indices)).trimmed()
                liveQueue.current?.let { song ->
                    selectedSong = song
                    playbackEngine.load(song, playWhenReady = false)
                }
            },
            localQueue = { liveQueue.songs to liveQueue.index },
            applyPartyUpcoming = { upcoming ->
                val aligned = liveQueue.withPartyUpcoming(upcoming.map { it.toDesktopSong() })
                if (aligned !== liveQueue) liveQueue = aligned
            },
            applyPartyAutoplay = { _ ->
                // The party setting overrides the personal preference only while connected. The
                // phone deliberately does not persist it or delete queue rows on this device.
                autoplayJob?.cancel()
                autoplayJob = null
                autoplaySeed = null
            },
            onEnteredParty = {
                if (personalQueueStash == null) {
                    personalQueueStash = liveQueue
                    personalPositionStash = playbackEngine.state.value.positionMs
                    personalPlayingStash = playbackEngine.state.value.isPlaying
                }
            },
            onLeftParty = {
                personalQueueStash?.let { stashed ->
                    liveQueue = stashed
                    selectedSong = stashed.current
                    stashed.current?.let {
                        playbackEngine.load(it, personalPlayingStash, personalPositionStash)
                    }
                    saveQueue()
                }
                personalQueueStash = null
            },
        )
    }
    DisposableEffect(partySync) {
        partySyncHolder[0] = partySync
        partySync.start()
        onDispose {
            partySync.stop()
            if (partySyncHolder[0] === partySync) partySyncHolder[0] = null
        }
    }
    fun togglePlayPauseFromUser() {
        if (partySync.handleLockedPlayPause()) return
        playbackEngine.togglePlayPause()
        partySync.onLocalIntent()
    }
    fun playFromUser() {
        if (DesktopListenTogether.state.value.controlsLocked) {
            if (!playbackEngine.state.value.isPlaying) partySync.handleLockedPlayPause()
            return
        }
        playbackEngine.play()
        partySync.onLocalIntent()
    }
    fun pauseFromUser() {
        if (DesktopListenTogether.state.value.controlsLocked) {
            if (playbackEngine.state.value.isPlaying) partySync.handleLockedPlayPause()
            return
        }
        playbackEngine.pause()
        partySync.onLocalIntent()
    }
    val mprisController = remember(playbackEngine) {
        DesktopMprisController(
            onPlay = ::playFromUser,
            onPause = ::pauseFromUser,
            onPlayPause = ::togglePlayPauseFromUser,
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onShuffleChanged = { enabled ->
                shuffle = enabled
                persistence.saveBoolean("shuffle", enabled)
            },
            onLoopStatusChanged = { status ->
                repeatMode = when (status) {
                    "Track" -> DesktopRepeatMode.ONE
                    "Playlist" -> DesktopRepeatMode.ALL
                    else -> DesktopRepeatMode.OFF
                }
                persistence.saveString("repeat_mode", repeatMode.name)
            },
            onRateChanged = { value ->
                playbackSpeed = value.toFloat().coerceIn(0.25f, 3.0f)
                persistence.saveString("playback_speed", playbackSpeed.toString())
            },
            onVolumeChanged = { value ->
                if (DesktopDacVolume.level.value != null) {
                    DesktopDacVolume.set(value.toFloat())
                } else if (!DesktopBitPerfect.status.value.active) {
                    volume = value.toFloat().coerceIn(0.0f, 1.0f)
                    persistence.saveString("volume", volume.toString())
                }
            },
            onSeek = { positionMs ->
                if (!DesktopListenTogether.state.value.controlsLocked) {
                    playbackEngine.seekTo(positionMs)
                    partySync.onLocalIntent()
                }
            },
        )
    }
    DisposableEffect(mprisController) {
        mprisController.start()
        onDispose { mprisController.stop() }
    }
    // The Windows half of the same idea. Linux has MPRIS above; on Windows this
    // is what the media keys and the volume flyout's card talk to.
    DisposableEffect(playbackEngine) {
        DesktopWindowsMedia.start(
            DesktopWindowsMedia.Controller(
                onPlay = ::playFromUser,
                onPause = ::pauseFromUser,
                onNext = ::playNext,
                onPrevious = ::playPrevious,
                onStop = ::pauseFromUser,
                onSeek = { positionMs ->
                    if (!DesktopListenTogether.state.value.controlsLocked) {
                        playbackEngine.seekTo(positionMs)
                        partySync.onLocalIntent()
                    }
                },
            ),
        )
        onDispose { DesktopWindowsMedia.stop() }
    }

    LaunchedEffect(playbackSpeed) { playbackEngine.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(volume) { playbackEngine.setVolume(volume) }
    LaunchedEffect(audioQuality) { playbackEngine.setAudioQuality(audioQuality.name) }
    LaunchedEffect(automixPerformance) {
        playbackEngine.setAutomixPerformance(automixPerformance)
    }
    // Every one of the seven settings renders the same curve, so they are collected together
    // rather than destructured — seven sources of one tuning is seven chances to read them in the
    // wrong order.
    val eqEnabled by DesktopEqualizerSettings.enabled.collectAsState()
    val eqMode by DesktopEqualizerSettings.mode.collectAsState()
    val eqToneX by DesktopEqualizerSettings.toneX.collectAsState()
    val eqToneY by DesktopEqualizerSettings.toneY.collectAsState()
    val eqFocused by DesktopEqualizerSettings.focused.collectAsState()
    val eqBalance by DesktopEqualizerSettings.balance.collectAsState()
    val eqBands by DesktopEqualizerSettings.bands.collectAsState()
    LaunchedEffect(eqEnabled, eqMode, eqToneX, eqToneY, eqFocused, eqBalance, eqBands) {
        playbackEngine.setEqualizer(eqEnabled, DesktopEqualizerSettings.curve(), eqBalance)
        DesktopPlayerHost.pipeline.value = playbackEngine.pipeline()
    }
    LaunchedEffect(spatialAudio, skipSilence, smartVersionAlignment, outputPrecision) {
        playbackEngine.setSpatialAudio(spatialAudio)
        playbackEngine.setSkipSilence(skipSilence)
        playbackEngine.setSmartVersionAlignment(smartVersionAlignment)
        playbackEngine.setOutputPrecision(outputPrecision)
        DesktopPlayerHost.pipeline.value = playbackEngine.pipeline()
    }
    // Not while listening together, exactly as on the phone: a blend starts the next track early,
    // by a length this computer decides from its own copy of the audio, so every member would begin
    // the next song at a different moment and be dragged back by a correcting seek. The transition a
    // party shares is the plain one. The settings themselves are left alone and come back after.
    val inParty = partyState.inParty
    LaunchedEffect(automix, automixOnSkip, automixPhraseBars, crossfadeSeconds, inParty, audioQuality, selectedSong?.videoId, queue, liveQueue.index, shuffle, repeatMode) {
        playbackEngine.setAutomixEnabled(automix && !inParty)
        playbackEngine.setAutomixPhraseBars(if (automix && !inParty) automixPhraseBars else 0)
        playbackEngine.setCrossfadeSeconds(if (inParty) 0 else crossfadeSeconds)
        // A party mixes only with the plain transition, so a skip there cuts like it always did.
        playbackEngine.setSkipMixEnabled(automixOnSkip && !inParty)
        // Exactly what Next would play, so a crossfade can never blend into anything else. Shuffle
        // is already the queue's order; picking "the first track that is not this one" under it
        // blended into a song from history whenever the current one was not at the top.
        // Repeat-one prepares nothing: the track ends and [onEnded] starts it again, rather than
        // blending into the next one.
        val next = selectedSong
            ?.takeIf { repeatMode != DesktopRepeatMode.ONE }
            ?.let { liveQueue.followingFor(it.videoId, repeatAll = repeatMode == DesktopRepeatMode.ALL) }
        playbackEngine.prepareNext(next)
    }
    // Topped up on every track change, the way Android does it, rather than only once the queue has
    // run dry.
    LaunchedEffect(selectedSong?.videoId, autoplay, repeatMode) {
        loadAutoplaySongs()
    }
    val partyAutoplaySupplier = DesktopAutoplay.supplierId(partyState)
    LaunchedEffect(
        partyState.code,
        partyState.playback.autoplayEnabled,
        partyAutoplaySupplier,
    ) {
        if (partyState.inParty &&
            partyState.playback.autoplayEnabled &&
            partyAutoplaySupplier == partyState.you?.memberId
        ) {
            autoplayJob?.cancel()
            autoplayJob = null
            autoplaySeed = null
            loadAutoplaySongs()
        }
    }
    LaunchedEffect(liveQueue.songs.size, liveQueue.index, partyState.queue.seq) {
        val currentId = selectedSong?.videoId
        val shouldRefresh = DesktopAutoplay.queueNeedsRefresh(
            enabled = DesktopAutoplay.enabled(partyState, autoplay),
            repeatAll = repeatMode == DesktopRepeatMode.ALL,
            currentIndex = liveQueue.index,
            itemCount = liveQueue.songs.size,
            loadInProgress = autoplayJob?.isActive == true,
        )
        // Only re-arm a seed that has already been attempted. The track-change effect above owns
        // the initial load; this effect owns later local or server queue edits that expose a tail.
        if (shouldRefresh && currentId != null && autoplaySeed == currentId) {
            autoplayJob = null
            autoplaySeed = null
            loadAutoplaySongs()
        }
    }
    LaunchedEffect(Unit) {
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            if (DesktopSleepTimer.isExpired()) {
                DesktopSleepTimer.cancel()
                playbackEngine.pause()
            }
        }
    }
    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }
    val playback by playbackEngine.state.collectAsState()

    // The shared player reads the playhead off this one object, and only where it
    // draws it — see PlaybackPosition — so a tick never recomposes the player.
    val playerPosition = remember { PlaybackPosition() }
    LaunchedEffect(playbackEngine) {
        playbackEngine.state.collect {
            // The audio thread's own timestamp, not this collector's: it runs on the UI thread and
            // gets to a reading as late as the UI is busy.
            playerPosition.report(it.positionMs, it.positionSampledAtNanos)
            playerPosition.seeks = it.seeks
            playerPosition.advancing = !it.awaitingAudio
        }
    }

    // Lyrics and motion artwork follow whatever is *playing*, not whatever page happens to be open.
    // This is where Android keeps it — `MainActivity`, keyed on the player's own track — so the
    // lookup starts the moment a track does and the panel is already populated by the time anyone
    // opens the player. Run from the player page instead, nothing was fetched until it was opened,
    // and a track half a minute in then showed a skeleton and arrived mid-song.
    var lyrics by remember { mutableStateOf<DesktopLyrics?>(null) }
    // The provider picked for this track in the player's lyrics drawer, if any.
    var lyricsOnly by remember(selectedSong?.videoId) { mutableStateOf<String?>(null) }
    // A title and artist typed into the song menu's lyrics search, overriding the
    // track's own credits for the lookup — for a cover or a mislabelled upload.
    var lyricsQuery by remember(selectedSong?.videoId) { mutableStateOf<Pair<String, String>?>(null) }
    // Bumped on every search so re-running the same query still refetches.
    var lyricsSearchTick by remember(selectedSong?.videoId) { mutableStateOf(0) }
    // What each provider's most recent race turned up, so the song menu's
    // provider drawer can say "Found" or "Not found" per source, not just name
    // the winner. It is keyed on the track and is only ever added to, never
    // cleared mid-track: a repeat search a cache answers straight away fires no
    // callbacks, so clearing on start would blank the very states it should hold.
    val lyricsProviderResults = remember(selectedSong?.videoId) {
        mutableStateOf<Map<LyricsSource, LyricsProviderState>>(emptyMap())
    }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var canvas by remember { mutableStateOf<DesktopCanvasArtwork?>(null) }

    // The length the lyrics are matched on, taken once per track. A better rendition swapped in
    // mid-song reports its own, slightly different length — or none, for a manifest — and keyed
    // on the live duration, every such upgrade threw away the lyrics on screen and fetched them
    // all over again. Only the playing track's own report counts: right after a skip, [playback]
    // still describes the track before.
    var lyricsLengthMs by remember(selectedSong?.videoId) { mutableStateOf(0L) }
    val playingLengthMs = playback.durationMs.takeIf { playback.song?.videoId == selectedSong?.videoId } ?: 0L
    LaunchedEffect(selectedSong?.videoId, playingLengthMs > 0L) {
        if (lyricsLengthMs <= 0L && playingLengthMs > 0L) lyricsLengthMs = playingLengthMs
    }

    // Keyed on the duration too: it lands a beat after the track, and a database match needs it,
    // so looking up against a length of zero would settle on the wrong recording.
    LaunchedEffect(
        selectedSong?.videoId,
        lyricsLengthMs,
        syncedLyrics,
        prioritizeSyllables,
        lyricsOn,
        lyricsOrder,
        lyricsOnly,
        lyricsQuery,
        lyricsSearchTick,
    ) {
        val current = selectedSong
        lyrics = null
        lyricsError = null
        if (current == null) {
            lyricsLoading = false
            return@LaunchedEffect
        }
        if (!syncedLyrics) {
            lyricsError = "Synced lyrics are switched off"
            lyricsLoading = false
            return@LaunchedEffect
        }
        // A length is what a database match is made on, and it lands a beat after the track. Looking
        // up without one would settle on whichever recording shares the name — so this waits, the
        // same way Android's `loadLyrics` turns the call away until a duration exists.
        val length = lyricsLengthMs.takeIf { it > 0L } ?: current.durationMillis()
        if (length <= 0L) {
            lyricsLoading = true
            return@LaunchedEffect
        }
        lyricsLoading = true
        // The manual search asks the same providers under the typed words rather
        // than the track's own credits; a provider switch keeps that query.
        val probe = lyricsQuery?.let { (title, artist) -> current.copy(title = title, artist = artist) } ?: current
        DesktopLyricsClient.lookup(
            probe,
            length,
            only = lyricsOnly,
            // Each provider reports itself as the race runs; the callback is on
            // the IO thread, so the map is updated back on the composition's own.
            onSourceResult = { source, found ->
                scope.launch {
                    lyricsProviderResults.value += source to
                        if (found) LyricsProviderState.FOUND else LyricsProviderState.NOT_FOUND
                }
            },
        ).fold(
            onSuccess = { lyrics = it },
            onFailure = { lyricsError = it.message ?: "Lyrics unavailable" },
        )
        lyricsLoading = false
    }

    // A manual ask from the song menu's lyrics drawer, for a track whose automatic
    // lookup came up empty and whose real title or artist the listener knows
    // better than the tag. It just sets the query and nudges the effect above,
    // which owns the loading state and the result — so the drawer's provider
    // list updates through the same path as the automatic lookup.
    fun searchLyrics(title: String, artist: String) {
        lyricsQuery = title to artist
        lyricsSearchTick++
    }

    // The song menu's lyrics offset nudges the whole synced lyric against the clock. It is a
    // per-track adjustment, so a new track starts back at zero; the desktop owns the reset so the
    // phone's sheet, which carries the offset across tracks, is left alone.
    val lyricsOffsetMs by DesktopPlayerSettings.lyricsOffsetMs.collectAsState()
    LaunchedEffect(selectedSong?.videoId) {
        DesktopPlayerSettings.setLyricsOffsetMs(0)
    }

    LaunchedEffect(selectedSong?.videoId, animatedCanvas) {
        val current = selectedSong
        if (current == null || !animatedCanvas) {
            canvas = null
            return@LaunchedEffect
        }
        // Paint what is already known before waiting on anything: re-opening the player on a track
        // resolved a minute ago should not go dark on its way back to the same clip.
        canvas = DesktopCanvasClient.cached(current)
        canvas = withContext(Dispatchers.IO) { DesktopCanvasClient.lookup(current) }
    }

    LaunchedEffect(
        playback.song?.videoId,
        playback.isPlaying,
        playback.durationMs,
        playback.volume,
        playback.error,
        shuffle,
        repeatMode,
        playbackSpeed,
    ) {
        mprisController.update(
            playback = playback,
            shuffle = shuffle,
            loopStatus = when (repeatMode) {
                DesktopRepeatMode.OFF -> "None"
                DesktopRepeatMode.ALL -> "Playlist"
                DesktopRepeatMode.ONE -> "Track"
            },
            rate = playbackSpeed.toDouble(),
        )
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs) {
        DesktopScrobbling.onPlaybackStateChanged(playback)
        DesktopWindowsMedia.publish(playback)
    }
    // Refetched on opening the page rather than cached for the session: the
    // point of it is that something played elsewhere shows up here.
    LaunchedEffect(destination, youtubeSignedIn, sessionRevision) {
        if (destination != DesktopDestination.HISTORY || !youtubeSignedIn) return@LaunchedEffect
        DesktopSearchClient.history().onSuccess { remoteHistory = it }
    }
    LaunchedEffect(selectedSong?.videoId) {
        trackLinks = null
        val current = selectedSong ?: return@LaunchedEffect
        if (current.artistId != null && current.albumId != null && current.artists.isNotEmpty() && !current.albumName.isNullOrBlank()) {
            return@LaunchedEffect
        }
        trackLinks = DesktopSearchClient.trackLinks(current.videoId).getOrNull()
    }
    val effectiveSong = remember(selectedSong, trackLinks) {
        val current = selectedSong ?: return@remember null
        trackLinks?.takeIf { it.videoId == current.videoId }?.let { extra ->
            current.copy(
                artistId = current.artistId ?: extra.artistId,
                artists = current.artists.ifEmpty { extra.artists },
                albumId = current.albumId ?: extra.albumId,
                albumName = current.albumName ?: extra.albumName,
            )
        } ?: current
    }
    // Keyed on the same things, plus the rate: Discord counts the bar down on
    // its own clock, so only a change to what it was told is worth another push.
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs, playbackSpeed, playback.streamFormat) {
        DesktopDiscordRpc.onPlaybackStateChanged(playback, playbackSpeed)
    }
    // The account's own history on YouTube Music, which is what the home feed is built out of.
    DisposableEffect(playback.song?.videoId) {
        val previous = playbackEngine.state.value.positionMs / 1_000
        onDispose { DesktopPlaybackTracker.onTrackChanged(previous) }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        DesktopPlaybackTracker.onPlaying(songId)
        while (currentCoroutineContext().isActive) {
            delay(HISTORY_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (snapshot.song?.videoId != songId) break
            DesktopPlaybackTracker.onProgress(songId, snapshot.positionMs / 1_000)
        }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        var previousPosition = playback.positionMs
        var countedAsPlay = songId in statsCountedSongs
        while (currentCoroutineContext().isActive) {
            delay(STATS_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (!snapshot.isPlaying || snapshot.song?.videoId != songId) break
            val position = snapshot.positionMs
            val delta = (position - previousPosition).takeIf { it in 1L..STATS_MAX_DELTA_MS } ?: 0L
            if (delta > 0L) {
                val threshold = snapshot.durationMs.takeIf { it > 0L }?.let { minOf(it / 2L, SCROBBLE_THRESHOLD_MS) }
                    ?: SCROBBLE_THRESHOLD_MS
                val reachedThreshold = !countedAsPlay && position >= threshold
                DesktopListeningStats.record(snapshot.song, delta, reachedThreshold)
                if (reachedThreshold) {
                    countedAsPlay = true
                    statsCountedSongs = statsCountedSongs + songId
                }
                statsRevision++
            }
            previousPosition = position
        }
    }
    LaunchedEffect(statsRevision) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }

    /** Opens an artist's page, from a card, a search row or the player's credit. */
    fun openArtist(browseId: String, name: String) {
        val target = DesktopArtistTarget(browseId, name)
        if (openedArtist == target && artistState is UiState.Success) return
        openedCollection = null
        homeMoodsAllOpen = false
        overlays.replay = false
        overlays.settingsPage = null
        openedArtist = target
        artistState = UiState.Loading
        artistReloads++
    }

    /**
     * The "…" menu for one row, wherever it is drawn.
     *
     * Built once so a row on Downloads or Local Music offers what the player's own "…" offers —
     * Android opens the same `SongActionsSheet` from every one of them.
     */
    fun songActionsFor(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        onOpenAlbum = ::openAlbum,
        onOpenArtist = { id -> openArtist(id, song.artist) },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    /** The "…" a song row hangs off, built the same way on every page that offers one. */
    @Composable
    fun songMenu(song: Song) = DesktopSongMenuAnchor(
        song = song,
        liked = song.videoId in likedIds,
        actions = songActionsFor(song),
        onToggleLike = { toggleLike(song) },
        onRevertToOriginal = null,
        onUpgradeQuality = null,
    )

    /** Replay counts artists by name, so the page has to be found from the name alone. */
    fun openArtistByName(name: String) {
        scope.launch {
            val match = DesktopSearchClient.search(name, SearchFilter.ARTISTS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.browseId.isNotBlank() }
                ?: return@launch
            openArtist(match.browseId, match.title)
        }
    }

    LaunchedEffect(openedArtist, artistReloads) {
        val target = openedArtist ?: return@LaunchedEffect
        if (artistState !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.artistPage(target.browseId).fold(
            onSuccess = { artistState = UiState.Success(it) },
            onFailure = { artistState = UiState.Error(it.message ?: "Could not open ${target.name}") },
        )
    }

    fun toggleSubscription(subscription: SubscriptionState) {
        val target = openedArtist ?: return
        val next = !subscription.subscribed
        // Painted before the network answers and put back if it refuses.
        artistState = (artistState as? UiState.Success)?.let { current ->
            UiState.Success(current.data.copy(subscription = subscription.copy(subscribed = next)))
        } ?: artistState
        scope.launch {
            DesktopSearchClient.setSubscribed(subscription.channelId, next).onFailure { failure ->
                DesktopTrackLog.log("subscription: ${target.name} failed: ${failure.message}")
                artistState = (artistState as? UiState.Success)?.let { current ->
                    UiState.Success(current.data.copy(subscription = subscription))
                } ?: artistState
            }
        }
    }

    /** A search hit or a recent search that is a page: an artist's own, or a collection's. */
    fun openBrowseItem(item: BrowseItem) {
        if (item.type == BrowseType.ARTIST) {
            openArtist(item.browseId, item.title)
        } else {
            openCollection(item.browseId, item)
        }
    }

    fun openShelfItem(item: ShelfItem, shelfTitle: String? = null) {
        val videoId = item.videoId
        val browseId = item.browseId
        when {
            videoId != null -> {
                val source = shelfTitle?.let { DesktopQueueSource(it, PlaybackSourceType.HOME) }
                playSong(item.toSong(), source = source)
            }
            // An artist is a page of its own, not a list of tracks with a photograph on top.
            browseTypeOf(browseId.orEmpty()) == BrowseType.ARTIST ->
                openArtist(browseId!!, item.title)
            browseId != null -> openCollection(
                browseId = browseId,
                fallback = BrowseItem(
                    browseId = browseId,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = BrowseType.OTHER,
                ),
            )
        }
    }

    fun openMenu(song: Song) = pageMenu(song)

    /** What the phone records for a search hit: the thing picked, not the words typed to find it. */
    fun recordSongSearch(song: Song) = DesktopSearchHistory.record(
        SearchHistoryEntity(
            id = song.videoId,
            title = song.title,
            subtitle = song.artist,
            artworkUrl = song.thumbnailUrl,
            entityType = EntityType.TRACK,
        ),
    )

    fun recordBrowseSearch(item: BrowseItem) = DesktopSearchHistory.record(
        SearchHistoryEntity(
            id = item.browseId,
            title = item.title,
            subtitle = item.subtitle,
            artworkUrl = item.thumbnailUrl,
            entityType = when (item.type) {
                BrowseType.ALBUM -> EntityType.ALBUM
                BrowseType.ARTIST -> EntityType.ARTIST
                BrowseType.PLAYLIST -> EntityType.PLAYLIST
                else -> EntityType.TRACK
            },
        ),
    )

    /** Typing, from the page's field or the sidebar's: the typeahead follows, and an empty field goes back to recents. */
    fun editQuery(text: String) {
        query = text
        searchTyping = true
        if (text.isBlank()) {
            searchCommitted = false
            searchRows = emptyList()
            searchError = null
        }
    }

    fun search() {
        if (query.isBlank() || searchLoading) return
        overlays.settingsPage = null
        destination = DesktopDestination.SEARCH
        searchTyping = false
        searchSuggestions = emptyList()
        // Submitting is picking the first track the typeahead offered, as on the phone — or, with
        // none, the first the results bring back.
        val typedHit = searchTypeahead.firstNotNullOfOrNull { row ->
            when (row) {
                is SearchResult.TopTrack -> row.song
                is SearchResult.Track -> row.song
                else -> null
            }
        }
        typedHit?.let(::recordSongSearch)
        searchCommitted = true
        searchScrollReset++
        searchLoading = true
        searchError = null
        scope.launch {
            try {
                DesktopMusicSources.search(query, searchFilter).fold(
                    onSuccess = { rows ->
                        searchRows = rows
                        if (typedHit == null) {
                            rows.firstNotNullOfOrNull { row ->
                                when (row) {
                                    is SearchResult.TopTrack -> row.song
                                    is SearchResult.Track -> row.song
                                    else -> null
                                }
                            }?.let(::recordSongSearch)
                        }
                    },
                    onFailure = { searchError = it.message ?: "Search failed" },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                searchError = failure.message ?: "Search failed"
            } finally {
                // OBS and other desktop capture tools can briefly interrupt focus/coroutines.
                searchLoading = false
            }
        }
    }

    /** The typeahead, one request behind the keystrokes rather than one per. */
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(SUGGESTION_DEBOUNCE_MS)
        DesktopSearchClient.searchSuggestions(query)
            .onSuccess { if (searchTyping) searchSuggestions = it }
            .onFailure { searchSuggestions = emptyList() }
    }

    // Its own pass, on its own delay: a search costs far more than a completion, so it waits for a
    // longer pause in the typing rather than riding the same one.
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchTypeahead = emptyList()
            return@LaunchedEffect
        }
        delay(TYPEAHEAD_MEDIA_DEBOUNCE_MS)
        DesktopSearchClient.searchTypeahead(query)
            .onSuccess { if (searchTyping) searchTypeahead = it.take(TYPEAHEAD_MEDIA_LIMIT) }
            .onFailure { searchTypeahead = emptyList() }
    }

    fun runSearch(term: String) {
        query = term
        searchTyping = false
        searchSuggestions = emptyList()
        searchTypeahead = emptyList()
        search()
    }

    fun selectDestination(next: DesktopDestination) {
        if (next == DesktopDestination.LIBRARY && libraryStale) {
            libraryStale = false
            reloadLibrary()
        }
        openedArtist = null
        openedCollection = null
        homeMoodsAllOpen = false
        overlays.replay = false
        overlays.settingsPage = null
        selectedMoodGenre = null
        libraryShowAll = null
        if (next == DesktopDestination.SEARCH) searchFocusRequested = true
        if (next == DesktopDestination.LISTEN_NOW) {
            navHistory.clear()
        }
        destination = next
    }

    // Settings' search and scroll outlive a visit to one of its pages, and start over each time
    // Settings itself is opened.
    var settingsSession by remember { mutableStateOf(0) }
    var settingsQuery by remember(settingsSession) { mutableStateOf("") }
    val settingsListState = remember(settingsSession) { LazyListState() }

    /** Settings in place of the page — from the sidebar, the account switcher or the tray. */
    fun openSettings() {
        overlays.nowPlaying = false
        if (overlays.settingsPage == null) settingsSession++
        overlays.settingsPage = DesktopSettingsPage.MAIN
    }

    /** The Library's folder rows: Liked Songs, the two folders this computer has, and Spotify once connected. */
    val libraryLinks = remember(spotifyCanvasCookie.isNotBlank()) {
        listOfNotNull(
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["auto_liked", "Liked songs"],
                    subtitle = DesktopStrings["auto_playlist", "Auto playlist"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = "VLLM",
                ),
                icon = Icons.Rounded.Favorite,
            ),
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["downloads", "Downloads"],
                    subtitle = DesktopStrings["downloaded_songs", "Downloaded songs"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = LOCAL_DOWNLOADS_ID,
                ),
                icon = Icons.Rounded.Download,
            ),
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["local_music", "Local Music"],
                    subtitle = DesktopStrings["d_audio_files_on_this_computer", "Audio files on this computer"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = LOCAL_MUSIC_ID,
                ),
                icon = Icons.Rounded.Folder,
            ),
            // As on the phone, only while signed in to Spotify in Account & integrations.
            LibraryLink(
                item = ShelfItem(
                    title = SPOTIFY_SHELF,
                    subtitle = DesktopStrings["spotify_library_subtitle", "Your playlists and Liked Songs"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = SPOTIFY_LIBRARY_ID,
                ),
                icon = Icons.Rounded.Cloud,
                logo = SharedRes.drawable.spotify_logo,
            ).takeIf { spotifyCanvasCookie.isNotBlank() },
        )
    }

    /** The Spotify account's playlists, as a Library grid; Liked Songs first, as on the phone. */
    fun openSpotifyLibrary() {
        libraryShowAll = HomeShelf(SPOTIFY_SHELF, emptyList())
        spotifyLibraryStatus = "Loading your Spotify playlists…"
        scope.launch {
            runCatching { SpotifyLibrary.playlists() }
                .onSuccess { found ->
                    spotifyLibraryStatus = null
                    if (libraryShowAll?.title == SPOTIFY_SHELF) {
                        libraryShowAll = HomeShelf(SPOTIFY_SHELF, found.map { it.toShelfItem() })
                    }
                }
                .onFailure {
                    DesktopTrackLog.log("spotify: playlists failed: ${it.message}")
                    spotifyLibraryStatus = "Could not load your Spotify playlists: ${it.message}"
                }
        }
    }

    /**
     * The Library's "On device" shelf: the playlists kept on this computer rather than on the
     * account — the desktop's counterpart of the phone's downloaded releases, and like them
     * reachable from nowhere else.
     */
    val libraryDeviceItems = remember(playlists) {
        playlists.map { playlist ->
            ShelfItem(
                title = playlist.title,
                subtitle = "Playlist • ${playlist.songs.size} songs",
                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                videoId = null,
                browseId = LOCAL_PLAYLIST_PREFIX + playlist.id,
            )
        }
    }

    /** A Library card: a folder is a page of this app's, a local playlist is opened from here. */
    fun openLibraryItem(item: ShelfItem) {
        val browseId = item.browseId.orEmpty()
        when {
            browseId == "VLLM" -> openLikedMusic()
            browseId == LOCAL_DOWNLOADS_ID -> selectDestination(DesktopDestination.DOWNLOADS)
            browseId == LOCAL_MUSIC_ID -> selectDestination(DesktopDestination.LOCAL_MUSIC)
            browseId == SPOTIFY_LIBRARY_ID -> openSpotifyLibrary()
            // Spotify's tracks fill in below, as they are read and matched.
            browseId.startsWith(SPOTIFY_PAGE_PREFIX) -> showCollection(
                DesktopCollection(
                    browseId = browseId,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = BrowseType.PLAYLIST,
                    songs = emptyList(),
                    loading = true,
                ),
            )
            browseId.startsWith(LOCAL_PLAYLIST_PREFIX) ->
                playlists.firstOrNull { it.id == browseId.removePrefix(LOCAL_PLAYLIST_PREFIX) }?.let(::openPlaylist)
            else -> openShelfItem(item)
        }
    }

    /**
     * Adds shelves to the page as they arrive, skipping any heading already on it and any shelf
     * that repeats one's releases — the phone's `publishHomeShelves`. Recently Played is
     * [prepend]ed and replaces any stale copy the core feed carried.
     */
    fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (homeState as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            val replacing = shelves.map { it.title.lowercase() }.toSet()
            homeSeenTitles.addAll(replacing)
            homeState = UiState.Success(shelves + existing.filterNot { it.title.lowercase() in replacing })
            return
        }
        val added = shelves.withoutRepeatsOf(existing)
            .filter { it.items.isNotEmpty() && homeSeenTitles.add(it.title.lowercase()) }
        if (added.isNotEmpty()) homeState = UiState.Success(existing + added)
    }

    /**
     * The phone's Home: the core feed, the account's own Recently Played at its head, and the
     * supplementary browse feeds, all asked for at once and laid down as each lands. Signed out
     * there is no Recently Played to ask for, which is also how the phone has it.
     */
    fun loadHome() {
        if (!youtubeSourceEnabled) {
            homeState = UiState.Error("YouTube Music is disabled")
            return
        }
        val generation = ++homeGeneration
        val signedIn = DesktopYouTubeAuth.isSignedIn
        homeIdentity = "$signedIn/${DesktopAccounts.activeAccountId()}"
        homeState = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        homeLoadingMore = false
        homeRecentsLoading = signedIn
        scope.launch {
            launch {
                DesktopSearchClient.home().fold(
                    onSuccess = { feed ->
                        if (generation != homeGeneration) return@fold
                        homeContinuation = feed.continuation
                        publishHomeShelves(feed.shelves)
                    },
                    onFailure = {
                        if (generation == homeGeneration && homeState !is UiState.Success) {
                            homeState = UiState.Error(it.message ?: "Could not load Home")
                        }
                    },
                )
            }
            if (signedIn) {
                launch {
                    YtMusicRepository.homeRecentlyPlayed()
                        .onSuccess { shelf ->
                            if (generation != homeGeneration) return@onSuccess
                            homeRecentsLoading = false
                            shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
                        }
                        .onFailure { if (generation == homeGeneration) homeRecentsLoading = false }
                }
            }
            DesktopSearchClient.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                launch {
                    DesktopSearchClient.homeSupplement(browseId).onSuccess { shelves ->
                        if (generation == homeGeneration) publishHomeShelves(shelves)
                    }
                }
            }
        }
    }

    /** The next page of the core feed, as the list nears its end; a no-op once it has run dry. */
    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (homeLoadingMore || homeState !is UiState.Success) return
        val generation = homeGeneration
        homeLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreHome(token).fold(
                onSuccess = { feed ->
                    if (generation != homeGeneration) return@fold
                    val before = homeSeenTitles.size
                    publishHomeShelves(feed.shelves)
                    // A page with nothing new is the feed looping back on itself, not running dry.
                    homeContinuation = feed.continuation.takeIf { homeSeenTitles.size > before }
                },
                onFailure = { homeContinuation = null },
            )
            homeLoadingMore = false
        }
    }

    suspend fun loadMixes() {
        if (!youtubeSourceEnabled) {
            mixesState = UiState.Error("YouTube Music is disabled")
            return
        }
        val identity = "$youtubeSignedIn/${DesktopAccounts.activeAccountId()}/${DesktopAccounts.activeProfileId()}"
        mixesIdentity = identity
        if (!youtubeSignedIn) {
            mixesState = UiState.Success(emptyList())
            return
        }
        mixesState = UiState.Loading
        DesktopSearchClient.mixedForYouKeyed().fold(
            onSuccess = { mixes ->
                mixesState = UiState.Success(mixes)
            },
            onFailure = { err ->
                mixesState = UiState.Error(err.message ?: DesktopStrings["couldnt_load_tracks", "Could not load these tracks"])
            },
        )
    }

    LaunchedEffect(destination, mixesReloads, youtubeSignedIn, activeAccountId, activeProfileId) {
        val identity = "$youtubeSignedIn/$activeAccountId/$activeProfileId"
        if (mixesIdentity != null && mixesIdentity != identity) {
            mixesState = UiState.Loading
        }
        if (destination == DesktopDestination.RADIO &&
            (mixesState is UiState.Loading || mixesIdentity != identity || mixesReloads > 0)
        ) {
            loadMixes()
        }
    }

    fun currentTopPicksSlot(): String {
        val now = java.time.LocalDateTime.now()
        return "${now.toLocalDate()}T%02d".format(now.hour)
    }

    fun loadTopPicks(shelves: List<HomeShelf>, rebuild: Boolean = false) {
        val listenerKey = if (youtubeSignedIn) DesktopAccounts.activeAccountId() else null
        val identity = "$youtubeSignedIn/${DesktopAccounts.activeAccountId()}"
        val slot = currentTopPicksSlot()
        if (!rebuild && topPicksIdentity == identity && topPicksSlotShown == slot && topPicks != null) return
        topPicksJob?.cancel()
        topPicksIdentity = identity
        topPicksJob = scope.launch {
            val cacheFile = DesktopAnalysisRuntime.analysisHome().parentFile.resolve("top_picks.json")
            val stored = withContext(Dispatchers.IO) {
                runCatching { cacheFile.takeIf { it.exists() }?.readText() }.getOrNull()?.let(TopPicks::decode)
            }?.takeIf { it.listener == listenerKey && it.picks.isNotEmpty() }
            stored?.let { AppleArtistPhotos.seed(it.photos) }
            if (stored != null) {
                topPicks = stored.picks
                topPicksSlotShown = stored.day
            } else if (!rebuild) {
                topPicks = null
            }
            if (!rebuild && stored?.day == slot) {
                topPicksLoading = false
                return@launch
            }
            topPicksLoading = topPicks == null
            val month = withContext(Dispatchers.IO) {
                runCatching { DesktopListeningStats.summary(DesktopReplayPeriod.THIS_MONTH) }.getOrNull()
            }
            val localTaste = if (month != null) {
                TopPicks.LocalTaste(
                    artists = month.artists.take(10).map { ArtistRef(name = it.title, browseId = it.browseId) },
                    songs = month.songs.take(5).map { it.song },
                    genres = emptyList(),
                )
            } else {
                TopPicks.LocalTaste()
            }
            val built = runCatching { TopPicks.build(shelves, localTaste) }.getOrNull()?.takeIf { it.isNotEmpty() }
            topPicksLoading = false
            if (built == null) {
                if (topPicks == null) topPicks = emptyList()
                return@launch
            }
            topPicks = built
            topPicksSlotShown = slot
            withContext(Dispatchers.IO) {
                runCatching {
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(TopPicks.encode(TopPicks.Stored(listenerKey, slot, built, AppleArtistPhotos.snapshot())))
                }
            }
        }
    }

    LaunchedEffect(homeState, youtubeSignedIn, activeAccountId) {
        val shelves = (homeState as? UiState.Success)?.data ?: return@LaunchedEffect
        loadTopPicks(shelves)
    }

    fun openTopPick(pick: TopPick, label: String) {
        val browseId = pick.browseId
        if (pick.kind == TopPickKind.LATEST_RELEASE && browseId != null) {
            openCollection(
                browseId = browseId,
                fallback = BrowseItem(
                    browseId = browseId,
                    title = pick.title,
                    subtitle = pick.artistName.orEmpty(),
                    thumbnailUrl = pick.images.firstOrNull(),
                    type = BrowseType.ALBUM,
                ),
            )
            return
        }
        if (partyTrackChangeBlocked()) return
        scope.launch {
            val songs = TopPicks.queue(pick).getOrNull().orEmpty()
            if (songs.isEmpty()) {
                DesktopPlayerHost.showMessage(
                    DesktopStrings["couldnt_load_tracks", "Could not load these tracks"],
                )
                return@launch
            }
            if (partyTrackChangeBlocked()) return@launch
            val sourceId = pick.watch?.playlistId ?: pick.mood?.name
            val station = songs.map {
                canonicalSong(it).copy(
                    radioName = label,
                    playbackSource = label,
                    playbackSourceType = PlaybackSourceType.HOME,
                    playbackSourceId = sourceId,
                )
            }
            playSongs(station, 0, DesktopQueueSource(label, PlaybackSourceType.HOME, sourceId))
        }
    }

    fun playMood(mood: TopPickMood, title: String) {
        val hints = topPicks?.firstOrNull { it.kind == TopPickKind.MOOD }?.hints
        openTopPick(
            if (hints != null) TopPicks.moodStation(mood, hints) else TopPicks.moodStation(mood),
            title,
        )
    }

    // The tray icon speaks StatusNotifierItem rather than going through AWT, which only implements
    // the XEmbed tray a Wayland session does not have.
    val tray = remember {
        DesktopStatusNotifierController(
            // Clicking the icon is how the window comes back once it has been closed to the tray,
            // so it has to raise the window before it does anything inside it.
            onActivate = {
                DesktopWindowVisibility.show()
                overlays.nowPlaying = true
            },
            onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
        )
    }
    DisposableEffect(tray, trayIconEnabled) {
        if (trayIconEnabled) tray.start()
        onDispose { tray.stop() }
    }
    // Deliberately not a key on the effect above.
    DisposableEffect(trayIconEnabled, closeToTray) {
        // The listener's choice, with the tray as its precondition: hiding with no tray to come
        // back from leaves an audible process and no window.
        DesktopWindowVisibility.keepRunningWhenClosed = trayIconEnabled && closeToTray
        onDispose { DesktopWindowVisibility.keepRunningWhenClosed = false }
    }
    LaunchedEffect(tray, trayIconEnabled) {
        if (!trayIconEnabled) return@LaunchedEffect
        // The logo travels to the tray as pixels: a themed icon name would be whatever the user's
        // theme draws for a generic music player.
        runCatching { Res.readBytes("drawable/logo.svg") }.onSuccess(tray::setIcon)
    }
    LaunchedEffect(tray) {
        DesktopTrayMenu.bind(
            onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onOpenPlayer = {
                DesktopWindowVisibility.show()
                overlays.nowPlaying = true
            },
            onOpenSettings = {
                DesktopWindowVisibility.show()
                openSettings()
            },
            onQuit = {
                // Native work first: the audio thread and the media controls are inside FFmpeg,
                // WASAPI and WinRT, and exitProcess would unload those libraries under them.
                DesktopSingleInstance.release()
                playbackEngine.shutdown()
                DesktopWindowsMedia.stop()
                exitProcess(0)
            },
        )
        DesktopSingleInstance.bindUiActions(
            onShow = { DesktopWindowVisibility.show() },
            onAction = { cmd ->
                DesktopSingleInstance.runOnUiThread {
                    when (cmd) {
                        "--play-pause" -> if (selectedSong != null) togglePlayPauseFromUser()
                        "--next" -> playNext()
                        "--previous" -> playPrevious()
                        else -> DesktopWindowVisibility.show()
                    }
                }
            },
        )
    }
    LaunchedEffect(selectedSong, playback.isPlaying) {
        val title = selectedSong?.let { "${it.title} — ${it.artist}" }
        tray.update(title = title, isPlaying = playback.isPlaying)
        DesktopTrayMenu.publish(title = title, playing = playback.isPlaying)
    }

    LaunchedEffect(Unit) {
        // Unpacks the analyser and its models on first run.
        DesktopAnalysisRuntime.ensureStarted()
        DesktopHorizontalScroll.install()
        // A download record is a claim about a folder this app does not own, so it can outlive the
        // file it names or hold stale quality flags. Checked once at startup before local scan.
        val onDisk = withContext(Dispatchers.IO) { DesktopDownloadManager.verified(downloads) }
        if (onDisk != downloads) {
            if (onDisk.size != downloads.size) {
                DesktopTrackLog.log(
                    "downloads: ${downloads.size - onDisk.size} recorded file(s) are gone; forgetting them",
                )
            }
            downloads = onDisk
            persistence.saveDownloads(onDisk)
        }
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan(onDisk) }
        loadHome()
    }

    // The feed is the account's: signing in, out or across to another channel is a different Home,
    // and Recently Played only exists with an account behind it.
    LaunchedEffect(youtubeSignedIn, activeAccountId) {
        val identity = "${DesktopYouTubeAuth.isSignedIn}/${DesktopAccounts.activeAccountId()}"
        if (homeIdentity != null && identity != homeIdentity) loadHome()
    }

    // The mark on a downloaded row, for the shared pages.
    LaunchedEffect(downloads) {
        DesktopAppUiHost.downloaded.value = downloads.mapTo(HashSet(), Song::videoId)
    }

    // What reached disk goes into the library's own record, which is what the Downloads page and
    // every row's tick read from.
    LaunchedEffect(Unit) {
        DesktopDownloadQueue.finished.collect { saved ->
            downloads = (listOf(saved) + downloads.filterNot { it.videoId == saved.videoId }).take(200)
            persistence.saveDownloads(downloads)
            localMusicRevision++
        }
    }

    // The charts are period-filtered at read time, so a different period is a different read.
    LaunchedEffect(replayPeriod) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }

    // Re-scanned when the filter or the folder changes, never on first composition.
    LaunchedEffect(localMusicRevision) {
        if (localMusicRevision == 0) return@LaunchedEffect
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan(downloads) }
        DesktopLocalMusicWatcher.restart()
    }

    // A track dropped into the folder appears without a relaunch.
    LaunchedEffect(Unit) {
        DesktopLocalMusicWatcher.restart()
        DesktopLocalMusicWatcher.changes.collect {
            localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan(downloads) }
        }
    }

    suspend fun loadExplore() {
        if (!youtubeSourceEnabled) {
            exploreState = UiState.Error("YouTube Music is disabled")
            return
        }
        exploreState = UiState.Loading
        DesktopSearchClient.moodAndGenres().fold(
            onSuccess = { sections ->
                exploreState = if (sections.isEmpty()) {
                    UiState.Error("Nothing to explore right now")
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { exploreState = UiState.Error(it.message ?: "Could not load Explore") },
        )
    }

    LaunchedEffect(destination, exploreReloads) {
        if (destination == DesktopDestination.EXPLORE &&
            (exploreState is UiState.Loading || exploreReloads > 0)
        ) {
            loadExplore()
        }
    }

    // Category buttons carry no artwork of their own, so a few are resolved at a time from the
    // shelves they open.
    LaunchedEffect(exploreState) {
        val sections = (exploreState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (sections.all { section -> section.items.all { it.thumbnailUrl != null } }) return@LaunchedEffect
        val limiter = Semaphore(4)
        coroutineScope {
            sections.flatMap(MoodGenreSection::items)
                .filter { it.thumbnailUrl == null }
                .forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            DesktopSearchClient.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (exploreState as? UiState.Success)?.data ?: return@launch
                        exploreState = UiState.Success(
                            current.map { section ->
                                section.copy(
                                    items = section.items.map { entry ->
                                        if (entry.browseId == item.browseId && entry.params == item.params) {
                                            entry.copy(thumbnailUrl = artwork)
                                        } else {
                                            entry
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
        }
    }

    /** The next page of the open collection, appended in place. */
    fun loadMoreCollectionSongs() {
        val collection = openedCollection ?: return
        val token = collection.continuation ?: return
        if (collectionLoadingMore) return
        collectionLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreCollectionSongs(token, collection.songs.firstOrNull()?.artist)
                .onSuccess { (songs, next) ->
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        openedCollection = current.copy(
                            songs = (current.songs + songs).distinctBy(Song::videoId),
                            continuation = next,
                        )
                    }
                }
                .onFailure {
                    DesktopTrackLog.log("collection: no more rows for ${collection.browseId}: ${it.message}")
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        // Dropped rather than retried on scroll for ever: the trailing row is what
                        // asks, and leaving the token in place would have it ask again on every
                        // frame.
                        openedCollection = current.copy(continuation = null)
                    }
                }
            collectionLoadingMore = false
        }
    }

    fun renameOpenPlaylist(title: String) {
        val collection = openedCollection ?: return
        val local = playlists.firstOrNull { it.id == collection.browseId }
        if (local != null) {
            playlists = playlists.map { if (it.id == local.id) it.copy(title = title) else it }
            persistence.savePlaylists(playlists)
            openedCollection = openedCollection
                ?.takeIf { it.browseId == collection.browseId }
                ?.copy(title = title)
                ?: openedCollection
            closePlaylistDialogs()
            return
        }
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.renamePlaylist(collection.playlistId, title)
                .onSuccess {
                    // Renamed in place as well as re-fetched: the page that ordered it is still on
                    // screen showing the old name, and the library reload behind it takes a moment.
                    openedCollection = openedCollection
                        ?.takeIf { it.browseId == collection.browseId }
                        ?.copy(title = title)
                        ?: openedCollection
                    editPlaylistShelf { items ->
                        items.map { if (it.browseId == collection.browseId) it.copy(title = title) else it }
                    }
                    closePlaylistDialogs()
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not rename the playlist"
                }
        }
    }

    fun deleteOpenPlaylist() {
        val collection = openedCollection ?: return
        val local = playlists.firstOrNull { it.id == collection.browseId }
        if (local != null) {
            playlists = playlists.filterNot { it.id == local.id }
            persistence.savePlaylists(playlists)
            closePlaylistDialogs()
            if (openedCollection?.browseId == collection.browseId) {
                openedCollection = null
            }
            DesktopPlayerHost.showMessage(
                DesktopStrings.format("d_playlist_deleted", local.title),
            )
            return
        }
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.deletePlaylist(collection.playlistId)
                .onSuccess {
                    val title = collection.title
                    closePlaylistDialogs()
                    // Its page is the one open, and a deleted playlist has nothing left to show.
                    if (openedCollection?.browseId == collection.browseId) openedCollection = null
                    editPlaylistShelf { items -> items.filterNot { it.browseId == collection.browseId } }
                    libraryStale = true
                    DesktopPlayerHost.showMessage(
                        DesktopStrings.format("d_playlist_deleted", title),
                    )
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not delete the playlist"
                }
        }
    }

    /** Takes one row back out of the playlist it is being read in. */
    fun removeFromOpenPlaylist(song: Song) {
        val collection = openedCollection ?: return
        val local = playlists.firstOrNull { it.id == collection.browseId }
        if (local != null) {
            val updatedSongs = local.songs.filterNot { it.videoId == song.videoId }
            playlists = playlists.map { if (it.id == local.id) it.copy(songs = updatedSongs) else it }
            persistence.savePlaylists(playlists)
            val current = openedCollection
            if (current != null && current.browseId == collection.browseId) {
                openedCollection = current.copy(
                    songs = updatedSongs,
                    subtitle = "Playlist • ${updatedSongs.size} songs",
                )
            }
            return
        }
        val setVideoId = song.setVideoId ?: return
        scope.launch {
            DesktopSearchClient.removeFromPlaylist(
                collection.playlistId,
                listOf(setVideoId to song.videoId),
            ).onSuccess {
                val current = openedCollection
                if (current != null && current.browseId == collection.browseId) {
                    openedCollection = current.copy(
                        songs = current.songs.filterNot { it.setVideoId == setVideoId },
                    )
                }
            }.onFailure {
                DesktopTrackLog.log("playlist: could not remove ${song.videoId}: ${it.message}")
            }
        }
    }

    /**
     * Sends a new order for the playlist being read. [original] and [reordered] are the same whole
     * list of entries — as it was and as the user left it — since a reorder is sent as moves relative
     * to neighbours and needs every entry to move against.
     */
    fun reorderOpenPlaylist(original: List<Song>, reordered: List<Song>) {
        val collection = openedCollection ?: return
        val local = playlists.firstOrNull { it.id == collection.browseId }
        if (local != null) {
            playlists = playlists.map { if (it.id == local.id) it.copy(songs = reordered) else it }
            persistence.savePlaylists(playlists)
            closePlaylistDialogs()
            val current = openedCollection
            if (current != null && current.browseId == collection.browseId) {
                openedCollection = current.copy(songs = reordered)
            }
            return
        }
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.reorderPlaylist(
                collection.playlistId,
                current = original.mapNotNull { it.setVideoId },
                target = reordered.mapNotNull { it.setVideoId },
            ).onSuccess {
                val current = openedCollection
                if (current != null && current.browseId == collection.browseId) {
                    // The whole list arrived to be reordered, so the page now shows all of it and has
                    // no further page to ask for.
                    openedCollection = current.copy(songs = reordered, continuation = null)
                }
                closePlaylistDialogs()
                libraryStale = true
            }.onFailure {
                playlistBusy = false
                playlistError = it.message ?: "Could not reorder the playlist"
            }
        }
    }

    /**
     * The account's library, re-read whenever the session behind it changes — signing in, switching
     * channel, signing out.
     */
    LaunchedEffect(activeAccountId, activeProfileId, sessionRevision) {
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
        if (!DesktopYouTubeAuth.isSignedIn) {
            libraryState = UiState.Success(LibraryPage(emptyList(), emptyList(), emptyList()))
            return@LaunchedEffect
        }
        libraryState = UiState.Loading
        DesktopSearchClient.library().fold(
            onSuccess = { page ->
                // The continuation is a job to run, not page state — it is consumed below.
                libraryState = UiState.Success(page.copy(likedContinuation = null))
                DesktopTrackLog.log(
                    "library: ${page.likedSongs.size} liked, ${page.librarySongs.size} added, " +
                        "shelves ${page.shelves.joinToString { "${it.title}=${it.items.size}" }}",
                )
                val fromAccount = page.likedSongs.mapTo(HashSet(), Song::videoId)
                if (!likedIds.containsAll(fromAccount)) {
                    likedIds = likedIds + fromAccount
                    // Off the drawing thread: an account with a long Liked Music writes a few tens
                    // of kilobytes through the preference store, and that is a disk flush.
                    withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                }
                // Liked Music is published to the tab a page budget deep; the rest is followed here
                // for its ids alone, so a liked track past the budget still reads as liked.
                // Tied to the account it was fetched for: the liked set is one shared map, and a
                // sync still running after a switch would seed it with a stranger's likes.
                val syncingFor = activeAccountId
                page.likedContinuation?.let { token ->
                    likedSyncJob?.cancel()
                    likedSyncJob = scope.launch {
                        val extra = HashSet<String>()
                        DesktopSearchClient.syncLikedIds(token, onIds = { ids -> extra += ids })
                        if (syncingFor != DesktopAccounts.activeAccountId()) return@launch
                        if (!likedIds.containsAll(extra)) {
                            likedIds = likedIds + extra
                            withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                            DesktopTrackLog.log("library: liked sync added ${extra.size} ids past the first pages")
                        }
                    }
                }
            },
            onFailure = { libraryState = UiState.Error(it.message ?: "Could not load your library") },
        )
        // A session refused mid-flight is dropped by the request itself, so the answer to "are we
        // signed in" is only settled once the call is over.
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
    }

    fun here() = DesktopNavEntry(
        destination = destination,
        artist = openedArtist,
        collection = openedCollection,
        mood = selectedMoodGenre,
        homeMoodsAll = homeMoodsAllOpen,
        showAll = libraryShowAll,
        replay = overlays.replay,
        settings = overlays.settingsPage,
    )

    // Every move between places is a visit, however it was made — the sidebar, a card, a search
    // hit, the player's credits. The place left is what back returns to.
    LaunchedEffect(Unit) {
        var last: DesktopNavEntry? = null
        snapshotFlow { here() }.collect { now ->
            val before = last
            if (before != null && before.key != now.key) {
                if (navRestoring[0]) {
                    navRestoring[0] = false
                } else {
                    navHistory.add(before)
                    if (navHistory.size > NAV_HISTORY_LIMIT) navHistory.removeAt(0)
                }
            }
            // The newest copy of the same place, so an album that has paged in more tracks comes
            // back with them.
            last = now
        }
    }

    /** Back to the place before this one — the top bar's arrow, the mouse's back button, Alt+Left. */
    fun goBack() {
        val entry = navHistory.removeLastOrNull() ?: return
        if (entry.key == here().key) return
        navRestoring[0] = true
        destination = entry.destination
        overlays.replay = entry.replay
        overlays.settingsPage = entry.settings
        homeMoodsAllOpen = entry.homeMoodsAll
        collectionError = null
        openedCollection = entry.collection
        libraryShowAll = entry.showAll
        if (entry.mood != selectedMoodGenre) {
            selectedMoodGenre = entry.mood
            moodGenreShelves = UiState.Loading
        }
        if (entry.artist != openedArtist) {
            openedArtist = entry.artist
            if (entry.artist != null) {
                artistState = UiState.Loading
                artistReloads++
            }
        }
        if (entry.destination == DesktopDestination.LISTEN_NOW &&
            entry.artist == null &&
            entry.collection == null &&
            entry.mood == null &&
            !entry.homeMoodsAll &&
            entry.showAll == null &&
            entry.settings == null &&
            !entry.replay
        ) {
            navHistory.clear()
        }
    }

    /** Refreshes the content of the currently active view or page. */
    fun refreshCurrentPage() {
        when {
            openedArtist != null -> {
                artistState = UiState.Loading
                artistReloads++
            }
            openedCollection != null -> {
                openedCollection = openedCollection?.copy(loading = true)
                collectionError = null
                collectionReloads++
            }
            destination == DesktopDestination.EXPLORE && selectedMoodGenre != null -> {
                moodGenreShelves = UiState.Loading
                moodGenreReloads++
            }
            destination == DesktopDestination.EXPLORE -> {
                exploreState = UiState.Loading
                exploreReloads++
            }
            destination == DesktopDestination.RADIO -> {
                mixesState = UiState.Loading
                mixesReloads++
            }
            destination == DesktopDestination.LISTEN_NOW -> {
                loadHome()
            }
            destination == DesktopDestination.LIBRARY -> {
                reloadLibrary()
            }
            destination == DesktopDestination.SEARCH -> {
                if (query.isNotBlank()) search()
            }
            destination == DesktopDestination.HISTORY -> {
                history = persistence.history()
                if (youtubeSignedIn) {
                    scope.launch { DesktopSearchClient.history().onSuccess { remoteHistory = it } }
                }
            }
        }
    }

    fun openMoodGenre(item: MoodGenre) {
        selectedMoodGenre = item
        moodGenreShelves = UiState.Loading
    }

    LaunchedEffect(selectedMoodGenre, moodGenreReloads) {
        val item = selectedMoodGenre ?: return@LaunchedEffect
        if (moodGenreShelves !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.moodGenreShelves(item.browseId, item.params).fold(
            onSuccess = { shelves ->
                moodGenreShelves = if (shelves.isEmpty()) {
                    UiState.Error("Nothing to explore here yet")
                } else {
                    UiState.Success(shelves)
                }
            },
            onFailure = { moodGenreShelves = UiState.Error(it.message ?: "Could not load this category") },
        )
    }

    // The menu the shared player's "…" opens.
    var playerMenuOpen by remember(selectedSong?.videoId) { mutableStateOf(false) }
    // The player's own overflow (volume, the lyrics/output/queue row, the lyric
    // footer), raised from the song menu's "More…" and drawn by the shared
    // player. Held here so the desktop song menu can open it.
    val playerOverflowOpen = remember(selectedSong?.videoId) { mutableStateOf(false) }
    // Requests the song menu sends the player (switch lyrics/queue, translate),
    // flipped here and consumed by it, and the player's state mirrored back so the
    // menu's rows say what is on screen. Not keyed to the track: the player's own
    // panel and translation state outlive a skip, so copies recreated per track
    // would contradict it until it next changed.
    val playerQueueToggle = remember { mutableStateOf(false) }
    val playerQueueShowing = remember { mutableStateOf(false) }
    val playerTranslateToggle = remember { mutableStateOf(false) }
    val playerShowingTranslation = remember { mutableStateOf(false) }
    // Where the player's "…" is, in window coordinates, so its menu hangs off it.
    val playerMenuAnchor = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var sidebarMinimized by remember { mutableStateOf(false) }
    val playerHaze = remember { HazeState() }
    val playerBackdrop = rememberDesktopLayerBackdrop(
        onDraw = {
            drawRect(Color.Black)
            drawContent()
        },
    )
    val sheetOrigin = remember { mutableStateOf(Offset.Zero) }

    /** The song menu's verbs for [song], as the player sheet offers them. */
    fun playerSongActions(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        // Both leave the player, the way opening a page from Android's sheet collapses it.
        onOpenAlbum = { id ->
            overlays.nowPlaying = false
            openAlbum(id)
        },
        onOpenArtist = { id ->
            overlays.nowPlaying = false
            openArtist(id, song.artist)
        },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    // What the shared player reads as settings, kept in step with the window's own.
    SideEffect {
        DesktopPlayerSettings.animatedCanvas.value = animatedCanvas
        DesktopPlayerSettings.fullBleedArtwork.value = fullBleedArtwork
        DesktopPlayerSettings.legacyMeshGradient.value = legacyMeshGradient
        DesktopPlayerSettings.playerTheme.value = if (legacyMeshGradient) com.music.bitchord.data.settings.PlayerTheme.LEGACY else com.music.bitchord.data.settings.PlayerTheme.DEFAULT
        DesktopPlayerSettings.lyricsBlur.value = lyricsBlur
        DesktopPlayerSettings.syncedLyrics.value = syncedLyrics
        DesktopPlayerSettings.showNerdStats.value = showNerdStats
        DesktopPlayerSettings.showBestPart.value = showBestPart
        DesktopPlayerSettings.showTotalDuration.value = showTotalDuration
        DesktopPlayerSettings.autoMixArtwork.value = DesktopPlayerSettings.autoMixArtwork.value.copy(
            artTransition = autoMixArtTransition,
            miniPlayerTransition = autoMixMiniArtTransition,
        )
        DesktopPlayerSettings.smartFadeEnabled.value = automix
        DesktopPlayerSettings.smartAnalysis.value = playback.smartAnalysis
        DesktopPlayerSettings.smartMixInProgress.value = playback.mixing
        DesktopPlayerSettings.smartTransitionWindow.value = playback.transitionWindow
        DesktopPlayerSettings.lyricsSourceOrder.value =
            DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn).mapNotNull(::lyricsSourceNamed)
        DesktopPlayerHost.setSoftwareVolume(volume)
        DesktopPlayerHost.onSoftwareVolumeChange = {
            volume = it
            persistence.saveString("volume", it.toString())
        }
    }

    // Keep persisted history/playlists synced with freshly probed local audio metadata.
    LaunchedEffect(localSongs) {
        val refreshedHistory = DesktopLocalMetadata.enrichWithLocalCatalog(history, localSongs)
        if (refreshedHistory != history) {
            history = refreshedHistory
            persistence.saveHistory(refreshedHistory)
        }
        val refreshedPlaylists = playlists.map { playlist ->
            val updatedSongs = DesktopLocalMetadata.enrichWithLocalCatalog(playlist.songs, localSongs)
            if (updatedSongs == playlist.songs) playlist else playlist.copy(songs = updatedSongs)
        }
        if (refreshedPlaylists != playlists) {
            playlists = refreshedPlaylists
            persistence.savePlaylists(refreshedPlaylists)
        }
    }

    // The engine's measured stream, as the player's quality badge and stats line read it.
    LaunchedEffect(playback.streamFormat, playback.streamSourceId) {
        val format = playback.streamFormat
        NerdStats.current.value = format?.nerdSnapshot(playback.streamSourceId)
        val played = playback.song
        if (played != null && format != null && format.isLossless) {
            val resolvedQuality = format.codecLabel ?: played.sourceQuality ?: "LOSSLESS"
            val resolvedHiRes = format.isHiRes
            val refreshedHistory = history.map { item ->
                if (item.videoId == played.videoId && (item.sourceHiRes != resolvedHiRes || item.sourceQuality.isNullOrBlank())) {
                    item.copy(
                        sourceQuality = item.sourceQuality ?: resolvedQuality,
                        sourceHiRes = resolvedHiRes,
                    )
                } else {
                    item
                }
            }
            if (refreshedHistory != history) {
                history = refreshedHistory
                persistence.saveHistory(refreshedHistory)
            }
        }
    }
    LaunchedEffect(playback.searchingBetter, selectedSong?.videoId) {
        NerdStats.racingLossless.value = selectedSong?.videoId
            ?.takeIf { playback.searchingBetter }
            ?.let(::setOf)
            .orEmpty()
    }
    val bitPerfectStatus by DesktopBitPerfect.status.collectAsState()
    LaunchedEffect(playback.streamFormat, playback.isPlaying, bitPerfectStatus) {
        DesktopPlayerHost.pipeline.value = playbackEngine.pipeline()
    }
    DesktopPlayerHost.pipelineDialog = { hazeState, playing, onDismiss ->
        DesktopAudioPipelineDialog(
            format = playback.streamFormat,
            sourceName = playback.streamSourceId?.let { id ->
                sourceConfigs.firstOrNull { it.id == id }?.displayName
            },
            pipeline = playbackEngine.pipeline(),
            isPlaying = playing || playback.isPlaying,
            hazeState = hazeState,
            onDismiss = onDismiss,
        )
    }

    // What the player and the lyrics column beside the page both read, worked out once for the two.
    val sharedLyrics = lyrics?.lines
    val sharedLyricsSource = remember(lyrics) { lyrics?.source?.let(::lyricsSourceNamed) }
    val sharedLyricsUnavailable = !lyricsLoading && sharedLyrics.isNullOrEmpty()
    val providerStates = remember(lyricsOrder, lyricsOn, sharedLyricsSource, lyricsLoading) {
        DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
            .mapNotNull(::lyricsSourceNamed)
            .associateWith { source ->
                when {
                    source == sharedLyricsSource -> LyricsProviderState.FOUND
                    lyricsProviderResults.value[source] != null -> lyricsProviderResults.value.getValue(source)
                    lyricsLoading -> LyricsProviderState.FETCHING
                    else -> LyricsProviderState.NOT_FETCHED
                }
            }
    }

    // Every provider the song menu's lyrics drawer lists — all of them, in the
    // saved order, not just the enabled ones — each carrying what its last race
    // turned up so the row can read "Found", "Not found" or "Searching…". The
    // winner is the one the sheet shows now.
    val menuLyricsProviders = remember(
        lyricsOrder,
        lyricsOn,
        lyricsOnly,
        lyricsLoading,
        lyrics?.source,
        lyricsProviderResults.value,
    ) {
        val currentLabel = lyrics?.source
        val asked = lyricsOnly?.let { listOf(it) } ?: DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
        lyricsOrder.map { label ->
            val source = lyricsSourceNamed(label)
            val state = when {
                label == currentLabel -> LyricsProviderState.FOUND
                source != null && lyricsProviderResults.value[source] != null -> lyricsProviderResults.value.getValue(source)
                lyricsLoading && label in asked -> LyricsProviderState.FETCHING
                else -> LyricsProviderState.NOT_FETCHED
            }
            DesktopLyricsProviderRow(label, state, label == currentLabel)
        }
    }

    // The queue's edits, for the player's queue and the queue column alike.
    fun removeFromQueue(at: Int) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.removeAt(at)
        if (edited !== liveQueue) {
            liveQueue = edited
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    fun removeEntriesFromQueue(identities: List<QueueEntryIdentity>) {
        if (DesktopListenTogether.state.value.controlsLocked || identities.isEmpty()) return
        val currentSongs = liveQueue.songs
        val currentIndex = liveQueue.index
        val toRemoveIndices = mutableSetOf<Int>()
        for (identity in identities) {
            var ordinalCount = 0
            for (i in currentSongs.indices) {
                if (i == currentIndex) continue
                val s = currentSongs[i]
                if (s.videoId == identity.mediaId && (identity.queueEntryId == null || s.queueEntryId == identity.queueEntryId)) {
                    if (ordinalCount == identity.occurrence) {
                        toRemoveIndices.add(i)
                        break
                    }
                    ordinalCount++
                }
            }
        }
        if (toRemoveIndices.isNotEmpty()) {
            val newSongs = currentSongs.filterIndexed { index, _ -> index !in toRemoveIndices }
            val newIndex = if (currentIndex in currentSongs.indices) {
                val removedBefore = toRemoveIndices.count { it < currentIndex }
                (currentIndex - removedBefore).coerceIn(0, (newSongs.size - 1).coerceAtLeast(0))
            } else 0
            liveQueue = liveQueue.copy(songs = newSongs, index = newIndex)
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.move(from, to)
        if (edited !== liveQueue) {
            liveQueue = edited
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    // Clears what the listener queued by hand, as the phone's Clear does; the album and AutoPlay
    // stay where they are.
    fun clearQueue() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.withoutUserQueue()
        if (edited === liveQueue) return
        liveQueue = edited
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    fun seekPlayer(target: Long) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val duration = playback.durationMs
        playbackEngine.seekTo(
            if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L),
        )
        partySyncHolder[0]?.onLocalIntent()
    }

    val themeMode by DesktopAppearanceSettings.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = resolveDarkTheme(themeMode, systemDark)
    val activePalette = if (isDarkTheme) DesktopDarkPalette else DesktopLightPalette

    MaterialTheme(
        colorScheme = desktopColorSchemeFor(isDarkTheme),
        typography = desktopTypography(),
    ) {
        val ambientBackdrop by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
        val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()
        val smoothTransitions by DesktopAppearanceSettings.smoothTransitions.collectAsState()
        val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
        val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
        val glassClickAnimations by DesktopAppearanceSettings.glassClickAnimations.collectAsState()
        val highPerformanceMode by DesktopAppearanceSettings.highPerformanceMode.collectAsState()
        val performanceRefreshRate by DesktopAppearanceSettings.performanceRefreshRate.collectAsState()
        LaunchedEffect(highPerformanceMode, performanceRefreshRate) {
            DesktopDisplayPerformance.applyFramePacing(highPerformanceMode, performanceRefreshRate)
        }
        val windowActions = LocalDesktopWindowActions.current
        val glassSurfaceOn = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
        val glassClickOn = glassSurfaceOn && glassClickAnimations && !reduceAnimation
        CompositionLocalProvider(
            LocalDesktopPalette provides activePalette,
            LocalContentColor provides activePalette.ink,
            LocalNowPlaying provides selectedSong,
            LocalDesktopIsPlaying provides playback.isPlaying,
            LocalPlayingBarsMotion provides (playback.isPlaying && !overlays.nowPlaying && !reduceAnimation),
            LocalPillSegmentedGlassStyle provides PillSegmentedGlassStyle(
                enabled = glassSurfaceOn,
                clickAnimation = glassClickOn,
            ),
            LocalSearchFieldGlassStyle provides SearchFieldGlassStyle(
                enabled = glassSurfaceOn,
                clickAnimation = glassClickOn,
            ),
        ) {
            DesktopFrame(
                // The phone's page is black, not the near-black of its cards.
                containerColor = DesktopBackground,
                // Only Replay dresses itself unless ambient backdrop is on; everywhere else the chrome sits on the plain surface.
                backdrop = { transparentBase ->
                    val ambientArtworkUrl = replaySummary.songs.firstOrNull()?.song?.thumbnailUrl
                        .takeIf { overlays.replay }
                        ?: selectedSong?.thumbnailUrl.takeIf { ambientBackdrop }
                    DesktopPageBackdrop(
                        artworkUrl = ambientArtworkUrl,
                        transparentBase = transparentBase,
                    )
                },
                modifier = Modifier
                    .focusRequester(rootFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    val isModifierPressed = event.isCtrlPressed || event.isMetaPressed
                    val isTypingContext = overlays.settingsPage != null ||
                        destination == DesktopDestination.SEARCH ||
                        overlays.playlistDialog ||
                        overlays.localPlaylistDialog ||
                        playlistTarget != null ||
                        playlistTargets.isNotEmpty() ||
                        overlays.rename ||
                        overlays.delete ||
                        overlays.reorder ||
                        overlays.lastfmLogin ||
                        overlays.listenBrainzToken ||
                        overlays.discordToken ||
                        overlays.signIn
                    when {
                        // Media keys from keyboard / OS
                        event.key == Key.MediaPlayPause -> {
                            if (selectedSong != null) togglePlayPauseFromUser()
                            true
                        }
                        event.key == Key.MediaPlay -> {
                            playFromUser()
                            true
                        }
                        event.key == Key.MediaPause || event.key == Key.MediaStop -> {
                            pauseFromUser()
                            true
                        }
                        event.key == Key.MediaNext -> {
                            playNext()
                            true
                        }
                        event.key == Key.MediaPrevious -> {
                            playPrevious()
                            true
                        }
                        // Spacebar: Play / Pause (disabled when in typing context such as settings search or search tab)
                        event.key == Key.Spacebar && !isTypingContext -> {
                            if (selectedSong != null) togglePlayPauseFromUser()
                            true
                        }
                        // Next / Previous track with Ctrl/Cmd + Right/Left
                        (event.key == Key.DirectionRight && isModifierPressed) -> {
                            playNext()
                            true
                        }
                        (event.key == Key.DirectionLeft && isModifierPressed) -> {
                            playPrevious()
                            true
                        }
                        // Seeking +/- 5 seconds with Arrow keys
                        (event.key == Key.DirectionRight && (event.isAltPressed || overlays.nowPlaying)) -> {
                            val duration = playbackEngine.state.value.durationMs
                            val target = (playbackEngine.state.value.positionMs + 5_000L).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
                            seekPlayer(target)
                            true
                        }
                        (event.key == Key.DirectionLeft && (event.isAltPressed || overlays.nowPlaying)) -> {
                            val target = (playbackEngine.state.value.positionMs - 5_000L).coerceAtLeast(0L)
                            seekPlayer(target)
                            true
                        }
                        // Volume +/- 5% with Arrow Up/Down
                        (event.key == Key.DirectionUp && (isModifierPressed || event.isAltPressed || overlays.nowPlaying)) -> {
                            if (DesktopDacVolume.level.value != null) {
                                DesktopDacVolume.step(0.05f)
                            } else if (!DesktopBitPerfect.status.value.active) {
                                val newVol = (volume + 0.05f).coerceIn(0f, 1f)
                                volume = newVol
                                persistence.saveString("volume", newVol.toString())
                            }
                            true
                        }
                        (event.key == Key.DirectionDown && (isModifierPressed || event.isAltPressed || overlays.nowPlaying)) -> {
                            if (DesktopDacVolume.level.value != null) {
                                DesktopDacVolume.step(-0.05f)
                            } else if (!DesktopBitPerfect.status.value.active) {
                                val newVol = (volume - 0.05f).coerceIn(0f, 1f)
                                volume = newVol
                                persistence.saveString("volume", newVol.toString())
                            }
                            true
                        }
                        // Mute toggle with M
                        (event.key == Key.M && !isModifierPressed && !isTypingContext) -> {
                            if (DesktopDacVolume.level.value != null) {
                                DesktopDacVolume.toggleMute()
                            } else if (!DesktopBitPerfect.status.value.active) {
                                val newVol = if (volume > 0f) 0f else 1f
                                volume = newVol
                                persistence.saveString("volume", newVol.toString())
                            }
                            true
                        }
                        // Fullscreen toggle via F11 or F
                        event.key == Key.F11 || (event.key == Key.F && !isModifierPressed && overlays.nowPlaying && !isTypingContext) -> {
                            DesktopWindowMode.toggle()
                            true
                        }
                        // Refresh page with F5 or Ctrl+R / Cmd+R
                        event.key == Key.F5 || (event.key == Key.R && isModifierPressed) -> {
                            refreshCurrentPage()
                            true
                        }
                        event.key == Key.DirectionLeft && event.isAltPressed && !overlays.nowPlaying -> {
                            goBack()
                            true
                        }
                        isModifierPressed && event.key == Key.Comma -> {
                            openSettings()
                            true
                        }
                        isModifierPressed && event.key == Key.F -> {
                            destination = DesktopDestination.SEARCH
                            searchFocusRequested = true
                            true
                        }
                        isModifierPressed && event.key == Key.W -> {
                            windowActions?.close?.invoke()
                            true
                        }
                        isModifierPressed && event.key == Key.M -> {
                            windowActions?.minimize?.invoke()
                            true
                        }
                        // One layer at a time, innermost first: the player's own side panel, then
                        // the player.
                        event.key == Key.Escape -> when {
                            playlistTarget != null || playlistTargets.isNotEmpty() || overlays.playlistDialog || overlays.localPlaylistDialog || overlays.rename || overlays.delete || overlays.reorder -> {
                                closePlaylistDialogs()
                                true
                            }
                            overlays.accounts -> {
                                overlays.accounts = false
                                true
                            }
                            overlays.signIn -> {
                                overlays.signIn = false
                                true
                            }
                            // The player's own layers first — the lyrics, the queue, a
                            // drawer — in the order Android's back reaches them.
                            overlays.nowPlaying && PlayerBack.dispatch() -> true
                            overlays.nowPlaying -> {
                                overlays.nowPlaying = false
                                true
                            }
                            overlays.sidePanel != null -> {
                                overlays.sidePanel = null
                                true
                            }
                            // A page of Settings steps back the way the back button does, unless
                            // one of its prompts is up over it.
                            overlays.settingsPage != null &&
                                !overlays.lastfmLogin && !overlays.listenBrainzToken && !overlays.discordToken -> {
                                goBack()
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                },
                topBar = {},
                sidebar = { compact ->
                    DesktopSidebar(
                        compact = compact,
                        minimized = compact || sidebarMinimized,
                        onToggleMinimize = { sidebarMinimized = !sidebarMinimized },
                        destination = destination,
                        settingsOpen = overlays.settingsPage != null,
                        accountPlaylists = sidebarAccountPlaylists,
                        localPlaylists = playlists,
                        openedCollectionId = openedCollection?.browseId,
                        query = query,
                        onQueryChange = { text ->
                            editQuery(text)
                            if (destination != DesktopDestination.SEARCH || overlays.settingsPage != null) {
                                selectDestination(DesktopDestination.SEARCH)
                            }
                        },
                        onSearch = ::search,
                        onDestinationSelected = ::selectDestination,
                        onOpenAccountPlaylist = { openShelfItem(it, PLAYLISTS_SHELF) },
                        onOpenLocalPlaylist = ::openPlaylist,
                        onDeleteAccountPlaylist = { playlist ->
                            openShelfItem(playlist, PLAYLISTS_SHELF)
                            overlays.delete = true
                        },
                        onRenameLocalPlaylist = { playlist ->
                            openPlaylist(playlist)
                            overlays.rename = true
                        },
                        onDeleteLocalPlaylist = { playlist ->
                            openPlaylist(playlist)
                            overlays.delete = true
                        },
                        onOpenLikedMusic = ::openLikedMusic,
                        onOpenSettings = ::openSettings,
                        focusSearch = searchFocusRequested,
                        onSearchFocused = { searchFocusRequested = false },
                        canGoBack = navHistory.isNotEmpty() &&
                            !(destination == DesktopDestination.LISTEN_NOW &&
                                openedArtist == null &&
                                openedCollection == null &&
                                selectedMoodGenre == null &&
                                !homeMoodsAllOpen &&
                                libraryShowAll == null &&
                                overlays.settingsPage == null &&
                                !overlays.replay),
                        onBack = ::goBack,
                        onRefresh = ::refreshCurrentPage,
                        accountAvatar = activeAccount?.profiles?.firstOrNull { it.profileId == activeProfileId }?.avatar?.takeIf { it.isNotBlank() }
                            ?: activeAccount?.avatar?.takeIf { it.isNotBlank() }
                            ?: activeAccount?.profiles?.firstOrNull()?.avatar?.takeIf { it.isNotBlank() },
                        accountName = activeAccount?.profiles?.firstOrNull { it.profileId == activeProfileId }?.name?.takeIf { it.isNotBlank() }
                            ?: activeAccount?.name?.takeIf { it.isNotBlank() },
                        onOpenAccounts = { DesktopTrackLog.log("accounts: opening the switcher"); overlays.accounts = true },
                    )
                },
                bottomBar = { _ ->
                    DesktopAppleMusicBottomBar(
                        song = effectiveSong,
                        isPlaying = playback.isPlaying,
                        isLoading = playback.isLoading,
                        previousEnabled = liveQueue.hasPrevious ||
                            playback.positionMs > BACK_RESTARTS_AFTER_MS,
                        progress = if (playback.durationMs > 0L) {
                            playback.positionMs.toFloat() / playback.durationMs.toFloat()
                        } else {
                            0f
                        },
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        onSeek = { fraction ->
                            if (playback.durationMs > 0L) {
                                seekPlayer((fraction * playback.durationMs).toLong().coerceIn(0L, playback.durationMs))
                            }
                        },
                        volume = playback.volume,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                        onPrevious = ::playPrevious,
                        onNext = ::playNext,
                        onShuffleChange = ::setShuffle,
                        onRepeatModeChange = {
                            repeatMode = it
                            persistence.saveString("repeat_mode", it.name)
                        },
                        onOpenNowPlaying = { overlays.nowPlaying = true },
                        onOpenArtist = { id, name ->
                            if (!id.isNullOrBlank()) openArtist(id, name) else openArtistByName(name)
                        },
                        onOpenAlbum = { id, name ->
                            if (!id.isNullOrBlank()) {
                                openAlbum(id)
                            } else if (!name.isNullOrBlank()) {
                                openAlbumByName(name)
                            }
                        },
                        onVolumeChange = {
                            volume = it
                            persistence.saveString("volume", it.toString())
                        },
                        onOpenAudioOutput = { overlays.audioOutput = true },
                        onOpenLyrics = { overlays.toggleSidePanel(DesktopSidePanel.LYRICS) },
                        onOpenQueue = { overlays.toggleSidePanel(DesktopSidePanel.QUEUE) },
                        sidePanel = overlays.sidePanel,
                        menu = effectiveSong?.let { s ->
                            {
                                DesktopSongMenuAnchor(
                                    song = s,
                                    liked = s.videoId in likedIds,
                                    actions = songActionsFor(s),
                                    onToggleLike = { toggleLike(s) },
                                    onRevertToOriginal = null,
                                    onUpgradeQuality = null,
                                    onOpenLyrics = { overlays.toggleSidePanel(DesktopSidePanel.LYRICS) },
                                    onOpenQueue = { overlays.toggleSidePanel(DesktopSidePanel.QUEUE) },
                                )
                            }
                        },
                    )
                },
                trailing = {
                    val current = selectedSong
                    DesktopSidePanelColumn(
                        panel = overlays.sidePanel.takeIf { current != null },
                        onClose = { overlays.sidePanel = null },
                    ) { which ->
                        if (current == null) return@DesktopSidePanelColumn
                        when (which) {
                            DesktopSidePanel.LYRICS -> LyricsSidePanel(
                                song = current,
                                isPlaying = playback.isPlaying,
                                position = playerPosition,
                                lyrics = sharedLyrics,
                                lyricsSource = sharedLyricsSource,
                                lyricsProviderStates = providerStates,
                                onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                                lyricsUnavailable = sharedLyricsUnavailable,
                                onSeek = ::seekPlayer,
                                modifier = Modifier.fillMaxSize(),
                            )
                            DesktopSidePanel.QUEUE -> QueueSidePanel(
                                queue = liveQueue.songs,
                                queueIndex = liveQueue.index,
                                autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                                onJumpTo = ::playQueueIndex,
                                onRemove = ::removeFromQueue,
                                onMove = ::moveInQueue,
                                onClear = ::clearQueue,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
                overlay = {
                    val playerSheetVisible = overlays.nowPlaying && selectedSong != null
                    DesktopPlayerSheet(
                        visible = playerSheetVisible,
                        onDismiss = {
                            DesktopWindowMode.exit()
                            overlays.nowPlaying = false
                        },
                        playerHaze = playerHaze,
                        playerBackdrop = playerBackdrop,
                    ) { windowWidth, windowHeight ->
                        val current = selectedSong ?: return@DesktopPlayerSheet
                        val playerSong = trackLinks?.takeIf { it.videoId == current.videoId }
                            ?.let { extra ->
                                current.copy(
                                    artistId = current.artistId ?: extra.artistId,
                                    // Same as on the phone: the watch-queue lookup
                                    // is what carries a channel per credited
                                    // artist, so it is what makes every name in
                                    // the credit line openable.
                                    artists = current.artists.ifEmpty { extra.artists },
                                    albumId = current.albumId ?: extra.albumId,
                                    albumName = current.albumName ?: extra.albumName,
                                )
                            }
                            ?: current
                        val playerLightTheme = !LocalDesktopPalette.current.isDark
                        val playerInk = if (playerLightTheme) Color(0xFF0A0A0C) else Color.White
                        CompositionLocalProvider(
                            LocalContentColor provides playerInk,
                            com.music.bitchord.ui.player.LocalPlayerSurfaceInk provides playerInk,
                            com.music.bitchord.ui.player.LocalPlayerLightTheme provides playerLightTheme,
                        ) {
                        NowPlayingScreen(
                            song = playerSong,
                            isPlaying = playback.isPlaying,
                            isLoading = playback.isLoading,
                            position = playerPosition,
                            durationMs = playback.durationMs,
                            audioVersionSwitching = false,
                            qualityUpgraded = false,
                            queue = liveQueue.songs,
                            queueIndex = liveQueue.index,
                            hasPrevious = liveQueue.hasPrevious,
                            hasNext = liveQueue.hasNext,
                            repeatMode = when (repeatMode) {
                                DesktopRepeatMode.OFF -> RepeatModes.OFF
                                DesktopRepeatMode.ONE -> RepeatModes.ONE
                                DesktopRepeatMode.ALL -> RepeatModes.ALL
                            },
                            shuffleEnabled = shuffle,
                            autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                            signedIn = youtubeSignedIn,
                            accountName = activeAccount?.profiles?.firstOrNull { it.profileId == activeProfileId }?.name?.takeIf(String::isNotBlank)
                                ?: activeAccount?.name?.takeIf(String::isNotBlank),
                            likeStatus = when (current.videoId) {
                                in likedIds -> LikeStatus.LIKE
                                in dislikedIds -> LikeStatus.DISLIKE
                                else -> LikeStatus.INDIFFERENT
                            },
                            onToggleLike = { toggleLike(current) },
                            onPlayPause = ::togglePlayPauseFromUser,
                            onNext = ::playNext,
                            onPrevious = ::playPrevious,
                            previousRestarts = {
                                playbackEngine.state.value.positionMs > BACK_RESTARTS_AFTER_MS || !liveQueue.hasPrevious
                            },
                            currentPlaybackSong = { playbackEngine.state.value.song ?: current },
                            onBlockedControl = {
                                DesktopPlayerHost.showMessage("Only the party host can change playback")
                            },
                            onSeek = ::seekPlayer,
                            onSeekFraction = { fraction ->
                                val duration = playbackEngine.state.value.durationMs
                                if (duration > 0) seekPlayer((fraction * duration).toLong())
                            },
                            onToggleShuffle = { setShuffle(!shuffle) },
                            // The phone's order: off, all, one.
                            onCycleRepeat = {
                                repeatMode = when (repeatMode) {
                                    DesktopRepeatMode.OFF -> DesktopRepeatMode.ALL
                                    DesktopRepeatMode.ALL -> DesktopRepeatMode.ONE
                                    DesktopRepeatMode.ONE -> DesktopRepeatMode.OFF
                                }
                                persistence.saveString("repeat_mode", repeatMode.name)
                            },
                            onToggleAutoplay = {
                                setAutoplay(!DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay))
                            },
                            onJumpTo = ::playQueueIndex,
                            onRemoveFromQueue = ::removeFromQueue,
                            onMoveInQueue = ::moveInQueue,
                            onClearQueue = ::clearQueue,
                            onQueueBulkAction = { songs, action ->
                                when (action) {
                                    QueueBulkAction.PLAYLIST -> {
                                        playlistTargets = songs
                                        overlays.playlistDialog = true
                                        true
                                    }
                                    QueueBulkAction.DOWNLOAD -> {
                                        downloadAll(songs)
                                        true
                                    }
                                }
                            },
                            onQueueRemoveEntries = { identities ->
                                removeEntriesFromQueue(identities)
                            },
                            onQueueAddAllToPlaylist = { songs ->
                                playlistTargets = songs
                                overlays.playlistDialog = true
                            },
                            onOpenMenu = {
                                playerMenuAnchor.value = com.music.bitchord.ui.components.MenuAnchor.consume()
                                playerMenuOpen = true
                            },
                            onOpenAlbum = { id ->
                                overlays.nowPlaying = false
                                openAlbum(id)
                            },
                            // The lead credit carries the track's own channel
                            // id and opens straight away; anyone else on the
                            // line has no id, so the page is found from the
                            // name — and only when the answer is that name
                            // exactly, so a fragment like "2115" opens nothing
                            // rather than White 2115.
                            onOpenArtist = { id, name ->
                                overlays.nowPlaying = false
                                if (id != null) {
                                    openArtist(id, name)
                                } else {
                                    scope.launch {
                                        YtMusicRepository.findArtistPageId(name).getOrNull()
                                            ?.let { found -> openArtist(found, name) }
                                    }
                                }
                            },
                            onOpenPlaybackSource = {
                                val id = playerSong.playbackSourceId
                                overlays.nowPlaying = false
                                when {
                                    playerSong.playbackSourceType == PlaybackSourceType.HISTORY ->
                                        selectDestination(DesktopDestination.HISTORY)
                                    playerSong.playbackSourceType == PlaybackSourceType.BROWSE && id != null -> when {
                                        id.startsWith("local:") -> selectDestination(DesktopDestination.LOCAL_MUSIC)
                                        id.startsWith("downloaded:") -> selectDestination(DesktopDestination.DOWNLOADS)
                                        else -> playlists.firstOrNull { it.id == id }?.let(::openPlaylist) ?: openAlbum(id)
                                    }
                                }
                            },
                            onListenTogether = {
                                overlays.nowPlaying = false
                                overlays.listenTogether = true
                            },
                            lyrics = sharedLyrics,
                            lyricsSource = sharedLyricsSource,
                            lyricsProviderStates = providerStates,
                            onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                            lyricsUnavailable = sharedLyricsUnavailable,
                            lyricsOffsetOpen = false,
                            onDismissLyricsOffset = {},
                            windowWidth = windowWidth,
                            windowHeight = windowHeight,
                            menuOpen = { playerMenuOpen },
                        )
                        }
                        Spacer(
                            Modifier
                                .matchParentSize()
                                .onGloballyPositioned { sheetOrigin.value = it.positionInWindow() },
                        )
                        val message by DesktopPlayerHost.messages.collectAsState()
                        LaunchedEffect(message) {
                            if (message != null) {
                                delay(2_500)
                                DesktopPlayerHost.messages.value = null
                            }
                        }
                        message?.let { text ->
                            Text(
                                text = text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                    val activeOverlayHaze = if (playerSheetVisible) playerHaze else LocalDesktopHaze.current
                    val activeOverlayBackdrop = if (playerSheetVisible) playerBackdrop else LocalDesktopBackdrop.current
                    CompositionLocalProvider(
                        LocalDesktopHaze provides activeOverlayHaze,
                        LocalDesktopBackdrop provides activeOverlayBackdrop,
                    ) {
                        if (playerSheetVisible && playerMenuOpen) {
                            val current = selectedSong
                            if (current != null) {
                                val playerSong = trackLinks?.takeIf { it.videoId == current.videoId }
                                    ?.let { extra ->
                                        current.copy(
                                            artistId = current.artistId ?: extra.artistId,
                                            artists = current.artists.ifEmpty { extra.artists },
                                            albumId = current.albumId ?: extra.albumId,
                                            albumName = current.albumName ?: extra.albumName,
                                        )
                                    }
                                    ?: current
                                Box(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    playerMenuOpen = false
                                                    playerMenuAnchor.value = null
                                                },
                                            ),
                                    )
                                    val anchor = playerMenuAnchor.value
                                    var menuSize by remember { mutableStateOf(IntSize.Zero) }
                                    val density = LocalDensity.current
                                    Box(
                                        Modifier
                                            .onSizeChanged { menuSize = it }
                                            .offset {
                                                if (anchor == null) return@offset IntOffset(0, 0)
                                                val margin = 12
                                                val menuW = if (menuSize.width > 0) menuSize.width else with(density) { 260.dp.roundToPx() }
                                                val relRight = (anchor.right - sheetOrigin.value.x).roundToInt()
                                                val relBottom = (anchor.bottom - sheetOrigin.value.y).roundToInt()
                                                val targetX = (relRight - menuW).coerceAtLeast(margin)
                                                val targetY = (relBottom + 6).coerceAtLeast(margin)
                                                IntOffset(targetX, targetY)
                                            },
                                    ) {
                                        DesktopSongMenuFor(
                                            song = playerSong,
                                            liked = current.videoId in likedIds,
                                            actions = playerSongActions(current),
                                            onToggleLike = { toggleLike(current) },
                                            onRevertToOriginal = {
                                                DesktopOriginalVersion.pin(current.videoId)
                                                playbackEngine.reloadCurrent()
                                            }.takeIf {
                                                playback.streamSourceId != null &&
                                                    playback.streamSourceId != "youtube" &&
                                                    DesktopMusicSources.hasYouTubeOriginal(current) &&
                                                    !DesktopOriginalVersion.isPinned(current.videoId)
                                            },
                                            onUpgradeQuality = {
                                                DesktopOriginalVersion.clear(current.videoId)
                                                playbackEngine.reloadCurrent(forceSourceRefresh = true)
                                            }.takeIf { DesktopOriginalVersion.isPinned(current.videoId) },
                                            onSwitchLyricsQueue = {
                                                playerMenuOpen = false
                                                playerQueueToggle.value = true
                                            },
                                            queueShowing = playerQueueShowing.value,
                                            onTranslate = {
                                                playerMenuOpen = false
                                                playerTranslateToggle.value = true
                                            },
                                            showingTranslation = playerShowingTranslation.value,
                                            translateEnabled = !sharedLyrics.isNullOrEmpty(),
                                            lyricsProviders = menuLyricsProviders,
                                            onSelectLyricsProvider = { label -> lyricsOnly = label },
                                            onSearchLyrics = { title, artist -> searchLyrics(title, artist) },
                                            lyricsOffsetMs = lyricsOffsetMs,
                                            onSetLyricsOffset = { DesktopPlayerSettings.setLyricsOffsetMs(it) },
                                            hideLyricsPreview = DesktopAppearanceSettings.hideLyricsPreview.collectAsState().value,
                                            onToggleHideLyricsPreview = {
                                                DesktopAppearanceSettings.setHideLyricsPreview(
                                                    !DesktopAppearanceSettings.hideLyricsPreview.value,
                                                )
                                            },
                                            usePopup = false,
                                            onDismiss = {
                                                playerMenuOpen = false
                                                playerMenuAnchor.value = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (overlays.accounts) {
                        DesktopAccountSelector(
                            accounts = accounts,
                            activeAccountId = activeAccountId,
                            activeProfileId = activeProfileId,
                            busy = signInBusy != null,
                            onSelect = { account, profile ->
                                DesktopAccounts.select(account.accountId, profile.profileId)
                                activeAccountId = account.accountId
                                activeProfileId = profile.profileId
                            },
                            onAddAccount = { overlays.accounts = false; overlays.signIn = true },
                            onRemoveAccount = { account ->
                                scope.launch {
                                    accounts = withContext(Dispatchers.IO) {
                                        DesktopAccounts.remove(account.accountId)
                                        DesktopAccounts.accounts()
                                    }
                                    activeAccountId = DesktopAccounts.activeAccountId()
                                    activeProfileId = DesktopAccounts.activeProfileId()
                                }
                            },
                            onOpenSettings = { overlays.accounts = false; openSettings() },
                            onDismiss = { overlays.accounts = false },
                        )
                    }

                    if (spotifyImportOpen) {
                        DesktopSpotifyImportDialog(
                            signedIn = youtubeSignedIn,
                            onImported = ::importSpotifyPlaylist,
                            onDismiss = { spotifyImportOpen = false },
                        )
                    }

                    if (overlays.signIn) {
                        DesktopSignInDialog(
                            busy = signInBusy,
                            error = signInError,
                            webSignIn = DesktopWebSignIn.available,
                            onWebSignIn = {
                                webSignInJob?.cancel()
                                webSignInJob = scope.launch {
                                    signInError = null
                                    signInBusy = WEB_SIGN_IN
                                    try {
                                        val cookie = DesktopWebSignIn.captureYouTube()
                                        signIn(cookie, WEB_SIGN_IN)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        DesktopTrackLog.log("sign-in: YouTube Music failed: ${failure.message}")
                                        signInError = failure.message ?: "Could not complete the sign-in."
                                    } finally {
                                        signInBusy = null
                                        webSignInJob = null
                                    }
                                }
                            },
                            onImport = { profile ->
                                scope.launch {
                                    when (val found = withContext(Dispatchers.IO) { DesktopBrowserCookies.read(profile) }) {
                                        is DesktopBrowserCookies.Result.Session ->
                            signIn(found.cookie, profile.label, profile.database.toString())
                                        DesktopBrowserCookies.Result.SignedOut ->
                                            signInError = "${profile.label} is not signed in."
                                        is DesktopBrowserCookies.Result.Unavailable ->
                                            signInError = "${profile.label}: ${found.reason}."
                                    }
                                }
                            },
                            onPaste = { pasted ->
                                scope.launch {
                                    if (!DesktopBrowserCookies.hasSigningSecret(pasted)) {
                                        signInError = "That cookie has no signing secret in it — copy the whole header."
                                    } else {
                                        signIn(pasted, "the cookie you pasted")
                                    }
                                }
                            },
                            onDismiss = {
                                webSignInJob?.cancel()
                                overlays.signIn = false
                                signInError = null
                            },
                        )
                    }
                    if (overlays.playlistDialog || overlays.localPlaylistDialog || playlistTarget != null || playlistTargets.isNotEmpty()) {
                        val targets = if (playlistTargets.isNotEmpty()) playlistTargets else listOfNotNull(playlistTarget)
                        DesktopPlaylistDialog(
                            song = playlistTarget,
                            songs = targets,
                            accountPlaylists = accountPlaylists,
                            localPlaylists = playlists,
                            signedIn = youtubeSignedIn,
                            canUseAccount = !overlays.localPlaylistDialog && targets.all { DesktopSearchClient.isVideoId(it.videoId) },
                            busy = playlistBusy,
                            error = playlistError,
                            onPickAccount = ::addToAccountPlaylist,
                            onPickLocal = ::addToPlaylist,
                            onCreate = ::createPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.rename) {
                        DesktopRenamePlaylistDialog(
                            current = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onRename = ::renameOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.delete) {
                        val isLocal = openedCollection?.let { c -> playlists.any { it.id == c.browseId } } == true
                        DesktopDeletePlaylistDialog(
                            title = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            isLocal = isLocal,
                            onDelete = ::deleteOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    val reorderCollection = openedCollection?.takeIf { overlays.reorder }
                    if (reorderCollection != null) {
                        DesktopReorderPlaylistDialog(
                            browseId = reorderCollection.browseId,
                            busy = playlistBusy,
                            error = playlistError,
                            onSave = ::reorderOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                            songContextMenu = { song, onDismiss ->
                                DesktopSongMenuFor(
                                    song = song,
                                    liked = song.videoId in likedIds,
                                    actions = songActionsFor(song),
                                    onToggleLike = { toggleLike(song) },
                                    onRevertToOriginal = null,
                                    onUpgradeQuality = null,
                                    onDismiss = onDismiss,
                                )
                            },
                        )
                    }
                    if (overlays.downloadManager) {
                        DesktopDownloadManagerDialog(onDismiss = { overlays.downloadManager = false })
                    }
                    if (overlays.lastfmLogin) {
                        DesktopLastfmLoginDialog(onDismiss = { overlays.lastfmLogin = false })
                    }
                    if (overlays.listenBrainzToken) {
                        DesktopListenBrainzTokenDialog(onDismiss = { overlays.listenBrainzToken = false })
                    }
                    if (overlays.discordToken) {
                        DesktopDiscordTokenDialog(onDismiss = { overlays.discordToken = false })
                    }
                    if (overlays.listenTogether) {
                        DesktopListenTogetherDialog(
                            autoplayEnabled = autoplay,
                            onDismiss = { overlays.listenTogether = false },
                        )
                    }
                    if (overlays.audioOutput) {
                        DesktopAudioOutputDialog(onDismiss = { overlays.audioOutput = false })
                    }
                    if (overlays.pipeline) {
                        DesktopAudioPipelineDialog(
                            format = playback.streamFormat,
                            sourceName = playback.streamSourceId?.let { id ->
                                sourceConfigs.firstOrNull { it.id == id }?.displayName
                            },
                            pipeline = playbackEngine.pipeline(),
                            isPlaying = playback.isPlaying,
                            onDismiss = { overlays.pipeline = false },
                        )
                    }
                    }
                    val globalMessage by DesktopPlayerHost.messages.collectAsState()
                    LaunchedEffect(globalMessage) {
                        if (globalMessage != null) {
                            delay(2_500)
                            DesktopPlayerHost.messages.value = null
                        }
                    }
                    globalMessage?.let { text ->
                        if (!overlays.nowPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 88.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Text(
                                    text = text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                },
            ) { contentPadding ->
                val titleBarEnabledForPadding by DesktopTitleBarSetting.enabled.collectAsState()
                val inlineCaptionForPadding = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabledForPadding
                val sharedPageTopPadding = resolveSharedPageTopPadding(inlineCaptionForPadding)
                // Preserve clean top breathing room aligned with the floating sidebar and room for the
                // floating player/navigation bar at the bottom.
                val sharedPagePadding = PaddingValues(
                    top = sharedPageTopPadding,
                    bottom = contentPadding.calculateBottomPadding(),
                )
                CompositionLocalProvider(
                    LocalPullToRefreshEnabled provides false,
                    LocalShelfRowChrome provides DesktopShelfRowChrome,
                    com.music.bitchord.ui.player.LocalPlayerLightTheme provides !DesktopIsDark,
                ) {
                DesktopPointerMenuHost<Song>(
                    menu = { song, onDismiss ->
                        DesktopSongMenuFor(
                            song = song,
                            liked = song.videoId in likedIds,
                            actions = songActionsFor(song),
                            onToggleLike = { toggleLike(song) },
                            onRevertToOriginal = null,
                            onUpgradeQuality = null,
                            onDismiss = onDismiss,
                        )
                    },
                ) { openMenu ->
                pageMenu = openMenu
                CompositionLocalProvider(LocalDesktopContextMenu provides openMenu) {
                val swipeBackGesture by DesktopAppearanceSettings.swipeBackGesture.collectAsState()
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(swipeBackGesture) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (shouldHandleBackPointerGesture(
                                            gestureEnabled = swipeBackGesture,
                                            isBackPressed = event.type == PointerEventType.Press &&
                                                event.buttons.isBackPressed,
                                        )
                                    ) {
                                        goBack()
                                    }
                                }
                            }
                        },
                ) {
                    val currentRoute: DesktopPageRoute = when {
                        overlays.settingsPage != null -> DesktopPageRoute.Settings(overlays.settingsPage!!)
                        overlays.replay -> DesktopPageRoute.Replay
                        openedArtist != null -> DesktopPageRoute.Artist(openedArtist!!)
                        openedCollection?.loading == true -> DesktopPageRoute.CollectionLoading(
                            browseId = openedCollection!!.browseId,
                            title = openedCollection!!.title,
                        )
                        openedCollection != null -> DesktopPageRoute.Collection(openedCollection!!)
                        destination == DesktopDestination.LISTEN_NOW && homeMoodsAllOpen -> DesktopPageRoute.HomeMoodsAll
                        destination == DesktopDestination.EXPLORE && selectedMoodGenre != null -> DesktopPageRoute.ExploreMood(selectedMoodGenre!!)
                        destination == DesktopDestination.LIBRARY && libraryShowAll != null -> DesktopPageRoute.LibraryShowAll(libraryShowAll!!)
                        else -> DesktopPageRoute.Destination(destination)
                    }
                    val animatePageTransitions = smoothTransitions && !reduceAnimation
                    AnimatedContent(
                        targetState = currentRoute,
                        contentKey = { it.transitionKey },
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (animatePageTransitions) {
                                (fadeIn(tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                                    scaleIn(
                                        initialScale = 0.986f,
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                    ))
                                    .togetherWith(
                                        fadeOut(tween(durationMillis = 150, easing = FastOutLinearInEasing)),
                                    )
                                    .using(SizeTransform(clip = false))
                            } else {
                                EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false)
                            }
                        },
                        label = "desktop-page-transition",
                    ) { route ->
                    when (route) {
                        // Settings and its pages stand in for the page, held to the width the
                        // Settings card had. Its prompts are still cards, over the page.
                        is DesktopPageRoute.Settings -> Box(
                            Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
                        ) {
                            fun applyRestoredBackupState() {
                                history = persistence.history()
                                likedIds = persistence.likedIds()
                                dislikedIds = persistence.dislikedIds()
                                playlists = persistence.playlists()
                                replayGenres = persistence.boolean("replay_genres", true)
                                replaySummary = DesktopListeningStats.summary(replayPeriod)
                                statsRevision++
                                autoplay = persistence.boolean("autoplay", true)
                                dontRepeatSuggestions = persistence.boolean(KEY_DONT_REPEAT_SUGGESTIONS, false)
                                filterNonMusicAudio = persistence.boolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, false)
                                localMusicRevision++
                                automix = persistence.boolean("automix", false)
                                automixOnSkip = persistence.boolean("automix_on_skip", false)
                                automixPhraseBars = persistence.string("automix_phrase_bars", "0").toIntOrNull()?.let { if (it == 8 || it == 16) it else 0 } ?: 0
                                automixPerformance = runCatching {
                                    AutomixPerformanceMode.valueOf(persistence.string("automix_performance", "BALANCED"))
                                }.getOrDefault(AutomixPerformanceMode.BALANCED)
                                autoMixArtTransition = persistence.boolean("automix_art_transition", false)
                                autoMixMiniArtTransition = persistence.boolean("automix_mini_art_transition", false)
                                showBestPart = persistence.boolean("show_best_part", true)
                                showTotalDuration = persistence.boolean("show_total_duration", false)
                                shuffle = persistence.boolean("shuffle", false)
                                repeatMode = runCatching {
                                    DesktopRepeatMode.valueOf(persistence.string("repeat_mode", "OFF"))
                                }.getOrDefault(DesktopRepeatMode.OFF)
                                playbackSpeed = persistence.string("playback_speed", "1.0").toFloatOrNull() ?: 1.0f
                                crossfadeSeconds = persistence.string("crossfade_seconds", "0").toIntOrNull()?.coerceIn(0, 12) ?: 0
                                downloadQuality = persistence.string("download_quality", "LOSSLESS")
                                trayIconEnabled = persistence.boolean("tray_icon", true)
                                closeToTray = persistence.boolean("close_to_tray", true)
                                spatialAudio = persistence.boolean("spatial_audio", false)
                                dolbyAtmos = persistence.boolean("dolby_atmos", true)
                                skipSilence = persistence.boolean("skip_silence", false)
                                smartVersionAlignment = persistence.boolean("smart_version_alignment", true)
                                outputPrecision = persistence.string("output_precision", "PCM_16")
                                legacyMeshGradient = persistence.boolean("legacy_mesh_gradient", false)
                                animatedCanvas = persistence.boolean("animated_canvas", true)
                                animatedArtistBanner = persistence.boolean("animated_artist_banner", true)
                                showNerdStats = persistence.boolean("show_nerd_stats", false)
                                syncedLyrics = persistence.boolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, true)
                                lyricsBlur = persistence.boolean(DesktopLyricsClient.KEY_LYRICS_BLUR, true)
                                prioritizeSyllables = persistence.boolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false)
                                lyricsOrder = persistence.lyricsSourceOrder()
                                lyricsOn = persistence.lyricsEnabledSources()
                                fullBleedArtwork = persistence.boolean("full_bleed_artwork", false)
                            }
                            CompositionLocalProvider(LocalDesktopPanelIsPage provides true) {
                            when (route.page) {
                                DesktopSettingsPage.BACKUP_AND_RESTORE -> DesktopBackupRestorePage(
                                    replaySummary = replaySummary,
                                    replayGenres = replayGenres,
                                    onReplayGenresChange = {
                                        replayGenres = it
                                        persistence.saveBoolean("replay_genres", it)
                                    },
                                    onOpenReplay = {
                                        overlays.settingsPage = null
                                        overlays.replay = true
                                    },
                                    onOpenDriveBackup = { overlays.settingsPage = DesktopSettingsPage.GOOGLE_DRIVE_BACKUP },
                                    onRestored = ::applyRestoredBackupState,
                                    onBack = ::goBack,
                                )
                                DesktopSettingsPage.GOOGLE_DRIVE_BACKUP -> DesktopGoogleDriveBackupPage(
                                    onRestored = ::applyRestoredBackupState,
                                    onReplaySynced = { statsRevision++ },
                                    onBack = ::goBack,
                                )
                                DesktopSettingsPage.EQUALIZER -> DesktopEqualizerDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.AUDIO_OUTPUT -> DesktopAudioOutputDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.LISTEN_TOGETHER -> DesktopListenTogetherDialog(
                                    autoplayEnabled = autoplay,
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.LYRICS_SOURCES -> DesktopLyricsSourcesDialog(
                                    order = lyricsOrder,
                                    enabled = lyricsOn,
                                    prioritizeSyllables = prioritizeSyllables,
                                    onReorder = {
                                        lyricsOrder = it
                                        persistence.saveLyricsSourceOrder(it)
                                    },
                                    onToggle = { name ->
                                        lyricsOn = if (name in lyricsOn) lyricsOn - name else lyricsOn + name
                                        persistence.saveLyricsEnabledSources(lyricsOn)
                                    },
                                    onPrioritizeSyllables = {
                                        prioritizeSyllables = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, it)
                                    },
                                    onReset = {
                                        lyricsOrder = DesktopLyricsClient.sources.map { it.name }
                                        lyricsOn = lyricsOrder.toSet()
                                        prioritizeSyllables = false
                                        persistence.saveLyricsSourceOrder(lyricsOrder)
                                        persistence.saveLyricsEnabledSources(lyricsOn)
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false)
                                    },
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.TRANSLATION_LANGUAGE -> DesktopTranslationLanguageDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.SPOTIFY_CANVAS -> DesktopSpotifyCanvasDialog(
                                    onDismiss = ::goBack,
                                    onSaved = { spotifyCanvasCookie = it },
                                )
                                DesktopSettingsPage.INTEGRATIONS -> DesktopIntegrationsDialog(
                                    song = playback.song,
                                    spotify = DesktopSpotifyConnection(
                                        connected = spotifyCanvasCookie.isNotBlank(),
                                        canSignIn = DesktopWebSignIn.available,
                                        signingIn = spotifySignInJob != null,
                                        error = spotifySignInError,
                                        onSignIn = ::signInToSpotify,
                                        onPasteCookie = { overlays.settingsPage = DesktopSettingsPage.SPOTIFY_CANVAS },
                                        onDisconnect = {
                                            DesktopSpotifyToken.setCookie("")
                                            spotifyCanvasCookie = ""
                                        },
                                    ),
                                    onOpenLastfm = { overlays.lastfmLogin = true },
                                    onOpenListenBrainz = { overlays.listenBrainzToken = true },
                                    onOpenDiscordToken = { overlays.discordToken = true },
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.LICENSES -> DesktopLicensesPage(onDismiss = ::goBack)
                                DesktopSettingsPage.UPDATES -> DesktopUpdatesPage(onDismiss = ::goBack)
                                else -> {
                                    val accountAvatarUrl = activeAccount?.profiles
                                        ?.firstOrNull { it.profileId == activeProfileId }?.avatar?.takeIf(String::isNotBlank)
                                        ?: activeAccount?.avatar?.takeIf(String::isNotBlank)
                                        ?: activeAccount?.profiles?.firstOrNull()?.avatar?.takeIf(String::isNotBlank)
                                    val accountSub = activeAccount?.email?.takeIf(String::isNotBlank)
                                        ?: if (youtubeSignedIn) DesktopStrings["signed_in", "Signed in"]
                                        else DesktopStrings[
                                            "d_discord_rich_presence_last_fm_and_listenbrainz",
                                            "Discord rich presence, Last.fm and ListenBrainz",
                                        ]
                                    DesktopSettingsRouter(
                                        page = route.page,
                                        state = DesktopSettingsUiState(
                                            query = settingsQuery,
                                            onQueryChange = { settingsQuery = it },
                                            listState = settingsListState,
                                            accountAvatar = accountAvatarUrl,
                                            accountSubtitle = accountSub,
                                            autoplay = autoplay,
                                            onAutoplayChange = ::setAutoplay,
                                            automix = automix,
                                            onAutomixChange = {
                                                automix = it
                                                persistence.saveBoolean("automix", it)
                                            },
                                            automixOnSkip = automixOnSkip,
                                            onAutomixOnSkipChange = {
                                                automixOnSkip = it
                                                persistence.saveBoolean("automix_on_skip", it)
                                            },
                                            automixPerformance = automixPerformance,
                                            onAutomixPerformanceChange = {
                                                automixPerformance = it
                                                persistence.saveString("automix_performance", it.name)
                                            },
                                            automixPhraseBars = automixPhraseBars,
                                            onAutomixPhraseBarsChange = {
                                                automixPhraseBars = it
                                                persistence.saveString("automix_phrase_bars", it.toString())
                                            },
                                            autoMixArtTransition = autoMixArtTransition,
                                            onAutoMixArtTransitionChange = {
                                                autoMixArtTransition = it
                                                DesktopPlayerSettings.setAutoMixArtTransition(it)
                                            },
                                            autoMixMiniArtTransition = autoMixMiniArtTransition,
                                            onAutoMixMiniArtTransitionChange = {
                                                autoMixMiniArtTransition = it
                                                DesktopPlayerSettings.setAutoMixMiniPlayerTransition(it)
                                            },
                                            showBestPart = showBestPart,
                                            onShowBestPartChange = {
                                                showBestPart = it
                                                DesktopPlayerSettings.setShowBestPart(it)
                                            },
                                            showTotalDuration = showTotalDuration,
                                            onShowTotalDurationChange = {
                                                showTotalDuration = it
                                                DesktopPlayerSettings.setShowTotalDuration(it)
                                            },
                                            shuffle = shuffle,
                                            onShuffleChange = ::setShuffle,
                                            repeatMode = repeatMode,
                                            onRepeatModeChange = {
                                                repeatMode = it
                                                persistence.saveString("repeat_mode", it.name)
                                            },
                                            playbackSpeed = playbackSpeed,
                                            onPlaybackSpeedChange = {
                                                playbackSpeed = it
                                                persistence.saveString("playback_speed", it.toString())
                                            },
                                            crossfadeSeconds = crossfadeSeconds,
                                            onCrossfadeSecondsChange = {
                                                crossfadeSeconds = it
                                                persistence.saveString("crossfade_seconds", it.toString())
                                            },
                                            animatedCanvas = animatedCanvas,
                                            onAnimatedCanvasChange = {
                                                animatedCanvas = it
                                                persistence.saveBoolean("animated_canvas", it)
                                            },
                                            animatedArtistBanner = animatedArtistBanner,
                                            onAnimatedArtistBannerChange = {
                                                animatedArtistBanner = it
                                                persistence.saveBoolean("animated_artist_banner", it)
                                            },
                                            spotifyCanvasReady = spotifyCanvasCookie.isNotBlank(),
                                            dontRepeatSuggestions = dontRepeatSuggestions,
                                            onDontRepeatSuggestionsChange = {
                                                dontRepeatSuggestions = it
                                                persistence.saveBoolean(KEY_DONT_REPEAT_SUGGESTIONS, it)
                                            },
                                            filterNonMusicAudio = filterNonMusicAudio,
                                            onFilterNonMusicAudioChange = {
                                                filterNonMusicAudio = it
                                                persistence.saveBoolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, it)
                                                localMusicRevision++
                                            },
                                            onChooseLocalMusicFolder = { onChosen ->
                                                DesktopLocalMusic.chooseFolder()?.let { chosen ->
                                                    DesktopLocalMusic.setFolder(chosen)
                                                    onChosen()
                                                    localMusicRevision++
                                                }
                                            },
                                            onLocalMusicFolderChanged = { localMusicRevision++ },
                                            syncedLyrics = syncedLyrics,
                                            onSyncedLyricsChange = {
                                                syncedLyrics = it
                                                persistence.saveBoolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, it)
                                            },
                                            lyricsBlur = lyricsBlur,
                                            onLyricsBlurChange = {
                                                lyricsBlur = it
                                                persistence.saveBoolean(DesktopLyricsClient.KEY_LYRICS_BLUR, it)
                                            },
                                            enabledLyricsSources = DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn),
                                            showNerdStats = showNerdStats,
                                            onShowNerdStatsChange = {
                                                showNerdStats = it
                                                persistence.saveBoolean("show_nerd_stats", it)
                                            },
                                            fullBleedArtwork = fullBleedArtwork,
                                            onFullBleedArtworkChange = {
                                                fullBleedArtwork = it
                                                persistence.saveBoolean("full_bleed_artwork", it)
                                            },
                                            legacyMeshGradient = legacyMeshGradient,
                                            onLegacyMeshGradientChange = {
                                                legacyMeshGradient = it
                                                persistence.saveBoolean("legacy_mesh_gradient", it)
                                            },
                                            trayIconEnabled = trayIconEnabled,
                                            onTrayIconChange = {
                                                trayIconEnabled = it
                                                persistence.saveBoolean("tray_icon", it)
                                            },
                                            closeToTray = closeToTray,
                                            onCloseToTrayChange = {
                                                closeToTray = it
                                                persistence.saveBoolean("close_to_tray", it)
                                            },
                                            spatialAudio = spatialAudio,
                                            onSpatialAudioChange = {
                                                spatialAudio = it
                                                persistence.saveBoolean("spatial_audio", it)
                                            },
                                            dolbyAtmos = dolbyAtmos,
                                            onDolbyAtmosChange = {
                                                dolbyAtmos = it
                                                DesktopAddonSettings.dolbyAtmosEnabled = it
                                                persistence.saveBoolean("dolby_atmos", it)
                                                DesktopAddonSource.clearCompletedTrackCalls()
                                            },
                                            skipSilence = skipSilence,
                                            onSkipSilenceChange = {
                                                skipSilence = it
                                                persistence.saveBoolean("skip_silence", it)
                                            },
                                            smartVersionAlignment = smartVersionAlignment,
                                            onSmartVersionAlignmentChange = {
                                                smartVersionAlignment = it
                                                persistence.saveBoolean("smart_version_alignment", it)
                                            },
                                            outputPrecision = outputPrecision,
                                            onOutputPrecisionChange = {
                                                outputPrecision = it
                                                persistence.saveString("output_precision", it)
                                            },
                                            outputSummary = playbackEngine.outputSummary(),
                                            downloadQuality = downloadQuality,
                                            onDownloadQualityChange = {
                                                downloadQuality = it
                                                persistence.saveString("download_quality", it)
                                            },
                                            autoDownloadLiked = autoDownloadLiked,
                                            onAutoDownloadLikedChange = {
                                                autoDownloadLiked = it
                                                persistence.saveAutoDownloadLiked(it)
                                            },
                                            audioQuality = audioQuality,
                                            onAudioQualityChange = {
                                                audioQuality = it
                                                persistence.saveAudioQuality(it)
                                            },
                                            sleepTimerMinutes = sleepTimerMinutes,
                                            sleepAfterTrack = sleepAfterTrack,
                                            sleepRemainingMs = sleepRemainingMs,
                                            sourceConfigs = sourceConfigs,
                                            sourceStatus = sourceStatus,
                                            onSourceEnabledChange = { config, enabled ->
                                                val next = sourceConfigs.map {
                                                    if (it.id == config.id && it.kind != DesktopSourceKind.YOUTUBE) {
                                                        it.copy(enabled = enabled)
                                                    } else {
                                                        it
                                                    }
                                                }
                                                persistence.saveSourceConfigs(next)
                                                sourceConfigs = persistence.sourceConfigs()
                                                DesktopModuleSource.reload()
                                            },
                                            onSaveSource = { saved ->
                                                val without = sourceConfigs.filterNot {
                                                    it.id == saved.id ||
                                                        (saved.kind == DesktopSourceKind.CUSTOM_MODULE &&
                                                            it.kind == DesktopSourceKind.CUSTOM_MODULE)
                                                }
                                                persistence.saveSourceConfigs((without + saved).inSourceOrder())
                                                sourceConfigs = persistence.sourceConfigs()
                                                DesktopAddonSource.forget(saved.id)
                                                DesktopModuleSource.reload()
                                            },
                                            onRemoveSource = { config ->
                                                if (config.isUserAdded) {
                                                    persistence.saveSourceConfigs(sourceConfigs.filterNot { it.id == config.id })
                                                    sourceConfigs = persistence.sourceConfigs()
                                                    DesktopAddonSource.forget(config.id)
                                                    DesktopModuleSource.reload()
                                                }
                                            },
                                            onMoveSource = { config, delta ->
                                                val next = moveUserSource(sourceConfigs, config.id, delta)
                                                persistence.saveSourceConfigs(next)
                                                sourceConfigs = persistence.sourceConfigs()
                                            },
                                            onTestSource = { candidate ->
                                                sourceStatus = sourceStatus + (candidate.id to "Checking source…")
                                                scope.launch {
                                                    val health = if (candidate.kind == DesktopSourceKind.ADDON) {
                                                        DesktopAddonSource.health(candidate)
                                                    } else {
                                                        DesktopModuleSource.health(candidate)
                                                    }
                                                    health.fold(
                                                        onSuccess = { sourceStatus = sourceStatus + (candidate.id to it) },
                                                        onFailure = {
                                                            sourceStatus = sourceStatus +
                                                                (candidate.id to (it.message ?: "Source unavailable"))
                                                        },
                                                    )
                                                }
                                            },
                                            onNavigate = { target -> overlays.settingsPage = target },
                                            onBack = ::goBack,
                                        ),
                                    )
                                }
                            }
                            }
                        }
                        DesktopPageRoute.Replay -> DesktopReplayPage(
                            summary = replaySummary,
                            period = replayPeriod,
                            holder = replayHolder,
                            onPeriodChange = { replayPeriod = it },
                            onPlaySong = { playSong(it) },
                            onOpenArtist = ::openArtistByName,
                            contentPadding = contentPadding,
                        )
                        is DesktopPageRoute.Artist -> DesktopArtistPage(
                            state = artistState,
                            fallbackName = route.target.name,
                            browseId = route.target.browseId,
                            animatedArtistBanner = animatedArtistBanner,
                            isPlaying = playback.isPlaying,
                            onTogglePlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                            onRetry = {
                                artistState = UiState.Loading
                                artistReloads++
                            },
                            onPlaySongs = { songs, index ->
                                playSongs(songs, index, artistSource(route.target))
                            },
                            onShuffle = { songs ->
                                playSongs(songs, 0, artistSource(route.target))
                            },
                            onOpenMenu = { song -> openMenu(song) },
                            onShelfItemClick = ::openShelfItem,
                            // A channel subscription is the account's, so a guest is never shown
                            // the button.
                            onToggleSubscription = if (youtubeSignedIn) ::toggleSubscription else null,
                            contentPadding = contentPadding,
                        )
                        is DesktopPageRoute.CollectionLoading -> DesktopPageScaffold(contentPadding) {
                            Box(Modifier.fillMaxSize()) {
                                val error = collectionError
                                if (error != null) {
                                    DesktopErrorPage(error) { collectionReloads++ }
                                } else {
                                    val title = route.title
                                    DesktopLoadingPage(if (title.isBlank()) "Loading…" else "Loading $title…")
                                }
                            }
                        }
                        is DesktopPageRoute.Collection -> DesktopCollectionPage(
                            collection = route.collection,
                            loadingMore = collectionLoadingMore,
                            onLoadMore = ::loadMoreCollectionSongs,
                            isPlaying = playback.isPlaying,
                            onTogglePlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                            onRename = { overlays.rename = true }.takeIf { route.collection.owned == true },
                            onShare = route.collection
                                .takeIf { it.type == BrowseType.ALBUM || it.type == BrowseType.PLAYLIST }
                                ?.let { collection -> { shareCollection(collection) } },
                            onDelete = { overlays.delete = true }.takeIf { route.collection.owned == true },
                            onReorder = { overlays.reorder = true }.takeIf {
                                route.collection.owned == true && route.collection.type == BrowseType.PLAYLIST
                            },
                            onRemoveFromPlaylist = ::removeFromOpenPlaylist
                                .takeIf { route.collection.owned == true },
                            likedIds = likedIds,
                            onPlaySongs = { songs, index ->
                                playSongs(songs, index, collectionSource(route.collection))
                            },
                            onShuffle = { songs ->
                                playSongs(songs.shuffled(), 0, collectionSource(route.collection))
                            },
                            onSavePlaylist = ::saveCollectionAsPlaylist,
                            onDownloadAll = ::downloadAll,
                            animatedCanvas = animatedCanvas,
                            onToggleLike = { song ->
                                toggleLike(song)
                            },
                            onAddToPlaylist = { playlistTarget = it },
                            onDownload = ::downloadSong,
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                        )
                        DesktopPageRoute.HomeMoodsAll -> Column(Modifier.fillMaxSize()) {
                            MoodGridPage(
                                gridState = moodsGridState,
                                onClick = ::playMood,
                                contentPadding = sharedPagePadding,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        is DesktopPageRoute.ExploreMood -> Column(Modifier.fillMaxSize()) {
                            MoodGenrePlaylistsScreen(
                                title = route.mood.title,
                                state = moodGenreShelves,
                                listState = moodGenreListState,
                                onItemClick = { item -> openShelfItem(item, route.mood.title) },
                                onRetry = {
                                    moodGenreShelves = UiState.Loading
                                    moodGenreReloads++
                                },
                                contentPadding = sharedPagePadding,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        is DesktopPageRoute.LibraryShowAll -> Column(Modifier.fillMaxSize()) {
                            // The phone keeps the grid's sort beside it, in the bar above; here it sits
                            // at the end of the row the way back is on.
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
                                horizontalArrangement = Arrangement.End,
                            ) { DesktopLibrarySortMenu() }
                            spotifyLibraryStatus?.takeIf { route.shelf.title == SPOTIFY_SHELF }?.let { status ->
                                Text(
                                    status,
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 12.dp),
                                )
                            }
                            LibraryGridPage(
                                shelf = route.shelf,
                                gridState = libraryGridState,
                                onItemClick = ::openLibraryItem,
                                onItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                                contentPadding = sharedPagePadding,
                                modifier = Modifier.weight(1f),
                                onNewPlaylist = { overlays.playlistDialog = true }
                                    .takeIf { route.shelf.title == PLAYLISTS_SHELF },
                            )
                        }
                        is DesktopPageRoute.Destination -> when (route.destination) {
                        DesktopDestination.LISTEN_NOW -> HomeScreen(
                            state = homeState,
                            listState = homeListState,
                            onItemClick = { item, shelfTitle -> openShelfItem(item, shelfTitle) },
                            onRetry = ::loadHome,
                            refreshing = false,
                            onRefresh = ::loadHome,
                            pullState = rememberPullToRefreshState(),
                            contentPadding = sharedPagePadding,
                            title = null,
                            signedIn = youtubeSignedIn,
                            onSignIn = { overlays.accounts = true },
                            onItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                            onLoadMore = ::loadMoreHome,
                            loadingMore = homeLoadingMore,
                            recentlyPlayedLoading = homeRecentsLoading,
                            currentSong = playback.song,
                            isPlaying = playback.isPlaying,
                            topPicks = topPicks,
                            topPicksLoading = topPicksLoading,
                            listenerName = activeAccount?.profiles?.firstOrNull { it.profileId == activeProfileId }?.name?.takeIf(String::isNotBlank)
                                ?: activeAccount?.name?.takeIf(String::isNotBlank),
                            onTopPickClick = ::openTopPick,
                            onTopPickLongPress = { pick ->
                                pick.browseId?.let { browseId ->
                                    openCollection(
                                        browseId = browseId,
                                        fallback = BrowseItem(
                                            browseId = browseId,
                                            title = pick.title,
                                            subtitle = pick.artistName.orEmpty(),
                                            thumbnailUrl = pick.images.firstOrNull(),
                                            type = BrowseType.ALBUM,
                                        ),
                                    )
                                }
                            },
                            onMoodClick = ::playMood,
                            onMoodsShowAll = { homeMoodsAllOpen = true },
                            artworkMotion = !reduceAnimation,
                        )
                        DesktopDestination.RADIO -> MixesScreen(
                            title = null,
                            state = mixesState,
                            signedIn = youtubeSignedIn,
                            listState = mixesListState,
                            onItemClick = { item -> openShelfItem(item, DesktopStrings["radio", "Radio"]) },
                            onRetry = {
                                mixesState = UiState.Loading
                                mixesReloads++
                            },
                            onSignIn = { overlays.accounts = true },
                            refreshing = false,
                            onRefresh = {
                                mixesState = UiState.Loading
                                mixesReloads++
                            },
                            pullState = rememberPullToRefreshState(),
                            contentPadding = sharedPagePadding,
                            onItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                        )
                        DesktopDestination.EXPLORE -> ExploreScreen(
                            state = exploreState,
                            listState = exploreListState,
                            onCategoryClick = ::openMoodGenre,
                            onRetry = { exploreReloads++ },
                            refreshing = false,
                            onRefresh = { exploreReloads++ },
                            pullState = rememberPullToRefreshState(),
                            contentPadding = sharedPagePadding,
                            showTitle = false,
                        )
                        DesktopDestination.SEARCH -> SearchScreen(
                            query = query,
                            onQueryChange = ::editQuery,
                            filter = searchFilter,
                            // The playing row's indicator, as the phone passes it: the engine's
                            // live state, not the selection a track change publishes behind it.
                            currentSong = playback.song,
                            isPlaying = playback.isPlaying,
                            onFilterChange = {
                                searchFilter = it
                                if (query.isNotBlank()) search()
                            },
                            results = when {
                                searchLoading -> UiState.Loading
                                searchError != null -> UiState.Error(searchError!!)
                                searchCommitted -> UiState.Success(searchRows)
                                else -> null
                            },
                            loadingMore = false,
                            onLoadMore = {},
                            listState = searchListState,
                            scrollResetTrigger = searchScrollReset,
                            focusRequested = searchFocusRequested,
                            onFocusHandled = { searchFocusRequested = false },
                            onSongClick = { songs, index ->
                                songs.getOrNull(index)?.let { song ->
                                    recordSongSearch(song)
                                    playSong(song, source = DesktopQueueSource(DesktopStrings["search", "Search"], PlaybackSourceType.SEARCH))
                                }
                            },
                            onSongLongPress = ::openMenu,
                            onSongSwipe = { song ->
                                if (DesktopAppUiHost.swipeToPlayNext.value) playNext(song) else addToQueue(song)
                            },
                            onTopResultPlay = { song ->
                                recordSongSearch(song)
                                playSong(song, source = DesktopQueueSource(DesktopStrings["search", "Search"], PlaybackSourceType.SEARCH))
                            },
                            onTopResultPlaylist = { song ->
                                recordSongSearch(song)
                                playlistTarget = song
                            },
                            onBrowseClick = { item ->
                                recordBrowseSearch(item)
                                openBrowseItem(item)
                            },
                            history = searchHistory,
                            // The typed text leads the list, put there by the keystroke rather than
                            // taken from the response — the page drops it, as the phone's does.
                            suggestions = if (searchTyping && query.isNotBlank()) {
                                listOf(query) +
                                    searchSuggestions.filterNot { it.equals(query, ignoreCase = true) }
                            } else {
                                emptyList()
                            },
                            typeaheadResults = if (searchTyping && query.isNotBlank()) searchTypeahead else emptyList(),
                            onSubmit = ::search,
                            onSuggestionClick = ::runSearch,
                            onHistoryClick = { entity ->
                                // Going back to a recent does not record it again, as on the phone.
                                when (entity.entityType) {
                                    EntityType.TRACK -> playSong(
                                        Song(
                                            videoId = entity.id,
                                            title = entity.title,
                                            artist = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                        ),
                                        source = DesktopQueueSource(entity.title, PlaybackSourceType.SEARCH),
                                    )
                                    EntityType.ARTIST -> openArtist(entity.id, entity.title)
                                    EntityType.ALBUM, EntityType.PLAYLIST -> openBrowseItem(
                                        BrowseItem(
                                            browseId = entity.id,
                                            title = entity.title,
                                            subtitle = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                            type = if (entity.entityType == EntityType.ALBUM) BrowseType.ALBUM else BrowseType.PLAYLIST,
                                        ),
                                    )
                                    else -> {}
                                }
                            },
                            onHistoryRemove = DesktopSearchHistory::remove,
                            onHistoryClear = DesktopSearchHistory::clear,
                            onTypeaheadLongPress = ::openMenu,
                            contentPadding = sharedPagePadding,
                            topPadding = if (LocalDesktopCompact.current || sidebarMinimized) 12.dp else sharedPageTopPadding,
                            showField = LocalDesktopCompact.current || sidebarMinimized,
                        )
                        DesktopDestination.LIBRARY -> LibraryScreen(
                            signedIn = youtubeSignedIn,
                            state = libraryState,
                            listState = libraryListState,
                            onShelfItemClick = ::openLibraryItem,
                            onShelfItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                            onNewPlaylist = { overlays.playlistDialog = true },
                            onImportSpotifyPlaylist = { spotifyImportOpen = true },
                            onShowAll = { shelf -> libraryShowAll = shelf },
                            replay = {
                                // The phone's wallet of Replay cards, or nothing until there is
                                // listening to deal them from.
                                val cards = remember(replaySummary) { replaySummary.heroCards() }
                                if (!replaySummary.isEmpty && cards.isNotEmpty()) {
                                    ShelfRow(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(cards, key = { it.label }) { card ->
                                            ReplayCreditCard(
                                                label = card.label,
                                                value = card.value,
                                                detail = card.detail,
                                                artworkUrl = card.artworkUrl,
                                                holder = replayHolder,
                                                memberSince = replaySummary.memberSince(),
                                                onClick = { overlays.replay = true },
                                                modifier = Modifier.width(300.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            onSignIn = { overlays.accounts = true },
                            onRetry = ::reloadLibrary,
                            refreshing = false,
                            onRefresh = ::reloadLibrary,
                            pullState = rememberPullToRefreshState(),
                            contentPadding = sharedPagePadding,
                            links = libraryLinks,
                            deviceItems = libraryDeviceItems,
                            showTitle = false,
                        )
                        DesktopDestination.HISTORY -> {
                            val historySource = DesktopQueueSource(
                                title = DesktopStrings["history", "History"],
                                type = PlaybackSourceType.HISTORY,
                                id = "history:all",
                            )
                            DesktopHistoryPage(
                                // The account's history when there is one, and what
                                // this computer played when there is not.
                                history = remoteHistory.ifEmpty { history },
                                isPlaying = playback.isPlaying,
                                onTogglePlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                                onSongClick = { playSong(it, source = historySource) },
                                onPlaySongs = { list, index -> playSongs(list, index, historySource) },
                                onShuffleAll = { list -> playSongs(list.shuffled(), 0, historySource) },
                                likedIds = likedIds,
                                onToggleLike = ::toggleLike,
                                onDownload = ::downloadSong,
                                onAddToPlaylist = { playlistTarget = it },
                                downloadedIds = downloads.map(Song::videoId).toSet(),
                                downloadInProgress = downloadInProgress,
                                contentPadding = contentPadding,
                                menu = { song -> songMenu(song) },
                            )
                        }
                        DesktopDestination.DOWNLOADS -> Box(Modifier.fillMaxSize()) {
                            val downloadsSource = DesktopQueueSource(
                                title = DesktopStrings["downloads", "Downloads"],
                                type = PlaybackSourceType.BROWSE,
                                id = "downloaded:all",
                            )
                            DesktopDownloadsPage(
                                downloads = downloads,
                                isPlaying = playback.isPlaying,
                                onTogglePlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                                onSongClick = { playSong(it, source = downloadsSource) },
                                onPlaySongs = { list, index, src -> playSongs(list, index, src ?: downloadsSource) },
                                onShuffleAll = { list, src -> playSongs(list.shuffled(), 0, src ?: downloadsSource) },
                                likedIds = likedIds,
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { playlistTarget = it },
                                contentPadding = contentPadding,
                                menu = { song -> songMenu(song) },
                            )
                            // Only while there is a queue to look at.
                            if (downloadInProgress.isNotEmpty()) {
                                DesktopActionButton(
                                    "Queue · ${downloadInProgress.size}",
                                    BitChordIcons.Download,
                                    onClick = { overlays.downloadManager = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 28.dp, bottom = 28.dp + contentPadding.calculateBottomPadding()),
                                )
                            }
                        }
                        DesktopDestination.LOCAL_MUSIC -> {
                            val localSource = DesktopQueueSource(
                                title = DesktopStrings["local_music", "Local Music"],
                                type = PlaybackSourceType.BROWSE,
                                id = "local:all",
                            )
                            DesktopLocalMusicPage(
                                songs = localSongs,
                                isPlaying = playback.isPlaying,
                                onTogglePlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                                onSongClick = { playSong(it, source = localSource) },
                                onPlaySongs = { list, index, src -> playSongs(list, index, src ?: localSource) },
                                onShuffleAll = { list, src -> playSongs(list.shuffled(), 0, src ?: localSource) },
                                likedIds = likedIds,
                                onToggleLike = ::toggleLike,
                                onAddToPlaylist = { playlistTarget = it },
                                localPlaylists = playlists,
                                onCreatePlaylist = { overlays.localPlaylistDialog = true },
                                onOpenPlaylist = ::openPlaylist,
                                showPlaylistsAndFolders = true,
                                contentPadding = contentPadding,
                                menu = { song -> songMenu(song) },
                            )
                        }
                        DesktopDestination.SETTINGS -> Unit
                        }
                    }
                    }
                }
                }
                }

            }
        }
        }
    }

}

@Suppress("Unused", "UNUSED_PARAMETER")
@Composable
private fun DesktopTopBar(
    compact: Boolean,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    progress: Float,
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    /** Which of the two the column beside the page is showing, lit on its button. */
    sidePanel: DesktopSidePanel?,
    accountAvatar: String?,
    onOpenAccounts: () -> Unit,
    /** The app's one way back — every page's own arrow was folded into this. */
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var skipHint by remember { mutableStateOf(1f) }
    val skipDirection = rememberTrackSkipDirection(song?.videoId, skipHint)
    val prevNudge = remember { Animatable(0f) }
    val nextNudge = remember { Animatable(0f) }

    val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled
    // The same claim the player's scrubber runs through an Automix blend, but breathing slowly and
    // gently rather than strobing on the beat — the desktop's calm reading of a mix.
    val mixBlend = DesktopPlayerSettings.smartMixBlend.collectAsState()
    val reduceAnimation by DesktopPlayerSettings.reduceAnimation.collectAsState()
    val mixPulse = rememberMixPulse({ mixBlend.value }, enabled = !reduceAnimation)
    val currentProgress by rememberUpdatedState(progress)

    // Apple Music uses one calm strip for both player controls and window furniture. The left
    // sidebar owns the traffic lights; the rest is a balanced transport / now-playing / utility
    // layout with deliberately smaller glyphs than the phone player.
    DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // The window's own buttons live at the head of the sidebar; a compact window has no
            // sidebar, so there they lead this bar instead.
            if (compact && inlineCaption) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .desktopWindowGlass(DesktopChromeEdge.BOTTOM)
                        .padding(start = if (DesktopPlatform.isMac) 16.dp else 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    DesktopWindowButtons()
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .desktopWindowGlass(DesktopChromeEdge.BOTTOM)
                    .padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The mark leads the player's own controls, now that the sidebar runs to the top.
                Image(
                    painter = painterResource(Res.drawable.logo_mark),
                    contentDescription = "BitChord",
                    colorFilter = if (DesktopIsDark) null else androidx.compose.ui.graphics.ColorFilter.tint(DesktopInk),
                    modifier = Modifier.size(width = 28.dp, height = 18.dp),
                )
                Spacer(Modifier.width(14.dp))
                DesktopToolbarButton(onClick = onBack, enabled = canGoBack, size = 32.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        DesktopStrings["back", "Back"],
                        tint = if (canGoBack) DesktopInk else DesktopSecondary.copy(alpha = 0.40f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (!compact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopToolbarButton(onClick = { onShuffleChange(!shuffle) }, size = 32.dp) {
                            Icon(
                                BitChordIcons.Shuffle,
                                DesktopStrings["shuffle", "Shuffle"],
                                tint = if (shuffle) DesktopAccent else DesktopSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DesktopToolbarButton(
                            onClick = {
                                skipHint = -1f
                                if (!reduceAnimation) {
                                    scope.launch {
                                        prevNudge.snapTo(0f)
                                        prevNudge.animateTo(-4.5f, tween(durationMillis = 85))
                                        prevNudge.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
                                        )
                                    }
                                }
                                onPrevious()
                            },
                            enabled = previousEnabled,
                            size = 32.dp,
                        ) {
                            Icon(
                                Icons.Rounded.FastRewind,
                                DesktopStrings["widget_previous", "Previous"],
                                tint = if (previousEnabled) DesktopInk else DesktopSecondary.copy(alpha = 0.40f),
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer { translationX = prevNudge.value.dp.toPx() },
                            )
                        }
                        DesktopToolbarButton(onClick = onPlayPause, size = 32.dp) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = DesktopInk,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = {
                                        if (reduceAnimation) {
                                            EnterTransition.None togetherWith ExitTransition.None
                                        } else {
                                            (scaleIn(initialScale = 0.78f, animationSpec = tween(150)) + fadeIn(tween(150)))
                                                .togetherWith(
                                                    scaleOut(targetScale = 0.78f, animationSpec = tween(110)) + fadeOut(tween(110)),
                                                )
                                        }
                                    },
                                    label = "topBarPlayPause",
                                ) { playing ->
                                    Icon(
                                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        if (playing) "Pause" else "Play",
                                        tint = DesktopInk,
                                        modifier = Modifier.size(23.dp),
                                    )
                                }
                            }
                        }
                        DesktopToolbarButton(
                            onClick = {
                                skipHint = 1f
                                if (!reduceAnimation) {
                                    scope.launch {
                                        nextNudge.snapTo(0f)
                                        nextNudge.animateTo(4.5f, tween(durationMillis = 85))
                                        nextNudge.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
                                        )
                                    }
                                }
                                onNext()
                            },
                            size = 32.dp,
                        ) {
                            Icon(
                                Icons.Rounded.FastForward,
                                DesktopStrings["widget_next", "Next"],
                                tint = DesktopInk,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer { translationX = nextNudge.value.dp.toPx() },
                            )
                        }
                        DesktopToolbarButton(
                            onClick = { onRepeatModeChange(repeatMode.next()) },
                            size = 32.dp,
                        ) {
                            val tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else DesktopSecondary
                            if (repeatMode == DesktopRepeatMode.ONE) {
                                Text(
                                    "1",
                                    color = tint,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.semantics { contentDescription = "Repeat one" },
                                )
                            } else {
                                Icon(
                                    BitChordIcons.Repeat,
                                    "Repeat ${repeatMode.label()}",
                                    tint = tint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Box(
                        Modifier.weight(1f).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val compactBarInk = DesktopInk
                        val compactBarBg = if (DesktopIsDark) Color(0xFF323236) else LocalDesktopPalette.current.surfaceRaised
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 300.dp, max = 520.dp)
                                .fillMaxWidth()
                                .height(46.dp)
                                .border(1.dp, DesktopCardEdge, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onOpenNowPlaying),
                            color = compactBarBg,
                            tonalElevation = 0.dp,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (song == null) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            BitChordIcons.MusicNote,
                                            DesktopStrings["playback_channel_name", "Now playing"],
                                            tint = DesktopSecondary.copy(alpha = 0.65f),
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }
                                } else {
                                    AnimatedContent(
                                        targetState = song,
                                        contentKey = { it.videoId },
                                        transitionSpec = {
                                            if (reduceAnimation) {
                                                EnterTransition.None togetherWith ExitTransition.None
                                            } else {
                                                (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) {
                                                    (it * 0.14f * skipDirection).roundToInt()
                                                } + fadeIn(tween(220, easing = FastOutSlowInEasing))).togetherWith(
                                                    slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) {
                                                        (-it * 0.12f * skipDirection).roundToInt()
                                                    } + fadeOut(tween(160, easing = FastOutLinearInEasing)),
                                                )
                                            }
                                        },
                                        label = "topBarTrackTransition",
                                    ) { activeSong ->
                                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                            DesktopArtwork(
                                                activeSong.thumbnailUrl,
                                                Modifier.size(44.dp).clip(RoundedCornerShape(3.dp)),
                                                px = ROW_ART_PX,
                                            )
                                            Column(
                                                Modifier.weight(1f).padding(start = 12.dp, end = 56.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    activeSong.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    activeSong.artist,
                                                    color = DesktopSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                    // Drawn rather than sized: through a blend the line covers
                                    // the full width and pulses on the beat, as the player's
                                    // scrubber does, and reading either in draw keeps the frame
                                    // clock from recomposing the whole bar.
                                    val barMixInk = DesktopInk
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(start = 44.dp)
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .drawBehind {
                                                val base = currentProgress.coerceIn(0f, 1f)
                                                val fraction = base + (1f - base) * mixPulse.cover
                                                drawRect(
                                                    color = barMixInk.copy(alpha = mixPulse.alpha(0.48f)),
                                                    size = Size(size.width * fraction, size.height),
                                                )
                                            },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                val hideVolumeBar by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
                val bitPerfectStatus by DesktopBitPerfect.status.collectAsState()
                val dacVolumeLevel by DesktopDacVolume.level.collectAsState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!hideVolumeBar) {
                        val activeHwVol = if (bitPerfectStatus.active) dacVolumeLevel else null
                        val bpLocked = bitPerfectStatus.active && activeHwVol == null
                        val displayedVolume = activeHwVol ?: volume
                        Icon(
                            if (!bpLocked && displayedVolume <= 0.001f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            DesktopStrings["d_volume", "Volume"],
                            tint = DesktopSecondary.copy(alpha = if (bpLocked) 0.4f else 1f),
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        DesktopThinSlider(
                            value = if (bpLocked) 1f else displayedVolume,
                            onValueChange = { next ->
                                if (activeHwVol != null) DesktopDacVolume.set(next) else onVolumeChange(next)
                            },
                            enabled = !bpLocked,
                            idleHeight = 4.dp,
                            activeHeight = 8.dp,
                            activeColor = DesktopInk.copy(alpha = if (bpLocked) 0.35f else 0.92f),
                            modifier = Modifier.width(78.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    DesktopToolbarButton(onClick = onOpenAudioOutput, size = 32.dp) {
                        Icon(
                            Icons.Rounded.Headphones,
                            DesktopStrings["audio_output", "Audio output"],
                            tint = DesktopSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    DesktopToolbarButton(onClick = onOpenLyrics, size = 32.dp) {
                        Icon(
                            BitChordIcons.LyricsQuote,
                            DesktopStrings["lyrics", "Lyrics"],
                            tint = if (sidePanel == DesktopSidePanel.LYRICS) DesktopAccent else DesktopSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    DesktopToolbarButton(onClick = onOpenQueue, size = 32.dp) {
                        Icon(
                            BitChordIcons.Queue,
                            DesktopStrings["queue", "Queue"],
                            tint = if (sidePanel == DesktopSidePanel.QUEUE) DesktopAccent else DesktopSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    DesktopAccountButton(avatar = accountAvatar, onClick = onOpenAccounts)
                }
            }
            }
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                thickness = 1.dp,
                color = desktopChromeDivider(),
            )
        }
    }
}

@Composable
internal fun DesktopToolbarButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .desktopHoverWash()
            .desktopGlassPress(CircleShape, interactionSource = interactionSource, pressScale = 0.92f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DesktopSidebar(
    compact: Boolean = false,
    minimized: Boolean = compact,
    onToggleMinimize: () -> Unit = {},
    destination: DesktopDestination,
    settingsOpen: Boolean,
    accountPlaylists: List<ShelfItem>,
    localPlaylists: List<DesktopPlaylist>,
    openedCollectionId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onOpenAccountPlaylist: (ShelfItem) -> Unit,
    onOpenLocalPlaylist: (DesktopPlaylist) -> Unit,
    onDeleteAccountPlaylist: ((ShelfItem) -> Unit)? = null,
    onRenameLocalPlaylist: ((DesktopPlaylist) -> Unit)? = null,
    onDeleteLocalPlaylist: ((DesktopPlaylist) -> Unit)? = null,
    onOpenLikedMusic: () -> Unit = {},
    onOpenSettings: () -> Unit,
    /** Search was picked: the sidebar's box is the page's field, so it takes the focus. */
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    canGoBack: Boolean = false,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    accountAvatar: String? = null,
    accountName: String? = null,
    onOpenAccounts: () -> Unit = {},
) {
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            runCatching { searchFocusRequester.requestFocus() }
            onSearchFocused()
        }
    }
    val windowInfo = LocalWindowInfo.current
    var searchFocused by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            restoreSearchFocus = searchFocused
            DesktopArtworkCache.trimMemory()
        } else if (restoreSearchFocus) {
            // Let the desktop window finish its activation before asking Compose to put the native
            // text input target back in focus.
            yield()
            searchFocusRequester.requestFocus()
            restoreSearchFocus = false
        }
    }

    val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled
    // Rows the listener has tucked away; keyed the same way the sidebar lists them.
    val hiddenSidebar by DesktopSidebarVisibility.hidden.collectAsState()
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val floatingSidebarGlass = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val sidebarShape = RoundedCornerShape(20.dp)

    val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()
    val animatedSidebarWidth by animateDpAsState(
        targetValue = when {
            minimized && floatingSidebarGlass -> 72.dp
            minimized -> 64.dp
            floatingSidebarGlass -> 230.dp
            else -> 220.dp
        },
        animationSpec = if (reduceAnimation) {
            snap()
        } else {
            spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "desktopSidebarWidth",
    )

    Box(
        Modifier
            .width(animatedSidebarWidth)
            .fillMaxHeight()
            .then(
                if (floatingSidebarGlass) {
                    Modifier
                        .padding(start = 10.dp, top = if (inlineCaption) 8.dp else 10.dp, bottom = 10.dp)
                        .shadow(14.dp, sidebarShape)
                        .clip(sidebarShape)
                        .desktopSidebarGlass(sidebarShape)
                } else {
                    Modifier.desktopWindowGlass(DesktopChromeEdge.END, fade = 0.07f)
                },
            ),
    ) {
        AnimatedContent(
            targetState = minimized,
            transitionSpec = {
                if (reduceAnimation) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(200, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(tween(120, easing = FastOutLinearInEasing)))
                }
            },
            label = "desktopSidebarMode",
        ) { isMinimized ->
        if (isMinimized) {
            Column(
                Modifier.fillMaxSize().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (inlineCaption) {
                    DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(SIDEBAR_CAPTION_HEIGHT)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            DesktopWindowButtons()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (canGoBack) {
                    DesktopSidebarRailItem(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = DesktopStrings["back", "Back"],
                        selected = false,
                        onClick = onBack,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                DesktopSidebarRailItem(
                    icon = Icons.Rounded.Refresh,
                    label = DesktopStrings["d_refresh", "Refresh"],
                    selected = false,
                    onClick = onRefresh,
                )
                Spacer(Modifier.height(6.dp))
                DesktopSidebarRailItem(
                    icon = BitChordIcons.Search,
                    label = DesktopStrings["search", "Search"],
                    selected = destination == DesktopDestination.SEARCH,
                    onClick = { onDestinationSelected(DesktopDestination.SEARCH) },
                )
                Spacer(Modifier.height(8.dp))
                DesktopSidebarRailItem(
                    icon = BitChordIcons.Home,
                    label = DesktopStrings["home", "Home"],
                    selected = destination == DesktopDestination.LISTEN_NOW,
                    onClick = { onDestinationSelected(DesktopDestination.LISTEN_NOW) },
                )
                Spacer(Modifier.height(6.dp))
                DesktopSidebarRailItem(
                    icon = BitChordIcons.Explore,
                    label = DesktopStrings["explore", "Explore"],
                    selected = destination == DesktopDestination.EXPLORE,
                    onClick = { onDestinationSelected(DesktopDestination.EXPLORE) },
                )
                Spacer(Modifier.height(6.dp))
                DesktopSidebarRailItem(
                    icon = BitChordIcons.TabRadio,
                    label = DesktopStrings["radio", "Radio"],
                    selected = destination == DesktopDestination.RADIO,
                    onClick = { onDestinationSelected(DesktopDestination.RADIO) },
                )
                Spacer(Modifier.height(6.dp))
                DesktopSidebarRailItem(
                    icon = BitChordIcons.Library,
                    label = DesktopStrings["library", "Library"],
                    selected = destination == DesktopDestination.LIBRARY && openedCollectionId != "VLLM",
                    onClick = { onDestinationSelected(DesktopDestination.LIBRARY) },
                )
                Spacer(Modifier.height(6.dp))
                DesktopSidebarRailItem(
                    icon = BitChordIcons.HeartFilled,
                    label = DesktopStrings["auto_liked", "Liked songs"],
                    selected = openedCollectionId == "VLLM",
                    onClick = onOpenLikedMusic,
                )
                Spacer(Modifier.height(6.dp))
                if (HISTORY_KEY !in hiddenSidebar) {
                    DesktopSidebarRailItem(
                        icon = BitChordIcons.Clock,
                        label = DesktopStrings["history", "History"],
                        selected = destination == DesktopDestination.HISTORY,
                        onClick = { onDestinationSelected(DesktopDestination.HISTORY) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (DOWNLOADS_KEY !in hiddenSidebar) {
                    DesktopSidebarRailItem(
                        icon = BitChordIcons.Download,
                        label = DesktopStrings["downloads", "Downloads"],
                        selected = destination == DesktopDestination.DOWNLOADS,
                        onClick = { onDestinationSelected(DesktopDestination.DOWNLOADS) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                DesktopSidebarRailPlaylistsItem(
                    accountPlaylists = accountPlaylists,
                    localPlaylists = localPlaylists,
                    hiddenSidebar = hiddenSidebar,
                    openedCollectionId = openedCollectionId,
                    onOpenAccountPlaylist = onOpenAccountPlaylist,
                    onOpenLocalPlaylist = onOpenLocalPlaylist,
                )
                Spacer(Modifier.weight(1f))
                DesktopAccountButton(avatar = accountAvatar, onClick = onOpenAccounts)
                if (!compact) {
                    Spacer(Modifier.height(8.dp))
                    DesktopSidebarRailItem(
                        icon = BitChordIcons.ChevronRight,
                        label = DesktopStrings["d_expand_sidebar", "Expand sidebar"],
                        selected = false,
                        onClick = onToggleMinimize,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // The sidebar runs to the top of the window, so its head is the window's caption: the
                // three buttons, and room to take hold of the window by.
                if (inlineCaption) {
                    DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(SIDEBAR_CAPTION_HEIGHT)) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (DesktopPlatform.isMac) 16.dp else 10.dp,
                                    end = 10.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DesktopWindowButtons()
                                if (canGoBack) {
                                    Spacer(Modifier.width(8.dp))
                                    DesktopToolbarButton(
                                        onClick = onBack,
                                        size = 26.dp,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = DesktopStrings["back", "Back"],
                                            tint = DesktopInk,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                DesktopToolbarButton(
                                    onClick = onRefresh,
                                    size = 26.dp,
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = DesktopStrings["d_refresh", "Refresh"],
                                        tint = DesktopSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            DesktopToolbarButton(
                                onClick = onToggleMinimize,
                                size = 26.dp,
                            ) {
                                Icon(
                                    Icons.Rounded.ChevronLeft,
                                    contentDescription = "Minimize sidebar",
                                    tint = DesktopSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = if (inlineCaption) 4.dp else 14.dp, bottom = 14.dp),
                ) {
                    if (!inlineCaption) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (canGoBack) {
                                    DesktopToolbarButton(
                                        onClick = onBack,
                                        size = 24.dp,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = DesktopStrings["back", "Back"],
                                            tint = DesktopInk,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                DesktopToolbarButton(
                                    onClick = onRefresh,
                                    size = 24.dp,
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = DesktopStrings["d_refresh", "Refresh"],
                                        tint = DesktopSecondary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            DesktopToolbarButton(
                                onClick = onToggleMinimize,
                                size = 24.dp,
                            ) {
                                Icon(
                                    Icons.Rounded.ChevronLeft,
                                    contentDescription = DesktopStrings["d_minimize_sidebar", "Minimize sidebar"],
                                    tint = DesktopSecondary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                    DesktopSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = onSearch,
                        focusRequester = searchFocusRequester,
                        onFocusChanged = { searchFocused = it },
                    )
            Spacer(Modifier.height(20.dp))
            DesktopSidebarItem(BitChordIcons.Home, DesktopStrings["home", "Home"], destination == DesktopDestination.LISTEN_NOW) {
                onDestinationSelected(DesktopDestination.LISTEN_NOW)
            }
            DesktopSidebarItem(BitChordIcons.Explore, DesktopStrings["explore", "Explore"], destination == DesktopDestination.EXPLORE) {
                onDestinationSelected(DesktopDestination.EXPLORE)
            }
            DesktopSidebarItem(BitChordIcons.TabRadio, DesktopStrings["radio", "Radio"], destination == DesktopDestination.RADIO) {
                onDestinationSelected(DesktopDestination.RADIO)
            }
            DesktopSidebarItem(BitChordIcons.Library, DesktopStrings["library", "Library"], destination == DesktopDestination.LIBRARY && openedCollectionId != "VLLM") {
                onDestinationSelected(DesktopDestination.LIBRARY)
            }
            DesktopSidebarItem(
                BitChordIcons.HeartFilled,
                DesktopStrings["auto_liked", "Liked songs"],
                openedCollectionId == "VLLM",
            ) {
                onOpenLikedMusic()
            }
            DesktopSidebarItem(BitChordIcons.Search, DesktopStrings["search", "Search"], destination == DesktopDestination.SEARCH) {
                onDestinationSelected(DesktopDestination.SEARCH)
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = desktopChromeDivider())
            Spacer(Modifier.height(12.dp))
            if (HISTORY_KEY !in hiddenSidebar) {
                DesktopSidebarItem(
                    BitChordIcons.Clock,
                    DesktopStrings["history", "History"],
                    destination == DesktopDestination.HISTORY,
                    onClick = { onDestinationSelected(DesktopDestination.HISTORY) },
                    onHide = { DesktopSidebarVisibility.hide(HISTORY_KEY, DesktopStrings["history", "History"]) },
                )
            }
            val queued by DesktopDownloadQueue.active.collectAsState()
            if (DOWNLOADS_KEY !in hiddenSidebar) {
                DesktopSidebarItem(
                    BitChordIcons.Download,
                    if (queued.isEmpty()) DesktopStrings["downloads", "Downloads"] else "${DesktopStrings["downloads", "Downloads"]} · ${queued.size}",
                    destination == DesktopDestination.DOWNLOADS,
                    onClick = { onDestinationSelected(DesktopDestination.DOWNLOADS) },
                    onHide = { DesktopSidebarVisibility.hide(DOWNLOADS_KEY, DesktopStrings["downloads", "Downloads"]) },
                )
            }
            if (LOCAL_MUSIC_KEY !in hiddenSidebar) {
                DesktopSidebarItem(
                    BitChordIcons.Library,
                    DesktopStrings["local_music", "Local Music"],
                    destination == DesktopDestination.LOCAL_MUSIC,
                    onClick = { onDestinationSelected(DesktopDestination.LOCAL_MUSIC) },
                    onHide = { DesktopSidebarVisibility.hide(LOCAL_MUSIC_KEY, DesktopStrings["local_music", "Local Music"]) },
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = desktopChromeDivider())
            Spacer(Modifier.height(8.dp))
            Text(
                text = DesktopStrings["playlists", "Playlists"],
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = DesktopSecondary.copy(alpha = 0.65f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(2.dp))
            val visibleAccount = accountPlaylists
                .filterNot { "account:${it.browseId}" in hiddenSidebar }
                .filterNot {
                    it.browseId == "VLLM" || it.browseId == "LM" ||
                        it.title.equals("Liked Music", ignoreCase = true) ||
                        it.title.equals("Musik yang Disukai", ignoreCase = true)
                }
            val visibleLocal = localPlaylists.filterNot { "local:${it.id}" in hiddenSidebar }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(visibleAccount, key = { "account:${it.browseId}" }) { playlist ->
                    DesktopSidebarItem(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        label = playlist.title,
                        selected = openedCollectionId == playlist.browseId,
                        onDelete = onDeleteAccountPlaylist?.let { del -> { del(playlist) } },
                        onClick = { onOpenAccountPlaylist(playlist) },
                        onHide = {
                            DesktopSidebarVisibility.hide("account:${playlist.browseId}", playlist.title)
                        },
                    )
                }
                items(visibleLocal, key = { "local:${it.id}" }) { playlist ->
                    DesktopSidebarItem(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        label = playlist.title,
                        selected = openedCollectionId == playlist.id,
                        onRename = onRenameLocalPlaylist?.let { ren -> { ren(playlist) } },
                        onDelete = onDeleteLocalPlaylist?.let { del -> { del(playlist) } },
                        onClick = { onOpenLocalPlaylist(playlist) },
                        onHide = { DesktopSidebarVisibility.hide("local:${playlist.id}", playlist.title) },
                    )
                }
                if (visibleAccount.isEmpty() && visibleLocal.isEmpty()) {
                    item {
                        Text(
                            DesktopStrings["d_playlists_empty_hint", "Your playlists will appear here"],
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            color = DesktopSecondary.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            HorizontalDivider(color = desktopChromeDivider())
            Spacer(Modifier.height(8.dp))
            if (hiddenSidebar.isNotEmpty()) {
                DesktopSidebarRestoreRow(hiddenSidebar)
            }
            val accountInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .desktopGlassPress(RoundedCornerShape(10.dp), interactionSource = accountInteraction, pressScale = 0.97f)
                    .clip(RoundedCornerShape(10.dp))
                    .desktopHoverFill(corner = 10.dp, alpha = 0.12f)
                    .clickable(
                        interactionSource = accountInteraction,
                        indication = null,
                        onClick = onOpenAccounts,
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (accountAvatar != null) {
                    DesktopArtwork(
                        accountAvatar,
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, DesktopCardEdge, CircleShape),
                        contentScale = ContentScale.Crop,
                        px = 96,
                    )
                } else {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DesktopInk.copy(alpha = 0.12f))
                            .border(1.dp, DesktopCardEdge, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            null,
                            tint = DesktopInk.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = accountName ?: DesktopStrings["accounts", "Account"],
                        color = DesktopInk,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DesktopStrings["switch_account", "Switch account"],
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        }
        }
        }
        if (!floatingSidebarGlass) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(desktopChromeDivider()),
            )
        }
    }
}

@Composable
private fun DesktopSidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    /** When set, a right-click offers to rename the item. */
    onRename: (() -> Unit)? = null,
    /** When set, a right-click offers to delete the item. */
    onDelete: (() -> Unit)? = null,
    /** When set, a right-click offers to hide the row — see [DesktopSidebarVisibility]. */
    onHide: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onRename != null || onDelete != null || onHide != null
    val interactionSource = remember { MutableInteractionSource() }
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassSurface = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val itemShape = RoundedCornerShape(9.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .desktopHoverFill(corner = 9.dp)
                .desktopGlassPress(
                    shape = itemShape,
                    interactionSource = interactionSource,
                    pressScale = 0.975f,
                    hoverScale = 1.008f,
                )
                .clip(itemShape)
                .then(
                    if (selected) {
                        val ink = DesktopInk
                        val isDark = DesktopIsDark
                        if (glassSurface) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        if (isDark) {
                                            listOf(
                                                Color.White.copy(alpha = 0.18f),
                                                Color.White.copy(alpha = 0.09f),
                                            )
                                        } else {
                                            listOf(
                                                Color.White.copy(alpha = 0.64f),
                                                Color.White.copy(alpha = 0.36f),
                                            )
                                        },
                                    ),
                                )
                                .border(
                                    0.75.dp,
                                    Brush.linearGradient(
                                        if (isDark) {
                                            listOf(
                                                Color.White.copy(alpha = 0.24f),
                                                Color.White.copy(alpha = 0.06f),
                                            )
                                        } else {
                                            listOf(
                                                ink.copy(alpha = 0.11f),
                                                ink.copy(alpha = 0.04f),
                                            )
                                        },
                                    ),
                                    itemShape,
                                )
                        } else {
                            Modifier.background(ink.copy(alpha = if (isDark) 0.13f else 0.09f))
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .then(
                    if (!hasMenu) Modifier
                    else Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    menuOpen = true
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
                )
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, label, tint = if (selected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = if (selected) DesktopInk else DesktopSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasMenu) {
            val menuShape = RoundedCornerShape(14.dp)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier
                    .shadow(14.dp, menuShape)
                    .clip(menuShape)
                    .desktopBarGlass(menuShape),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                shape = menuShape,
            ) {
                if (onRename != null) {
                    DropdownMenuItem(
                        text = { Text(DesktopStrings["rename", "Rename"]) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                }
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text(DesktopStrings["delete", "Delete"]) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
                if (onHide != null) {
                    DropdownMenuItem(
                        text = { Text(DesktopStrings["hide_from_sidebar", "Hide from sidebar"]) },
                        onClick = {
                            menuOpen = false
                            onHide()
                        },
                    )
                }
            }
        }
    }
}

/** The secondary destinations that can be tucked away, by the key they hide under. */
private const val HISTORY_KEY = "dest:HISTORY"
private const val DOWNLOADS_KEY = "dest:DOWNLOADS"
private const val LOCAL_MUSIC_KEY = "dest:LOCAL_MUSIC"

/**
 * The "N hidden" row that brings tucked-away rows back: a menu of everything
 * hidden, each restoring on its own, plus a "Show all". Only drawn while
 * something is hidden, so an ordinary sidebar never shows it.
 */
@Composable
private fun DesktopSidebarRestoreRow(hidden: Map<String, String>) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val restoreRowShape = RoundedCornerShape(7.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .desktopHoverFill()
                .desktopGlassPress(restoreRowShape, interactionSource = interactionSource, pressScale = 0.975f)
                .clip(restoreRowShape)
                .clickable(interactionSource = interactionSource, indication = null) { open = true }
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.RemoveCircleOutline,
                null,
                tint = DesktopSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                DesktopStrings.format("n_hidden", hidden.size, fallback = "%1\$d hidden"),
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val restoreShape = RoundedCornerShape(14.dp)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .shadow(14.dp, restoreShape)
                .clip(restoreShape)
                .desktopBarGlass(restoreShape),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = restoreShape,
        ) {
            hidden.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        DesktopSidebarVisibility.show(key)
                        if (DesktopSidebarVisibility.hidden.value.isEmpty()) open = false
                    },
                )
            }
            if (hidden.size > 1) {
                DropdownMenuItem(
                    text = { Text(DesktopStrings["show_all", "Show all"]) },
                    onClick = {
                        DesktopSidebarVisibility.showAll()
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Modifier.desktopRailGlassTile(
    selected: Boolean,
    hovered: Boolean,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()
    val glassEnabled = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val hoverProgress by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceAnimation) 0 else 130),
        label = "railHover",
    )
    val ink = DesktopInk
    val isDark = DesktopIsDark
    if (!glassEnabled) {
        return this
            .clip(shape)
            .background(
                when {
                    selected -> DesktopAccent.copy(alpha = if (isDark) 0.20f else 0.12f)
                    hoverProgress > 0.01f -> ink.copy(alpha = 0.09f * hoverProgress)
                    else -> Color.Transparent
                },
            )
    }
    val fillBrush = when {
        selected -> Brush.verticalGradient(
            if (isDark) {
                listOf(
                    DesktopAccent.copy(alpha = 0.26f + 0.06f * hoverProgress),
                    DesktopAccent.copy(alpha = 0.14f + 0.04f * hoverProgress),
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.66f),
                    Color.White.copy(alpha = 0.38f + 0.08f * hoverProgress),
                )
            },
        )
        hoverProgress > 0.01f -> Brush.verticalGradient(
            listOf(
                ink.copy(alpha = (if (isDark) 0.17f else 0.11f) * hoverProgress),
                ink.copy(alpha = 0.05f * hoverProgress),
            ),
        )
        else -> null
    }
    val rimAlpha = if (selected) 0.20f + 0.07f * hoverProgress else 0.18f * hoverProgress
    return this
        .graphicsLayer {
            val scale = if (!reduceAnimation) 1f + 0.045f * hoverProgress else 1f
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .then(if (fillBrush != null) Modifier.background(fillBrush, shape) else Modifier)
        .then(
            if (rimAlpha > 0.01f) {
                Modifier.border(
                    width = 0.85.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            ink.copy(alpha = rimAlpha),
                            ink.copy(alpha = rimAlpha * 0.28f),
                        ),
                    ),
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

@Composable
private fun DesktopSidebarRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val tileShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .desktopRailGlassTile(selected = selected, hovered = hovered, shape = tileShape)
            .desktopGlassPress(tileShape, interactionSource = interactionSource, pressScale = 0.92f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        hovered = event.type == PointerEventType.Enter || event.type == PointerEventType.Move
                        if (event.type == PointerEventType.Exit) hovered = false
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                selected -> DesktopAccent
                hovered -> DesktopInk
                else -> DesktopSecondary
            },
            modifier = Modifier.size(20.dp),
        )
        if (hovered && label.isNotBlank()) {
            val density = LocalDensity.current
            val offsetX = with(density) { 48.dp.roundToPx() }
            val tooltipShape = RoundedCornerShape(12.dp)
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(x = offsetX, y = 0),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .desktopPopupMotion(pivotX = 0f, pivotY = 0.5f, slideXDp = (-6).dp, slideYDp = 0.dp)
                        .shadow(12.dp, tooltipShape)
                        .clip(tooltipShape)
                        .desktopBarGlass(tooltipShape, tintAlpha = 0.52f, borderAlpha = 0.14f)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        color = DesktopInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebarRailPlaylistsItem(
    accountPlaylists: List<ShelfItem>,
    localPlaylists: List<DesktopPlaylist>,
    hiddenSidebar: Map<String, String>,
    openedCollectionId: String?,
    onOpenAccountPlaylist: (ShelfItem) -> Unit,
    onOpenLocalPlaylist: (DesktopPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleAccount = remember(accountPlaylists, hiddenSidebar) {
        accountPlaylists
            .filterNot { "account:${it.browseId}" in hiddenSidebar }
            .filterNot {
                it.browseId == "VLLM" || it.browseId == "LM" ||
                    it.title.equals("Liked Music", ignoreCase = true) ||
                    it.title.equals("Musik yang Disukai", ignoreCase = true)
            }
    }
    val visibleLocal = remember(localPlaylists, hiddenSidebar) {
        localPlaylists.filterNot { "local:${it.id}" in hiddenSidebar }
    }
    val anySelected = (visibleAccount.any { openedCollectionId == it.browseId }) ||
        (visibleLocal.any { openedCollectionId == it.id })

    var iconHovered by remember { mutableStateOf(false) }
    var flyoutHovered by remember { mutableStateOf(false) }
    var showFlyout by remember { mutableStateOf(false) }

    LaunchedEffect(iconHovered, flyoutHovered) {
        if (iconHovered || flyoutHovered) {
            showFlyout = true
        } else {
            delay(220)
            if (!iconHovered && !flyoutHovered) {
                showFlyout = false
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val tileShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .desktopRailGlassTile(selected = anySelected, hovered = iconHovered || showFlyout, shape = tileShape)
            .desktopGlassPress(tileShape, interactionSource = interactionSource, pressScale = 0.92f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        iconHovered = event.type == PointerEventType.Enter || event.type == PointerEventType.Move
                        if (event.type == PointerEventType.Exit) iconHovered = false
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) { showFlyout = !showFlyout },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
            contentDescription = DesktopStrings["playlists", "Playlists"],
            tint = when {
                anySelected -> DesktopAccent
                iconHovered || showFlyout -> DesktopInk
                else -> DesktopSecondary
            },
            modifier = Modifier.size(20.dp),
        )

        if (showFlyout) {
            val density = LocalDensity.current
            val offsetX = with(density) { 48.dp.roundToPx() }
            val shape = RoundedCornerShape(16.dp)
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(x = offsetX, y = 0),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .desktopPopupMotion(pivotX = 0f, pivotY = 0.5f, slideXDp = (-8).dp, slideYDp = 0.dp)
                        .shadow(16.dp, shape)
                        .clip(shape)
                        .desktopBarGlass(shape, tintAlpha = 0.54f, borderAlpha = 0.15f)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    flyoutHovered = event.type == PointerEventType.Enter || event.type == PointerEventType.Move
                                    if (event.type == PointerEventType.Exit) flyoutHovered = false
                                }
                            }
                        }
                        .widthIn(min = 210.dp, max = 280.dp),
                ) {
                    Column(
                        Modifier
                            .padding(vertical = 8.dp)
                            .heightIn(max = 380.dp),
                    ) {
                        Text(
                            text = DesktopStrings["playlists", "Playlists"],
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopSecondary.copy(alpha = 0.70f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                        HorizontalDivider(
                            color = desktopChromeDivider(),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        if (visibleAccount.isEmpty() && visibleLocal.isEmpty()) {
                            Text(
                                text = DesktopStrings["d_playlists_empty_hint", "Your playlists will appear here"],
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                            ) {
                                items(visibleAccount, key = { "account:${it.browseId}" }) { item ->
                                    val isSelected = openedCollectionId == item.browseId
                                    DesktopSidebarFlyoutItem(
                                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                        title = item.title,
                                        selected = isSelected,
                                        onClick = {
                                            showFlyout = false
                                            iconHovered = false
                                            flyoutHovered = false
                                            onOpenAccountPlaylist(item)
                                        },
                                    )
                                }
                                items(visibleLocal, key = { "local:${it.id}" }) { item ->
                                    val isSelected = openedCollectionId == item.id
                                    DesktopSidebarFlyoutItem(
                                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                        title = item.title,
                                        selected = isSelected,
                                        onClick = {
                                            showFlyout = false
                                            iconHovered = false
                                            flyoutHovered = false
                                            onOpenLocalPlaylist(item)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebarFlyoutItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ink = DesktopInk
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ink.copy(alpha = 0.12f) else Color.Transparent)
            .desktopHoverFill(corner = 8.dp, alpha = 0.10f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) DesktopAccent else DesktopSecondary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) DesktopInk else DesktopInk.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun HoverableLinkText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DesktopSecondary,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 12.sp,
    softWrap: Boolean = false,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Text(
        text = text,
        color = if (isHovered) DesktopInk else color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        textDecoration = if (isHovered) TextDecoration.Underline else TextDecoration.None,
        maxLines = 1,
        softWrap = softWrap,
        overflow = overflow,
        modifier = modifier
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun DesktopTimelineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    scrubberAlpha: Float,
    interactionSource: MutableInteractionSource,
    bestPartFraction: Float? = null,
    showBestPart: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ink = DesktopInk
    val isDark = DesktopIsDark
    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }
                    onValueChangeFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackH = (2.5f + (5.0f - 2.5f) * scrubberAlpha).dp.toPx()
            val thumbR = (5.5f * scrubberAlpha).dp.toPx()
            val corner = CornerRadius(trackH / 2f)

            val inactiveAlpha = 0.16f + (0.28f - 0.16f) * scrubberAlpha
            val activeAlpha = 0.75f + (0.95f - 0.75f) * scrubberAlpha

            val trackTop = centerY - trackH / 2f

            // Inactive background track
            drawRoundRect(
                color = ink.copy(alpha = inactiveAlpha),
                topLeft = Offset(0f, trackTop),
                size = androidx.compose.ui.geometry.Size(size.width, trackH),
                cornerRadius = corner,
            )

            // Best Part highlight marker on the scrubber track
            if (showBestPart && bestPartFraction != null && bestPartFraction in 0.005f..0.995f) {
                val markerX = size.width * bestPartFraction
                val markerW = 3.dp.toPx()
                val markerH = trackH + 3.5.dp.toPx()
                val markerAlpha = 0.85f * (0.65f + 0.35f * scrubberAlpha)
                val markerTint = if (isDark) Color(0xFFFFD54F) else Color(0xFFD99500)
                drawRoundRect(
                    color = markerTint.copy(alpha = markerAlpha),
                    topLeft = Offset(markerX - markerW / 2f, centerY - markerH / 2f),
                    size = androidx.compose.ui.geometry.Size(markerW, markerH),
                    cornerRadius = CornerRadius(markerW / 2f),
                )
            }

            // Active played track
            val frac = value.coerceIn(0f, 1f)
            val playedWidth = size.width * frac
            if (playedWidth > 0f) {
                drawRoundRect(
                    color = ink.copy(alpha = activeAlpha),
                    topLeft = Offset(0f, trackTop),
                    size = androidx.compose.ui.geometry.Size(playedWidth, trackH),
                    cornerRadius = corner,
                )
            }

            // Circle thumb handle (smoothly scales up with scrubberAlpha)
            if (thumbR > 0.5f) {
                drawCircle(
                    color = ink,
                    radius = thumbR,
                    center = Offset(playedWidth.coerceIn(thumbR, size.width - thumbR), centerY),
                )
            }
        }
    }
}

internal fun shouldHandleBackPointerGesture(
    gestureEnabled: Boolean,
    isBackPressed: Boolean,
): Boolean = gestureEnabled && isBackPressed

internal fun resolveSharedPageTopPadding(inlineCaption: Boolean): Dp =
    if (inlineCaption) 4.dp else 14.dp

internal fun resolveTrackSkipDirection(
    previousId: String?,
    nextId: String?,
    hint: Float,
): Float = if (previousId != nextId && hint < 0f) -1f else 1f

@Composable
private fun rememberTrackSkipDirection(songId: String?, hint: Float): Float {
    val previousId = remember { mutableStateOf(songId) }
    val direction = remember { mutableStateOf(1f) }
    if (previousId.value != songId) {
        direction.value = resolveTrackSkipDirection(previousId.value, songId, hint)
        previousId.value = songId
    }
    return direction.value
}

@Composable
private fun DesktopAppleMusicBottomBar(
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    progress: Float,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Float) -> Unit,
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenArtist: ((id: String?, name: String) -> Unit)? = null,
    onOpenAlbum: ((id: String?, name: String?) -> Unit)? = null,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: (() -> Unit)? = null,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    sidePanel: DesktopSidePanel?,
    menu: (@Composable () -> Unit)? = null,
) {
    if (song == null) return

    val scope = rememberCoroutineScope()
    val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()
    var skipHint by remember { mutableStateOf(1f) }
    val skipDirection = rememberTrackSkipDirection(song.videoId, skipHint)
    val prevNudge = remember { Animatable(0f) }
    val nextNudge = remember { Animatable(0f) }

    val shape = RoundedCornerShape(percent = 50)
    val sidePanelPadding by animateDpAsState(
        targetValue = if (sidePanel != null) 360.dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bottomBarSidePanelPadding",
    )
    val maxBarWidth by animateDpAsState(
        targetValue = if (sidePanel != null) 600.dp else 760.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "bottomBarMaxWidth",
    )

    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableStateOf(0f) }
    val displayedProgress = if (isSeeking) seekProgress else progress.coerceIn(0f, 1f)

    val showTotalDuration by DesktopPlayerSettings.showTotalDuration.collectAsState()
    val showBestPart by DesktopPlayerSettings.showBestPart.collectAsState()
    val bestPartMs by produceState<Long?>(null, song.videoId) {
        value = runCatching { DesktopBestParts.startFor(song).takeIf { it.source != BestPartStart.Source.FALLBACK }?.startMs }.getOrNull()
    }
    val bestPartFraction = remember(bestPartMs, durationMs) {
        if (durationMs > 0 && bestPartMs != null) (bestPartMs!!.toFloat() / durationMs).coerceIn(0f, 1f) else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp + sidePanelPadding,
                top = 10.dp,
                bottom = 10.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
        val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
        val useRealLiquidGlass = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
        Surface(
            modifier = Modifier
                .widthIn(max = maxBarWidth)
                .fillMaxWidth()
                .height(60.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .then(
                    if (useRealLiquidGlass) {
                        Modifier.desktopLiquidGlass(
                            shape = shape,
                            backdrop = LocalDesktopContentBackdrop.current ?: LocalDesktopBackdrop.current,
                            underlayBackdrop = LocalDesktopChromeBackdrop.current,
                            tintAlpha = 0.42f,
                            borderAlpha = 0.15f,
                        )
                    } else {
                        Modifier.desktopBarGlass(shape)
                    },
                ),
            shape = shape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: 5 playback controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                DesktopToolbarButton(
                    onClick = { onShuffleChange(!shuffle) },
                    size = 30.dp,
                ) {
                    Icon(
                        BitChordIcons.Shuffle,
                        DesktopStrings["shuffle", "Shuffle"],
                        tint = if (shuffle) DesktopAccent else DesktopInk.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                DesktopToolbarButton(
                    onClick = {
                        skipHint = -1f
                        if (!reduceAnimation) {
                            scope.launch {
                                prevNudge.snapTo(0f)
                                prevNudge.animateTo(-4.5f, tween(durationMillis = 85))
                                prevNudge.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
                                )
                            }
                        }
                        onPrevious()
                    },
                    enabled = previousEnabled,
                    size = 30.dp,
                ) {
                    Icon(
                        Icons.Rounded.FastRewind,
                        DesktopStrings["widget_previous", "Previous"],
                        tint = if (previousEnabled) DesktopInk else DesktopSecondary.copy(alpha = 0.40f),
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer { translationX = prevNudge.value.dp.toPx() },
                    )
                }
                DesktopToolbarButton(
                    onClick = onPlayPause,
                    size = 34.dp,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DesktopInk,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                if (reduceAnimation) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (scaleIn(initialScale = 0.78f, animationSpec = tween(150)) + fadeIn(tween(150)))
                                        .togetherWith(
                                            scaleOut(targetScale = 0.78f, animationSpec = tween(110)) + fadeOut(tween(110)),
                                        )
                                }
                            },
                            label = "miniPlayerPlayPause",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (playing) "Pause" else "Play",
                                tint = DesktopInk,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                DesktopToolbarButton(
                    onClick = {
                        skipHint = 1f
                        if (!reduceAnimation) {
                            scope.launch {
                                nextNudge.snapTo(0f)
                                nextNudge.animateTo(4.5f, tween(durationMillis = 85))
                                nextNudge.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
                                )
                            }
                        }
                        onNext()
                    },
                    size = 30.dp,
                ) {
                    Icon(
                        Icons.Rounded.FastForward,
                        DesktopStrings["widget_next", "Next"],
                        tint = DesktopInk,
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer { translationX = nextNudge.value.dp.toPx() },
                    )
                }
                DesktopToolbarButton(
                    onClick = { onRepeatModeChange(repeatMode.next()) },
                    size = 30.dp,
                ) {
                    val tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else DesktopInk.copy(alpha = 0.72f)
                    if (repeatMode == DesktopRepeatMode.ONE) {
                        Text(
                            "1",
                            color = tint,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    } else {
                        Icon(
                            BitChordIcons.Repeat,
                            "Repeat ${repeatMode.label()}",
                            tint = tint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // Center: Unified Apple Music Section (Cover art + Title/Artist + Scrubber)
            val artInteractionSource = remember { MutableInteractionSource() }
                val isArtHovered by artInteractionSource.collectIsHoveredAsState()
                val artScale by animateFloatAsState(
                    targetValue = if (isArtHovered) 1.08f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "artScale",
                )
                val artHoverAlpha by animateFloatAsState(
                    targetValue = if (isArtHovered) 1f else 0f,
                    animationSpec = tween(durationMillis = 140),
                    label = "artHoverAlpha",
                )

                val sliderInteractionSource = remember { MutableInteractionSource() }
                val isSliderHovered by sliderInteractionSource.collectIsHoveredAsState()
                val isScrubActive = isSliderHovered || isSeeking

                val textBlur by animateDpAsState(
                    targetValue = if (isScrubActive) 9.dp else 0.dp,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "textBlur",
                )
                val textAlpha by animateFloatAsState(
                    targetValue = if (isScrubActive) 0.18f else 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "textAlpha",
                )
                val scrubberAlpha by animateFloatAsState(
                    targetValue = if (isScrubActive) 1f else 0f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                    label = "scrubberAlpha",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    // 1. Background layer: Cover art & Track info (pinned cleanly at TopStart above the scrubber)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = textAlpha }
                            .then(
                                if (textBlur > 0.5.dp) {
                                    Modifier.blur(textBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        AnimatedContent(
                            targetState = song,
                            contentKey = { it.videoId },
                            transitionSpec = {
                                if (reduceAnimation) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) {
                                        (it * 0.14f * skipDirection).roundToInt()
                                    } + fadeIn(tween(220, easing = FastOutSlowInEasing))).togetherWith(
                                        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) {
                                            (-it * 0.12f * skipDirection).roundToInt()
                                        } + fadeOut(tween(160, easing = FastOutLinearInEasing)),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopStart,
                            label = "miniPlayerTrackTransition",
                        ) { activeSong ->
                            Row(
                                modifier = Modifier
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            // Cover art: zoom on hover + maximize icon overlay + click to maximize
                            Box(
                                modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .hoverable(artInteractionSource)
                                .clickable(
                                    interactionSource = artInteractionSource,
                                    indication = null,
                                    onClick = onOpenNowPlaying,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                DesktopArtwork(
                                    activeSong.thumbnailUrl,
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = artScale
                                            scaleY = artScale
                                        },
                                    px = ROW_ART_PX,
                                )
                                if (artHoverAlpha > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = artHoverAlpha }
                                            .background(Color.Black.copy(alpha = 0.52f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Rounded.OpenInFull,
                                            contentDescription = DesktopStrings["d_maximize_player", "Maximize player"],
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            // Title & Multi-Artist / Album Column (tight 0.5 - 1 px gap between title and artist)
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = activeSong.title,
                                    color = DesktopInk,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 2000,
                                        repeatDelayMillis = 1500,
                                        velocity = 30.dp,
                                    ),
                                )
                                Spacer(Modifier.height(0.5.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 2000,
                                            repeatDelayMillis = 1500,
                                            velocity = 30.dp,
                                        )
                                        .clipToBounds(),
                                ) {
                                    val credits = remember(activeSong.artist, activeSong.artists, activeSong.artistId) {
                                        artistCredits(activeSong.artist, activeSong.artists, activeSong.artistId)
                                    }
                                    val segments = remember(activeSong.artist, credits) {
                                        creditSegments(activeSong.artist, credits)
                                    }
                                    val albumName = activeSong.albumName
                                    val albumId = activeSong.albumId

                                    segments.forEach { segment ->
                                        val credit = segment.credit
                                        if (credit != null) {
                                            HoverableLinkText(
                                                text = segment.text,
                                                onClick = { onOpenArtist?.invoke(credit.browseId, credit.name) },
                                                fontSize = 11.sp,
                                                lineHeight = 12.sp,
                                            )
                                        } else {
                                            Text(
                                                text = segment.text,
                                                color = DesktopSecondary,
                                                fontSize = 11.sp,
                                                lineHeight = 12.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                    if (!albumName.isNullOrBlank()) {
                                        Text(
                                            text = " — ",
                                            color = DesktopSecondary.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            lineHeight = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        HoverableLinkText(
                                            text = albumName,
                                            onClick = {
                                                onOpenAlbum?.invoke(albumId, albumName)
                                            },
                                            fontSize = 11.sp,
                                            lineHeight = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    // 2. Active timestamps above scrubber
                    if (scrubberAlpha > 0.01f) {
                        val currentMs = if (isSeeking) {
                            (seekProgress * durationMs).toLong().coerceIn(0L, durationMs)
                        } else {
                            positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                        }
                        val elapsedStr = formatTime(currentMs)
                        val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)
                        val remainingStr = if (showTotalDuration && durationMs > 0) {
                            formatTime(durationMs)
                        } else {
                            "-" + formatTime(remainingMs)
                        }

                        // Timestamps above the scrubber track
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 18.dp)
                                .graphicsLayer { alpha = scrubberAlpha },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = elapsedStr,
                                color = DesktopInk.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = remainingStr,
                                color = DesktopInk.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // 3. Unified timeline slider (always responsive to clicks and hover with generous hit target)
                    DesktopTimelineSlider(
                        value = displayedProgress,
                        onValueChange = { frac ->
                            isSeeking = true
                            seekProgress = frac
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            onSeek(seekProgress)
                        },
                        scrubberAlpha = scrubberAlpha,
                        bestPartFraction = bestPartFraction,
                        showBestPart = showBestPart,
                        interactionSource = sliderInteractionSource,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                            .height(22.dp),
                    )
                }

            Spacer(Modifier.width(8.dp))

            // Right: Lyrics, Queue, More (...), Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DesktopToolbarButton(
                    onClick = onOpenLyrics,
                    size = 30.dp,
                ) {
                    Icon(
                        BitChordIcons.LyricsQuote,
                        DesktopStrings["lyrics", "Lyrics"],
                        tint = if (sidePanel == DesktopSidePanel.LYRICS) DesktopAccent else DesktopInk.copy(alpha = 0.80f),
                        modifier = Modifier.size(17.dp),
                    )
                }
                DesktopToolbarButton(
                    onClick = onOpenQueue,
                    size = 30.dp,
                ) {
                    Icon(
                        BitChordIcons.Queue,
                        DesktopStrings["queue", "Queue"],
                        tint = if (sidePanel == DesktopSidePanel.QUEUE) DesktopAccent else DesktopInk.copy(alpha = 0.80f),
                        modifier = Modifier.size(17.dp),
                    )
                }
                menu?.invoke()
                val hideVolumeBar by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
                val bitPerfectStatus by DesktopBitPerfect.status.collectAsState()
                val dacVolumeLevel by DesktopDacVolume.level.collectAsState()
                if (!hideVolumeBar) {
                    var volumePopupOpen by remember { mutableStateOf(false) }
                    val activeHwVol = if (bitPerfectStatus.active) dacVolumeLevel else null
                    val bpLocked = bitPerfectStatus.active && activeHwVol == null
                    val displayedVolume = activeHwVol ?: volume
                    Box {
                        DesktopToolbarButton(onClick = { volumePopupOpen = !volumePopupOpen }, size = 30.dp) {
                            Icon(
                                if (!bpLocked && displayedVolume <= 0.001f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                DesktopStrings["d_volume", "Volume"],
                                tint = DesktopInk.copy(alpha = if (bpLocked) 0.45f else 0.85f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        if (volumePopupOpen) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                offset = IntOffset(0, -60),
                                onDismissRequest = { volumePopupOpen = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                val volShape = RoundedCornerShape(percent = 50)
                                Box(
                                    modifier = Modifier
                                        .desktopPopupMotion(pivotX = 0.5f, pivotY = 1f, slideYDp = 6.dp)
                                        .padding(bottom = 8.dp)
                                        .shadow(16.dp, volShape)
                                        .clip(volShape)
                                        .desktopBarGlass(volShape, tintAlpha = 0.52f, borderAlpha = 0.15f),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        DesktopThinSlider(
                                            value = if (bpLocked) 1f else displayedVolume,
                                            onValueChange = { next ->
                                                if (activeHwVol != null) DesktopDacVolume.set(next) else onVolumeChange(next)
                                            },
                                            enabled = !bpLocked,
                                            idleHeight = 4.dp,
                                            activeHeight = 6.dp,
                                            activeColor = DesktopInk.copy(alpha = if (bpLocked) 0.35f else 0.92f),
                                            modifier = Modifier.width(90.dp),
                                        )
                                        onOpenAudioOutput?.let { openOutput ->
                                            Spacer(Modifier.width(6.dp))
                                            DesktopToolbarButton(
                                                onClick = {
                                                    volumePopupOpen = false
                                                    openOutput()
                                                },
                                                size = 24.dp,
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Headphones,
                                                    DesktopStrings["audio_output", "Audio output"],
                                                    tint = DesktopSecondary,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
internal fun DesktopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String = "Search",
) {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassSurface = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val searchShape = RoundedCornerShape(if (glassSurface) 10.dp else 7.dp)
    val ink = DesktopInk
    val isDark = DesktopIsDark
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .desktopGlassPress(
                shape = searchShape,
                interactionSource = interactionSource,
                pressScale = 0.988f,
                hoverScale = 1f,
                rimBoostAlpha = 0.30f,
            )
            .clip(searchShape)
            .then(
                if (glassSurface) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                if (isDark) {
                                    listOf(
                                        Color.White.copy(alpha = if (isFocused) 0.14f else 0.10f),
                                        Color(0xFF0E0E13).copy(alpha = 0.34f),
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = if (isFocused) 0.76f else 0.58f),
                                        Color.White.copy(alpha = if (isFocused) 0.58f else 0.38f),
                                    )
                                },
                            ),
                        )
                        .border(
                            width = if (isFocused) 1.dp else 0.85.dp,
                            brush = Brush.linearGradient(
                                if (isDark) {
                                    listOf(
                                        Color.White.copy(alpha = if (isFocused) 0.34f else 0.21f),
                                        Color.White.copy(alpha = if (isFocused) 0.14f else 0.06f),
                                    )
                                } else {
                                    listOf(
                                        ink.copy(alpha = if (isFocused) 0.24f else 0.12f),
                                        ink.copy(alpha = if (isFocused) 0.11f else 0.05f),
                                    )
                                },
                            ),
                            shape = searchShape,
                        )
                } else {
                    Modifier
                        .background(ink.copy(alpha = if (isDark) 0.07f else 0.055f))
                        .border(1.dp, ink.copy(alpha = if (isDark) 0.10f else 0.11f), searchShape)
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { focusRequester.requestFocus() },
            )
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
            cursorBrush = SolidColor(DesktopAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        BitChordIcons.Search,
                        contentDescription = null,
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(placeholder, color = DesktopSecondary, maxLines = 1)
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        val clearInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .desktopGlassPress(
                                    CircleShape,
                                    interactionSource = clearInteraction,
                                    pressScale = 0.90f,
                                )
                                .background(ink.copy(alpha = if (isDark) 0.12f else 0.09f))
                                .clickable(interactionSource = clearInteraction, indication = null) {
                                    onQueryChange("")
                                    runCatching { focusRequester.requestFocus() }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = DesktopStrings["d_clear_search", "Clear search"],
                                tint = ink.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

internal val LocalDesktopCompact = compositionLocalOf { false }

@Composable
private fun DesktopFrame(
    containerColor: Color,
    modifier: Modifier = Modifier,
    /**
     * The open page's own colours, painted across the whole window rather than only the content
     * area, so the title bar, top bar and sidebar take their tint from the page they are framing
     * instead of staying a flat slab beside it.
     */
    /** Receives true when its solid base should be left clear for the system material. */
    backdrop: @Composable (transparentBase: Boolean) -> Unit,
    topBar: @Composable (Boolean) -> Unit = {},
    sidebar: @Composable (Boolean) -> Unit,
    bottomBar: @Composable (Boolean) -> Unit,
    /** The column on the far side of the page — the lyrics or the queue, when one is open. */
    trailing: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Held out here rather than inside the constraints box.
    val haze = remember { HazeState() }
    // With a system material active, the window stays clear under its chrome. The app-background
    // preference decides whether the page and side column also stay clear or retain their solid
    // gray ground.
    val material by DesktopWindowBackdrop.active.collectAsState()
    val appBackground by DesktopWindowBackdrop.appBackground.collectAsState()
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassRecordActive = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val glass = material != DesktopBackdrop.OFF
    val materialBehindApp = glass && appBackground
    val chromeBackdrop = rememberDesktopLayerBackdrop()
    val appBackdrop = rememberDesktopLayerBackdrop()
    val overlayBackdrop = rememberDesktopLayerBackdrop()
    val modalHost = remember { DesktopModalHostState() }
    var titleBarHeightPx by remember { mutableStateOf(0) }
    val titleBarHeightDp = with(LocalDensity.current) { titleBarHeightPx.toDp() }
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val roundCorners = DesktopPlatform.isMac && !maximized
    CompositionLocalProvider(
        LocalDesktopHaze provides haze,
        LocalDesktopBackdrop provides overlayBackdrop,
        LocalDesktopContentBackdrop provides appBackdrop,
        LocalDesktopChromeBackdrop provides chromeBackdrop,
        LocalDesktopModalHost provides modalHost,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .then(if (roundCorners) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
                .then(if (glass) Modifier else Modifier.background(containerColor)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .desktopLayerBackdrop(overlayBackdrop, enabled = glassRecordActive),
            ) {
                // Both sources of the same state: the chrome blurs the backdrop behind it, and the
                // floating bottom bar blurs the page scrolling under it.
                if (!glass) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .hazeSource(haze)
                            .desktopLayerBackdrop(chromeBackdrop, enabled = glassRecordActive),
                    ) {
                        backdrop(false)
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    // Above everything, and outside the box the rest of the window is drawn in, because
                    // that is what a title bar is.
                    Box(Modifier.onSizeChanged { titleBarHeightPx = it.height }) {
                        DesktopTitleBar()
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val compact = maxWidth < 980.dp
                            CompositionLocalProvider(LocalDesktopCompact provides compact) {
                                // The sidebar runs the full height of the window, its head the window's
                                // caption; the player's bar and the page share the column beside it.
                                Row(Modifier.fillMaxSize()) {
                                    sidebar(compact)
                                    Column(Modifier.weight(1f).fillMaxHeight()) {
                                        val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
                                        val inlineCaption = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled
                                        if (inlineCaption) {
                                            DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(24.dp))
                                        }
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .then(
                                                    if (glass && !materialBehindApp) {
                                                        Modifier.background(containerColor)
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                        ) {
                                            val hasSong = LocalNowPlaying.current != null
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .hazeSource(haze)
                                                    .desktopLayerBackdrop(appBackdrop, enabled = glassRecordActive),
                                            ) {
                                                if (glass) backdrop(materialBehindApp)
                                                content(
                                                    PaddingValues(
                                                        start = 0.dp,
                                                        end = 0.dp,
                                                        // Room for the bar that floats over this, so the
                                                        // last row can still be scrolled clear of it.
                                                        bottom = if (hasSong) 90.dp else 16.dp,
                                                    ),
                                                )
                                            }
                                            Box(Modifier.align(Alignment.BottomCenter)) { bottomBar(compact) }
                                            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) { trailing() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(top = titleBarHeightDp)) {
                DesktopModalPortalHost(modalHost)
                CompositionLocalProvider(LocalDesktopInRootOverlay provides true) {
                    overlay()
                }
            }
        }
    }
}

/**
 * What [DesktopFrame] paints behind the window.
 *
 * [artworkUrl] is the page's lead artwork when it has one, and the mesh built from it is laid under
 * ink so a page of text stays readable over it.
 */
@Composable
private fun DesktopPageBackdrop(artworkUrl: String?, transparentBase: Boolean) {
    val disableBlendEffects by DesktopAppearanceSettings.disableBlendEffects.collectAsState()
    val isDark = DesktopIsDark
    val bg = DesktopBackground
    Box(
        Modifier
            .fillMaxSize()
            .then(if (transparentBase) Modifier else Modifier.background(bg)),
    ) {
        if (artworkUrl == null || disableBlendEffects) return@Box
        DesktopMesh(artworkUrl, lightTheme = !isDark)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (isDark) {
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.66f),
                            Color.Black.copy(alpha = 0.80f),
                        )
                    } else {
                        listOf(
                            Color.Transparent,
                            bg.copy(alpha = 0.28f),
                            bg.copy(alpha = 0.54f),
                        )
                    },
                ),
            ),
        )
    }
}

/** What the mesh is averaged from — coarse by design. */
/** Whether AutoPlay may offer a song this session has already played or suggested. */
internal const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"

internal const val KEY_LIBRARY_SORT = "library_sort"

private const val MESH_SOURCE_PX = 120



/** Enough to scroll through, short of turning the shelf into the history page. */
/** The Library's "On device" cards — the phone's ids for the two folders, so their cards draw alike. */
private const val LOCAL_DOWNLOADS_ID = "local:downloads"
private const val LOCAL_MUSIC_ID = "local:all"
private const val LOCAL_PLAYLIST_PREFIX = "desktop-playlist:"



/**
 * An artist: their picture, the numbers under it, their top songs, and the carousels of what they
 * have released.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopArtistPage(
    state: UiState<ArtistPage>,
    fallbackName: String,
    browseId: String = "",
    animatedArtistBanner: Boolean = true,
    onRetry: () -> Unit,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    /** The ⋮ on a top song, or a right-click on it. */
    onOpenMenu: (Song) -> Unit,
    onShelfItemClick: (ShelfItem, String?) -> Unit,
    /** Null for a guest: a channel subscription is the account's. */
    onToggleSubscription: ((SubscriptionState) -> Unit)?,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        when (state) {
            UiState.Loading -> Box(Modifier.fillMaxSize()) {
                DesktopLoadingPage("Loading $fallbackName…")
            }
            is UiState.Error -> Box(Modifier.fillMaxSize()) {
                DesktopErrorPage(state.message, onRetry)
            }
            is UiState.Success -> {
                val artist = state.data
                val name = artist.name?.takeIf(String::isNotBlank) ?: fallbackName
                val catalogueTitles = remember<List<String>>(artist) {
                    AppleCatalogue.titlesOf(artist.songs, artist.sections)
                }
                val artistArt by produceState<DesktopArtistArt?>(
                    initialValue = if (browseId.isNotBlank()) DesktopArtistArtRepository.cached(browseId) else null,
                    key1 = browseId,
                    key2 = animatedArtistBanner,
                ) {
                    value = if (!animatedArtistBanner || browseId.isBlank()) null else {
                        DesktopArtistArtRepository.artFor(browseId, name, catalogueTitles)
                    }
                }
                val motionUrl = if (animatedArtistBanner) {
                    artistArt?.videoUrl ?: artist.thumbnailUrl?.takeIf { it.contains(".gif", ignoreCase = true) }
                } else null
                val heroUrl = artistArt?.heroUrl ?: artist.thumbnailUrl
                val palette = rememberDesktopArtworkPalette(heroUrl)
                // Android caps the top-songs block and pages it sideways so the release shelves are
                // not buried under a hundred rows.
                val top = artist.songs.take(MAX_ARTIST_SONGS)
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = pagePadding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item(key = "artist-header") {
                            DesktopArtistBanner(heroUrl, name, palette, motionUrl)
                        }
                        if (artist.subscriberCountText != null || artist.monthlyListenerCount != null) {
                            item(key = "artist-stats") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // YouTube's own text already reads "1.2M subscribers" in full,
                                    // so only the number is kept and the label re-said in the app's
                                    // words.
                                    artist.subscriberCountText?.let {
                                        DesktopStatChip(Icons.Rounded.Person, "${it.substringBefore(' ')} subscribers")
                                    }
                                    artist.monthlyListenerCount?.let {
                                        DesktopStatChip(Icons.Rounded.GraphicEq, "${it.substringBefore(' ')} monthly listeners")
                                    }
                                }
                            }
                        }
                        item(key = "artist-actions") {
                            // The phone's artist controls: subscribing first, where saving sits on a
                            // release, then the labelled Play pill and Shuffle.
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val subscription = artist.subscription
                                if (subscription != null && onToggleSubscription != null) {
                                    DesktopCircleButton(
                                        if (subscription.subscribed) BitChordIcons.Check else BitChordIcons.Plus,
                                        if (subscription.subscribed) {
                                            DesktopStrings["unsubscribe", "Unsubscribe"]
                                        } else {
                                            DesktopStrings["subscribe", "Subscribe"]
                                        },
                                        onClick = { onToggleSubscription(subscription) },
                                    )
                                }
                                val currentSong = LocalNowPlaying.current
                                val isCurrentRow = rememberPlayingRowMatcher(top)
                                val isArtistActive = isContextSourceMatch(
                                    currentSong = currentSong,
                                    sourceId = browseId.takeIf(String::isNotBlank),
                                    tracks = top,
                                    isCurrentRow = isCurrentRow,
                                )
                                val isArtistPlaying = isPlaying && isArtistActive
                                DesktopPlayPill(
                                    label = if (isArtistPlaying) DesktopStrings["pause", "Pause"] else DesktopStrings["play", "Play"],
                                    isPlaying = isArtistPlaying,
                                    onClick = {
                                        if (isArtistActive) {
                                            onTogglePlayPause()
                                        } else if (top.isNotEmpty()) {
                                            onPlaySongs(top, 0)
                                        }
                                    },
                                )
                                DesktopCircleButton(BitChordIcons.Shuffle, DesktopStrings["shuffle", "Shuffle"], onClick = { onShuffle(top) })
                            }
                        }
                        artist.description?.takeIf(String::isNotBlank)?.let { blurb ->
                            item(key = "artist-about") {
                                Column(Modifier.padding(top = 16.dp)) {
                                    SectionTitle(DesktopStrings["d_about_artist", "About artist"])
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        blurb,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = DesktopPageGutter),
                                    )
                                }
                            }
                        }
                        if (top.isNotEmpty()) {
                            // Paged sideways four to a column, the way Home lists Recents.
                            item(key = "artist-top-songs") {
                                Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                                    SectionTitle(DesktopStrings["top_songs", "Top songs"])
                                    Spacer(Modifier.height(8.dp))
                                    BoxWithConstraints {
                                        val columnWidth = trackColumnWidth(maxWidth)
                                        ShelfRow(
                                            contentPadding = PaddingValues(horizontal = DesktopPageGutter),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(top.withIndex().chunked(ARTIST_SONGS_PER_COLUMN)) { column ->
                                                Column(Modifier.width(columnWidth)) {
                                                    column.forEach { (index, song) ->
                                                        CompactTrackRow(
                                                            title = song.title,
                                                            subtitle = song.artist,
                                                            thumbnailUrl = song.thumbnailUrl,
                                                            onClick = { onPlaySongs(top, index) },
                                                            onLongPress = { onOpenMenu(song) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        artist.sections.forEach { shelf ->
                            item(key = "artist-shelf-${shelf.title}") {
                                Box(Modifier.padding(top = 18.dp)) {
                                    DesktopShelf(shelf, onItemClick = onShelfItemClick)
                                }
                            }
                        }
                        if (top.isEmpty() && artist.sections.isEmpty()) {
                            item {
                                DesktopEmptyPage(
                                    Icons.Rounded.Person,
                                    "Nothing to show for $name",
                                    "YouTube Music has no songs or releases on this page.",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The photograph, and the name across the foot of it. */
@Composable
private fun DesktopArtistBanner(
    url: String?,
    name: String,
    palette: DesktopArtworkPalette,
    motionUrl: String? = null,
) {
    Box(Modifier.fillMaxWidth().height(ARTIST_BANNER_HEIGHT)) {
        DesktopArtwork(url, Modifier.matchParentSize(), px = HEADER_ART_PX)
        if (!motionUrl.isNullOrBlank()) {
            DesktopCanvasView(
                url = motionUrl,
                modifier = Modifier.matchParentSize(),
                isPlaying = true,
            )
        }
        // Two washes rather than one.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to palette.primary.copy(alpha = 0.18f),
                    1f to DesktopSurface,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.4f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            ),
        )
        Text(
            name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 18.dp),
        )
    }
}

@Composable
private fun DesktopStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(DesktopGlass.copy(alpha = if (DesktopIsDark) 0.55f else 0.82f))
            .border(0.5.dp, DesktopCardEdge, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = DesktopInk)
    }
}

/** The artist page that is open, and the name to bill it under until it loads. */
/** The page an artist's tracks were started from. */
private fun artistSource(target: DesktopArtistTarget) =
    DesktopQueueSource(target.name, PlaybackSourceType.BROWSE, target.browseId)

/** The album or playlist a queue was started from. */
private fun collectionSource(collection: DesktopCollection) =
    DesktopQueueSource(collection.title, PlaybackSourceType.BROWSE, collection.browseId)

/** Where a queue was started from, for the player's "Playing from" caption. */
internal data class DesktopQueueSource(
    val title: String,
    val type: PlaybackSourceType,
    val id: String? = null,
)

/**
 * Where this row says it was played from, as the shared queue rules take it — the phone's
 * `play()` makes the same one from the first row of what it was handed.
 */
private fun Song.queueSource(): QueueSource = QueueSource(
    title = playbackSource ?: albumName ?: "Queue",
    type = playbackSourceType ?: PlaybackSourceType.QUEUE,
    id = playbackSourceId,
)

/** Stamps [source] onto a row, preserving pre-stamped stations that carry their own radio name. */
private fun Song.withSource(source: DesktopQueueSource?): Song = when {
    source == null -> this
    radioName != null && playbackSource != null -> this
    else -> copy(
        playbackSource = source.title,
        playbackSourceType = source.type,
        playbackSourceId = source.id,
    )
}

/** Strips stale origin tags before saving a song inside another playlist. */
private fun Song.withoutPlaybackOrigin(): Song = copy(
    playbackSource = null,
    playbackSourceType = null,
    playbackSourceId = null,
    radioName = null,
)

private data class DesktopArtistTarget(val browseId: String, val name: String)

private sealed interface DesktopPageRoute {
    val transitionKey: String

    data class Settings(val page: DesktopSettingsPage) : DesktopPageRoute {
        override val transitionKey: String get() = "settings:${page.name}"
    }

    data object Replay : DesktopPageRoute {
        override val transitionKey: String = "replay"
    }

    data class Artist(val target: DesktopArtistTarget) : DesktopPageRoute {
        override val transitionKey: String get() = "artist:${target.browseId}"
    }

    data class CollectionLoading(val browseId: String, val title: String) : DesktopPageRoute {
        override val transitionKey: String get() = "collection_loading:$browseId"
    }

    data class Collection(val collection: DesktopCollection) : DesktopPageRoute {
        override val transitionKey: String get() = "collection:${collection.browseId}"
    }

    data object HomeMoodsAll : DesktopPageRoute {
        override val transitionKey: String = "home_moods_all"
    }

    data class ExploreMood(val mood: MoodGenre) : DesktopPageRoute {
        override val transitionKey: String get() = "explore_mood:${mood.browseId}:${mood.params.orEmpty()}"
    }

    data class LibraryShowAll(val shelf: HomeShelf) : DesktopPageRoute {
        override val transitionKey: String get() = "library_show_all:${shelf.title}"
    }

    data class Destination(val destination: DesktopDestination) : DesktopPageRoute {
        override val transitionKey: String get() = "dest:${destination.name}"
    }
}

/** One place the window has been: which page, and whatever was open on it. */
private data class DesktopNavEntry(
    val destination: DesktopDestination,
    val artist: DesktopArtistTarget?,
    val collection: DesktopCollection?,
    val mood: MoodGenre?,
    val homeMoodsAll: Boolean,
    val showAll: HomeShelf?,
    val replay: Boolean,
    val settings: DesktopSettingsPage?,
) {
    /** What makes two entries the same place: an album that paged in more tracks has not moved. */
    val key: List<Any?>
        get() = listOf(destination, artist?.browseId, collection?.browseId, mood?.browseId, mood?.params, homeMoodsAll, showAll?.title, replay, settings)
}

/** Deep enough for any way back anyone takes; the oldest fall off. */
private const val NAV_HISTORY_LIMIT = 50

/** The strip at the head of the sidebar that holds the window's buttons and can be dragged by. */
private val SIDEBAR_CAPTION_HEIGHT = 34.dp


internal val HERO_LOCAL_MUSIC_COLORS = listOf(Color(0xFF134E5E), Color(0xFF2B7A67), Color(0xFF71B280))
internal val HERO_DOWNLOADS_COLORS = listOf(Color(0xFF162B52), Color(0xFF1E3C72), Color(0xFF2A5298))
internal val HERO_HISTORY_COLORS = listOf(Color(0xFF2D1B4E), Color(0xFF512D73), Color(0xFFA64378))

private val HERO_LOCAL_MUSIC_LIGHT_COLORS = listOf(Color(0xFFB5F0D4), Color(0xFF91E3C3), Color(0xFF74D6B8))
private val HERO_DOWNLOADS_LIGHT_COLORS = listOf(Color(0xFFC8E2FF), Color(0xFFA0CAFF), Color(0xFF87B6FF))
private val HERO_HISTORY_LIGHT_COLORS = listOf(Color(0xFFEBD2FF), Color(0xFFD9B5FF), Color(0xFFF6BCDC))

internal fun resolveHeroColors(darkColors: List<Color>, isDark: Boolean): List<Color> = when {
    isDark -> darkColors
    darkColors == HERO_LOCAL_MUSIC_COLORS -> HERO_LOCAL_MUSIC_LIGHT_COLORS
    darkColors == HERO_DOWNLOADS_COLORS -> HERO_DOWNLOADS_LIGHT_COLORS
    darkColors == HERO_HISTORY_COLORS -> HERO_HISTORY_LIGHT_COLORS
    else -> darkColors
}

internal fun resolveHeroIconInk(isDark: Boolean): Color =
    if (isDark) Color.White else Color(0xFF0A0A0C)

/**
 * Mobile-inspired centered hero header used for top-level personal libraries (Local Music,
 * Downloads, History): a prominent rounded icon card, centered heading + subtitle, and large
 * Shuffle + Play/Pause pills underneath.
 */
@Composable
private fun DesktopSectionHeroHeader(
    heroKey: String,
    gradientColors: List<Color>,
    icon: ImageVector,
    title: String,
    subtitle: String,
    showActions: Boolean,
    isPlaying: Boolean,
    onShuffle: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = DesktopIsDark
    val themedColors = resolveHeroColors(gradientColors, isDark)
    val shape = RoundedCornerShape(24.dp)
    val palette = remember(heroKey, themedColors) { MeshPalette(themedColors) }
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
    val shadowAlpha = if (isDark) 0.50f else 0.24f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(164.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = shape,
                    ambientColor = themedColors.first().copy(alpha = shadowAlpha),
                    spotColor = themedColors.last().copy(alpha = shadowAlpha * 0.9f),
                )
                .clip(shape)
                .border(1.dp, borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(com.music.bitchord.ui.player.LocalPlayerLightTheme provides !isDark) {
                MeshGradientBackground(
                    palette = palette,
                    trackKey = heroKey,
                    blurRadius = 32.dp,
                    continuous = false,
                    animated = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolveHeroIconInk(isDark),
                modifier = Modifier.size(66.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = DesktopInk,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DesktopSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showActions) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shuffleInteraction = remember { MutableInteractionSource() }
                val liquidGlassHero by DesktopAppearanceSettings.liquidGlass.collectAsState()
                val reduceBlurHero by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
                val glassHeroSurface = liquidGlassHero && DesktopPlatform.supportsLiquidGlass && !reduceBlurHero
                val isDarkHero = DesktopIsDark
                val inkHero = DesktopInk
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .desktopGlassPress(CircleShape, interactionSource = shuffleInteraction, pressScale = 0.96f)
                        .clip(CircleShape)
                        .then(
                            if (glassHeroSurface) {
                                Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            if (isDarkHero) {
                                                listOf(
                                                    Color.White.copy(alpha = 0.19f),
                                                    Color.White.copy(alpha = 0.10f),
                                                )
                                            } else {
                                                listOf(
                                                    Color.White.copy(alpha = 0.72f),
                                                    Color.White.copy(alpha = 0.46f),
                                                )
                                            },
                                        ),
                                    )
                                    .border(
                                        0.85.dp,
                                        Brush.linearGradient(
                                            if (isDarkHero) {
                                                listOf(
                                                    Color.White.copy(alpha = 0.30f),
                                                    Color.White.copy(alpha = 0.08f),
                                                )
                                            } else {
                                                listOf(
                                                    inkHero.copy(alpha = 0.13f),
                                                    inkHero.copy(alpha = 0.05f),
                                                )
                                            },
                                        ),
                                        CircleShape,
                                    )
                            } else {
                                Modifier
                                    .background(inkHero.copy(alpha = if (isDarkHero) 0.12f else 0.08f))
                                    .border(1.dp, DesktopCardEdge, CircleShape)
                            },
                        )
                        .desktopHoverFill(corner = 23.dp)
                        .clickable(interactionSource = shuffleInteraction, indication = null, onClick = onShuffle),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = BitChordIcons.Shuffle,
                            contentDescription = null,
                            tint = DesktopInk,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = DesktopStrings["shuffle", "Shuffle"],
                            style = MaterialTheme.typography.titleMedium,
                            color = DesktopInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DesktopPlayPill(
                    label = if (isPlaying) DesktopStrings["pause", "Pause"] else DesktopStrings["play", "Play"],
                    isPlaying = isPlaying,
                    onClick = onPlayPause,
                    size = 46.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatDesktopSongCount(count: Int): String = if (count == 1) {
    DesktopStrings.format("song_count", count, fallback = "$count song")
} else {
    DesktopStrings.format("d_songs_count", count, fallback = "$count songs")
}

/**
 * Real-time frosted Liquid Glass surface for sticky scrolled headers over local lists & grids.
 * Kept inactive when [alpha] is ~0 so unpinned scrolling pays zero extra blur cost.
 */
@Composable
private fun Modifier.desktopStickyBarGlass(
    haze: HazeState,
    alpha: Float,
    backdrop: DesktopLayerBackdrop? = null,
    shape: Shape = RoundedCornerShape(20.dp),
): Modifier {
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    if (alpha <= 0.01f) return this
    if (reduceDynamicBlur) {
        return this.clip(shape).background(DesktopSurface.copy(alpha = 0.92f * alpha), shape)
    }
    if (liquidGlass && DesktopPlatform.supportsLiquidGlass && backdrop != null) {
        return this.clip(shape).desktopLiquidGlass(
            shape = shape,
            backdrop = backdrop,
            underlayBackdrop = LocalDesktopChromeBackdrop.current,
            tintAlpha = 0.44f * alpha,
            borderAlpha = 0.13f * alpha,
        )
    }
    val surfaceColor = DesktopSurface
    val barGlassColor = DesktopBarGlass
    val isDark = DesktopIsDark
    return this
        .clip(shape)
        .hazeEffect(state = haze) {
            blurEnabled = true
            backgroundColor = surfaceColor
            blurRadius = 30.dp
            tints = listOf(HazeTint(barGlassColor.copy(alpha = (if (isDark) 0.62f else 0.78f) * alpha)))
            noiseFactor = 0.04f
        }
        .border(0.5.dp, DesktopBarEdge.copy(alpha = alpha), shape)
}

/**
 * Fades scrolled items smoothly along the top margin while a floating sticky pill header is pinned
 * so covers do not peek above the header's top gap without obscuring ambient artwork glow behind.
 */
private fun Modifier.desktopStickyTopMask(
    clearTop: Dp = 18.dp,
    softFade: Dp = 16.dp,
    strength: Float = 1f,
): Modifier {
    if (strength <= 0.01f) return this
    val clamped = strength.coerceIn(0f, 1f)
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val clearPx = clearTop.toPx()
            val fadeEndPx = clearPx + softFade.toPx()
            if (fadeEndPx > 1f) {
                val edgeAlpha = 1f - clamped
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = edgeAlpha),
                            (clearPx / fadeEndPx).coerceIn(0f, 1f) to Color.Black.copy(alpha = edgeAlpha),
                            1.0f to Color.Black,
                        ),
                        startY = 0f,
                        endY = fadeEndPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

@Composable
private fun DesktopHistoryPage(
    history: List<Song>,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    likedIds: Set<String> = emptySet(),
    onToggleLike: ((Song) -> Unit)? = null,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopPageScaffold(contentPadding) {
        val currentSong = LocalNowPlaying.current
        val isCurrentRow = rememberPlayingRowMatcher(history)
        val isHistoryActive = isContextSourceMatch(
            currentSong = currentSong,
            sourceId = "history:all",
            tracks = history,
            isCurrentRow = isCurrentRow,
        )
        val isHistoryPlaying = isPlaying && isHistoryActive
        val baseSubtitle = DesktopStrings["d_recently_played_on_this_computer", "Recently played on this computer"]
        val heroSubtitle = if (history.isNotEmpty()) "$baseSubtitle · ${formatDesktopSongCount(history.size)}" else baseSubtitle
        val listState = rememberLazyListState()
        val historyHaze = remember { HazeState() }
        val historyBackdrop = rememberDesktopLayerBackdrop()
        val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
        val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
        val glassLayerActive = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
        val isPinned by remember {
            derivedStateOf { listState.firstVisibleItemIndex >= 1 }
        }
        val glassAlpha by animateFloatAsState(
            targetValue = if (isPinned) 1f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "history-sticky-glass",
        )

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(historyHaze)
                    .desktopLayerBackdrop(historyBackdrop, enabled = glassLayerActive && glassAlpha > 0.01f)
                    .desktopStickyTopMask(strength = glassAlpha),
                contentPadding = pagePadding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "history:hero") {
                    DesktopSectionHeroHeader(
                        heroKey = "local:history",
                        gradientColors = HERO_HISTORY_COLORS,
                        icon = BitChordIcons.Clock,
                        title = DesktopStrings["history", "History"],
                        subtitle = heroSubtitle,
                        showActions = history.isNotEmpty(),
                        isPlaying = isHistoryPlaying,
                        onShuffle = { onShuffleAll(history) },
                        onPlayPause = {
                            if (isHistoryActive) {
                                onTogglePlayPause()
                            } else if (history.isNotEmpty()) {
                                onPlaySongs(history, 0)
                            }
                        },
                    )
                }
                if (history.isEmpty()) {
                    item(key = "history:empty") {
                        DesktopEmptyPage(BitChordIcons.Clock, "Nothing played yet", "Songs you play will show up here.")
                    }
                } else {
                    itemsIndexed(history, key = { index, song -> "${song.videoId}:$index" }) { index, song ->
                        DesktopSongRow(
                            song = song,
                            isCurrent = isCurrentRow(song),
                            liked = song.videoId in likedIds,
                            onClick = { onPlaySongs(history, index) },
                            onToggleLike = onToggleLike,
                            onDownload = onDownload,
                            onAddToPlaylist = onAddToPlaylist,
                            downloaded = song.videoId in downloadedIds,
                            downloadInProgress = song.videoId in downloadInProgress,
                            menu = menu,
                        )
                    }
                }
            }
            if (glassAlpha > 0.01f && history.isNotEmpty()) {
                val historyStickyShape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = glassAlpha }
                            .shadow(14.dp * glassAlpha, historyStickyShape)
                            .desktopStickyBarGlass(historyHaze, glassAlpha, historyBackdrop, historyStickyShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = DesktopStrings["history", "History"],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatDesktopSongCount(history.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopCircleButton(
                                icon = BitChordIcons.Shuffle,
                                contentDescription = DesktopStrings["shuffle", "Shuffle"],
                                onClick = { onShuffleAll(history) },
                                size = 36.dp,
                            )
                            DesktopPlayPill(
                                label = if (isHistoryPlaying) DesktopStrings["pause", "Pause"] else DesktopStrings["play", "Play"],
                                isPlaying = isHistoryPlaying,
                                onClick = {
                                    if (isHistoryActive) {
                                        onTogglePlayPause()
                                    } else {
                                        onPlaySongs(history, 0)
                                    }
                                },
                                size = 36.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldKeepLocalHeaderPinnedOnTabSwitch(
    isPinned: Boolean,
    alreadyLocked: Boolean,
): Boolean = isPinned || alreadyLocked

@Composable
private fun DesktopDownloadsPage(
    downloads: List<Song>,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int, DesktopQueueSource?) -> Unit,
    onShuffleAll: (List<Song>, DesktopQueueSource?) -> Unit,
    likedIds: Set<String>,
    onToggleLike: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopLocalMusicPage(
        songs = downloads,
        isPlaying = isPlaying,
        onTogglePlayPause = onTogglePlayPause,
        onSongClick = onSongClick,
        onPlaySongs = onPlaySongs,
        onShuffleAll = onShuffleAll,
        likedIds = likedIds,
        onToggleLike = onToggleLike,
        onAddToPlaylist = onAddToPlaylist,
        localPlaylists = emptyList(),
        onCreatePlaylist = null,
        onOpenPlaylist = null,
        showPlaylistsAndFolders = false,
        contentPadding = contentPadding,
        menu = menu,
        title = DesktopStrings["downloads", "Downloads"],
        subtitle = DesktopStrings["d_available_offline", "Available offline"],
        heroIcon = BitChordIcons.Download,
        heroColors = HERO_DOWNLOADS_COLORS,
        emptyIcon = BitChordIcons.Download,
        emptyTitle = DesktopStrings["d_no_downloads", "No downloads"],
        emptyDescription = DesktopStrings["d_downloaded_songs_will_appear_here", "Downloaded songs will appear here."],
        persistenceKey = "downloaded",
    )
}

private const val LOCAL_LIKED_GROUP_ID = "local:liked_songs"

private data class LocalDrillDownState(
    val id: String,
    val fallbackTitle: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val placeholder: ImageVector = BitChordIcons.MusicNote,
    val circularArt: Boolean = false,
    val showTrackNumbers: Boolean = false,
    val likedOnly: Boolean = false,
)

@Composable
private fun DesktopLocalMusicPage(
    songs: List<Song>,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onPlaySongs: (List<Song>, Int, DesktopQueueSource?) -> Unit = { list, idx, _ -> list.getOrNull(idx)?.let(onSongClick) },
    onShuffleAll: (List<Song>, DesktopQueueSource?) -> Unit = { list, _ -> list.shuffled().firstOrNull()?.let(onSongClick) },
    likedIds: Set<String> = emptySet(),
    onToggleLike: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    localPlaylists: List<DesktopPlaylist> = emptyList(),
    onCreatePlaylist: (() -> Unit)? = null,
    onOpenPlaylist: ((DesktopPlaylist) -> Unit)? = null,
    showPlaylistsAndFolders: Boolean = true,
    contentPadding: PaddingValues,
    title: String = DesktopStrings["local_music", "Local Music"],
    subtitle: String = DesktopStrings["d_audio_files_on_this_computer", "Audio files on this computer"],
    heroIcon: ImageVector = Icons.Rounded.Folder,
    heroColors: List<Color> = HERO_LOCAL_MUSIC_COLORS,
    emptyIcon: ImageVector = BitChordIcons.MusicNote,
    emptyTitle: String = DesktopStrings["d_no_local_music", "No local music"],
    emptyDescription: String = DesktopStrings["d_local_music_empty_hint", "Put audio files in your Music folder and reopen this page."],
    persistenceKey: String = "local",
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val persistence = remember { DesktopPersistence() }
    val tabCount = if (showPlaylistsAndFolders) 5 else 3
    var selectedTab by remember(persistenceKey) {
        mutableStateOf(persistence.int("${persistenceKey}_tab", 0).coerceIn(0, tabCount - 1))
    }
    var searchQuery by remember(persistenceKey) { mutableStateOf("") }
    var view by remember(persistenceKey) {
        mutableStateOf(
            if (persistence.string("${persistenceKey}_view", "LIST") == "GRID") {
                DesktopLibraryView.GRID
            } else {
                DesktopLibraryView.LIST
            },
        )
    }
    var sort by remember(persistenceKey) {
        mutableStateOf(
            runCatching {
                DesktopLibrarySort.valueOf(persistence.string("${persistenceKey}_sort", "TITLE_ASC"))
            }.getOrDefault(DesktopLibrarySort.TITLE_ASC),
        )
    }
    var drillDownState by remember(persistenceKey) { mutableStateOf<LocalDrillDownState?>(null) }

    val unknownArtist = DesktopStrings["unknown_artist", "Unknown artist"]
    val formatSongCount = remember { { count: Int -> formatDesktopSongCount(count) } }

    val filteredSongs = remember(songs, sort, searchQuery) {
        songs.sortedForDesktopLibrary(sort).filter { song ->
            searchQuery.isBlank() ||
                song.title.contains(searchQuery, true) ||
                song.artist.contains(searchQuery, true) ||
                song.albumName.orEmpty().contains(searchQuery, true)
        }
    }
    val likedSongs = remember(filteredSongs, likedIds) {
        filteredSongs.filter { it.videoId in likedIds }
    }
    val artists = remember(filteredSongs, unknownArtist) {
        DesktopLocalGroups.artistsOf(filteredSongs, formatSongCount, unknownArtist)
    }
    val albums = remember(filteredSongs) {
        DesktopLocalGroups.albumsOf(filteredSongs, formatSongCount)
    }
    val folders = remember(filteredSongs) {
        DesktopLocalGroups.foldersOf(filteredSongs, formatSongCount)
    }
    val filteredPlaylists = remember(localPlaylists, searchQuery) {
        if (searchQuery.isBlank()) {
            localPlaylists
        } else {
            localPlaylists.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val activeGroup: DesktopLocalGroup? = remember(drillDownState, artists, albums, folders) {
        val state = drillDownState ?: return@remember null
        if (state.likedOnly) return@remember null
        (artists + albums + folders).firstOrNull { it.id == state.id }
    }
    val drillDownSongs: List<Song>? = remember(drillDownState, activeGroup, likedSongs) {
        val state = drillDownState ?: return@remember null
        when {
            state.likedOnly -> likedSongs
            else -> activeGroup?.tracks ?: emptyList()
        }
    }
    val playableTracks = drillDownSongs ?: filteredSongs
    val currentSong = LocalNowPlaying.current
    val localPlaylistIds = remember(localPlaylists) { localPlaylists.mapTo(HashSet(), DesktopPlaylist::id) }
    val globalSource = remember(persistenceKey, title) {
        DesktopQueueSource(title = title, type = PlaybackSourceType.BROWSE, id = "$persistenceKey:all")
    }
    val isGlobalActive = isGlobalLocalMusicMatch(
        currentSong = currentSong,
        librarySongs = songs,
        localPlaylistIds = localPlaylistIds,
        persistenceKey = persistenceKey,
        includePlaylists = showPlaylistsAndFolders,
    )
    val isGlobalPlaying = isPlaying && isGlobalActive

    val tabOptions = if (showPlaylistsAndFolders) {
        listOf(
            DesktopStrings["songs", "Songs"],
            DesktopStrings["artists", "Artists"],
            DesktopStrings["albums", "Albums"],
            DesktopStrings["playlists", "Playlists"],
            DesktopStrings["folders", "Folders"],
        )
    } else {
        listOf(
            DesktopStrings["songs", "Songs"],
            DesktopStrings["artists", "Artists"],
            DesktopStrings["albums", "Albums"],
        )
    }

    DesktopPageScaffold(contentPadding) {
        val density = LocalDensity.current
        val openDrillDown = drillDownState
        val smoothTransitions by DesktopAppearanceSettings.smoothTransitions.collectAsState()
        val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()
        val animateTabs = smoothTransitions && !reduceAnimation
        var tabDirection by remember(persistenceKey) { mutableStateOf(1f) }
        val tabReveal = remember(persistenceKey) { Animatable(1f) }
        val hasMountedTab = remember(persistenceKey) { booleanArrayOf(false) }
        LaunchedEffect(selectedTab, view, animateTabs) {
            if (!hasMountedTab[0] || !animateTabs) {
                hasMountedTab[0] = true
                tabReveal.snapTo(1f)
            } else {
                tabReveal.snapTo(0f)
                tabReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
                )
            }
        }
        val tabItemModifier = if (animateTabs) {
            Modifier.graphicsLayer {
                val t = tabReveal.value
                alpha = t
                translationX = (1f - t) * 26.dp.toPx() * tabDirection
            }
        } else {
            Modifier
        }

        AnimatedContent(
            targetState = openDrillDown,
            contentKey = { it?.id ?: "root" },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (!animateTabs) {
                    EnterTransition.None togetherWith ExitTransition.None using SizeTransform(clip = false)
                } else {
                    val forward = targetState != null
                    val sign = if (forward) 1 else -1
                    (fadeIn(tween(durationMillis = 210, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(durationMillis = 210, easing = FastOutSlowInEasing)) {
                            sign * (it * 0.06f).roundToInt()
                        })
                        .togetherWith(
                            fadeOut(tween(durationMillis = 150, easing = FastOutLinearInEasing)) +
                                slideOutHorizontally(tween(durationMillis = 150, easing = FastOutLinearInEasing)) {
                                    -sign * (it * 0.04f).roundToInt()
                                },
                        )
                        .using(SizeTransform(clip = false))
                }
            },
            label = "local-drilldown-transition",
        ) { activeDrill ->
        if (activeDrill != null) {
            val drillGroup = remember(activeDrill, artists, albums, folders) {
                if (activeDrill.likedOnly) null else (artists + albums + folders).firstOrNull { it.id == activeDrill.id }
            }
            val tracks = remember(activeDrill, drillGroup, likedSongs) {
                if (activeDrill.likedOnly) likedSongs else drillGroup?.tracks ?: emptyList()
            }
            val headerTitle = drillGroup?.title ?: activeDrill.fallbackTitle
            val headerSubtitle = drillGroup?.subtitle ?: formatSongCount(tracks.size)
            val headerArt = drillGroup?.artworkUrl ?: activeDrill.artworkUrl
            val drillDownSourceId = "$persistenceKey:${activeDrill.id}"
            val drillDownSource = DesktopQueueSource(
                title = headerTitle,
                type = PlaybackSourceType.BROWSE,
                id = drillDownSourceId,
            )
            val isCurrentDrillRow = rememberPlayingRowMatcher(tracks)
            val isDrillDownActive = isContextSourceMatch(
                currentSong = currentSong,
                sourceId = drillDownSourceId,
                tracks = tracks,
                isCurrentRow = isCurrentDrillRow,
            )
            val isDrillDownPlaying = isPlaying && isDrillDownActive
            val drillHaze = remember(activeDrill.id) { HazeState() }
            val drillBackdrop = rememberDesktopLayerBackdrop()
            val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
            val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
            val drillGlassActive = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
            var drillHeaderPx by remember(activeDrill.id) { mutableStateOf(0) }
            val drillHeaderDp = with(density) {
                (drillHeaderPx.takeIf { it > 0 } ?: with(density) { 126.dp.roundToPx() }).toDp()
            }
            val emptyDrillTitle = if (activeDrill.likedOnly) {
                DesktopStrings["auto_liked", "Liked songs"]
            } else {
                DesktopStrings["d_no_matching_music", "No matching music"]
            }
            val emptyDrillSubtitle = if (activeDrill.likedOnly) {
                DesktopStrings["d_liked_local_songs_empty", "Like local songs to find them quickly here."]
            } else {
                DesktopStrings["d_try_different_search", "Try a different search."]
            }
            val openContextMenu = LocalDesktopContextMenu.current

            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 164.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(drillHaze)
                        .desktopLayerBackdrop(drillBackdrop, enabled = drillGlassActive)
                        .desktopStickyTopMask(strength = 1f),
                    contentPadding = pagePadding(
                        start = DesktopPageGutter,
                        end = DesktopPageGutter,
                        top = drillHeaderDp + 16.dp,
                        bottom = 32.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    localSongGridItems(
                        songs = tracks,
                        view = view,
                        isCurrentRow = isCurrentDrillRow,
                        isPlaying = isPlaying,
                        likedIds = likedIds,
                        onSongClick = { tapped ->
                            val idx = tracks.indexOf(tapped).coerceAtLeast(0)
                            onPlaySongs(tracks, idx, drillDownSource)
                        },
                        onToggleLike = onToggleLike,
                        onAddToPlaylist = onAddToPlaylist,
                        showTrackNumbers = activeDrill.showTrackNumbers,
                        emptyTitle = emptyDrillTitle,
                        emptySubtitle = emptyDrillSubtitle,
                        openContextMenu = openContextMenu,
                        menu = menu,
                    )
                }
                val drillShape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .shadow(14.dp, drillShape)
                            .desktopStickyBarGlass(drillHaze, 1f, drillBackdrop, drillShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .onSizeChanged { drillHeaderPx = it.height },
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            DesktopLocalControlsRow(
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                sort = sort,
                                onCycleSort = {
                                    sort = sort.next()
                                    persistence.saveString("${persistenceKey}_sort", sort.name)
                                },
                                view = view,
                                onToggleView = {
                                    view = if (view == DesktopLibraryView.LIST) DesktopLibraryView.GRID else DesktopLibraryView.LIST
                                    persistence.saveString("${persistenceKey}_view", view.name)
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DesktopToolbarButton(onClick = { drillDownState = null }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, DesktopStrings["back", "Back"], tint = DesktopInk)
                                }
                                Spacer(Modifier.width(6.dp))
                                val drillArtShape = if (activeDrill.circularArt) CircleShape else RoundedCornerShape(10.dp)
                                Box(Modifier.size(52.dp)) {
                                    DesktopArtwork(
                                        url = headerArt,
                                        modifier = Modifier.fillMaxSize().clip(drillArtShape),
                                        placeholder = activeDrill.placeholder,
                                    )
                                    DesktopCoverPlayingIndicator(
                                        isCurrent = isDrillDownActive,
                                        isPlaying = isPlaying,
                                        shape = drillArtShape,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = headerTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = headerSubtitle,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (tracks.isNotEmpty()) {
                                    Spacer(Modifier.width(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        DesktopCircleButton(
                                            icon = BitChordIcons.Shuffle,
                                            contentDescription = DesktopStrings["shuffle", "Shuffle"],
                                            onClick = { onShuffleAll(tracks, drillDownSource) },
                                            size = 38.dp,
                                        )
                                        DesktopPlayPill(
                                            label = if (isDrillDownPlaying) DesktopStrings["pause", "Pause"] else DesktopStrings["play", "Play"],
                                            isPlaying = isDrillDownPlaying,
                                            onClick = {
                                                if (isDrillDownActive) {
                                                    onTogglePlayPause()
                                                } else {
                                                    onPlaySongs(tracks, 0, drillDownSource)
                                                }
                                            },
                                            size = 38.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val isCurrentRow = rememberPlayingRowMatcher(filteredSongs)
            val openContextMenu = LocalDesktopContextMenu.current
            val scope = rememberCoroutineScope()
            val gridState = rememberLazyGridState()
            var stickyPinnedLock by remember(persistenceKey) { mutableStateOf(false) }
            val stickyHaze = remember(persistenceKey) { HazeState() }
            val stickyBackdrop = rememberDesktopLayerBackdrop()
            val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
            val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
            val stickyGlassActive = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
            var controlsHeightPx by remember { mutableStateOf(0) }
            val controlsHeightDp = with(density) {
                (controlsHeightPx.takeIf { it > 0 } ?: with(density) { 108.dp.roundToPx() }).toDp()
            }
            val stickyTopPx by remember {
                derivedStateOf {
                    if (stickyPinnedLock) return@derivedStateOf 0
                    val layout = gridState.layoutInfo
                    val placeholder = layout.visibleItemsInfo.firstOrNull { it.key == "local:controls-placeholder" }
                    when {
                        placeholder != null -> placeholder.offset.y.coerceAtLeast(0)
                        gridState.firstVisibleItemIndex >= 2 -> 0
                        else -> {
                            val hero = layout.visibleItemsInfo.firstOrNull { it.key == "local:hero" }
                            hero?.let { (it.offset.y + it.size.height).coerceAtLeast(0) } ?: 0
                        }
                    }
                }
            }
            val isPinned by remember {
                derivedStateOf {
                    if (stickyPinnedLock) return@derivedStateOf true
                    val layout = gridState.layoutInfo
                    val placeholder = layout.visibleItemsInfo.firstOrNull { it.key == "local:controls-placeholder" }
                    when {
                        placeholder != null -> placeholder.offset.y <= 1
                        else -> gridState.firstVisibleItemIndex >= 2
                    }
                }
            }
            LaunchedEffect(selectedTab, stickyPinnedLock) {
                if (stickyPinnedLock) {
                    gridState.scrollToItem(0, 0)
                }
            }
            val unpinAtTopNestedScroll = remember(gridState) {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: androidx.compose.ui.geometry.Offset,
                        available: androidx.compose.ui.geometry.Offset,
                        source: NestedScrollSource,
                    ): androidx.compose.ui.geometry.Offset {
                        if (stickyPinnedLock &&
                            source == NestedScrollSource.UserInput &&
                            available.y > 4f &&
                            gridState.firstVisibleItemIndex == 0 &&
                            gridState.firstVisibleItemScrollOffset == 0
                        ) {
                            stickyPinnedLock = false
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }
            val glassAlpha by animateFloatAsState(
                targetValue = if (isPinned) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "local-sticky-glass",
            )
            val heroSubtitle = if (songs.isNotEmpty()) "$subtitle · ${formatSongCount(songs.size)}" else subtitle

            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 164.dp),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(unpinAtTopNestedScroll)
                        .hazeSource(stickyHaze)
                        .desktopLayerBackdrop(stickyBackdrop, enabled = stickyGlassActive && glassAlpha > 0.01f)
                        .desktopStickyTopMask(strength = glassAlpha),
                    contentPadding = pagePadding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!stickyPinnedLock) {
                        item(key = "local:hero", span = { GridItemSpan(maxLineSpan) }) {
                            DesktopSectionHeroHeader(
                                heroKey = "hero:$persistenceKey",
                                gradientColors = heroColors,
                                icon = heroIcon,
                                title = title,
                                subtitle = heroSubtitle,
                                showActions = playableTracks.isNotEmpty(),
                                isPlaying = isGlobalPlaying,
                                onShuffle = { onShuffleAll(playableTracks, globalSource) },
                                onPlayPause = {
                                    if (isGlobalActive) {
                                        onTogglePlayPause()
                                    } else if (playableTracks.isNotEmpty()) {
                                        onPlaySongs(playableTracks, 0, globalSource)
                                    }
                                },
                            )
                        }
                    }
                    // Reserve exact space of the floating/sticky controls so scrolling up to the
                    // hero tracks 1:1 with zero layout jump and pins seamlessly to the top as Liquid Glass.
                    item(key = "local:controls-placeholder", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.fillMaxWidth().height(controlsHeightDp))
                    }
                    if (songs.isEmpty() && (!showPlaylistsAndFolders || selectedTab != 3)) {
                        item(key = "local:empty", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp).then(tabItemModifier)) {
                                DesktopEmptyPage(emptyIcon, emptyTitle, emptyDescription)
                            }
                        }
                    } else {
                        when (selectedTab) {
                            0 -> localSongGridItems(
                                songs = filteredSongs,
                                view = view,
                                isCurrentRow = isCurrentRow,
                                isPlaying = isPlaying,
                                likedIds = likedIds,
                                onSongClick = { tapped ->
                                    val idx = filteredSongs.indexOf(tapped).coerceAtLeast(0)
                                    onPlaySongs(filteredSongs, idx, globalSource)
                                },
                                onToggleLike = onToggleLike,
                                onAddToPlaylist = onAddToPlaylist,
                                showTrackNumbers = false,
                                emptyTitle = DesktopStrings["d_no_matching_music", "No matching music"],
                                emptySubtitle = DesktopStrings["d_try_different_search", "Try a different search."],
                                openContextMenu = openContextMenu,
                                menu = menu,
                                itemModifier = tabItemModifier,
                            )
                            1 -> localGroupGridItems(
                                groups = artists,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                persistenceKey = persistenceKey,
                                view = view,
                                circularArt = true,
                                placeholder = Icons.Rounded.Person,
                                showFolderBadge = false,
                                emptyIcon = Icons.Rounded.Person,
                                emptyTitle = DesktopStrings["artists", "Artists"],
                                emptyDescription = DesktopStrings["d_try_different_search", "Try a different search."],
                                onGroupClick = { group ->
                                    drillDownState = LocalDrillDownState(
                                        id = group.id,
                                        fallbackTitle = group.title,
                                        subtitle = group.subtitle,
                                        artworkUrl = group.artworkUrl,
                                        placeholder = Icons.Rounded.Person,
                                        circularArt = true,
                                    )
                                },
                                itemModifier = tabItemModifier,
                            )
                            2 -> localGroupGridItems(
                                groups = albums,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                persistenceKey = persistenceKey,
                                view = view,
                                circularArt = false,
                                placeholder = Icons.Rounded.Album,
                                showFolderBadge = false,
                                emptyIcon = Icons.Rounded.Album,
                                emptyTitle = DesktopStrings["albums", "Albums"],
                                emptyDescription = if (searchQuery.isNotBlank()) {
                                    DesktopStrings["d_try_different_search", "Try a different search."]
                                } else {
                                    DesktopStrings[
                                        "no_local_albums",
                                        "Nothing here belongs to an album or playlist yet. Download a complete album or playlist and it will appear here.",
                                    ]
                                },
                                onGroupClick = { group ->
                                    drillDownState = LocalDrillDownState(
                                        id = group.id,
                                        fallbackTitle = group.title,
                                        subtitle = group.subtitle,
                                        artworkUrl = group.artworkUrl,
                                        placeholder = Icons.Rounded.Album,
                                        showTrackNumbers = true,
                                    )
                                },
                                itemModifier = tabItemModifier,
                            )
                            3 -> localPlaylistsGridItems(
                                view = view,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                persistenceKey = persistenceKey,
                                likedSongs = likedSongs,
                                playlists = filteredPlaylists,
                                songCountLabel = formatSongCount,
                                onCreatePlaylist = onCreatePlaylist,
                                onOpenLiked = {
                                    drillDownState = LocalDrillDownState(
                                        id = LOCAL_LIKED_GROUP_ID,
                                        fallbackTitle = DesktopStrings["auto_liked", "Liked songs"],
                                        placeholder = Icons.Rounded.Favorite,
                                        likedOnly = true,
                                    )
                                },
                                onOpenPlaylist = onOpenPlaylist,
                                itemModifier = tabItemModifier,
                            )
                            else -> localGroupGridItems(
                                groups = folders,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                persistenceKey = persistenceKey,
                                view = view,
                                circularArt = false,
                                placeholder = Icons.Rounded.Folder,
                                showFolderBadge = true,
                                emptyIcon = Icons.Rounded.Folder,
                                emptyTitle = DesktopStrings["folders", "Folders"],
                                emptyDescription = if (searchQuery.isNotBlank()) {
                                    DesktopStrings["d_try_different_search", "Try a different search."]
                                } else {
                                    DesktopStrings["no_local_folders", "No folders with music in them"]
                                },
                                onGroupClick = { group ->
                                    drillDownState = LocalDrillDownState(
                                        id = group.id,
                                        fallbackTitle = group.title,
                                        subtitle = group.subtitle,
                                        artworkUrl = group.artworkUrl,
                                        placeholder = Icons.Rounded.Folder,
                                    )
                                },
                                itemModifier = tabItemModifier,
                            )
                        }
                    }
                }
                val stickyShape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .offset { IntOffset(0, stickyTopPx) }
                        .padding(
                            start = 12.dp * glassAlpha,
                            end = 12.dp * glassAlpha,
                            top = 8.dp * glassAlpha,
                        ),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (glassAlpha > 0.01f) {
                                    Modifier.shadow(14.dp * glassAlpha, stickyShape)
                                } else {
                                    Modifier
                                },
                            )
                            .desktopStickyBarGlass(stickyHaze, glassAlpha, stickyBackdrop, stickyShape)
                            .then(
                                if (isPinned) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .onSizeChanged { controlsHeightPx = it.height },
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = DesktopPageGutter - 2.dp * glassAlpha,
                                    vertical = 10.dp,
                                ),
                        ) {
                            DesktopLocalControlsRow(
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                sort = sort,
                                onCycleSort = {
                                    sort = sort.next()
                                    persistence.saveString("${persistenceKey}_sort", sort.name)
                                },
                                view = view,
                                onToggleView = {
                                    view = if (view == DesktopLibraryView.LIST) DesktopLibraryView.GRID else DesktopLibraryView.LIST
                                    persistence.saveString("${persistenceKey}_view", view.name)
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                PillSegmentedControl(
                                    options = tabOptions,
                                    selectedIndex = selectedTab.coerceIn(tabOptions.indices),
                                    onSelect = { index ->
                                        val wasPinned = shouldKeepLocalHeaderPinnedOnTabSwitch(
                                            isPinned = isPinned,
                                            alreadyLocked = stickyPinnedLock,
                                        )
                                        tabDirection = if (index >= selectedTab) 1f else -1f
                                        selectedTab = index
                                        drillDownState = null
                                        persistence.saveInt("${persistenceKey}_tab", index)
                                        if (wasPinned) {
                                            stickyPinnedLock = true
                                            scope.launch { gridState.scrollToItem(0, 0) }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun DesktopLocalControlsRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sort: DesktopLibrarySort,
    onCycleSort: () -> Unit,
    view: DesktopLibraryView,
    onToggleView: () -> Unit,
) {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val glassSurface = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val sortInteraction = remember { MutableInteractionSource() }
    val sortShape = CircleShape
    Row(verticalAlignment = Alignment.CenterVertically) {
        DesktopSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            onSearch = {},
            modifier = Modifier.weight(1f),
            placeholder = DesktopStrings["d_search_your_music", "Search your music"],
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .height(34.dp)
                .desktopGlassPress(sortShape, interactionSource = sortInteraction, pressScale = 0.94f)
                .clip(sortShape)
                .desktopHoverFill(corner = 17.dp)
                .then(
                    if (glassSurface) {
                        Modifier
                            .background(DesktopInk.copy(alpha = if (DesktopIsDark) 0.08f else 0.06f))
                            .border(0.75.dp, DesktopCardEdge, sortShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(interactionSource = sortInteraction, indication = null, onClick = onCycleSort)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = DesktopInk, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(sort.label(), color = DesktopInk, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(6.dp))
        DesktopToolbarButton(onClick = onToggleView, size = 34.dp) {
            Icon(
                if (view == DesktopLibraryView.LIST) BitChordIcons.GridView else BitChordIcons.ListView,
                DesktopStrings["d_change_view", "Change view"],
                tint = DesktopInk,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun DesktopLibrarySort.next(): DesktopLibrarySort = when (this) {
    DesktopLibrarySort.TITLE_ASC -> DesktopLibrarySort.TITLE_DESC
    DesktopLibrarySort.TITLE_DESC -> DesktopLibrarySort.DATE_ADDED
    DesktopLibrarySort.DATE_ADDED -> DesktopLibrarySort.DATE_MODIFIED
    DesktopLibrarySort.DATE_MODIFIED -> DesktopLibrarySort.TITLE_ASC
}

private fun DesktopLibrarySort.label(): String = when (this) {
    DesktopLibrarySort.TITLE_ASC -> "A–Z"
    DesktopLibrarySort.TITLE_DESC -> "Z–A"
    DesktopLibrarySort.DATE_ADDED -> DesktopStrings["d_sort_added", "Added"]
    DesktopLibrarySort.DATE_MODIFIED -> DesktopStrings["d_sort_modified", "Modified"]
}

@Composable
private fun BoxScope.DesktopCoverPlayingIndicator(
    isCurrent: Boolean,
    isPlaying: Boolean,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    if (!isCurrent) return
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            SearchPlayingBars()
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = DesktopStrings["now_playing", "Now playing"],
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

private fun LazyGridScope.localSongGridItems(
    songs: List<Song>,
    view: DesktopLibraryView,
    isCurrentRow: (Song) -> Boolean,
    isPlaying: Boolean,
    likedIds: Set<String>,
    onSongClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onAddToPlaylist: ((Song) -> Unit)?,
    showTrackNumbers: Boolean,
    emptyTitle: String,
    emptySubtitle: String,
    openContextMenu: (Song) -> Unit,
    menu: (@Composable (Song) -> Unit)?,
    itemModifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) {
        item(key = "songs:empty", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp).then(itemModifier)) {
                DesktopEmptyPage(BitChordIcons.Search, emptyTitle, emptySubtitle)
            }
        }
        return
    }
    if (view == DesktopLibraryView.GRID) {
        val cardShape = RoundedCornerShape(12.dp)
        gridItems(songs, key = Song::videoId) { song ->
            val current = isCurrentRow(song)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .padding(bottom = 12.dp)
                    .clip(cardShape)
                    .contextClick { openContextMenu(song) }
                    .clickable { onSongClick(song) },
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    DesktopArtwork(
                        url = song.thumbnailUrl,
                        modifier = Modifier.fillMaxSize().clip(cardShape),
                        px = CARD_ART_PX,
                        placeholder = BitChordIcons.MusicNote,
                    )
                    DesktopCoverPlayingIndicator(
                        isCurrent = current,
                        isPlaying = isPlaying,
                        shape = cardShape,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (current) DesktopAccent else DesktopInk,
                )
                Text(
                    text = listOfNotNull(song.artist.takeIf(String::isNotBlank), song.durationText).joinToString(" · "),
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        items(
            count = songs.size,
            key = { songs[it].videoId },
            span = { GridItemSpan(maxLineSpan) },
        ) { index ->
            val song = songs[index]
            Box(itemModifier) {
                DesktopSongRow(
                    song = song,
                    isCurrent = isCurrentRow(song),
                    liked = song.videoId in likedIds,
                    onClick = onSongClick,
                    onToggleLike = onToggleLike,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = song.localPath != null,
                    number = song.trackNumber.takeIf { showTrackNumbers },
                    menu = menu,
                )
            }
        }
    }
}

private fun LazyGridScope.localGroupGridItems(
    groups: List<DesktopLocalGroup>,
    currentSong: Song?,
    isPlaying: Boolean,
    persistenceKey: String,
    view: DesktopLibraryView,
    circularArt: Boolean,
    placeholder: ImageVector,
    showFolderBadge: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyDescription: String,
    onGroupClick: (DesktopLocalGroup) -> Unit,
    itemModifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) {
        item(key = "groups:empty", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp).then(itemModifier)) {
                DesktopEmptyPage(emptyIcon, emptyTitle, emptyDescription)
            }
        }
        return
    }
    if (view == DesktopLibraryView.GRID) {
        val artShape = if (circularArt) CircleShape else RoundedCornerShape(12.dp)
        gridItems(groups, key = DesktopLocalGroup::id) { group ->
            val isActive = isGroupActiveInLocalTab(currentSong, group, persistenceKey)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onGroupClick(group) },
                horizontalAlignment = if (circularArt) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    DesktopArtwork(
                        url = group.artworkUrl,
                        modifier = Modifier.fillMaxSize().clip(artShape),
                        px = CARD_ART_PX,
                        placeholder = placeholder,
                    )
                    DesktopCoverPlayingIndicator(
                        isCurrent = isActive,
                        isPlaying = isPlaying,
                        shape = artShape,
                    )
                    if (showFolderBadge && group.artworkUrl != null) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = group.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) DesktopAccent else DesktopInk,
                    textAlign = if (circularArt) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = group.subtitle,
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (circularArt) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        val artShape = if (circularArt) CircleShape else RoundedCornerShape(10.dp)
        items(
            count = groups.size,
            key = { groups[it].id },
            span = { GridItemSpan(maxLineSpan) },
        ) { index ->
            val group = groups[index]
            val isActive = isGroupActiveInLocalTab(currentSong, group, persistenceKey)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DesktopGlass.copy(alpha = 0.38f))
                    .desktopHoverFill(corner = 10.dp)
                    .clickable { onGroupClick(group) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(54.dp)) {
                    DesktopArtwork(
                        url = group.artworkUrl,
                        modifier = Modifier.fillMaxSize().clip(artShape),
                        px = ROW_ART_PX,
                        placeholder = placeholder,
                    )
                    DesktopCoverPlayingIndicator(
                        isCurrent = isActive,
                        isPlaying = isPlaying,
                        shape = artShape,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) DesktopAccent else DesktopInk,
                    )
                    Text(
                        group.subtitle,
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(BitChordIcons.Play, DesktopStrings["open", "Open"], tint = DesktopSecondary)
            }
        }
    }
}

private fun LazyGridScope.localPlaylistsGridItems(
    view: DesktopLibraryView,
    currentSong: Song?,
    isPlaying: Boolean,
    persistenceKey: String,
    likedSongs: List<Song>,
    playlists: List<DesktopPlaylist>,
    songCountLabel: (Int) -> String,
    onCreatePlaylist: (() -> Unit)?,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: ((DesktopPlaylist) -> Unit)?,
    itemModifier: Modifier = Modifier,
) {
    val likedSourceId = "$persistenceKey:$LOCAL_LIKED_GROUP_ID"
    val isLikedActive = isContextSourceMatch(
        currentSong = currentSong,
        sourceId = likedSourceId,
        tracks = likedSongs,
    )
    if (view == DesktopLibraryView.GRID) {
        if (onCreatePlaylist != null) {
            item(key = "local:new_playlist") {
                val newPlaylistLabel = DesktopStrings["new_playlist", "New playlist"]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(itemModifier)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onCreatePlaylist),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DesktopGlass.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            BitChordIcons.Plus,
                            contentDescription = newPlaylistLabel,
                            tint = DesktopAccent,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(newPlaylistLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        DesktopStrings["d_playlists_on_this_computer", "Playlists on this computer"],
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(key = LOCAL_LIKED_GROUP_ID) {
            val likedLabel = DesktopStrings["auto_liked", "Liked songs"]
            val cardShape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .padding(bottom = 12.dp)
                    .clip(cardShape)
                    .clickable(onClick = onOpenLiked),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(cardShape)
                        .background(Color(0xFF6B2D52)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = likedLabel,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                    DesktopCoverPlayingIndicator(
                        isCurrent = isLikedActive,
                        isPlaying = isPlaying,
                        shape = cardShape,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = likedLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (isLikedActive) DesktopAccent else DesktopInk,
                )
                Text(
                    songCountLabel(likedSongs.size),
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        gridItems(playlists, key = DesktopPlaylist::id) { playlist ->
            val art = playlist.songs.firstNotNullOfOrNull { it.thumbnailUrl }
            val isPlaylistActive = isContextSourceMatch(
                currentSong = currentSong,
                sourceId = playlist.id,
                tracks = playlist.songs,
            )
            val cardShape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .padding(bottom = 12.dp)
                    .clip(cardShape)
                    .clickable { onOpenPlaylist?.invoke(playlist) },
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    DesktopArtwork(
                        url = art,
                        modifier = Modifier.fillMaxSize().clip(cardShape),
                        px = CARD_ART_PX,
                        placeholder = BitChordIcons.Queue,
                    )
                    DesktopCoverPlayingIndicator(
                        isCurrent = isPlaylistActive,
                        isPlaying = isPlaying,
                        shape = cardShape,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = playlist.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlaylistActive) DesktopAccent else DesktopInk,
                )
                Text(
                    songCountLabel(playlist.songs.size),
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        if (onCreatePlaylist != null) {
            item(key = "local:new_playlist", span = { GridItemSpan(maxLineSpan) }) {
                val newPlaylistLabel = DesktopStrings["new_playlist", "New playlist"]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(itemModifier)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DesktopGlass.copy(alpha = 0.55f))
                        .desktopHoverFill(corner = 10.dp)
                        .clickable(onClick = onCreatePlaylist)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DesktopAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(BitChordIcons.Plus, contentDescription = newPlaylistLabel, tint = DesktopAccent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(newPlaylistLabel, fontWeight = FontWeight.Medium, color = DesktopAccent)
                        Text(
                            DesktopStrings["d_playlists_on_this_computer", "Playlists on this computer"],
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item(key = LOCAL_LIKED_GROUP_ID, span = { GridItemSpan(maxLineSpan) }) {
            val likedLabel = DesktopStrings["auto_liked", "Liked songs"]
            val thumbShape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .clip(thumbShape)
                    .background(DesktopGlass.copy(alpha = 0.38f))
                    .desktopHoverFill(corner = 10.dp)
                    .clickable(onClick = onOpenLiked)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(thumbShape)
                        .background(Color(0xFF6B2D52)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = likedLabel, tint = Color.White)
                    DesktopCoverPlayingIndicator(
                        isCurrent = isLikedActive,
                        isPlaying = isPlaying,
                        shape = thumbShape,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = likedLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = if (isLikedActive) DesktopAccent else DesktopInk,
                    )
                    Text(songCountLabel(likedSongs.size), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(BitChordIcons.Play, DesktopStrings["open", "Open"], tint = DesktopSecondary)
            }
        }
        items(
            count = playlists.size,
            key = { playlists[it].id },
            span = { GridItemSpan(maxLineSpan) },
        ) { index ->
            val playlist = playlists[index]
            val art = playlist.songs.firstNotNullOfOrNull { it.thumbnailUrl }
            val isPlaylistActive = isContextSourceMatch(
                currentSong = currentSong,
                sourceId = playlist.id,
                tracks = playlist.songs,
            )
            val thumbShape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(itemModifier)
                    .clip(thumbShape)
                    .background(DesktopGlass.copy(alpha = 0.38f))
                    .desktopHoverFill(corner = 10.dp)
                    .clickable { onOpenPlaylist?.invoke(playlist) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(54.dp)) {
                    DesktopArtwork(
                        url = art,
                        modifier = Modifier.fillMaxSize().clip(thumbShape),
                        px = ROW_ART_PX,
                        placeholder = BitChordIcons.Queue,
                    )
                    DesktopCoverPlayingIndicator(
                        isCurrent = isPlaylistActive,
                        isPlaying = isPlaying,
                        shape = thumbShape,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = if (isPlaylistActive) DesktopAccent else DesktopInk,
                    )
                    Text(songCountLabel(playlist.songs.size), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(BitChordIcons.Play, DesktopStrings["open", "Open"], tint = DesktopSecondary)
            }
        }
    }
}

/** Everything this build is assembled from, and the terms each part came under. A page of Settings. */
@Composable
private fun DesktopLicensesPage(onDismiss: () -> Unit) {
    DesktopSettingsPageFrame {
        DesktopSettingsSubPageHeader(
            title = DesktopStrings["d_third_party_licenses", "Third-party licenses"],
            subtitle = DesktopStrings["d_bitchord_is_free_software_and_so_is_everything_it_is_bui", "BitChord is free software, and so is everything it is built on."],
            onBack = onDismiss,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DesktopLicenses.groups.forEach { group ->
                item(key = group.title) {
                    DesktopGroupedCard(header = group.title) {
                        group.entries.forEachIndexed { idx, entry ->
                            if (idx > 0) DesktopGroupedDivider()
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.name, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        entry.license,
                                        color = DesktopAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        entry.note,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    entry.url,
                                    color = DesktopSecondary.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopCollectionPage(
    collection: DesktopCollection,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    /** The three edits only a playlist's owner is offered. */
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** Opens the drag-into-order sheet; only a playlist the account owns can be rearranged. */
    onReorder: (() -> Unit)?,
    /** Copies the release's own YouTube Music link; null for anything with no link to share. */
    onShare: (() -> Unit)?,
    onRemoveFromPlaylist: ((Song) -> Unit)?,
    likedIds: Set<String>,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSavePlaylist: ((DesktopCollection) -> Unit)? = null,
    onDownloadAll: (List<Song>) -> Unit,
    animatedCanvas: Boolean,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
) {
    val songs = collection.songs
    // The phone's "search this list": a filter over the rows already loaded.
    var searching by remember(collection.browseId) { mutableStateOf(false) }
    var listQuery by remember(collection.browseId) { mutableStateOf("") }
    val shownSongs = remember(songs, listQuery) {
        val needle = listQuery.trim()
        songs.withIndex().filter { (_, song) ->
            needle.isBlank() || song.title.contains(needle, ignoreCase = true) ||
                song.artist.contains(needle, ignoreCase = true) ||
                song.albumName.orEmpty().contains(needle, ignoreCase = true)
        }
    }
    var moreOpen by remember(collection.browseId) { mutableStateOf(false) }
    val subtitleParts = collection.subtitle
        .split(" • ", " · ", " | ")
        .map(String::trim)
        .filter(String::isNotBlank)
    val credit = subtitleParts.firstOrNull {
        it.lowercase() !in setOf("album", "single", "ep", "playlist", "artist") &&
            !it.matches(Regex("\\d{4}")) &&
            !it.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")) &&
            !it.matches(Regex("[0-9,.]+\\s*(?:songs?|tracks?)", RegexOption.IGNORE_CASE))
    }.orEmpty()
    val typeLabel = when (collection.type) {
        BrowseType.ALBUM -> "Album"
        BrowseType.PLAYLIST -> "Playlist"
        BrowseType.ARTIST -> "Artist"
        BrowseType.OTHER -> "Collection"
    }
    val metadata = buildList {
        add(typeLabel.uppercase())
        subtitleParts.firstOrNull { it.matches(Regex("\\d{4}")) }?.let(::add)
        if (songs.isNotEmpty()) add("${songs.size} songs")
        val seconds = songs.sumOf { it.durationText.durationMillis() / 1_000L }
        if (seconds > 0) add(formatCollectionDuration(seconds))
    }.joinToString(" • ").uppercase()
    DesktopPageScaffold(contentPadding) {
        val currentSong = LocalNowPlaying.current
        val isCurrentRow = rememberPlayingRowMatcher(songs)
        val isCollectionActive = isContextSourceMatch(
            currentSong = currentSong,
            sourceId = collection.browseId,
            tracks = songs,
            isCurrentRow = isCurrentRow,
        )
        val isCollectionPlaying = isPlaying && isCollectionActive
        LazyColumn(
            contentPadding = pagePadding(
                start = DesktopPageGutter,
                top = DesktopPageGutter,
                end = DesktopPageGutter,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The services hang motion artwork off the release, so a page for one asks for
                    // it directly rather than picking a track and hoping it sits on the right
                    // edition.
                    val albumCanvas by produceState<DesktopCanvasArtwork?>(null, collection.title, animatedCanvas) {
                        value = if (!animatedCanvas) {
                            null
                        } else {
                            withContext(Dispatchers.IO) {
                                DesktopCanvasClient.lookupAlbum(
                                    collection.title,
                                    songs.firstOrNull()?.artist.orEmpty(),
                                )
                            }
                        }
                    }
                    Box(Modifier.size(286.dp).clip(RoundedCornerShape(8.dp))) {
                        DesktopArtwork(
                            collection.thumbnailUrl ?: songs.firstOrNull()?.thumbnailUrl,
                            Modifier.fillMaxSize(),
                        )
                        albumCanvas?.let {
                            DesktopCanvasView(it.url, Modifier.fillMaxSize(), fallbackUrl = it.fallbackUrl)
                        }
                    }
                    Spacer(Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            typeLabel.uppercase(),
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            collection.title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (credit.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                credit,
                                color = DesktopAccent,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (metadata.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(metadata, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(24.dp))
                        // The phone's release controls, in its order: Shuffle, Play, search this
                        // list, and everything else behind the overflow — downloading a release is
                        // done once, finding a track on it is done while reading the page.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopCircleButton(BitChordIcons.Shuffle, DesktopStrings["shuffle", "Shuffle"], onClick = { onShuffle(songs) })
                            DesktopPlayPill(
                                label = if (isCollectionPlaying) DesktopStrings["pause", "Pause"] else DesktopStrings["play", "Play"],
                                isPlaying = isCollectionPlaying,
                                onClick = {
                                    if (isCollectionActive) {
                                        onTogglePlayPause()
                                    } else if (songs.isNotEmpty()) {
                                        onPlaySongs(songs, 0)
                                    }
                                },
                                iconOnly = true,
                            )
                            onSavePlaylist?.let { save ->
                                DesktopCircleButton(
                                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    DesktopStrings["d_save_playlist", "Save playlist"],
                                    onClick = { save(collection) },
                                )
                            }
                            DesktopCircleButton(
                                if (searching) Icons.Rounded.Close else BitChordIcons.Search,
                                if (searching) DesktopStrings["close_search", "Close search"] else DesktopStrings["search_this_list", "Search this list"],
                                onClick = {
                                    searching = !searching
                                    if (!searching) listQuery = ""
                                },
                            )
                            Box {
                                DesktopCircleButton(Icons.Rounded.MoreHoriz, DesktopStrings["more", "More"], onClick = { moreOpen = true })
                                val moreShape = RoundedCornerShape(14.dp)
                                DropdownMenu(
                                    expanded = moreOpen,
                                    onDismissRequest = { moreOpen = false },
                                    modifier = Modifier
                                        .shadow(16.dp, moreShape)
                                        .clip(moreShape)
                                        .desktopBarGlass(moreShape),
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    shape = moreShape,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(DesktopStrings["download_all", "Download all"]) },
                                        leadingIcon = { Icon(BitChordIcons.Download, null) },
                                        onClick = {
                                            moreOpen = false
                                            onDownloadAll(songs)
                                        },
                                    )
                                    onSavePlaylist?.let { save ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["d_save_playlist", "Save playlist"]) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                                            onClick = {
                                                moreOpen = false
                                                save(collection)
                                            },
                                        )
                                    }
                                    onShare?.let { share ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["share", "Share"]) },
                                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                            onClick = {
                                                moreOpen = false
                                                share()
                                            },
                                        )
                                    }
                                    onRename?.let { rename ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["rename", "Rename"]) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                            onClick = {
                                                moreOpen = false
                                                rename()
                                            },
                                        )
                                    }
                                    onReorder?.let { reorder ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["reorder_playlist", "Edit order"]) },
                                            leadingIcon = { Icon(Icons.Rounded.DragHandle, null) },
                                            onClick = {
                                                moreOpen = false
                                                reorder()
                                            },
                                        )
                                    }
                                    onDelete?.let { delete ->
                                        DropdownMenuItem(
                                            text = { Text(DesktopStrings["delete", "Delete"]) },
                                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                            onClick = {
                                                moreOpen = false
                                                delete()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (searching) {
                item(key = "collection-search") {
                    DesktopSearchField(
                        query = listQuery,
                        onQueryChange = { listQuery = it },
                        onSearch = {},
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        placeholder = DesktopStrings["search_this_list", "Search this list"],
                    )
                }
            }
            itemsIndexed(shownSongs, key = { _, (index, song) -> "${song.videoId}-$index" }) { position, (index, song) ->
                DesktopCollectionSongRow(
                    // Striped by place on screen, not track number, so a filtered list still
                    // alternates.
                    striped = position % 2 == 1,
                    song = song,
                    isCurrent = isCurrentRow(song),
                    collectionType = collection.type,
                    liked = song.videoId in likedIds,
                    onClick = { _ -> onPlaySongs(songs, index) },
                    onToggleLike = onToggleLike,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = song.videoId in downloadedIds,
                    downloadInProgress = song.videoId in downloadInProgress,
                    number = index + 1,
                    onRemove = onRemoveFromPlaylist?.takeIf { song.setVideoId != null },
                )
            }
            if (collection.continuation != null) {
                // Reaching this row is what asks for the next page, the way the home feed pages.
                item(key = "collection-more") {
                    LaunchedEffect(songs.size) { onLoadMore() }
                    Box(
                        Modifier.fillMaxWidth().height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                color = DesktopAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The width of a collection row's action cluster, fixed so the duration column lines up down the
 * table whichever rows can be removed.
 */
private fun collectionActionsWidth(hasRemove: Boolean): Dp =
    if (hasRemove) 136.dp else 102.dp

@Composable
private fun DesktopCollectionSongRow(
    song: Song,
    /** From the list's [rememberPlayingRowMatcher]; null falls back to the name rule. */
    isCurrent: Boolean? = null,
    collectionType: BrowseType,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)?,
    onAddToPlaylist: ((Song) -> Unit)?,
    downloaded: Boolean,
    downloadInProgress: Boolean,
    number: Int,
    /** Every other row is shaded, so a wide row can be followed across to its buttons. */
    striped: Boolean,
    /** Takes this row out of the playlist being read. */
    onRemove: ((Song) -> Unit)? = null,
) {
    // A Spotify track still being matched to YouTube Music, or with no match: listed, but there is
    // nothing yet to play, like, save or remove.
    val unresolved = song.isUnresolvedSpotify
    val openContextMenu = LocalDesktopContextMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (song.isMatchMissing) 0.45f else 1f)
            .contextClick(if (unresolved) null else ({ openContextMenu(song) }))
            .clip(RoundedCornerShape(6.dp))
            .background(if (striped) DesktopInk.copy(alpha = 0.04f) else Color.Transparent)
            .desktopHoverFill(corner = 6.dp)
            .clickable(enabled = !unresolved) { onClick(song) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nowPlaying = isCurrent ?: song.isNowPlaying()
        val isPlayingNow = LocalDesktopIsPlaying.current
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (song.isMatchPending) {
                CircularProgressIndicator(color = DesktopSecondary, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            } else if (collectionType == BrowseType.ALBUM && nowPlaying && isPlayingNow) {
                SearchPlayingBars()
            } else if (collectionType == BrowseType.ALBUM && nowPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = DesktopStrings["now_playing", "Now playing"],
                    tint = DesktopAccent,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    number.toString(),
                    color = if (nowPlaying) DesktopAccent else DesktopSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // No sleeve on an album's rows.
        if (collectionType != BrowseType.ALBUM) {
            val rowArtShape = RoundedCornerShape(6.dp)
            Box(Modifier.size(44.dp)) {
                DesktopArtwork(song.thumbnailUrl, Modifier.fillMaxSize().clip(rowArtShape), px = ROW_ART_PX)
                DesktopCoverPlayingIndicator(
                    isCurrent = nowPlaying,
                    isPlaying = isPlayingNow,
                    shape = rowArtShape,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Row(
            Modifier.weight(0.42f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                song.title,
                Modifier.weight(1f, fill = false),
                color = if (nowPlaying) DesktopAccent else DesktopInk.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            song.sourceQualityBadge()?.let { quality ->
                SourceQualityBadge(
                    quality = quality,
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Text(
            song.artist,
            Modifier.weight(0.20f).padding(horizontal = 8.dp),
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Every row of an album is on that album; the column would only repeat the page's title.
        if (collectionType != BrowseType.ALBUM) {
            Text(
                song.albumName ?: "—",
                Modifier.weight(0.25f).padding(horizontal = 8.dp),
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            song.durationText ?: "—",
            Modifier.width(58.dp),
            color = DesktopSecondary,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.width(collectionActionsWidth(onRemove != null)),
            horizontalArrangement = Arrangement.End,
        ) {
            if (unresolved) return@Row
            onRemove?.let { remove ->
                IconButton(onClick = { remove(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        DesktopStrings["d_remove_from_playlist", "Remove from playlist"],
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onToggleLike?.let { onLike ->
                IconButton(onClick = { onLike(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        "Favorite",
                        tint = if (liked) DesktopAccent else DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onDownload?.let { onSave ->
                IconButton(onClick = { if (!downloaded) onSave(song) }, modifier = Modifier.size(34.dp)) {
                    if (downloadInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = DesktopAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (downloaded) BitChordIcons.Download else BitChordIcons.Download,
                            if (downloaded) "Downloaded" else "Download",
                            tint = if (downloaded) DesktopAccent else DesktopSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            onAddToPlaylist?.let { onAdd ->
                IconButton(onClick = { onAdd(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(BitChordIcons.Plus, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


/** The track playing, for rows that want to say so. */
private val LocalNowPlaying = compositionLocalOf<Song?> { null }

/** Whether the audio transport is actively playing, for dancing playing bars on active artwork. */
private val LocalDesktopIsPlaying = compositionLocalOf { false }

/**
 * Opens the app's song menu where the pointer is, for the desktop's own rows.
 *
 * The pages the phone shares already hang right-click off this via their long-press call; a
 * desktop-only row has no such hook, so it reads this and calls it from a [contextClick]. The
 * pages that hold it provide it from the pointer host that plants the menu under the cursor.
 */
internal val LocalDesktopContextMenu = compositionLocalOf<(Song) -> Unit> { {} }
/** Whether this row is the track being played, for the now-playing highlight. */
@Composable
private fun Song.isNowPlaying(): Boolean = isSameTrackAs(LocalNowPlaying.current)

/** Which of [rows] is the track being played: the one that was played, not every namesake. */
@Composable
private fun rememberPlayingRowMatcher(rows: List<Song>): (Song) -> Boolean {
    val current = LocalNowPlaying.current
    return remember(rows, current) { playingRowMatcher(rows, current) }
}

/** The frosted or Liquid Glass pane the floating bars and popups are made of. */
@Composable
internal fun Modifier.desktopBarGlass(
    shape: Shape,
    tintAlpha: Float = 0.46f,
    borderAlpha: Float = 0.15f,
    surfaceTint: Color? = null,
): Modifier {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val backdrop = LocalDesktopBackdrop.current
    val useGlass = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    return when {
        useGlass && backdrop != null -> desktopLiquidGlass(
            shape = shape,
            backdrop = backdrop,
            surfaceTint = surfaceTint,
            tintAlpha = tintAlpha,
            borderAlpha = borderAlpha,
        )
        useGlass -> desktopFrosted(surfaceTint ?: DesktopBarGlass, tintAlpha = tintAlpha)
            .desktopLiquidGlassSpecularRim(shape, borderAlpha = borderAlpha)
        else -> desktopFrosted(surfaceTint ?: DesktopBarGlass, tintAlpha = 0.55f).border(0.5.dp, DesktopBarEdge, shape)
    }
}

/** The frosted or Liquid Glass pane for the sidebar over window backdrop or OS material. */
@Composable
internal fun Modifier.desktopSidebarGlass(shape: Shape): Modifier {
    val material by DesktopWindowBackdrop.active.collectAsState()
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val chromeBackdrop = LocalDesktopChromeBackdrop.current
    val glass = material != DesktopBackdrop.OFF
    val useGlass = liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur
    val isDark = DesktopIsDark
    val windowGlassTintColor = if (isDark) Color.Black.copy(alpha = WINDOW_GLASS_TINT) else Color.White.copy(alpha = 0.42f)
    return when {
        useGlass && !glass && chromeBackdrop != null -> desktopLiquidGlass(
            shape = shape,
            backdrop = chromeBackdrop,
            tintAlpha = 0.42f,
            borderAlpha = 0.14f,
        )
        useGlass && !glass -> desktopFrosted(DesktopSurface.copy(alpha = 0.88f), tintAlpha = 0.42f)
            .desktopLiquidGlassSpecularRim(shape, borderAlpha = 0.14f)
        useGlass && glass -> background(windowGlassTintColor, shape)
            .desktopLiquidGlassSpecularRim(shape, borderAlpha = 0.14f)
        else -> desktopWindowGlass(DesktopChromeEdge.END, fade = 0.07f)
    }
}

/** The frosted or Liquid Glass pane the right-hand side panels (lyrics and queue) are made of. */
@Composable
internal fun Modifier.desktopSidePanelGlass(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val liquidGlass by DesktopAppearanceSettings.liquidGlass.collectAsState()
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    return if (liquidGlass && DesktopPlatform.supportsLiquidGlass && !reduceDynamicBlur) {
        desktopLiquidGlass(
            shape = shape,
            backdrop = LocalDesktopContentBackdrop.current ?: LocalDesktopBackdrop.current,
            underlayBackdrop = LocalDesktopChromeBackdrop.current,
            tintAlpha = 0.42f,
            borderAlpha = 0.14f,
        )
    } else {
        desktopFrosted(DesktopBarGlass, tintAlpha = 0.55f)
    }
}

/** The edge of a chrome pane that meets the page, and so has to dissolve into it. */
internal enum class DesktopChromeEdge { NONE, BOTTOM, END }

/**
 * The same pane for the window's own chrome — square-edged, and a shade denser, because the sidebar
 * and top bar carry far more text than a floating bar does.
 *
 * [edge] names the side the page is on. The pane's tint is dissolved across the last [fade] of it so
 * the two meet in a gradient rather than on a line.
 */
@Composable
internal fun Modifier.desktopChromeGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier = desktopFrosted(
    opaque = DesktopSurface.copy(alpha = 0.88f),
    tintAlpha = 0.46f,
    edge = edge,
    fade = fade,
)

/**
 * The window's own chrome — title bar, top bar, sidebar. Over Windows 11's Mica or Acrylic
 * ([DesktopWindowBackdrop]) it blends softly with the native backdrop rather than occluding it.
 */
@Composable
internal fun Modifier.desktopWindowGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier {
    val backdrop by DesktopWindowBackdrop.active.collectAsState()
    val isDark = DesktopIsDark
    val tintColor = if (isDark) Color.Black.copy(alpha = WINDOW_GLASS_TINT) else Color.White.copy(alpha = 0.42f)
    return if (backdrop == DesktopBackdrop.OFF) {
        desktopChromeGlass(edge, fade)
    } else {
        val base = background(tintColor)
        if (DesktopPlatform.isMac && edge == DesktopChromeEdge.BOTTOM) {
            base.drawBehind {
                // Subtle liquid glass specular highlight along the top edge
                drawLine(
                    color = Color.White.copy(alpha = if (isDark) 0.08f else 0.45f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
        } else {
            base
        }
    }
}

/** Enough to keep text readable over a bright wallpaper, little enough to let it through. */
private const val WINDOW_GLASS_TINT = 0.28f

/**
 * The separators in the window's chrome.
 */
@Composable
internal fun desktopChromeDivider(): Color {
    val backdrop by DesktopWindowBackdrop.active.collectAsState()
    val ink = DesktopInk
    return when {
        backdrop == DesktopBackdrop.ACRYLIC -> ink.copy(alpha = 0.14f)
        DesktopPlatform.isMac && backdrop != DesktopBackdrop.OFF -> ink.copy(alpha = 0.10f)
        else -> DesktopDivider
    }
}

@Composable
private fun Modifier.desktopFrosted(
    opaque: Color,
    tintAlpha: Float,
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0f,
): Modifier {
    val haze = LocalDesktopHaze.current
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    if (haze == null || reduceDynamicBlur) {
        return this.background(edge.dissolve(opaque, fade) ?: SolidColor(opaque))
    }
    val surfaceColor = DesktopSurface
    val effectiveTint = if (DesktopIsDark) tintAlpha else (tintAlpha * 0.72f).coerceAtMost(0.55f)
    val dissolve = edge.dissolve(Color.Black, fade)
    return this.hazeEffect(state = haze) {
        // Spelled out rather than taken from a preset.
        blurEnabled = true
        backgroundColor = surfaceColor
        blurRadius = 30.dp
        tints = listOf(HazeTint(surfaceColor.copy(alpha = effectiveTint)))
        noiseFactor = 0.04f
        mask = dissolve
    }
}

/** The gradient that takes [colour] to nothing across the pane's last [fade]. */
private fun DesktopChromeEdge.dissolve(colour: Color, fade: Float): Brush? {
    if (this == DesktopChromeEdge.NONE || fade <= 0f) return null
    val stops = arrayOf(
        0f to colour,
        (1f - fade).coerceIn(0f, 1f) to colour,
        1f to Color.Transparent,
    )
    return if (this == DesktopChromeEdge.BOTTOM) {
        Brush.verticalGradient(*stops)
    } else {
        Brush.horizontalGradient(*stops)
    }
}

/** Android's wording for the Automix line, so both players read alike. */
private fun TrackAnalysisState.label(): String = when (this) {
    TrackAnalysisState.ANALYSED -> "analysed"
    TrackAnalysisState.REFINING -> "analysed, refining…"
    TrackAnalysisState.ANALYSING -> "analysing…"
    TrackAnalysisState.WAITING -> "waiting"
    TrackAnalysisState.FAILED -> "failed"
}

@Composable
private fun DesktopShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    gutter: Dp = DesktopPageGutter,
) {
    Column {
        SectionTitle(shelf.title, shelf.subtitle, gutter)
        DesktopScrollableRow(gutter = gutter) {
            items(shelf.items, key = { it.videoId ?: it.browseId ?: it.title }) { item ->
                DesktopShelfCard(item) { onItemClick(it, shelf.title) }
            }
        }
    }
}

/**
 * A sideways row with the affordances a desktop needs to move it.
 *
 * A carousel longer than the window has no way to be scrolled with a mouse — the wheel belongs to
 * the page underneath — so this adds the three that work: dragging it, the pointer's horizontal
 * ticks while it is hovered, and a paging arrow at whichever end still has more.
 *
 * Shared rather than repeated: every shelf-shaped row in the app wants the same three.
 */
@Composable
internal fun DesktopScrollableRow(
    gutter: Dp = DesktopPageGutter,
    spacing: Dp = 14.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val rowState = rememberLazyListState()
    DesktopShelfRowChrome.Wrap(rowState) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * What every sideways shelf is wrapped in on the desktop — the shared pages' rows too, through
 * [LocalShelfRowChrome]. A mouse cannot swipe a row, so it gets the arrows it pages with, a drag
 * to pull it by, and the horizontal wheel while the pointer is over it.
 */
internal object DesktopShelfRowChrome : ShelfRowChrome {
    @Composable
    override fun Wrap(state: LazyListState, row: @Composable () -> Unit) {
        val scope = rememberCoroutineScope()
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        /** One screenful, less a sliver so the card at the edge stays in view. */
        fun page(forward: Boolean) {
            val viewport = state.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
            val distance = (viewport - SHELF_PAGE_OVERLAP_PX).coerceAtLeast(SHELF_PAGE_OVERLAP_PX)
            scope.launch { state.animateScrollBy(if (forward) distance.toFloat() else -distance.toFloat()) }
        }

        // Whichever row the pointer is in takes the horizontal ticks; the page underneath keeps the
        // wheel, as it does everywhere else.
        LaunchedEffect(hovered) {
            if (!hovered) return@LaunchedEffect
            DesktopHorizontalScroll.ticks.collect { tick -> state.scrollBy(tick * SHELF_WHEEL_STEP) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .hoverable(interaction)
                // A row can be taken hold of and pulled, which is the first thing anyone tries.
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { state.scrollBy(-delta) } },
                    orientation = Orientation.Horizontal,
                ),
        ) {
            row()
            DesktopShelfArrow(
                alignment = Alignment.CenterStart,
                icon = Icons.Rounded.ChevronLeft,
                description = DesktopStrings["d_scroll_left", "Scroll left"],
                visible = hovered && state.canScrollBackward,
                onClick = { page(forward = false) },
            )
            DesktopShelfArrow(
                alignment = Alignment.CenterEnd,
                icon = BitChordIcons.ChevronRight,
                description = DesktopStrings["d_scroll_right", "Scroll right"],
                visible = hovered && state.canScrollForward,
                onClick = { page(forward = true) },
            )
        }
    }
}

/** How much of the outgoing card stays visible after a paged scroll. */
internal const val SHELF_PAGE_OVERLAP_PX = 120

/** How far one notch of a horizontal scroll moves a shelf. */
internal const val SHELF_WHEEL_STEP = 90f

@Composable
internal fun BoxScope.DesktopShelfArrow(
    alignment: Alignment,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(alignment).padding(horizontal = 6.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DesktopShelfCard(item: ShelfItem, onClick: (ShelfItem) -> Unit) {
    val width = 158.dp
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(width)
            // The whole card answers the pointer, artwork and captions together, so the two do not
            // move independently of each other.
            .desktopHoverLift(shape)
            .clickable { onClick(item) },
    ) {
        Box(
            Modifier
                .size(width)
                .clip(shape)
                .background(DesktopGlass)
                .border(1.dp, DesktopCardEdge, shape),
        ) {
            DesktopArtwork(item.thumbnailUrl, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DesktopSongRow(
    song: Song,
    /** From the list's [rememberPlayingRowMatcher]; null falls back to the name rule. */
    isCurrent: Boolean? = null,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    downloaded: Boolean = false,
    downloadInProgress: Boolean = false,
    number: Int? = null,
    /** The row's own "…", when the page has a menu to give it. */
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val openContextMenu = LocalDesktopContextMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .contextClick { openContextMenu(song) }
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopGlass.copy(alpha = 0.38f))
            .desktopHoverFill(corner = 10.dp)
            .clickable { onClick(song) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rowCurrent = isCurrent ?: song.isNowPlaying()
        val rowPlaying = LocalDesktopIsPlaying.current
        if (number != null) {
            Text(
                "$number",
                Modifier.width(28.dp),
                color = if (rowCurrent) DesktopAccent else DesktopSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(12.dp))
        }
        val artShape = RoundedCornerShape(8.dp)
        Box(Modifier.size(52.dp)) {
            DesktopArtwork(
                song.thumbnailUrl,
                Modifier.fillMaxSize().clip(artShape),
                px = ROW_ART_PX,
                placeholder = BitChordIcons.MusicNote,
            )
            DesktopCoverPlayingIndicator(
                isCurrent = rowCurrent,
                isPlaying = rowPlaying,
                shape = artShape,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (rowCurrent) DesktopAccent else DesktopInk.copy(alpha = 0.88f),
            )
            Text(listOfNotNull(song.artist.takeIf(String::isNotBlank), song.durationText).joinToString(" · "), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        song.sourceQualityBadge()?.let { quality ->
            SourceQualityBadge(
                quality = quality,
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (onToggleLike != null) {
            IconButton(onClick = { onToggleLike(song) }) { Icon(if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart, "Favorite", tint = if (liked) DesktopAccent else DesktopSecondary) }
        }
        if (onDownload != null) {
            if (downloadInProgress) {
                IconButton(onClick = { onDownload(song) }) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DesktopAccent,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                IconButton(onClick = { if (!downloaded) onDownload(song) }) {
                    Icon(
                        imageVector = if (downloaded) BitChordIcons.Download else BitChordIcons.Download,
                        contentDescription = if (downloaded) "Downloaded" else "Download",
                        tint = if (downloaded) DesktopAccent else DesktopSecondary,
                    )
                }
            }
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = { onAddToPlaylist(song) }) {
                Icon(BitChordIcons.Plus, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary)
            }
        }
        // The same menu every other surface opens, so a row on a list page offers what a row on
        // the player does; the row itself plays.
        menu?.invoke(song)
    }
}

/** A page's box, inset from the shell's chrome. */
@Composable
private fun DesktopPageScaffold(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    val direction = LocalLayoutDirection.current
    Box(
        Modifier.fillMaxSize().padding(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
            top = contentPadding.calculateTopPadding(),
        ),
    ) {
        CompositionLocalProvider(
            LocalDesktopBottomInset provides contentPadding.calculateBottomPadding(),
        ) {
            content()
        }
    }
}

/** A scrollable's own padding, plus room to clear whatever floats over the page. */
@Composable
private fun pagePadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom + LocalDesktopBottomInset.current,
)

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String = "",
    gutter: Dp = DesktopPageGutter,
    /** An action belonging to this heading, drawn at the far end of it. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}

@Composable
private fun DesktopLoadingPage(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DesktopAccent)
            Spacer(Modifier.height(14.dp))
            Text(label, color = DesktopSecondary)
        }
    }
}

@Composable
private fun DesktopErrorPage(message: String, onRetry: () -> Unit) {
    DesktopEmptyPage(Icons.Rounded.Tune, "Something went wrong", message, onRetry, "Try again")
}

@Composable
private fun DesktopEmptyPage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, onAction: (() -> Unit)? = null, actionLabel: String? = null) {
    Column(Modifier.fillMaxWidth().padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = DesktopSecondary, textAlign = TextAlign.Center)
        if (onAction != null && actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** Material's slider with the step ticks and end-stop dot taken off, as Android draws them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBareSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = DesktopAccent,
        activeTrackColor = DesktopAccent,
        inactiveTrackColor = DesktopInk.copy(alpha = 0.22f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun DesktopActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier
            .clip(CircleShape)
            .desktopGlassPress(
                shape = CircleShape,
                interactionSource = interactionSource,
                pressScale = 0.95f,
            )
            .background(DesktopAccent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopOnAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = DesktopOnAccent, fontWeight = FontWeight.SemiBold)
    }
}

private fun SearchResult.key(): String = when (this) {
    is SearchResult.TopTrack -> "track:${song.videoId}"
    is SearchResult.Track -> "track:${song.videoId}"
    is SearchResult.Browse -> "browse:${item.browseId}"
}

private fun ShelfItem.toSong(): Song {
    val parts = subtitle.split(" • ", " · ", " | ").filter(String::isNotBlank)
    // Android's InnertubeParser uses the first non-type/non-tally segment.
    val artist = parts.firstOrNull {
        !it.matches(SHELF_DURATION) &&
            it.lowercase() !in SHELF_TYPE_WORDS &&
            !it.matches(SHELF_TALLY)
    } ?: "Unknown Artist"
    return Song(
        videoId = videoId.orEmpty(),
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = parts.firstOrNull { it.matches(SHELF_DURATION) },
    )
}

private fun String.isUnknownArtist(): Boolean =
    trim().equals("unknown", ignoreCase = true) ||
        trim().equals("unknown artist", ignoreCase = true) ||
        trim().equals("unknown artist(s)", ignoreCase = true) ||
        isBlank()

private val SHELF_DURATION = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
private val SHELF_TALLY = Regex("[0-9,.]+\\s*(?:views?|likes?|songs?)", RegexOption.IGNORE_CASE)
private val SHELF_TYPE_WORDS = setOf("song", "video", "album", "ep", "single", "playlist", "mix")

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatCollectionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    return if (minutes < 60L) {
        "$minutes min"
    } else {
        "${minutes / 60L} hr ${minutes % 60L} min"
    }
}

private fun formatSleepTimer(ms: Long): String = formatTime(ms)
