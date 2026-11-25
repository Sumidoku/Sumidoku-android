package org.sumidoku.android.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ServerProfileEntity::class, CatalogCacheEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SumidokuDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun catalogCacheDao(): CatalogCacheDao
}
