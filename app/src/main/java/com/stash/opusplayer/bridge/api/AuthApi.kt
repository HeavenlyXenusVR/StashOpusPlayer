package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
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

/** Body for POST /auth/2fa/login, sent after a login response has [AuthResponse.requiresTwoFactor]. */
data class TwoFactorLoginRequest(
    @SerializedName("pending_token") val pendingToken: String,
    val code: String,
    @SerializedName("device_name") val deviceName: String? = null
)

/** Body for PUT /auth/me. `dateOfBirth` is immutable server-side once set (400 if already set), so it's omitted here. */
data class UpdateMeRequest(
    @SerializedName("display_name") val displayName: String? = null
)

/** Body for POST /auth/change-password. Server force-logs-out every OTHER session on success (current one survives) -- see main.py's ChangePasswordRequest. */
data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

/** Body for POST /auth/delete-account -- password is confirmation, not a new value. Deletion is immediate, no separate confirmation-email/token flow server-side. */
data class DeleteAccountRequest(
    val password: String
)

/** Body for POST /auth/2fa/verify -- the 6-digit TOTP code from the authenticator app, confirming the secret returned by [AuthApi.startTwoFactorSetup] was scanned/entered correctly. */
data class TwoFactorVerifyRequest(
    val code: String
)

/** Body for POST /auth/2fa/disable -- password is confirmation, matching [DeleteAccountRequest]'s shape. */
data class TwoFactorDisableRequest(
    val password: String
)

/** Response of GET /auth/2fa/status (main.py ~L4954). */
data class TwoFactorStatusResponse(
    val enabled: Boolean = false
)

/**
 * Response of GET /auth/discord/start (main.py ~L14342). [authorizeUrl] is
 * Discord's own OAuth consent-screen URL, opened in a Custom Tab; the
 * server-side callback then redirects to `lumisound://discord-login` with
 * either `?success=true&token=...`, `?success=true&requires_2fa=true&
 * pending_token=...` (same pending-token mechanism as password login's
 * 2FA branch), or `?success=false&reason=...` -- see
 * [com.stash.opusplayer.bridge.DiscordLoginEvents].
 */
data class DiscordLoginStartResponse(
    @SerializedName("authorize_url") val authorizeUrl: String
)

/**
 * Response of POST /auth/2fa/setup (main.py ~L4969). [secret] is the raw
 * base32 TOTP secret -- shown as a manual-entry fallback for authenticator
 * apps that can't scan a QR code, same as Lumisound's own UI treats it (not
 * an afterthought). [otpauthUrl] is an `otpauth://totp/...` URI, rendered as
 * a QR code client-side.
 */
data class TwoFactorSetupResponse(
    val secret: String,
    @SerializedName("otpauth_url") val otpauthUrl: String
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
 * Body for PUT /user/privacy (`PrivacyRequest`, main.py ~L5327). Either
 * field may be omitted (left null) to leave that setting unchanged --
 * this client only ever sends [shareListeningActivity]. [aiAssistedSuggestions]
 * is accepted server-side for backward compatibility but no longer read by
 * anything, so it's not modeled here.
 */
data class PrivacyUpdateRequest(
    @SerializedName("share_listening_activity") val shareListeningActivity: Boolean? = null
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
 */
interface AuthApi {

    @Headers("X-Bridge-Auth-Mode: none")
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @Headers("X-Bridge-Auth-Mode: none")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    /** Completes a login that returned [AuthResponse.requiresTwoFactor] -- same response shape as [login]. */
    @Headers("X-Bridge-Auth-Mode: none")
    @POST("auth/2fa/login")
    suspend fun completeTwoFactorLogin(@Body body: TwoFactorLoginRequest): Response<AuthResponse>

    /** No body/params -- mints a login-purpose OAuth state token server-side. */
    @Headers("X-Bridge-Auth-Mode: none")
    @GET("auth/discord/start")
    suspend fun startDiscordLogin(): Response<DiscordLoginStartResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("auth/me")
    suspend fun me(): Response<BridgeUser>

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("auth/me")
    suspend fun updateMe(@Body body: UpdateMeRequest): Response<BridgeUser>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("auth/sessions")
    suspend fun listSessions(): Response<SessionsResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("auth/sessions/{tokenId}")
    suspend fun revokeSession(@Path("tokenId") tokenId: String): Response<Unit>

    /** 204 on success. Every OTHER session gets force-logged-out server-side; the session making this call survives. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    /** 204 on success -- the user row (and everything FK-cascaded from it) is gone immediately, no confirmation-email flow. Caller must clear its local session afterward, same as Lumisound's client does. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/delete-account")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<Unit>

    /**
     * Raw-body upload, NOT multipart -- confirmed against main.py: the route
     * reads the POST body directly and sniffs JPEG/GIF by magic bytes,
     * ignoring Content-Type entirely (same contract shape as
     * [com.stash.opusplayer.bridge.api.FingerprintApi]). 15MB cap either
     * format, enforced server-side (413 if exceeded). No response body
     * beyond `{"ok": true}` -- callers don't need to parse it, a 2xx is
     * success. `avatar_url` on [BridgeUser] is a dead/unused column; display
     * is always `{baseUrl}/user/avatar/{userId}` fetched directly (public,
     * no auth), not round-tripped through JSON -- see AvatarUrlProvider.
     */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/avatar")
    suspend fun uploadAvatar(@Body body: RequestBody): Response<Unit>

    /** Toggles whether recent plays (title/artist only) are visible to other signed-in users via GET /social/activity and /social/discover. Current value comes back on [BridgeUser.shareListeningActivity] from [me]. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/privacy")
    suspend fun updatePrivacy(@Body body: PrivacyUpdateRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("auth/2fa/status")
    suspend fun getTwoFactorStatus(): Response<TwoFactorStatusResponse>

    /** No body. Generates a fresh secret every call -- the previous secret becomes invalid, matching the server's own semantics (calling this again before finishing [verifyTwoFactorSetup] just restarts setup). */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/2fa/setup")
    suspend fun startTwoFactorSetup(): Response<TwoFactorSetupResponse>

    /** 400 if setup was never started, or if [TwoFactorVerifyRequest.code] is wrong. Success enables 2FA server-side immediately. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/2fa/verify")
    suspend fun verifyTwoFactorSetup(@Body body: TwoFactorVerifyRequest): Response<Unit>

    /** 401 if the password is wrong. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("auth/2fa/disable")
    suspend fun disableTwoFactor(@Body body: TwoFactorDisableRequest): Response<Unit>
}
