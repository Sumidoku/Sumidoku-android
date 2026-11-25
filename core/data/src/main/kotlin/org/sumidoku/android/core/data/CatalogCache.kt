package org.sumidoku.android.core.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.sumidoku.android.core.data.db.CatalogCacheDao
import org.sumidoku.android.core.data.db.CatalogCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline snapshot of the server-backed lists (library first page, history, updates),
 * one JSON row per item keyed by (profile, listKey, position). The API models are
 * already @Serializable, so rows carry the payload verbatim — no per-screen tables.
 *
 * Contract: writes happen only on a successful response (a failed refresh keeps the
 * cache untouched), an empty page replaces the slot too — it is the truth, so stale
 * rows can never be seeded — and screens seed from here only while they have nothing
 * on display; the network response replaces the seeded content as soon as it arrives.
 */
@Singleton
class CatalogCache @Inject constructor(
    private val dao: CatalogCacheDao,
) {

    // ignoreUnknownKeys so rows written by a newer app version still decode after a
    // downgrade; unparseable rows are skipped one by one instead of failing the seed.
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> save(profileId: Long, listKey: String, items: List<T>, serializer: KSerializer<T>) {
        val rows = items.mapIndexed { index, item ->
            CatalogCacheEntity(
                profileId = profileId,
                listKey = listKey,
                position = index,
                payloadJson = json.encodeToString(serializer, item),
            )
        }
        dao.replace(profileId, listKey, rows)
    }

    /** The cached list in display order; empty when the slot was never written. */
    suspend fun <T> seed(profileId: Long, listKey: String, serializer: KSerializer<T>): List<T> =
        dao.seedRows(profileId, listKey).mapNotNull { payload ->
            runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
        }

    suspend fun clearProfile(profileId: Long) = dao.deleteProfile(profileId)
}
