package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT

/** Response of POST /user/rpc-token (main.py ~L15126). Despite the name, this is a literal 365-day auth session token (same shape/mechanism as any other login), just labeled `device_name = "Discord RPC Bridge"` so it shows up and is revocable in Settings -> Account & Server -> Sessions like any other device -- not a special-purpose polling token. */
data class RpcTokenResponse(
    val token: String,
    @SerializedName("expires_at") val expiresAt: String
)

/**
 * Response of GET /user/discord-rpc-config (main.py ~L15158). Field
 * values are already resolved: [discordClientId]/[largeImage]/[smallImage]
 * fall back to the bridge's own shared defaults when [isCustom] is false,
 * so this is always safe to display/use as-is.
 */
data class DiscordRpcConfig(
    val configured: Boolean = false,
    val enabled: Boolean = false,
    @SerializedName("discord_client_id") val discordClientId: String? = null,
    @SerializedName("large_image") val largeImage: String? = null,
    @SerializedName("small_image") val smallImage: String? = null,
    @SerializedName("show_buttons") val showButtons: Boolean = true,
    @SerializedName("is_custom") val isCustom: Boolean = false
)

/** Body for PUT /user/discord-rpc-config. An empty [discordClientId] explicitly clears back to the shared default app -- distinct from `null`, which leaves it unchanged. Server validates a non-empty id against `^\d{15,25}$` (matching a real Discord Application snowflake). */
data class DiscordRpcConfigRequest(
    @SerializedName("discord_client_id") val discordClientId: String? = null,
    @SerializedName("large_image") val largeImage: String? = null,
    @SerializedName("small_image") val smallImage: String? = null,
    @SerializedName("show_buttons") val showButtons: Boolean = true,
    val enabled: Boolean = true
)

/**
 * Discord Rich Presence setup, ported from `DiscordRichPresenceView.swift`.
 * The actual Rich Presence IPC runs on a desktop daemon the user installs
 * separately (`discord-rpc` folder on GitHub, `./install.sh <token>`) --
 * this app's role is only to mint that one-time setup token and manage
 * the enabled/custom-app config the daemon reads. Fully independent
 * server-side of both the Discord account-verification flow
 * ([DiscordVerificationApi]) and the separate Discord Webhook feature --
 * no shared tables/endpoints with either. iOS gates the enable toggle
 * behind having verified Discord, but only client-side (no server-side
 * check on any of these routes), a UX choice this port keeps.
 */
interface DiscordRpcApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/rpc-token")
    suspend fun generateRpcToken(): Response<RpcTokenResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/discord-rpc-config")
    suspend fun getConfig(): Response<DiscordRpcConfig>

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/discord-rpc-config")
    suspend fun setConfig(@Body body: DiscordRpcConfigRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/discord-rpc-config")
    suspend fun deleteConfig(): Response<Unit>
}
