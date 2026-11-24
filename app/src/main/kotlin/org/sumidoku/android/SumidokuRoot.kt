package org.sumidoku.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.sumidoku.android.core.api.model.FilterValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.sumidoku.android.core.api.model.ProgressPatch
import retrofit2.await
import androidx.lifecycle.viewModelScope
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.feature.browse.AddSourceScreen
import org.sumidoku.android.feature.backups.BackupsScreen
import org.sumidoku.android.feature.browse.BrowseScreen
import org.sumidoku.android.feature.downloads.DownloadsScreen
import org.sumidoku.android.feature.history.HistoryScreen
import org.sumidoku.android.feature.library.LibraryScreen
import org.sumidoku.android.feature.manga.MangaScreen
import org.sumidoku.android.feature.migrate.MigrateScreen
import org.sumidoku.android.feature.reader.ReaderDefaultsScreen
import org.sumidoku.android.feature.reader.ReaderScreen
import org.sumidoku.android.feature.repos.ReposScreen
import org.sumidoku.android.feature.search.SearchScreen
import org.sumidoku.android.feature.servers.ServersScreen
import org.sumidoku.android.feature.settings.ApiKeysScreen
import org.sumidoku.android.feature.settings.CacheScreen
import org.sumidoku.android.feature.settings.CategoriesScreen
import org.sumidoku.android.feature.settings.FilterGroupsScreen
import org.sumidoku.android.feature.settings.ServerSettingsScreen
import org.sumidoku.android.feature.settings.SettingsScreen
import org.sumidoku.android.feature.settings.SettingsPage
import org.sumidoku.android.feature.settings.TasksScreen
import org.sumidoku.android.feature.settings.TrashScreen
import org.sumidoku.android.feature.source.SourceHomeScreen
import org.sumidoku.android.feature.source.SourceSettingsScreen
import org.sumidoku.android.feature.trackers.TrackersScreen
import org.sumidoku.android.feature.updates.UpdatesScreen
import org.sumidoku.android.ui.theme.SumidokuTheme
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    repository: ServerProfileRepository,
    appearanceStore: org.sumidoku.android.core.data.AppearanceStore,
    private val offlineProgressStore: org.sumidoku.android.core.data.OfflineProgressStore,
    private val clientFactory: org.sumidoku.android.core.network.SumidokuClientFactory,
) : ViewModel() {
    /** null while loading; false = no server configured yet. */
    val hasServer: StateFlow<Boolean?> = repository.activeProfile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Appearance preference (M9 theming): system | dark | light. */
    val theme: StateFlow<String> = appearanceStore.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    init {
        // M2 offline progress queue: replay progress writes captured
        // while the server was unreachable. Best-effort — failed
        // entries stay queued for the next start.
        viewModelScope.launch {
            val profile = repository.activeProfile.filterNotNull().first()
            val api = clientFactory.create<org.sumidoku.android.core.api.MangaApi>(profile)
            offlineProgressStore.pending.first().forEach { entry ->
                try {
                    api.updateChapterProgress(
                        entry.chapterId,
                        ProgressPatch(
                            lastPageRead = entry.lastPageRead.toLong(),
                            isRead = if (entry.isRead) true else null,
                        ),
                    ).await()
                    offlineProgressStore.remove(setOf(entry.chapterId))
                } catch (_: Exception) {
                    // stays queued
                }
            }
        }
    }
}

private data class Tab(val route: String, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("library", R.string.tab_library, Icons.Outlined.CollectionsBookmark),
    Tab("browse", R.string.tab_browse, Icons.Outlined.Explore),
    Tab("updates", R.string.tab_updates, Icons.Outlined.NewReleases),
    Tab("history", R.string.tab_history, Icons.Outlined.History),
    // komikku parity: secondary destinations live behind "More" instead of
    // crowding the bar (5 tabs max, Material guidance).
    Tab("more", R.string.tab_more, Icons.Outlined.MoreHoriz),
)

