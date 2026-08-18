package com.stash.opusplayer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stash.opusplayer.data.MusicRepository
import com.stash.opusplayer.utils.LibraryScanTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryRescanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            LibraryScanTracker.startScan("Rescanning library…")
            val repo = MusicRepository(applicationContext)
            // Trigger MediaStore + SAF scans via repository methods.
            // refreshSongIndex() does the live MediaStore scan AND persists the result into the
            // Room-backed song index, so a manual "rescan library" also refreshes the cache that
            // getAllSongs() now reads from.
            repo.refreshSongIndex() // MediaStore scan + persist to song index
            repo.scanCustomFolders() // SAF custom folders
            LibraryScanTracker.completeScan()
            com.stash.opusplayer.backup.FolderBackupService.pushNow(applicationContext)
            Result.success()
        } catch (e: Exception) {
            LibraryScanTracker.completeScan()
            Result.retry()
        }
    }
}