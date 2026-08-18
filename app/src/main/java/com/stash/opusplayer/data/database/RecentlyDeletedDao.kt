package com.stash.opusplayer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyDeletedDao {

    @Query("SELECT * FROM recently_deleted ORDER BY deletedAt DESC")
    fun getAllFlow(): Flow<List<RecentlyDeletedEntity>>

    @Query("SELECT * FROM recently_deleted ORDER BY deletedAt DESC")
    suspend fun getAll(): List<RecentlyDeletedEntity>

    @Insert
    suspend fun insert(entry: RecentlyDeletedEntity): Long

    @Query("DELETE FROM recently_deleted WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recently_deleted")
    suspend fun deleteAll()

    @Query("SELECT * FROM recently_deleted WHERE deletedAt < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<RecentlyDeletedEntity>

    @Query("DELETE FROM recently_deleted WHERE deletedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
