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
import org.sumidoku.android.core.image.upscale.DenoiseLevel
import org.sumidoku.android.core.image.upscale.UpscalePrecision
import org.sumidoku.android.core.image.upscale.UpscaleSettings
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

            // Waifu2x upscaling: a device capability (GPU time, bundled models, driver quirks), so
            // it lives on the global layer only and defaults to off.
            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_upscale_title))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_upscale_enable)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.reader_upscale_enable_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.upscale.enabled,
                            onCheckedChange = viewModel::setUpscaleEnabled,
                        )
                    },
                )
                if (state.upscale.enabled) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "%.2fx".format(state.upscale.autoTriggerScale),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = state.upscale.autoTriggerScale,
                            onValueChange = viewModel::setUpscaleThreshold,
                            valueRange = UpscaleSettings.MIN_AUTO_TRIGGER_SCALE..UpscaleSettings.MAX_AUTO_TRIGGER_SCALE,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Caption(stringResource(R.string.reader_upscale_threshold_hint))
                    Caption(stringResource(R.string.reader_upscale_denoise))
                    ChipRow(
                        options = DenoiseLevel.entries.map { it.raw to stringResource(denoiseLabel(it)) },
                        selected = state.upscale.denoise.raw,
                        onSelect = { viewModel.setUpscaleDenoise(DenoiseLevel.fromRaw(it)) },
                    )
                    Caption(stringResource(R.string.reader_upscale_precision))
                    ChipRow(
                        options = UpscalePrecision.entries.map {
                            it.raw to stringResource(
                                when (it) {
                                    UpscalePrecision.AUTO -> R.string.reader_upscale_precision_auto
                                    UpscalePrecision.FP16 -> R.string.reader_upscale_precision_fp16
                                    UpscalePrecision.FP32 -> R.string.reader_upscale_precision_fp32
                                },
                            )
                        },
                        selected = state.upscale.precision.raw,
                        onSelect = { viewModel.setUpscalePrecision(UpscalePrecision.fromRaw(it)) },
                    )
                    Caption(stringResource(R.string.reader_upscale_preload))
                    ChipRow(
                        options = (0..UpscaleSettings.MAX_PRELOAD_AHEAD).map {
                            it.toString() to
                                if (it == 0) stringResource(R.string.reader_upscale_preload_off) else it.toString()
                        },
                        selected = state.upscale.preloadAhead.toString(),
                        onSelect = { viewModel.setUpscalePreload(it.toIntOrNull() ?: 0) },
                    )
                    Caption(stringResource(R.string.reader_upscale_device_hint))
                }
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
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_volume_key_paging)) },
                    supportingContent = {
                        Text(
                            stringResource(R.string.reader_volume_key_paging_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.volumeKeyPaging,
                            onCheckedChange = viewModel::setVolumeKeyPaging,
                        )
                    },
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
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_text_columns)) },
                    supportingContent = { Text(stringResource(R.string.reader_text_columns_hint)) },
                    trailingContent = {
                        Switch(
                            checked = state.textColumns,
                            onCheckedChange = viewModel::setTextColumns,
                        )
                    },
                )
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

            SettingsGroupCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle(stringResource(R.string.reader_behavior_global))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_keep_screen_awake)) },
                    trailingContent = {
                        Switch(
                            checked = state.keepScreenAwake,
                            onCheckedChange = viewModel::setKeepScreenAwake,
                        )
                    },
                )
                val hasCutout = rememberHasDisplayCutout()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reader_draw_under_cutout)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (hasCutout) R.string.reader_draw_under_cutout_hint
                                else R.string.reader_no_display_cutout,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.drawUnderCutout,
                            onCheckedChange = viewModel::setDrawUnderCutout,
                            enabled = hasCutout,
                        )
                    },
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

/** Localized label of one waifu2x denoise level ("none" ships its own weights). */
private fun denoiseLabel(level: DenoiseLevel): Int = when (level) {
    DenoiseLevel.NONE -> R.string.reader_upscale_denoise_none
    DenoiseLevel.LOW -> R.string.reader_upscale_denoise_low
    DenoiseLevel.MEDIUM -> R.string.reader_upscale_denoise_medium
    DenoiseLevel.HIGH -> R.string.reader_upscale_denoise_high
    DenoiseLevel.MAX -> R.string.reader_upscale_denoise_max
}
