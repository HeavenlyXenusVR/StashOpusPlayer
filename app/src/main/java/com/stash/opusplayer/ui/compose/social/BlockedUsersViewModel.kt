package com.stash.opusplayer.ui.compose.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.BlockedUser
import com.stash.opusplayer.bridge.api.SocialApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs `Settings -> Blocked Users`, ported from Lumisound's `BlockedUsersView` (`FriendsListView.swift`). */
@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val socialApi: SocialApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val blocked: List<BlockedUser> = emptyList(),
        val error: String? = null,
        val unblockingUserId: String? = null
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
                val response = socialApi.getBlockedUsers()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, blocked = response.body()?.blocked.orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load blocked users.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun unblock(userId: String) {
        if (_uiState.value.unblockingUserId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(unblockingUserId = userId) }
            runCatching { socialApi.unblockUser(userId) }
            _uiState.update { it.copy(unblockingUserId = null, blocked = it.blocked.filterNot { u -> u.userId == userId }) }
        }
    }
}
