package org.sumidoku.android.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CatalogCacheDao {

    @Query(
        "SELECT payloadJson FROM catalog_cache " +
            "WHERE profileId = :profileId AND listKey = :listKey ORDER BY position ASC",
    )
    suspend fun seedRows(profileId: Long, listKey: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CatalogCacheEntity>)

    @Query("DELETE FROM catalog_cache WHERE profileId = :profileId AND listKey = :listKey")
    suspend fun deleteList(profileId: Long, listKey: String)

    /** Replace = the server response is the whole truth of the list: stale rows
     *  (items deleted server-side, or a now-empty page) must not survive. */
    @Transaction
    suspend fun replace(profileId: Long, listKey: String, rows: List<CatalogCacheEntity>) {
        deleteList(profileId, listKey)
        insertAll(rows)
    }

    /** A deleted profile never queries its rows again; drop them eagerly. */
    @Query("DELETE FROM catalog_cache WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: Long)
}
