package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT

/** Body for PUT /user/youtube-api-key. */
data class YoutubeApiKeyRequest(
    @SerializedName("api_key") val apiKey: String
)

/** Response of GET /user/youtube-api-key (main.py ~L14712). [apiKey] is server-masked (`key[:6] + "..." + key[-4:]`) when [configured], never the raw key. */
data class YoutubeApiKeyStatus(
    val configured: Boolean = false,
    @SerializedName("api_key") val apiKey: String? = null
)

/** Response of POST /youtube/validate-key (main.py ~L14833). [status] is one of `valid`, `invalid`, `quota_exceeded`. */
data class YoutubeApiKeyValidation(
    val status: String
)

/**
 * Response of GET /youtube/key-exposure-check (main.py ~L14863). NOT a
 * normal validity check -- a heuristic that detects signs an
 * already-working key has since been leaked/scraped and is now being
 * abused by someone else (invalidated, referrer/IP-restricted from this
 * server, or its quota exhausted suspiciously fast). [detail] is a
 * ready-to-show explanation when [exposed] is true.
 */
data class YoutubeApiKeyExposureCheck(
    val exposed: Boolean = false,
    val detail: String = ""
)

/**
 * Bring-your-own YouTube Data API v3 key, ported from
 * `YoutubeApiKeyView.swift` / the YouTube-key methods on `AccountService`.
 * A personal key lets full playlists (over yt-dlp's ~205-entry
 * flat-playlist cap) resolve via the real `playlistItems.list` API
 * instead. Falls back to the server's own shared key when no personal key
 * is configured -- this screen is purely an opt-in override, not required
 * for playlists to work at all.
 *
 * iOS also polls [getKeyExposureCheck] automatically every 5 minutes
 * while its Settings screen is open; that always-on polling isn't ported
 * here -- deliberately kept as a manual "Check for Key Exposure" action
 * instead, since a background poller is more surface area than this
 * chunk's scope justifies.
 */
interface YoutubeApiKeyApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/youtube-api-key")
    suspend fun getStatus(): Response<YoutubeApiKeyStatus>

    /** 400 if [YoutubeApiKeyRequest.apiKey] is blank. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/youtube-api-key")
    suspend fun setApiKey(@Body body: YoutubeApiKeyRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/youtube-api-key")
    suspend fun deleteApiKey(): Response<Unit>

    /** A cheap (1-quota-unit) real API call against the configured key -- takes a moment, not just a DB read. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("youtube/validate-key")
    suspend fun validateApiKey(): Response<YoutubeApiKeyValidation>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("youtube/key-exposure-check")
    suspend fun getKeyExposureCheck(): Response<YoutubeApiKeyExposureCheck>
}
