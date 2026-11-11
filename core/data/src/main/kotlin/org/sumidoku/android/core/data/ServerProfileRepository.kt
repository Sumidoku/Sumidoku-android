package org.sumidoku.android.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.sumidoku.android.core.data.db.ServerProfileDao
import org.sumidoku.android.core.data.db.toDomain
import org.sumidoku.android.core.data.db.toEntity
import org.sumidoku.android.core.model.ServerProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerProfileRepository @Inject constructor(
    private val dao: ServerProfileDao,
    private val activeStore: ActiveProfileStore,
) {
    val profiles: Flow<List<ServerProfile>> =
        dao.observeAll().combine(activeStore.activeId) { entities, _ ->
            entities.map { it.toDomain() }
        }

    val activeId: Flow<Long?> = activeStore.activeId

    /** The active profile; falls back to the first profile when the stored
     *  selection is gone (e.g. it was deleted). Null when no profile exists. */
    val activeProfile: Flow<ServerProfile?> =
        combine(dao.observeAll(), activeStore.activeId) { entities, id ->
            val all = entities.map { it.toDomain() }
            all.firstOrNull { it.id == id } ?: all.firstOrNull()
        }

    suspend fun add(profile: ServerProfile): Long {
        val id = dao.insert(profile.copy(id = 0).toEntity())
        activeStore.setActive(id)
        return id
    }

    suspend fun update(profile: ServerProfile) = dao.update(profile.toEntity())

    suspend fun delete(profile: ServerProfile) = dao.delete(profile.toEntity())

    suspend fun setActive(id: Long) = activeStore.setActive(id)

    suspend fun get(id: Long): ServerProfile? = dao.byId(id)?.toDomain()
}
