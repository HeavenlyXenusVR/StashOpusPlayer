package com.stash.opusplayer.ui.compose.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.backup.CloudBackupService
import com.stash.opusplayer.bridge.api.BridgeBackupSummary
import com.stash.opusplayer.bridge.api.SyncApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupHistoryUiState(
    val isLoading: Boolean = true,
    val backups: List<BridgeBackupSummary> = emptyList(),
    val error: String? = null,
    val isRestoring: Boolean = false,
    val restoreMessage: String? = null,
    /** Row awaiting the "this overwrites the server's live favorites/playlists" confirmation. */
    val pendingRestoreId: Long? = null,
    val isClearing: Boolean = false,
    val showClearConfirm: Boolean = false
)

@HiltViewModel
class BackupHistoryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val syncApi: SyncApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupHistoryUiState())
    val uiState: StateFlow<BackupHistoryUiState> = _uiState.asStateFlow()

    init {
        loadBackups()
    }

    fun loadBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = syncApi.listBackups()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, backups = response.body()?.backups.orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load backups (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun requestRestoreConfirm(backupId: Long) {
        _uiState.update { it.copy(pendingRestoreId = backupId) }
    }

    fun cancelRestoreConfirm() {
        _uiState.update { it.copy(pendingRestoreId = null) }
    }

    /** Overwrites the server's LIVE favorites/playlists from this snapshot (server snapshots current state first), then merges the result into the local library. */
    fun confirmRestore() {
        val backupId = _uiState.value.pendingRestoreId ?: return
        if (_uiState.value.isRestoring) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, pendingRestoreId = null, restoreMessage = null, error = null) }
            try {
                val response = syncApi.restoreBackup(backupId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val summary = CloudBackupService.mergeFavoritesAndPlaylists(
                        appContext, body?.favorites.orEmpty(), body?.playlists.orEmpty()
                    )
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            restoreMessage = "Restored ${summary.favoritesMatched}/${summary.favoritesTotal} favorites and " +
                                "${summary.tracksMatched}/${summary.tracksTotal} playlist tracks " +
                                "(${summary.playlistsCreated} new playlist${if (summary.playlistsCreated == 1) "" else "s"})."
                        )
                    }
                    loadBackups()
                } else {
                    _uiState.update { it.copy(isRestoring = false, error = "Restore failed (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRestoring = false, error = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun requestClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun cancelClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClear() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true, showClearConfirm = false) }
            runCatching { syncApi.clearBackups() }
            _uiState.update { it.copy(isClearing = false) }
            loadBackups()
        }
    }
}
