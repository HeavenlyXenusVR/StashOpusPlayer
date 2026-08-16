package com.stash.opusplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A song staged in the in-app trash after [com.stash.opusplayer.library.RecentlyDeletedService]
 * moves its bytes into app-private storage. `id` is its own autoincrement key rather than the
 * original song's MediaStore id, since that id can in principle be reused by a later, unrelated
 * MediaStore row once the original is deleted -- this row needs to stay unambiguous on its own.
 */
@Entity(tableName = "recently_deleted")
data class RecentlyDeletedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val originalPath: String,
    val trashFileName: String,
    val sizeBytes: Long,
    val deletedAt: Long
)
