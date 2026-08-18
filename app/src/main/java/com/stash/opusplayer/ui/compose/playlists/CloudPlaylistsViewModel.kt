package com.stash.opusplayer.ui.compose.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.AddCollaboratorRequest
import com.stash.opusplayer.bridge.api.BridgePlaylist
import com.stash.opusplayer.bridge.api.PlaylistCollaborator
import com.stash.opusplayer.bridge.api.SharedWithMePlaylist
import com.stash.opusplayer.bridge.api.SyncApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * Cloud playlist browsing + collaboration, ported from Lumisound's
 * `CollaborativePlaylistView.swift`/`SharedPlaylistsView.swift` -- except
 * Stash had NO cloud-playlist browsing UI at all before this, so this is a
 * from-scratch screen (`SyncApi` already modeled `getPlaylists`/etc. for a
 * future settings-sync feature, but nothing in the app called it).
 *
 * Deliberately does NOT implement "add track to a cloud playlist" even
 * though `POST /user/playlists/{id}/tracks` exists server-side: confirmed
 * Lumisound's own UI never calls that endpoint either (grepped, zero
 * references) -- collaborative playlists on iOS are share/view/manage-
 * collaborators only, never live co-editing of track lists. Matching that
 * scope here rather than inventing a flow with no reference design.
 *
 * A single Compose "island" hosts both the list and detail screens (no
 * Fragment-level navigation) via [selectedPlaylistId]/[selectedIsOwned] --
 * simplest shape given every other settings screen this app already builds
 * as one flat Compose destination.
 */
@HiltViewModel
class CloudPlaylistsViewModel @Inject constructor(
    private val syncApi: SyncApi,
    private val tokenStore: BridgeTokenStore
) : ViewModel() {

    data class UiState(
        val isLoadingList: Boolean = true,
        val ownPlaylists: List<BridgePlaylist> = emptyList(),
        val sharedPlaylists: List<SharedWithMePlaylist> = emptyList(),
        val listError: String? = null,

        val selectedPlaylistId: String? = null,
        val isLoadingDetail: Boolean = false,
        val detailPlaylist: BridgePlaylist? = null,
        val detailError: String? = null,

        val collaborators: List<PlaylistCollaborator> = emptyList(),
        val isLoadingCollaborators: Boolean = false,
        val newCollaboratorUsername: String = "",
        val newCollaboratorIsEditor: Boolean = true,
        val isAddingCollaborator: Boolean = false,
        val collaboratorActionError: String? = null,
        val removingCollaboratorUserId: String? = null,
        val showLeaveConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val currentUsername: String? get() = tokenStore.getUsername()

    init {
        loadList()
    }

    fun loadList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingList = true, listError = null) }
            try {
                val ownResponse = syncApi.getPlaylists()
                val sharedResponse = syncApi.getSharedWithMePlaylists()
                if (ownResponse.isSuccessful && sharedResponse.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isLoadingList = false,
                            ownPlaylists = ownResponse.body().orEmpty(),
                            sharedPlaylists = sharedResponse.body().orEmpty()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoadingList = false, listError = extractErrorMessage(if (!ownResponse.isSuccessful) ownResponse else sharedResponse))
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingList = false, listError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun openPlaylist(playlistId: String) {
        _uiState.update {
            it.copy(
                selectedPlaylistId = playlistId,
                detailPlaylist = null,
                detailError = null,
                collaborators = emptyList(),
                newCollaboratorUsername = "",
                newCollaboratorIsEditor = true,
                collaboratorActionError = null
            )
        }
        loadDetail(playlistId)
    }

    fun closeDetail() {
        _uiState.update {
            it.copy(selectedPlaylistId = null, detailPlaylist = null, collaborators = emptyList(), detailError = null)
        }
    }

    private fun loadDetail(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, detailError = null) }
            try {
                val response = syncApi.getPlaylist(playlistId)
                val playlist = response.body()
                if (response.isSuccessful && playlist != null) {
                    _uiState.update { it.copy(isLoadingDetail = false, detailPlaylist = playlist) }
                    if (playlist.role == "owner" || playlist.role == "editor" || playlist.role == "viewer") {
                        loadCollaborators(playlistId)
                    }
                } else {
                    _uiState.update { it.copy(isLoadingDetail = false, detailError = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDetail = false, detailError = "Something went wrong. Check your connection.") }
            }
        }
    }

    private fun loadCollaborators(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCollaborators = true) }
            try {
                val response = syncApi.getCollaborators(playlistId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingCollaborators = false, collaborators = response.body().orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingCollaborators = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingCollaborators = false) }
            }
        }
    }

    fun onNewCollaboratorUsernameChanged(value: String) {
        _uiState.update { it.copy(newCollaboratorUsername = value) }
    }

    fun onNewCollaboratorRoleChanged(isEditor: Boolean) {
        _uiState.update { it.copy(newCollaboratorIsEditor = isEditor) }
    }

    fun addCollaborator() {
        val playlistId = _uiState.value.selectedPlaylistId ?: return
        val username = _uiState.value.newCollaboratorUsername.trim()
        if (username.isBlank() || _uiState.value.isAddingCollaborator) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingCollaborator = true, collaboratorActionError = null) }
            try {
                val role = if (_uiState.value.newCollaboratorIsEditor) "editor" else "viewer"
                val response = syncApi.addCollaborator(playlistId, AddCollaboratorRequest(username = username, role = role))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isAddingCollaborator = false, newCollaboratorUsername = "") }
                    loadCollaborators(playlistId)
                } else {
                    _uiState.update { it.copy(isAddingCollaborator = false, collaboratorActionError = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAddingCollaborator = false, collaboratorActionError = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun removeCollaborator(collaboratorUserId: String) {
        val playlistId = _uiState.value.selectedPlaylistId ?: return
        if (_uiState.value.removingCollaboratorUserId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(removingCollaboratorUserId = collaboratorUserId, collaboratorActionError = null) }
            try {
                val response = syncApi.removeCollaborator(playlistId, collaboratorUserId)
                if (response.isSuccessful) {
                    loadCollaborators(playlistId)
                } else {
                    _uiState.update { it.copy(collaboratorActionError = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(collaboratorActionError = "Something went wrong. Check your connection.") }
            } finally {
                _uiState.update { it.copy(removingCollaboratorUserId = null) }
            }
        }
    }

    fun requestLeaveConfirm() {
        _uiState.update { it.copy(showLeaveConfirm = true) }
    }

    fun cancelLeaveConfirm() {
        _uiState.update { it.copy(showLeaveConfirm = false) }
    }

    /** A non-owner collaborator removing themselves ("leave playlist") -- finds their own row by username, then reuses [removeCollaborator]. */
    fun confirmLeavePlaylist() {
        val myUsername = currentUsername
        val myRow = _uiState.value.collaborators.firstOrNull { it.username == myUsername }
        _uiState.update { it.copy(showLeaveConfirm = false) }
        if (myRow != null) {
            removeCollaborator(myRow.userId)
            closeDetail()
            loadList()
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
