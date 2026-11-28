package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.PillSegmentedControl
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Backup and Restore page: Replay hub & preferences, Google Drive backup
 * (built-in or BYO OAuth + automatic schedule), and local portable `.json` export/import.
 */
@Composable
internal fun DesktopBackupRestorePage(
    replaySummary: DesktopReplaySummary,
    replayGenres: Boolean,
    onReplayGenresChange: (Boolean) -> Unit,
    onOpenReplay: () -> Unit,
    onOpenDriveBackup: () -> Unit,
    onRestored: () -> Unit,
    onBack: () -> Unit,
) {
    val driveSettings by DesktopDriveBackup.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var pendingImportFile by remember { mutableStateOf<File?>(null) }
    var fileStatus by remember { mutableStateOf<String?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    DesktopSettingsPageFrame {
        DesktopSettingsSubPageHeader(
            title = DesktopStrings["d_backup_restore", "Backup and Restore"],
            subtitle = DesktopStrings["d_backup_restore_subtitle", "Save and restore your library, playlists, listening history, and settings"],
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // Section 1: Replay (matching mobile Settings → Your data)
            DesktopGroupedCard(
                header = DesktopStrings["replay", "Replay"],
                topSpacing = 4.dp,
            ) {
                DesktopSettingsRow(
                    icon = Icons.Rounded.BarChart,
                    title = DesktopStrings["replay", "Replay"],
                    subtitle = DesktopStrings["replay_subtitle", "Your top songs, artists, albums and genres"],
                    value = formatReplayDurationSummary(replaySummary),
                    onClick = onOpenReplay,
                )
                DesktopGroupedDivider()
                DesktopSettingsRow(
                    icon = Icons.Rounded.LocalOffer,
                    title = DesktopStrings["work_out_genres", "Work out genres"],
                    subtitle = if (replayGenres) {
                        DesktopStrings[
                            "replay_genres_enabled_subtitle",
                            "Asks Last.fm for an artist's genres. Only the artist name is sent.",
                        ]
                    } else {
                        DesktopStrings[
                            "replay_genres_disabled_subtitle",
                            "The Replay genre chart stays hidden while this is off",
                        ]
                    },
                    trailing = {
                        Switch(
                            checked = replayGenres,
                            onCheckedChange = onReplayGenresChange,
                            colors = desktopSwitchColors(),
                        )
                    },
                    onClick = { onReplayGenresChange(!replayGenres) },
                )
            }

            // Section 2: Google Drive Cloud Backup & Replay Sync
            DesktopGroupedCard(
                header = DesktopStrings["d_cloud_backup", "Cloud Backup"],
            ) {
                DesktopSettingsRow(
                    icon = Icons.Rounded.Cloud,
                    title = DesktopStrings["d_drive_backup_title", "Google Drive backup"],
                    subtitle = when {
                        driveSettings.connected && driveSettings.needsSignIn ->
                            DesktopStrings["d_drive_sign_in_again_subtitle", "Google Drive access was removed. Sign in again to keep backing up."]
                        driveSettings.connected && driveSettings.needsDeviceSetup ->
                            "${driveSettings.accountEmail} · ${DesktopStrings["d_drive_choose_device_badge", "Setup needed"]}"
                        driveSettings.connected && driveSettings.lastBackupAt > 0L ->
                            "${driveSettings.deviceName} · ${driveSettings.accountEmail} · ${formatTimestamp(driveSettings.lastBackupAt)}"
                        driveSettings.connected ->
                            "${driveSettings.deviceName} · ${driveSettings.accountEmail ?: DesktopStrings["signed_in", "Signed in"]}"
                        else ->
                            DesktopStrings["d_drive_backup_row_subtitle", "Manual and automatic backups saved in your Google Drive app folder"]
                    },
                    value = when {
                        !driveSettings.connected -> DesktopStrings["d_off", "Off"]
                        driveSettings.needsSignIn -> DesktopStrings["sign_in", "Sign in"]
                        driveSettings.needsDeviceSetup -> DesktopStrings["d_drive_choose_device_badge", "Setup needed"]
                        else -> driveFrequencyLabel(driveSettings.frequency)
                    },
                    onClick = onOpenDriveBackup,
                )
            }

            // Section 3: Local Portable JSON Backup
            DesktopGroupedCard(
                header = DesktopStrings["d_local_backup", "Local File Backup"],
                footer = DesktopStrings[
                    "d_backup_security_note",
                    "Account cookies, credentials, and linked service tokens are excluded from backups for your security.",
                ],
            ) {
                DesktopSettingsRow(
                    icon = Icons.Rounded.UploadFile,
                    title = DesktopStrings["d_export_backup", "Export backup to file"],
                    subtitle = DesktopStrings["d_export_backup_subtitle", "Save settings, playlists, liked songs, and Replay stats as a JSON file"],
                    onClick = {
                        fileError = null
                        val target = DesktopBackup.chooseExportFile() ?: return@DesktopSettingsRow
                        scope.launch {
                            DesktopBackup.exportToFile(target).fold(
                                onSuccess = { summary ->
                                    fileStatus = DesktopStrings.format(
                                        "d_export_backup_done",
                                        summary.playlists,
                                        summary.likedCount,
                                        target.name,
                                        fallback = "Exported %1\$d playlists and %2\$d liked tracks to %3\$s",
                                    )
                                },
                                onFailure = { err ->
                                    fileError = err.message ?: "Export failed"
                                },
                            )
                        }
                    },
                )
                DesktopGroupedDivider()
                DesktopSettingsRow(
                    icon = Icons.Rounded.Download,
                    title = DesktopStrings["d_import_backup", "Import backup from file"],
                    subtitle = DesktopStrings["d_import_backup_subtitle", "Restore playlists, listening stats, and settings from a saved JSON backup"],
                    onClick = {
                        fileError = null
                        val picked = DesktopBackup.chooseImportFile() ?: return@DesktopSettingsRow
                        pendingImportFile = picked
                    },
                )
            }

            fileStatus?.let { msg ->
                BackupNoticeBanner(text = msg, isError = false, modifier = Modifier.padding(top = 14.dp))
            }
            fileError?.let { msg ->
                BackupNoticeBanner(text = msg, isError = true, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }

    pendingImportFile?.let { file ->
        DesktopConfirmDialog(
            title = DesktopStrings["import_backup_title", "Import a backup?"],
            message = DesktopStrings[
                "import_backup_warning",
                "This replaces this device's settings and listening history with the contents of the file. Export the current data first if you want to keep it.",
            ],
            confirmLabel = DesktopStrings["d_drive_restore_action", "Restore"],
            destructive = true,
            onDismiss = { pendingImportFile = null },
            onConfirm = {
                pendingImportFile = null
                fileError = null
                scope.launch {
                    DesktopBackup.importFromFile(file).fold(
                        onSuccess = { summary ->
                            fileStatus = DesktopStrings.format(
                                "d_import_backup_done",
                                summary.playlists,
                                summary.months,
                                fallback = "Restored %1\$d playlists and %2\$d months of listening history",
                            )
                            onRestored()
                        },
                        onFailure = { err ->
                            fileError = err.message ?: "Import failed"
                        },
                    )
                }
            },
        )
    }
}

/**
 * Settings → Backup and Restore → Google Drive backup page.
 */
@Composable
internal fun DesktopGoogleDriveBackupPage(
    onRestored: () -> Unit,
    onReplaySynced: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by DesktopDriveBackup.settings.collectAsState()
    val progress by DesktopDriveBackup.progress.collectAsState()
    val backupsState by DesktopDriveBackup.backups.collectAsState()
    val lastResult by DesktopDriveBackup.result.collectAsState()
    val scope = rememberCoroutineScope()

    var showConnectDialog by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<DesktopDriveBackup.RemoteBackup?>(null) }
    var adoptSessionOnRestore by remember { mutableStateOf(false) }
    var pendingDeleteSnapshot by remember { mutableStateOf<DesktopDriveBackup.RemoteBackup?>(null) }
    var pendingDeleteDevice by remember { mutableStateOf<DesktopDriveBackup.RemoteDeviceGroup?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var confirmNewSession by remember { mutableStateOf(false) }
    val expandedDevices = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(settings.connected) {
        if (!settings.connected) return@LaunchedEffect
        val alreadyLoaded = DesktopDriveBackup.backups.value is DesktopDriveBackup.BackupsState.Loaded
        DesktopDriveBackup.refreshBackups(silent = alreadyLoaded, onReplaySynced = onReplaySynced)
        while (true) {
            kotlinx.coroutines.delay(15_000L)
            if (DesktopDriveBackup.progress.value !is DesktopDriveBackup.Progress.Running) {
                DesktopDriveBackup.refreshBackups(silent = true, onReplaySynced = onReplaySynced)
            }
        }
    }

    val isBusy = progress is DesktopDriveBackup.Progress.Running
    val footerCopy = DesktopStrings[
        "d_drive_backup_footer",
        "Backups are kept in a hidden app folder in your Google Drive and count toward your Drive storage. Passwords and sign-ins are never included. To delete it, open Drive -> Settings -> Manage apps.",
    ]

    DesktopSettingsPageFrame {
        DesktopSettingsSubPageHeader(
            title = DesktopStrings["d_drive_backup_title", "Google Drive backup"],
            subtitle = DesktopStrings["d_drive_backup_row_subtitle", "Manual and automatic backups saved in your Google Drive app folder"],
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // Connection & Manual Backup Card
            DesktopGroupedCard(
                header = DesktopStrings["d_drive_account", "Account"],
                footer = if (!settings.connected) footerCopy else null,
                topSpacing = 4.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (settings.connected) DesktopAccent.copy(alpha = 0.16f)
                                    else DesktopInk.copy(alpha = 0.08f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (settings.connected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = if (settings.connected) DesktopAccent else DesktopSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = settings.accountName ?: DesktopStrings["d_drive_connect", "Connect Google Drive"],
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = DesktopInk,
                            )
                            Text(
                                text = when {
                                    !settings.connected -> DesktopStrings["d_drive_not_connected_hint", "Sign in with Google to enable manual and automatic cloud backups"]
                                    settings.needsSignIn -> DesktopStrings["d_drive_sign_in_again_subtitle", "Google Drive access was removed. Sign in again to keep backing up."]
                                    else -> buildString {
                                        append(settings.accountEmail.orEmpty())
                                        if (settings.lastBackupAt > 0L) {
                                            append(" · ")
                                            append(formatTimestamp(settings.lastBackupAt))
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.needsSignIn) DesktopDestructive else DesktopSecondary,
                            )
                        }
                    }

                    if (progress is DesktopDriveBackup.Progress.Running) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                            color = DesktopAccent,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = (progress as DesktopDriveBackup.Progress.Running).step,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!settings.connected || settings.needsSignIn) {
                            Button(
                                onClick = { showConnectDialog = true },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopAccent,
                                    contentColor = DesktopOnAccent,
                                ),
                            ) {
                                Icon(Icons.Rounded.Cloud, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (settings.needsSignIn) DesktopStrings["sign_in", "Sign in"]
                                    else DesktopStrings["d_drive_connect", "Connect Google Drive"],
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else {
                            Button(
                                onClick = { DesktopDriveBackup.backupNowAsync(onSynced = onReplaySynced) },
                                enabled = !isBusy && !settings.needsDeviceSetup,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopAccent,
                                    contentColor = DesktopOnAccent,
                                ),
                            ) {
                                Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(DesktopStrings["d_backup_now", "Back up now"], fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { confirmDisconnect = true },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(DesktopStrings["disconnect", "Disconnect"], color = DesktopInk)
                            }
                        }
                    }
                }
            }

            if (settings.connected) {
                // This Computer Session Card
                DesktopGroupedCard(
                    header = DesktopStrings["d_drive_this_device_header", "This device"],
                ) {
                    if (settings.needsDeviceSetup) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = DesktopAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = DesktopStrings["d_drive_choose_device_title", "Set up this computer's session"],
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopInk,
                                )
                            }
                            Text(
                                text = DesktopStrings[
                                    "d_drive_choose_device_body",
                                    "Existing devices were found on this Google account. Start a new device session for this computer, or restore from another device below.",
                                ],
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        DesktopDriveBackup.chooseNewDeviceAsync(
                                            deviceName = settings.deviceName,
                                            syncImmediately = true,
                                            onSynced = onReplaySynced,
                                        )
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DesktopAccent,
                                        contentColor = DesktopOnAccent,
                                    ),
                                ) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        DesktopStrings["d_drive_new_device_action", "Back up as new device"],
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Computer,
                                contentDescription = null,
                                tint = DesktopInk.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = settings.deviceName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = DesktopInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = DesktopStrings[
                                        "d_drive_this_device_subtitle",
                                        "Each computer keeps its own backup session and history in Drive",
                                    ],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DesktopSecondary,
                                )
                            }
                            TextButton(
                                onClick = { showRenameDialog = true },
                                enabled = !isBusy,
                                colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
                            ) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(DesktopStrings["d_drive_rename", "Rename"])
                            }
                            TextButton(
                                onClick = { confirmNewSession = true },
                                enabled = !isBusy,
                                colors = ButtonDefaults.textButtonColors(contentColor = DesktopSecondary),
                            ) {
                                Text(DesktopStrings["d_drive_new_session_action", "New session"])
                            }
                        }
                    }
                }

                // Automatic Backup Schedule & Snapshot Retention
                DesktopGroupedCard(
                    header = DesktopStrings["d_auto_backup", "Automatic Backup"],
                ) {
                    val autoEnabled = settings.frequency != DesktopBackupFrequency.OFF
                    DesktopSettingsRow(
                        icon = Icons.Rounded.Schedule,
                        title = DesktopStrings["d_auto_backup", "Automatic Backup"],
                        subtitle = if (settings.hasRefreshToken) {
                            DesktopStrings["d_auto_backup_subtitle", "Periodically upload your library, Replay stats, and settings in the background"]
                        } else {
                            DesktopStrings["d_auto_backup_refresh_hint", "Sign in via OAuth PKCE with a refresh token for uninterrupted background backups"]
                        },
                        trailing = {
                            Switch(
                                checked = autoEnabled,
                                onCheckedChange = { on ->
                                    DesktopDriveBackup.setFrequency(
                                        if (on) DesktopBackupFrequency.DAILY else DesktopBackupFrequency.OFF,
                                    )
                                },
                                colors = desktopSwitchColors(),
                            )
                        },
                        onClick = {
                            DesktopDriveBackup.setFrequency(
                                if (autoEnabled) DesktopBackupFrequency.OFF else DesktopBackupFrequency.DAILY,
                            )
                        },
                    )
                    if (autoEnabled) {
                        DesktopGroupedDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = DesktopStrings["d_backup_frequency", "Backup frequency"],
                                style = MaterialTheme.typography.bodyLarge,
                                color = DesktopInk,
                            )
                            Spacer(Modifier.height(10.dp))
                            val choices = listOf(
                                DesktopBackupFrequency.DAILY,
                                DesktopBackupFrequency.WEEKLY,
                                DesktopBackupFrequency.MONTHLY,
                            )
                            PillSegmentedControl(
                                options = choices.map(::driveFrequencyLabel),
                                selectedIndex = choices.indexOf(settings.frequency).coerceAtLeast(0),
                                onSelect = { idx -> DesktopDriveBackup.setFrequency(choices[idx]) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(DesktopInk.copy(alpha = 0.06f)),
                            )
                        }
                    }
                    DesktopGroupedDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = DesktopStrings["d_drive_retention", "Keep backups per device"],
                            style = MaterialTheme.typography.bodyLarge,
                            color = DesktopInk,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = DesktopStrings[
                                "d_drive_retention_subtitle",
                                "Older backups on this device are kept up to this limit instead of being overwritten",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        val retentions = DesktopBackupRetention.entries
                        PillSegmentedControl(
                            options = retentions.map(::driveRetentionLabel),
                            selectedIndex = retentions.indexOf(settings.retention).coerceAtLeast(0),
                            onSelect = { idx -> DesktopDriveBackup.setRetention(retentions[idx]) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(DesktopInk.copy(alpha = 0.06f)),
                        )
                    }
                }

                // Multi-device & Multi-snapshot Restore Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = DesktopStrings["d_drive_devices_backups_header", "Devices & Backups"].uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopSecondary,
                    )
                    IconButton(
                        onClick = { DesktopDriveBackup.refreshBackupsAsync(onReplaySynced = onReplaySynced) },
                        enabled = !isBusy,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, "Refresh", tint = DesktopSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                DesktopGroupedCard(
                    topSpacing = 0.dp,
                    footer = footerCopy,
                ) {
                    when (val bs = backupsState) {
                        is DesktopDriveBackup.BackupsState.Loading, DesktopDriveBackup.BackupsState.Unknown -> {
                            Row(
                                Modifier.fillMaxWidth().padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DesktopAccent)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    DesktopStrings["d_drive_checking", "Checking Google Drive..."],
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        is DesktopDriveBackup.BackupsState.Failed -> {
                            Text(
                                bs.message,
                                color = DesktopDestructive,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                        is DesktopDriveBackup.BackupsState.Loaded -> {
                            if (bs.devices.isEmpty()) {
                                Text(
                                    DesktopStrings["d_drive_no_backups", "No backups found in Google Drive yet. Click Back up now to create your first cloud backup."],
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(18.dp),
                                )
                            } else {
                                bs.devices.forEachIndexed { idx, group ->
                                    if (idx > 0) DesktopGroupedDivider()
                                    val deviceKey = "${group.kind.name}:${group.deviceId}"
                                    val isThisComputer = DesktopDriveBackup.isCurrentDevice(group)
                                    val defaultExpanded = isThisComputer || settings.needsDeviceSetup || bs.devices.size <= 3
                                    val expanded = expandedDevices[deviceKey] ?: defaultExpanded
                                    DriveDeviceGroupSection(
                                        group = group,
                                        isThisComputer = isThisComputer,
                                        expanded = expanded,
                                        isBusy = isBusy,
                                        onToggleExpand = { expandedDevices[deviceKey] = !expanded },
                                        onRestoreSnapshot = { snap ->
                                            adoptSessionOnRestore =
                                                snap.deviceKind == RemoteDeviceKind.DESKTOP &&
                                                !isThisComputer &&
                                                settings.needsDeviceSetup
                                            pendingRestore = snap
                                        },
                                        onDeleteSnapshot = { snap -> pendingDeleteSnapshot = snap },
                                        onDeleteDevice = { pendingDeleteDevice = group },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (val res = lastResult) {
                is DesktopDriveBackup.ActionResult.BackedUp -> {
                    val peerCount = DesktopListeningStats.peerDeviceCount()
                    val msg = if (peerCount > 0) {
                        DesktopStrings.format(
                            "d_drive_backup_synced_done",
                            res.fileName,
                            (res.sizeBytes / 1024L).coerceAtLeast(1L),
                            peerCount,
                            fallback = "Backed up to Google Drive (%1\$s · %2\$d KB) · Synced Replay from %3\$d other devices",
                        )
                    } else {
                        DesktopStrings.format(
                            "d_drive_backup_done",
                            res.fileName,
                            (res.sizeBytes / 1024L).coerceAtLeast(1L),
                            fallback = "Backed up to Google Drive (%1\$s · %2\$d KB)",
                        )
                    }
                    BackupNoticeBanner(
                        text = msg,
                        isError = false,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                is DesktopDriveBackup.ActionResult.Restored -> {
                    val msg = if (res.fromMobile) {
                        DesktopStrings.format(
                            "d_drive_restore_mobile_done",
                            res.sourceName,
                            res.summary.months,
                            res.settingsApplied,
                            fallback = "Restored Replay and preferences from %1\$s (%2\$d months, %3\$d settings)",
                        )
                    } else {
                        DesktopStrings.format(
                            "d_drive_restore_done",
                            res.sourceName,
                            res.summary.playlists,
                            res.summary.months,
                            fallback = "Restored from %1\$s (%2\$d playlists, %3\$d months)",
                        )
                    }
                    BackupNoticeBanner(
                        text = msg,
                        isError = false,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                is DesktopDriveBackup.ActionResult.Failed -> {
                    BackupNoticeBanner(
                        text = res.message,
                        isError = true,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                null -> Unit
            }

            settings.lastError?.takeIf { lastResult !is DesktopDriveBackup.ActionResult.Failed }?.let { err ->
                BackupNoticeBanner(
                    text = err,
                    isError = true,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }

    if (showConnectDialog) {
        DriveConnectDialog(
            onDismiss = { showConnectDialog = false },
            onConnected = {
                showConnectDialog = false
                onReplaySynced()
            },
        )
    }

    if (confirmDisconnect) {
        DesktopConfirmDialog(
            title = DesktopStrings["d_drive_disconnect_confirm_title", "Disconnect Google Drive?"],
            message = DesktopStrings["d_drive_disconnect_confirm_body", "Automatic backups will stop and Google Drive authorization on this computer will be removed."],
            confirmLabel = DesktopStrings["disconnect", "Disconnect"],
            destructive = true,
            onDismiss = { confirmDisconnect = false },
            onConfirm = {
                confirmDisconnect = false
                scope.launch {
                    DesktopDriveBackup.disconnect()
                    onReplaySynced()
                }
            },
        )
    }

    pendingRestore?.let { item ->
        val isThisComputer = DesktopDriveBackup.isCurrentDevice(item)
        DriveRestoreDialog(
            item = item,
            isThisComputer = isThisComputer,
            adoptSession = adoptSessionOnRestore,
            onAdoptChange = { adoptSessionOnRestore = it },
            onDismiss = { pendingRestore = null },
            onConfirm = {
                val chosen = item
                val adopt = adoptSessionOnRestore && chosen.deviceKind == RemoteDeviceKind.DESKTOP && !isThisComputer
                pendingRestore = null
                DesktopDriveBackup.restoreBackupAsync(
                    backup = chosen,
                    adoptDeviceSession = adopt,
                    onRestored = onRestored,
                )
            },
        )
    }

    pendingDeleteSnapshot?.let { item ->
        val whenLabel = if (item.updatedAtMs > 0L) formatTimestamp(item.updatedAtMs) else item.name
        DesktopConfirmDialog(
            title = DesktopStrings.format(
                "d_drive_delete_snapshot_title",
                whenLabel,
                fallback = "Delete backup from %1\$s?",
            ),
            message = DesktopStrings["d_drive_delete_backup_body", "This removes that backup file from your Google Drive app folder."],
            confirmLabel = DesktopStrings["delete", "Delete"],
            destructive = true,
            onDismiss = { pendingDeleteSnapshot = null },
            onConfirm = {
                val chosen = item
                pendingDeleteSnapshot = null
                DesktopDriveBackup.deleteBackupAsync(chosen, onDeleted = onReplaySynced)
            },
        )
    }

    pendingDeleteDevice?.let { group ->
        DesktopConfirmDialog(
            title = DesktopStrings.format(
                "d_drive_delete_device_title",
                group.deviceName,
                fallback = "Delete device %1\$s?",
            ),
            message = DesktopStrings.format(
                "d_drive_delete_device_body",
                group.snapshotCount,
                fallback = "This permanently removes all %1\$d backup files for this device from your Google Drive app folder.",
            ),
            confirmLabel = DesktopStrings["delete", "Delete"],
            destructive = true,
            onDismiss = { pendingDeleteDevice = null },
            onConfirm = {
                val chosen = group
                pendingDeleteDevice = null
                DesktopDriveBackup.deleteDeviceAsync(chosen, onDeleted = onReplaySynced)
            },
        )
    }

    if (showRenameDialog) {
        DriveRenameDeviceDialog(
            initialName = settings.deviceName,
            onDismiss = { showRenameDialog = false },
            onSave = { newName ->
                DesktopDriveBackup.setDeviceName(newName)
                showRenameDialog = false
            },
        )
    }

    if (confirmNewSession) {
        DesktopConfirmDialog(
            title = DesktopStrings["d_drive_new_session_title", "Start a new device session?"],
            message = DesktopStrings[
                "d_drive_new_session_body",
                "This computer will receive a new device ID. Future backups will appear under the new device without deleting backups already in Google Drive.",
            ],
            confirmLabel = DesktopStrings["d_drive_new_session_action", "New session"],
            destructive = false,
            onDismiss = { confirmNewSession = false },
            onConfirm = {
                confirmNewSession = false
                DesktopDriveBackup.startNewDeviceSessionAsync(onDone = onReplaySynced)
            },
        )
    }
}

@Composable
private fun DriveDeviceGroupSection(
    group: DesktopDriveBackup.RemoteDeviceGroup,
    isThisComputer: Boolean,
    expanded: Boolean,
    isBusy: Boolean,
    onToggleExpand: () -> Unit,
    onRestoreSnapshot: (DesktopDriveBackup.RemoteBackup) -> Unit,
    onDeleteSnapshot: (DesktopDriveBackup.RemoteBackup) -> Unit,
    onDeleteDevice: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (group.kind == RemoteDeviceKind.MOBILE) Icons.Rounded.Smartphone else Icons.Rounded.Computer,
                contentDescription = null,
                tint = DesktopInk.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = group.deviceName,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isThisComputer) {
                        DriveStatusBadge(
                            text = DesktopStrings["d_drive_this_computer", "This computer"],
                            accent = true,
                        )
                    } else if (group.kind == RemoteDeviceKind.MOBILE) {
                        DriveStatusBadge(
                            text = DesktopStrings["d_drive_mobile_badge", "Mobile"],
                            accent = false,
                        )
                    }
                }
                val subtitle = buildString {
                    if (group.kind == RemoteDeviceKind.MOBILE) {
                        if (group.latestUpdatedAtMs > 0L) {
                            append(formatTimestamp(group.latestUpdatedAtMs))
                            append(" · ")
                        }
                        append(
                            DesktopStrings.format(
                                "d_drive_month_count",
                                group.mobileMonthsCount,
                                fallback = "%1\$d months",
                            ),
                        )
                    } else {
                        append(
                            DesktopStrings.format(
                                "d_drive_backup_count",
                                group.snapshots.size,
                                fallback = "%1\$d backups",
                            ),
                        )
                        if (group.latestUpdatedAtMs > 0L) {
                            append(" · ")
                            append(formatTimestamp(group.latestUpdatedAtMs))
                        }
                    }
                    if (group.totalSizeBytes > 0L) {
                        append(" · ")
                        append("${(group.totalSizeBytes / 1024L).coerceAtLeast(1L)} KB")
                    }
                    if (group.versionName.isNotBlank()) {
                        append(" · v${group.versionName}")
                    }
                }
                Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(
                onClick = onDeleteDevice,
                enabled = !isBusy,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, "Delete device", tint = DesktopSecondary, modifier = Modifier.size(18.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = DesktopSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (group.kind == RemoteDeviceKind.MOBILE) {
                    val mobileSnap = group.latestSnapshot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DesktopInk.copy(alpha = 0.04f))
                            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DesktopStrings[
                                "d_drive_mobile_note",
                                "Mobile cloud sync stores Replay listening history and app settings.",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (mobileSnap != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = { onRestoreSnapshot(mobileSnap) },
                                enabled = !isBusy,
                                colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
                            ) {
                                Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(DesktopStrings["d_drive_restore_action", "Restore"])
                            }
                        }
                    }
                } else {
                    group.snapshots.forEachIndexed { snapIndex, snap ->
                        DriveSnapshotRow(
                            snapshot = snap,
                            isLatest = snapIndex == 0,
                            isBusy = isBusy,
                            onRestore = { onRestoreSnapshot(snap) },
                            onDelete = { onDeleteSnapshot(snap) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveSnapshotRow(
    snapshot: DesktopDriveBackup.RemoteBackup,
    isLatest: Boolean,
    isBusy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DesktopInk.copy(alpha = 0.04f))
            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = DesktopSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (snapshot.updatedAtMs > 0L) formatTimestamp(snapshot.updatedAtMs) else snapshot.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = DesktopInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLatest) {
                    DriveStatusBadge(
                        text = DesktopStrings["d_drive_latest_badge", "Latest"],
                        accent = true,
                    )
                }
                DriveStatusBadge(
                    text = if (snapshot.isAutoBackup) {
                        DesktopStrings["d_drive_auto_badge", "Auto"]
                    } else {
                        DesktopStrings["d_drive_manual_badge", "Manual"]
                    },
                    accent = false,
                )
            }
            val meta = buildString {
                if (snapshot.playlistsCount != null && snapshot.likedCount != null && snapshot.monthsCount != null) {
                    append(
                        DesktopStrings.format(
                            "d_drive_snapshot_meta",
                            snapshot.playlistsCount,
                            snapshot.likedCount,
                            snapshot.monthsCount,
                            fallback = "%1\$d playlists · %2\$d liked · %3\$d months",
                        ),
                    )
                }
                if (snapshot.sizeBytes > 0L) {
                    if (isNotEmpty()) append(" · ")
                    append("${(snapshot.sizeBytes / 1024L).coerceAtLeast(1L)} KB")
                }
                if (snapshot.versionName.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("v${snapshot.versionName}")
                }
            }
            if (meta.isNotBlank()) {
                Text(meta, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(
            onClick = onRestore,
            enabled = !isBusy,
            colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
        ) {
            Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(DesktopStrings["d_drive_restore_action", "Restore"])
        }
        IconButton(
            onClick = onDelete,
            enabled = !isBusy,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(Icons.Rounded.DeleteOutline, "Delete snapshot", tint = DesktopSecondary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun DriveStatusBadge(text: String, accent: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (accent) DesktopAccent else DesktopSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) DesktopAccent.copy(alpha = 0.14f) else DesktopInk.copy(alpha = 0.08f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun DriveRestoreDialog(
    item: DesktopDriveBackup.RemoteBackup,
    isThisComputer: Boolean,
    adoptSession: Boolean,
    onAdoptChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460, popup = true) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                DesktopStrings["import_backup_title", "Import a backup?"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DesktopInk,
            )
            val body = if (item.deviceKind == RemoteDeviceKind.MOBILE) {
                DesktopStrings.format(
                    "d_drive_restore_mobile_body",
                    item.deviceName,
                    fallback = "This restores Replay listening stats and compatible settings from mobile device %1\$s. Your desktop playlists and liked songs will be kept.",
                )
            } else {
                val whenStr = if (item.updatedAtMs > 0L) " (${formatTimestamp(item.updatedAtMs)})" else ""
                DesktopStrings.format(
                    "d_drive_restore_confirm_body",
                    "${item.deviceName}$whenStr",
                    fallback = "This replaces this computer's listening history, playlists, and settings with the backup from %1\$s. Sign-ins aren't included.",
                )
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = DesktopSecondary)

            if (item.deviceKind == RemoteDeviceKind.DESKTOP && !isThisComputer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DesktopInk.copy(alpha = 0.04f))
                        .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(12.dp))
                        .clickable { onAdoptChange(!adoptSession) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = adoptSession,
                            onCheckedChange = onAdoptChange,
                            colors = CheckboxDefaults.colors(checkedColor = DesktopAccent, checkmarkColor = DesktopOnAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = DesktopStrings.format(
                                "d_drive_restore_adopt_option",
                                item.deviceName,
                                fallback = "Continue syncing as %1\$s (adopt its device session)",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = DesktopInk,
                        )
                    }
                    Text(
                        text = DesktopStrings[
                            "d_drive_restore_adopt_hint",
                            "Recommended when migrating a device so Replay stats are not counted twice across devices.",
                        ],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        modifier = Modifier.padding(start = 42.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
                ) {
                    Text(DesktopStrings["cancel", "Cancel"])
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopDestructive, contentColor = Color.White),
                ) {
                    Text(DesktopStrings["d_drive_restore_action", "Restore"], fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DriveRenameDeviceDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420, popup = true) {
        val focusRequester = remember { FocusRequester() }
        var draft by remember(initialName) {
            val clamped = initialName.take(60)
            mutableStateOf(
                TextFieldValue(
                    text = clamped,
                    selection = TextRange(clamped.length),
                ),
            )
        }
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }
        fun saveIfValid() {
            val trimmed = draft.text.trim()
            if (trimmed.isNotEmpty()) {
                onSave(trimmed)
            }
        }
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                DesktopStrings["d_drive_rename_title", "Rename this device"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DesktopInk,
            )
            DesktopGlassTextField(
                value = draft,
                onValueChange = { next -> draft = limitTextFieldValue(next, maxLength = 60) },
                label = DesktopStrings["d_drive_rename_placeholder", "Device name"],
                placeholder = DesktopDriveBackup.defaultDeviceName(),
                onSubmit = ::saveIfValid,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
                ) {
                    Text(DesktopStrings["cancel", "Cancel"])
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = ::saveIfValid,
                    enabled = draft.text.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = DesktopOnAccent),
                ) {
                    Text(DesktopStrings["save", "Save"], fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DriveConnectDialog(
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }

    DesktopDialogPanel(onDismiss = { if (!busy) onDismiss() }, maxWidth = 540, popup = true) {
        val scope = rememberCoroutineScope()
        val hasBuiltIn = remember { DesktopDriveAuth.hasBuiltInClient() }
        var mode by remember { mutableStateOf(if (hasBuiltIn) 0 else 1) } // 0 = Built-in, 1 = Custom BYO, 2 = Access Token
        var clientId by remember { mutableStateOf(DesktopDriveAuth.savedClientId()) }
        var clientSecret by remember { mutableStateOf(DesktopDriveAuth.savedClientSecret()) }
        var accessToken by remember { mutableStateOf("") }
        var showByoGuide by remember { mutableStateOf(!hasBuiltIn && clientId.isBlank()) }
        var error by remember { mutableStateOf<String?>(null) }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = DesktopStrings["d_drive_connect", "Connect Google Drive"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DesktopInk,
            )
            PillSegmentedControl(
                options = listOf(
                    DesktopStrings["d_drive_auth_builtin", "BitChord Sign-In"],
                    DesktopStrings["d_drive_auth_byo", "Custom OAuth (BYO)"],
                    DesktopStrings["d_drive_auth_token", "Access Token"],
                ),
                selectedIndex = mode,
                onSelect = {
                    if (!busy) {
                        mode = it
                        error = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(DesktopInk.copy(alpha = 0.06f)),
            )

            when (mode) {
                0 -> {
                    Text(
                        text = DesktopStrings[
                            "d_drive_builtin_hint",
                            "Quickly sign in using BitChord Desktop's built-in Google OAuth app. BitChord only requests the hidden drive.appdata folder and can never view your personal Drive files.",
                        ],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                    )
                    if (!hasBuiltIn) {
                        BackupNoticeBanner(
                            text = DesktopStrings[
                                "d_drive_builtin_unavailable",
                                "This build does not bundle a built-in OAuth Client ID (GDRIVE_CLIENT_ID). Switch to Custom OAuth (BYO) or Access Token to connect now.",
                            ],
                            isError = false,
                        )
                    }
                }
                1 -> {
                    // Privacy rationale explaining why BYO exists
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DesktopInk.copy(alpha = 0.05f))
                            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Shield, null, tint = DesktopAccent, modifier = Modifier.size(16.dp))
                            Text(
                                text = DesktopStrings["d_drive_byo_why_title", "Why Custom OAuth (BYO)?"],
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopInk,
                            )
                        }
                        Text(
                            text = DesktopStrings[
                                "d_drive_byo_why_body",
                                "For maximum privacy and transparency, BYO lets you use your own Google Cloud OAuth app instead of sharing the developer's client. Everything stays strictly inside your own Google Cloud project.",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                        )
                    }

                    // Expandable step-by-step tutorial
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DesktopInk.copy(alpha = 0.04f))
                            .border(0.75.dp, DesktopCardEdge, RoundedCornerShape(12.dp)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showByoGuide = !showByoGuide }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.HelpOutline, null, tint = DesktopInk, modifier = Modifier.size(16.dp))
                                Text(
                                    text = DesktopStrings["d_drive_byo_tutorial_title", "How to get your own OAuth Client ID"],
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopInk,
                                )
                            }
                            Icon(
                                imageVector = if (showByoGuide) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = DesktopSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        AnimatedVisibility(visible = showByoGuide) {
                            Text(
                                text = DesktopStrings[
                                    "d_drive_byo_tutorial_steps",
                                    "1. Open Google Cloud Console (console.cloud.google.com) and create a project.\n2. Go to APIs & Services -> Library, search and enable Google Drive API.\n3. Open Google Auth Platform / OAuth consent screen -> select External -> stay in Testing mode and add your Gmail under Test users.\n4. Go to Clients / Credentials -> Create OAuth client ID -> choose Desktop app.\n5. Copy the Client ID (and Client Secret if shown) into the fields below.",
                                ],
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            )
                        }
                    }

                    DesktopGlassTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = DesktopStrings["d_drive_client_id", "OAuth Client ID"],
                        placeholder = "1234567890-xxxx.apps.googleusercontent.com",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DesktopGlassTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        label = DesktopStrings["d_drive_client_secret", "Client Secret (optional)"],
                        placeholder = "GOCSPX-...",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Text(
                        text = DesktopStrings[
                            "d_drive_token_hint",
                            "Paste an OAuth 2.0 Bearer access token authorized for https://www.googleapis.com/auth/drive.appdata (for example from Google OAuth 2.0 Playground).",
                        ],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                    )
                    DesktopGlassTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = DesktopStrings["d_drive_auth_token", "Access Token"],
                        placeholder = "ya29.a0...",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            error?.let { BackupNoticeBanner(text = it, isError = true) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
                ) {
                    Text(DesktopStrings["cancel", "Cancel"])
                }
                Spacer(Modifier.width(8.dp))
                val canSubmit = when (mode) {
                    0 -> hasBuiltIn
                    1 -> clientId.isNotBlank()
                    else -> accessToken.isNotBlank()
                }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val outcome = when (mode) {
                                0 -> DesktopDriveBackup.connectWithBuiltIn()
                                1 -> DesktopDriveBackup.connectWithBrowser(clientId, clientSecret)
                                else -> DesktopDriveBackup.connectWithAccessToken(accessToken)
                            }
                            busy = false
                            outcome.fold(
                                onSuccess = { onConnected() },
                                onFailure = { err -> error = err.message ?: "Connection failed" },
                            )
                        }
                    },
                    enabled = !busy && canSubmit,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = DesktopOnAccent),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DesktopOnAccent)
                    else Text(
                        text = if (mode == 0) DesktopStrings["sign_in", "Sign in"] else DesktopStrings["d_drive_connect_action", "Connect"],
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420, popup = true) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DesktopInk)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = DesktopSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = DesktopInk),
                ) {
                    Text(DesktopStrings["cancel", "Cancel"])
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (destructive) DesktopDestructive else DesktopAccent,
                        contentColor = if (destructive) Color.White else DesktopOnAccent,
                    ),
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BackupNoticeBanner(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isError) DesktopDestructive else Color(0xFF28C840)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(0.75.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = DesktopInk)
    }
}

private fun formatReplayDurationSummary(summary: DesktopReplaySummary): String? {
    if (summary.isEmpty) return null
    val mins = summary.minutes
    val hours = mins / 60L
    return when {
        hours > 0L -> "${hours}h ${mins % 60L}m"
        mins > 0L -> "${mins}m"
        else -> "${summary.totalPlays}"
    }
}

private fun driveFrequencyLabel(frequency: DesktopBackupFrequency): String = when (frequency) {
    DesktopBackupFrequency.OFF -> DesktopStrings["d_off", "Off"]
    DesktopBackupFrequency.DAILY -> DesktopStrings["d_freq_daily", "Daily"]
    DesktopBackupFrequency.WEEKLY -> DesktopStrings["d_freq_weekly", "Weekly"]
    DesktopBackupFrequency.MONTHLY -> DesktopStrings["d_freq_monthly", "Monthly"]
}

private fun driveRetentionLabel(retention: DesktopBackupRetention): String = when (retention) {
    DesktopBackupRetention.FIVE -> "5"
    DesktopBackupRetention.TEN -> "10"
    DesktopBackupRetention.TWENTY -> "20"
    DesktopBackupRetention.UNLIMITED -> DesktopStrings["d_drive_retention_all", "All"]
}

private fun formatTimestamp(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
