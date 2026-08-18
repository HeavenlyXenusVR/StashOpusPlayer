package com.stash.opusplayer.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    /**
     * Replaces the entire index in one transaction: clears stale rows (e.g. deleted files)
     * then inserts the freshly-scanned set. Used by rescans (see LibraryScanWorker /
     * MusicRepository.refreshSongIndex) so removed songs don't linger in the index forever.
     */
    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        deleteAll()
        insertAll(songs)
    }
}
