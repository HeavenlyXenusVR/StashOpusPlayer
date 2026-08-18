package com.stash.opusplayer.ui.compose.scrobble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.api.ScrobbleLinkTokenRequest
import com.stash.opusplayer.bridge.api.ScrobbleLinkUpdateRequest
import com.stash.opusplayer.bridge.api.ScrobbleLinksResponse
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
 * Ported from Lumisound's AccountService+Scrobbling.swift/ScrobblingView.swift.
 * The actual scrobble POST to Last.fm/Libre.fm/ListenBrainz happens
 * entirely server-side (fire-and-forget from `/user/history` -- see
 * [com.stash.opusplayer.history.PlayHistoryLogger]); everything here only
 * manages which accounts are linked.
 *
 * Last.fm/Libre.fm linking is a manual two-step flow, matching the Swift
 * original exactly (no polling, no deep link/callback -- the bridge has
 * no way to notify the client when the user finishes approving in the
 * browser): request a token, open the server-provided auth URL, then the
 * user comes back and taps "Finish Linking" to attempt the link call --
 * a 400 there just means "not approved yet," not a hard failure. This
 * screen adds one small convenience beyond the iOS original: a pending
 * link is auto-retried once when the screen resumes (e.g. returning from
 * the browser), falling back to the same manual button/error path if that
 * silent attempt also 400s.
 */
@HiltViewModel
class ScrobblingViewModel @Inject constructor(
    private val syncApi: SyncApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val links: ScrobbleLinksResponse? = null,
        val error: String? = null,

        val pendingLastfmToken: String? = null,
        val pendingLastfmAuthUrl: String? = null,
        val isLinkingLastfm: Boolean = false,

        val pendingLibrefmToken: String? = null,
        val pendingLibrefmAuthUrl: String? = null,
        val isLinkingLibrefm: Boolean = false,

        val listenBrainzTokenInput: String = "",
        val isLinkingListenBrainz: Boolean = false,

        val isTogglingEnabled: Boolean = false,
        val isUnlinking: Boolean = false,
        val showUnlinkConfirm: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadLinks()
    }

    fun loadLinks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = syncApi.getScrobbleLinks()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, links = response.body()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    // --- Last.fm ---------------------------------------------------------

    fun startLastfmLink() {
        if (_uiState.value.isLinkingLastfm) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLinkingLastfm = true, error = null) }
            try {
                val response = syncApi.lastfmRequestToken()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(isLinkingLastfm = false, pendingLastfmToken = body.token, pendingLastfmAuthUrl = body.authUrl)
                    }
                } else {
                    _uiState.update { it.copy(isLinkingLastfm = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLinkingLastfm = false, error = "Something went wrong. Check your connection.") }
            }
        }
    }

    /** [silent]: true for the auto-retry-on-resume attempt -- a 400 here should NOT surface as a visible error, the user hasn't necessarily gone to approve it yet. */
    fun finishLastfmLink(silent: Boolean = false) {
        val token = _uiState.value.pendingLastfmToken ?: return
        if (_uiState.value.isLinkingLastfm) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLinkingLastfm = true, error = if (silent) it.error else null) }
            try {
                val response = syncApi.lastfmLink(ScrobbleLinkTokenRequest(token))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLinkingLastfm = false, pendingLastfmToken = null, pendingLastfmAuthUrl = null) }
                    loadLinks()
                } else {
                    _uiState.update {
                        it.copy(
                            isLinkingLastfm = false,
                            error = if (silent) it.error else "Last.fm hasn't approved this link yet -- open the link and approve it, then try again."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLinkingLastfm = false, error = if (silent) it.error else "Something went wrong. Check your connection.") }
            }
        }
    }

    fun cancelLastfmLink() {
        _uiState.update { it.copy(pendingLastfmToken = null, pendingLastfmAuthUrl = null) }
    }

    // --- Libre.fm ---------------------------------------------------------

    fun startLibrefmLink() {
        if (_uiState.value.isLinkingLibrefm) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLinkingLibrefm = true, error = null) }
            try {
                val response = syncApi.librefmRequestToken()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(isLinkingLibrefm = false, pendingLibrefmToken = body.token, pendingLibrefmAuthUrl = body.authUrl)
                    }
                } else {
                    _uiState.update { it.copy(isLinkingLibrefm = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLinkingLibrefm = false, error = "Something went wrong. Check your connection.") }
            }
        }
    }

    fun finishLibrefmLink(silent: Boolean = false) {
        val token = _uiState.value.pendingLibrefmToken ?: return
        if (_uiState.value.isLinkingLibrefm) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLinkingLibrefm = true, error = if (silent) it.error else null) }
            try {
                val response = syncApi.librefmLink(ScrobbleLinkTokenRequest(token))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLinkingLibrefm = false, pendingLibrefmToken = null, pendingLibrefmAuthUrl = null) }
                    loadLinks()
                } else {
                    _uiState.update {
                        it.copy(
                            isLinkingLibrefm = false,
                            error = if (silent) it.error else "Libre.fm hasn't approved this link yet -- open the link and approve it, then try again."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLinkingLibrefm = false, error = if (silent) it.error else "Something went wrong. Check your connection.") }
            }
        }
    }

    fun cancelLibrefmLink() {
        _uiState.update { it.copy(pendingLibrefmToken = null, pendingLibrefmAuthUrl = null) }
    }

    /** Called from the screen's resume effect -- retries whichever link(s) are pending, silently. */
    fun retryPendingLinksOnResume() {
        if (_uiState.value.pendingLastfmToken != null) finishLastfmLink(silent = true)
        if (_uiState.value.pendingLibrefmToken != null) finishLibrefmLink(silent = true)
    }

    // --- ListenBrainz -------------------------------------------------------

    fun onListenBrainzTokenChanged(value: String) {
        _uiState.update { it.copy(listenBrainzTokenInput = value) }
    }

    fun linkListenBrainz() {
        val token = _uiState.value.listenBrainzTokenInput.trim()
        if (token.isBlank() || _uiState.value.isLinkingListenBrainz) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLinkingListenBrainz = true, error = null) }
            try {
                val response = syncApi.updateScrobbleLinks(ScrobbleLinkUpdateRequest(listenBrainzToken = token))
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLinkingListenBrainz = false, listenBrainzTokenInput = "") }
                    loadLinks()
                } else {
                    _uiState.update { it.copy(isLinkingListenBrainz = false, error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLinkingListenBrainz = false, error = "Something went wrong. Check your connection.") }
            }
        }
    }

    // --- Enable toggle / unlink all ---------------------------------------

    fun setEnabled(enabled: Boolean) {
        if (_uiState.value.isTogglingEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingEnabled = true) }
            try {
                val response = syncApi.updateScrobbleLinks(ScrobbleLinkUpdateRequest(enabled = enabled))
                if (response.isSuccessful) {
                    loadLinks()
                } else {
                    _uiState.update { it.copy(error = extractErrorMessage(response)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Something went wrong. Check your connection.") }
            } finally {
                _uiState.update { it.copy(isTogglingEnabled = false) }
            }
        }
    }

    fun requestUnlinkConfirm() {
        _uiState.update { it.copy(showUnlinkConfirm = true) }
    }

    fun cancelUnlinkConfirm() {
        _uiState.update { it.copy(showUnlinkConfirm = false) }
    }

    /** Unlinks EVERY service at once -- the bridge has no per-service unlink route. */
    fun confirmUnlinkAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnlinking = true, showUnlinkConfirm = false) }
            runCatching { syncApi.unlinkAllScrobbling() }
            _uiState.update { it.copy(isUnlinking = false) }
            loadLinks()
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
