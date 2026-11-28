package com.music.bitchord.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.MixTransitionView
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.ui.player.AudioOutputDevice
import com.music.bitchord.ui.player.AudioOutputKind
import com.music.bitchord.ui.player.AutoMixArtworkPreferences
import com.music.bitchord.ui.player.BestPartStart
import com.music.bitchord.ui.player.CanvasVideoSpec
import com.music.bitchord.ui.player.LyricsRomanizationResult
import com.music.bitchord.ui.player.LyricsTranslationResult
import com.music.bitchord.ui.player.MAX_LYRICS_OFFSET_MS
import com.music.bitchord.ui.player.MIN_LYRICS_OFFSET_MS
import com.music.bitchord.ui.player.OutputFormatUi
import com.music.bitchord.ui.player.PartyUi
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerSettingsSource
import com.music.bitchord.ui.player.SystemVolume
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The desktop's answers to the shared player's [PlayerHost].
 *
 * The player itself is the phone's, line for line; this is only what sits
 * under it here — the desktop's settings, its FFmpeg Canvas decoder, its mixers
 * as outputs, its party, its translation client. The window keeps the few
 * pieces of live state it owns ([volumeLevel], [pipelineDialog]) up to date.
 */
internal object DesktopPlayerHost : PlayerHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val settings: DesktopPlayerSettings = DesktopPlayerSettings

    override fun cachedCanvas(song: Song): CanvasArtwork? = DesktopCanvasClient.cached(song)?.toShared()

    override suspend fun canvasFor(song: Song): CanvasArtwork? =
        withContext(Dispatchers.IO) { DesktopCanvasClient.lookup(song) }?.toShared()

    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) = DesktopCanvasVideo(spec, modifier)

    override val musicVideoSupported: Boolean get() = true

    @Composable
    override fun MusicVideo(videoId: String, fit: Boolean, onRenderedChanged: (Boolean) -> Unit, modifier: Modifier) =
        DesktopMusicVideoSurface(videoId, fit, onRenderedChanged, modifier)

    override fun showsMusicVideoPicture(videoId: String): Boolean =
        DesktopVersionSwitcher.showsVideoPicture(videoId)

    override val musicVideoAspect: StateFlow<Float> get() = DesktopMusicVideoEngine.aspect

    override val musicVideoQuality: StateFlow<com.music.bitchord.ui.player.MusicVideoQualityUi>
        get() = DesktopMusicVideoEngine.quality

    override fun setMusicVideoQuality(height: Int?) = DesktopMusicVideoEngine.setQuality(height)

    val videoFullscreen = MutableStateFlow(false)

    override fun setVideoFullscreen(active: Boolean) {
        videoFullscreen.value = active
    }

    // Local files carry their art on the row already; there is no remote store to ask.
    @Composable
    override fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl

    override suspend fun bestPartMs(song: Song): Long? =
        DesktopBestParts.startFor(song).takeIf { it.source != BestPartStart.Source.FALLBACK }?.startMs

    /** The player's software volume, 0..1, as the window holds it. */
    private val softwareVolumeLevel = MutableStateFlow(1f)

    fun setSoftwareVolume(level: Float) {
        softwareVolumeLevel.value = level.coerceIn(0f, 1f)
    }

    /** Where a drag on the shared player's volume bar goes when not in Bit-Perfect hardware mode. */
    @Volatile
    var onSoftwareVolumeChange: (Float) -> Unit = { softwareVolumeLevel.value = it }

    /** Effective volume exposed to shared player & output sheet: hardware DAC level when Bit-Perfect is active. */
    val volumeLevel: StateFlow<Float> = combine(
        softwareVolumeLevel,
        DesktopBitPerfect.status,
        DesktopDacVolume.level,
    ) { sw, bp, hw ->
        when {
            bp.active && hw != null -> hw
            bp.active -> 1f
            else -> sw
        }
    }.stateIn(scope, SharingStarted.Eagerly, 1f)

    override val volume: SystemVolume = object : SystemVolume {
        override val level: StateFlow<Float> = volumeLevel
        override fun set(level: Float) {
            val next = level.coerceIn(0f, 1f)
            if (DesktopBitPerfect.status.value.active) {
                if (DesktopDacVolume.level.value != null) {
                    DesktopDacVolume.set(next)
                }
            } else {
                softwareVolumeLevel.value = next
                onSoftwareVolumeChange(next)
            }
        }
        override fun refresh() {
            if (DesktopBitPerfect.status.value.active) {
                DesktopDacVolume.refresh()
            }
        }
    }

    /** The last list handed to the drawer, for turning a row's id back into a mixer. */
    @Volatile
    private var listed: List<String> = emptyList()

    @Composable
    override fun rememberAudioOutputs(): List<AudioOutputDevice> {
        val selected by DesktopAudioDevices.selected.collectAsState()
        val changes by DesktopAudioDevices.changes.collectAsState()
        val devices = remember(changes) { DesktopAudioDevices.available() }
        return remember(selected, devices) {
            val ids = listOf(DesktopAudioDevices.SYSTEM_DEFAULT) + devices.map { it.id }
            listed = ids
            ids.mapIndexed { index, id ->
                val name = if (id == DesktopAudioDevices.SYSTEM_DEFAULT) {
                    DesktopStrings["d_system_default", "System default"]
                } else {
                    devices.first { it.id == id }.name
                }
                AudioOutputDevice(
                    id = index,
                    name = name,
                    kind = kindOf(name),
                    isActive = id == selected,
                )
            }
        }
    }

    @Composable
    override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = onOpen

    override fun selectAudioOutput(id: Int?) {
        val device = id?.let { listed.getOrNull(it) } ?: DesktopAudioDevices.SYSTEM_DEFAULT
        DesktopAudioDevices.select(device)
    }

    /** The engine's output stage, kept current by the window. */
    val pipeline = MutableStateFlow(DesktopAudioPipeline())

    override val outputFormat: StateFlow<OutputFormatUi> = pipeline
        .map { it.toOutputFormat() }
        .stateIn(scope, SharingStarted.Eagerly, OutputFormatUi())

    override val party: StateFlow<PartyUi> = DesktopListenTogether.state
        .map {
            PartyUi(
                inParty = it.inParty,
                controlsLocked = it.controlsLocked,
                members = it.members,
                you = it.you,
                code = it.code,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, PartyUi())

    override suspend fun translateLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsTranslationResult =
        when (val result = LyricsTranslation.translate(trackId, lines, targetLanguageTag)) {
            is LyricsTranslation.Result.Translated -> LyricsTranslationResult.Translated(result.lines)
            is LyricsTranslation.Result.SameLanguage -> LyricsTranslationResult.SameLanguage(result.language)
            LyricsTranslation.Result.Unavailable -> LyricsTranslationResult.Unavailable
        }

    override suspend fun romanizeLyrics(
        trackId: String,
        lines: List<LyricLine>,
        targetLanguageTag: String,
    ): LyricsRomanizationResult =
        when (val result = LyricsTranslation.romanize(trackId, lines, targetLanguageTag)) {
            is LyricsTranslation.RomanizationResult.Romanized -> LyricsRomanizationResult.Romanized(result.lines)
            LyricsTranslation.RomanizationResult.AlreadyRomanized -> LyricsRomanizationResult.AlreadyRomanized
            LyricsTranslation.RomanizationResult.Unavailable -> LyricsRomanizationResult.Unavailable
        }

    /** The newest notice, for the window to show and clear. */
    val messages = MutableStateFlow<String?>(null)

    override fun showMessage(message: String) {
        messages.value = message
    }

    /** The desktop's signal-chain readout, supplied by the window that has the engine. */
    @Volatile
    var pipelineDialog: (@Composable (hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) -> Unit)? = null

    @Composable
    override fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) {
        pipelineDialog?.invoke(hazeState, isPlaying, onDismiss)
    }
}

