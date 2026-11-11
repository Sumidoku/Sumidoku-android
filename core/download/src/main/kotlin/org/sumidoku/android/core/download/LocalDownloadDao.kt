package org.sumidoku.android.core.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalDownloadEntity)

    @Query("SELECT * FROM local_downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalDownloadEntity>>

    @Query("SELECT * FROM local_downloads WHERE mangaId = :mangaId")
    fun observeForManga(mangaId: Long): Flow<List<LocalDownloadEntity>>

    @Query("SELECT * FROM local_downloads WHERE chapterId = :chapterId")
    suspend fun get(chapterId: Long): LocalDownloadEntity?

    @Query("UPDATE local_downloads SET status = :status, error = :error, bytes = :bytes, path = :path WHERE chapterId = :chapterId")
    suspend fun updateState(chapterId: Long, status: String, error: String?, bytes: Long, path: String)

    @Query("UPDATE local_downloads SET status = 'queued' WHERE status = 'downloading'")
    suspend fun requeueInterrupted()

    @Query("SELECT * FROM local_downloads WHERE status = 'queued' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): LocalDownloadEntity?

    @Query("DELETE FROM local_downloads WHERE chapterId = :chapterId")
    suspend fun delete(chapterId: Long)

    @Query("DELETE FROM local_downloads")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM local_downloads WHERE status = 'done'")
    suspend fun totalBytes(): Long
}
