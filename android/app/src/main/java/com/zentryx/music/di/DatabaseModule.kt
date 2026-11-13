package com.zentryx.music.di

import android.content.Context
import androidx.room.Room
import com.zentryx.music.database.ZentryxDatabase
import com.zentryx.music.database.dao.FolderSyncDao
import com.zentryx.music.database.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideZentryxDatabase(
        @ApplicationContext context: Context
    ): ZentryxDatabase {
        return Room.databaseBuilder(
            context,
            ZentryxDatabase::class.java,
            ZentryxDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSongDao(database: ZentryxDatabase): SongDao = database.songDao()

    @Provides
    @Singleton
    fun provideFolderSyncDao(database: ZentryxDatabase): FolderSyncDao = database.folderSyncDao()
}
