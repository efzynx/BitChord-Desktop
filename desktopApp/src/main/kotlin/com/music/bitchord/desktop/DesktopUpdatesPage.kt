package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Halaman pengaturan khusus BitChord Desktop untuk memantau status pembaruan,
 * informasi sistem, dan metode instalasi aplikasi.
 */
@Composable
internal fun DesktopUpdatesPage(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var updateStatus by remember {
        mutableStateOf<DesktopUpdateChecker.UpdateCheckStatus>(DesktopUpdateChecker.UpdateCheckStatus.Idle)
    }

    fun triggerCheck() {
        scope.launch {
            updateStatus = DesktopUpdateChecker.UpdateCheckStatus.Checking
            updateStatus = DesktopUpdateChecker.checkManual()
        }
    }

    // Jalankan cek saat halaman pertama kali dibuka
    LaunchedEffect(Unit) {
        triggerCheck()
    }

    DesktopSettingsPageFrame {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "Pembaruan & Info Sistem",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "BitChord Desktop · efzynx/BitChord-Desktop",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: Update Checker
            item {
                UpdateStatusCard(
                    status = updateStatus,
                    onCheckAgain = ::triggerCheck,
                )
            }

            // Section 2: System Information
            item {
                SystemInfoCard()
            }

            // Section 3: Installation & Uninstallation Guide
            item {
                InstallationInfoCard()
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(
    status: DesktopUpdateChecker.UpdateCheckStatus,
    onCheckAgain: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp),
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DesktopAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = DesktopAccent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Status Pembaruan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Versi Terpasang: v${DesktopSystemInfo.appVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                        )
                    }
                }

                if (status !is DesktopUpdateChecker.UpdateCheckStatus.Checking) {
                    OutlinedButton(
                        onClick = onCheckAgain,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Periksa", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (status) {
                is DesktopUpdateChecker.UpdateCheckStatus.Idle,
                is DesktopUpdateChecker.UpdateCheckStatus.Checking,
                -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = DesktopAccent,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Memeriksa rilis terbaru di repository efzynx/BitChord-Desktop...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DesktopSecondary,
                        )
                    }
                }

                is DesktopUpdateChecker.UpdateCheckStatus.UpToDate -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "BitChord Desktop sudah versi terbaru.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Tidak ada pembaruan rilis baru saat ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                            )
                        }
                    }
                }

                is DesktopUpdateChecker.UpdateCheckStatus.UpdateAvailable -> {
                    val update = status.update
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DesktopAccent.copy(alpha = 0.08f))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = DesktopAccent,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Versi Baru Tersedia: v${update.version}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DesktopAccent,
                            )
                        }

                        if (!update.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Catatan Rilis:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = update.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                maxLines = 6,
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!update.downloadUrl.isNullOrBlank()) {
                                Button(
                                    onClick = { DesktopExternalLinks.open(update.downloadUrl) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Unduh Installer")
                                }
                            }

                            OutlinedButton(
                                onClick = { DesktopExternalLinks.open(update.releaseUrl) },
                            ) {
                                Text("Buka Rilis GitHub")
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                is DesktopUpdateChecker.UpdateCheckStatus.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemInfoCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp),
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Informasi Sistem & Runtime",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Lingkungan perangkat saat ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            InfoRow(label = "Sistem Operasi", value = DesktopSystemInfo.prettyOsName)
            InfoRow(label = "Arsitektur CPU", value = DesktopSystemInfo.osArch)
            InfoRow(label = "Java Runtime", value = "${DesktopSystemInfo.javaVersion} (${DesktopSystemInfo.javaVendor})")
            InfoRow(label = "Versi BitChord", value = "v${DesktopSystemInfo.appVersion}")
            InfoRow(
                label = "Repositori Sumber",
                value = "efzynx/BitChord-Desktop",
                isLink = true,
                onClick = { DesktopExternalLinks.open(DesktopSystemInfo.repoUrl) },
            )
        }
    }
}

@Composable
private fun InstallationInfoCard() {
    val installType = DesktopSystemInfo.installType

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp),
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Metode Instalasi Terdeteksi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = installType.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopAccent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = installType.formatDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(14.dp))

            // Box panduan uninstall
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Panduan Uninstall / Menghapus:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = installType.uninstallHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DesktopSecondary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (isLink && onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isLink) DesktopAccent else MaterialTheme.colorScheme.onSurface,
            )
            if (isLink) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = DesktopAccent,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}
