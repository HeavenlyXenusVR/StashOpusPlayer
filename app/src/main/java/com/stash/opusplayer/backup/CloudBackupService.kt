package com.stash.opusplayer.backup

import android.content.Context
import com.stash.opusplayer.bridge.api.BridgeFavorite
import com.stash.opusplayer.bridge.api.BridgePlaylist
import com.stash.opusplayer.data.MusicRepository
import com.stash.opusplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Merges a restored cloud-backup snapshot's favorites/playlists into the
 * local library, ported from the matching intent of Lumisound's own local
 * `LibraryBackupService` import (merge, don't replace; skip songs that
 * don't exist locally) -- same "songs referenced that don't exist locally
 * are skipped" behavior, applied here to a snapshot that may have come
 * from a DIFFERENT device/platform (a favorite/playlist entry has no
 * StashOpusPlayer song id to match against at all, only title/artist/
 * album metadata), so matching is always by title[+artist] against the
 * current local library -- the same fallback-matching approach
 * [com.stash.opusplayer.mood.M3UImportService] already established for
 * an analogous "reconcile external references against local songs"
 * problem.
 */
object CloudBackupService {

    data class RestoreSummary(
        val favoritesMatched: Int,
        val favoritesTotal: Int,
        val playlistsCreated: Int,
        val tracksMatched: Int,
        val tracksTotal: Int
    )

    suspend fun mergeFavoritesAndPlaylists(
        context: Context,
        favorites: List<BridgeFavorite>,
        playlists: List<BridgePlaylist>
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val repository = MusicRepository(context)
        val librarySongs = repository.getAllSongs()

        fun matchSong(title: String?, artist: String?): Song? {
            if (title.isNullOrBlank()) return null
            return librarySongs.firstOrNull { candidate ->
                candidate.displayName.equals(title, ignoreCase = true) &&
                    (artist.isNullOrBlank() || candidate.artistName.equals(artist, ignoreCase = true))
            }
        }

        var favoritesMatched = 0
        for (favorite in favorites) {
            val song = matchSong(favorite.title, favorite.artist) ?: continue
            if (!repository.isFavorite(song.id)) {
                repository.addToFavorites(song)
            }
            favoritesMatched++
        }

        val existingPlaylists = repository.getPlaylists().first()
        var playlistsCreated = 0
        var tracksMatched = 0
        var tracksTotal = 0

        for (bridgePlaylist in playlists) {
            tracksTotal += bridgePlaylist.tracks.size
            val matchedSongs = bridgePlaylist.tracks.mapNotNull { matchSong(it.title, it.artist) }
            tracksMatched += matchedSongs.size
            if (matchedSongs.isEmpty()) continue

            val existing = existingPlaylists.firstOrNull { it.name.equals(bridgePlaylist.name, ignoreCase = true) }
            if (existing != null) {
                val currentSongIds = repository.getPlaylistTracks(existing.id).first().map { it.songId }.toSet()
                val newSongs = matchedSongs.filter { it.id !in currentSongIds }
                if (newSongs.isNotEmpty()) repository.addSongsToPlaylist(existing.id, newSongs)
            } else {
                repository.createPlaylist(bridgePlaylist.name, matchedSongs)
                playlistsCreated++
            }
        }

        RestoreSummary(favoritesMatched, favorites.size, playlistsCreated, tracksMatched, tracksTotal)
    }
}
