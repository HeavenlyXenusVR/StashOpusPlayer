package com.stash.opusplayer.ui.compose.subscriptions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.StashOpusApplication
import com.stash.opusplayer.bridge.BridgeStreamResolver
import com.stash.opusplayer.bridge.PlaybackRequest
import com.stash.opusplayer.bridge.api.ArtistSubscription
import com.stash.opusplayer.bridge.api.BridgeTrack
import com.stash.opusplayer.bridge.api.SubscribeChannelRequest
import com.stash.opusplayer.bridge.api.SubscriptionFeedItem
import com.stash.opusplayer.bridge.api.SubscriptionsApi
import com.stash.opusplayer.bridge.api.UpdateSubscriptionRequest
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
enum class SubscriptionsTab { CHANNELS, FEED }

/**
 * Backs `Settings -> Subscriptions`, ported from Lumisound's
 * `AccountService+Subscriptions.swift`/`SubscriptionsView.swift`/
 * `SubscriptionFeedView.swift`. A subscription is a followed YouTube
 * channel -- NOT the separate "tracked playlist" feature iOS bundles on
 * the same screen, out of scope here.
 *
 * The server already checks every subscription automatically roughly
 * every 4 hours (confirmed background polling loop in main.py), so this
 * screen has no polling of its own -- only manual "Check"/"Check All",
 * matching iOS's own foreground-only check triggering.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val subscriptionsApi: SubscriptionsApi,
    private val streamResolver: BridgeStreamResolver
) : ViewModel() {

    data class UiState(
        val selectedTab: SubscriptionsTab = SubscriptionsTab.CHANNELS,

        val isLoadingSubscriptions: Boolean = true,
        val subscriptions: List<ArtistSubscription> = emptyList(),
        val subscriptionsError: String? = null,

        val channelUrlInput: String = "",
        val channelNameInput: String = "",
        val isSubscribing: Boolean = false,
        val subscribeError: String? = null,

        val checkingSubscriptionIds: Set<String> = emptySet(),
        val isCheckingAll: Boolean = false,
        val newTracksBySubscriptionId: Map<String, List<BridgeTrack>> = emptyMap(),

        val isLoadingFeed: Boolean = true,
        val feed: List<SubscriptionFeedItem> = emptyList(),
        val feedError: String? = null,
        val unreadCount: Int = 0,

        val resolvingKey: String? = null,
        val playbackError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSubscriptions()
        loadFeed()
        loadUnreadCount()
    }

    fun onTabSelected(tab: SubscriptionsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadSubscriptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSubscriptions = true, subscriptionsError = null) }
            try {
                val response = subscriptionsApi.getSubscriptions()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingSubscriptions = false, subscriptions = response.body()?.subscriptions.orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingSubscriptions = false, subscriptionsError = "Couldn't load subscriptions (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingSubscriptions = false, subscriptionsError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun onChannelUrlChanged(value: String) {
        _uiState.update { it.copy(channelUrlInput = value, subscribeError = null) }
    }

    fun onChannelNameChanged(value: String) {
        _uiState.update { it.copy(channelNameInput = value) }
    }

    /** [channelUrlInput] accepts a raw URL, an @handle, or a plain channel name -- the server resolves it, no client-side validation beyond non-blank. */
    fun subscribe() {
        val url = _uiState.value.channelUrlInput.trim()
        if (url.isEmpty() || _uiState.value.isSubscribing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubscribing = true, subscribeError = null) }
            try {
                val name = _uiState.value.channelNameInput.trim().ifBlank { null }
                val response = subscriptionsApi.subscribe(SubscribeChannelRequest(channelUrl = url, channelName = name))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSubscribing = false, channelUrlInput = "", channelNameInput = "") }
                    loadSubscriptions()
                } else {
                    _uiState.update { it.copy(isSubscribing = false, subscribeError = "Couldn't subscribe (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubscribing = false, subscribeError = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun unsubscribe(subscriptionId: String) {
        viewModelScope.launch {
            runCatching { subscriptionsApi.unsubscribe(subscriptionId) }
            _uiState.update {
                it.copy(
                    subscriptions = it.subscriptions.filterNot { s -> s.id == subscriptionId },
                    newTracksBySubscriptionId = it.newTracksBySubscriptionId - subscriptionId
                )
            }
        }
    }

    fun toggleMute(subscription: ArtistSubscription) {
        viewModelScope.launch {
            val response = runCatching {
                subscriptionsApi.updateSubscription(subscription.id, UpdateSubscriptionRequest(notificationsMuted = !subscription.notificationsMuted))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                val updated = response.body()
                if (updated != null) {
                    _uiState.update { state ->
                        state.copy(subscriptions = state.subscriptions.map { if (it.id == subscription.id) updated else it })
                    }
                }
            }
        }
    }

    fun checkSubscription(subscriptionId: String) {
        if (_uiState.value.checkingSubscriptionIds.contains(subscriptionId)) return
        viewModelScope.launch {
            _uiState.update { it.copy(checkingSubscriptionIds = it.checkingSubscriptionIds + subscriptionId) }
            try {
                val response = subscriptionsApi.checkSubscription(subscriptionId)
                if (response.isSuccessful) {
                    val newTracks = response.body()?.newTracks.orEmpty()
                    _uiState.update {
                        it.copy(newTracksBySubscriptionId = it.newTracksBySubscriptionId + (subscriptionId to newTracks))
                    }
                    if (newTracks.isNotEmpty()) {
                        loadSubscriptions()
                        loadFeed()
                        loadUnreadCount()
                    }
                }
            } catch (e: Exception) {
                // Best-effort -- the row's spinner just clears, matching iOS's own silent-failure check button.
            } finally {
                _uiState.update { it.copy(checkingSubscriptionIds = it.checkingSubscriptionIds - subscriptionId) }
            }
        }
    }

    fun checkAll() {
        val ids = _uiState.value.subscriptions.map { it.id }
        if (ids.isEmpty() || _uiState.value.isCheckingAll) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingAll = true) }
            ids.forEach { id -> checkSubscription(id) }
            _uiState.update { it.copy(isCheckingAll = false) }
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFeed = true, feedError = null) }
            try {
                val response = subscriptionsApi.getFeed()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingFeed = false, feed = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingFeed = false, feedError = "Couldn't load feed (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingFeed = false, feedError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    private fun loadUnreadCount() {
        viewModelScope.launch {
            val response = runCatching { subscriptionsApi.getFeed(limit = 200, unreadOnly = true) }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.update { it.copy(unreadCount = response.body()?.size ?: 0) }
            }
        }
    }

    fun markAllFeedRead() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(feed = state.feed.map { it.copy(isRead = true) }, unreadCount = 0) }
            runCatching { subscriptionsApi.markAllFeedRead() }
        }
    }

    fun deleteFeedItem(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(feed = it.feed.filterNot { item -> item.id == itemId }) }
            runCatching { subscriptionsApi.deleteFeedItem(itemId) }
        }
    }

    /** Marks read (optimistic) and plays -- matches iOS's `SubscriptionFeedView` tap behavior exactly (never "open source"). */
    fun playFeedItem(item: SubscriptionFeedItem) {
        val track = item.track ?: return
        if (!item.isRead) {
            _uiState.update { state ->
                state.copy(
                    feed = state.feed.map { if (it.id == item.id) it.copy(isRead = true) else it },
                    unreadCount = (state.unreadCount - 1).coerceAtLeast(0)
                )
            }
            viewModelScope.launch { runCatching { subscriptionsApi.markFeedItemRead(item.id) } }
        }
        playTrack(track, key = "feed:${item.id}")
    }

    fun playNewTrack(track: BridgeTrack) {
        playTrack(track, key = "new:${track.id}")
    }

    private fun playTrack(track: BridgeTrack, key: String) {
        if (_uiState.value.resolvingKey != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(resolvingKey = key, playbackError = null) }
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
                _uiState.update { it.copy(resolvingKey = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(resolvingKey = null, playbackError = "Couldn't play that track: ${error.message ?: "unknown error"}") }
            }
        }
    }
}
