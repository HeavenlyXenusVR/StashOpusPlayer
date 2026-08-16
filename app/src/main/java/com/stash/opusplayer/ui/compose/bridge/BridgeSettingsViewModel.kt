package com.stash.opusplayer.ui.compose.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.BridgeConfig
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.AuthApi
import com.stash.opusplayer.bridge.api.LoginRequest
import com.stash.opusplayer.bridge.api.RegisterRequest
import com.stash.opusplayer.bridge.api.TwoFactorLoginRequest
import com.stash.opusplayer.bridge.api.UpdateMeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val displayNameJustSaved: Boolean = false
)

@HiltViewModel
class BridgeSettingsViewModel @Inject constructor(
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

    /** Refreshes [BridgeSettingsUiState.displayNameInput] from the server -- called after login and once at startup if already signed in. */
    private suspend fun refreshProfile() {
        val response = runCatching { authApi.me() }.getOrNull() ?: return
        if (!response.isSuccessful) return
        val user = response.body() ?: return
        _uiState.update { it.copy(displayNameInput = user.displayName.orEmpty()) }
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
                    authError = null,
                    authInfo = null
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
