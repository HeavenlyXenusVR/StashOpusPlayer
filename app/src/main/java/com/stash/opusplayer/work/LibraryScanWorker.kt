package com.stash.opusplayer.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.opusplayer.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Keeps the persisted Room song index (see [com.stash.opusplayer.data.database.SongEntity] /
 * [com.stash.opusplayer.data.database.SongDao]) in sync with MediaStore, so
 * [MusicRepository.getAllSongs] can read from the index instead of re-querying MediaStore
 * from scratch every time the library screen opens.
 */
class LibraryScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "LibraryScanWorker"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val periodic = PeriodicWorkRequestBuilder<LibraryScanWorker>(12, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork("library-song-index-scan", ExistingPeriodicWorkPolicy.UPDATE, periodic)

            val once = OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
            wm.enqueue(once)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repo = MusicRepository(applicationContext)
            val songs = repo.refreshSongIndex()
            Log.i(TAG, "Library song index refreshed with ${songs.size} songs")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failure", e)
            Result.failure()
        }
    }
}
