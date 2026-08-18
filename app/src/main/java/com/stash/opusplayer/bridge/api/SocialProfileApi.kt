package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A trimmed subset of GET /api/social/profile/{userId}'s response (main.py
 * ~L15767-15795) -- the full payload also includes top genres/artists,
 * visitor stats, accent colors/glow, avatar decoration/frame, profile
 * effect, and featured playlist. None of that rich profile-customization
 * surface is modeled here; this pass covers identity + banner + guestbook
 * + badges + listening streak + pinned tracks. Only the fields this client
 * actually reads are declared -- Gson silently ignores the rest.
 */
data class PublicSocialProfile(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    val pronouns: String? = null,
    @SerializedName("status_emoji") val statusEmoji: String? = null,
    @SerializedName("status_text") val statusText: String? = null,
    @SerializedName("show_guestbook") val showGuestbook: Boolean = true,
    @SerializedName("is_friend") val isFriend: Boolean = false,
    @SerializedName("member_since") val memberSince: String? = null,
    val badges: List<ProfileBadge> = emptyList(),
    @SerializedName("listening_streak") val listeningStreak: ListeningStreak? = null,
    @SerializedName("pinned_tracks") val pinnedTracks: List<PinnedTrack> = emptyList()
)

/**
 * One entry of [PublicSocialProfile.pinnedTracks] (main.py ~L15779-15782,
 * the `SELECT source_track_id, track_url, title, artist, album` shape from
 * `ios_social_pinned_tracks`). Up to 5 per profile, server-enforced.
 * [sourceTrackId]/[trackUrl] are always null when pinned from this client
 * -- a pinned track picked from Android's plain on-device library has
 * neither a bridge source id nor a streamable URL, same honest scope
 * choice already made for [com.stash.opusplayer.bridge.api.FolderBackupTrack]
 * (metadata-only, nothing here is ever playable from someone else's
 * device -- matches Lumisound's own `PinnedTrackPickerSheet`, which picks
 * from `library.allSongs` rather than a server-side track search, since
 * "a pinned track is just a display card on the profile, not something
 * that needs to be streamable from someone else's device").
 */
data class PinnedTrack(
    @SerializedName("source_track_id") val sourceTrackId: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null
)

/** Body for PUT /api/social/profile/pinned-tracks (`PinnedTracksUpdate`, main.py ~L15389). Wholesale replace -- always send the FULL desired list (not a single add/remove), max 5 entries (400s over that). Position is implicit from list order. */
data class SetPinnedTracksRequest(
    val tracks: List<PinnedTrack>
)

/**
 * Body for PUT /api/social/profile (`SocialProfileUpdate`, main.py
 * ~L15357). Every field is independently optional -- omitting one (null)
 * leaves it unchanged server-side, same convention as
 * [com.stash.opusplayer.bridge.api.ScrobbleLinkUpdateRequest]. Only the
 * fields this client can both display AND read back a current value for
 * (via [PublicSocialProfile]) are modeled: [bio]/[pronouns]/[statusEmoji]/
 * [statusText]/[showGuestbook]. The rest of `SocialProfileUpdate`
 * (accent colors, avatar frame/decoration, profile effect,
 * `share_now_playing`, `show_top_genres`, `show_visitor_stats`,
 * `show_listening_stats`) is deliberately NOT modeled -- `GET
 * /api/social/profile/{userId}` doesn't return most of those flags back to
 * the caller, so an edit UI for them would either show a possibly-wrong
 * default or need a second `/me`-shaped endpoint this pass doesn't add.
 */
data class SocialProfileUpdateRequest(
    val bio: String? = null,
    val pronouns: String? = null,
    @SerializedName("status_emoji") val statusEmoji: String? = null,
    @SerializedName("status_text") val statusText: String? = null,
    @SerializedName("show_guestbook") val showGuestbook: Boolean? = null
)

/**
 * One entry of [PublicSocialProfile.badges] (`_compute_profile_badges`,
 * main.py ~L16950) -- milestone achievement chips, always present (no
 * privacy toggle, public flair). Only the highest tier reached per
 * category is returned. [icon] is an SF Symbol name (e.g.
 * "headphones") -- not rendered here, Android has no equivalent icon
 * set to map it onto; [tier] alone drives this client's chip color.
 */
