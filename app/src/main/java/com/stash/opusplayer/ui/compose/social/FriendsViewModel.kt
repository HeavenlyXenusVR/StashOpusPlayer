package com.stash.opusplayer.ui.compose.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.BridgeFriend
import com.stash.opusplayer.bridge.api.FriendRequestCreate
import com.stash.opusplayer.bridge.api.FriendRequestEntry
import com.stash.opusplayer.bridge.api.SocialApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * UI state for [FriendsScreen]. Built against [SocialApi] as it exists today —
 * that interface only models friends list / friend requests / a presence
 * heartbeat (see its own KDoc for what's explicitly NOT covered: profiles,
 * blocking, nicknames/tags, listening-together presence-for-friends,
 * discovery, activity feeds, etc.), so this screen has no concept of those.
 *
 * Notably, neither [BridgeFriend] nor [FriendRequestEntry] carries an
 * online/offline flag, and [SocialApi.updatePresence] only lets the current
 * user push their own presence — it doesn't return friends' presence. So
 * there is no per-friend online dot: the data to draw one doesn't exist yet.
 */
data class FriendsUiState(
    val isLoggedIn: Boolean = true,
    val isLoading: Boolean = false,
    val friends: List<BridgeFriend> = emptyList(),
    val incomingRequests: List<FriendRequestEntry> = emptyList(),
    val outgoingRequests: List<FriendRequestEntry> = emptyList(),
    val errorMessage: String? = null,
    val sendRequestUsername: String = "",
    val isSendingRequest: Boolean = false,
    val sendRequestFeedback: String? = null,
    val pendingRequestActionIds: Set<String> = emptySet()
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val socialApi: SocialApi,
    private val tokenStore: BridgeTokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState(isLoggedIn = tokenStore.isLoggedIn()))
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        if (tokenStore.isLoggedIn()) {
            refresh()
        }
    }

    /** Re-checks login status and (re)loads friends/requests if logged in. */
    fun refresh() {
        val loggedIn = tokenStore.isLoggedIn()
        _uiState.update { it.copy(isLoggedIn = loggedIn) }
        if (!loggedIn) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val friendsResponse = socialApi.listFriends()
                val requestsResponse = socialApi.listFriendRequests()

                if (friendsResponse.isSuccessful && requestsResponse.isSuccessful) {
                    val friends = friendsResponse.body()?.friends.orEmpty()
                    val requests = requestsResponse.body()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            friends = friends,
                            incomingRequests = requests?.incoming.orEmpty(),
                            outgoingRequests = requests?.outgoing.orEmpty(),
                            errorMessage = null
                        )
                    }
                } else {
                    val code = if (!friendsResponse.isSuccessful) friendsResponse.code() else requestsResponse.code()
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Failed to load friends (HTTP $code)")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load friends: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    fun onSendUsernameChange(value: String) {
        _uiState.update { it.copy(sendRequestUsername = value, sendRequestFeedback = null) }
    }

    fun sendFriendRequest() {
        val username = _uiState.value.sendRequestUsername.trim()
        if (username.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingRequest = true, sendRequestFeedback = null) }
            try {
                val response = socialApi.sendFriendRequest(FriendRequestCreate(toUsername = username))
                if (response.isSuccessful && response.body()?.ok == true) {
                    _uiState.update {
                        it.copy(
                            isSendingRequest = false,
                            sendRequestUsername = "",
                            sendRequestFeedback = "Friend request sent to $username"
                        )
                    }
                    refreshRequestsOnly()
                } else {
                    _uiState.update {
                        it.copy(
                            isSendingRequest = false,
                            sendRequestFeedback = "Couldn't send request (HTTP ${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSendingRequest = false,
                        sendRequestFeedback = "Couldn't send request: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun acceptRequest(requestId: String) {
        performRequestAction(requestId) { socialApi.acceptFriendRequest(requestId) }
    }

    fun declineRequest(requestId: String) {
        performRequestAction(requestId) { socialApi.declineFriendRequest(requestId) }
    }

    private fun performRequestAction(requestId: String, action: suspend () -> Response<*>) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingRequestActionIds = it.pendingRequestActionIds + requestId) }
            try {
                val response = action()
                if (response.isSuccessful) {
                    // Refresh both lists: accepting also changes the friends list.
                    refresh()
                } else {
                    _uiState.update {
                        it.copy(errorMessage = "Action failed (HTTP ${response.code()})")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Action failed: ${e.message ?: "unknown error"}")
                }
            } finally {
                _uiState.update { it.copy(pendingRequestActionIds = it.pendingRequestActionIds - requestId) }
            }
        }
    }

    private suspend fun refreshRequestsOnly() {
        try {
            val requestsResponse = socialApi.listFriendRequests()
            if (requestsResponse.isSuccessful) {
                val requests = requestsResponse.body()
                _uiState.update {
                    it.copy(
                        incomingRequests = requests?.incoming.orEmpty(),
                        outgoingRequests = requests?.outgoing.orEmpty()
                    )
                }
            }
        } catch (_: Exception) {
            // Best-effort refresh after sending a request; the send itself already
            // succeeded, so silently skip if this follow-up read fails.
        }
    }
}
