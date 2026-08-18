package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * One row of GET /user/devices (main.py ~L14035) -- a device is just a row
 * in `ios_push_tokens`; there's no explicit device-registration call on
 * this client, since StashOpusPlayer doesn't register a push token (push
 * notifications are unported -- see PORTING_STATUS.md). This means the
 * device list will only ever show OTHER devices (e.g. Lumisound iOS
 * installs with push enabled), never this one -- so playback transfer here
 * is outbound-only: this device can send playback to another, but can't be
 * a transfer target itself. See [DevicesApi.transferPlayback]'s doc.
 */
data class RegisteredDeviceResponse(
    @SerializedName("device_token") val deviceToken: String,
    val platform: String? = null,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null
)

/** Body for POST /user/playback/transfer (main.py ~L14060). */
data class PlaybackTransferRequest(
    @SerializedName("target_device_token") val targetDeviceToken: String,
    @SerializedName("song_id") val songId: String? = null,
    val title: String,
    val artist: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    val source: String? = null,
    @SerializedName("source_id") val sourceId: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("position_seconds") val positionSeconds: Double = 0.0,
    @SerializedName("duration_seconds") val durationSeconds: Double = 0.0,
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    val bpm: Double? = null
)

/**
 * Cross-device playback handoff, ported from
 * `AccountService+PlaybackTransfer.swift`. See [RegisteredDeviceResponse]'s
 * doc for why this is outbound-only on Android: receiving a transfer
 * requires a registered push token to be pushed a `playback_transfer`
 * notification, which needs the Firebase subsystem this project hasn't
 * added (deliberately out of scope, same reasoning as Weekly Mix/push
 * notifications generally).
 */
interface DevicesApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/devices")
    suspend fun listDevices(): Response<List<RegisteredDeviceResponse>>

    /** 204 on success. Also upserts server-side playback state as a fallback if the push itself is dropped -- this client doesn't need to do anything extra for that. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/playback/transfer")
    suspend fun transferPlayback(@Body body: PlaybackTransferRequest): Response<Unit>
}
