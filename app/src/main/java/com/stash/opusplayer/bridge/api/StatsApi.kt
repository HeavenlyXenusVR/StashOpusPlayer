package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/** One row of [AccountStatsResponse.topArtists] -- no `listen_seconds` here, unlike the year/month-in-review variants below (confirmed different shape server-side). */
data class TopArtistStat(
    val artist: String,
    @SerializedName("play_count") val playCount: Int = 0,
    @SerializedName("listen_seconds") val listenSeconds: Int? = null
)

data class TopTrackStat(
    val title: String,
    val artist: String? = null,
    @SerializedName("play_count") val playCount: Int = 0
)

/** Response of GET /user/stats (main.py ~L6708) -- lifetime totals, no date filter, no query params. */
data class AccountStatsResponse(
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Int = 0,
    @SerializedName("top_artists") val topArtists: List<TopArtistStat> = emptyList(),
    @SerializedName("top_tracks") val topTracks: List<TopTrackStat> = emptyList()
)

/** One row of GET /user/stats/weekly and GET /user/stats/heatmap (main.py ~L10897/10923) -- identical shape, different date windows. Days with zero plays are omitted server-side in both (no zero-fill), and in [YearInReviewResponse.byMonth]/[MonthInReviewResponse.byDay] too, except `by_month` is always zero-filled to exactly 12 entries. */
data class DayStat(
    val date: String,
    val plays: Int = 0,
    @SerializedName("listen_seconds") val listenSeconds: Int = 0
)

data class MonthStat(
    val month: Int,
    val plays: Int = 0,
    @SerializedName("listen_seconds") val listenSeconds: Int = 0
)

/** Response of GET /user/stats/year-in-review (main.py ~L10956). [year] defaults to the current UTC year server-side if omitted. [byMonth] is always exactly 12 entries (zero-filled), unlike [MonthInReviewResponse.byDay]. */
data class YearInReviewResponse(
    val year: Int,
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Int = 0,
    @SerializedName("distinct_artists") val distinctArtists: Int = 0,
    @SerializedName("distinct_tracks") val distinctTracks: Int = 0,
    @SerializedName("average_bpm") val averageBpm: Double? = null,
    @SerializedName("top_artists") val topArtists: List<TopArtistStat> = emptyList(),
    @SerializedName("top_tracks") val topTracks: List<TopTrackStat> = emptyList(),
    @SerializedName("by_month") val byMonth: List<MonthStat> = emptyList(),
    @SerializedName("peak_day") val peakDay: DayStat? = null
)

/** Response of GET /user/stats/month-in-review (main.py ~L11062). [year]/[month] both default to the current UTC year/month independently if omitted. [byDay] is NOT zero-filled (only days with >=1 play appear), unlike [YearInReviewResponse.byMonth]. */
data class MonthInReviewResponse(
    val year: Int,
    val month: Int,
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Int = 0,
    @SerializedName("distinct_artists") val distinctArtists: Int = 0,
    @SerializedName("distinct_tracks") val distinctTracks: Int = 0,
    @SerializedName("average_bpm") val averageBpm: Double? = null,
    @SerializedName("top_artists") val topArtists: List<TopArtistStat> = emptyList(),
    @SerializedName("top_tracks") val topTracks: List<TopTrackStat> = emptyList(),
    @SerializedName("by_day") val byDay: List<DayStat> = emptyList(),
    @SerializedName("peak_day") val peakDay: DayStat? = null
)

/**
 * Listening stats/insights ("Rewind" + "Listening Heatmap" in Lumisound),
 * ported from `AccountService+Stats.swift`/`RewindView.swift`/
 * `ListeningHeatmapView.swift`. All 5 endpoints are pure read
 * aggregations over play history -- no writes, no server-side caching.
 *
 * Deliberately does NOT model Rewind's "Share My Rewind" image export
 * (iOS rasterizes the SwiftUI recap card via `ImageRenderer` and pushes it
 * through the share sheet) -- that's an on-device rendering feature with
 * no bridge contract to port, out of scope for this pass; only the
 * underlying numeric data is modeled here.
 */
interface StatsApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/stats")
    suspend fun getStats(): Response<AccountStatsResponse>

    /** Always the last 7 days -- no query params, no period selection. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/stats/weekly")
    suspend fun getWeeklyStats(): Response<List<DayStat>>

    /** [days]: 7-400, default 365 (server-enforced range). */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/stats/heatmap")
    suspend fun getHeatmap(@Query("days") days: Int = 365): Response<List<DayStat>>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/stats/year-in-review")
    suspend fun getYearInReview(@Query("year") year: Int? = null): Response<YearInReviewResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/stats/month-in-review")
    suspend fun getMonthInReview(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): Response<MonthInReviewResponse>
}
