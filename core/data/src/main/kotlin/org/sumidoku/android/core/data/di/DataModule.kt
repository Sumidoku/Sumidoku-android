package org.sumidoku.android.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.sumidoku.android.core.data.db.CatalogCacheDao
import org.sumidoku.android.core.data.db.ServerProfileDao
import org.sumidoku.android.core.data.db.SumidokuDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SumidokuDatabase =
        Room.databaseBuilder(context, SumidokuDatabase::class.java, "sumidoku.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideServerProfileDao(db: SumidokuDatabase): ServerProfileDao = db.serverProfileDao()

    @Provides
    fun provideCatalogCacheDao(db: SumidokuDatabase): CatalogCacheDao = db.catalogCacheDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("sumidoku")
        }

    /** v2 adds the generic catalog_cache table; the profiles table is untouched. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalog_cache` (" +
                    "`profileId` INTEGER NOT NULL, " +
                    "`listKey` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "`payloadJson` TEXT NOT NULL, " +
                    "PRIMARY KEY(`profileId`, `listKey`, `position`))",
            )
        }
    }
}
