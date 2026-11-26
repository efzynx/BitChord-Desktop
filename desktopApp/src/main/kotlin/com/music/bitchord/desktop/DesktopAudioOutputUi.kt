package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Where playback is sent. Android routes through `AudioManager`; here the choice is a mixer.
 *
 * Enumerated when the dialog opens rather than held: devices come and go with what is plugged in.
 */
@Composable
internal fun DesktopAudioOutputDialog(onDismiss: () -> Unit) {
    val selected by DesktopAudioDevices.selected.collectAsState()
    val changes by DesktopAudioDevices.changes.collectAsState()
    val devices = remember(changes) { DesktopAudioDevices.available() }
    val isPage = LocalDesktopPanelIsPage.current

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 440) {
        if (isPage) {
            Column(Modifier.fillMaxSize()) {
                DesktopSettingsSubPageHeader(
                    title = DesktopStrings["audio_output", "Audio output"],
                    subtitle = DesktopStrings["d_audio_output_subtitle", "Changes take effect immediately."],
                    onBack = onDismiss,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp),
                ) {
                    DesktopGroupedCard(topSpacing = 4.dp) {
                        OutputRow(
                            name = DesktopStrings["d_system_default", "System default"],
                            description = "",
                            chosen = selected == DesktopAudioDevices.SYSTEM_DEFAULT,
                            isPage = true,
                        ) {
                            DesktopAudioDevices.select(DesktopAudioDevices.SYSTEM_DEFAULT)
                            onDismiss()
                        }
                        devices.forEach { device ->
                            DesktopGroupedDivider()
                            OutputRow(
                                name = device.name,
                                description = device.description,
                                chosen = selected == device.id,
                                isPage = true,
                            ) {
                                DesktopAudioDevices.select(device.id)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = panelInset(22.dp), vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    DesktopStrings["audio_output", "Audio output"],
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    DesktopStrings["d_audio_output_subtitle", "Changes take effect immediately."],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutputRow(
                    name = DesktopStrings["d_system_default", "System default"],
                    description = "",
                    chosen = selected == DesktopAudioDevices.SYSTEM_DEFAULT,
                    isPage = false,
                ) {
                    DesktopAudioDevices.select(DesktopAudioDevices.SYSTEM_DEFAULT)
                    onDismiss()
                }
                devices.forEach { device ->
                    OutputRow(device.name, device.description, chosen = selected == device.id, isPage = false) {
                        DesktopAudioDevices.select(device.id)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputRow(name: String, description: String, chosen: Boolean, isPage: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val rowShape = RoundedCornerShape(if (isPage) 0.dp else 10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (isPage) {
                    Modifier.desktopHoverFill(corner = 0.dp)
                } else {
                    Modifier.clip(rowShape).desktopHoverFill(corner = 10.dp)
                },
            )
            .desktopGlassPress(rowShape, interactionSource = interaction, pressScale = 0.988f, hoverScale = 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = if (isPage) 54.dp else 44.dp)
            .padding(horizontal = if (isPage) 16.dp else 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPage) {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = if (chosen) DesktopAccent else DesktopInk.copy(alpha = 0.8f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (chosen) DesktopInk else DesktopInk.copy(alpha = 0.92f),
                fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chosen) {
            Spacer(Modifier.width(12.dp))
            Icon(BitChordIcons.Check, DesktopStrings["selected", "Selected"], tint = DesktopAccent, modifier = Modifier.size(20.dp))
        }
    }
}
