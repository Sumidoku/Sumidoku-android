package org.sumidoku.android.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.sumidoku.android.core.data.db.ServerProfileDao
import org.sumidoku.android.core.data.db.SumidokuDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SumidokuDatabase =
        Room.databaseBuilder(context, SumidokuDatabase::class.java, "sumidoku.db").build()

    @Provides
    fun provideServerProfileDao(db: SumidokuDatabase): ServerProfileDao = db.serverProfileDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("sumidoku")
        }
}