/**
 * The shared player's settings, as flows the desktop window feeds from its own
 * state. What the desktop has no setting for keeps the phone's default.
 */
internal object DesktopPlayerSettings : PlayerSettingsSource {
    private val persistence by lazy { DesktopPersistence() }

    override val animatedCanvas = MutableStateFlow(true)
    override val audioQualityCellular = MutableStateFlow(AudioQuality.LOSSLESS)
    override val audioQualityWifi = MutableStateFlow(AudioQuality.LOSSLESS)
    override val canvasOverCellular = MutableStateFlow(true)
    override val fullBleedArtwork = MutableStateFlow(false)
    override val showTotalDuration = MutableStateFlow(persistence.boolean(KEY_SHOW_TOTAL_DURATION, false))
    override val hideSongStatus = MutableStateFlow(false)
    override val hideLyricsPreview = DesktopAppearanceSettings.hideLyricsPreview
    override val autoHideLyricsInVideo = DesktopAppearanceSettings.autoHideLyricsInVideo
    override val revealedVideoLyricsId = DesktopAppearanceSettings.revealedVideoLyricsId
    override val showQueueHistory = MutableStateFlow(false)
    override val hideVolumeBar = DesktopAppearanceSettings.hideVolumeBar
    // A desktop window has no status bar to hide.
    override val hideStatusBar = MutableStateFlow(false)
    override val lastPlayerScreen = MutableStateFlow(
        runCatching { LastPlayerScreen.valueOf(persistence.string(KEY_LAST_SCREEN, "MAIN")) }
            .getOrDefault(LastPlayerScreen.MAIN),
    )
    override val playerTheme = MutableStateFlow(PlayerTheme.DEFAULT)
    val legacyMeshGradient = MutableStateFlow(false)
    override val lyricsBlur = MutableStateFlow(true)
    override val lyricsOffsetMs = MutableStateFlow(
        persistence.int(KEY_LYRICS_OFFSET, 0).coerceIn(MIN_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS),
    )
    override val lyricsSourceOrder = MutableStateFlow<List<LyricsSource>>(LyricsSource.offered)

