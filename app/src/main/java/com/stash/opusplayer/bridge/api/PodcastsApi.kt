package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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
 * Body for PUT /user/podcasts/episode-progress (`PodcastEpisodeProgressRequest`,
 * main.py ~L17309). [positionSeconds]/[durationSeconds] are floats
 * server-side but always sent as whole seconds from this client.
 * [completed] should be computed client-side as `duration > 0 &&
 * position >= duration - 5`, matching Lumisound's own
 * `pushPodcastProgressIfNeeded` exactly.
 */
data class PodcastEpisodeProgressRequest(
    @SerializedName("feed_url") val feedUrl: String,
    @SerializedName("episode_guid") val episodeGuid: String,
    val title: String? = null,
    @SerializedName("position_seconds") val positionSeconds: Double = 0.0,
    @SerializedName("duration_seconds") val durationSeconds: Double = 0.0,
    val completed: Boolean = false
)

/** One row of GET /user/podcasts/episode-progress (main.py ~L17579) -- either scoped to one [feedUrl] or, if omitted, every in-progress episode across all subscriptions (the Continue Listening case), newest-updated first. [title] is a cached snapshot from whenever progress was last saved, not re-fetched from the feed. */
data class PodcastEpisodeProgress(
    @SerializedName("episode_guid") val episodeGuid: String,
    @SerializedName("feed_url") val feedUrl: String,
    val title: String? = null,
    @SerializedName("position_seconds") val positionSeconds: Double = 0.0,
    @SerializedName("duration_seconds") val durationSeconds: Double = 0.0,
    val completed: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null
)

/** Body for POST /user/podcasts/import-opml (`ImportOPMLRequest`, main.py ~L17666) -- the raw OPML document as a string, not a file upload. Server bulk-subscribes to every `<outline xmlUrl="...">` found (capped at 100), reusing the same per-feed validation/dedup as a normal single subscribe. */
data class ImportOpmlRequest(
    val opml: String
)

/** Response of POST /user/podcasts/import-opml: `{"added": int, "failed": int, "total": int}` -- [total] is how many feed URLs were found in the document, which can exceed `added + failed` if it had more than 100 (server caps processing at 100). */
data class ImportOpmlResponse(
    val added: Int = 0,
    val failed: Int = 0,
    val total: Int = 0
)

/**
 * One chapter of a "Podcasting 2.0" chapters JSON file (main.py
 * ~L17528-17532, `_fetch_podcast_chapters_sync`). [startTimeSeconds] is a
 * timestamp within the episode, not a duration -- seek the player directly
 * to it. [imageUrl] (a per-chapter image, distinct from the episode/show
 * artwork) is deliberately not modeled/rendered here to keep the chapter
 * list a plain text list, matching the trimmed-down scope of this pass.
 */
data class PodcastChapter(
    @SerializedName("start_time_seconds") val startTimeSeconds: Double,
    val title: String
)

/**
 * One result of GET /podcasts/search or GET /podcasts/trending (main.py
 * ~L17712/17782) -- both iTunes-Search-API-backed, both return this exact
 * same shape. [feedUrl] is directly usable as-is with [PodcastsApi.subscribe].
 * Trending is server-filtered to exclude shows the caller is already
 * subscribed to; search is not (searching for an already-subscribed show
 * is a normal, harmless thing to do -- [PodcastsApi.subscribe]'s own
 * `ON CONFLICT` upsert makes re-subscribing a no-op either way).
 */
data class PodcastSearchResult(
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("feed_url") val feedUrl: String,
    @SerializedName("artwork_url") val artworkUrl: String? = null
)

/**
 * The complete podcast feature set: subscriptions + episode listing +
 * playback-progress sync + chapters + search/trending discovery + OPML
 * import/export, ported from the podcast slice of Lumisound's account
 * services. Distinct from -- and unrelated to -- artist channel
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

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/podcasts/episode-progress")
    suspend fun updateEpisodeProgress(@Body body: PodcastEpisodeProgressRequest): Response<Unit>

    /** [feedUrl] null fetches the cross-feed "in progress everywhere" view instead of one feed's progress -- see [PodcastEpisodeProgress]'s doc comment. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/podcasts/episode-progress")
    suspend fun getEpisodeProgress(
        @Query("feed_url") feedUrl: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<PodcastEpisodeProgress>>

    /** [chaptersUrl] comes from [PodcastEpisode.chaptersUrl] -- null there means the episode has no Podcasting 2.0 chapters file, don't call this. A separate on-demand call per the bridge's own doc comment, not inlined into the episode list. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/podcasts/chapters")
    suspend fun getChapters(@Query("chapters_url") chaptersUrl: String): Response<List<PodcastChapter>>

    /** Not under `/user/` (matches the bridge's own routing) but still JWT-gated like everything else here. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("podcasts/search")
    suspend fun searchPodcasts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<List<PodcastSearchResult>>

    /** Apple's public top-podcasts chart, server-filtered to exclude shows the caller already follows. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("podcasts/trending")
    suspend fun getTrendingPodcasts(@Query("limit") limit: Int = 20): Response<List<PodcastSearchResult>>

    /**
     * Raw OPML XML text (`Content-Type: text/x-opml+xml`), not JSON --
     * [ResponseBody] deliberately bypasses this Retrofit instance's Gson
     * converter (which would otherwise fail trying to parse XML as JSON).
     * Unlike the avatar/banner raw-bytes GETs elsewhere in this app (which
     * use a plain `HttpURLConnection` to sidestep the same problem), this
     * goes through Retrofit normally -- `ResponseBody` is one of the types
     * Retrofit always handles specially regardless of the configured
     * converter, so the shared OkHttp client's `BridgeAuthInterceptor`
     * (auth header, base-URL rewriting) still applies automatically here,
     * which the manual-HttpURLConnection approach has to duplicate by hand.
     */
    @Streaming
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/podcasts/export-opml")
    suspend fun exportOpml(): Response<ResponseBody>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/podcasts/import-opml")
    suspend fun importOpml(@Body body: ImportOpmlRequest): Response<ImportOpmlResponse>
}
