package com.stash.opusplayer.ui.compose.discordrpc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.DiscordRpcApi
import com.stash.opusplayer.bridge.api.DiscordRpcConfigRequest
import com.stash.opusplayer.bridge.api.DiscordVerificationApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs `Settings -> Discord Rich Presence`, ported from
 * `DiscordRichPresenceView.swift`. The enable toggle is gated behind
 * having a verified Discord account -- matching iOS, this is a
 * client-side-only UX choice (no server-side dependency between this
 * feature and [DiscordVerificationApi]).
 */
@HiltViewModel
class DiscordRpcViewModel @Inject constructor(
    private val discordRpcApi: DiscordRpcApi,
    private val discordVerificationApi: DiscordVerificationApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isDiscordVerified: Boolean = false,
        val configured: Boolean = false,
        val enabled: Boolean = false,
        val isCustom: Boolean = false,
        val discordClientId: String? = null,
        val largeImage: String? = null,
        val smallImage: String? = null,
        val showButtons: Boolean = true,

        val isTogglingEnabled: Boolean = false,
        val generatedToken: String? = null,
        val isGeneratingToken: Boolean = false,

        val clientIdInput: String = "",
        val largeImageInput: String = "",
        val smallImageInput: String = "",
        val showButtonsInput: Boolean = true,
        val isSavingCustomApp: Boolean = false,
        val customAppError: String? = null,

        val isDeleting: Boolean = false,
        val showDeleteConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val verifiedResponse = runCatching { discordVerificationApi.getVerification() }.getOrNull()
            val isVerified = verifiedResponse?.takeIf { it.isSuccessful }?.body()?.verified ?: false

            val configResponse = runCatching { discordRpcApi.getConfig() }.getOrNull()
            val config = configResponse?.takeIf { it.isSuccessful }?.body()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isDiscordVerified = isVerified,
                    configured = config?.configured ?: false,
                    enabled = config?.enabled ?: false,
                    isCustom = config?.isCustom ?: false,
                    discordClientId = config?.discordClientId,
                    largeImage = config?.largeImage,
                    smallImage = config?.smallImage,
                    showButtons = config?.showButtons ?: true,
                    clientIdInput = if (config?.isCustom == true) config.discordClientId.orEmpty() else "",
                    largeImageInput = if (config?.isCustom == true) config.largeImage.orEmpty() else "",
                    smallImageInput = if (config?.isCustom == true) config.smallImage.orEmpty() else "",
                    showButtonsInput = config?.showButtons ?: true
                )
            }
        }
    }

    /** On first-ever enable, mints a fresh setup token before flipping the config on -- matching iOS's `toggleEnabled`. Disabling never needs a new token. */
    fun setEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (state.isTogglingEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingEnabled = true) }
            if (enabled && !state.configured) {
                generateTokenInternal()
            }
            val response = runCatching {
                discordRpcApi.setConfig(
                    DiscordRpcConfigRequest(
                        discordClientId = if (state.isCustom) state.clientIdInput.ifBlank { null } else null,
                        largeImage = if (state.isCustom) state.largeImageInput.ifBlank { null } else null,
                        smallImage = if (state.isCustom) state.smallImageInput.ifBlank { null } else null,
                        showButtons = state.showButtonsInput,
                        enabled = enabled
                    )
                )
            }.getOrNull()
            _uiState.update { it.copy(isTogglingEnabled = false, enabled = if (response?.isSuccessful == true) enabled else it.enabled) }
            load()
        }
    }

    fun generateNewToken() {
        if (_uiState.value.isGeneratingToken) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingToken = true) }
            generateTokenInternal()
            _uiState.update { it.copy(isGeneratingToken = false) }
        }
    }

    private suspend fun generateTokenInternal() {
        val response = runCatching { discordRpcApi.generateRpcToken() }.getOrNull()
        if (response?.isSuccessful == true) {
            _uiState.update { it.copy(generatedToken = response.body()?.token) }
        }
    }

    fun onClientIdChanged(value: String) {
        _uiState.update { it.copy(clientIdInput = value.filter { c -> c.isDigit() }, customAppError = null) }
    }

    fun onLargeImageChanged(value: String) {
        _uiState.update { it.copy(largeImageInput = value, customAppError = null) }
    }

    fun onSmallImageChanged(value: String) {
        _uiState.update { it.copy(smallImageInput = value, customAppError = null) }
    }

    fun onShowButtonsChanged(value: Boolean) {
        _uiState.update { it.copy(showButtonsInput = value) }
    }

    fun saveCustomApp() {
        val state = _uiState.value
        if (state.isSavingCustomApp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingCustomApp = true, customAppError = null) }
            try {
                val response = discordRpcApi.setConfig(
                    DiscordRpcConfigRequest(
                        discordClientId = state.clientIdInput,
                        largeImage = state.largeImageInput.ifBlank { null },
                        smallImage = state.smallImageInput.ifBlank { null },
                        showButtons = state.showButtonsInput,
                        enabled = state.enabled
                    )
                )
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSavingCustomApp = false) }
                    load()
                } else {
                    _uiState.update { it.copy(isSavingCustomApp = false, customAppError = "Couldn't save (HTTP ${response.code()}). Client ID must be a real Discord Application ID.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingCustomApp = false, customAppError = "Something went wrong. Check your connection.") }
            }
        }
    }

    /** Explicitly clears back to the shared default app -- an empty client id, distinct from leaving it unset. */
    fun switchBackToSharedApp() {
        if (_uiState.value.isSavingCustomApp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingCustomApp = true, customAppError = null) }
            val response = runCatching {
                discordRpcApi.setConfig(DiscordRpcConfigRequest(discordClientId = "", enabled = _uiState.value.enabled))
            }.getOrNull()
            _uiState.update { it.copy(isSavingCustomApp = false) }
            if (response?.isSuccessful == true) load()
        }
    }

    fun requestDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun cancelDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val ok = runCatching { discordRpcApi.deleteConfig() }.getOrNull()?.isSuccessful == true
            _uiState.update { it.copy(isDeleting = false, showDeleteConfirm = false, generatedToken = null) }
            if (ok) load()
        }
    }
}
