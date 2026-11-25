package org.sumidoku.android.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sumidoku.android.core.ui.SettingsGroupCard

/**
 * Reader defaults — preview 24-reader-settings.html without the per-manga
 * override chain: every change writes the global DataStore keys directly, and
 * the reader still prefers a per-manga override, then `Manga.viewer`, then these.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderDefaultsScreen(
    onBack: () -> Unit,
    viewModel: ReaderDefaultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reader_defaults_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.reset() }) { Text(stringResource(R.string.reader_reset_defaults)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Note(stringResource(R.string.reader_defaults_intro))

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_mode))
                ChipRow(
                    options = listOf(
                        "paged" to stringResource(R.string.reader_mode_paged),
                        "continuous" to stringResource(R.string.reader_defaults_mode_continuous),
                        "webtoon" to stringResource(R.string.reader_mode_webtoon),
                    ),
                    selected = state.mode,
                    onSelect = viewModel::setMode,
                )
                Caption(stringResource(R.string.reader_defaults_mode_hint))
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_defaults_layout))
                ChipRow(
                    options = listOf(
                        "single" to stringResource(R.string.reader_layout_single),
                        "double" to stringResource(R.string.reader_layout_double),
                        "auto" to stringResource(R.string.reader_layout_auto),
                    ),
                    selected = state.layout,
                    onSelect = viewModel::setLayout,
                )
                Caption(stringResource(R.string.reader_defaults_layout_hint))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_cover_first)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.reader_defaults_cover_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = state.coverFirst, onCheckedChange = viewModel::setCoverFirst)
                    },
                )
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_direction))
                ChipRow(
                    options = listOf(
                        "default" to stringResource(R.string.reader_dir_follow_manga),
                        "ltr" to stringResource(R.string.reader_dir_ltr),
                        "rtl" to stringResource(R.string.reader_dir_rtl),
                    ),
                    selected = state.dir,
                    onSelect = viewModel::setDir,
                )
                Caption(stringResource(R.string.reader_defaults_dir_hint))
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_defaults_tap_zones))
                ChipRow(
                    options = listOf(
                        "lr" to stringResource(R.string.reader_defaults_tap_lr),
                        "rl" to stringResource(R.string.reader_defaults_tap_rl),
                        "edge" to stringResource(R.string.reader_tap_edge),
                        "kindle" to "Kindle",
                        "off" to stringResource(R.string.reader_defaults_tap_off),
                    ),
                    selected = state.tapZones,
                    onSelect = viewModel::setTapZones,
                )
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_double_tap_zoom))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("%.1fx".format(state.doubleTapZoom), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Slider(
                        value = state.doubleTapZoom,
                        onValueChange = viewModel::setDoubleTapZoom,
                        valueRange = 1f..8f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Caption(stringResource(R.string.reader_double_tap_zoom_hint))
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_page_scale))
                ChipRow(
                    options = listOf(
                        "fit" to stringResource(R.string.reader_defaults_scale_fit),
                        "width" to stringResource(R.string.reader_defaults_scale_width),
                        "height" to stringResource(R.string.reader_defaults_scale_height),
                        "original" to stringResource(R.string.reader_defaults_scale_original),
                    ),
                    selected = state.scale,
                    onSelect = viewModel::setScale,
                )
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_defaults_background_filters))
                ChipRow(
                    options = listOf(
                        "dark" to stringResource(R.string.reader_defaults_bg_dark),
                        "gray" to stringResource(R.string.reader_defaults_bg_gray),
                        "light" to stringResource(R.string.reader_defaults_bg_light),
                        "auto" to stringResource(R.string.reader_defaults_bg_auto),
                    ),
                    selected = state.background,
                    onSelect = viewModel::setBackground,
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_grayscale)) },
                    trailingContent = {
                        Switch(checked = state.grayscale, onCheckedChange = viewModel::setGrayscale)
                    },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_sepia)) },
                    trailingContent = {
                        Switch(checked = state.sepia, onCheckedChange = viewModel::setSepia)
                    },
                )
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_defaults_typography))
                ChipRow(
                    options = listOf(
                        "default" to stringResource(R.string.reader_font_default),
                        "sans" to stringResource(R.string.reader_font_sans),
                        "serif" to stringResource(R.string.reader_font_serif),
                        "mono" to stringResource(R.string.reader_font_mono),
                    ),
                    selected = state.font,
                    onSelect = viewModel::setFont,
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("%.0f%%".format(state.fontSize * 100), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Slider(
                        value = state.fontSize,
                        onValueChange = viewModel::setFontSize,
                        valueRange = ReaderPrefs.FONT_SIZE_RANGE,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("%.1f".format(state.lineHeight), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Slider(
                        value = state.lineHeight,
                        onValueChange = viewModel::setLineHeight,
                        valueRange = ReaderPrefs.LINE_HEIGHT_RANGE,
                        modifier = Modifier.weight(1f),
                    )
                }
                Caption(stringResource(R.string.reader_defaults_typography_hint))
            }

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_brightness))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${(state.brightness * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Slider(
                        value = state.brightness,
                        onValueChange = viewModel::setBrightness,
                        valueRange = 0.4f..1.6f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.reader_defaults_brightness_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    )
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
