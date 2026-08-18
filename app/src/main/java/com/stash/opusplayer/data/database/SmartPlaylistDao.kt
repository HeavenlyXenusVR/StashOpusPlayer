package com.stash.opusplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: SmartPlaylistEntity): Long

    @Query("SELECT * FROM smart_playlists ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SmartPlaylistEntity>>

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun deleteById(id: Long)
}
