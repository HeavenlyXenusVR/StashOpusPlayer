package com.stash.opusplayer.ui.compose.acoustidkey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.AcoustIdApiKeyApi
import com.stash.opusplayer.bridge.api.AcoustIdApiKeyRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs `Settings -> AcoustID API Key`, ported from `SettingsView+AcoustIDAPIKeyRows.swift`. No validate/exposure-check for this key, unlike the YouTube key screen -- the bridge only checks non-blank on save. */
@HiltViewModel
class AcoustIdApiKeyViewModel @Inject constructor(
    private val acoustIdApiKeyApi: AcoustIdApiKeyApi
) : ViewModel() {

    data class UiState(
        val isLoadingStatus: Boolean = true,
        val configured: Boolean = false,
        val maskedKey: String? = null,

        val keyInput: String = "",
        val isSaving: Boolean = false,
        val saveError: String? = null,

        val isDeleting: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStatus = true) }
            val response = runCatching { acoustIdApiKeyApi.getStatus() }.getOrNull()
            if (response?.isSuccessful == true) {
                val body = response.body()
                _uiState.update {
                    it.copy(isLoadingStatus = false, configured = body?.configured ?: false, maskedKey = body?.apiKey)
                }
            } else {
                _uiState.update { it.copy(isLoadingStatus = false) }
            }
        }
    }

    fun onKeyInputChanged(value: String) {
        _uiState.update { it.copy(keyInput = value, saveError = null) }
    }

    fun saveKey() {
        val state = _uiState.value
        if (state.isSaving) return
        val trimmed = state.keyInput.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            try {
                val response = acoustIdApiKeyApi.setApiKey(AcoustIdApiKeyRequest(apiKey = trimmed))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSaving = false, keyInput = "") }
                    loadStatus()
                } else {
                    _uiState.update { it.copy(isSaving = false, saveError = "Couldn't save that key (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun deleteKey() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val ok = runCatching { acoustIdApiKeyApi.deleteApiKey() }.getOrNull()?.isSuccessful == true
            if (ok) {
                _uiState.update { it.copy(isDeleting = false, configured = false, maskedKey = null) }
            } else {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }
}
