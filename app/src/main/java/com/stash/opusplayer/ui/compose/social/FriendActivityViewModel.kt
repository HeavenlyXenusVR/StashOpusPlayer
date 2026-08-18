package com.stash.opusplayer.ui.compose.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.FriendActivityEntry
import com.stash.opusplayer.bridge.api.FriendLeaderboardEntry
import com.stash.opusplayer.bridge.api.SocialApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs `Settings -> Friend Activity`, ported from Lumisound's
 * `ActivitySegmentView`/`FriendsActivityFeedView` (`FriendsSegments.swift`/
 * `FriendsListView.swift`) -- a leaderboard card ("Most Active This Week")
 * plus a friends activity feed, loaded together. The server defaults the
 * leaderboard window to 7 days with no client-facing control to change it
 * anywhere in the Swift reference either, so this ViewModel doesn't expose
 * one.
 */
@HiltViewModel
class FriendActivityViewModel @Inject constructor(
    private val socialApi: SocialApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val leaderboard: List<FriendLeaderboardEntry> = emptyList(),
        val activity: List<FriendActivityEntry> = emptyList(),
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val leaderboardResponse = socialApi.getFriendsLeaderboard()
                val activityResponse = socialApi.getFriendsActivity()
                if (leaderboardResponse.isSuccessful && activityResponse.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            leaderboard = leaderboardResponse.body()?.leaderboard.orEmpty(),
                            activity = activityResponse.body()?.activity.orEmpty()
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load friend activity.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }
}
