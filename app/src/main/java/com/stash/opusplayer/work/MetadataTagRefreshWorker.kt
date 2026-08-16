package com.stash.opusplayer.work

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.opusplayer.data.database.MusicDatabase
import com.stash.opusplayer.data.database.toSong
import com.stash.opusplayer.utils.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Periodically re-reads embedded ID3/Vorbis tags for a rotating batch of
 * already-indexed songs, ported from Lumisound's
 * LibraryManager+PeriodicMetadataRefresh.swift: catches tags edited by
 * another app (a desktop tagger, a re-download) without needing a full
 * manual rescan. A cursor persisted in `SharedPreferences` advances by
 * [BATCH_SIZE] each run and wraps around, so a full pass over the library
 * takes several runs rather than one expensive sweep -- same rotating-batch
 * shape as the Swift original's `metadataRefreshCursor`, adapted from its
 * in-process 3-minute `Timer` to `WorkManager`'s periodic scheduling since
 * Android gives no guarantee the process is still alive to run a `Timer`.
 *
 * Only touches title/artist/album/genre/year/track -- the fields
 * [MetadataExtractor.extractMetadata] already falls back to the existing
 * value for when a tag can't be read, so a failed read never blanks out a
 * field this pass couldn't see. Deliberately skips artwork/bitrate, which
 * `MetadataExtractor` also extracts but which this pass has no reason to
 * pay for on every rotation -- artwork already has its own dedicated
 * `AutoEmbedWorker`.
 */
class MetadataTagRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "metadata-tag-refresh-periodic"
        private const val PREF_CURSOR = "metadata_tag_refresh_cursor"
        private const val BATCH_SIZE = 200

        fun schedulePeriodic(context: Context) {
            val constraintsBuilder = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                constraintsBuilder.setRequiresDeviceIdle(true)
            }
            val request = PeriodicWorkRequestBuilder<MetadataTagRefreshWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraintsBuilder.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val songDao = MusicDatabase.getDatabase(applicationContext).songDao()
            val allSongs = songDao.getAllSongs()
            if (allSongs.isEmpty()) return@withContext Result.success()

            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val storedCursor = prefs.getInt(PREF_CURSOR, 0)
            val cursor = if (storedCursor >= allSongs.size) 0 else storedCursor
            val batch = allSongs.drop(cursor).take(BATCH_SIZE).ifEmpty { allSongs.take(BATCH_SIZE) }

            val extractor = MetadataExtractor(applicationContext)
            for (entity in batch) {
                val refreshed = extractor.extractMetadata(entity.toSong(), forceArtworkExtraction = false)
                val merged = entity.copy(
                    title = refreshed.title.ifBlank { entity.title },
                    artist = refreshed.artist.ifBlank { entity.artist },
                    album = refreshed.album.ifBlank { entity.album },
                    genre = refreshed.genre.ifBlank { entity.genre },
                    year = refreshed.year.ifBlank { entity.year },
                    track = if (refreshed.track > 0) refreshed.track else entity.track
                )
                if (merged != entity) {
                    songDao.insertSong(merged)
                }
            }

            val nextCursor = (cursor + batch.size).let { if (it >= allSongs.size) 0 else it }
            prefs.edit().putInt(PREF_CURSOR, nextCursor).apply()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
