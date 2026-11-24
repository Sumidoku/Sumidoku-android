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

/** Tri-state filter groups of the cross-source search view. The stored
 *  shape mirrors webui globalSearchPrefs (`{"ratings":{"0":1},…}`, 1 =
 *  include / -1 = exclude / key absent = no verdict), so the persisted
 *  JSON stays readable by the web client and vice versa. */
@Serializable
data class GlobalSearchFilters(
    val ratings: Map<String, Int> = emptyMap(),
    val langs: Map<String, Int> = emptyMap(),
    val sources: Map<String, Int> = emptyMap(),
) {
    /** Drop anything but the two valid states (legacy or hand-edited
     *  storage); webui's `clean()` does the same on load. */
    fun sanitized(): GlobalSearchFilters = GlobalSearchFilters(
        ratings = ratings.validStates(),
        langs = langs.validStates(),
        sources = sources.validStates(),
    )

    private fun Map<String, Int>.validStates(): Map<String, Int> =
        filterValues { it == INCLUDE || it == EXCLUDE }

    companion object {
        const val INCLUDE = 1
        const val EXCLUDE = -1
    }
}

/** Persists the global-search tri-state filters (design parity: webui
 *  `sumidoku.globalSearch.filters` in localStorage). One JSON string in
 *  the shared `sumidoku` preferences DataStore, like OfflineProgressStore. */
@Singleton
class GlobalSearchFilterStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val filtersKey = stringPreferencesKey("global_search_filters")
    private val json = Json { ignoreUnknownKeys = true }

    /** Broken JSON counts as "nothing saved" and never blocks the screen. */
    suspend fun load(): GlobalSearchFilters {
        val raw = dataStore.data.first()[filtersKey] ?: return GlobalSearchFilters()
        return runCatching {
            json.decodeFromString(GlobalSearchFilters.serializer(), raw)
        }.getOrDefault(GlobalSearchFilters()).sanitized()
    }

    suspend fun save(filters: GlobalSearchFilters) {
        val encoded = json.encodeToString(GlobalSearchFilters.serializer(), filters.sanitized())
        dataStore.edit { prefs -> prefs[filtersKey] = encoded }
    }
}
