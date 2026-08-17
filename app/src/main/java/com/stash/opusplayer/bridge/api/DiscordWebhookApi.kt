package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT

/**
 * Response of GET /user/discord-webhook (main.py ~L14217). [webhookUrl] is
 * always server-masked once configured (truncated to 48 chars + "...") --
 * the full URL is never sent back to the client after the initial PUT,
 * matching Lumisound's `DiscordWebhookView` (it only ever shows the masked
 * value in a read-only `LabeledContent`, never repopulates the text field).
 */
data class DiscordWebhookStatus(
    val configured: Boolean,
    val enabled: Boolean,
    @SerializedName("webhook_url") val webhookUrl: String? = null
)

/**
 * Body for PUT /user/discord-webhook (`DiscordWebhookRequest`, main.py
 * ~L2141). [webhookUrl] null means "don't change the URL, just toggle
 * [enabled]" -- server 400s if that's sent with no existing webhook saved
 * yet. Must be an `https://discord.com/api/webhooks/...`-style URL
 * (validated server-side against a fixed set of discord.com hostnames).
 */
data class SetDiscordWebhookRequest(
    @SerializedName("webhook_url") val webhookUrl: String? = null,
    val enabled: Boolean = true
)

/**
 * Discord "Now Playing" webhook -- distinct from Discord Rich Presence
 * (which needs a desktop IPC daemon and has no Android equivalent, ruled
 * out entirely). This posts a message to a Discord channel via a standard
 * incoming webhook, entirely server-side and fire-and-forget from
 * `POST /user/history` (`_notify_now_playing_discord`, main.py ~L6668) --
 * the exact same trigger [com.stash.opusplayer.history.PlayHistoryLogger]
 * already calls for scrobbling, so linking a webhook here needs zero new
 * client-side triggering logic.
 */
interface DiscordWebhookApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/discord-webhook")
    suspend fun getDiscordWebhook(): Response<DiscordWebhookStatus>

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/discord-webhook")
    suspend fun setDiscordWebhook(@Body body: SetDiscordWebhookRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/discord-webhook")
    suspend fun deleteDiscordWebhook(): Response<Unit>
}
