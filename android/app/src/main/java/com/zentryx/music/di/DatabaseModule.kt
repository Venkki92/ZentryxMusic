package com.zentryx.music.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zentryx.music.database.ZentryxDatabase
import com.zentryx.music.database.dao.FolderSyncDao
import com.zentryx.music.database.dao.PlaylistDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'WEBDAV'")
            db.execSQL("ALTER TABLE songs ADD COLUMN sampleRateHz INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN bitsPerSample INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN bitrateKbps INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN replayGainDb REAL")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlists (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_songs (
                    playlistId TEXT NOT NULL,
                    songId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, songId)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlistId ON playlist_songs(playlistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE songs ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE songs ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
        }
    }

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSongDao(database: ZentryxDatabase): SongDao = database.songDao()

    @Provides
    @Singleton
    fun provideFolderSyncDao(database: ZentryxDatabase): FolderSyncDao = database.folderSyncDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: ZentryxDatabase): PlaylistDao = database.playlistDao()
}
