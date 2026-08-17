package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A trimmed subset of GET /api/social/profile/{userId}'s response (main.py
 * ~L15767-15795) -- the full payload also includes badges, pinned tracks,
 * top genres/artists, listening streak, visitor stats, accent colors/glow,
 * avatar decoration/frame, profile effect, and featured playlist. None of
 * that rich profile-customization surface is modeled here; this pass only
 * covers identity + banner + guestbook. Only the fields this client
 * actually reads are declared -- Gson silently ignores the rest.
 */
data class PublicSocialProfile(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerializedName("show_guestbook") val showGuestbook: Boolean = true,
    @SerializedName("is_friend") val isFriend: Boolean = false,
    @SerializedName("member_since") val memberSince: String? = null
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
