package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * Maps `_parse_track()` in main.py (~L1057) — the normalized track shape
 * shared by /api/search, /api/track (which additionally sets
 * [description]), and /api/resolve. [isTopicChannel] is absent from the
 * YouTube-Data-API search fast path's hand-built dicts (main.py ~L1990) and
 * simply defaults to false there, which is the correct fallback either way.
 */
data class BridgeTrack(
    val id: String,
    val title: String,
    val artist: String,
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    val source: String,
    @SerializedName("youtube_url") val youtubeUrl: String? = null,
    @SerializedName("is_topic_channel") val isTopicChannel: Boolean = false,
    // Only populated by /api/track (main.py ~L3332); absent/null from
    // /api/search and /api/resolve responses.
    val description: String? = null
)

/** /api/stream's response shape (main.py ~L2117): `{"url": ..., "expires_in": 21600}`. */
data class StreamResponse(
    val url: String,
    @SerializedName("expires_in") val expiresIn: Int = 0
)

/**
 * Search/stream/track/resolve — the yt-dlp-backed resource endpoints. All
 * are gated by the operator's IOS_BRIDGE_API_KEY (`check_auth()` in main.py
 * ~L744), NOT user auth, so every method here is tagged
 * `X-Bridge-Auth-Mode: apikey` — [com.stash.opusplayer.bridge.BridgeAuthInterceptor]
 * sends `Authorization: Bearer <IOS_BRIDGE_API_KEY>` instead of the user's
 * JWT for these.
 *
 * NOT modeled in this pass (left for follow-up): /api/stream/proxy (the
 * re-streamed/proxied alternative to /api/stream — see its docstring in
 * main.py for when it's needed instead), the whole /api/download* job family,
 * /api/spotify/resolve, /api/playlist/expand|links, /api/lyrics*, /api/radio,
 * /api/search/trending, /api/search/suggestions.
 */
interface StreamingApi {

    @Headers("X-Bridge-Auth-Mode: apikey")
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("source") source: String = "youtube"
    ): Response<List<BridgeTrack>>

    @Headers("X-Bridge-Auth-Mode: apikey")
    @GET("api/stream")
    suspend fun stream(
        @Query("id") id: String,
        @Query("source") source: String = "youtube",
        @Query("url") url: String? = null,
        @Query("format") format: String = "m4a"
    ): Response<StreamResponse>

    @Headers("X-Bridge-Auth-Mode: apikey")
    @GET("api/track")
    suspend fun trackMetadata(@Query("url") url: String): Response<BridgeTrack>

    @Headers("X-Bridge-Auth-Mode: apikey")
    @GET("api/resolve")
    suspend fun resolvePlaylist(
        @Query("url") url: String,
        @Query("limit") limit: Int = 100,
        @Query("existing_ids") existingIds: String? = null
    ): Response<List<BridgeTrack>>
}
