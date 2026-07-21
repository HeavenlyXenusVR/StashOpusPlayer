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

/** One track row inside a playlist (main.py `get_playlists`/`get_playlist` ~L4838/5011). */
data class BridgePlaylistTrack(
    val id: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = null,
    val position: Int? = null
)

/**
 * A playlist as returned by GET/POST/PUT `/user/playlists(/{id})` (main.py
 * ~L4838-5062). [role] is only present on the single-playlist GET (owner vs.
 * collaborator access for "Shared with Me") — null for the list endpoint and
 * for create/update responses.
 */
data class BridgePlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val tracks: List<BridgePlaylistTrack> = emptyList(),
    val role: String? = null
)

/** Body for POST /user/playlists (CreatePlaylistRequest in main.py ~L1629). */
data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null,
    val folder: String? = null,
    val tags: List<String> = emptyList()
)

/** Body for PUT /user/playlists/{id} (UpdatePlaylistRequest in main.py ~L1636) —
 * all fields nullable/optional so only the ones the caller actually sets are
 * updated server-side (main.py's update_playlist builds its SQL SET clause
 * from only the non-null fields). */
data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val folder: String? = null,
    val tags: List<String>? = null
)

/** One row of GET /user/favorites (main.py ~L5070). */
data class BridgeFavorite(
    @SerializedName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("added_at") val addedAt: String? = null
)

/** Body for POST /user/favorites (AddFavoriteRequest in main.py ~L1643). */
data class AddFavoriteRequest(
    @SerializedName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)

/** Response of POST /user/favorites (main.py ~L5119): `{"song_id": ..., "status": "added"}`. */
data class AddFavoriteResponse(
    @SerializedName("song_id") val songId: String,
    val status: String
)

/**
 * Playlists + favorites — the "basic sync" subset covered in this pass. Both
 * require the user's JWT (`get_current_user`), tagged
 * `X-Bridge-Auth-Mode: user`.
 *
 * GET/POST `/user/sync` (the iOS app's full cross-device settings sync) is
 * deliberately NOT modeled here: its push body (`SyncPushRequest`, main.py
 * ~L1707) is a large, iOS-app-specific settings blob (`vinyl_disc_enabled`,
 * `car_mode_enabled`, `now_playing_seeker_style`, `bg_shuffle_interval`,
 * etc.) that doesn't map onto this Android app's own settings model — pushing
 * it wholesale from this client would either silently drop fields or clobber
 * server state with meaningless defaults. A real Android /user/sync
 * integration needs its own field-by-field design, not a blind port of the
 * iOS request shape. Also not modeled: `/user/library/inventory`, backups
 * (`/user/backups*`), `/user/stats`, `/user/history`, `/user/settings`.
 */
interface SyncApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/playlists")
    suspend fun getPlaylists(): Response<List<BridgePlaylist>>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/playlists/{playlistId}")
    suspend fun getPlaylist(@Path("playlistId") playlistId: String): Response<BridgePlaylist>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/playlists")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): Response<BridgePlaylist>

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/playlists/{playlistId}")
    suspend fun updatePlaylist(
        @Path("playlistId") playlistId: String,
        @Body body: UpdatePlaylistRequest
    ): Response<BridgePlaylist>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/playlists/{playlistId}")
    suspend fun deletePlaylist(@Path("playlistId") playlistId: String): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/favorites")
    suspend fun getFavorites(): Response<List<BridgeFavorite>>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/favorites")
    suspend fun addFavorite(@Body body: AddFavoriteRequest): Response<AddFavoriteResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/favorites/{songId}")
    suspend fun removeFavorite(@Path("songId") songId: String): Response<Unit>
}
