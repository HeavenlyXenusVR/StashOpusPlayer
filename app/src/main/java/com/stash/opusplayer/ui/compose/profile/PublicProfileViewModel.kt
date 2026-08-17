package com.stash.opusplayer.ui.compose.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.BridgeConfig
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.MusicCompatibility
import com.stash.opusplayer.bridge.api.PostProfileCommentRequest
import com.stash.opusplayer.bridge.api.ProfileComment
import com.stash.opusplayer.bridge.api.PublicSocialProfile
import com.stash.opusplayer.bridge.api.SocialApi
import com.stash.opusplayer.bridge.api.SocialProfileApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Backs [PublicProfileScreen], ported from Lumisound's `ProfileView.swift`/
 * `PublicProfileView.swift`. Covers identity + banner + guestbook +
 * badges/streak + blocking + a friends-only Music Match compatibility
 * score -- bio/accent-color editing, pinned tracks, top genres/artists,
 * visitor stats, and the "blend mix" companion to compatibility are still
 * out of scope (see [PublicSocialProfile]'s doc comment).
 *
 * "Self view" (own profile, with banner edit controls, and never a Block
 * User button) is detected by comparing the loaded profile's username
 * against [BridgeTokenStore.getUsername] -- this app has no stored user id
 * to compare against directly, only the username cached at login.
 */
@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val socialProfileApi: SocialProfileApi,
    private val socialApi: SocialApi,
    private val bridgeConfig: BridgeConfig,
    private val tokenStore: BridgeTokenStore
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val profile: PublicSocialProfile? = null,
        val error: String? = null,
        val bannerBitmap: Bitmap? = null,
        val isLoadingBanner: Boolean = true,
        val isUploadingBanner: Boolean = false,
        val bannerError: String? = null,

        val comments: List<ProfileComment> = emptyList(),
        val isLoadingComments: Boolean = true,
        val newCommentBody: String = "",
        val isPostingComment: Boolean = false,
        val commentError: String? = null,
        val deletingCommentId: String? = null,

        val isBlocking: Boolean = false,
        val showBlockConfirm: Boolean = false,
        val wasBlocked: Boolean = false,

        val compatibility: MusicCompatibility? = null,
        val isLoadingCompatibility: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadedUserId: String? = null

    val currentUsername: String? get() = tokenStore.getUsername()

    fun isSelfProfile(): Boolean {
        val username = _uiState.value.profile?.username ?: return false
        return username == currentUsername
    }

    fun load(userId: String) {
        loadedUserId = userId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = socialProfileApi.getPublicProfile(userId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update { it.copy(isLoading = false, profile = body) }
                    loadBanner(userId)
                    loadComments(userId)
                    if (body.isFriend && body.username != currentUsername) {
                        loadCompatibility(userId)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load this profile (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    /** A 404 (or any non-200) means "no banner set" -- the normal, expected state for most profiles, not an error. */
    private fun loadBanner(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBanner = true) }
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val baseUrl = bridgeConfig.getBaseUrl().trimEnd('/')
                    val connection = java.net.URL("$baseUrl/api/social/profile/banner/$userId")
                        .openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    if (connection.responseCode == 200) {
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            _uiState.update { it.copy(isLoadingBanner = false, bannerBitmap = bitmap) }
        }
    }

    /** GIF-sniffs by magic bytes, re-encodes anything else to JPEG quality 80 -- mirrors [com.stash.opusplayer.ui.compose.bridge.BridgeSettingsViewModel.uploadAvatarFromUri] exactly. */
    fun uploadBannerFromUri(uri: Uri) {
        val userId = _uiState.value.profile?.userId ?: return
        if (_uiState.value.isUploadingBanner) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingBanner = true, bannerError = null) }
            try {
                val rawBytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Couldn't read the selected file.")

                val isGif = rawBytes.size >= 6 &&
                    (rawBytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) == "GIF87a" ||
                        rawBytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) == "GIF89a")

                val (uploadBytes, mediaType) = if (isGif) {
                    rawBytes to "image/gif"
                } else {
                    val jpegBytes = withContext(Dispatchers.Default) {
                        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                            ?: throw IllegalStateException("That doesn't look like a valid image.")
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        bitmap.recycle()
                        out.toByteArray()
                    }
                    jpegBytes to "image/jpeg"
                }

                val requestBody = uploadBytes.toRequestBody(mediaType.toMediaType())
                val response = socialProfileApi.uploadBanner(requestBody)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploadingBanner = false) }
                    loadBanner(userId)
                } else {
                    _uiState.update { it.copy(isUploadingBanner = false, bannerError = "Couldn't upload that image (HTTP ${response.code()}).") }
                }
            } catch (t: Exception) {
                _uiState.update { it.copy(isUploadingBanner = false, bannerError = t.message ?: "Couldn't upload that image.") }
            }
        }
    }

    fun removeBanner() {
        val userId = _uiState.value.profile?.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingBanner = true, bannerError = null) }
            runCatching { socialProfileApi.deleteBanner() }
            _uiState.update { it.copy(isUploadingBanner = false, bannerBitmap = null) }
            loadBanner(userId)
        }
    }

    private fun loadComments(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComments = true) }
            try {
                val response = socialProfileApi.getProfileComments(userId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingComments = false, comments = response.body()?.comments.orEmpty()) }
                } else {
                    _uiState.update { it.copy(isLoadingComments = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingComments = false) }
            }
        }
    }

    /** Only called when the loaded profile is a friend and it isn't a self-view -- the server itself 403s/400s otherwise, but there's no point firing a call that can't succeed. */
    private fun loadCompatibility(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCompatibility = true) }
            try {
                val response = socialApi.getCompatibility(userId)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingCompatibility = false, compatibility = response.body()) }
                } else {
                    _uiState.update { it.copy(isLoadingCompatibility = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingCompatibility = false) }
            }
        }
    }

    fun onNewCommentChanged(value: String) {
        _uiState.update { it.copy(newCommentBody = value.take(280)) }
    }

    fun postComment() {
        val userId = loadedUserId ?: return
        val text = _uiState.value.newCommentBody.trim()
        if (text.isEmpty() || _uiState.value.isPostingComment) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPostingComment = true, commentError = null) }
            try {
                val response = socialProfileApi.postProfileComment(userId, PostProfileCommentRequest(text))
                val posted = response.body()
                if (response.isSuccessful && posted != null) {
                    _uiState.update {
                        it.copy(isPostingComment = false, newCommentBody = "", comments = listOf(posted) + it.comments)
                    }
                } else {
                    _uiState.update { it.copy(isPostingComment = false, commentError = "Couldn't post that comment (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPostingComment = false, commentError = "Something went wrong. Check your connection.") }
            }
        }
    }

    /** Optimistic removal, matching the Swift original -- doesn't roll back on failure, mirrors iOS exactly. */
    fun deleteComment(commentId: String) {
        _uiState.update { it.copy(deletingCommentId = commentId, comments = it.comments.filterNot { c -> c.id == commentId }) }
        viewModelScope.launch {
            runCatching { socialProfileApi.deleteProfileComment(commentId) }
            _uiState.update { it.copy(deletingCommentId = null) }
        }
    }

    // --- Blocking -----------------------------------------------------------

    fun requestBlockConfirm() {
        _uiState.update { it.copy(showBlockConfirm = true) }
    }

    fun cancelBlockConfirm() {
        _uiState.update { it.copy(showBlockConfirm = false) }
    }

    /**
     * Blocking tears down any friendship/pending request server-side and
     * makes the profile mutually invisible (blocked-either-direction is
     * treated as "not found" by `GET /api/social/profile/{id}`) -- matches
     * `PublicProfileView.swift`'s own behavior of reloading the profile
     * after a block, which then shows its "profile isn't available" state.
     * [UiState.wasBlocked] drives that same fallback here.
     */
    fun confirmBlock() {
        val userId = loadedUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBlocking = true, showBlockConfirm = false) }
            val response = runCatching { socialApi.blockUser(userId) }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.update { it.copy(isBlocking = false, wasBlocked = true, profile = null) }
            } else {
                _uiState.update { it.copy(isBlocking = false, error = "Couldn't block that user.") }
            }
        }
    }
}