@Composable
fun SumidokuRoot(appViewModel: AppViewModel = hiltViewModel()) {
    val themePref by appViewModel.theme.collectAsState()
    SumidokuTheme(themePref = themePref) {
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
                        MainTabs(
                            onOpenReader = { mangaId, chapterId ->
                                nav.navigate("reader/$mangaId/$chapterId")
                            },
                            onManageServers = { nav.navigate("servers") },
                        )
                    }
                    composable(
                        route = "reader/{mangaId}/{chapterId}",
                        arguments = listOf(
                            navArgument("mangaId") { type = NavType.LongType },
                            navArgument("chapterId") { type = NavType.LongType },
                        ),
                    ) {
                        // The reader is deliberately an outer real route: fully
                        // immersive, and exiting lands back on the two-pane/tab shell.
                        ReaderScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    onManageServers: () -> Unit,
) {
    val innerNav = rememberNavController()
    val backStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo(),
    )
    // Manual scaffold instead of NavigationSuiteScaffoldLayout: the layout
    // mismeasures a custom navigationSuite (the rail ended up centred
    // horizontally with a zero-width content area). A plain Row/Column is
    // deterministic — komikku does the same with its own rail composable.
    val navigationSuite: @Composable () -> Unit = {
            val navigateTo: (Tab) -> Unit = { tab ->
                innerNav.navigate(tab.route) {
                    popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            if (layoutType == NavigationSuiteType.NavigationBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigateTo(tab) },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            } else {
                // komikku parity (its custom NavigationRail does the same):
                // rail items centred vertically instead of pinned to the top.
                NavigationRail {
                    Column(
                        // No fillMaxWidth here: under the rail's loose
                        // measurement it would stretch the whole rail to the
                        // parent's full width and starve the content pane.
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTo(tab) },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            }
        }

    val navContent: @Composable () -> Unit = {
        NavHost(
            navController = innerNav,
            startDestination = "library",
        ) {
            // Filtered browse (webui /browse/{id}?filters=): a source home
            // opened straight into FilteredMode with the tapped creator/tag filter.
            val openFilteredSearch: (Long, List<FilterValue>) -> Unit = { sourceId, filters ->
                val encoded = Uri.encode(
                    Json.encodeToString(
                        ListSerializer(FilterValue.serializer()),
                        filters,
                    ),
                )
                innerNav.navigate("source-filtered/$sourceId?filters=$encoded")
            }
            composable("library") {
                LibraryTab(
                    onOpenManga = { innerNav.navigate("manga/$it") },
                    onBrowseSources = { innerNav.navigate("browse") },
                )
            }
            composable(
                // sourceId/mangaKey carry an unregistered source entry:
                // the page opens instantly (Aidoku parity) and the view
                // model registers it in the background (mangaId == -1).
                route = "manga/{mangaId}?sourceId={sourceId}&mangaKey={mangaKey}",
                arguments = listOf(
                    navArgument("mangaId") { type = NavType.LongType },
                    navArgument("sourceId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("mangaKey") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                MangaScreen(
                    mangaId = entry.arguments?.getLong("mangaId") ?: -1L,
                    sourceId = entry.arguments?.getLong("sourceId") ?: -1L,
                    mangaKey = entry.arguments?.getString("mangaKey"),
                    onBack = { innerNav.popBackStack() },
                    onOpenChapter = onOpenReader,
                    onFilterSearch = openFilteredSearch,
                )
            }
            composable("browse") {
                BrowseScreen(
                    onOpenSource = { innerNav.navigate("source/$it") },
                    onOpenSourceSettings = { innerNav.navigate("source-settings/$it") },
                    onAddSource = { innerNav.navigate("add-source") },
                    onMigrate = { innerNav.navigate("migrate") },
                    onSearch = { innerNav.navigate("search") },
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenManga = { innerNav.navigate("manga/$it") },
                    onOpenSource = { sourceId, query ->
                        // webui /browse/{id}?q= — the source-scoped grid,
                        // pre-filled with the aggregate query.
                        val encoded = if (query.isBlank()) "" else "?query=${Uri.encode(query)}"
                        innerNav.navigate("source/$sourceId$encoded")
                    },
                )
            }
            composable(
                route = "source-filtered/{sourceId}?filters={filters}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.LongType },
                    navArgument("filters") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val sourceId = entry.arguments?.getLong("sourceId") ?: return@composable
                // The filters argument is consumed by SourceHomeViewModel via
                // SavedStateHandle: the screen lands directly on the grid.
                SourceHomeScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenManga = { id, key ->
                        innerNav.navigate(openMangaRoute(sourceId, id, key))
                    },
                    onOpenSettings = { innerNav.navigate("source-settings/$sourceId") },
                )
            }
            composable("add-source") {
                AddSourceScreen(onBack = { innerNav.popBackStack() })
            }
            composable(
                route = "source/{sourceId}?query={query}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.LongType },
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val sourceId = entry.arguments?.getLong("sourceId") ?: return@composable
                SourceHomeScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenManga = { id, key ->
                        innerNav.navigate(openMangaRoute(sourceId, id, key))
                    },
                    onOpenSettings = { innerNav.navigate("source-settings/$sourceId") },
                    initialQuery = entry.arguments?.getString("query"),
                )
            }
            composable(
                route = "source-settings/{sourceId}",
                arguments = listOf(navArgument("sourceId") { type = NavType.LongType }),
            ) {
                SourceSettingsScreen(onBack = { innerNav.popBackStack() })
            }
            composable("repos") {
                ReposScreen(onBack = { innerNav.popBackStack() })
            }
            composable("updates") {
                UpdatesScreen(
                    onOpenManga = { innerNav.navigate("manga/$it") },
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenCache = { innerNav.navigate("cache") },
                )
            }
            composable("history") {
                HistoryScreen(
                    onOpenManga = { innerNav.navigate("manga/$it") },
                    onOpenChapter = { mangaId, chapterId ->
                        innerNav.navigate("reader/$mangaId/$chapterId")
                    },
                )
            }
            composable("more") {
                MoreScreen(
                    onOpenDownloads = { innerNav.navigate("downloads") },
                    onOpenSettings = { innerNav.navigate("settings") },
                    onOpenCategories = { innerNav.navigate("categories") },
                    onOpenTrackers = { innerNav.navigate("trackers") },
                    onOpenBackups = { innerNav.navigate("backups") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenRepos = { innerNav.navigate("repos") },
                    onManageServers = onManageServers,
                    onOpenTrackers = { innerNav.navigate("trackers") },
                    onOpenBackups = { innerNav.navigate("backups") },
                    onOpenApiKeys = { innerNav.navigate("api-keys") },
                    onOpenCache = { innerNav.navigate("cache") },
                    onOpenTasks = { innerNav.navigate("tasks") },
                    onOpenTrash = { innerNav.navigate("trash") },
                    onOpenCategories = { innerNav.navigate("categories") },
                    onOpenFilterGroups = { innerNav.navigate("filter-groups") },
                    onOpenServerSettings = { innerNav.navigate("server-settings") },
                    onOpenReaderDefaults = { innerNav.navigate("reader-defaults") },
                    // Wide tablet: hub stays on the left, the chosen page
                    // renders in the right pane (KMReader settings split).
                    splitDetail = { page, onDismiss, onNavigate ->
                        when (page) {
                            SettingsPage.ReaderDefaults -> ReaderDefaultsScreen(onBack = onDismiss)
                            SettingsPage.Repos -> ReposScreen(onBack = onDismiss)
                            SettingsPage.Servers -> ServersScreen(onServerSelected = onDismiss)
                            SettingsPage.Trackers -> TrackersScreen(onBack = onDismiss)
                            SettingsPage.Backups -> BackupsScreen(onBack = onDismiss)
                            SettingsPage.ApiKeys -> ApiKeysScreen(onBack = onDismiss)
                            SettingsPage.Cache -> CacheScreen(onBack = onDismiss)
                            SettingsPage.Tasks -> TasksScreen(onBack = onDismiss)
                            SettingsPage.Categories -> CategoriesScreen(onBack = onDismiss)
                            SettingsPage.FilterGroups -> FilterGroupsScreen(onBack = onDismiss)
                            SettingsPage.ServerSettings -> ServerSettingsScreen(
                                onBack = onDismiss,
                                onOpenBackups = { onNavigate(SettingsPage.Backups) },
                            )
                            SettingsPage.Trash -> TrashScreen(onBack = onDismiss)
                        }
                    },
                )
            }
            composable("categories") {
                CategoriesScreen(onBack = { innerNav.popBackStack() })
            }
            composable("filter-groups") {
                FilterGroupsScreen(onBack = { innerNav.popBackStack() })
            }
            composable("server-settings") {
                ServerSettingsScreen(
                    onBack = { innerNav.popBackStack() },
                    onOpenBackups = { innerNav.navigate("backups") },
                )
            }
            composable("trash") {
                TrashScreen(onBack = { innerNav.popBackStack() })
            }
            composable("api-keys") {
                ApiKeysScreen(onBack = { innerNav.popBackStack() })
            }
            composable("cache") {
                CacheScreen(onBack = { innerNav.popBackStack() })
            }
            composable("reader-defaults") {
                ReaderDefaultsScreen(onBack = { innerNav.popBackStack() })
            }
            composable("tasks") {
                TasksScreen(onBack = { innerNav.popBackStack() })
            }
            composable("backups") {
                BackupsScreen(onBack = { innerNav.popBackStack() })
            }
            composable("trackers") {
                TrackersScreen(onBack = { innerNav.popBackStack() })
            }
            composable("migrate") {
                MigrateScreen(onBack = { innerNav.popBackStack() })
            }
        }
    }

    if (layoutType == NavigationSuiteType.NavigationBar) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { navContent() }
            navigationSuite()
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            navigationSuite()
            Box(Modifier.weight(1f)) { navContent() }
        }
    }
}

/** Detail-page route for a source entry: registered ids navigate directly;
 *  unregistered entries (id == null) open instantly with their source key
 *  and the detail view model registers them in the background (Aidoku
 *  open-first parity). Keys are URL-encoded: they can carry /, #, %. */
private fun openMangaRoute(sourceId: Long, id: Long?, key: String): String =
    if (id != null) {
        "manga/$id"
    } else {
        "manga/-1?sourceId=$sourceId&mangaKey=" + Uri.encode(key)
    }

@Composable
private fun PlaceholderTab(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Library tab host — design 31: one full-width adaptive grid on every window
 *  size. The tablet split view is gone on purpose; tapping a cover opens the
 *  detail as a full page, exactly like the phone layout. */
@Composable
private fun LibraryTab(
    onOpenManga: (Long) -> Unit,
    onBrowseSources: () -> Unit,
) {
    LibraryScreen(
        onOpenManga = onOpenManga,
        onBrowseSources = onBrowseSources,
    )
}
