package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Body for POST /user/podcasts/subscriptions -- [feedUrl] is validated by actually fetching it server-side (catches typos/dead feeds at add-time), so this call can be slow-ish and can fail with a descriptive 400. */
data class PodcastSubscribeRequest(
    @SerializedName("feed_url") val feedUrl: String
)

/** A followed podcast RSS feed (main.py ~L17438-17456 for GET's list shape; POST's 201 response shares the same fields minus [notificationsMuted], which defaults false either way). */
data class PodcastSubscription(
    val id: String,
    @SerializedName("feed_url") val feedUrl: String,
    val title: String? = null,
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("added_at") val addedAt: String? = null,
    @SerializedName("notifications_muted") val notificationsMuted: Boolean = false
)

/** Body for PATCH /user/podcasts/subscriptions/{id} -- mute is the only setting this endpoint exposes server-side, nothing trimmed client-side here (unlike artist-subscription mute, this one genuinely has no other fields to skip). */
data class UpdatePodcastSubscriptionRequest(
    @SerializedName("notifications_muted") val notificationsMuted: Boolean
)

/**
 * One episode of a podcast feed (main.py ~L17370-17378, `_parse_podcast_episodes`).
 * [audioUrl] is a direct enclosure URL -- playable as-is, no bridge
 * resolve/stream step needed at all (unlike every YouTube-sourced track
 * list elsewhere in this app). [guid] is the feed's own episode identity
 * (falls back to [audioUrl] if the feed has no `<guid>`), used as a stable
 * list key since episodes have no bridge-assigned id.
 */
data class PodcastEpisode(
    val guid: String,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("audio_url") val audioUrl: String,
    @SerializedName("duration_seconds") val durationSeconds: Int? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("chapters_url") val chaptersUrl: String? = null
)

/**
 * Podcast subscriptions + episode listing, ported from the podcast slice of
 * Lumisound's account services (a full podcast subsystem also including
 * chapters, per-episode playback-progress sync, OPML import/export, and
 * search/trending discovery -- none of that is modeled here, this pass
 * covers subscribe/list/mute/unsubscribe + episode browsing + direct
 * playback only). Distinct from -- and unrelated to -- artist channel
 * subscriptions ([SubscriptionsApi]): this is RSS-feed-based, not
 * YouTube-channel-based, and episodes play from a direct enclosure URL
 * with no yt-dlp/bridge-stream-resolve step at all.
 */
interface PodcastsApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/podcasts/subscriptions")
    suspend fun subscribe(@Body body: PodcastSubscribeRequest): Response<PodcastSubscription>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/podcasts/subscriptions")
    suspend fun getSubscriptions(): Response<List<PodcastSubscription>>

    @Headers("X-Bridge-Auth-Mode: user")
    @PATCH("user/podcasts/subscriptions/{subscriptionId}")
    suspend fun updateSubscription(
        @Path("subscriptionId") subscriptionId: String,
        @Body body: UpdatePodcastSubscriptionRequest
    ): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/podcasts/subscriptions/{subscriptionId}")
    suspend fun unsubscribe(@Path("subscriptionId") subscriptionId: String): Response<Unit>

    /** Fetches and parses the feed live on every call (no server-side caching) -- [limit] max 200, newest-first (feed order). */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/podcasts/episodes")
    suspend fun getEpisodes(
        @Query("feed_url") feedUrl: String,
        @Query("limit") limit: Int = 50
    ): Response<List<PodcastEpisode>>
}
