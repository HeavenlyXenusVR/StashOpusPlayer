package com.stash.opusplayer.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stash.opusplayer.data.Song

/**
 * Persisted song library index. This mirrors [com.stash.opusplayer.data.Song] so that
 * mapping between the two is a straight field-for-field conversion (see [toSong]/[toEntity]).
 *
 * The primary key is the MediaStore row id (`MediaStore.Audio.Media._ID`), so rescans can be
 * applied as simple upserts (`OnConflictStrategy.REPLACE`) instead of diffing rows manually.
 *
 * NOTE: this is a real, queryable song index (title/artist/album/etc. for populating the
 * library UI without hitting MediaStore every time). It is unrelated to [com.stash.opusplayer.data.MetadataInfo],
 * which is a narrower technical-scan cache (bitrate/sampleRate/codec/duration/errors) keyed by file path.
 */
@Entity(tableName = "songs", indices = [Index(value = ["path"])])
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = -1,
    val artistId: Long = -1,
    val duration: Long = 0L,
    val path: String,
    val dateAdded: Long = 0L,
    val size: Long = 0L,
    val mimeType: String = "",
    val relativePath: String = "",
    val albumArt: String? = null,
    val track: Int = 0,
    val year: String = "",
    val genre: String = "",
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val lastScanned: Long = System.currentTimeMillis()
)

/** Converts a persisted row back into the domain [Song] model used throughout the UI. */
fun SongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    path = path,
    albumId = albumId,
    artistId = artistId,
    dateAdded = dateAdded,
    size = size,
    mimeType = mimeType,
    relativePath = relativePath,
    albumArt = albumArt,
    track = track,
    year = year,
    genre = genre,
    bitrate = bitrate,
    sampleRate = sampleRate
)

/** Converts a [Song] (e.g. freshly read from MediaStore) into a row for the persisted index. */
fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    path = path,
    dateAdded = dateAdded,
    size = size,
    mimeType = mimeType,
    relativePath = relativePath,
    albumArt = albumArt,
    track = track,
    year = year,
    genre = genre,
    bitrate = bitrate,
    sampleRate = sampleRate,
    lastScanned = System.currentTimeMillis()
)
