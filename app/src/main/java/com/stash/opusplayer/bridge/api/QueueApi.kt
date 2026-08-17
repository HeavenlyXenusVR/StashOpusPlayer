package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT

/** One track in a PUT /user/queue request body -- server derives position from array order, no explicit position field needed (main.py ~L13483). */
data class QueueTrackRequest(
    @SerializedName("local_song_id") val localSongId: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = 0
)

data class ReplaceQueueRequest(
    val tracks: List<QueueTrackRequest> = emptyList()
)

/** One row of GET /user/queue (main.py ~L13459), ordered by `position ASC` server-side. */
data class QueueItemResponse(
    val id: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = 0,
    val position: Int? = null
)

/**
 * Cross-device play queue sync, ported from
 * `AccountService+QueueSync.swift`. Mirrors that file's own fire-and-forget
 * shape: [replaceQueue] is meant to be called whenever the local queue
 * changes (best-effort, failures aren't surfaced), and [getQueue] is a pull
 * used to restore/adopt another device's queue. `queue_source` is sent by
 * iOS but confirmed dropped server-side (main.py's `QueueTrackRequest` has
 * no such field) -- not modeled here either.
 */
interface QueueApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/queue")
    suspend fun getQueue(): Response<List<QueueItemResponse>>

    /** Full replace, not a diff/append -- the server deletes all existing rows for this user first. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/queue")
    suspend fun replaceQueue(@Body body: ReplaceQueueRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/queue")
    suspend fun clearQueue(): Response<Unit>
}
