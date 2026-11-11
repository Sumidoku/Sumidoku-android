package org.sumidoku.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.feature.browse.AddSourceScreen
import org.sumidoku.android.feature.browse.BrowseScreen
import org.sumidoku.android.feature.library.LibraryScreen
import org.sumidoku.android.feature.manga.MangaScreen
import org.sumidoku.android.feature.reader.ReaderScreen
import org.sumidoku.android.feature.servers.ServersScreen
import org.sumidoku.android.ui.theme.SumidokuTheme
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    repository: ServerProfileRepository,
) : ViewModel() {
    /** null while loading; false = no server configured yet. */
    val hasServer: StateFlow<Boolean?> = repository.activeProfile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("library", "书库", Icons.Outlined.CollectionsBookmark),
    Tab("browse", "浏览", Icons.Outlined.Explore),
    Tab("updates", "更新", Icons.Outlined.NewReleases),
    Tab("history", "历史", Icons.Outlined.History),
    Tab("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun SumidokuRoot(appViewModel: AppViewModel = hiltViewModel()) {
    SumidokuTheme {
        val hasServer by appViewModel.hasServer.collectAsState()
        when (hasServer) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            else -> {
                val nav = rememberNavController()
                NavHost(
                    navController = nav,
                    startDestination = if (hasServer == true) "main" else "servers",
                ) {
                    composable("servers") {
                        ServersScreen(onServerSelected = {
                            nav.navigate("main") { popUpTo("servers") { inclusive = true } }
                        })
                    }
                    composable("main") {
                        MainTabs(onOpenReader = { mangaId, chapterId ->
                            nav.navigate("reader/$mangaId/$chapterId")
                        })
                    }
                    composable(
                        route = "reader/{mangaId}/{chapterId}",
                        arguments = listOf(
                            navArgument("mangaId") { type = NavType.LongType },
                            navArgument("chapterId") { type = NavType.LongType },
                        ),
                    ) {
                        // 阅读器刻意是外层真路由：全屏沉浸，退出落回双栏/标签壳。
                        ReaderScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(onOpenReader: (mangaId: Long, chapterId: Long) -> Unit) {
    val innerNav = rememberNavController()
    val backStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            innerNav.navigate(tab.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = innerNav,
            startDestination = "library",
            modifier = Modifier.padding(padding),
        ) {
            composable("library") {
                LibraryScreen(onOpenManga = { innerNav.navigate("manga/$it") })
            }
            composable(
                route = "manga/{mangaId}",
                arguments = listOf(navArgument("mangaId") { type = NavType.LongType }),
            ) {
                MangaScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenChapter = onOpenReader,
                )
            }
            composable("browse") {
                BrowseScreen(onAddSource = { innerNav.navigate("add-source") })
            }
            composable("add-source") {
                AddSourceScreen(onBack = { innerNav.popBackStack() })
            }
            composable("updates") { PlaceholderTab("更新 · M3") }
            composable("history") { PlaceholderTab("历史 · M3") }
            composable("settings") { PlaceholderTab("设置 · M5+") }
        }
    }
}

@Composable
private fun PlaceholderTab(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
