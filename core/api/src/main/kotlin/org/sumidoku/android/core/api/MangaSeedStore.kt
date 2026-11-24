package org.sumidoku.android.core.api

import java.util.concurrent.ConcurrentHashMap
import org.sumidoku.android.core.api.model.SearchManga

/** In-memory handoff of the browse entry the user just tapped. Aidoku
 *  parity: the detail page renders the entry's title/cover instantly while
 *  registration streams in the background — no waiting spinner. Both the
 *  source feature (writer) and the manga feature (reader) depend on
 *  core:api, so the store lives here. Entries are single-use ([take])
 *  and only written for unregistered manga (id == null). */
object MangaSeedStore {
    private val seeds = ConcurrentHashMap<String, SearchManga>()

    private fun key(sourceId: Long, mangaKey: String) = "$sourceId|$mangaKey"

    fun put(sourceId: Long, entry: SearchManga) {
        seeds[key(sourceId, entry.key)] = entry
    }

    fun take(sourceId: Long, mangaKey: String): SearchManga? = seeds.remove(key(sourceId, mangaKey))
}
