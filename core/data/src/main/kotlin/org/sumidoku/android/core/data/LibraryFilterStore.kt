package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Every library control the toolbar carries, persisted as one JSON blob.
 *  The stored shape mirrors webui libraryFilterPrefs
 *  (`sumidoku.library.filters`: `{"sort":"added","ratings":{"1":1},…}`,
 *  1 = include / -1 = exclude / key absent = no verdict), so the JSON stays
 *  readable by the web client and vice versa. */
@Serializable
data class LibraryFilters(
    val sort: String = SORT_ADDED,
    val filters: Map<String, Int> = emptyMap(),
    val ratings: Map<String, Int> = emptyMap(),
    val sources: Map<String, Int> = emptyMap(),
    val categories: Map<String, Int> = emptyMap(),
    val authors: Map<String, Int> = emptyMap(),
    val tags: Map<String, Int> = emptyMap(),
    val group: Long? = null,
) {
    /** Drop anything but the two valid states and an unknown sort key (legacy
     *  or hand-edited storage); webui's `clean()`/`cleanSort()` do the same. */
    fun sanitized(): LibraryFilters = LibraryFilters(
        sort = if (sort in SORTS) sort else SORT_ADDED,
        filters = filters.validStates(),
        ratings = ratings.validStates(),
        sources = sources.validStates(),
        categories = categories.validStates(),
        authors = authors.validStates(),
        tags = tags.validStates(),
        group = group,
    )

    private fun Map<String, Int>.validStates(): Map<String, Int> =
        filterValues { it == INCLUDE || it == EXCLUDE }

    companion object {
        const val INCLUDE = 1
        const val EXCLUDE = -1

        const val SORT_ADDED = "added"

        /** Server whitelist for `sort` (lib.rs library sort); anything else 400s. */
        val SORTS = setOf(SORT_ADDED, "title", "lastRead", "latestChapter", "unread")
    }
}

/** Persists the library toolbar state (design parity: webui
 *  `libraryFilterPrefs` in localStorage). One JSON string in the shared
 *  `sumidoku` preferences DataStore, like GlobalSearchFilterStore. */
@Singleton
class LibraryFilterStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val filtersKey = stringPreferencesKey("library_filters")
    private val json = Json { ignoreUnknownKeys = true }

    /** Broken JSON counts as "nothing saved" and never blocks the screen. */
    suspend fun load(): LibraryFilters {
        val raw = dataStore.data.first()[filtersKey] ?: return LibraryFilters()
        return runCatching {
            json.decodeFromString(LibraryFilters.serializer(), raw)
        }.getOrDefault(LibraryFilters()).sanitized()
    }

    suspend fun save(filters: LibraryFilters) {
        val encoded = json.encodeToString(LibraryFilters.serializer(), filters.sanitized())
        dataStore.edit { prefs -> prefs[filtersKey] = encoded }
    }
}
