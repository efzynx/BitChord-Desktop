package com.music.bitchord.desktop

import com.music.bitchord.data.lyrics.TRANSLATION_LANGUAGES
import com.music.bitchord.data.lyrics.translationLanguageName

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.icons.BitChordIcons
import java.util.Locale

/**
 * Which language the translate button translates into.
 *
 * Borrows the lyrics-sources card — label over detail, a checkmark, pinned actions — with the two
 * things a hundred and thirty rows force: it scrolls, and it has a filter field, because scrolling
 * to Vietnamese is not a way to pick Vietnamese.
 */
@Composable
internal fun DesktopTranslationLanguageDialog(onDismiss: () -> Unit) {
    val stored by DesktopTranslationSetting.language.collectAsState()
    val locale = remember { Locale.forLanguageTag(DesktopStrings.resolvedTag().ifBlank { "en" }) }
    var filter by remember { mutableStateOf("") }
    val isPage = LocalDesktopPanelIsPage.current

    // Named in the reader's own language, and sorted that way too — the table's own order is
    // alphabetical by English name, which is not alphabetical once localised.
    val named = remember(locale) {
        TRANSLATION_LANGUAGES
            .map { it.code to translationLanguageName(it.code, locale) }
            .sortedBy { it.second.lowercase(locale) }
    }
    val shown = remember(named, filter) {
        val needle = filter.trim().lowercase(locale)
        if (needle.isBlank()) named else named.filter { it.second.lowercase(locale).contains(needle) }
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420) {
        if (isPage) {
            Column(Modifier.fillMaxSize()) {
                DesktopSettingsSubPageHeader(
                    title = DesktopStrings["translation_language", "Translation language"],
                    subtitle = DesktopStrings["d_what_the_translate_button_translates_into", "What the translate button on the lyrics translates into"],
                    onBack = onDismiss,
                )
                DesktopSearchField(
                    query = filter,
                    onQueryChange = { filter = it },
                    onSearch = {},
                    placeholder = DesktopStrings["d_filter_languages", "Filter languages"],
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 28.dp)
                        .desktopSettingsCardSurface(RoundedCornerShape(14.dp)),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        item(key = "follow_app") {
                            LanguageRow(
                                name = DesktopStrings["d_follow_the_app_language", "Follow the app language"],
                                detail = translationLanguageName(DesktopStrings.resolvedTag(), locale),
                                selected = stored.isBlank(),
                                isPage = true,
                                onClick = { DesktopTranslationSetting.set("") },
                            )
                        }
                        itemsIndexed(shown, key = { _, pair -> pair.first }) { _, (code, name) ->
                            HorizontalDivider(thickness = 0.5.dp, color = DesktopCardEdge)
                            LanguageRow(
                                name = name,
                                detail = code,
                                selected = stored.equals(code, ignoreCase = true),
                                isPage = true,
                                onClick = { DesktopTranslationSetting.set(code) },
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp), vertical = 18.dp)) {
                Text(
                    DesktopStrings["translation_language", "Translation language"],
                    style = MaterialTheme.typography.titleLarge,
                    color = DesktopInk,
                )
                Text(
                    DesktopStrings["d_what_the_translate_button_translates_into", "What the translate button on the lyrics translates into"],
                    style = MaterialTheme.typography.bodyMedium,
                    color = DesktopSecondary,
                )
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp))) {
                DesktopSearchField(
                    query = filter,
                    onQueryChange = { filter = it },
                    onSearch = {},
                    placeholder = DesktopStrings["d_filter_languages", "Filter languages"],
                )
            }

            LazyColumn(
                Modifier.fillMaxWidth().then(desktopPanelBody(cardMax = 340.dp)).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    LanguageRow(
                        name = DesktopStrings["d_follow_the_app_language", "Follow the app language"],
                        detail = translationLanguageName(DesktopStrings.resolvedTag(), locale),
                        selected = stored.isBlank(),
                        isPage = false,
                        onClick = { DesktopTranslationSetting.set("") },
                    )
                }
                itemsIndexed(shown, key = { _, pair -> pair.first }) { _, (code, name) ->
                    LanguageRow(
                        name = name,
                        detail = code,
                        selected = stored.equals(code, ignoreCase = true),
                        isPage = false,
                        onClick = { DesktopTranslationSetting.set(code) },
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp), vertical = 14.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = DesktopInk) }
            }
        }
    }
}

@Composable
private fun LanguageRow(name: String, detail: String, selected: Boolean, isPage: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ink = DesktopInk
    Row(
        Modifier
            .fillMaxWidth()
            .desktopHoverFill(corner = 0.dp)
            .desktopGlassPress(RectangleShape, interactionSource = interaction, pressScale = 0.99f, hoverScale = 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(
                if (selected && !isPage) ink.copy(alpha = 0.06f) else Color.Transparent,
            )
            .heightIn(min = if (isPage) 52.dp else 44.dp)
            .padding(horizontal = if (isPage) 16.dp else panelInset(20.dp), vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = if (isPage) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = ink,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                BitChordIcons.Check,
                contentDescription = DesktopStrings["selected", "Selected"],
                tint = DesktopAccent,
                modifier = Modifier.size(if (isPage) 20.dp else 18.dp),
            )
        }
    }
}
