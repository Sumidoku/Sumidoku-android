package org.sumidoku.android.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profile ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ServerProfileEntity>>

    @Insert
    suspend fun insert(entity: ServerProfileEntity): Long

    @Update
    suspend fun update(entity: ServerProfileEntity)

    @Delete
    suspend fun delete(entity: ServerProfileEntity)

    @Query("SELECT * FROM server_profile WHERE id = :id")
    suspend fun byId(id: Long): ServerProfileEntity?
}
