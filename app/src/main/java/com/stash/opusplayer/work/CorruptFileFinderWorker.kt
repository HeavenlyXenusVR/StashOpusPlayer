package com.stash.opusplayer.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stash.opusplayer.data.database.CorruptFileEntity
import com.stash.opusplayer.data.database.MusicDatabase
import com.stash.opusplayer.library.AudioFileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Ported from Lumisound's CorruptFileFinderService: periodically re-checks
 * every indexed song for header corruption / truncation (see
 * [AudioFileValidator]) and keeps the `corrupt_files` table self-healing --
 * a song that now validates cleanly (re-downloaded, fixed externally) is
 * dropped from the flagged list on the very next pass, same as a newly
 * broken one is added. Lumisound reruns this every 5 minutes while
 * foregrounded (an in-process `Timer`, since iOS keeps the app alive during
 * background audio); Android has no equivalent guarantee the process is
 * even running, so this uses the same idle/battery-not-low periodic
 * `WorkManager` pattern already established by [MetadataScanWorker].
 */
class CorruptFileFinderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME_PERIODIC = "corrupt-file-finder-periodic"
        private const val WORK_NAME_ONESHOT = "corrupt-file-finder-scan-now"

        fun schedulePeriodic(context: Context) {
            val constraintsBuilder = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                constraintsBuilder.setRequiresDeviceIdle(true)
            }
            val request = PeriodicWorkRequestBuilder<CorruptFileFinderWorker>(8, TimeUnit.HOURS)
                .setConstraints(constraintsBuilder.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Manual "Scan Now" entry point -- no idle/battery constraints, since the user is actively watching. */
        fun scanNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CorruptFileFinderWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_ONESHOT, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = MusicDatabase.getDatabase(applicationContext)
            val songDao = db.songDao()
            val corruptDao = db.corruptFileDao()

            val songs = songDao.getAllSongs()
            val stillCorruptIds = mutableListOf<Long>()

            for (song in songs) {
                val flag = AudioFileValidator.validate(applicationContext, song)
                if (flag != null) {
                    stillCorruptIds += song.id
                    corruptDao.insert(
                        CorruptFileEntity(
                            songId = song.id,
                            title = song.title,
                            artist = song.artist,
                            path = song.path,
                            sizeBytes = song.size,
                            reason = flag.reason,
                            flaggedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            corruptDao.deleteAllExcept(stillCorruptIds)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
