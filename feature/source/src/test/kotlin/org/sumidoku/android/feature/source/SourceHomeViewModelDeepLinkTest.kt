package org.sumidoku.android.feature.source

import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sumidoku.android.core.api.model.FilterValue
import org.sumidoku.android.core.api.model.FilterValueOneOf2Check
import org.sumidoku.android.core.data.ActiveProfileStore
import org.sumidoku.android.core.data.CatalogCache
import org.sumidoku.android.core.data.LibraryDisplayStore
import org.sumidoku.android.core.data.ServerProfileRepository
import org.sumidoku.android.core.data.SourceSettingsAvailabilityStore
import org.sumidoku.android.core.data.db.CatalogCacheDao
import org.sumidoku.android.core.data.db.CatalogCacheEntity
import org.sumidoku.android.core.data.db.ServerProfileDao
import org.sumidoku.android.core.data.db.ServerProfileEntity
import org.sumidoku.android.core.network.ServerReachability
import org.sumidoku.android.core.network.SumidokuClientFactory

/**
 * Deep-link construction contract (Aidoku parity): the author/tag route
 * carries `filters`, the global-search "view all" route carries `query`.
 * The very first state must already be SearchMode — home is neither fetched
 * nor painted. Regression: the m02989 build briefly showed home and never
 * reached the grid; this test pins the VM side of the chain.
 *
 * The fake DAO never emits, so refresh() suspends on the profile probe and
 * the state observed is exactly the constructor-seeded one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceHomeViewModelDeepLinkTest {

    private val dispatcher = StandardTestDispatcher()

    // The ViewModel only consults appContext when resolving error strings, and the
    // fake DAO never emits, so refresh() suspends before any error path is reached:
    // an unbounded wrapper is enough to satisfy the constructor.
    private val appContext = ContextWrapper(null)

    private class FakeDao : ServerProfileDao {
        override fun observeAll(): Flow<List<ServerProfileEntity>> = emptyFlow()
        override suspend fun insert(entity: ServerProfileEntity): Long = 1L
        override suspend fun update(entity: ServerProfileEntity) = Unit
        override suspend fun delete(entity: ServerProfileEntity) = Unit
        override suspend fun byId(id: Long): ServerProfileEntity? = null
    }

    /** No rows, no writes: the cache is never consulted by these tests. */
    private class FakeCatalogCacheDao : CatalogCacheDao {
        override suspend fun seedRows(profileId: Long, listKey: String): List<String> = emptyList()
        override suspend fun insertAll(rows: List<CatalogCacheEntity>) = Unit
        override suspend fun deleteList(profileId: Long, listKey: String) = Unit
        override suspend fun deleteProfile(profileId: Long) = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle): SourceHomeViewModel {
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { java.io.File.createTempFile("deeplink-test", ".preferences_pb") },
        )
        val repository = ServerProfileRepository(
            FakeDao(),
            ActiveProfileStore(dataStore),
            CatalogCache(FakeCatalogCacheDao()),
        )
        return SourceHomeViewModel(
            appContext = appContext,
            savedStateHandle = handle,
            repository = repository,
            clientFactory = SumidokuClientFactory(ServerReachability()),
            filterPrefs = SourceFilterPrefs(dataStore),
            availabilityStore = SourceSettingsAvailabilityStore(dataStore),
            displayStore = LibraryDisplayStore(dataStore),
        )
    }

    @Test
    fun filtersDeepLinkStartsInSearchMode() {
        // The exact encoding the navigation layer produces: kotlinx JSON of
        // the FilterValue list (URL-decoded by the time it lands here).
        val filters = listOf(FilterValue(check = FilterValueOneOf2Check("author", 1)))
        val raw = Json.encodeToString(ListSerializer(FilterValue.serializer()), filters)
        val vm = viewModel(SavedStateHandle(mapOf("sourceId" to 2L, "filters" to raw)))

        val mode = vm.state.value.mode
        assertTrue("expected SearchMode but was $mode", mode is SourceMode.SearchMode)
        assertNull((mode as SourceMode.SearchMode).query)
        assertEquals(filters, mode.filters)
        assertEquals(1, vm.state.value.activeFilters["author"]?.check?.value)
    }

    @Test
    fun queryDeepLinkStartsInSearchMode() {
        val vm = viewModel(SavedStateHandle(mapOf("sourceId" to 2L, "query" to "naruto")))

        val mode = vm.state.value.mode
        assertTrue("expected SearchMode but was $mode", mode is SourceMode.SearchMode)
        assertEquals("naruto", (mode as SourceMode.SearchMode).query)
    }

    @Test
    fun plainEntryStartsInHomeMode() {
        val vm = viewModel(SavedStateHandle(mapOf("sourceId" to 2L)))
        assertEquals(SourceMode.Home, vm.state.value.mode)
    }

    @Test
    fun corruptFiltersArgFallsBackToHomeMode() {
        val vm = viewModel(SavedStateHandle(mapOf("sourceId" to 2L, "filters" to "not-json")))
        assertEquals(SourceMode.Home, vm.state.value.mode)
    }
}
