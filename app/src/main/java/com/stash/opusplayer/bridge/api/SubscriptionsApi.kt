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

/** Body for POST /user/subscriptions (`SubscribeChannelRequest`, main.py ~L2069). [channelUrl] accepts a raw URL, an @handle, or a plain channel name -- the server resolves it via the YouTube Data API when the user has an API key configured, falling back to storing it as typed otherwise. */
data class SubscribeChannelRequest(
    @SerializedName("channel_url") val channelUrl: String,
    @SerializedName("channel_name") val channelName: String? = null
)

/**
 * A followed YouTube channel (main.py ~L12935-12951 for the full
 * list-response shape). POST's 201 response and PATCH's response share the
 * same fields but omit [uploadFrequencyLabel]/[isStale]/[daysSinceActivity]
 * -- those three are only computed by the list query's extra join, so they
 * default to null there rather than being genuinely absent information.
 */
data class ArtistSubscription(
    val id: String,
    @SerializedName("channel_url") val channelUrl: String,
    @SerializedName("channel_name") val channelName: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("channel_thumbnail") val channelThumbnail: String? = null,
    @SerializedName("last_video_id") val lastVideoId: String? = null,
    @SerializedName("last_checked_at") val lastCheckedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("auto_download") val autoDownload: Boolean = false,
    @SerializedName("destination_folder") val destinationFolder: String? = null,
    @SerializedName("notifications_muted") val notificationsMuted: Boolean = false,
    val category: String? = null,
    @SerializedName("upload_frequency_label") val uploadFrequencyLabel: String? = null,
    @SerializedName("is_stale") val isStale: Boolean = false,
    @SerializedName("days_since_activity") val daysSinceActivity: Int? = null
)

data class SubscriptionsResponse(
    val subscriptions: List<ArtistSubscription> = emptyList()
)

/** Body for PATCH /user/subscriptions/{id} (`UpdateSubscriptionSettingsRequest`, main.py ~L2074) -- this client only ever sends [notificationsMuted] (mute/unmute toggle); [autoDownload]/[destinationFolder]/[category] exist server-side but have no editor UI in this pass (destination-folder editing needs an Android SAF-folder-picker equivalent, out of scope here). */
data class UpdateSubscriptionRequest(
    @SerializedName("notifications_muted") val notificationsMuted: Boolean? = null
)

/** Response of POST /user/subscriptions/{id}/check: `{"new_tracks": [...]}` -- [BridgeTrack]'s exact `_parse_track` shape, same as every other bridge-track list in this app. */
data class SubscriptionCheckResponse(
    @SerializedName("new_tracks") val newTracks: List<BridgeTrack> = emptyList()
)

/**
 * One row of GET /user/subscriptions/feed (main.py ~L13183). [track] reuses
 * [BridgeTrack] -- confirmed identical `_parse_track` shape -- and is only
 * ever null if the server's stored JSON somehow failed to decode
 * (defensive, extremely unlikely in practice).
 */
data class SubscriptionFeedItem(
    val id: String,
    @SerializedName("subscription_id") val subscriptionId: String,
    val track: BridgeTrack? = null,
    @SerializedName("discovered_at") val discoveredAt: String? = null,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("channel_name") val channelName: String? = null,
    @SerializedName("channel_thumbnail") val channelThumbnail: String? = null
)

/**
 * Channel subscriptions + a "new releases" feed, ported from Lumisound's
 * `AccountService+Subscriptions.swift`/`SubscriptionsView.swift`/
 * `SubscriptionFeedView.swift`. A subscription is a followed YouTube
 * channel (NOT the separate, unrelated "tracked playlist" feature also
 * shown on the same iOS screen -- out of scope here). Checking is both
 * automatic server-side (every ~4 hours via a background polling loop,
 * confirmed in main.py) and user-triggerable per-subscription or all-at-
 * once, matching iOS's own "Check"/"Check All" buttons -- there's no need
 * for this client to run its own background polling since the server
 * already keeps the feed populated independently of the app being open.
 *
 * Deliberately does NOT model the per-subscription settings sheet's
 * auto-download/destination-folder/category fields beyond mute -- those
 * need Android-specific UI (a folder picker, category management) this
 * pass doesn't build; the mute toggle alone covers the most common
 * per-subscription action.
 */
interface SubscriptionsApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/subscriptions")
    suspend fun subscribe(@Body body: SubscribeChannelRequest): Response<ArtistSubscription>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/subscriptions")
    suspend fun getSubscriptions(): Response<SubscriptionsResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @PATCH("user/subscriptions/{subId}")
    suspend fun updateSubscription(
        @Path("subId") subId: String,
        @Body body: UpdateSubscriptionRequest
    ): Response<ArtistSubscription>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/subscriptions/{subId}")
    suspend fun unsubscribe(@Path("subId") subId: String): Response<Unit>

    /** Synchronous, not queued -- resolves via the YouTube Data API (if configured) or yt-dlp fallback right on this call, may take a few seconds. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/subscriptions/{subId}/check")
    suspend fun checkSubscription(@Path("subId") subId: String): Response<SubscriptionCheckResponse>

    /** [unreadOnly]: filters to unread rows only -- used for a cheap unread-count fetch (e.g. `limit=200, unreadOnly=true` then count the array), same as Lumisound's own badge computation. No cursor/offset pagination, just a flat [limit] (max 200), newest-first. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/subscriptions/feed")
    suspend fun getFeed(
        @Query("limit") limit: Int = 50,
        @Query("unread_only") unreadOnly: Boolean = false
    ): Response<List<SubscriptionFeedItem>>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/subscriptions/feed/read-all")
    suspend fun markAllFeedRead(): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/subscriptions/feed/{itemId}/read")
    suspend fun markFeedItemRead(@Path("itemId") itemId: String): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/subscriptions/feed/{itemId}")
    suspend fun deleteFeedItem(@Path("itemId") itemId: String): Response<Unit>
}
