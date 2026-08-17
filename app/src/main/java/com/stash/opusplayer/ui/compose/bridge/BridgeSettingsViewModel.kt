package com.stash.opusplayer.ui.compose.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.BridgeConfig
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.AuthApi
import com.stash.opusplayer.bridge.api.BridgeSession
import com.stash.opusplayer.bridge.api.ChangePasswordRequest
import com.stash.opusplayer.bridge.api.DeleteAccountRequest
import com.stash.opusplayer.bridge.api.LoginRequest
import com.stash.opusplayer.bridge.api.RegisterRequest
import com.stash.opusplayer.bridge.api.TwoFactorDisableRequest
import com.stash.opusplayer.bridge.api.TwoFactorLoginRequest
import com.stash.opusplayer.bridge.api.TwoFactorSetupResponse
import com.stash.opusplayer.bridge.api.TwoFactorVerifyRequest
import com.stash.opusplayer.bridge.api.UpdateMeRequest
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
import retrofit2.Response

/** Which auth form is currently shown in the logged-out state. */
enum class AuthMode { LOGIN, REGISTER }

/**
 * Single UI-state holder for [BridgeSettingsScreen]. Deliberately one flat
 * data class rather than a sealed hierarchy — this screen has one visible
 * shape at a time (server config + account section), just with a handful of
 * independent in-flight/error flags, so a sealed class per "mode" would only
 * add ceremony without buying anything.
 */
data class BridgeSettingsUiState(
    // --- Server configuration section ---
    val baseUrlInput: String = "",
    val apiKeyInput: String = "",
    val isConfigured: Boolean = false,
    val isSavingConfig: Boolean = false,
    val configJustSaved: Boolean = false,

    // --- Account section ---
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val authMode: AuthMode = AuthMode.LOGIN,
    val isAuthLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val authError: String? = null,
    val authInfo: String? = null,

    val loginUsername: String = "",
    val loginPassword: String = "",
    val registerUsername: String = "",
    val registerPassword: String = "",
    val registerEmail: String = "",

    // --- Two-factor continuation (after a login/register returns requires_2fa) ---
    val isTwoFactorPending: Boolean = false,
    val pendingToken: String? = null,
    val twoFactorCode: String = "",

    // --- Profile (shown once logged in) ---
    val displayNameInput: String = "",
    val isSavingDisplayName: Boolean = false,
    val displayNameJustSaved: Boolean = false,
    /** Needed to build the avatar URL (`{baseUrl}/user/avatar/{userId}`) -- see AuthApi.uploadAvatar's doc comment for why this isn't a server-supplied URL field. */
    val userId: String? = null,
    val isUploadingAvatar: Boolean = false,
    val avatarError: String? = null,
    /** Bumped on every successful upload so the UI can cache-bust its avatar image request. */
    val avatarVersion: Long = 0L,
    /** `null` = no avatar set (a 404) or not loaded yet -- not distinguished, matching every other "absence" in this screen. Decoded via `BitmapFactory` -- an animated GIF avatar shows only its first frame, a deliberate simplification (this project has no Coil/GIF-playback dependency to render the rest). */
    val avatarBitmap: android.graphics.Bitmap? = null,

    // --- Sessions (device list) ---
    val sessions: List<BridgeSession> = emptyList(),
    val isLoadingSessions: Boolean = false,

    // --- Change password ---
    val currentPasswordInput: String = "",
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val isChangingPassword: Boolean = false,
    val passwordChangeError: String? = null,
    val passwordChangeSucceeded: Boolean = false,

    // --- Delete account ---
    val deleteAccountPasswordInput: String = "",
    val isDeletingAccount: Boolean = false,
    val deleteAccountError: String? = null,
    val showDeleteAccountConfirm: Boolean = false,

    // --- Privacy ---
    /** Whether recent plays (title/artist only) are visible to other signed-in users via `/social/activity` and `/social/discover` -- see `Settings -> Discover`'s Trending/Community tabs. */
    val shareListeningActivity: Boolean = false,
    val isUpdatingPrivacy: Boolean = false,

    // --- Two-factor setup (enabling/disabling TOTP, distinct from the login-time completion above) ---
    val isTwoFactorEnabled: Boolean = false,
    val isLoadingTwoFactorStatus: Boolean = false,
    /** Non-null only while a fresh setup is in progress (between "Enable 2FA" and either a successful verify or Cancel). */
    val twoFactorSetup: TwoFactorSetupResponse? = null,
    val twoFactorSetupQrBitmap: android.graphics.Bitmap? = null,
    val twoFactorSetupCodeInput: String = "",
    val isStartingTwoFactorSetup: Boolean = false,
    val isVerifyingTwoFactorSetup: Boolean = false,
    val twoFactorSetupError: String? = null,
    val twoFactorDisablePasswordInput: String = "",
    val isDisablingTwoFactor: Boolean = false,
    val twoFactorDisableError: String? = null,
    val showTwoFactorDisableConfirm: Boolean = false
)

