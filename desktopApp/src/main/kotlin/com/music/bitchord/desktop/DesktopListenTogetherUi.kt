package com.music.bitchord.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.listentogether.JamInvite
import com.music.bitchord.data.listentogether.PartyActivity
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyPreview
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Desktop counterpart of Android's complete Listen Together page. */
@Composable
internal fun DesktopListenTogetherDialog(autoplayEnabled: Boolean, onDismiss: () -> Unit) {
    val state by DesktopListenTogether.state.collectAsState()
    val activity by DesktopListenTogether.activity.collectAsState()
    val partyMode by DesktopListenTogether.partyMode.collectAsState()
    val customSubMode by DesktopListenTogether.customSubMode.collectAsState()
    val savedServers by DesktopListenTogether.savedServers.collectAsState()
    val server by DesktopListenTogether.customServerUrl.collectAsState()
    val builtinStatus by DesktopListenTogether.builtinStatus.collectAsState()
    val customStatus by DesktopListenTogether.customStatus.collectAsState()
    val localStatus by DesktopListenTogether.localStatus.collectAsState()
    val serverStatus by DesktopListenTogether.serverStatus.collectAsState()
    val tunnelState by DesktopCloudflareTunnel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(DesktopListenTogether.nickname()) }
    var maxMembers by remember { mutableStateOf(5) }
    var serverDraft by remember { mutableStateOf(server) }
    var preview by remember { mutableStateOf<PartyPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server) {
        if (serverDraft == server || serverDraft.isBlank()) serverDraft = server
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 920) {
        Column(desktopPanelBody(cardMax = 760.dp, cardMin = 560.dp, fill = true)) {
            Row(
                Modifier.fillMaxWidth().padding(start = panelInset(28.dp), end = panelInset(18.dp), top = 22.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DesktopStrings["listen_together", "Listen together"],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.inParty) "Everyone hears the same song at the same moment"
                        else "Start a room or join friends with a six-character code",
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // A page is left by the window's back, as every other page is.
                if (!LocalDesktopPanelIsPage.current) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                }
            }
            if (!LocalDesktopPanelIsPage.current) HorizontalDivider(color = DesktopCardEdge)

            when {
                state.inParty -> PartyRoom(
                    state = state,
                    activity = activity,
                    busy = busy,
                    onLeave = {
                        busy = true
                        scope.launch {
                            DesktopListenTogether.leaveParty()
                            busy = false
                        }
                    },
                )
                preview != null -> JoinConfirmation(
                    preview = preview!!,
                    nickname = nickname,
                    onNicknameChange = {
                        nickname = it.take(80)
                        DesktopListenTogether.setNickname(nickname)
                    },
                    busy = busy,
                    error = localError ?: state.error,
                    onBack = { preview = null; localError = null },
                    onJoin = {
                        busy = true
                        localError = null
                        scope.launch {
                            DesktopListenTogether.joinParty(preview!!.code, nickname)
                                .onFailure { localError = it.message }
                            busy = false
                        }
                    },
                )
                else -> PartyLanding(
                    partyMode = partyMode,
                    onPartyModeChange = { DesktopListenTogether.setPartyMode(it) },
                    customSubMode = customSubMode,
                    onCustomSubModeChange = { DesktopListenTogether.setCustomSubMode(it) },
                    hasBuiltinServer = DesktopListenTogether.hasBuiltinServer,
                    builtinServerUrl = DesktopListenTogether.builtinServerUrl,
                    builtinStatus = builtinStatus,
                    customStatus = customStatus,
                    localStatus = localStatus,
                    savedServers = savedServers,
                    onRemoveSavedServer = { DesktopListenTogether.removeSavedServer(it) },
                    onSelectSavedServer = { chosen ->
                        localError = null
                        DesktopListenTogether.selectSavedServer(chosen)
                            .onSuccess { serverDraft = chosen }
                            .onFailure { localError = it.message }
                    },
                    tunnelState = tunnelState,
                    signedIn = DesktopListenTogether.canJoin(),
                    avatarUrl = DesktopListenTogether.myAvatarUrl(),
                    nickname = nickname,
                    onNicknameChange = {
                        nickname = it.take(80)
                        DesktopListenTogether.setNickname(nickname)
                    },
                    maxMembers = maxMembers,
                    onMaxMembersChange = { maxMembers = it.coerceIn(2, 10) },
                    code = code,
                    onCodeChange = {
                        code = JamInvite.parse(it)
                            ?: it.filter(Char::isLetterOrDigit).uppercase().take(DesktopListenTogether.CODE_LENGTH)
                        localError = null
                    },
                    server = serverDraft,
                    serverStatus = serverStatus,
                    onServerChange = { serverDraft = it },
                    onSaveServer = {
                        localError = null
                        if (serverDraft.isNotBlank()) {
                            DesktopListenTogether.addSavedServer(serverDraft)
                                .onSuccess { serverDraft = serverDraft.trim() }
                                .onFailure { localError = it.message }
                        }
                    },
                    onRefreshServer = DesktopListenTogether::refreshServerHealth,
                    busy = busy,
                    error = localError ?: state.error,
                    onCreate = {
                        busy = true
                        localError = null
                        scope.launch {
                            DesktopListenTogether.createParty(nickname, maxMembers, autoplayEnabled)
                                .onFailure { localError = it.message }
                            busy = false
                        }
                    },
                    onPreview = {
                        busy = true
                        localError = null
                        scope.launch {
                            DesktopListenTogether.previewParty(code)
                                .onSuccess { preview = it }
                                .onFailure { localError = it.message }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PartyLanding(
    partyMode: DesktopListenTogether.PartyMode,
    onPartyModeChange: (DesktopListenTogether.PartyMode) -> Unit,
    customSubMode: DesktopListenTogether.CustomSubMode,
    onCustomSubModeChange: (DesktopListenTogether.CustomSubMode) -> Unit,
    hasBuiltinServer: Boolean,
    builtinServerUrl: String,
    builtinStatus: DesktopListenTogether.ServerStatus,
    customStatus: DesktopListenTogether.ServerStatus,
    localStatus: DesktopListenTogether.ServerStatus,
    savedServers: List<String>,
    onRemoveSavedServer: (String) -> Unit,
    onSelectSavedServer: (String) -> Unit,
    tunnelState: DesktopCloudflareTunnel.State,
    signedIn: Boolean,
    avatarUrl: String?,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    maxMembers: Int,
    onMaxMembersChange: (Int) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    server: String,
    serverStatus: DesktopListenTogether.ServerStatus,
    onServerChange: (String) -> Unit,
    onSaveServer: () -> Unit,
    onRefreshServer: () -> Unit,
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onPreview: () -> Unit,
) {
    val isServerReady = when (partyMode) {
        DesktopListenTogether.PartyMode.QUICK -> hasBuiltinServer && builtinStatus.health == DesktopListenTogether.Health.ONLINE
        DesktopListenTogether.PartyMode.CUSTOM -> when (customSubMode) {
            DesktopListenTogether.CustomSubMode.CLOUD -> server.isNotBlank() && customStatus.health == DesktopListenTogether.Health.ONLINE
            DesktopListenTogether.CustomSubMode.LOCAL -> tunnelState.status == DesktopCloudflareTunnel.Status.RUNNING
        }
    }
    val canAct = signedIn && isServerReady

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Two Main Mode Cards (Quick Party vs Custom Party)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // QUICK PARTY CARD
                val isQuick = partyMode == DesktopListenTogether.PartyMode.QUICK
                val quickBorder = if (isQuick) BorderStroke(1.5.dp, DesktopAccent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                val quickBg = if (isQuick) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(quickBg)
                        .border(quickBorder, RoundedCornerShape(16.dp))
                        .clickable(enabled = hasBuiltinServer) {
                            onPartyModeChange(DesktopListenTogether.PartyMode.QUICK)
                        }
                        .padding(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isQuick) DesktopAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = if (isQuick) DesktopAccent else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    DesktopStrings["d_party_mode_quick", "Quick Party"],
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    DesktopStrings["d_party_mode_quick_subtitle", "Built-in Cloud Server"],
                                    color = DesktopSecondary,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                        if (isQuick) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(DesktopAccent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        DesktopStrings["d_party_mode_quick_desc", "Host or join instantly using BitChord's included party server."],
                        color = DesktopSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )

                    Spacer(Modifier.height(12.dp))
                    if (!hasBuiltinServer) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                DesktopStrings["d_party_mode_quick_unavailable", "Not included in this build"],
                                color = DesktopSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (builtinStatus.health) {
                                DesktopListenTogether.Health.ONLINE -> Color(0xFF28C840)
                                DesktopListenTogether.Health.CHECKING -> Color(0xFFFFB300)
                                DesktopListenTogether.Health.OFFLINE -> DesktopDestructive
                            }
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Spacer(Modifier.width(6.dp))
                            val statusText = when (builtinStatus.health) {
                                DesktopListenTogether.Health.ONLINE -> "${DesktopStrings["d_tunnel_online", "Online"]} · ${builtinStatus.latencyMs} ms"
                                DesktopListenTogether.Health.CHECKING -> DesktopStrings["d_tunnel_starting", "Checking…"]
                                DesktopListenTogether.Health.OFFLINE -> DesktopStrings["d_party_offline", "Party server is offline"]
                            }
                            Text(
                                statusText,
                                color = dotColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // CUSTOM PARTY CARD
                val isCustom = partyMode == DesktopListenTogether.PartyMode.CUSTOM
                val customBorder = if (isCustom) BorderStroke(1.5.dp, DesktopAccent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                val customBg = if (isCustom) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(customBg)
                        .border(customBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            onPartyModeChange(DesktopListenTogether.PartyMode.CUSTOM)
                        }
                        .padding(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCustom) DesktopAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = null,
                                    tint = if (isCustom) DesktopAccent else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    DesktopStrings["d_party_mode_custom", "Custom Party"],
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    DesktopStrings["d_party_mode_custom_subtitle", "Cloud or Local Tunnel"],
                                    color = DesktopSecondary,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                        if (isCustom) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(DesktopAccent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        DesktopStrings["d_party_mode_custom_desc", "Connect to your personal VPS or host locally via Cloudflare Quick Tunnel."],
                        color = DesktopSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val subModeLabel = if (customSubMode == DesktopListenTogether.CustomSubMode.CLOUD) {
                            DesktopStrings["d_party_submode_cloud", "Cloud Server"]
                        } else {
                            DesktopStrings["d_party_submode_local", "Local Host (Tunnel)"]
                        }
                        Text(
                            subModeLabel,
                            color = DesktopSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // 2. Mode Configuration Panel
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .desktopCardInset(RoundedCornerShape(14.dp))
                    .padding(18.dp),
            ) {
                if (partyMode == DesktopListenTogether.PartyMode.QUICK) {
                    // Quick Party info view
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = DesktopAccent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                DesktopStrings["d_party_mode_quick", "Quick Party"],
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                            )
                            Text(
                                builtinServerUrl,
                                color = DesktopSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (builtinStatus.health) {
                                DesktopListenTogether.Health.ONLINE -> Color(0xFF28C840)
                                DesktopListenTogether.Health.CHECKING -> Color(0xFFFFB300)
                                DesktopListenTogether.Health.OFFLINE -> DesktopDestructive
                            }
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Spacer(Modifier.width(6.dp))
                            val txt = when (builtinStatus.health) {
                                DesktopListenTogether.Health.ONLINE -> "${DesktopStrings["d_tunnel_online", "Online"]} (${builtinStatus.latencyMs} ms)"
                                DesktopListenTogether.Health.CHECKING -> DesktopStrings["d_tunnel_starting", "Checking…"]
                                DesktopListenTogether.Health.OFFLINE -> DesktopStrings["d_party_offline", "Party server is offline"]
                            }
                            Text(
                                txt,
                                color = dotColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = onRefreshServer,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(13.dp), tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text(DesktopStrings["test", "Test"], fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                } else {
                    // Custom Party view with Sub-mode tabs (Cloud vs Local)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(4.dp),
                    ) {
                        val isCloud = customSubMode == DesktopListenTogether.CustomSubMode.CLOUD
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCloud) DesktopAccent else Color.Transparent)
                                .clickable { onCustomSubModeChange(DesktopListenTogether.CustomSubMode.CLOUD) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    tint = if (isCloud) Color.Black else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    DesktopStrings["d_party_submode_cloud", "Cloud Server"],
                                    color = if (isCloud) Color.Black else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isCloud) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }

                        val isLocal = customSubMode == DesktopListenTogether.CustomSubMode.LOCAL
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isLocal) DesktopAccent else Color.Transparent)
                                .clickable { onCustomSubModeChange(DesktopListenTogether.CustomSubMode.LOCAL) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Computer,
                                    contentDescription = null,
                                    tint = if (isLocal) Color.Black else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    DesktopStrings["d_party_submode_local", "Local Host (Tunnel)"],
                                    color = if (isLocal) Color.Black else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isLocal) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (customSubMode == DesktopListenTogether.CustomSubMode.CLOUD) {
                        // CLOUD SUB-MODE: Server URL input + Test & Save + Saved Servers list
                        Text(
                            DesktopStrings["d_party_server", "Party server"],
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        DesktopSearchField(
                            query = server,
                            onQueryChange = onServerChange,
                            onSearch = {},
                            placeholder = "https://your-party-server.example.com",
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (customStatus.health) {
                                DesktopListenTogether.Health.ONLINE -> Color(0xFF28C840)
                                DesktopListenTogether.Health.CHECKING -> Color(0xFFFFB300)
                                DesktopListenTogether.Health.OFFLINE -> DesktopDestructive
                            }
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Spacer(Modifier.width(6.dp))
                            val statusLine = when (customStatus.health) {
                                DesktopListenTogether.Health.ONLINE -> "${DesktopStrings["d_tunnel_online", "Online"]} · ${customStatus.latencyMs} ms"
                                DesktopListenTogether.Health.CHECKING -> DesktopStrings["d_tunnel_starting", "Checking…"]
                                DesktopListenTogether.Health.OFFLINE -> DesktopStrings["d_party_offline", "Party server is offline"]
                            }
                            Text(
                                statusLine,
                                color = dotColor,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRefreshServer) {
                                Text(DesktopStrings["test", "Test"])
                            }
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = onSaveServer,
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopCardInsetFill),
                            ) {
                                Text(DesktopStrings["save", "Save"])
                            }
                        }

                        // Saved servers list
                        if (savedServers.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                DesktopStrings["d_party_saved_servers", "Saved Servers"],
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopSecondary,
                                fontSize = 11.5.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                savedServers.forEach { itemUrl ->
                                    val isCurrent = itemUrl == server
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCurrent) DesktopAccent.copy(alpha = 0.12f)
                                                else Color.White.copy(alpha = 0.04f),
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isCurrent) DesktopAccent.copy(alpha = 0.5f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .clickable { onSelectSavedServer(itemUrl) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isCurrent) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = DesktopStrings["d_party_active", "Active"],
                                                tint = DesktopAccent,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = itemUrl,
                                            color = if (isCurrent) DesktopAccent else Color.White,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { onRemoveSavedServer(itemUrl) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = DesktopSecondary,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // LOCAL SUB-MODE: Cloudflare Quick Tunnel section
                        CloudflareTunnelSection(
                            tunnelState = tunnelState,
                        )
                    }
                }
            }
        }

        // 3. Informational Notice if not ready or not signed in
        if (!signedIn) {
            item { Notice("Sign in to YouTube Music before creating or joining a party.", true) }
        } else if (!isServerReady) {
            item {
                val prompt = when (partyMode) {
                    DesktopListenTogether.PartyMode.QUICK -> "Built-in server is currently unreachable. Check your internet connection or use a Custom Party."
                    DesktopListenTogether.PartyMode.CUSTOM -> when (customSubMode) {
                        DesktopListenTogether.CustomSubMode.CLOUD -> DesktopStrings["d_party_enter_cloud_prompt", "Enter and save an active cloud server address first."]
                        DesktopListenTogether.CustomSubMode.LOCAL -> DesktopStrings["d_party_start_tunnel_prompt", "Start the local tunnel first to host on this computer."]
                    }
                }
                Notice(prompt, false)
            }
        }
        error?.takeIf(String::isNotBlank)?.let { item { Notice(it, true) } }

        // 4. Action Cards: Start a party & Join a party
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PartyChoiceCard(
                    title = "Start a party",
                    subtitle = "Choose how friends see you and how many can join.",
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PartyAvatar(avatarUrl, 44)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = onNicknameChange,
                            label = { Text("Nickname") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Party size", fontWeight = FontWeight.Medium)
                            Text("2–10 people", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Stepper("−", canAct && maxMembers > 2) { onMaxMembersChange(maxMembers - 1) }
                        Text("$maxMembers", modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                        Stepper("+", canAct && maxMembers < 10) { onMaxMembersChange(maxMembers + 1) }
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Start party", busy, canAct, onCreate)
                }

                PartyChoiceCard(
                    title = "Join a party",
                    subtitle = "Enter the code first. You’ll see who is inside before joining.",
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        label = { Text("Party code") },
                        placeholder = { Text("ABC123") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 5.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        label = "Look up party",
                        busy = busy,
                        enabled = canAct && code.length == DesktopListenTogether.CODE_LENGTH,
                        onClick = onPreview,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudflareTunnelSection(
    tunnelState: DesktopCloudflareTunnel.State,
) {
    val isAvailable by DesktopCloudflareTunnel.isAvailable.collectAsState()
    val isRunning = tunnelState.status == DesktopCloudflareTunnel.Status.RUNNING
    val isStarting = tunnelState.status == DesktopCloudflareTunnel.Status.STARTING

    var copiedInstallCmd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DesktopCloudflareTunnel.refreshAvailable()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DesktopStrings["d_tunnel_title", "Cloudflare Quick Tunnel"],
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isAvailable) {
                        if (isRunning) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF28C840)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                DesktopStrings["d_tunnel_online", "Online"],
                                color = Color(0xFF28C840),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        } else if (isStarting) {
                            CircularProgressIndicator(
                                color = DesktopAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                DesktopStrings["d_tunnel_starting", "Starting…"],
                                color = DesktopSecondary,
                                fontSize = 11.sp,
                            )
                        } else {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF28C840).copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    DesktopStrings["d_tunnel_ready", "Ready"],
                                    color = Color(0xFF28C840),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                DesktopStrings["d_tunnel_not_installed", "Not installed"],
                                color = Color(0xFFFFB300),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isAvailable) {
                        DesktopStrings["d_tunnel_desc_ready", "Host local party server and create a free public link for friends."]
                    } else {
                        DesktopStrings["d_tunnel_desc_missing", "Requires cloudflared CLI so local party server can be accessed over the internet."]
                    },
                    color = DesktopSecondary,
                    fontSize = 11.sp,
                )
            }

            if (isAvailable) {
                if (isRunning) {
                    TextButton(
                        onClick = { DesktopCloudflareTunnel.stop() },
                    ) {
                        Text(DesktopStrings["d_tunnel_stop", "Stop tunnel"], color = DesktopDestructive, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            DesktopCloudflareTunnel.start()
                        },
                        enabled = !isStarting,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopAccent,
                            contentColor = Color.Black,
                            disabledContainerColor = DesktopAccent.copy(alpha = 0.5f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        if (isStarting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    DesktopStrings["d_tunnel_connecting", "Connecting…"],
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            Text(
                                DesktopStrings["d_tunnel_start", "Start tunnel"],
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { DesktopCloudflareTunnel.refreshAvailable() },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = DesktopStrings["d_tunnel_recheck", "Check again"],
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(DesktopStrings["d_tunnel_recheck", "Check again"], fontSize = 11.5.sp, color = Color.White)
                }
            }
        }

        // Informational notice when cloudflared is not installed
        if (!isAvailable) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFB300).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            DesktopStrings["d_tunnel_notice_title", "Notice: Requires cloudflared CLI"],
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        DesktopStrings["d_tunnel_notice_body", "To allow your local party server to be accessed by friends over the internet, your system requires the 'cloudflared' utility. Once installed, click \"Check again\" above."],
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    val installCmd = if (DesktopPlatform.isWindows) {
                        "winget install --id Cloudflare.cloudflared"
                    } else {
                        "sudo apt install cloudflared"
                    }

                    Text(
                        text = if (DesktopPlatform.isWindows) {
                            DesktopStrings["d_tunnel_cmd_windows", "Installation command (Windows PowerShell / CMD):"]
                        } else {
                            DesktopStrings["d_tunnel_cmd_linux", "Installation command (Linux Terminal):"]
                        },
                        color = DesktopSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = installCmd,
                            color = DesktopAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                DesktopExternalLinks.copy(installCmd)
                                copiedInstallCmd = true
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = DesktopStrings["d_copy_command", "Copy command"],
                                tint = if (copiedInstallCmd) DesktopAccent else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    if (!DesktopPlatform.isWindows) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Arch / CachyOS: sudo pacman -S cloudflared",
                            color = DesktopSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    if (copiedInstallCmd) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            DesktopStrings["d_tunnel_copied", "Command copied to clipboard!"],
                            color = DesktopAccent,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }

        if (tunnelState.status == DesktopCloudflareTunnel.Status.ERROR) {
            Spacer(Modifier.height(8.dp))
            Text(
                tunnelState.error ?: "Tunnel error",
                color = DesktopDestructive,
                fontSize = 11.sp,
            )
        } else if (!tunnelState.notice.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                tunnelState.notice.orEmpty(),
                color = DesktopSecondary,
                fontSize = 11.sp,
            )
        }

        if (isRunning && !tunnelState.url.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tunnelState.url.orEmpty(),
                    color = DesktopAccent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { DesktopExternalLinks.copy(tunnelState.url.orEmpty()) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = DesktopStrings["d_copy_url", "Copy URL"],
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    DesktopStrings["d_tunnel_share_hint", "Share this link with friends so they can join your party"],
                    color = DesktopSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    DesktopStrings.format(
                        "d_party_local_active_desc",
                        tunnelState.port,
                        fallback = "Host is connected locally via 127.0.0.1:%1\$d (0 ms latency)",
                    ),
                    color = Color(0xFF28C840),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PartyChoiceCard(title: String, subtitle: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.desktopCardInset(RoundedCornerShape(16.dp)).padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun JoinConfirmation(
    preview: PartyPreview,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = panelInset(36.dp), vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Groups, null, tint = DesktopAccent, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (preview.hostName.isBlank()) "Join this party?" else "Join ${preview.hostName}’s party?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("${preview.memberCount} of ${preview.maxMembers} people · ${preview.code}", color = DesktopSecondary)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            preview.members.take(5).forEach { PartyAvatar(it.avatarUrl, 50, it.displayName) }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text("Nickname") },
            singleLine = true,
            modifier = Modifier.width(360.dp),
        )
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = DesktopDestructive) }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
            PrimaryButton("Join party", busy, !preview.isFull, onJoin, Modifier.width(190.dp))
        }
    }
}

@Composable
private fun PartyRoom(state: DesktopListenTogether.State, activity: List<PartyActivity>, busy: Boolean, onLeave: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = panelInset(24.dp), vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        LazyColumn(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { RoomHero(state) }
            item { NowPlayingCard(state) }
            item { QueueCard(state) }
        }
        LazyColumn(Modifier.weight(.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { MembersCard(state) }
            if (state.you?.isHost == true) item { HostControls(state) }
            if (activity.isNotEmpty()) item { ActivityCard(activity) }
            state.error?.let { item { Notice(it, true) } }
            item {
                TextButton(onClick = onLeave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave party", color = DesktopDestructive)
                }
            }
        }
    }
}

@Composable
private fun RoomHero(state: DesktopListenTogether.State) {
    val invite = DesktopListenTogether.inviteUrl(state.code.orEmpty())
    Row(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("PARTY CODE", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
            Text(state.code.orEmpty(), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text(connectionLine(state), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Scan from a phone or copy the invite link", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        }
        DesktopQrCode(invite)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = { DesktopExternalLinks.copy(invite) }) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Copy invite")
            }
        }
    }
}

@Composable
private fun NowPlayingCard(state: DesktopListenTogether.State) {
    val track = state.playback.track
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("NOW PLAYING", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        if (track == null) {
            Text("Nothing playing yet", color = DesktopSecondary)
            if (state.you?.isHost == true) Text("Play a song and it will appear for everyone.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopArtwork(track.thumbnailUrl, Modifier.size(62.dp).clip(RoundedCornerShape(8.dp)), px = 160)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = DesktopSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.playback.startedByName?.let { Text("Started by $it", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(state: DesktopListenTogether.State) {
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("UP NEXT", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        val upcoming = state.queue.items.drop((state.queue.index + 1).coerceAtLeast(0)).take(5)
        if (upcoming.isEmpty()) Text("The shared queue is empty", color = DesktopSecondary)
        upcoming.forEachIndexed { index, track ->
            if (index > 0 && !LocalDesktopPanelIsPage.current) HorizontalDivider(color = DesktopCardEdge)
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = DesktopSecondary, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MembersCard(state: DesktopListenTogether.State) {
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LISTENING", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("${state.members.size}/${state.maxMembers}", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        state.members.forEachIndexed { index, member ->
            if (index > 0 && !LocalDesktopPanelIsPage.current) HorizontalDivider(color = DesktopCardEdge)
            MemberRow(member, member.memberId == state.you?.memberId, state.you?.isHost == true && !member.isHost) {
                DesktopListenTogether.kick(member.memberId)
            }
        }
    }
}

@Composable
private fun HostControls(state: DesktopListenTogether.State) {
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("HOST CONTROLS", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.hostOnlyControl) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Only I can control playback")
                Text("Guests can still pause on their own device.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(state.hostOnlyControl, onCheckedChange = DesktopListenTogether::setHostOnlyControl)
        }
        if (LocalDesktopPanelIsPage.current) {
            Spacer(Modifier.height(20.dp))
        } else {
            HorizontalDivider(color = DesktopCardEdge, modifier = Modifier.padding(vertical = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Party size")
                Text("Never removes people already inside.", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Stepper("−", state.maxMembers > maxOf(2, state.members.size)) { DesktopListenTogether.setMaxMembers(state.maxMembers - 1) }
            Text("${state.maxMembers}", modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            Stepper("+", state.maxMembers < 10) { DesktopListenTogether.setMaxMembers(state.maxMembers + 1) }
        }
    }
}

@Composable
private fun ActivityCard(entries: List<PartyActivity>) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text("ACTIVITY", color = DesktopSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        entries.take(8).forEach { entry ->
            Text(
                "${clock.format(Date(entry.atMs))}  ${entry.by}: ${entry.detail.ifBlank { entry.action }}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun MemberRow(member: PartyMember, isYou: Boolean, canKick: Boolean, onKick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        PartyAvatar(member.avatarUrl, 34, member.displayName)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(member.displayName.ifBlank { "Someone" } + if (isYou) " · You" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when { member.isHost -> "Host"; !member.connected -> "Away"; else -> "Connected" },
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (canKick) IconButton(onClick = onKick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = DesktopDestructive, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PartyAvatar(url: String?, size: Int, fallback: String = "") {
    if (url != null) {
        DesktopArtwork(url, Modifier.size(size.dp).clip(CircleShape), px = size * 3)
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(DesktopCardInsetFill).border(0.5.dp, DesktopCardEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (fallback.isNotBlank()) Text(fallback.take(1).uppercase(), fontWeight = FontWeight.Bold)
            else Icon(Icons.Rounded.Person, null, tint = DesktopSecondary, modifier = Modifier.size((size * .55f).dp))
        }
    }
}

@Composable
private fun Stepper(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(DesktopCardInsetFill).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Color.White else DesktopSecondary) }
}

@Composable
private fun PrimaryButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = Color.Black),
        modifier = modifier.height(46.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
        else Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(text: String, destructive: Boolean) {
    Text(
        text,
        color = if (destructive) DesktopDestructive else DesktopSecondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().desktopCardInset(RoundedCornerShape(12.dp)).padding(14.dp),
    )
}

@Composable
private fun connectionLine(state: DesktopListenTogether.State): String = when {
    state.connection == DesktopListenTogether.Connection.CONNECTING -> "Reconnecting…"
    state.connection == DesktopListenTogether.Connection.OFFLINE -> "Offline"
    !state.clockSynced -> "Syncing clocks…"
    else -> "In sync · ${state.roundTripMs} ms"
}

private fun serverStatusLine(status: DesktopListenTogether.ServerStatus): String = when {
    status.health == DesktopListenTogether.Health.CHECKING -> DesktopStrings["d_party_checking", "Checking the party server…"]
    status.health == DesktopListenTogether.Health.OFFLINE -> DesktopStrings["d_party_offline", "Party server is offline"]
    status.isFallback -> DesktopStrings.format("d_party_custom_offline", status.latencyMs, fallback = "Custom server is offline · built-in server online · %d ms")
    else -> DesktopStrings.format("d_party_online", status.latencyMs, fallback = "Party server online · %d ms")
}