    // A desktop is never on a metered link as far as this app can tell.
    override val meteredConnection = MutableStateFlow<Boolean?>(false)
    override val streamCeilingOverride = MutableStateFlow<AudioQuality?>(null)
    override val preferUsbDac = MutableStateFlow(false)
    override val disableSpotifyCanvas = MutableStateFlow(false)
    override val prioritizeSpotifyCanvas = MutableStateFlow(false)
    override val reduceAnimation = DesktopAppearanceSettings.reduceAnimation
    override val reduceDynamicBlur = DesktopAppearanceSettings.reduceDynamicBlur
    override val disableBlendEffects = DesktopAppearanceSettings.disableBlendEffects
    override val showNerdStats = MutableStateFlow(false)
    override val showBestPart = MutableStateFlow(persistence.boolean(KEY_SHOW_BEST_PART, true))
    override val showListenTogetherButton = MutableStateFlow(true)
    override val smartAnalysis = MutableStateFlow(SmartAnalysis())
    override val automixDetail = MutableStateFlow<String?>(null)
    override val smartFadeEnabled = MutableStateFlow(false)
    override val autoMixArtwork = MutableStateFlow(
        AutoMixArtworkPreferences(
            artTransition = persistence.boolean(KEY_AUTOMIX_ART_TRANSITION, false),
            miniPlayerTransition = persistence.boolean(KEY_AUTOMIX_MINI_ART_TRANSITION, false),
        )
    )
    override val smartMixInProgress = MutableStateFlow(false)
    override val smartMixBlend = MutableStateFlow<MixBlend?>(null)
    override val smartMixView = MutableStateFlow<MixTransitionView?>(null)
    override val smartMixProgress = MutableStateFlow(0f)
    override val smartMixPreview = MutableStateFlow<MixTransitionView?>(null)
    override val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)
    override val spotifyCanvasAutoHide = MutableStateFlow(true)
    override val syncedLyrics = MutableStateFlow(true)
    override val translationLanguage = MutableStateFlow("")
    override val versionAlignmentInProgress = MutableStateFlow(false)

    override fun setLastPlayerScreen(value: LastPlayerScreen) {
        lastPlayerScreen.value = value
        persistence.saveString(KEY_LAST_SCREEN, value.name)
    }

    override fun setLyricsOffsetMs(value: Int) {
        val clamped = value.coerceIn(MIN_LYRICS_OFFSET_MS, MAX_LYRICS_OFFSET_MS)
        lyricsOffsetMs.value = clamped
        persistence.saveInt(KEY_LYRICS_OFFSET, clamped)
    }

    override fun setMixViewOpen(open: Boolean) {}

    override fun setHideLyricsPreview(value: Boolean) = DesktopAppearanceSettings.setHideLyricsPreview(value)

    override fun setAutoHideLyricsInVideo(value: Boolean) = DesktopAppearanceSettings.setAutoHideLyricsInVideo(value)

    override fun setVideoLyricsRevealed(videoId: String, revealed: Boolean) =
        DesktopAppearanceSettings.setVideoLyricsRevealed(videoId, revealed)

    fun setShowTotalDuration(value: Boolean) {
        showTotalDuration.value = value
        persistence.saveBoolean(KEY_SHOW_TOTAL_DURATION, value)
    }

    fun setShowBestPart(value: Boolean) {
        showBestPart.value = value
        persistence.saveBoolean(KEY_SHOW_BEST_PART, value)
    }

    fun setAutoMixArtTransition(enabled: Boolean) {
        autoMixArtwork.value = autoMixArtwork.value.copy(artTransition = enabled)
        persistence.saveBoolean(KEY_AUTOMIX_ART_TRANSITION, enabled)
    }

    fun setAutoMixMiniPlayerTransition(enabled: Boolean) {
        autoMixArtwork.value = autoMixArtwork.value.copy(miniPlayerTransition = enabled)
        persistence.saveBoolean(KEY_AUTOMIX_MINI_ART_TRANSITION, enabled)
    }

    private const val KEY_LAST_SCREEN = "last_player_screen"
    private const val KEY_LYRICS_OFFSET = "lyrics_offset_ms"
    private const val KEY_SHOW_TOTAL_DURATION = "show_total_duration"
    private const val KEY_SHOW_BEST_PART = "show_best_part"
    private const val KEY_AUTOMIX_ART_TRANSITION = "automix_art_transition"
    private const val KEY_AUTOMIX_MINI_ART_TRANSITION = "automix_mini_art_transition"
}

