package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

// --- Request bodies ---------------------------------------------------------

/** Body for POST /auth/register (RegisterRequest in main.py ~L1616). */
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null,
    @SerializedName("display_name") val displayName: String? = null
)

/** Body for POST /auth/login (LoginRequest in main.py ~L1623). */
data class LoginRequest(
    val username: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String? = null
)

// --- Response bodies ---------------------------------------------------------

/**
 * Maps `_user_dict()` in main.py (~L1896) — the shape returned by
 * /auth/register, /auth/login (non-2FA success), and /auth/me.
 */
data class BridgeUser(
    val id: String,
    val username: String,
    val email: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_login") val lastLogin: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("share_listening_activity") val shareListeningActivity: Boolean = false,
    @SerializedName("ai_assisted_suggestions") val aiAssistedSuggestions: Boolean = false
)

/**
 * Covers both /auth/register|login's normal `{user, token}` shape AND
 * /auth/login's 2FA-required shape (`{requires_2fa: true, pending_token}`) —
 * see `login()` in main.py ~L4034. Callers must check [requiresTwoFactor]
 * first: if true, [user]/[token] are absent and [pendingToken] would need to
 * be sent to /auth/2fa/login to finish signing in. /auth/2fa/login and the
 * rest of the TOTP setup/verify/disable flow are NOT modeled in this pass —
 * see follow-up notes.
 */
data class AuthResponse(
    val user: BridgeUser? = null,
    val token: String? = null,
    @SerializedName("requires_2fa") val requiresTwoFactor: Boolean = false,
    @SerializedName("pending_token") val pendingToken: String? = null
)

/** One row of GET /auth/sessions (main.py ~L4297). */
data class BridgeSession(
    @SerializedName("token_id") val tokenId: String,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("is_current") val isCurrent: Boolean = false
)

data class SessionsResponse(
    val sessions: List<BridgeSession> = emptyList()
)

/**
 * Account + session endpoints on the Lumisound ios-bridge backend (main.py).
 *
 * /auth/register and /auth/login require NEITHER the operator API key nor a
 * user JWT — confirmed by reading main.py: both call only
 * `_check_auth_rate()` (a brute-force throttle), never `check_auth()` or
 * `get_current_user()`. Every other method here requires the user's JWT
 * (`get_current_user`, main.py ~L772), tagged `X-Bridge-Auth-Mode: user` for
 * [com.stash.opusplayer.bridge.BridgeAuthInterceptor].
 *
 * NOT modeled in this pass (left for follow-up): /auth/2fa/* (setup/verify/
 * disable/login), PUT /auth/me, /auth/change-password, /auth/delete-account,
 * avatar upload, privacy settings.
 */
interface AuthApi {

    @Headers("X-Bridge-Auth-Mode: none")
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @Headers("X-Bridge-Auth-Mode: none")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("auth/me")
    suspend fun me(): Response<BridgeUser>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("auth/sessions")
    suspend fun listSessions(): Response<SessionsResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("auth/sessions/{tokenId}")
    suspend fun revokeSession(@Path("tokenId") tokenId: String): Response<Unit>
}
