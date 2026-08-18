package com.stash.opusplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CorruptFileDao {

    @Query("SELECT * FROM corrupt_files ORDER BY flaggedAt DESC")
    fun getAllFlow(): Flow<List<CorruptFileEntity>>

    @Query("SELECT * FROM corrupt_files ORDER BY flaggedAt DESC")
    suspend fun getAll(): List<CorruptFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CorruptFileEntity)

    @Query("DELETE FROM corrupt_files WHERE songId = :songId")
    suspend fun deleteBySongId(songId: Long)

    @Query("DELETE FROM corrupt_files")
    suspend fun deleteAll()

    /** Self-heals the flagged list after a scan -- drops anything not in [keepSongIds]. */
    @Query("DELETE FROM corrupt_files WHERE songId NOT IN (:keepSongIds)")
    suspend fun deleteAllExcept(keepSongIds: List<Long>)
}
