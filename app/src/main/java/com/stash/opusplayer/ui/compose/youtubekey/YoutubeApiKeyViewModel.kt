package com.stash.opusplayer.ui.compose.youtubekey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.YoutubeApiKeyApi
import com.stash.opusplayer.bridge.api.YoutubeApiKeyRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs `Settings -> YouTube API Key`, ported from `YoutubeApiKeyView.swift`. */
@HiltViewModel
class YoutubeApiKeyViewModel @Inject constructor(
    private val youtubeApiKeyApi: YoutubeApiKeyApi
) : ViewModel() {

    data class UiState(
        val isLoadingStatus: Boolean = true,
        val configured: Boolean = false,
        val maskedKey: String? = null,

        val keyInput: String = "",
        val isSaving: Boolean = false,
        val justSaved: Boolean = false,
        val saveError: String? = null,

        val isValidating: Boolean = false,
        val validationResult: String? = null,

        val isCheckingExposure: Boolean = false,
        val exposureWarning: String? = null,

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
            val response = runCatching { youtubeApiKeyApi.getStatus() }.getOrNull()
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
        _uiState.update { it.copy(keyInput = value, justSaved = false, saveError = null) }
    }

    fun saveKey() {
        val state = _uiState.value
        if (state.isSaving) return
        val trimmed = state.keyInput.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null, justSaved = false) }
            try {
                val response = youtubeApiKeyApi.setApiKey(YoutubeApiKeyRequest(apiKey = trimmed))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isSaving = false, justSaved = true, keyInput = "", validationResult = null, exposureWarning = null) }
                    loadStatus()
                } else {
                    _uiState.update { it.copy(isSaving = false, saveError = "Couldn't save that key (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun validateKey() {
        if (_uiState.value.isValidating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, validationResult = null) }
            val response = runCatching { youtubeApiKeyApi.validateApiKey() }.getOrNull()
            val result = if (response?.isSuccessful == true) {
                when (response.body()?.status) {
                    "valid" -> "YouTube API key is valid."
                    "quota_exceeded" -> "YouTube API quota exceeded for today."
                    else -> "YouTube API key is invalid."
                }
            } else {
                "Couldn't validate key -- check your connection."
            }
            _uiState.update { it.copy(isValidating = false, validationResult = result) }
        }
    }

    fun checkKeyExposure() {
        if (_uiState.value.isCheckingExposure) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingExposure = true, exposureWarning = null) }
            val response = runCatching { youtubeApiKeyApi.getKeyExposureCheck() }.getOrNull()
            val body = if (response?.isSuccessful == true) response.body() else null
            _uiState.update {
                it.copy(
                    isCheckingExposure = false,
                    exposureWarning = if (body?.exposed == true) body.detail else "No signs of exposure -- looks fine."
                )
            }
        }
    }

    fun deleteKey() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val ok = runCatching { youtubeApiKeyApi.deleteApiKey() }.getOrNull()?.isSuccessful == true
            if (ok) {
                _uiState.update {
                    it.copy(isDeleting = false, configured = false, maskedKey = null, validationResult = null, exposureWarning = null)
                }
            } else {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }
}