data class ProfileBadge(
    val id: String,
    val label: String,
    val icon: String? = null,
    val tier: String
)

/** [PublicSocialProfile.listeningStreak] (`_compute_listening_streak`, main.py ~L16912) -- null if the profile owner disabled `show_listening_stats`, not merely zero. */
data class ListeningStreak(
    @SerializedName("current_streak_days") val currentStreakDays: Int = 0,
    @SerializedName("longest_streak_days") val longestStreakDays: Int = 0
)

/** One row of GET /api/social/profile/{userId}/comments, and POST's single-object response (main.py ~L16395-16479). */
data class ProfileComment(
    val id: String,
    @SerializedName("author_user_id") val authorUserId: String,
    @SerializedName("author_username") val authorUsername: String,
    @SerializedName("author_display_name") val authorDisplayName: String? = null,
    @SerializedName("author_avatar_url") val authorAvatarUrl: String? = null,
    val body: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ProfileCommentsResponse(
    val comments: List<ProfileComment> = emptyList()
)

/** Body for POST /api/social/profile/{userId}/comments -- server caps at 280 chars, 400s over that or if blank. */
data class PostProfileCommentRequest(
    val body: String
)

/**
 * Profile banner + guestbook comments, ported from Lumisound's
 * `ProfileView.swift`/`PublicProfileView.swift`. Banner upload/delete are
 * raw-bytes bodies (not multipart), same convention as [AuthApi.uploadAvatar]
 * -- see that method's doc comment. Banner GET is deliberately NOT modeled
 * here: it's a public, unauthenticated raw-bytes endpoint that 404s as its
 * NORMAL "no banner set" response (not an error), which doesn't fit this
 * interface's JSON-`Response<T>` shape any better than avatar GET did --
 * fetch it the same way [com.stash.opusplayer.ui.compose.bridge.BridgeSettingsViewModel]
 * fetches avatars, via a plain `HttpURLConnection` against
 * `{baseUrl}/api/social/profile/banner/{userId}`, treating any non-200 as
 * "no banner" rather than surfacing an error.
 */
interface SocialProfileApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/profile/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: String): Response<PublicSocialProfile>

    /** Server caps: bio 280 chars, pronouns 30, status text 60, status emoji 8. 400s over any limit. Fields are always sent as the full edit-form state here (never a true partial omission), so callers should populate every field from the currently-loaded [PublicSocialProfile] before editing, not leave any null unless intentionally leaving that one unchanged. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("api/social/profile")
    suspend fun updateMyProfile(@Body body: SocialProfileUpdateRequest): Response<BridgeOkResponse>

    /** Wholesale replace -- always send the full desired list, max 5. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("api/social/profile/pinned-tracks")
    suspend fun setPinnedTracks(@Body body: SetPinnedTracksRequest): Response<BridgeOkResponse>

    /** [body]'s `Content-Type` must be `image/jpeg` or `image/gif` -- server sniffs magic bytes regardless of the header, 15MB cap either way. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/profile/banner")
    suspend fun uploadBanner(@Body body: RequestBody): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("api/social/profile/banner")
    suspend fun deleteBanner(): Response<BridgeOkResponse>

    /** Flat limit only, no cursor/offset pagination -- matches the bridge's own contract (max 100, default/used here: 50). */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/profile/{userId}/comments")
    suspend fun getProfileComments(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 50
    ): Response<ProfileCommentsResponse>

    /** 403s server-side unless the caller is friends with [userId] (and 400s on your own profile) -- the UI should only ever show a compose box when [PublicSocialProfile.isFriend] is true and it isn't a self-view, matching Lumisound's own gating rather than relying on the error path. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/profile/{userId}/comments")
    suspend fun postProfileComment(
        @Path("userId") userId: String,
        @Body body: PostProfileCommentRequest
    ): Response<ProfileComment>

    /** 403s server-side unless the caller is the comment's author OR the profile owner. */
    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("api/social/profile/comments/{commentId}")
    suspend fun deleteProfileComment(@Path("commentId") commentId: String): Response<Unit>
}
