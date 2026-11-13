package com.zentryx.music.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zentryx.music.database.entities.FolderSyncEntity

@Dao
interface FolderSyncDao {

    @Query("SELECT * FROM folder_sync WHERE folderPath = :folderPath LIMIT 1")
    suspend fun getFolderSync(folderPath: String): FolderSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolderSync(entity: FolderSyncEntity)

    @Query("DELETE FROM folder_sync WHERE folderPath = :folderPath")
    suspend fun deleteFolderSync(folderPath: String)

    @Query("SELECT * FROM folder_sync")
    suspend fun getAllFolderSyncs(): List<FolderSyncEntity>

    @Query("DELETE FROM folder_sync")
    suspend fun clearAll()
}
