package com.stash.opusplayer.history

import android.content.Context
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.LogHistoryRequest
import com.stash.opusplayer.bridge.api.SyncApi
import com.stash.opusplayer.data.Song
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Logs a completed "genuine listen" to the shared bridge, ported from
 * Lumisound's `AudioPlayerManager+PositionTracking.swift` `scheduleHistoryLog()`.
 * The actual 5-second debounce (fired only if the same track is still
 * playing after 5s, filtering accidental skips) lives in
 * [com.stash.opusplayer.player.MusicPlayerManager] -- this object is just
 * the network call itself, kept separate so the player class doesn't need
 * a direct `SyncApi`/`BridgeTokenStore` dependency (it's constructed as a
 * plain object, not through Hilt).
 *
 * This is the same server call that quietly unlocks two other features
 * once wired: `POST /user/history` is what `GET /user/achievements`
 * computes badges/streaks from, and what the bridge's own scrobble
 * fire-and-forget (Last.fm/ListenBrainz, once a user links an account --
 * not ported yet) hangs off of. No behavior needs to change here for
 * either of those; they just start working once history rows exist.
 */
object PlayHistoryLogger {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun syncApi(): SyncApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    /** Best-effort, silent on any failure (not logged in, offline, server error) -- a missed history row is never worth surfacing to the user. */
    suspend fun log(context: Context, song: Song) {
        if (song.id == 0L || song.title.isBlank()) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        if (!deps.bridgeTokenStore().isLoggedIn()) return

        runCatching {
            deps.syncApi().logHistory(
                LogHistoryRequest(
                    title = song.title,
                    artist = song.artist.ifBlank { null },
                    localSongId = song.id.toString()
                )
            )
        }
    }
}
