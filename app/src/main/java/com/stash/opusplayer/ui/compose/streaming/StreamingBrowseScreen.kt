package com.stash.opusplayer.ui.compose.streaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.BridgeFavorite
import com.stash.opusplayer.bridge.api.BridgePlaylist
import com.stash.opusplayer.bridge.api.BridgeTrack

/**
 * Search + library browse screen backed by [StreamingBrowseViewModel] — see
 * that file's KDoc for exactly what's wired up (search/resolve via
 * [com.stash.opusplayer.bridge.api.StreamingApi], synced playlists/favorites
 * via [com.stash.opusplayer.bridge.api.SyncApi]) and what's deliberately not
 * (no playback pipeline hookup yet — resolving a stream here only proves the
 * call succeeds and shows the result, same as [StreamingBrowseViewModel.resolveStream]'s
 * own doc explains).
 */
@Composable
fun StreamingBrowseScreen(viewModel: StreamingBrowseViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isCheckingConfig -> CenteredLoading()

            !uiState.isBridgeConfigured -> CenteredMessage(
                "Set up the bridge connection in Settings to search and stream music."
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                    Tab(
                        selected = uiState.selectedTab == StreamingBrowseTab.SEARCH,
                        onClick = { viewModel.onTabSelected(StreamingBrowseTab.SEARCH) },
                        text = { Text("Search") }
                    )
                    Tab(
                        selected = uiState.selectedTab == StreamingBrowseTab.LIBRARY,
                        onClick = { viewModel.onTabSelected(StreamingBrowseTab.LIBRARY) },
                        text = { Text("Your Library") }
                    )
                }

                when (uiState.selectedTab) {
                    StreamingBrowseTab.SEARCH -> SearchTab(
                        query = uiState.searchQuery,
                        isSearching = uiState.isSearching,
                        hasSearched = uiState.hasSearched,
                        results = uiState.searchResults,
                        error = uiState.searchError,
                        resolvingTrackId = uiState.resolvingTrackId,
                        resolvedTrackId = uiState.resolvedTrackId,
                        resolvedMessage = uiState.resolvedMessage,
                        resolveError = uiState.resolveError,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSearch = viewModel::search,
                        onResolve = viewModel::resolveStream,
                        onDismissResolveMessage = viewModel::dismissResolveMessage
                    )

                    StreamingBrowseTab.LIBRARY -> LibraryTab(
                        isLoggedIn = uiState.isLoggedIn,
                        isLoading = uiState.isLoadingLibrary,
                        hasLoaded = uiState.hasLoadedLibrary,
                        playlists = uiState.playlists,
                        favorites = uiState.favorites,
                        error = uiState.libraryError,
                        onRetry = viewModel::loadLibrary
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun SearchTab(
    query: String,
    isSearching: Boolean,
    hasSearched: Boolean,
    results: List<BridgeTrack>,
    error: String?,
    resolvingTrackId: String?,
    resolvedTrackId: String?,
    resolvedMessage: String?,
    resolveError: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResolve: (BridgeTrack) -> Unit,
    onDismissResolveMessage: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search for a track") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onSearch, enabled = query.isNotBlank() && !isSearching) {
                    Text("Go")
                }
            }
        }

        if (isSearching) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
        }

        if (error != null) {
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (resolvedMessage != null || resolveError != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = resolvedMessage ?: resolveError.orEmpty(),
                            color = if (resolveError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = onDismissResolveMessage) { Text("Dismiss") }
                    }
                }
            }
        }

        if (!isSearching && hasSearched && results.isEmpty() && error == null) {
            item {
                Text(
                    text = "No results.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(results, key = { it.id }) { track ->
            SearchResultRow(
                track = track,
                isResolving = resolvingTrackId == track.id,
                isResolved = resolvedTrackId == track.id,
                onResolve = { onResolve(track) }
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    track: BridgeTrack,
    isResolving: Boolean,
    isResolved: Boolean,
    onResolve: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                isResolving -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                isResolved -> Text(
                    text = "Resolved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> Button(onClick = onResolve) { Text("Play") }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    isLoggedIn: Boolean,
    isLoading: Boolean,
    hasLoaded: Boolean,
    playlists: List<BridgePlaylist>,
    favorites: List<BridgeFavorite>,
    error: String?,
    onRetry: () -> Unit
) {
    when {
        !isLoggedIn -> CenteredMessage("Log in to see your synced playlists and favorites.")

        isLoading && !hasLoaded -> CenteredLoading()

        error != null && playlists.isEmpty() && favorites.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = error, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        text = "No synced playlists yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist)
                }
            }

            item {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (favorites.isEmpty()) {
                item {
                    Text(
                        text = "No favorites yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(favorites, key = { it.songId }) { favorite ->
                    FavoriteRow(favorite)
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: BridgePlaylist) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = playlist.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${playlist.tracks.size} track${if (playlist.tracks.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FavoriteRow(favorite: BridgeFavorite) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = favorite.title?.takeIf { it.isNotBlank() } ?: favorite.songId,
                style = MaterialTheme.typography.titleMedium
            )
            if (!favorite.artist.isNullOrBlank()) {
                Text(
                    text = favorite.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
