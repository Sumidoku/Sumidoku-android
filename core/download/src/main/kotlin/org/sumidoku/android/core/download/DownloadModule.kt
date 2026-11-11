package org.sumidoku.android.core.download

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideLocalDownloadDatabase(
        @ApplicationContext context: Context,
    ): LocalDownloadDatabase =
        Room.databaseBuilder(context, LocalDownloadDatabase::class.java, "sumidoku-local-downloads.db")
            .build()
}
