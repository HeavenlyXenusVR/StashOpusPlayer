package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT

/** Body for PUT /user/ytdlp-cookies -- raw Netscape-format cookies.txt text, not multipart (main.py's `YtdlpCookiesUploadRequest`). Server caps at 2,000,000 chars and rejects anything that doesn't parse as Netscape-format cookies (400). */
data class YtdlpCookiesUploadRequest(
    @SerializedName("cookies_text") val cookiesText: String
)

/** Response of GET /user/ytdlp-cookies (main.py ~L15047). Cookie contents are never returned -- status only. */
data class YtdlpCookiesStatus(
    val configured: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Response of POST /user/ytdlp-cookies/validate (main.py ~L15110/14954).
 * [status] is one of: `valid`, `valid_no_age_restriction`, `expired`,
 * `incomplete`, `missing`, `invalid`. A `valid`/`valid_no_age_restriction`
 * structural check is followed server-side by a REAL `yt-dlp --simulate`
 * run against a fixed test video, downgrading to `invalid` if that fails
 * -- so this call can take a few seconds, not just a DB read.
 */
data class YtdlpCookiesValidation(
    val status: String,
    val detail: String,
    val missing: List<String> = emptyList(),
    @SerializedName("age_restriction_ready") val ageRestrictionReady: Boolean = false,
    @SerializedName("cookie_count") val cookieCount: Int = 0
)

/**
 * Bring-your-own YouTube session cookies for server-side yt-dlp
 * extraction, ported from `AccountService+YtdlpCookies.swift` /
 * `CookiesFileView.swift`. Lets age-restricted/login-required YouTube
 * content resolve through Browse & Stream / Discovery, which otherwise
 * fails for those videos on an unauthenticated yt-dlp session. The
 * uploaded file is stored server-side for this account only and never
 * echoed back -- [YtdlpCookiesApi.getStatus] returns configured/timestamp
 * only, never the cookie contents themselves.
 */
interface YtdlpCookiesApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/ytdlp-cookies")
    suspend fun getStatus(): Response<YtdlpCookiesStatus>

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/ytdlp-cookies")
    suspend fun setCookies(@Body body: YtdlpCookiesUploadRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/ytdlp-cookies")
    suspend fun deleteCookies(): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/ytdlp-cookies/validate")
    suspend fun validate(): Response<YtdlpCookiesValidation>
}
