package org.sumidoku.android.core.download

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LocalDownloadEntity::class], version = 1, exportSchema = false)
abstract class LocalDownloadDatabase : RoomDatabase() {
    abstract fun localDownloadDao(): LocalDownloadDao
}
