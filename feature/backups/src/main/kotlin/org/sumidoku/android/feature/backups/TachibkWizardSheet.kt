package org.sumidoku.android.feature.backups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sumidoku.android.core.api.model.SuwayomiImportResponse
import org.sumidoku.android.core.api.model.SuwayomiSourceReport

/** Mihon/Suwayomi .tachibk import wizard (design §14 M8): the
 *  dry-run report lists every Mihon source in the backup; auto/mapped
 *  matches show their target, unmatched rows offer a candidate picker
 *  (or skip). The import runs with the collected mapping JSON. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TachibkWizardSheet(
    report: SuwayomiImportResponse,
    mapping: Map<String, String>,
    onMap: (mihonSourceId: String, aidokuSourceKey: String?) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.tachibk_preview_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.tachibk_preview_message,
                    report.imported.library,
                    report.imported.progress,
                    report.imported.categories,
                    report.imported.trackers,
                    report.skipped.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            report.sources.forEach { source ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(source.mihonSourceName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.tachibk_manga_count, source.mangaCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (source.match) {
                        SuwayomiSourceReport.Match.MAPPED,
                        SuwayomiSourceReport.Match.AUTO,
                        -> Text(
                            "✓ ${source.aidokuSourceName ?: source.aidokuSourceKey ?: stringResource(R.string.tachibk_matched_fallback)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SuwayomiSourceReport.Match.UNMATCHED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.tachibk_unmatched),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                SourceMappingPicker(
                                    candidates = source.candidates.map { it.key to it.name },
                                    selectedKey = mapping[source.mihonSourceId],
                                    onSelect = { onMap(source.mihonSourceId, it) },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backups_cancel)) }
                Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backups_import)) }
            }
        }
    }
}

@Composable
private fun SourceMappingPicker(
    candidates: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedName = candidates.firstOrNull { it.first == selectedKey }?.second
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(selectedName ?: stringResource(R.string.tachibk_select_mapping))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.tachibk_skip_source)) }, onClick = {
                open = false
                onSelect(null)
            })
            candidates.forEach { (key, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onSelect(key)
                })
            }
        }
    }
}
