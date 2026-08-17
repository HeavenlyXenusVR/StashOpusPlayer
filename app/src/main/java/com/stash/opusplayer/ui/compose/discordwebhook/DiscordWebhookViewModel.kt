package com.stash.opusplayer.ui.compose.discordwebhook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.api.DiscordWebhookApi
import com.stash.opusplayer.bridge.api.DiscordWebhookStatus
import com.stash.opusplayer.bridge.api.SetDiscordWebhookRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * Ported from Lumisound's `AccountService+DiscordWebhook.swift`/
 * `DiscordWebhookView.swift`. Distinct from Discord Rich Presence, which
 * needs a local desktop IPC daemon and has no Android equivalent -- ruled
 * out of this port entirely. This posts a "Now Playing" message to a
 * Discord channel via a standard incoming webhook, entirely server-side
 * and fire-and-forget from `POST /user/history` -- the same trigger
 * [com.stash.opusplayer.history.PlayHistoryLogger] already calls for
 * scrobbling, so no new client-side triggering logic is needed here.
 */
@HiltViewModel
class DiscordWebhookViewModel @Inject constructor(
    private val discordWebhookApi: DiscordWebhookApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val status: DiscordWebhookStatus? = null,
        val error: String? = null,

        val webhookUrlInput: String = "",
        val enabledInput: Boolean = true,
        val isSaving: Boolean = false,
        val didSave: Boolean = false,

        val isRemoving: Boolean = false,
        val showRemoveConfirm: Boolean = false
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
                val response = discordWebhookApi.getDiscordWebhook()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update { it.copy(isLoading = false, status = body, enabledInput = body.enabled) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun onWebhookUrlChanged(value: String) {
        _uiState.update { it.copy(webhookUrlInput = value, didSave = false) }
    }

    fun save() {
        val url = _uiState.value.webhookUrlInput.trim()
        if (url.isEmpty() || _uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val response = discordWebhookApi.setDiscordWebhook(SetDiscordWebhookRequest(webhookUrl = url, enabled = _uiState.value.enabledInput))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSaving = false, didSave = true) }
                    load()
                } else {
                    _uiState.update { it.copy(isSaving = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Something went wrong. Check your connection.") }
            }
        }
    }

    /** Toggling `enabled` on an already-configured webhook -- no URL resend needed (the server never gives it back unmasked). */
    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabledInput = enabled) }
        if (_uiState.value.status?.configured != true) return
        viewModelScope.launch {
            try {
                val response = discordWebhookApi.setDiscordWebhook(SetDiscordWebhookRequest(webhookUrl = null, enabled = enabled))
                if (response.isSuccessful) {
                    load()
                } else {
                    _uiState.update { it.copy(error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun requestRemoveConfirm() {
        _uiState.update { it.copy(showRemoveConfirm = true) }
    }

    fun cancelRemoveConfirm() {
        _uiState.update { it.copy(showRemoveConfirm = false) }
    }

    fun confirmRemove() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoving = true, showRemoveConfirm = false) }
            runCatching { discordWebhookApi.deleteDiscordWebhook() }
            _uiState.update { it.copy(isRemoving = false, webhookUrlInput = "", didSave = false) }
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
