package com.stash.opusplayer.ui.compose.podcasts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.StashOpusApplication
import com.stash.opusplayer.bridge.api.PodcastEpisode
import com.stash.opusplayer.bridge.api.PodcastEpisodeProgress
import com.stash.opusplayer.bridge.api.PodcastEpisodeProgressRequest
import com.stash.opusplayer.bridge.api.PodcastSearchResult
import com.stash.opusplayer.bridge.api.PodcastSubscribeRequest
import com.stash.opusplayer.bridge.api.PodcastSubscription
import com.stash.opusplayer.bridge.api.PodcastsApi
import com.stash.opusplayer.bridge.api.UpdatePodcastSubscriptionRequest
import com.stash.opusplayer.data.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs `Settings -> Podcasts`. Covers subscribe/list/mute/unsubscribe +
 * episode browsing + direct playback + playback-progress sync + search/
 * trending discovery -- chapters and OPML import/export are still real
 * bridge features not modeled in this pass (see [PodcastsApi]'s class
 * doc). List/detail (subscriptions vs. one feed's episodes) is one
 * Compose island with internal state, same pattern as
 * [com.stash.opusplayer.ui.compose.playlists.CloudPlaylistsViewModel].
 *
 * Episode playback needs no bridge resolve step at all -- [PodcastEpisode.audioUrl]
 * is a direct enclosure URL, playable as-is via the app's shared player,
 * unlike every YouTube-sourced track list elsewhere in this app.
 *
 * Progress sync is intentionally scoped to "while this screen is open,"
 * NOT app-wide background tracking -- [com.stash.opusplayer.player.MusicPlayerManager]
 * has no steady internal tick this ViewModel could piggyback on the way
 * Lumisound's `AudioPlayerManager+PositionTracking.swift` does (its own
 * position timer already ticks every 0.5s regardless of which screen is
 * visible; this app's position updates are event-driven, not timer-driven).
 * A future pass could move this into `MusicPlayerManager` itself for true
 * background tracking; this pass keeps the change fully additive and
 * confined to this screen. A podcast episode is identified purely by
 * marker fields on the plain [Song] object handed to the player --
 * `genre = "Podcast"`, `album = <feed URL>`, `relativePath = <episode
 * guid>` -- mirroring Lumisound's own identical reuse-the-Song-model trick
 * (`Song` has no dedicated podcast fields on either platform).
 */
/** Which half of the screen is currently shown. */
enum class PodcastsTab { SUBSCRIPTIONS, DISCOVER }

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val podcastsApi: PodcastsApi
) : ViewModel() {

    data class UiState(
        val selectedTab: PodcastsTab = PodcastsTab.SUBSCRIPTIONS,

        val isLoadingSubscriptions: Boolean = true,
        val subscriptions: List<PodcastSubscription> = emptyList(),
        val subscriptionsError: String? = null,

        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchResults: List<PodcastSearchResult> = emptyList(),
        val searchError: String? = null,
        val hasSearched: Boolean = false,

        val isLoadingTrending: Boolean = true,
        val trending: List<PodcastSearchResult> = emptyList(),
        val trendingError: String? = null,

        val subscribingFeedUrls: Set<String> = emptySet(),

        val feedUrlInput: String = "",
        val isSubscribing: Boolean = false,
        val subscribeError: String? = null,

        val selectedFeedUrl: String? = null,
        val isLoadingEpisodes: Boolean = false,
        val episodes: List<PodcastEpisode> = emptyList(),
        val episodesError: String? = null,
        val progressByGuid: Map<String, PodcastEpisodeProgress> = emptyMap(),

        val playingGuid: String? = null
    )

    private var progressPushJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSubscriptions()
        loadTrending()
    }

    fun onTabSelected(tab: PodcastsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadSubscriptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSubscriptions = true, subscriptionsError = null) }
            try {
                val response = podcastsApi.getSubscriptions()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingSubscriptions = false, subscriptions = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingSubscriptions = false, subscriptionsError = "Couldn't load podcast subscriptions (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingSubscriptions = false, subscriptionsError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun onFeedUrlChanged(value: String) {
        _uiState.update { it.copy(feedUrlInput = value, subscribeError = null) }
    }

    fun subscribe() {
        val url = _uiState.value.feedUrlInput.trim()
        if (url.isEmpty() || _uiState.value.isSubscribing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubscribing = true, subscribeError = null) }
            try {
                val response = podcastsApi.subscribe(PodcastSubscribeRequest(url))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSubscribing = false, feedUrlInput = "") }
                    loadSubscriptions()
                } else {
                    _uiState.update { it.copy(isSubscribing = false, subscribeError = "Couldn't add that feed (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubscribing = false, subscribeError = "Couldn't read that feed. Check the URL and try again.") }
            }
        }
    }

    fun unsubscribe(subscriptionId: String) {
        viewModelScope.launch {
            runCatching { podcastsApi.unsubscribe(subscriptionId) }
            _uiState.update { it.copy(subscriptions = it.subscriptions.filterNot { s -> s.id == subscriptionId }) }
        }
    }

    fun toggleMute(subscription: PodcastSubscription) {
        viewModelScope.launch {
            val newMuted = !subscription.notificationsMuted
            val response = runCatching {
                podcastsApi.updateSubscription(subscription.id, UpdatePodcastSubscriptionRequest(newMuted))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.update { state ->
                    state.copy(subscriptions = state.subscriptions.map {
                        if (it.id == subscription.id) it.copy(notificationsMuted = newMuted) else it
                    })
                }
            }
        }
    }

    fun openFeed(feedUrl: String) {
        _uiState.update { it.copy(selectedFeedUrl = feedUrl, episodes = emptyList(), episodesError = null, progressByGuid = emptyMap()) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            try {
                val response = podcastsApi.getEpisodes(feedUrl)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingEpisodes = false, episodes = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingEpisodes = false, episodesError = "Couldn't load episodes (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingEpisodes = false, episodesError = "Something went wrong. Check your connection.") }
            }
        }
        viewModelScope.launch {
            val response = runCatching { podcastsApi.getEpisodeProgress(feedUrl) }.getOrNull()
            if (response?.isSuccessful == true) {
                val byGuid = response.body().orEmpty().associateBy { it.episodeGuid }
                _uiState.update { it.copy(progressByGuid = byGuid) }
            }
        }
    }

    fun closeFeed() {
        _uiState.update { it.copy(selectedFeedUrl = null, episodes = emptyList()) }
    }

    /**
     * No bridge round-trip needed to play -- [PodcastEpisode.audioUrl] is
     * already a directly-playable enclosure URL. Seeks to the saved
     * position first (if any, and not already completed), then starts the
     * periodic progress-push loop for as long as this screen stays open
     * and this same episode keeps playing.
     */
    fun playEpisode(episode: PodcastEpisode) {
        val feedUrl = _uiState.value.selectedFeedUrl ?: return
        val title = episode.title.orEmpty()
        _uiState.update { it.copy(playingGuid = episode.guid) }
        val song = Song(
            id = -1L,
            title = title,
            artist = _uiState.value.subscriptions.firstOrNull { it.feedUrl == feedUrl }?.title.orEmpty(),
            album = feedUrl,
            duration = (episode.durationSeconds ?: 0) * 1000L,
            path = episode.audioUrl,
            genre = "Podcast",
            relativePath = episode.guid
        )
        val playerManager = (appContext.applicationContext as? StashOpusApplication)?.playerManager
        playerManager?.playSong(song)

        val savedProgress = _uiState.value.progressByGuid[episode.guid]
        if (savedProgress != null && !savedProgress.completed && savedProgress.positionSeconds > 5) {
            playerManager?.seekTo((savedProgress.positionSeconds * 1000).toLong())
        }

        startProgressPushLoop(feedUrl = feedUrl, guid = episode.guid, title = title)
    }

    /**
     * Every 5 seconds while this episode is still the current song, pushes
     * its position -- matches Lumisound's own cadence exactly
     * (`AudioPlayerManager+PositionTracking.swift`'s 5s piggyback on its
     * position timer). Stops itself once a different song becomes current,
     * the episode is marked completed, or this ViewModel is cleared (see
     * [onCleared]) -- there is no app-wide background continuation, see
     * this class's own doc comment for why.
     */
    private fun startProgressPushLoop(feedUrl: String, guid: String, title: String) {
        progressPushJob?.cancel()
        val playerManager = (appContext.applicationContext as? StashOpusApplication)?.playerManager ?: return
        progressPushJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                val currentSong = playerManager.currentSong.value ?: break
                if (currentSong.genre != "Podcast" || currentSong.album != feedUrl || currentSong.relativePath != guid) break

                val positionSeconds = playerManager.getCurrentPosition() / 1000.0
                val durationSeconds = playerManager.getDuration() / 1000.0
                val completed = durationSeconds > 0 && positionSeconds >= durationSeconds - 5
                runCatching {
                    podcastsApi.updateEpisodeProgress(
                        PodcastEpisodeProgressRequest(
                            feedUrl = feedUrl,
                            episodeGuid = guid,
                            title = title,
                            positionSeconds = positionSeconds,
                            durationSeconds = durationSeconds,
                            completed = completed
                        )
                    )
                }
                if (completed) break
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressPushJob?.cancel()
    }

    // --- Discovery (search / trending) ---------------------------------------

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun searchPodcasts() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty() || _uiState.value.isSearching) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            try {
                val response = podcastsApi.searchPodcasts(query)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSearching = false, hasSearched = true, searchResults = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isSearching = false, hasSearched = true, searchError = "Search failed (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, hasSearched = true, searchError = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun loadTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrending = true, trendingError = null) }
            try {
                val response = podcastsApi.getTrendingPodcasts()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingTrending = false, trending = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingTrending = false, trendingError = "Couldn't load trending podcasts (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingTrending = false, trendingError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    /** Subscribing to an already-subscribed feed is a harmless no-op server-side (upsert), so no "already subscribed" check is needed here. */
    fun subscribeToResult(result: PodcastSearchResult) {
        val feedUrl = result.feedUrl
        if (_uiState.value.subscribingFeedUrls.contains(feedUrl)) return
        viewModelScope.launch {
            _uiState.update { it.copy(subscribingFeedUrls = it.subscribingFeedUrls + feedUrl) }
            runCatching { podcastsApi.subscribe(PodcastSubscribeRequest(feedUrl)) }
            _uiState.update { it.copy(subscribingFeedUrls = it.subscribingFeedUrls - feedUrl) }
            loadSubscriptions()
        }
    }
}
