package com.stash.opusplayer.ui.compose.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.BridgeConfig
import com.stash.opusplayer.bridge.BridgeStreamResolver
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.PlaybackRequest
import com.stash.opusplayer.bridge.api.BridgeFavorite
import com.stash.opusplayer.bridge.api.BridgePlaylist
import com.stash.opusplayer.bridge.api.BridgeTrack
import com.stash.opusplayer.bridge.api.StreamingApi
import com.stash.opusplayer.bridge.api.SyncApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which half of the screen is currently shown. */
enum class StreamingBrowseTab { SEARCH, LIBRARY }

/**
 * Single flat UI-state holder for [StreamingBrowseScreen].
 *
 * Built strictly against what [StreamingApi] and [SyncApi] actually expose
 * today: search/stream/track/resolve are yt-dlp-backed, operator-API-key-gated
 * endpoints with NO concept of a per-user cloud music library (see
 * `StreamingApi`'s own class doc for what's explicitly deferred — `/api/stream/
 * proxy`, the whole `/api/download*` job family, `/api/spotify/resolve`,
 * `/api/playlist/expand|links`, `/api/lyrics*`, `/api/radio`, trending/
 * suggestions). The "library" half of this screen is therefore just the
 * user's synced playlists/favorites from [SyncApi] (`/user/playlists`,
 * `/user/favorites`) — not a personal cloud storage browser, since no
 * `/user/music/*` API exists to browse.
 */
data class StreamingBrowseUiState(
    // --- Bridge configuration gate ---
    val isCheckingConfig: Boolean = true,
    val isBridgeConfigured: Boolean = false,

    // --- Tab selection ---
    val selectedTab: StreamingBrowseTab = StreamingBrowseTab.SEARCH,

    // --- Search section (StreamingApi) ---
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchResults: List<BridgeTrack> = emptyList(),
    val searchError: String? = null,

    // --- Stream resolve (BridgeStreamResolver, wrapping StreamingApi.stream) ---
    val resolvingTrackId: String? = null,
    val resolvedTrackId: String? = null,
    val resolvedMessage: String? = null,
    val resolveError: String? = null,

    // --- Library section (SyncApi) ---
    val isLoggedIn: Boolean = false,
    val isLoadingLibrary: Boolean = false,
    val hasLoadedLibrary: Boolean = false,
    val playlists: List<BridgePlaylist> = emptyList(),
    val favorites: List<BridgeFavorite> = emptyList(),
    val libraryError: String? = null
)

@HiltViewModel
class StreamingBrowseViewModel @Inject constructor(
    private val bridgeConfig: BridgeConfig,
    private val tokenStore: BridgeTokenStore,
    private val streamingApi: StreamingApi,
    private val syncApi: SyncApi,
    private val streamResolver: BridgeStreamResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamingBrowseUiState())
    val uiState: StateFlow<StreamingBrowseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = bridgeConfig.isConfigured()
            val loggedIn = tokenStore.isLoggedIn()
            _uiState.update {
                it.copy(
                    isCheckingConfig = false,
                    isBridgeConfigured = configured,
                    isLoggedIn = loggedIn
                )
            }
            if (configured && loggedIn) {
                loadLibrary()
            }
        }
    }

    fun onTabSelected(tab: StreamingBrowseTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == StreamingBrowseTab.LIBRARY &&
            _uiState.value.isLoggedIn &&
            !_uiState.value.hasLoadedLibrary &&
            !_uiState.value.isLoadingLibrary
        ) {
            loadLibrary()
        }
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    /** Runs a search via [StreamingApi.search] against the yt-dlp-backed backend. */
    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty() || _uiState.value.isSearching) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    searchError = null,
                    resolvedMessage = null,
                    resolveError = null,
                    resolvedTrackId = null
                )
            }
            try {
                val response = streamingApi.search(query = query, limit = 25, source = "youtube")
                if (response.isSuccessful) {
                    val results = response.body().orEmpty()
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            searchResults = results,
                            searchError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = true,
                            searchResults = emptyList(),
                            searchError = "Search failed (HTTP ${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = true,
                        searchResults = emptyList(),
                        searchError = "Search failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    /**
     * Resolves a playable stream URL for [track] via [BridgeStreamResolver]
     * (the existing, correct abstraction over `StreamingApi.stream` — see its
     * KDoc). Deliberately does NOT hand the resolved URL to `MusicService` or
     * any playback pipeline: that wiring is separate, later integration work.
     * This just proves the resolve call succeeds and surfaces the result.
     */
    fun resolveStream(track: BridgeTrack) {
        if (_uiState.value.resolvingTrackId != null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    resolvingTrackId = track.id,
                    resolvedTrackId = null,
                    resolvedMessage = null,
                    resolveError = null
                )
            }
            val request = PlaybackRequest(
                sourceId = track.id,
                source = track.source,
                url = track.youtubeUrl,
                preferredFormat = "m4a"
            )
            val result = streamResolver.resolve(request)
            result.onSuccess { resolution ->
                _uiState.update {
                    it.copy(
                        resolvingTrackId = null,
                        resolvedTrackId = track.id,
                        resolvedMessage = "Ready to play: ${resolution.streamUrl}",
                        resolveError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        resolvingTrackId = null,
                        resolvedTrackId = null,
                        resolvedMessage = null,
                        resolveError = "Couldn't resolve stream: ${error.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun dismissResolveMessage() {
        _uiState.update { it.copy(resolvedMessage = null, resolveError = null, resolvedTrackId = null) }
    }

    /** (Re)loads the user's synced playlists + favorites via [SyncApi]. No-op if not logged in. */
    fun loadLibrary() {
        val loggedIn = tokenStore.isLoggedIn()
        _uiState.update { it.copy(isLoggedIn = loggedIn) }
        if (!loggedIn) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLibrary = true, libraryError = null) }
            try {
                val playlistsResponse = syncApi.getPlaylists()
                val favoritesResponse = syncApi.getFavorites()

                if (playlistsResponse.isSuccessful && favoritesResponse.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isLoadingLibrary = false,
                            hasLoadedLibrary = true,
                            playlists = playlistsResponse.body().orEmpty(),
                            favorites = favoritesResponse.body().orEmpty(),
                            libraryError = null
                        )
                    }
                } else {
                    val code = if (!playlistsResponse.isSuccessful) {
                        playlistsResponse.code()
                    } else {
                        favoritesResponse.code()
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingLibrary = false,
                            hasLoadedLibrary = true,
                            libraryError = "Failed to load your library (HTTP $code)"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingLibrary = false,
                        hasLoadedLibrary = true,
                        libraryError = "Failed to load your library: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }
}
