package org.sumidoku.android

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The "More" tab (komikku parity): secondary destinations — downloads,
 *  search, settings and the misc management pages — as a rounded-card entry
 *  list, keeping the bottom bar at five tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTrackers: () -> Unit,
    onOpenBackups: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_more)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    MoreRow(Icons.Outlined.CloudDownload, R.string.tab_downloads, onOpenDownloads)
                    MoreRow(Icons.Outlined.Category, R.string.more_categories, onOpenCategories)
                    MoreRow(Icons.Outlined.SyncAlt, R.string.more_trackers, onOpenTrackers)
                    MoreRow(Icons.Outlined.Backup, R.string.more_backups, onOpenBackups)
                    MoreRow(Icons.Outlined.Settings, R.string.tab_settings, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}
