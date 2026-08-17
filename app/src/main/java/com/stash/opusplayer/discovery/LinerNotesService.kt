package com.stash.opusplayer.discovery

import android.content.Context
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.DiscoveryApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches GET /music/liner-notes for an album detail screen, ported from
 * Lumisound's `AccountService+Intelligence.swift`'s `fetchLinerNotes` /
 * `AlbumDetailView`'s `AlbumLinerNotesCard`. Silent best-effort, same style
 * as [ArtistBioService] -- not logged in, network failure, or no confident
 * blurb are all just "show no card".
 *
 * Uses Hilt [EntryPointAccessors] for the same reason as [ArtistBioService]:
 * called from [com.stash.opusplayer.ui.fragments.FolderDetailFragment], a
 * plain (non-Hilt) `Fragment`.
 */
object LinerNotesService {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun discoveryApi(): DiscoveryApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    suspend fun fetchLinerNotes(context: Context, artist: String, album: String): String? =
        withContext(Dispatchers.IO) {
            if (artist.isBlank() || album.isBlank()) return@withContext null
            val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
            if (!deps.bridgeTokenStore().isLoggedIn()) return@withContext null
            try {
                val response = deps.discoveryApi().getLinerNotes(artist, album)
                val body = response.body()
                if (response.isSuccessful && body != null && !body.blurb.isNullOrBlank()) body.blurb else null
            } catch (e: Exception) {
                null
            }
        }
}
