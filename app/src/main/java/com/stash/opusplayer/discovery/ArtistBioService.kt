package com.stash.opusplayer.discovery

import android.content.Context
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.ArtistBioResponse
import com.stash.opusplayer.bridge.api.DiscoveryApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches GET /api/artist/bio for [ArtistSongsFragment]'s bio card, ported
 * from Lumisound's `AccountService+ArtistBio.swift`. Silent best-effort,
 * same style as [com.stash.opusplayer.history.PlayHistoryLogger] --
 * not logged in, network failure, or a server "not found" (`found: false`)
 * are all just "show no card", matching iOS's own treatment (there's no
 * distinct error state in `ArtistDetailView`, only presence/absence of the
 * card).
 *
 * Uses Hilt [EntryPointAccessors] since [ArtistSongsFragment] is a plain
 * (non-Hilt) `Fragment`, same reasoning as [com.stash.opusplayer.identify.AcoustIdService].
 */
object ArtistBioService {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun discoveryApi(): DiscoveryApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    suspend fun fetchBio(context: Context, artistName: String): ArtistBioResponse? =
        withContext(Dispatchers.IO) {
            if (artistName.isBlank()) return@withContext null
            val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
            if (!deps.bridgeTokenStore().isLoggedIn()) return@withContext null
            try {
                val response = deps.discoveryApi().getArtistBio(artistName)
                val body = response.body()
                if (response.isSuccessful && body != null && body.found && !body.bio.isNullOrBlank()) body else null
            } catch (e: Exception) {
                null
            }
        }
}
