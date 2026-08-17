package com.stash.opusplayer.bridge.api

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Track identification via the Lumisound ios-bridge's AcoustID proxy
 * (main.py `/api/fingerprint/identify`). Deliberately NOT `@Multipart` --
 * confirmed against both the iOS client and the actual bridge route: the
 * request body is the raw audio bytes themselves (`Content-Type:
 * application/octet-stream`), with the source extension as a query param
 * (`ext`, used only so the server's ffmpeg/fpcalc invocation can sniff the
 * right demuxer) -- there is no form field/boundary at all. The server runs
 * real Chromaprint fingerprinting (`fpcalc`) and queries api.acoustid.org;
 * nothing is computed on-device.
 */
interface FingerprintApi {

    /** Body's Content-Type must be set to "application/octet-stream" by the caller (see AcoustIdService). */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/fingerprint/identify")
    suspend fun identify(@Query("ext") ext: String, @Body body: RequestBody): Response<IdentifyResponse>
}

/** Maps main.py's exact response shape: `{"matched": false}` or `{"matched": true, "title": ..., "artist": ..., "album": ..., "score": ...}`. */
data class IdentifyResponse(
    val matched: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val score: Double? = null
)
