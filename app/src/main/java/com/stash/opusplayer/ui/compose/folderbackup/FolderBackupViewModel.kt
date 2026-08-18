package com.stash.opusplayer.ui.compose.folderbackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.FolderBackupApi
import com.stash.opusplayer.bridge.api.FolderBackupEntrySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Read-only viewer for `GET /user/folder-backups` -- a small addition
 * beyond Lumisound's own scope (iOS has no dedicated restore/viewer UI for
 * this feature at all, only push + fetch internally). No restore action
 * here either: there is no restore endpoint on the bridge for folder
 * backups, and every track's `source_track_id` is always null from this
 * client (see [com.stash.opusplayer.bridge.api.FolderBackupApi]'s class
 * doc) -- this screen exists purely so the feature is visible/verifiable,
 * not to drive a recovery flow.
 */
@HiltViewModel
class FolderBackupViewModel @Inject constructor(
    private val folderBackupApi: FolderBackupApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val folders: List<FolderBackupEntrySnapshot> = emptyList(),
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
                val response = folderBackupApi.getFolderBackups()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update { it.copy(isLoading = false, folders = body.folders) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load folder backups (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }
}
