package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * One group of GET /user/on-this-day (main.py ~L6775). [tracks] reuses
 * [BridgeTrack] -- same `_parse_track` shape as the discover-mix/search/
 * resolve endpoints -- but [BridgeTrack.durationSeconds]/[BridgeTrack.thumbnailUrl]
 * are always 0/"" here (not derivable from play-history rows alone).
 */
data class OnThisDayGroup(
    @SerializedName("years_ago") val yearsAgo: Int,
    val year: Int,
    val tracks: List<BridgeTrack> = emptyList()
)

/**
 * Response of GET /api/artist/bio (main.py ~L6898). Despite the `/api/`
 * prefix (elsewhere meaning operator-API-key auth, see [StreamingApi]'s
 * class doc), this route is JWT user-auth-gated like the rest of this file
 * -- confirmed against main.py directly, and matches Lumisound's own
 * `AccountService+ArtistBio.swift` guarding on login state. Never 404s --
 * a not-found artist is a 200 with only [found] = false and every other
 * field null/absent (server negative-caches this for 30 days too, so a
 * bad/misspelled name won't hammer MusicBrainz/Wikipedia on every repeat
 * visit).
 */
data class ArtistBioResponse(
    val found: Boolean,
    val name: String? = null,
    val bio: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("wikipedia_url") val wikipediaUrl: String? = null,
    @SerializedName("artist_type") val artistType: String? = null,
    val country: String? = null,
    @SerializedName("begin_date") val beginDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Discover Mix, On This Day, and Artist Bio -- three read-only, JWT-gated
 * endpoints with no Stash UI before this pass (confirmed via grep: zero
 * prior references anywhere in this app). All three needed no bridge
 * changes. Kept as a separate interface from [SyncApi] (which is already
 * 400+ lines) rather than folded in, since this is a distinct feature area
 * (discovery/recall, not account sync).
 *
 * [getDiscoverMix]/[getOnThisDay] return metadata only -- [BridgeTrack.id]/
 * [BridgeTrack.source]/[BridgeTrack.youtubeUrl] must be resolved to a
 * playable URL via [StreamingApi.stream] (through
 * [com.stash.opusplayer.bridge.BridgeStreamResolver]) before playback, same
 * two-step flow every other bridge-track list in this app already uses --
 * neither endpoint embeds a directly-playable stream URL.
 */
interface DiscoveryApi {

    /** Recomputed fresh on every call server-side (a live yt-dlp search seeded by the user's top-3 most-played artists) -- there is no server-side cache to invalidate, unlike [getArtistBio]. Empty array if the user has no play history yet. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/discover-mix")
    suspend fun getDiscoverMix(@Query("limit") limit: Int = 20): Response<List<BridgeTrack>>

    /** Grouped by year (most recent past year first), each year capped at 15 tracks server-side. Empty array if nothing was played on this calendar date in a past year. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/on-this-day")
    suspend fun getOnThisDay(): Response<List<OnThisDayGroup>>

    /** [name]: exact artist name to look up (not fuzzy-matched client-side -- pass whatever string the UI already has, e.g. the track's artist field). 30-day server-side cache, including negative results. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/artist/bio")
    suspend fun getArtistBio(@Query("name") name: String): Response<ArtistBioResponse>
}
