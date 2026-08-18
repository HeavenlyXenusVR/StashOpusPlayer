package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers

/** Response of GET /api/discord/oauth/start (main.py ~L14326): a fully server-built Discord consent-screen URL, open as-is via Chrome Custom Tabs -- no client-side URL construction. */
data class DiscordOAuthStartResponse(
    @SerializedName("authorize_url") val authorizeUrl: String
)

/**
 * Response of GET /api/discord/verification (main.py ~L14589). Proves the
 * user owns a specific Discord account (distinct from
 * [com.stash.opusplayer.bridge.api.DiscordWebhookApi], which just posts to
 * a channel and proves nothing about identity) -- this is what an eventual
 * "Discord Verified" badge would be driven by.
 */
data class DiscordVerificationStatus(
    val verified: Boolean,
    @SerializedName("discord_username") val discordUsername: String? = null,
    @SerializedName("discord_avatar_hash") val discordAvatarHash: String? = null,
    @SerializedName("verified_at") val verifiedAt: String? = null
)

/**
 * Discord account verification via OAuth2 "identify" scope, ported from
 * Lumisound's `DiscordVerificationService.swift`. A standard authorization-
 * code flow: [getOauthStart] hands back Discord's own consent-screen URL
 * (opened in a Chrome Custom Tab -- the Android equivalent of iOS's
 * `ASWebAuthenticationSession`, chosen there specifically so the OS can
 * observe the redirect without the app needing to poll; Custom Tabs +
 * an intent-filter on the same fixed `lumisound://discord-verify` redirect
 * the bridge always uses gives the exact same guarantee here, see
 * [com.stash.opusplayer.ui.MainActivity]'s manifest intent-filter and
 * `onNewIntent` handling). The actual code exchange (with Discord's client
 * secret) happens entirely server-side at `/api/discord/oauth/callback` --
 * this client never sees an authorization code or the exchanged token,
 * only the final linked/not-linked state via [getVerification].
 */
interface DiscordVerificationApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/discord/oauth/start")
    suspend fun getOauthStart(): Response<DiscordOAuthStartResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/discord/verification")
    suspend fun getVerification(): Response<DiscordVerificationStatus>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("api/discord/verification")
    suspend fun deleteVerification(): Response<Unit>
}
