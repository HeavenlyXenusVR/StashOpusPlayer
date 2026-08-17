package com.stash.opusplayer.ui.compose.discovery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.StashOpusApplication
import com.stash.opusplayer.bridge.BridgeStreamResolver
import com.stash.opusplayer.bridge.PlaybackRequest
import com.stash.opusplayer.bridge.api.AriaDailyPickResponse
import com.stash.opusplayer.bridge.api.BridgeTrack
import com.stash.opusplayer.bridge.api.DiscoveryApi
import com.stash.opusplayer.bridge.api.GlobalActivityEntry
import com.stash.opusplayer.bridge.api.OnThisDayGroup
import com.stash.opusplayer.bridge.api.StreamingApi
import com.stash.opusplayer.bridge.api.TrendingTrack
import com.stash.opusplayer.data.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which half of the screen is currently shown. */
enum class DiscoveryTab { DISCOVER_MIX, ON_THIS_DAY, TRENDING, COMMUNITY, SIMILAR_LISTENERS }

/**
 * Backs `Settings -> Discover`, ported from Lumisound's `DiscoverMixView.swift`/
 * `OnThisDayView.swift`. Neither bridge endpoint returns a directly-playable
 * URL -- tapping a row resolves one on demand via [BridgeStreamResolver]
 * (the same abstraction [com.stash.opusplayer.ui.compose.streaming.StreamingBrowseViewModel]
 * uses for search results), then hands a single-track [Song] to the app's
 * shared [com.stash.opusplayer.player.MusicPlayerManager] -- the actual
 * "play a resolved bridge track" wiring that screen's own KDoc flagged as
 * deferred, now built here since Discover Mix/On This Day are useless
 * without it. Uses `song.id = -1L` (a sentinel below any real MediaStore
 * id) so [com.stash.opusplayer.player.MusicPlayerManager.resolveSongUri]'s
 * "fall back to a MediaStore id lookup" branch is skipped and it falls
 * straight through to parsing the streamed URL, matching how that
 * resolver already handles non-local paths.
 *
 * No "Play All" here (unlike Lumisound's Discover Mix) -- that needs
 * resolving every row's stream URL up front before queueing, a lazy-queue-
 * resolution feature this app's player doesn't have yet; per-row tap-to-
 * play covers the actual "listen to something new" use case without it.
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val discoveryApi: DiscoveryApi,
    private val streamResolver: BridgeStreamResolver,
    private val streamingApi: StreamingApi
) : ViewModel() {

    data class UiState(
        val selectedTab: DiscoveryTab = DiscoveryTab.DISCOVER_MIX,

        val isLoadingDiscoverMix: Boolean = true,
        val discoverMix: List<BridgeTrack> = emptyList(),
        val discoverMixError: String? = null,

        val isLoadingOnThisDay: Boolean = true,
        val onThisDay: List<OnThisDayGroup> = emptyList(),
        val onThisDayError: String? = null,

        val isLoadingTrending: Boolean = true,
        val trending: List<TrendingTrack> = emptyList(),
        val trendingError: String? = null,

        val isLoadingCommunityActivity: Boolean = true,
        val communityActivity: List<GlobalActivityEntry> = emptyList(),
        val communityActivityError: String? = null,

        val isLoadingSimilarListeners: Boolean = true,
        val similarListeners: List<TrendingTrack> = emptyList(),
        val similarListenerCount: Int = 0,
        val similarListenersReason: String? = null,
        val similarListenersError: String? = null,

        val isLoadingDailyPick: Boolean = true,
        val dailyPick: AriaDailyPickResponse? = null,

        val resolvingTrackId: String? = null,
        /** Keyed by "title|artist" -- title/artist rows (Trending/Similar Listeners) have no id to key on, unlike [BridgeTrack.id]. */
        val resolvingTitleArtistKey: String? = null,
        val playbackError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadDiscoverMix()
        loadOnThisDay()
        loadTrending()
        loadCommunityActivity()
        loadSimilarListeners()
        loadDailyPick()
    }

    fun onTabSelected(tab: DiscoveryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadDiscoverMix() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDiscoverMix = true, discoverMixError = null) }
            try {
                val response = discoveryApi.getDiscoverMix()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingDiscoverMix = false, discoverMix = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingDiscoverMix = false, discoverMixError = "Couldn't load your Discover Mix (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDiscoverMix = false, discoverMixError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun loadOnThisDay() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOnThisDay = true, onThisDayError = null) }
            try {
                val response = discoveryApi.getOnThisDay()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingOnThisDay = false, onThisDay = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingOnThisDay = false, onThisDayError = "Couldn't load On This Day (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingOnThisDay = false, onThisDayError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    /** Global trending -- title/artist pairs, purely informational, no tap-to-play, matching Lumisound's own Trending tab (no `track_url`/id to resolve against anyway, this is an aggregate, not a single history row). */
    fun loadTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrending = true, trendingError = null) }
            try {
                val response = discoveryApi.getTrendingTracks()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingTrending = false, trending = response.body()?.tracks.orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingTrending = false, trendingError = "Couldn't load trending tracks (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingTrending = false, trendingError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    /** "What others are listening to" among ALL opted-in users, not just friends -- distinct from the friends-only feed in [com.stash.opusplayer.ui.compose.social.FriendActivityScreen]. Also purely informational. */
    fun loadCommunityActivity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCommunityActivity = true, communityActivityError = null) }
            try {
                val response = discoveryApi.getGlobalActivity()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingCommunityActivity = false, communityActivity = response.body()?.activity.orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingCommunityActivity = false, communityActivityError = "Couldn't load community activity (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingCommunityActivity = false, communityActivityError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    /** Real collaborative-filtering recommendations, based on other opted-in users whose top artists overlap with the caller's own. */
    fun loadSimilarListeners() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSimilarListeners = true, similarListenersError = null) }
            try {
                val response = discoveryApi.getSimilarListeners()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(
                            isLoadingSimilarListeners = false,
                            similarListeners = body.tracks,
                            similarListenerCount = body.similarListenerCount,
                            similarListenersReason = body.reason
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingSimilarListeners = false, similarListenersError = "Couldn't load similar listeners (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingSimilarListeners = false, similarListenersError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    /**
     * Trending/Similar Listeners rows are plain title/artist suggestions --
     * not something already resolvable, unlike [BridgeTrack] rows -- so
     * tapping one runs a live search (same [StreamingApi.search] the
     * Cloud Services search screen uses) and plays the first result.
     * Lumisound's own `HubSimilarListenersCarousel` instead opens Cloud
     * Services search pre-filled with the query rather than auto-playing;
     * auto-playing the top hit is a deliberate small improvement here
     * (one tap instead of two) since this app already has the resolve+play
     * pipeline built for [playTrack] and a plain title/artist search is
     * usually unambiguous enough to trust the first result.
     */
    fun playTitleArtist(title: String, artist: String?) {
        val key = "$title|${artist.orEmpty()}"
        if (_uiState.value.resolvingTitleArtistKey != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(resolvingTitleArtistKey = key, playbackError = null) }
            try {
                val query = listOfNotNull(title, artist).joinToString(" ")
                val searchResponse = streamingApi.search(query = query, limit = 1, source = "youtube")
                val match = searchResponse.body()?.firstOrNull()
                if (!searchResponse.isSuccessful || match == null) {
                    _uiState.update { it.copy(resolvingTitleArtistKey = null, playbackError = "Couldn't find a playable match for \"$title\".") }
                    return@launch
                }
                val request = PlaybackRequest(
                    sourceId = match.id,
                    source = match.source,
                    url = match.youtubeUrl,
                    preferredFormat = "m4a"
                )
                val result = streamResolver.resolve(request)
                result.onSuccess { resolution ->
                    val song = Song(
                        id = -1L,
                        title = match.title,
                        artist = match.artist,
                        album = "",
                        duration = match.durationSeconds * 1000L,
                        path = resolution.streamUrl
                    )
                    (appContext.applicationContext as? StashOpusApplication)?.playerManager?.playSong(song)
                    _uiState.update { it.copy(resolvingTitleArtistKey = null) }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(resolvingTitleArtistKey = null, playbackError = "Couldn't play that track: ${error.message ?: "unknown error"}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(resolvingTitleArtistKey = null, playbackError = "Couldn't find a playable match for \"$title\".") }
            }
        }
    }

    /**
     * Ported from `AccountService+Intelligence.swift`'s `fetchAriaDailyPick`.
     * Silent on failure/no-history, matching iOS -- there's no dedicated
     * error state, [UiState.dailyPick] just stays null and the card simply
     * doesn't render, same as `LibraryHubView`'s `ariaDailyPick?.track != nil`
     * visibility gate.
     */
    fun loadDailyPick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDailyPick = true) }
            try {
                val response = discoveryApi.getAriaDailyPick()
                val body = if (response.isSuccessful) response.body() else null
                _uiState.update { it.copy(isLoadingDailyPick = false, dailyPick = body) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDailyPick = false) }
            }
        }
    }

    fun playTrack(track: BridgeTrack) {
        if (_uiState.value.resolvingTrackId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(resolvingTrackId = track.id, playbackError = null) }
            val request = PlaybackRequest(
                sourceId = track.id,
                source = track.source,
                url = track.youtubeUrl,
                preferredFormat = "m4a"
            )
            val result = streamResolver.resolve(request)
            result.onSuccess { resolution ->
                val song = Song(
                    id = -1L,
                    title = track.title,
                    artist = track.artist,
                    album = "",
                    duration = track.durationSeconds * 1000L,
                    path = resolution.streamUrl
                )
                (appContext.applicationContext as? StashOpusApplication)?.playerManager?.playSong(song)
                _uiState.update { it.copy(resolvingTrackId = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(resolvingTrackId = null, playbackError = "Couldn't play that track: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    fun dismissPlaybackError() {
        _uiState.update { it.copy(playbackError = null) }
    }
}
