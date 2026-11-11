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
            Text("导入 Mihon 备份（预览）", style = MaterialTheme.typography.titleMedium)
            Text(
                "将导入：漫画 ${report.imported.library}、进度 ${report.imported.progress}、分类 ${report.imported.categories}、追踪绑定 ${report.imported.trackers}（跳过 ${report.skipped.size} 条）。请确认每个图源的映射：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            report.sources.forEach { source ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(source.mihonSourceName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${source.mangaCount} 部漫画",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (source.match) {
                        SuwayomiSourceReport.Match.MAPPED,
                        SuwayomiSourceReport.Match.AUTO,
                        -> Text(
                            "✓ ${source.aidokuSourceName ?: source.aidokuSourceKey ?: "已匹配"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SuwayomiSourceReport.Match.UNMATCHED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "未自动匹配",
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
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入") }
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
            Text(selectedName ?: "选择映射…")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("跳过该源") }, onClick = {
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
