package com.stash.opusplayer.bridge

import android.content.Context
import com.stash.opusplayer.bridge.api.DevicesApi
import com.stash.opusplayer.bridge.api.PlaybackTransferRequest
import com.stash.opusplayer.bridge.api.RegisteredDeviceResponse
import com.stash.opusplayer.data.Song
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Cross-device playback handoff, ported from
 * `AccountService+PlaybackTransfer.swift`. Plain object with Hilt
 * [EntryPointAccessors] so [com.stash.opusplayer.ui.NowPlayingActivity] (a
 * plain, non-`@AndroidEntryPoint` `Activity`) can call it -- same reasoning
 * as [QueueSyncService]. See [DevicesApi]'s doc for why this is
 * outbound-only.
 */
object PlaybackTransferService {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun devicesApi(): DevicesApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    suspend fun fetchOtherDevices(context: Context): List<RegisteredDeviceResponse> {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        if (!deps.bridgeTokenStore().isLoggedIn()) return emptyList()
        val response = runCatching { deps.devicesApi().listDevices() }.getOrNull() ?: return emptyList()
        return if (response.isSuccessful) response.body().orEmpty() else emptyList()
    }

    /** [song]'s bridge source/id, when known, is split the same way [QueueSyncService] treats a bridge-resolved [Song] -- `id == -1L` means it came from a bridge stream, not the on-device library. */
    suspend fun transferPlayback(
        context: Context,
        song: Song,
        positionSeconds: Double,
        isPlaying: Boolean,
        targetDeviceToken: String
    ): Boolean {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val isBridgeTrack = song.id == -1L
        val body = PlaybackTransferRequest(
            targetDeviceToken = targetDeviceToken,
            songId = if (isBridgeTrack) null else song.id.toString(),
            title = song.title,
            artist = song.artist.ifBlank { null },
            trackUrl = if (isBridgeTrack) song.path.ifBlank { null } else null,
            positionSeconds = positionSeconds,
            durationSeconds = song.duration / 1000.0,
            isPlaying = isPlaying
        )
        val response = runCatching { deps.devicesApi().transferPlayback(body) }.getOrNull() ?: return false
        return response.isSuccessful
    }
}
