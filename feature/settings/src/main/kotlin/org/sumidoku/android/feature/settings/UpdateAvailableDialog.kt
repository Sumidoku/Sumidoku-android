package org.sumidoku.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Announcement for a newer GitHub release.
 *
 * "Download" hands the release APK to the browser — GitHub serves it directly,
 * so the app needs no storage permission and no installer plumbing — and
 * "Later" remembers the version so the launch check stops nagging (a manual
 * check still re-opens it).
 */
@Composable
fun UpdateAvailableDialog(
    state: AppUpdateState,
    onDismiss: () -> Unit,
) {
    val update = state.update ?: return
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_updates_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        R.string.settings_updates_dialog_message,
                        update.version,
                        state.currentVersion,
                    ),
                )
                if (update.notes.isNotBlank()) {
                    Text(
                        text = update.notes.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 40,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = update.downloadUrl ?: update.releaseUrl
                    if (url.isNotBlank()) runCatching { uriHandler.openUri(url) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_updates_dialog_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_updates_dialog_later))
            }
        },
    )
}
