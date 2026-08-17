package com.stash.opusplayer.ui.compose.ytdlpcookies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.YtdlpCookiesApi
import com.stash.opusplayer.bridge.api.YtdlpCookiesUploadRequest
import com.stash.opusplayer.bridge.api.YtdlpCookiesValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs `Settings -> yt-dlp Cookies`, ported from
 * `AccountService+YtdlpCookies.swift` / `CookiesFileView.swift`. The
 * cookies.txt file itself is picked and read to a `String` at the
 * Fragment layer (Storage Access Framework, same `GetContent()` +
 * `openInputStream().readBytes()` convention `PlaylistsFragment`'s M3U
 * import already uses) -- this ViewModel only ever sees the resulting
 * text, matching how [com.stash.opusplayer.bridge.api.YtdlpCookiesApi]
 * takes raw text, not a multipart file.
 */
@HiltViewModel
class YtdlpCookiesViewModel @Inject constructor(
    private val ytdlpCookiesApi: YtdlpCookiesApi
) : ViewModel() {

    data class UiState(
        val isLoadingStatus: Boolean = true,
        val configured: Boolean = false,
        val updatedAt: String? = null,

        val isUploading: Boolean = false,
        val uploadError: String? = null,

        val isValidating: Boolean = false,
        val validation: YtdlpCookiesValidation? = null,
        val validationError: String? = null,

        val isDeleting: Boolean = false,
        val showDeleteConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStatus = true) }
            val response = runCatching { ytdlpCookiesApi.getStatus() }.getOrNull()
            if (response?.isSuccessful == true) {
                val body = response.body()
                _uiState.update {
                    it.copy(isLoadingStatus = false, configured = body?.configured ?: false, updatedAt = body?.updatedAt)
                }
            } else {
                _uiState.update { it.copy(isLoadingStatus = false) }
            }
        }
    }

    /** [cookiesText] is the already-read contents of a user-picked cookies.txt file. Clears any previous validation result, matching iOS's own "upload invalidates the last check" behavior. */
    fun uploadCookies(cookiesText: String) {
        if (_uiState.value.isUploading) return
        val text = cookiesText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(uploadError = "That file looks empty.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, uploadError = null, validation = null) }
            try {
                val response = ytdlpCookiesApi.setCookies(YtdlpCookiesUploadRequest(cookiesText = text))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploading = false) }
                    loadStatus()
                } else {
                    _uiState.update { it.copy(isUploading = false, uploadError = "Couldn't upload that file (HTTP ${response.code()}). Make sure it's a Netscape-format cookies.txt export.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, uploadError = "Something went wrong. Check your connection.") }
            }
        }
    }

    /** Real server-side check, not just a DB read -- can take a few seconds since it runs an actual yt-dlp simulate call. */
    fun validate() {
        if (_uiState.value.isValidating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, validationError = null) }
            try {
                val response = ytdlpCookiesApi.validate()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isValidating = false, validation = response.body()) }
                } else {
                    _uiState.update { it.copy(isValidating = false, validationError = "Couldn't validate (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isValidating = false, validationError = "Something went wrong. Check your connection.") }
            }
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
            val ok = runCatching { ytdlpCookiesApi.deleteCookies() }.getOrNull()?.isSuccessful == true
            if (ok) {
                _uiState.update {
                    it.copy(isDeleting = false, showDeleteConfirm = false, configured = false, updatedAt = null, validation = null)
                }
            } else {
                _uiState.update { it.copy(isDeleting = false, showDeleteConfirm = false) }
            }
        }
    }
}
