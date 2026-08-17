package com.stash.opusplayer.ui.compose.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.BridgeFriend
import com.stash.opusplayer.bridge.api.FriendNicknameUpdate
import com.stash.opusplayer.bridge.api.FriendRequestCreate
import com.stash.opusplayer.bridge.api.FriendRequestEntry
import com.stash.opusplayer.bridge.api.FriendSuggestion
import com.stash.opusplayer.bridge.api.FriendTagCreate
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
    val pendingRequestActionIds: Set<String> = emptySet(),

    val editingFriendId: String? = null,
    val editNicknameInput: String = "",
    val editNewTagInput: String = "",
    val allTagNames: List<String> = emptyList(),
    val isSavingEdit: Boolean = false,

    val suggestions: List<FriendSuggestion> = emptyList(),
    val sendingSuggestionIds: Set<String> = emptySet()
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
        loadSuggestions()
    }

    /** Best-effort, separate from the main [refresh] load so a suggestions failure never blocks the friends/requests list itself from showing. */
    private fun loadSuggestions() {
        viewModelScope.launch {
            val response = runCatching { socialApi.getFriendSuggestions() }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.update { it.copy(suggestions = response.body()?.suggestions.orEmpty()) }
            }
        }
    }

    /** Sends a request from a "People You May Know" suggestion row -- reuses the same endpoint as [sendFriendRequest], just by id instead of typed username. */
    fun sendRequestToSuggestion(userId: String) {
        if (_uiState.value.sendingSuggestionIds.contains(userId)) return
        viewModelScope.launch {
            _uiState.update { it.copy(sendingSuggestionIds = it.sendingSuggestionIds + userId) }
            try {
                val response = socialApi.sendFriendRequest(FriendRequestCreate(toUserId = userId))
                if (response.isSuccessful && response.body()?.ok == true) {
                    _uiState.update { it.copy(suggestions = it.suggestions.filterNot { s -> s.userId == userId }) }
                    refreshRequestsOnly()
                }
            } catch (_: Exception) {
                // Best-effort; the row's spinner just clears and the button reappears for a retry.
            } finally {
                _uiState.update { it.copy(sendingSuggestionIds = it.sendingSuggestionIds - userId) }
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

    // --- Per-friend nickname/tags edit dialog --------------------------------

    /**
     * Opens the nickname/tags editor for [friendId], ported from Lumisound's
     * per-friend "..." sheet in `FriendsListView.swift`. [allTagNames] is
     * fetched fresh each time (distinct tag names across ALL friends, for
     * quick-add chips of tags the caller has already used elsewhere) rather
     * than cached, since it can change between visits.
     */
    fun startEditingFriend(friendId: String) {
        val friend = _uiState.value.friends.firstOrNull { it.userId == friendId } ?: return
        _uiState.update {
            it.copy(
                editingFriendId = friendId,
                editNicknameInput = friend.nickname.orEmpty(),
                editNewTagInput = ""
            )
        }
        viewModelScope.launch {
            val response = runCatching { socialApi.getFriendTagNames() }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.update { it.copy(allTagNames = response.body()?.tags.orEmpty()) }
            }
        }
    }

    fun cancelEditingFriend() {
        _uiState.update { it.copy(editingFriendId = null) }
    }

    fun onEditNicknameChanged(value: String) {
        _uiState.update { it.copy(editNicknameInput = value.take(60)) }
    }

    fun onEditNewTagChanged(value: String) {
        _uiState.update { it.copy(editNewTagInput = value.take(40)) }
    }

    fun saveNickname() {
        val friendId = _uiState.value.editingFriendId ?: return
        val nickname = _uiState.value.editNicknameInput.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingEdit = true) }
            val response = runCatching {
                socialApi.setFriendNickname(friendId, FriendNicknameUpdate(nickname.ifBlank { null }))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                updateLocalFriend(friendId) { it.copy(nickname = nickname.ifBlank { null }) }
            }
            _uiState.update { it.copy(isSavingEdit = false) }
        }
    }

    fun addTag(tagName: String) {
        val friendId = _uiState.value.editingFriendId ?: return
        val trimmed = tagName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val response = runCatching { socialApi.addFriendTag(friendId, FriendTagCreate(trimmed)) }.getOrNull()
            if (response?.isSuccessful == true) {
                updateLocalFriend(friendId) { it.copy(tags = (it.tags + trimmed).distinct()) }
                _uiState.update { it.copy(editNewTagInput = "") }
            }
        }
    }

    fun removeTag(tagName: String) {
        val friendId = _uiState.value.editingFriendId ?: return
        viewModelScope.launch {
            val response = runCatching { socialApi.removeFriendTag(friendId, tagName) }.getOrNull()
            if (response?.isSuccessful == true) {
                updateLocalFriend(friendId) { it.copy(tags = it.tags - tagName) }
            }
        }
    }

    private fun updateLocalFriend(friendId: String, transform: (BridgeFriend) -> BridgeFriend) {
        _uiState.update { state ->
            state.copy(friends = state.friends.map { if (it.userId == friendId) transform(it) else it })
        }
    }
}
