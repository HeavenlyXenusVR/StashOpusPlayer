package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT

/** Body for PUT /user/acoustid-api-key. */
data class AcoustIdApiKeyRequest(
    @SerializedName("api_key") val apiKey: String
)

/** Response of GET/PUT-echo /user/acoustid-api-key (main.py ~L14770). [apiKey] is server-masked when [configured], never the raw key. */
data class AcoustIdApiKeyStatus(
    val configured: Boolean = false,
    @SerializedName("api_key") val apiKey: String? = null
)

/**
 * Bring-your-own AcoustID API key, ported from
 * `SettingsView+AcoustIDAPIKeyRows.swift`. Overrides the bridge-side
 * default key [com.stash.opusplayer.identify.AcoustIdService] otherwise
 * uses for "Identify Track" fingerprint lookups. Unlike the YouTube key
 * screen, there is no validate or exposure-check endpoint for this one --
 * PUT only checks the key is non-blank, no live check against the
 * AcoustID API itself.
 */
interface AcoustIdApiKeyApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/acoustid-api-key")
    suspend fun getStatus(): Response<AcoustIdApiKeyStatus>

    /** 400 if [AcoustIdApiKeyRequest.apiKey] is blank. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/acoustid-api-key")
    suspend fun setApiKey(@Body body: AcoustIdApiKeyRequest): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/acoustid-api-key")
    suspend fun deleteApiKey(): Response<Unit>
}
