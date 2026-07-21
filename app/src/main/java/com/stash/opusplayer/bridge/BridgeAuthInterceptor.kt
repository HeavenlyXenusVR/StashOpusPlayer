package com.stash.opusplayer.bridge

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** The internal, request-scoped marker header each Retrofit API method in
 * `bridge/api/*` tags itself with via `@Headers("X-Bridge-Auth-Mode: ...")`.
 * [BridgeAuthInterceptor] reads it, strips it, and substitutes the real
 * `Authorization` header before the request leaves the device — it is never
 * actually sent to the server. Kept as a plain string constant (rather than
 * referenced from annotations) so every call site's `@Headers` value is a
 * literal Retrofit/Kotlin annotations can accept; the two are simply kept in
 * sync by convention/code review. */
const val BRIDGE_AUTH_MODE_HEADER = "X-Bridge-Auth-Mode"

enum class BridgeAuthMode(val headerValue: String) {
    /** No Authorization header at all — matches /auth/login and
     * /auth/register, which call neither check_auth() nor get_current_user()
     * server-side (confirmed by reading main.py). */
    NONE("none"),

    /** `Authorization: Bearer <IOS_BRIDGE_API_KEY>` — for routes gated by
     * check_auth() (main.py ~L744): /api/search, /api/stream(/proxy),
     * /api/track, /api/resolve, /api/download*, /api/spotify/resolve,
     * /api/playlist/*, /api/lyrics*, /api/radio, /api/search/trending,
     * /api/search/suggestions. Only enforced server-side at all if the
     * operator set IOS_BRIDGE_API_KEY. */
    API_KEY("apikey"),

    /** `Authorization: Bearer <user JWT>` — for routes gated by
     * get_current_user() (main.py ~L772): /auth/me, /auth/logout,
     * /auth/sessions, /user/playlists, /user/favorites, /api/social/*, etc. */
    USER("user");

    companion object {
        fun from(value: String?): BridgeAuthMode = values().firstOrNull { it.headerValue == value } ?: NONE
    }
}

/**
 * Attaches the correct Bearer credential to each outgoing bridge request and
 * clears the stored session on a 401 from a user-JWT-gated request.
 *
 * Reading ios-bridge's actual server code (auth.py / main.py) turned up a
 * design that's easy to get wrong by guessing: IOS_BRIDGE_API_KEY and the
 * user's session JWT are NOT two separate headers sent together on every
 * request. They both ride the SAME `Authorization: Bearer <token>` header
 * slot, but on two disjoint, mutually-exclusive sets of routes:
 *
 *  - `check_auth()` (main.py ~L744) — the operator API key gate. Expects
 *    `Authorization: Bearer <IOS_BRIDGE_API_KEY>`. Missing header -> 401;
 *    wrong value -> 403. Applied only to specific yt-dlp-backed resource
 *    routes (search/stream/track/resolve/download/lyrics/radio/etc.), and
 *    only enforced at all if the operator actually set IOS_BRIDGE_API_KEY.
 *  - `get_current_user()` (main.py ~L772) — the account/session gate.
 *    Expects `Authorization: Bearer <user JWT>` from /auth/login or
 *    /auth/register. Invalid/expired/revoked -> 401 in all cases.
 *  - `/auth/login` and `/auth/register` call NEITHER check — they only run
 *    `_check_auth_rate()` (a brute-force throttle, unrelated to either
 *    Bearer scheme), since a client has no JWT yet at that point and the API
 *    key gate was evidently never meant to cover account creation/login.
 *
 * So a client holding both an API key and a JWT must pick exactly one per
 * request, not send both. Each Retrofit method tags which applies via the
 * [BRIDGE_AUTH_MODE_HEADER] marker; this interceptor converts that tag into
 * the real Authorization header.
 */
class BridgeAuthInterceptor(
    private val config: BridgeConfig,
    private val tokenStore: BridgeTokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val mode = BridgeAuthMode.from(original.header(BRIDGE_AUTH_MODE_HEADER))

        val requestBuilder = original.newBuilder().removeHeader(BRIDGE_AUTH_MODE_HEADER)

        when (mode) {
            BridgeAuthMode.API_KEY -> {
                // DataStore's Flow.first() is a fast in-memory read after the
                // first collection; blocking briefly here is the standard
                // (and simplest correct) way to bridge a suspend config read
                // into OkHttp's synchronous Interceptor.Chain.
                val apiKey = runBlocking { config.getApiKey() }
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }
            }
            BridgeAuthMode.USER -> {
                val token = tokenStore.getToken()
                if (!token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }
            BridgeAuthMode.NONE -> {
                // No Authorization header — matches /auth/login and
                // /auth/register's actual server-side requirements.
            }
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401 && mode == BridgeAuthMode.USER) {
            // No refresh endpoint exists server-side (TOKEN_EXPIRE_DAYS = 30,
            // plain expiry, no /auth/refresh route anywhere in main.py) — a
            // 401 on a user-JWT-gated request means the token is
            // invalid/expired/revoked. Clear it so the app's auth-state check
            // prompts a full re-login next time, rather than pretending a
            // refresh is possible.
            tokenStore.clearToken()
        }

        return response
    }
}
