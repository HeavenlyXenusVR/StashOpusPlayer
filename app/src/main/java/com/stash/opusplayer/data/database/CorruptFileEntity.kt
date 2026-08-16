package com.stash.opusplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A song currently flagged by [com.stash.opusplayer.work.CorruptFileFinderWorker]
 * (see [com.stash.opusplayer.library.AudioFileValidator] for what gets checked).
 * Keyed by `songId` so a re-scan can upsert in place; a song that passes validation
 * again is removed from this table entirely rather than marked resolved, so its
 * presence here always means "flagged as of `flaggedAt`," never stale history.
 */
@Entity(tableName = "corrupt_files")
data class CorruptFileEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val path: String,
    val sizeBytes: Long,
    val reason: String,
    val flaggedAt: Long
)
