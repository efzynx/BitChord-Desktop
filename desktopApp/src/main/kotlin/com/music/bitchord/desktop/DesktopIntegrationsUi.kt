package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.SpotifyImportTrack
import com.music.bitchord.data.spotify.SpotifyImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Signing in to Last.fm — the whole setup in one dialog.
 *
 * Last.fm signs every request with the *app's* API key and shared secret, so a
 * build that shipped without them baked in (no `local.properties`) still needs
 * them before the account password means anything. Android lets the listener
 * enter those two under AppSettings; here they join the username and password on
 * one screen so there is a single, obvious way in rather than a switch that is
 * greyed out until a key turns up from somewhere. The key and secret are kept so
 * later scrobbles can sign themselves; the password is sent to
 * `auth.getMobileSession` once and only the returned session key is stored.
 */
@Composable
internal fun DesktopLastfmLoginDialog(onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf(DesktopScrobbleSettings.lastfmApiKey.value) }
    var apiSecret by remember { mutableStateOf(DesktopScrobbleSettings.lastfmApiSecret.value) }
    var username by remember { mutableStateOf(DesktopScrobbleSettings.lastfmUsername.value) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // A release build signs in with BitChord's own app key, so only a build
    // without one asks for the listener's — the two fields are not shown otherwise.
    val needsAppKey = !DesktopScrobbling.hasBuiltInLastFmKey
    val ready = username.isNotBlank() && password.isNotBlank() &&
        (!needsAppKey || (apiKey.isNotBlank() && apiSecret.isNotBlank()))

    fun submit() {
        if (busy || !ready) return
        busy = true
        error = null
        // Save the app credentials first: the sign-in request is signed with them,
        // and every scrobble afterwards reads them back from here.
        if (needsAppKey) {
            DesktopScrobbleSettings.setLastfmApiKey(apiKey.trim())
            DesktopScrobbleSettings.setLastfmApiSecret(apiSecret.trim())
        }
        scope.launch {
            DesktopScrobbling.signInToLastFm(username.trim(), password)
                .onSuccess { onDismiss() }
                .onFailure { error = it.message ?: "Last.fm would not sign you in" }
            busy = false
        }
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 400) {
        DialogHeading(
            title = DesktopStrings["d_sign_in_to_last_fm", "Sign in to Last.fm"],
            message = error
                ?: if (needsAppKey) {
                    "Your password is sent to Last.fm once and never stored. The API key " +
                        "and shared secret are your app's, from last.fm/api."
                } else {
                    "Your password is sent to Last.fm once and never stored."
                },
            isError = error != null,
        )
        if (needsAppKey) {
            DialogField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = DesktopStrings["lastfm_api_key", "API key"],
                enabled = !busy,
                imeAction = ImeAction.Next,
                modifier = Modifier.focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            DialogField(
                value = apiSecret,
                onValueChange = { apiSecret = it },
                placeholder = DesktopStrings["lastfm_shared_secret", "Shared secret"],
                enabled = !busy,
                isPassword = true,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(8.dp))
        }
        DialogField(
            value = username,
            onValueChange = { username = it },
            placeholder = DesktopStrings["username", "Username"],
            enabled = !busy,
            imeAction = ImeAction.Next,
            modifier = if (needsAppKey) Modifier else Modifier.focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        DialogField(
            value = password,
            onValueChange = { password = it },
            placeholder = DesktopStrings["password", "Password"],
            enabled = !busy,
            isPassword = true,
            onSubmit = ::submit,
        )
        if (needsAppKey) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp)),
                horizontalArrangement = Arrangement.Start,
            ) {
                TextButton(onClick = { DesktopExternalLinks.open("https://www.last.fm/api/account/create") }) {
                    Text(
                        DesktopStrings["d_get_an_api_key", "Get an API key"],
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        DialogActions(
            confirm = if (busy) "Signing in…" else "Sign in",
            confirmEnabled = !busy && ready,
            busy = busy,
            onConfirm = ::submit,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The `sp_dc` cookie Spotify Canvas needs.
 *
 * Android's `SpotifyCanvasAuthScreen`, with its four steps unchanged — they name a browser's
 * developer tools, which is where a desktop listener already is.
 */
@Composable
internal fun DesktopSpotifyCanvasDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var token by remember { mutableStateOf(DesktopSpotifyToken.cookie()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun save() {
        DesktopSpotifyToken.setCookie(token.trim())
        onSaved(token.trim())
        onDismiss()
    }

    val isPage = LocalDesktopPanelIsPage.current
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420) {
        if (isPage) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                DesktopSettingsSubPageHeader(
                    title = DesktopStrings["spotify_canvas_setup", "Spotify Canvas setup"],
                    subtitle = DesktopStrings["spotify_canvas_setup_description", "To use Spotify Canvas, provide your Spotify sp_dc cookie."],
                    onBack = onDismiss,
                )
                DesktopGroupedCard(
                    header = DesktopStrings["d_how_to_connect", "How to connect"],
                    topSpacing = 4.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            DesktopStrings[
                                "spotify_canvas_setup_steps",
                                "1. Sign in at open.spotify.com in a browser.\n" +
                                    "2. Open Developer Tools, then Application and Cookies.\n" +
                                    "3. Copy the value of the sp_dc cookie.\n" +
                                    "4. Paste it below.",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        DesktopGlassPillButton(
                            text = DesktopStrings["d_open_spotify", "Open Spotify"],
                            onClick = { DesktopExternalLinks.open("https://open.spotify.com/") },
                        )
                    }
                }
                DesktopGroupedCard(
                    header = DesktopStrings["spdc_token", "sp_dc token"],
                    topSpacing = 18.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DesktopGlassTextField(
                            value = token,
                            onValueChange = { token = it },
                            placeholder = DesktopStrings["spdc_token", "sp_dc token"],
                            isPassword = true,
                            onSubmit = ::save,
                            modifier = Modifier.focusRequester(focus),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            DesktopGlassPillButton(
                                text = DesktopStrings["cancel", "Cancel"],
                                onClick = onDismiss,
                            )
                            DesktopGlassPillButton(
                                text = DesktopStrings["save", "Save"],
                                accent = true,
                                onClick = ::save,
                            )
                        }
                    }
                }
            }
        } else {
            DialogHeading(
                title = DesktopStrings["spotify_canvas_setup", "Spotify Canvas setup"],
                message = DesktopStrings["spotify_canvas_setup_description", "To use Spotify Canvas, provide your Spotify sp_dc cookie."],
            )
            Text(
                DesktopStrings[
                    "spotify_canvas_setup_steps",
                    "1. Sign in at open.spotify.com in a browser.\n" +
                        "2. Open Developer Tools, then Application and Cookies.\n" +
                        "3. Copy the value of the sp_dc cookie.\n" +
                        "4. Paste it below.",
                ],
                modifier = Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
            )
            Spacer(Modifier.height(12.dp))
            DialogField(
                value = token,
                onValueChange = { token = it },
                placeholder = DesktopStrings["spdc_token", "sp_dc token"],
                isPassword = true,
                onSubmit = ::save,
                modifier = Modifier.focusRequester(focus),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp)),
                horizontalArrangement = Arrangement.Start,
            ) {
                TextButton(onClick = { DesktopExternalLinks.open("https://open.spotify.com/") }) {
                    Text(DesktopStrings["d_open_spotify", "Open Spotify"], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            DialogActions(
                confirm = DesktopStrings["save", "Save"],
                confirmEnabled = true,
                busy = false,
                onConfirm = ::save,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * ListenBrainz's user token.
 *
 * One field and no verification, as on Android: ListenBrainz has no endpoint
 * that says whether a token is good, so the first listen is the test.
 */
@Composable
internal fun DesktopListenBrainzTokenDialog(onDismiss: () -> Unit) {
    var token by remember { mutableStateOf(DesktopScrobbleSettings.listenBrainzToken.value) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun save() {
        DesktopScrobbleSettings.setListenBrainzToken(token.trim())
        DesktopScrobbleSettings.setListenBrainzEnabled(token.isNotBlank())
        onDismiss()
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 380) {
        DialogHeading(
            title = DesktopStrings["listenbrainz_token", "ListenBrainz token"],
            message = DesktopStrings["d_found_on_your_listenbrainz_profile_under_settings", "Found on your ListenBrainz profile, under Settings."],
        )
        DialogField(
            value = token,
            onValueChange = { token = it },
            placeholder = DesktopStrings["api_token", "API token"],
            isPassword = true,
            onSubmit = ::save,
            modifier = Modifier.focusRequester(focus),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp)),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = { DesktopExternalLinks.open("https://listenbrainz.org/settings/") }) {
                Text(DesktopStrings["d_open_listenbrainz", "Open ListenBrainz"], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        DialogActions(
            confirm = DesktopStrings["save", "Save"],
            confirmEnabled = true,
            busy = false,
            onConfirm = ::save,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun DialogHeading(title: String, message: String, isError: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 18.dp),
        horizontalAlignment = if (LocalDesktopPanelIsPage.current) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = DesktopInk,
        )
        Text(
            message,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) DesktopDestructive else DesktopSecondary,
            textAlign = if (LocalDesktopPanelIsPage.current) TextAlign.Start else TextAlign.Center,
        )
    }
}

/**
 * Paste a Spotify playlist link, match its songs on YouTube Music, keep the result: the phone's
 * `SpotifyImportAlert`. [onImported] makes the playlist and returns a note when it ended up
 * somewhere the listener would not expect. Songs with no match are listed afterwards rather than
 * dropped without a word. The link has to be public; no Spotify sign-in is involved.
 */
@Composable
internal fun DesktopSpotifyImportDialog(
    signedIn: Boolean,
    onImported: suspend (title: String, songs: List<Song>) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var unmatched by remember { mutableStateOf<List<SpotifyImportTrack>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val title = DesktopStrings["spotify_import_title", "Import Spotify playlist"]

    fun start() {
        if (working) return
        val id = SpotifyImporter.extractPlaylistId(link) ?: run {
            status = DesktopStrings["spotify_import_invalid", "That isn't a Spotify playlist link."]
            failed = true
            return
        }
        working = true
        failed = false
        status = DesktopStrings["spotify_import_fetching", "Reading the Spotify playlist…"]
        scope.launch {
            try {
                val (name, tracks) = SpotifyImporter.fetchPlaylistTracks(id)
                val (songs, missed) = SpotifyImporter.resolveToSongs(tracks) { done, total ->
                    status = DesktopStrings.format("spotify_import_matching", done, total, fallback = "Matching songs %1\$d of %2\$d")
                }
                if (songs.isEmpty()) {
                    error(DesktopStrings["spotify_import_none", "None of those songs were found on YouTube Music."])
                }
                val note = onImported(name, songs)
                if (missed.isEmpty() && note == null) {
                    onDismiss()
                } else {
                    status = note
                    unmatched = missed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                DesktopTrackLog.log("spotify import failed: ${failure.message}")
                status = DesktopStrings.format(
                    "spotify_import_failed",
                    failure.message.orEmpty(),
                    fallback = "Couldn't import the playlist: %1\$s",
                )
                failed = true
            }
            working = false
        }
    }

    // Not dismissed mid-import, which would leave the matching running with nowhere to report.
    DesktopDialogPanel(onDismiss = { if (!working) onDismiss() }, maxWidth = 420, popup = true) {
        val missed = unmatched
        if (missed == null) {
            DialogHeading(
                title = title,
                message = if (signedIn) {
                    DesktopStrings["spotify_import_description", "Paste a public playlist link. Songs are matched on YouTube Music."]
                } else {
                    DesktopStrings[
                        "spotify_import_description_local",
                        "Paste a public playlist link. Songs are matched on YouTube Music and saved on this device.",
                    ]
                },
            )
            DialogField(
                value = link,
                onValueChange = {
                    link = it
                    if (failed) status = null
                    failed = false
                },
                placeholder = DesktopStrings["spotify_import_hint", "open.spotify.com/playlist/…"],
                enabled = !working,
                onSubmit = ::start,
                modifier = Modifier.focusRequester(focus),
            )
            status?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) DesktopDestructive else DesktopSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            DialogActions(
                confirm = DesktopStrings["spotify_import_button", "Import"],
                confirmEnabled = link.isNotBlank() && !working,
                busy = working,
                onConfirm = ::start,
                onDismiss = onDismiss,
            )
        } else {
            DialogHeading(
                title = title,
                message = listOfNotNull(
                    status,
                    DesktopStrings.format(
                        "spotify_import_unmatched",
                        missed.size,
                        fallback = "%1\$d songs weren't found on YouTube Music",
                    ).takeIf { missed.isNotEmpty() },
                ).joinToString("\n"),
            )
            if (missed.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = panelInset(22.dp))
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DesktopInk.copy(alpha = 0.06f))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    missed.forEach { track ->
                        Text(
                            if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = panelInset(16.dp), vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = DesktopAccent) }
            }
        }
    }
}

/** The frosted glass text field used across dialogs and panels. */
@Composable
private fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
) {
    DesktopGlassTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = Modifier.padding(horizontal = panelInset(22.dp)).then(modifier),
        enabled = enabled,
        isPassword = isPassword,
        imeAction = imeAction,
        onSubmit = onSubmit,
    )
}

@Composable
private fun DialogActions(
    confirm: String,
    confirmEnabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = panelInset(16.dp), vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = DesktopAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
        }
        DesktopGlassPillButton(
            text = DesktopStrings["cancel", "Cancel"],
            enabled = !busy,
            onClick = onDismiss,
        )
        DesktopGlassPillButton(
            text = confirm,
            accent = true,
            enabled = confirmEnabled,
            onClick = onConfirm,
        )
    }
}


/**
 * Discord's user token.
 *
 * Verified before it is kept — Android's alert does the same, because a token
 * that was mistyped and one that has expired look identical until Discord is
 * asked about it.
 */
@Composable
internal fun DesktopDiscordTokenDialog(onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun save() {
        if (busy || token.isBlank()) return
        busy = true
        error = null
        scope.launch {
            DesktopDiscordRpc.signIn(token.trim())
                .onSuccess { onDismiss() }
                .onFailure { error = "Discord would not accept that token" }
            busy = false
        }
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 400) {
        DialogHeading(
            title = DesktopStrings["discord_token", "Discord token"],
            message = error
                ?: "Your account's own token, since Discord has no other way to let " +
                "an app set your presence.",
            isError = error != null,
        )
        DialogField(
            value = token,
            onValueChange = { token = it },
            placeholder = DesktopStrings["token", "Token"],
            enabled = !busy,
            isPassword = true,
            onSubmit = ::save,
            modifier = Modifier.focusRequester(focus),
        )
        DialogActions(
            confirm = if (busy) "Checking…" else "Save",
            confirmEnabled = !busy && token.isNotBlank(),
            busy = busy,
            onConfirm = ::save,
            onDismiss = onDismiss,
        )
    }
}


/**
 * One free-text value — an activity name, a button label.
 *
 * [message] is where the caller explains the field, including which `{...}`
 * variables it accepts, since that is the only place anyone would find out.
 */
@Composable
internal fun DesktopTextValueDialog(
    title: String,
    message: String,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 400, popup = true) {
        DialogHeading(title = title, message = message)
        DialogField(
            value = value,
            onValueChange = { value = it },
            placeholder = placeholder,
            onSubmit = { onSave(value.trim()) },
            modifier = Modifier.focusRequester(focus),
        )
        DialogActions(
            confirm = DesktopStrings["save", "Save"],
            confirmEnabled = true,
            busy = false,
            onConfirm = { onSave(value.trim()) },
            onDismiss = onDismiss,
        )
    }
}
