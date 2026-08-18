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
import com.stash.opusplayer.tempo.BpmCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Periodically analyzes tempo (BPM) for a rotating batch of songs missing a
 * cached result, so [com.stash.opusplayer.mood.MoodClassifier]'s BPM tier
 * and any future BPM-aware feature (sort-by-tempo, BPM-proximity shuffle,
 * beat-matched crossfade -- all still unported, see PORTING_STATUS.md)
 * gradually gets real data without ever blocking the UI or a Mood Playlists
 * "Re-analyze" pass with a fresh decode.
 *
 * Batch size is much smaller than [MetadataTagRefreshWorker]'s -- a real
 * MediaCodec decode of up to 60s of audio per song is genuinely CPU-heavy
 * (seconds of work, not a cheap tag read), so this also requires device-idle
 * in addition to the idle/battery-not-low constraints [CorruptFileFinderWorker]
 * and [MetadataTagRefreshWorker] already established for background CPU work.
 */
class BpmAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "bpm-analysis-periodic"
        private const val PREF_CURSOR = "bpm_analysis_cursor"
        private const val BATCH_SIZE = 20

        fun schedulePeriodic(context: Context) {
            val constraintsBuilder = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                constraintsBuilder.setRequiresDeviceIdle(true)
            }
            val request = PeriodicWorkRequestBuilder<BpmAnalysisWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraintsBuilder.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            val db = MusicDatabase.getDatabase(applicationContext)
            val allSongs = db.songDao().getAllSongs()
            if (allSongs.isEmpty()) return@withContext Result.success()

            val analyzedIds = db.bpmCacheDao().getAll().map { it.songId }.toHashSet()
            val pending = allSongs.filter { it.id !in analyzedIds }
            if (pending.isEmpty()) return@withContext Result.success()

            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val storedCursor = prefs.getInt(PREF_CURSOR, 0)
            val cursor = if (storedCursor >= pending.size) 0 else storedCursor
            val batch = pending.drop(cursor).take(BATCH_SIZE).ifEmpty { pending.take(BATCH_SIZE) }

            for (song in batch) {
                if (isStopped) break
                BpmCacheService.getOrAnalyze(applicationContext, song)
            }

            val nextCursor = (cursor + batch.size).let { if (it >= pending.size) 0 else it }
            prefs.edit().putInt(PREF_CURSOR, nextCursor).apply()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
