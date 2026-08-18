package com.stash.opusplayer.bridge

import android.content.Context
import com.stash.opusplayer.bridge.api.QueueApi
import com.stash.opusplayer.bridge.api.QueueTrackRequest
import com.stash.opusplayer.bridge.api.ReplaceQueueRequest
import com.stash.opusplayer.data.Song
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Cross-device play queue sync, ported from
 * `AccountService+QueueSync.swift`. Kept as a plain object with Hilt
 * [EntryPointAccessors] (not constructor-injected) so it can be called from
 * [com.stash.opusplayer.player.MusicPlayerManager], which is constructed as
 * a plain object rather than through Hilt -- same reasoning as
 * [com.stash.opusplayer.history.PlayHistoryLogger].
 */
object QueueSyncService {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun queueApi(): QueueApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    /**
     * Best-effort, silent on any failure -- called whenever the local
     * queue is replaced wholesale (see `MusicPlayerManager.playQueue`),
     * matching iOS's own "automatically in the background whenever the
     * queue changes" fire-and-forget push. A dropped push just means the
     * next [fetchQueue] restores an older queue, never a crash or a
     * surfaced error.
     */
    suspend fun pushQueue(context: Context, songs: List<Song>) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        if (!deps.bridgeTokenStore().isLoggedIn()) return

        val tracks = songs.mapNotNull { song ->
            if (song.title.isBlank()) return@mapNotNull null
            val isBridgeTrack = song.id == -1L
            QueueTrackRequest(
                localSongId = if (isBridgeTrack) null else song.id.toString(),
                trackUrl = if (isBridgeTrack) song.path.ifBlank { null } else null,
                title = song.title,
                artist = song.artist.ifBlank { null },
                album = song.album.ifBlank { null },
                durationSeconds = (song.duration / 1000L).toInt()
            )
        }

        runCatching { deps.queueApi().replaceQueue(ReplaceQueueRequest(tracks = tracks)) }
    }

    /**
     * Pulls the synced queue and resolves each item to a playable [Song].
     * Matches iOS's resolution order: a local-library match by
     * [com.stash.opusplayer.bridge.api.QueueItemResponse.localSongId] wins
     * over building a streaming [Song] from
     * [com.stash.opusplayer.bridge.api.QueueItemResponse.trackUrl] (already
     * a resolved, playable URL as pushed -- no re-resolution through
     * [BridgeStreamResolver] needed). Items with neither are skipped.
     */
    suspend fun fetchQueue(context: Context, librarySongs: List<Song>): List<Song> {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        if (!deps.bridgeTokenStore().isLoggedIn()) return emptyList()

        val response = runCatching { deps.queueApi().getQueue() }.getOrNull() ?: return emptyList()
        if (!response.isSuccessful) return emptyList()
        val items = response.body().orEmpty()
        val byId = librarySongs.associateBy { it.id.toString() }

        return items.mapNotNull { item ->
            item.localSongId?.let { byId[it] } ?: run {
                val url = item.trackUrl
                if (url.isNullOrBlank()) return@run null
                Song(
                    id = -1L,
                    title = item.title,
                    artist = item.artist.orEmpty(),
                    album = item.album.orEmpty(),
                    duration = (item.durationSeconds ?: 0) * 1000L,
                    path = url
                )
            }
        }
    }
}
