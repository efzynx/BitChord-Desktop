package com.music.bitchord.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The account button in the toolbar: the listener's photo, or a person glyph when there is nobody
 * signed in.
 */
@Composable
internal fun DesktopAccountButton(
    avatar: String?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        // Rests at the toolbar buttons' hover wash and brightens a step when pointed at.
        Modifier.size(36.dp).clip(CircleShape)
            .desktopHoverWash(restAlpha = 0.12f, hoverAlpha = 0.2f)
            .desktopGlassPress(CircleShape, interactionSource = interactionSource, pressScale = 0.93f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            DesktopArtwork(
                avatar,
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(1.dp, DesktopCardEdge, CircleShape),
                contentScale = ContentScale.Crop,
                px = 96,
            )
        } else {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(DesktopInk.copy(alpha = 0.14f))
                    .border(1.dp, DesktopCardEdge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    DesktopStrings["accounts", "Accounts"],
                    tint = DesktopInk.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * The account switcher: every login this app holds, the channels under each, and the actions that
 * change the set.
 */
@Composable
internal fun DesktopAccountSelector(
    accounts: List<DesktopGoogleAccount>,
    activeAccountId: String?,
    activeProfileId: String?,
    busy: Boolean,
    onSelect: (DesktopGoogleAccount, DesktopYouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (DesktopGoogleAccount) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var managing by remember { mutableStateOf(false) }
    var accountToSignOut by remember { mutableStateOf<DesktopGoogleAccount?>(null) }

    Box(Modifier.fillMaxSize()) {
        // The scrim is its own layer rather than a modifier on the container.
        Box(
            Modifier
                .fillMaxSize()
                .background(DesktopScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 68.dp)
                .width(340.dp)
                .shadow(20.dp, shape)
                .clip(shape)
                // Real-time refracting Liquid Glass (or frosted fallback) sampling the sidebar
                // and page content underneath.
                .desktopBarGlass(shape, tintAlpha = 0.44f, borderAlpha = 0.15f),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (accounts.isEmpty()) DesktopStrings["accounts", "Accounts"] else DesktopStrings["switch_account", "Switch account"],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                )
                Spacer(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (accounts.isEmpty()) {
                    item {
                        Text(
                            DesktopStrings[
                                "d_nobody_is_signed_in",
                                "Nobody is signed in. Your library, playlists and history need a " +
                                    "YouTube Music account.",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
                accounts.forEach { account ->
                    item(key = "header-${account.accountId}") {
                        Text(
                            account.email.ifBlank { account.name.ifBlank { "Account" } },
                            style = MaterialTheme.typography.labelMedium,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(account.profiles.size, key = { "p-${account.accountId}-$it" }) { index ->
                        val profile = account.profiles[index]
                        DesktopProfileRow(
                            profile = profile,
                            selected = account.accountId == activeAccountId &&
                                profile.profileId == activeProfileId,
                            managing = managing,
                            onClick = { onSelect(account, profile); onDismiss() },
                            onRequestSignOut = { accountToSignOut = account },
                        )
                    }
                }
                item { DesktopSelectorAction(BitChordIcons.Plus, DesktopStrings["add_account", "Add account"], onAddAccount) }
                if (accounts.isNotEmpty()) {
                    item {
                        DesktopSelectorAction(
                            icon = Icons.Rounded.ManageAccounts,
                            label = if (managing) DesktopStrings["d_done_managing", "Done managing"] else DesktopStrings["manage_accounts", "Manage accounts"],
                        ) {
                            managing = !managing
                        }
                    }
                }
                item { DesktopSelectorAction(Icons.Rounded.Settings, DesktopStrings["settings", "Settings"], onOpenSettings) }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }

        accountToSignOut?.let { account ->
            DesktopConfirmSignOutDialog(
                account = account,
                onConfirm = {
                    accountToSignOut = null
                    onRemoveAccount(account)
                },
                onDismiss = {
                    accountToSignOut = null
                },
            )
        }
    }
}

@Composable
internal fun DesktopConfirmSignOutDialog(
    account: DesktopGoogleAccount,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(20.dp, shape)
                .clip(shape)
                .desktopBarGlass(
                    shape = shape,
                    surfaceTint = desktopModalSurfaceTint(DesktopIsDark),
                    borderAlpha = if (DesktopIsDark) 0.22f else 0.30f,
                )
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DesktopDestructive.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = DesktopDestructive,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = DesktopStrings["d_sign_out_confirm_title", "Sign out of account?"],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                )
            }

            Spacer(Modifier.height(14.dp))

            val name = account.name.ifBlank { account.email.ifBlank { "Account" } }
            Text(
                text = DesktopStrings.format("d_sign_out_confirm_message", name, "Are you sure you want to sign out of \"%s\"? This account will be removed from BitChord Desktop."),
                style = MaterialTheme.typography.bodyMedium,
                color = DesktopSecondary,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(DesktopStrings["cancel", "Cancel"], color = DesktopSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopDestructive),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(DesktopStrings["sign_out", "Sign out"], color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DesktopProfileRow(
    profile: DesktopYouTubeProfile,
    selected: Boolean,
    managing: Boolean,
    onClick: () -> Unit,
    onRequestSignOut: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile.avatar != null) {
            DesktopArtwork(
                profile.avatar,
                Modifier.size(38.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                px = 128,
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                null,
                tint = DesktopSecondary,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, color = DesktopInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                profile.handle.ifBlank { if (profile.isBrandAccount) "Brand account" else "Personal" },
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (managing) {
            IconButton(
                onClick = onRequestSignOut,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Keluar dari akun",
                    tint = DesktopDestructive,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (selected) {
            Icon(BitChordIcons.Check, DesktopStrings["selected", "Selected"], tint = DesktopAccent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DesktopSelectorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopInk.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = DesktopInk)
    }
}

/** What [DesktopSignInDialog]'s `busy` holds while the in-app sign-in window is open. */
internal const val WEB_SIGN_IN = "Google"

/** Signing in: Google's own page in a BitChord window, a browser to take the session from, or the cookie by hand. */
@Composable
internal fun DesktopSignInDialog(
    /** Whether the in-app sign-in window can be shown; see [DesktopWebSignIn]. */
    webSignIn: Boolean,
    onWebSignIn: () -> Unit,
    onImport: (DesktopBrowserCookies.Profile) -> Unit,
    onPaste: (String) -> Unit,
    busy: String?,
    error: String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<DesktopBrowserCookies.Profile>?>(null) }
    var refreshingProfiles by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    fun loadProfiles() {
        scope.launch {
            refreshingProfiles = true
            profiles = withContext(Dispatchers.IO) {
                DesktopBrowserCookies.profiles()
            }
            refreshingProfiles = false
        }
    }

    LaunchedEffect(Unit) {
        loadProfiles()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                // Swallows clicks without dismissing: leaving now would cancel a sign-in that is
                // still open, so only the close button does that.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .shadow(20.dp, shape)
                .clip(shape)
                .desktopBarGlass(
                    shape = shape,
                    surfaceTint = desktopModalSurfaceTint(DesktopIsDark),
                    borderAlpha = if (DesktopIsDark) 0.22f else 0.30f,
                )
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    DesktopStrings["sign_in_youtube_music", "Sign in to YouTube Music"],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopInk,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, DesktopStrings["close", "Close"], tint = DesktopSecondary)
                }
            }
            Text(
                DesktopStrings["d_open_youtube_music_browser_desc", "Make sure you are signed in on your browser, then click your browser profile below to connect."],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 8.dp),
            )
            error?.let {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            when {
                pasting -> {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DesktopInk.copy(alpha = 0.05f))
                            .border(1.dp, DesktopCardEdge, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = DesktopStrings["d_copy_cookie_guide_title", "How to copy session cookie:"],
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopInk,
                        )
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            DesktopStrings["d_copy_cookie_step_1", "1. Open music.youtube.com in your browser and sign in."],
                            DesktopStrings["d_copy_cookie_step_2", "2. Press F12 (Developer Tools) and select the \"Network\" tab."],
                            DesktopStrings["d_copy_cookie_step_3", "3. Refresh the page (F5) and click any request (e.g. \"browse\")."],
                            DesktopStrings["d_copy_cookie_step_4", "4. Under \"Headers\" > \"Request Headers\", find \"cookie:\", right-click and choose \"Copy value\"."],
                            DesktopStrings["d_copy_cookie_step_5", "5. Paste the cookie header into the box below and click Sign in."],
                        ).forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        DesktopStrings["d_paste_the_cookie_header_for_music_youtube_com", "Paste the Cookie header for music.youtube.com."],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    BasicTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = DesktopInk),
                        cursorBrush = SolidColor(DesktopAccent),
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 130.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DesktopInk.copy(alpha = 0.08f))
                            .padding(12.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { pasting = false }) { Text(DesktopStrings["back", "Back"], color = DesktopSecondary) }
                        TextButton(
                            onClick = { onPaste(pasted.trim()) },
                            enabled = pasted.isNotBlank() && busy == null,
                        ) {
                            Text(DesktopStrings["sign_in", "Sign in"], color = DesktopAccent)
                        }
                    }
                }
                profiles == null -> Row(
                    Modifier.fillMaxWidth().padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(DesktopStrings["d_looking_for_browsers", "Looking for browsers…"], color = DesktopSecondary)
                }
                else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    val found = profiles.orEmpty()

                    // Kartu Buka di Browser Default
                    item(key = "open-browser-card") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { DesktopExternalLinks.open("https://music.youtube.com") },
                            shape = RoundedCornerShape(12.dp),
                            color = DesktopAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, DesktopAccent.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DesktopAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = DesktopAccent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = DesktopStrings["d_open_youtube_music_browser", "Open YouTube Music in Default Browser"],
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DesktopInk,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = DesktopStrings["d_open_youtube_music_browser_desc", "Make sure you are signed in on your browser, then click your browser profile below to connect."],
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = DesktopSecondary,
                                    )
                                }
                            }
                        }
                    }

                    // Header Profil Browser
                    item(key = "browser-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = DesktopStrings["d_detected_browsers", "Detected Browsers (Auto-import session)"],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopSecondary,
                            )
                            if (refreshingProfiles) {
                                CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(14.dp))
                            } else {
                                IconButton(
                                    onClick = { loadProfiles() },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = DesktopStrings["refresh", "Refresh"],
                                        tint = DesktopSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (found.isEmpty()) {
                        item {
                            Text(
                                DesktopStrings["d_no_browser_profile_was_found_on_this_machine", "No browser profile was found on this machine."],
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(found.size, key = { "profile-${found[it].database}" }) { index ->
                            val profile = found[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .clickable(enabled = busy == null) { onImport(profile) }
                                    .padding(horizontal = 22.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(DesktopInk.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Language,
                                        null,
                                        tint = DesktopAccent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(profile.label, color = DesktopInk, fontWeight = FontWeight.Medium)
                                    Text(
                                        DesktopStrings.format("d_use_session_from", profile.browser, "Use session from %s without password"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = DesktopSecondary,
                                    )
                                }
                                if (busy == profile.label) {
                                    CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Opsi Lainnya (In-App WebView & Paste Cookie)
                    item(key = "other-options-header") {
                        Text(
                            text = DesktopStrings["d_other_options", "Other Options"],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }

                    if (webSignIn) {
                        item(key = "web-sign-in") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable(enabled = busy == null) { onWebSignIn() }
                                    .padding(horizontal = 22.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    null,
                                    tint = DesktopInk.copy(alpha = 0.75f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(DesktopStrings["d_web_sign_in_title", "Sign in via App Window (WebView)"], color = DesktopInk)
                                    Text(
                                        if (busy == WEB_SIGN_IN) {
                                            DesktopStrings["d_web_sign_in_busy", "Finish in the sign-in window, then click \"Use this profile\""]
                                        } else {
                                            DesktopStrings["d_web_sign_in_desc", "Opens Google sign-in in a separate BitChord window"]
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = DesktopSecondary,
                                    )
                                }
                                if (busy == WEB_SIGN_IN) {
                                    CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    item(key = "paste-cookie") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { pasting = true }
                                .padding(horizontal = 22.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                null,
                                tint = DesktopInk.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                DesktopStrings["d_paste_cookie_title", "Paste session cookie manually"],
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
