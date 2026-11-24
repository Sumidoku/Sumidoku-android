package org.sumidoku.android.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent "does this source have a settings schema" judgment, shared by
 *  the browse list and the source home page. Keyed by profile+source and
 *  invalidated by the source's own version: the probe re-runs only after the
 *  extension updates, so opening the app or the source costs no request once
 *  the answer is known. Probe failures are never cached. */
@Singleton
class SourceSettingsAvailabilityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("source_settings_availability")

    /** profileId:sourceId -> "version|1" / "version|0". The format is flat
     *  text (no JSON) so a hand-edited or truncated payload degrades to
     *  "unknown" per entry instead of poisoning the whole map. */
    suspend fun load(): Map<String, String> {
        val raw = dataStore.data.first()[key] ?: return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val i = entry.indexOf('=')
            if (i <= 0) null else entry.substring(0, i) to entry.substring(i + 1)
        }.toMap()
    }

    /** Cached judgment, or null when absent or stale (version changed). */
    fun lookup(cache: Map<String, String>, profileId: Long, sourceId: Long, version: Long): Boolean? {
        val raw = cache["$profileId:$sourceId"] ?: return null
        val sep = raw.lastIndexOf('|')
        if (sep <= 0) return null
        if (raw.substring(0, sep).toLongOrNull() != version) return null
        return raw.substring(sep + 1) == "1"
    }

    /** Merge probe results into the loaded cache. `results` holds
     *  (sourceId, version, hasSettings) triples of successful probes only. */
    suspend fun save(cache: Map<String, String>, profileId: Long, results: List<Triple<Long, Long, Boolean>>) {
        if (results.isEmpty()) return
        val updated = cache.toMutableMap()
        results.forEach { (sourceId, version, has) ->
            updated["$profileId:$sourceId"] = "$version|${if (has) 1 else 0}"
        }
        dataStore.edit { prefs ->
            prefs[key] = updated.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }
}
