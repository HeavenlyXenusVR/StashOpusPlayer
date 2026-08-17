package com.stash.opusplayer.ui.compose.podcasts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.StashOpusApplication
import com.stash.opusplayer.bridge.api.PodcastEpisode
import com.stash.opusplayer.bridge.api.PodcastSubscribeRequest
import com.stash.opusplayer.bridge.api.PodcastSubscription
import com.stash.opusplayer.bridge.api.PodcastsApi
import com.stash.opusplayer.bridge.api.UpdatePodcastSubscriptionRequest
import com.stash.opusplayer.data.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs `Settings -> Podcasts`. Covers subscribe/list/mute/unsubscribe +
 * episode browsing + direct playback only -- chapters, per-episode
 * playback-progress sync, OPML import/export, and search/trending
 * discovery are all real bridge features not modeled in this pass (see
 * [PodcastsApi]'s class doc). List/detail (subscriptions vs. one feed's
 * episodes) is one Compose island with internal state, same pattern as
 * [com.stash.opusplayer.ui.compose.playlists.CloudPlaylistsViewModel].
 *
 * Episode playback needs no bridge resolve step at all -- [PodcastEpisode.audioUrl]
 * is a direct enclosure URL, playable as-is via the app's shared player,
 * unlike every YouTube-sourced track list elsewhere in this app.
 */
@HiltViewModel
class PodcastsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val podcastsApi: PodcastsApi
) : ViewModel() {

    data class UiState(
        val isLoadingSubscriptions: Boolean = true,
        val subscriptions: List<PodcastSubscription> = emptyList(),
        val subscriptionsError: String? = null,

        val feedUrlInput: String = "",
        val isSubscribing: Boolean = false,
        val subscribeError: String? = null,

        val selectedFeedUrl: String? = null,
        val isLoadingEpisodes: Boolean = false,
        val episodes: List<PodcastEpisode> = emptyList(),
        val episodesError: String? = null,

        val playingGuid: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSubscriptions()
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
        _uiState.update { it.copy(selectedFeedUrl = feedUrl, episodes = emptyList(), episodesError = null) }
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
    }

    fun closeFeed() {
        _uiState.update { it.copy(selectedFeedUrl = null, episodes = emptyList()) }
    }

    /** No bridge round-trip needed -- [PodcastEpisode.audioUrl] is already a directly-playable enclosure URL. */
    fun playEpisode(episode: PodcastEpisode) {
        _uiState.update { it.copy(playingGuid = episode.guid) }
        val song = Song(
            id = -1L,
            title = episode.title.orEmpty(),
            artist = _uiState.value.subscriptions.firstOrNull { it.feedUrl == _uiState.value.selectedFeedUrl }?.title.orEmpty(),
            album = "",
            duration = (episode.durationSeconds ?: 0) * 1000L,
            path = episode.audioUrl
        )
        (appContext.applicationContext as? StashOpusApplication)?.playerManager?.playSong(song)
    }
}
