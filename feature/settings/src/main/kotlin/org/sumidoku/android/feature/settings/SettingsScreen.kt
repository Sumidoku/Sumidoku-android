package org.sumidoku.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.hilt.navigation.compose.hiltViewModel

/** Settings hub — static entry rows. Server-side setting groups
 *  (updates/library/download/…) land with M8; this screen only hosts
 *  entries that already have screens. Preview 12-settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenRepos: () -> Unit,
    onManageServers: () -> Unit,
    onOpenTrackers: () -> Unit = {},
    onOpenBackups: () -> Unit = {},
    onOpenApiKeys: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenFilterGroups: () -> Unit = {},
    onOpenServerSettings: () -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("外观") }
            item { AppearanceRow() }
            item { SectionHeader("通用") }
            item { IncognitoRow() }
            item { SectionHeader("图源") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Extension,
                    title = "图源仓库",
                    subtitle = "添加、刷新与删除来源列表",
                    onClick = onOpenRepos,
                )
            }
            item { SectionHeader("服务器") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Dns,
                    title = "服务器管理",
                    subtitle = "切换或添加 Sumidoku 实例",
                    onClick = onManageServers,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "实例间同步（M8）",
                    subtitle = "多实例 peer 同步，即将支持",
                    enabled = false,
                    onClick = {},
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Public,
                    title = "追踪器账号",
                    subtitle = "AniList / MAL / Bangumi / MangaBaka 绑定与默认提供方",
                    enabled = true,
                    onClick = onOpenTrackers,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.CloudQueue,
                    title = "备份与恢复",
                    subtitle = "导出完整备份、从文件恢复、服务器定时备份、Aidoku .aib 导入",
                    enabled = true,
                    onClick = onOpenBackups,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Key,
                    title = "API 密钥与密码",
                    subtitle = "管理访问密钥（新建/吊销）、修改密码",
                    enabled = true,
                    onClick = onOpenApiKeys,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Storage,
                    title = "缓存管理",
                    subtitle = "章节页缓存与封面缓存的占用与清理",
                    enabled = true,
                    onClick = onOpenCache,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.PlaylistPlay,
                    title = "任务队列",
                    subtitle = "服务器后台任务（扫描/分析/清空回收站）",
                    enabled = true,
                    onClick = onOpenTasks,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Label,
                    title = "分类管理",
                    subtitle = "书库分类的新建、重命名、删除与排序",
                    enabled = true,
                    onClick = onOpenCategories,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.FilterList,
                    title = "过滤组",
                    subtitle = "保存的书库筛选规则集",
                    enabled = true,
                    onClick = onOpenFilterGroups,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "服务器设置",
                    subtitle = "书库更新、下载、计划备份与缓存上限",
                    enabled = true,
                    onClick = onOpenServerSettings,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "本地回收站",
                    subtitle = "恢复或彻底删除被回收的本地漫画与章节",
                    enabled = true,
                    onClick = onOpenTrash,
                )
            }
            item { SectionHeader("关于") }
            item {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "关于",
                    subtitle = "Sumidoku Android（M5 构建中）",
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun AppearanceRow(viewModel: AppearanceViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    ListItem(
        leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
        headlineContent = { Text("主题") },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "dark" to "深色", "light" to "浅色").forEach { (value, label) ->
                    FilterChip(
                        selected = theme == value,
                        onClick = { viewModel.setTheme(value) },
                        label = { Text(label) },
                    )
                }
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 无痕阅读开关：webui 的常规组设置，放在首页而不是服务器设置里；
 *  开/关立即 PUT 服务器设置（详情页仍可按单部覆盖）。 */
@Composable
private fun IncognitoRow(viewModel: IncognitoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    ListItem(
        leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
        headlineContent = { Text("无痕阅读") },
        supportingContent = {
            Text(
                state.error ?: "全局暂停记录阅读历史；漫画详情页可按单部覆盖",
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Switch(
                    checked = state.enabled,
                    enabled = !state.busy,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
