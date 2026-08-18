package com.stash.opusplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BpmCacheDao {

    @Query("SELECT * FROM bpm_cache WHERE songId = :songId")
    suspend fun get(songId: Long): BpmCacheEntity?

    @Query("SELECT * FROM bpm_cache")
    suspend fun getAll(): List<BpmCacheEntity>

    @Query("SELECT * FROM bpm_cache")
    fun getAllFlow(): Flow<List<BpmCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BpmCacheEntity)

    @Query("DELETE FROM bpm_cache WHERE songId = :songId")
    suspend fun deleteBySongId(songId: Long)

    @Query("DELETE FROM bpm_cache WHERE songId NOT IN (:keepSongIds)")
    suspend fun deleteAllExcept(keepSongIds: List<Long>)
}