@HiltViewModel
class BridgeSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bridgeConfig: BridgeConfig,
    private val tokenStore: BridgeTokenStore,
    private val authApi: AuthApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BridgeSettingsUiState(
            isLoggedIn = tokenStore.isLoggedIn(),
            username = tokenStore.getUsername()
        )
    )
    val uiState: StateFlow<BridgeSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = bridgeConfig.getBaseUrl()
            val apiKey = bridgeConfig.getApiKey()
            _uiState.update {
                it.copy(
                    baseUrlInput = baseUrl,
                    apiKeyInput = apiKey,
                    isConfigured = bridgeConfig.isConfigured()
                )
            }
            if (_uiState.value.isLoggedIn) refreshProfile()
        }
    }

    /** Refreshes [BridgeSettingsUiState.displayNameInput]/[BridgeSettingsUiState.userId] from the server -- called after login and once at startup if already signed in. */
    private suspend fun refreshProfile() {
        val response = runCatching { authApi.me() }.getOrNull() ?: return
        if (!response.isSuccessful) return
        val user = response.body() ?: return
        _uiState.update {
            it.copy(
                displayNameInput = user.displayName.orEmpty(),
                userId = user.id,
                shareListeningActivity = user.shareListeningActivity
            )
        }
        loadSessions()
        loadAvatar(user.id)
        loadTwoFactorStatus()
    }

    fun setShareListeningActivity(enabled: Boolean) {
        if (_uiState.value.isUpdatingPrivacy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPrivacy = true) }
            val response = runCatching {
                authApi.updatePrivacy(com.stash.opusplayer.bridge.api.PrivacyUpdateRequest(shareListeningActivity = enabled))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                _uiState.update { it.copy(shareListeningActivity = enabled) }
            }
            _uiState.update { it.copy(isUpdatingPrivacy = false) }
        }
    }

    /**
     * Fetches `{baseUrl}/user/avatar/{userId}` directly (public endpoint, no
     * auth) rather than through the Retrofit/Hilt-provided client -- that
     * client is built against a fixed placeholder base URL rewritten by an
     * interceptor per-request, which is fine for JSON calls but awkward for
     * a raw-bytes GET consumed by `BitmapFactory`; a plain `HttpURLConnection`
     * against the actually-resolved [BridgeConfig] URL is simpler here.
     */
    private fun loadAvatar(userId: String) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val baseUrl = bridgeConfig.getBaseUrl().trimEnd('/')
                    val connection = java.net.URL("$baseUrl/user/avatar/$userId")
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
            _uiState.update { it.copy(avatarBitmap = bitmap) }
        }
    }

    // --- Sessions ---------------------------------------------------------

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }
            val response = runCatching { authApi.listSessions() }.getOrNull()
            val sessions = if (response?.isSuccessful == true) response.body()?.sessions.orEmpty() else emptyList()
            _uiState.update { it.copy(isLoadingSessions = false, sessions = sessions) }
        }
    }

    /** Revoking the current session logs this device out locally too -- the server accepts revoking your own current session same as any other. */
    fun revokeSession(tokenId: String, isCurrent: Boolean) {
        viewModelScope.launch {
            val ok = runCatching { authApi.revokeSession(tokenId) }.getOrNull()?.isSuccessful == true
            if (!ok) return@launch
            if (isCurrent) {
                logout()
            } else {
                loadSessions()
            }
        }
    }

    // --- Server configuration ---------------------------------------------

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrlInput = value, configJustSaved = false) }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.update { it.copy(apiKeyInput = value, configJustSaved = false) }
    }

    fun saveServerConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingConfig = true, configJustSaved = false) }
            val current = _uiState.value
            bridgeConfig.setBaseUrl(current.baseUrlInput)
            bridgeConfig.setApiKey(current.apiKeyInput)
            _uiState.update {
                it.copy(
                    isSavingConfig = false,
                    configJustSaved = true,
                    isConfigured = bridgeConfig.isConfigured()
                )
            }
        }
    }

    // --- Auth mode / field edits ---------------------------------------------

    fun onAuthModeChanged(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode, authError = null, authInfo = null) }
    }

    fun onLoginUsernameChanged(value: String) {
        _uiState.update { it.copy(loginUsername = value) }
    }

    fun onLoginPasswordChanged(value: String) {
        _uiState.update { it.copy(loginPassword = value) }
    }

    fun onRegisterUsernameChanged(value: String) {
        _uiState.update { it.copy(registerUsername = value) }
    }

    fun onRegisterPasswordChanged(value: String) {
        _uiState.update { it.copy(registerPassword = value) }
    }

    fun onRegisterEmailChanged(value: String) {
        _uiState.update { it.copy(registerEmail = value) }
    }

    fun onTwoFactorCodeChanged(value: String) {
        _uiState.update { it.copy(twoFactorCode = value) }
    }

    /** Backs out of the 2FA prompt back to the plain login form (e.g. wrong account, changed mind). */
    fun cancelTwoFactorLogin() {
        _uiState.update {
            it.copy(isTwoFactorPending = false, pendingToken = null, twoFactorCode = "", authError = null, authInfo = null)
        }
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update { it.copy(displayNameInput = value, displayNameJustSaved = false) }
    }

    fun onCurrentPasswordChanged(value: String) {
        _uiState.update { it.copy(currentPasswordInput = value, passwordChangeError = null, passwordChangeSucceeded = false) }
    }

    fun onNewPasswordChanged(value: String) {
        _uiState.update { it.copy(newPasswordInput = value, passwordChangeError = null, passwordChangeSucceeded = false) }
    }

    fun onConfirmPasswordChanged(value: String) {
        _uiState.update { it.copy(confirmPasswordInput = value, passwordChangeError = null, passwordChangeSucceeded = false) }
    }

    fun onDeleteAccountPasswordChanged(value: String) {
        _uiState.update { it.copy(deleteAccountPasswordInput = value, deleteAccountError = null) }
    }

    // --- Auth actions ---------------------------------------------------------

    fun login() {
        val state = _uiState.value
        if (state.isAuthLoading) return
        val username = state.loginUsername.trim()
        val password = state.loginPassword
        if (username.isBlank() || password.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null, authInfo = null) }
            try {
                val response = authApi.login(LoginRequest(username = username, password = password))
                handleAuthResponse(response, fallbackUsername = username)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = "Something went wrong. Check your connection and Bridge server settings."
                    )
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isAuthLoading) return
        val username = state.registerUsername.trim()
        val password = state.registerPassword
        if (username.isBlank() || password.isBlank()) return
        val email = state.registerEmail.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null, authInfo = null) }
            try {
                val response = authApi.register(
                    RegisterRequest(username = username, password = password, email = email)
                )
                handleAuthResponse(response, fallbackUsername = username)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = "Something went wrong. Check your connection and Bridge server settings."
                    )
                }
            }
        }
    }

    private fun handleAuthResponse(
        response: Response<com.stash.opusplayer.bridge.api.AuthResponse>,
        fallbackUsername: String
    ) {
        if (!response.isSuccessful) {
            _uiState.update { it.copy(isAuthLoading = false, authError = extractErrorMessage(response)) }
            return
        }
        val body = response.body()
        when {
            body?.requiresTwoFactor == true && body.pendingToken != null -> {
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        isTwoFactorPending = true,
                        pendingToken = body.pendingToken,
                        twoFactorCode = "",
                        authError = null,
                        authInfo = "Enter the 6-digit code from your authenticator app."
                    )
                }
            }
            body?.token != null -> {
                val resolvedUsername = body.user?.username ?: fallbackUsername
                tokenStore.saveSession(body.token, resolvedUsername)
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        isLoggedIn = true,
                        username = tokenStore.getUsername(),
                        loginPassword = "",
                        registerPassword = "",
                        isTwoFactorPending = false,
                        pendingToken = null,
                        twoFactorCode = "",
                        displayNameInput = body.user?.displayName.orEmpty(),
                        authError = null,
                        authInfo = null
                    )
                }
                viewModelScope.launch { refreshProfile() }
            }
            else -> {
                _uiState.update {
                    it.copy(isAuthLoading = false, authError = "Unexpected response from server.")
                }
            }
        }
    }

    /** Sends the entered TOTP code + [BridgeSettingsUiState.pendingToken] to finish a 2FA-gated login. */
    fun completeTwoFactorLogin() {
        val state = _uiState.value
        if (state.isAuthLoading) return
        val pendingToken = state.pendingToken ?: return
        val code = state.twoFactorCode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            try {
                val response = authApi.completeTwoFactorLogin(
                    TwoFactorLoginRequest(pendingToken = pendingToken, code = code)
                )
                handleAuthResponse(response, fallbackUsername = state.loginUsername.trim())
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authError = "Something went wrong. Check your connection and Bridge server settings."
                    )
                }
            }
        }
    }

    /** Saves a new display name via PUT /auth/me. */
    fun saveDisplayName() {
        val state = _uiState.value
        if (state.isSavingDisplayName || !state.isLoggedIn) return
        val displayName = state.displayNameInput.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingDisplayName = true, displayNameJustSaved = false) }
            try {
                val response = authApi.updateMe(UpdateMeRequest(displayName = displayName))
                if (response.isSuccessful) {
                    val user = response.body()
                    _uiState.update {
                        it.copy(
                            isSavingDisplayName = false,
                            displayNameJustSaved = true,
                            displayNameInput = user?.displayName.orEmpty()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isSavingDisplayName = false, authError = extractErrorMessage(response))
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isSavingDisplayName = false, authError = "Couldn't save your display name -- check your connection.")
                }
            }
        }
    }

    /** Changing your password force-logs-out every OTHER session server-side -- this device stays signed in. */
    fun changePassword() {
        val state = _uiState.value
        if (state.isChangingPassword) return
        if (state.newPasswordInput.length < 8) {
            _uiState.update { it.copy(passwordChangeError = "New password must be at least 8 characters.") }
            return
        }
        if (state.newPasswordInput != state.confirmPasswordInput) {
            _uiState.update { it.copy(passwordChangeError = "New passwords don't match.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, passwordChangeError = null, passwordChangeSucceeded = false) }
            try {
                val response = authApi.changePassword(
                    ChangePasswordRequest(currentPassword = state.currentPasswordInput, newPassword = state.newPasswordInput)
                )
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            passwordChangeSucceeded = true,
                            currentPasswordInput = "",
                            newPasswordInput = "",
                            confirmPasswordInput = ""
                        )
                    }
                    loadSessions()
                } else {
                    _uiState.update { it.copy(isChangingPassword = false, passwordChangeError = extractErrorMessage(response)) }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isChangingPassword = false, passwordChangeError = "Something went wrong. Check your connection.")
                }
            }
        }
    }

    fun requestDeleteAccountConfirm() {
        if (_uiState.value.deleteAccountPasswordInput.isBlank()) {
            _uiState.update { it.copy(deleteAccountError = "Enter your password to confirm.") }
            return
        }
        _uiState.update { it.copy(showDeleteAccountConfirm = true) }
    }

    fun cancelDeleteAccountConfirm() {
        _uiState.update { it.copy(showDeleteAccountConfirm = false) }
    }

    /** Called only after the second (destructive) confirmation -- deletion is immediate server-side, no undo. */
    fun confirmDeleteAccount() {
        val state = _uiState.value
        if (state.isDeletingAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, deleteAccountError = null) }
            try {
                val response = authApi.deleteAccount(DeleteAccountRequest(password = state.deleteAccountPasswordInput))
                if (response.isSuccessful) {
                    // The account (and this token) is already gone server-side -- clear local session unconditionally.
                    tokenStore.clearToken()
                    _uiState.update {
                        BridgeSettingsUiState(
                            baseUrlInput = it.baseUrlInput,
                            apiKeyInput = it.apiKeyInput,
                            isConfigured = it.isConfigured
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isDeletingAccount = false, showDeleteAccountConfirm = false, deleteAccountError = extractErrorMessage(response))
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isDeletingAccount = false, showDeleteAccountConfirm = false, deleteAccountError = "Something went wrong. Check your connection.")
                }
            }
        }
    }

    /**
     * Reads [uri], sniffs GIF vs. anything-else by magic bytes (mirroring
     * the bridge's own detection -- see AuthApi.uploadAvatar's doc comment),
     * re-encodes non-GIF images to JPEG quality 80 (matching Lumisound's
     * `jpegData(compressionQuality: 0.8)`) rather than uploading an
     * arbitrary original, and uploads the raw bytes.
     */
    fun uploadAvatarFromUri(uri: Uri) {
        if (_uiState.value.isUploadingAvatar) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, avatarError = null) }
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

                val body = uploadBytes.toRequestBody(mediaType.toMediaType())
                val response = authApi.uploadAvatar(body)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isUploadingAvatar = false, avatarVersion = it.avatarVersion + 1) }
                    _uiState.value.userId?.let { loadAvatar(it) }
                } else {
                    _uiState.update { it.copy(isUploadingAvatar = false, avatarError = extractErrorMessage(response)) }
                }
            } catch (t: Exception) {
                _uiState.update {
                    it.copy(isUploadingAvatar = false, avatarError = t.message ?: "Couldn't upload that image.")
                }
            }
        }
    }

    // --- Two-factor setup (enable/disable) ---------------------------------

    fun loadTwoFactorStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTwoFactorStatus = true) }
            val response = runCatching { authApi.getTwoFactorStatus() }.getOrNull()
            val enabled = if (response?.isSuccessful == true) response.body()?.enabled ?: false else _uiState.value.isTwoFactorEnabled
            _uiState.update { it.copy(isLoadingTwoFactorStatus = false, isTwoFactorEnabled = enabled) }
        }
    }

    /** Requests a fresh secret + otpauth URL and enters the "scan or enter manually, then confirm a code" state. */
    fun startTwoFactorSetup() {
        if (_uiState.value.isStartingTwoFactorSetup) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingTwoFactorSetup = true, twoFactorSetupError = null) }
            try {
                val response = authApi.startTwoFactorSetup()
                if (response.isSuccessful) {
                    val setup = response.body()
                    _uiState.update {
                        it.copy(
                            isStartingTwoFactorSetup = false,
                            twoFactorSetup = setup,
                            twoFactorSetupQrBitmap = setup?.otpauthUrl?.let(::renderQrBitmap),
                            twoFactorSetupCodeInput = ""
                        )
                    }
                } else {
                    _uiState.update { it.copy(isStartingTwoFactorSetup = false, twoFactorSetupError = extractErrorMessage(response)) }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isStartingTwoFactorSetup = false, twoFactorSetupError = "Something went wrong. Check your connection.")
                }
            }
        }
    }

    fun onTwoFactorSetupCodeChanged(value: String) {
        _uiState.update { it.copy(twoFactorSetupCodeInput = value.filter { c -> c.isDigit() }.take(6), twoFactorSetupError = null) }
    }

    /** Backs out of an in-progress setup without calling the server -- the secret from [startTwoFactorSetup] simply goes unused. */
    fun cancelTwoFactorSetup() {
        _uiState.update {
            it.copy(twoFactorSetup = null, twoFactorSetupQrBitmap = null, twoFactorSetupCodeInput = "", twoFactorSetupError = null)
        }
    }

    fun verifyTwoFactorSetup() {
        val state = _uiState.value
        if (state.isVerifyingTwoFactorSetup) return
        if (state.twoFactorSetupCodeInput.length != 6) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingTwoFactorSetup = true, twoFactorSetupError = null) }
            try {
                val response = authApi.verifyTwoFactorSetup(TwoFactorVerifyRequest(code = state.twoFactorSetupCodeInput))
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isVerifyingTwoFactorSetup = false,
                            isTwoFactorEnabled = true,
                            twoFactorSetup = null,
                            twoFactorSetupQrBitmap = null,
                            twoFactorSetupCodeInput = ""
                        )
                    }
                } else {
                    _uiState.update { it.copy(isVerifyingTwoFactorSetup = false, twoFactorSetupError = extractErrorMessage(response)) }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isVerifyingTwoFactorSetup = false, twoFactorSetupError = "Something went wrong. Check your connection.")
                }
            }
        }
    }

    fun onTwoFactorDisablePasswordChanged(value: String) {
        _uiState.update { it.copy(twoFactorDisablePasswordInput = value, twoFactorDisableError = null) }
    }

    fun requestDisableTwoFactorConfirm() {
        if (_uiState.value.twoFactorDisablePasswordInput.isBlank()) {
            _uiState.update { it.copy(twoFactorDisableError = "Enter your password to confirm.") }
            return
        }
        _uiState.update { it.copy(showTwoFactorDisableConfirm = true) }
    }

    fun cancelDisableTwoFactorConfirm() {
        _uiState.update { it.copy(showTwoFactorDisableConfirm = false) }
    }

    fun confirmDisableTwoFactor() {
        val state = _uiState.value
        if (state.isDisablingTwoFactor) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDisablingTwoFactor = true, twoFactorDisableError = null) }
            try {
                val response = authApi.disableTwoFactor(TwoFactorDisableRequest(password = state.twoFactorDisablePasswordInput))
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isDisablingTwoFactor = false,
                            isTwoFactorEnabled = false,
                            showTwoFactorDisableConfirm = false,
                            twoFactorDisablePasswordInput = ""
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isDisablingTwoFactor = false, showTwoFactorDisableConfirm = false, twoFactorDisableError = extractErrorMessage(response))
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isDisablingTwoFactor = false, showTwoFactorDisableConfirm = false, twoFactorDisableError = "Something went wrong. Check your connection.")
                }
            }
        }
    }

    /** Renders [content] (an `otpauth://` URI) as a 240dp-ish square black/white QR bitmap via ZXing's encoder -- no camera/scanning dependency needed, just the writer. */
    private fun renderQrBitmap(content: String): Bitmap? = runCatching {
        val size = 480
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()

    fun logout() {
        if (_uiState.value.isLoggingOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            // Best-effort: revoke server-side session, but never let a network
            // failure block signing out locally.
            runCatching { authApi.logout() }
            tokenStore.clearToken()
            _uiState.update {
                it.copy(
                    isLoggingOut = false,
                    isLoggedIn = false,
                    username = null,
                    loginUsername = "",
                    loginPassword = "",
                    registerUsername = "",
                    registerPassword = "",
                    registerEmail = "",
                    isTwoFactorPending = false,
                    pendingToken = null,
                    twoFactorCode = "",
                    displayNameInput = "",
                    displayNameJustSaved = false,
                    userId = null,
                    avatarError = null,
                    avatarBitmap = null,
                    sessions = emptyList(),
                    currentPasswordInput = "",
                    newPasswordInput = "",
                    confirmPasswordInput = "",
                    passwordChangeError = null,
                    passwordChangeSucceeded = false,
                    deleteAccountPasswordInput = "",
                    deleteAccountError = null,
                    showDeleteAccountConfirm = false,
                    authError = null,
                    authInfo = null,
                    isTwoFactorEnabled = false,
                    twoFactorSetup = null,
                    twoFactorSetupQrBitmap = null,
                    twoFactorSetupCodeInput = "",
                    twoFactorSetupError = null,
                    twoFactorDisablePasswordInput = "",
                    twoFactorDisableError = null,
                    showTwoFactorDisableConfirm = false
                )
            }
        }
    }

    /**
     * Best-effort extraction of a human-readable reason from a failed
     * Retrofit [response]. The bridge server (FastAPI) typically returns
     * `{"detail": "..."}` for auth failures and `{"detail": [{"msg": ...}]}`
     * for request-validation errors (422) — both are handled here, falling
     * back to a generic, status-code-flavored message if the body can't be
     * parsed.
     */
    private fun extractErrorMessage(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching {
                val detail = JsonParser.parseString(raw).asJsonObject.get("detail") ?: return@runCatching null
                when {
                    detail.isJsonPrimitive -> detail.asString
                    detail.isJsonArray -> detail.asJsonArray.joinToString("; ") { element ->
                        runCatching { element.asJsonObject.get("msg")?.asString }.getOrNull()
                            ?: element.toString()
                    }
                    else -> null
                }
            }.getOrNull()
            if (!parsed.isNullOrBlank()) return parsed
        }
        return when (response.code()) {
            401 -> "Invalid username or password."
            409 -> "That username is already taken."
            422 -> "The server rejected that request."
            else -> "Something went wrong."
        }
    }
}
