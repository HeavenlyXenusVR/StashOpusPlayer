package com.stash.opusplayer.ui.compose.discordverify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.api.DiscordVerificationApi
import com.stash.opusplayer.bridge.api.DiscordVerificationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * Ported from Lumisound's `AccountService+DiscordVerification.swift`/
 * implicit `DiscordVerificationService.swift`. Distinct from the
 * [com.stash.opusplayer.ui.compose.discordwebhook] screen -- that one just
 * posts messages to a channel and proves nothing about identity; this one
 * proves the signed-in user owns a specific Discord account via a real
 * OAuth2 "identify"-scope authorization-code flow.
 *
 * This ViewModel only fetches the authorize URL and the current
 * verified/not-verified state -- the actual browser hand-off (Chrome
 * Custom Tabs) is a UI-layer concern handled by the screen/Fragment, same
 * separation [com.stash.opusplayer.ui.compose.scrobble.ScrobblingScreen]
 * already uses for its Last.fm/Libre.fm auth-url handoff.
 */
@HiltViewModel
class DiscordVerificationViewModel @Inject constructor(
    private val discordVerificationApi: DiscordVerificationApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val status: DiscordVerificationStatus? = null,
        val error: String? = null,

        val isStartingVerify: Boolean = false,
        val pendingAuthorizeUrl: String? = null,

        val isUnlinking: Boolean = false,
        val showUnlinkConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Re-checks verification status -- call on screen resume too (e.g. returning from the Custom Tab), same pattern as [com.stash.opusplayer.ui.compose.scrobble.ScrobblingViewModel.retryPendingLinksOnResume]. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = discordVerificationApi.getVerification()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update { it.copy(isLoading = false, status = body, pendingAuthorizeUrl = null) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun startVerify() {
        if (_uiState.value.isStartingVerify) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingVerify = true, error = null) }
            try {
                val response = discordVerificationApi.getOauthStart()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update { it.copy(isStartingVerify = false, pendingAuthorizeUrl = body.authorizeUrl) }
                } else {
                    _uiState.update { it.copy(isStartingVerify = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isStartingVerify = false, error = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun consumePendingAuthorizeUrl() {
        _uiState.update { it.copy(pendingAuthorizeUrl = null) }
    }

    fun requestUnlinkConfirm() {
        _uiState.update { it.copy(showUnlinkConfirm = true) }
    }

    fun cancelUnlinkConfirm() {
        _uiState.update { it.copy(showUnlinkConfirm = false) }
    }

    fun confirmUnlink() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnlinking = true, showUnlinkConfirm = false) }
            runCatching { discordVerificationApi.deleteVerification() }
            _uiState.update { it.copy(isUnlinking = false) }
            load()
        }
    }

    private fun extractErrorMessage(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching {
                JsonParser.parseString(raw).asJsonObject.get("detail")?.asString
            }.getOrNull()
            if (!parsed.isNullOrBlank()) return parsed
        }
        return "Something went wrong (HTTP ${response.code()})."
    }
}
