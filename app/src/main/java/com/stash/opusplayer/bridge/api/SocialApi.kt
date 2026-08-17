package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** One row of `list_friends()`'s response (main.py ~L13559). */
data class BridgeFriend(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("friends_since") val friendsSince: String? = null,
    val nickname: String? = null,
    val tags: List<String> = emptyList()
)

data class FriendsResponse(
    val friends: List<BridgeFriend> = emptyList()
)

/** One row of `list_friend_requests()`'s incoming/outgoing arrays (main.py ~L13518). */
data class FriendRequestEntry(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class FriendRequestsResponse(
    val incoming: List<FriendRequestEntry> = emptyList(),
    val outgoing: List<FriendRequestEntry> = emptyList()
)

/** Body for POST /api/social/friends/request (FriendRequestCreate in main.py ~L12982).
 * Exactly one of [toUserId] / [toUsername] should be set. */
data class FriendRequestCreate(
    @SerializedName("to_user_id") val toUserId: String? = null,
    @SerializedName("to_username") val toUsername: String? = null
)

/** `{"ok": true[, "request_id": ...]}` — send/accept/decline/presence all
 * return this same minimal shape (main.py ~L13392-13783). */
data class BridgeOkResponse(
    val ok: Boolean = false,
    @SerializedName("request_id") val requestId: String? = null
)

/** Body for POST /api/social/presence (PresenceUpdate in main.py ~L12987) — the
 * heartbeat the client is expected to call every 30-60s while foregrounded,
 * plus a best-effort call with [goingOffline] = true on background/terminate. */
data class PresenceUpdate(
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    @SerializedName("now_playing_title") val nowPlayingTitle: String? = null,
    @SerializedName("now_playing_artist") val nowPlayingArtist: String? = null,
    @SerializedName("going_offline") val goingOffline: Boolean = false
)

/** One row of GET /api/social/activity/friends's `activity` array (main.py ~L16317). [kind] is always `"played"` or `"favorited"`; the server gives no per-row id, so callers needing a stable list key should synthesize one from userId+kind+at+title. */
data class FriendActivityEntry(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val kind: String,
    val title: String? = null,
    val artist: String? = null,
    val at: String? = null
)

data class FriendsActivityResponse(
    val activity: List<FriendActivityEntry> = emptyList()
)

/** One row of GET /api/social/friends/leaderboard's `leaderboard` array (main.py ~L16793) -- already ranked by [playCount] descending server-side. */
data class FriendLeaderboardEntry(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("play_count") val playCount: Int = 0
)

data class FriendsLeaderboardResponse(
    val leaderboard: List<FriendLeaderboardEntry> = emptyList()
)

/** One row of GET /api/social/block's `blocked` array (main.py ~L16115) -- same `_public_user_fields` shape as [BridgeFriend]'s identity subset. */
data class BlockedUser(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

data class BlockedUsersResponse(
    val blocked: List<BlockedUser> = emptyList()
)

/**
 * Response of GET /api/social/compatibility/{userId} (main.py ~L16539) --
 * a friends-only "Music Match" score (0-100, weighted 70% shared-artist /
 * 30% shared-genre Jaccard similarity). [insufficientData] is true (with
 * [score] pinned to 0 and both share lists empty) when either side's
 * listening history isn't rich enough to compute anything meaningful --
 * show a plain "not enough history" message in that case, not a 0% match.
 * Deliberately does NOT model the companion `/api/social/blend/{userId}`
 * "press play" mix endpoint (a full playable blended-artists mix) -- out
 * of scope for this pass, the score card alone is the contained chunk.
 */
data class MusicCompatibility(
    val score: Int = 0,
    @SerializedName("insufficient_data") val insufficientData: Boolean = false,
    @SerializedName("shared_artists") val sharedArtists: List<String> = emptyList(),
    @SerializedName("shared_genres") val sharedGenres: List<String> = emptyList()
)

/** One row of GET /api/social/friends/suggestions's `suggestions` array (main.py ~L16135) -- other users who share at least one friend with the caller, ranked by mutual-friend count descending, excluding existing friends/pending requests/blocks. */
data class FriendSuggestion(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("mutual_friend_count") val mutualFriendCount: Int = 0
)

data class FriendSuggestionsResponse(
    val suggestions: List<FriendSuggestion> = emptyList()
)

/** Body for PUT /api/social/friends/{friendId}/nickname (`FriendNicknameUpdate`, main.py ~L16686). Null or blank clears the nickname; max 60 chars server-side. Private to the caller -- never visible to the friend or anyone else. */
data class FriendNicknameUpdate(
    val nickname: String? = null
)

/** Response of GET /api/social/friends/tags: distinct tag names the caller has ever used across all friends (main.py ~L16734), for a filter-chip row / autocomplete -- not per-friend. */
data class FriendTagNamesResponse(
    val tags: List<String> = emptyList()
)

/** Body for POST /api/social/friends/{friendId}/tags (`FriendTagCreate`, main.py ~L16690). Max 40 chars, max 10 tags per friend, both enforced server-side (400s over either limit). */
data class FriendTagCreate(
    @SerializedName("tag_name") val tagName: String
)

/**
 * A representative subset of `/api/social/…` — friends list/requests, a
 * presence heartbeat, the friends activity feed/leaderboard, blocking, a
 * friend's compatibility score, and per-friend nicknames/tags. All require
 * the user's JWT (`get_current_user`), tagged `X-Bridge-Auth-Mode: user`.
 *
 * NOT modeled in this pass (left for follow-up): profile endpoints
 * (/api/social/profile/…, including avatar/banner upload and pinned tracks
 * — banner + guestbook comments ARE modeled, see
 * [com.stash.opusplayer.bridge.api.SocialProfileApi]), presence-for-
 * friends / listening-together, the blend-mix companion to compatibility,
 * and discovery (/social/discover, /social/similar-listeners,
 * /social/trending-by-energy).
 */
interface SocialApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/friends")
    suspend fun listFriends(): Response<FriendsResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/friends/requests")
    suspend fun listFriendRequests(): Response<FriendRequestsResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/friends/request")
    suspend fun sendFriendRequest(@Body body: FriendRequestCreate): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/friends/request/{requestId}/accept")
    suspend fun acceptFriendRequest(@Path("requestId") requestId: String): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/friends/request/{requestId}/decline")
    suspend fun declineFriendRequest(@Path("requestId") requestId: String): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/presence")
    suspend fun updatePresence(@Body body: PresenceUpdate): Response<BridgeOkResponse>

    /** Merges recent plays + favorites from all of the caller's friends, newest first. Empty immediately if the caller has no friends -- no error, matches the server's own short-circuit. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/activity/friends")
    suspend fun getFriendsActivity(@Query("limit") limit: Int = 30): Response<FriendsActivityResponse>

    /** [days]: trailing window (max 30), [limit]: top-N (max 30) -- a ranking, not a browsable/paginated list. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/friends/leaderboard")
    suspend fun getFriendsLeaderboard(
        @Query("days") days: Int = 7,
        @Query("limit") limit: Int = 10
    ): Response<FriendsLeaderboardResponse>

    /** 400s on a self-block. Also tears down any existing friendship/pending request between the two, in both directions, server-side. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/block/{userId}")
    suspend fun blockUser(@Path("userId") userId: String): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("api/social/block/{userId}")
    suspend fun unblockUser(@Path("userId") userId: String): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/block")
    suspend fun getBlockedUsers(): Response<BlockedUsersResponse>

    /** 403s server-side unless [userId] is an actual friend; 400s on your own id. Only call this once [PublicSocialProfile.isFriend] is confirmed true and it isn't a self-view. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/compatibility/{userId}")
    suspend fun getCompatibility(@Path("userId") userId: String): Response<MusicCompatibility>

    /** 400s if [userId] isn't an actual friend (`_require_friendship`). */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("api/social/friends/{userId}/nickname")
    suspend fun setFriendNickname(
        @Path("userId") userId: String,
        @Body body: FriendNicknameUpdate
    ): Response<BridgeOkResponse>

    /** Distinct tag names across ALL friends, not per-friend -- see [FriendTagNamesResponse]'s doc comment. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/friends/tags")
    suspend fun getFriendTagNames(): Response<FriendTagNamesResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("api/social/friends/{userId}/tags")
    suspend fun addFriendTag(
        @Path("userId") userId: String,
        @Body body: FriendTagCreate
    ): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("api/social/friends/{userId}/tags/{tagName}")
    suspend fun removeFriendTag(
        @Path("userId") userId: String,
        @Path("tagName") tagName: String
    ): Response<BridgeOkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("api/social/friends/suggestions")
    suspend fun getFriendSuggestions(@Query("limit") limit: Int = 10): Response<FriendSuggestionsResponse>
}