/** Which glyph a mixer gets, from the only thing Java Sound says about it: its name. */
private fun kindOf(name: String): AudioOutputKind {
    val n = name.lowercase(Locale.ROOT)
    return when {
        "bluetooth" in n || "airpods" in n || "buds" in n -> AudioOutputKind.BLUETOOTH
        "usb" in n || "dac" in n -> AudioOutputKind.USB
        "hdmi" in n || "display" in n || "nvidia" in n -> AudioOutputKind.HDMI
        "headphone" in n || "headset" in n -> AudioOutputKind.WIRED
        else -> AudioOutputKind.OTHER
    }
}

private fun DesktopAudioPipeline.toOutputFormat(): OutputFormatUi {
    val bp = bitPerfect
    if (bp.active && bp.sampleRateHz > 0 && bp.outputBits > 0) {
        return OutputFormatUi(
            summary = bp.label.orEmpty(),
            bitPerfect = bp.exact,
            dacLimited = bp.dacLimited,
        )
    }
    val depth = when {
        outputIsFloat == true -> "32-bit float"
        outputBytesPerSample != null -> "${outputBytesPerSample * 8}-bit PCM"
        else -> null
    }
    val rate = outputSampleRateHz?.takeIf { it > 0 }
        ?.let { "${"%.1f".format(Locale.ROOT, it / 1000f).removeSuffix(".0")} kHz" }
    return OutputFormatUi(
        summary = listOfNotNull(depth, rate).joinToString(" · "),
        bitPerfect = false,
        dacLimited = false,
    )
}

/** The shared provider a desktop source name refers to, matched on its label. */
internal fun lyricsSourceNamed(name: String): LyricsSource? =
    LyricsSource.entries.firstOrNull { it.label.equals(name, ignoreCase = true) }
        ?: LyricsSource.entries.firstOrNull { it.name.equals(name.replace(' ', '_'), ignoreCase = true) }
