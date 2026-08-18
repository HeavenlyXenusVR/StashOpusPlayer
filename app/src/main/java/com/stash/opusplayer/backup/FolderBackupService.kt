package com.stash.opusplayer.backup

import android.content.Context
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.FolderBackupApi
import com.stash.opusplayer.bridge.api.FolderBackupEntry
import com.stash.opusplayer.bridge.api.FolderBackupPushRequest
import com.stash.opusplayer.bridge.api.FolderBackupTrack
import com.stash.opusplayer.data.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watched-folder structure backup, ported from Lumisound's
 * `AccountService+FolderBackup.swift`. Wholesale replace-on-push,
 * metadata-only (title/artist/duration, never audio bytes) -- mirrors that
 * file's exact scope, including its notable absence of any restore/
 * redownload action: there is no dedicated restore UI on iOS either, only
 * push + fetch. See [com.stash.opusplayer.bridge.api.FolderBackupApi]'s
 * class doc for why every track's `source_track_id` is always null from
 * this client.
 *
 * Uses Hilt [EntryPointAccessors] since call sites
 * ([com.stash.opusplayer.ui.fragments.settings.LibrarySettingsFragment])
 * aren't themselves Hilt-managed, same reasoning as
 * [com.stash.opusplayer.identify.AcoustIdService].
 */
object FolderBackupService {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun folderBackupApi(): FolderBackupApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    /** Schedules a push 2 seconds after the last call (matches iOS's own debounce) -- call whenever watched folders change or a rescan picks up new files. */
    fun schedulePush(context: Context) {
        val appContext = context.applicationContext
        debounceJob?.cancel()
        debounceJob = debounceScope.launch {
            delay(2_000L)
            pushNow(appContext)
        }
    }

    /** Fire-and-forget -- failures are silently swallowed, same as the Swift original ("failures are logged only"). */
    suspend fun pushNow(context: Context) {
        val appContext = context.applicationContext
        val deps = EntryPointAccessors.fromApplication(appContext, Deps::class.java)
        if (!deps.bridgeTokenStore().isLoggedIn()) return
        try {
            val repo = MusicRepository(appContext)
            val entries = repo.getFolderBackupEntries().map { (folderPath, songs) ->
                FolderBackupEntry(
                    folderPath = folderPath,
                    tracks = songs.map { song ->
                        FolderBackupTrack(
                            filename = song.displayName,
                            title = song.title.takeIf { it.isNotBlank() },
                            artist = song.artist.takeIf { it.isNotBlank() },
                            durationSeconds = (song.duration / 1000.0).takeIf { it > 0 }
                        )
                    }
                )
            }
            deps.folderBackupApi().pushFolderBackups(FolderBackupPushRequest(folders = entries))
        } catch (e: Exception) {
            // Best-effort only, matching the Swift original.
        }
    }
}
