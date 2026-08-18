package com.stash.opusplayer.library

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.stash.opusplayer.data.database.RecentlyDeletedDao
import com.stash.opusplayer.data.database.RecentlyDeletedEntity
import com.stash.opusplayer.data.database.SongDao
import com.stash.opusplayer.data.database.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLConnection
import java.util.UUID

/**
 * In-app trash, ported from Lumisound's RecentlyDeletedService
 * (ios/Lumisound/Sources/Services/RecentlyDeletedService.swift). A deleted
 * song's bytes are copied into a hidden app-private folder (Android's
 * scoped-storage analogue of Lumisound's dot-prefixed `Documents/.RecentlyDeleted/`)
 * before the original is actually removed, and can be restored back into
 * public storage (`Music/Restored`) for up to [RETENTION_DAYS].
 *
 * Deliberately split into [copyToTrash] (pure local copy, no deletion) and
 * [finalize]/[discard] (called only after the caller has separately
 * confirmed the OS actually deleted the original -- see
 * `MainActivity.requestMediaDelete`/`trashSong`) so a copy is never staged
 * as "deleted" while the real file still sits in the user's library. This
 * two-step shape has no equivalent need in Lumisound, where deleting an
 * app-sandboxed file needs no separate OS confirmation at all.
 */
object RecentlyDeletedService {

    private const val TRASH_DIR = ".recently_deleted"
    private const val RETENTION_DAYS = 30L

    fun trashDir(context: Context): File =
        File(context.filesDir, TRASH_DIR).apply { mkdirs() }

    suspend fun copyToTrash(context: Context, song: SongEntity): File? = withContext(Dispatchers.IO) {
        val uri = AudioFileValidator.contentUriFor(song)
        val extension = song.path.substringAfterLast('.', "").ifBlank { "audio" }
        val dest = File(trashDir(context), "${UUID.randomUUID()}.$extension")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source -> dest.outputStream().use { output -> source.copyTo(output) } }
            dest
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    suspend fun finalize(
        context: Context,
        song: SongEntity,
        trashFile: File,
        dao: RecentlyDeletedDao,
        songDao: SongDao
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            RecentlyDeletedEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                originalPath = song.path,
                trashFileName = trashFile.name,
                sizeBytes = trashFile.length(),
                deletedAt = System.currentTimeMillis()
            )
        )
        songDao.deleteById(song.id)
        Unit
    }

    fun discard(trashFile: File) {
        trashFile.delete()
    }

    /** Copies the trashed file back into public storage under `Music/Restored` and drops the trash entry. */
    suspend fun restore(context: Context, entry: RecentlyDeletedEntity, dao: RecentlyDeletedDao): Boolean =
        withContext(Dispatchers.IO) {
            val source = File(trashDir(context), entry.trashFileName)
            if (!source.exists()) {
                dao.deleteById(entry.id)
                return@withContext false
            }

            val displayName = File(entry.originalPath).name.ifBlank { "${entry.title}.${source.extension}" }
            val mimeType = URLConnection.guessContentTypeFromName(displayName) ?: "audio/*"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Restored")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val newUri: Uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            val wrote = try {
                resolver.openOutputStream(newUri)?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                }
                true
            } catch (e: Exception) {
                false
            }

            if (!wrote) {
                resolver.delete(newUri, null, null)
                return@withContext false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val clearPending = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                resolver.update(newUri, clearPending, null, null)
            }

            source.delete()
            dao.deleteById(entry.id)
            true
        }

    suspend fun purgeForever(context: Context, entry: RecentlyDeletedEntity, dao: RecentlyDeletedDao) =
        withContext(Dispatchers.IO) {
            File(trashDir(context), entry.trashFileName).delete()
            dao.deleteById(entry.id)
            Unit
        }

    suspend fun purgeAll(context: Context, dao: RecentlyDeletedDao) = withContext(Dispatchers.IO) {
        dao.getAll().forEach { File(trashDir(context), it.trashFileName).delete() }
        dao.deleteAll()
    }

    /** Sweeps anything past [RETENTION_DAYS] -- called once at app launch, mirroring the Swift original. */
    suspend fun purgeExpired(context: Context, dao: RecentlyDeletedDao) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1000
        dao.getOlderThan(cutoff).forEach { File(trashDir(context), it.trashFileName).delete() }
        dao.deleteOlderThan(cutoff)
    }

    fun daysRemaining(entry: RecentlyDeletedEntity): Long {
        val elapsedDays = (System.currentTimeMillis() - entry.deletedAt) / (24L * 60 * 60 * 1000)
        return (RETENTION_DAYS - elapsedDays).coerceAtLeast(0)
    }
}
