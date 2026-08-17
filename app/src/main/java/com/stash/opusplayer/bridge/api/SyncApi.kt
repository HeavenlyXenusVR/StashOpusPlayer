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
 * One row of GET /user/backups (main.py ~L7590) -- a summary only, no
 * `favorites`/`playlists` payload (that only comes back via the restore
 * response, matching `sync_pull`'s shape). Snapshots are created
 * server-side automatically before every `/user/sync` push and before
 * every restore -- there's no "create a backup now" endpoint, so this
 * list is empty until the account has been used with a client that DOES
 * push `/user/sync` (Lumisound), or after this app's own first restore
 * (which itself creates a `pre_restore` snapshot as a side effect).
 */
data class BridgeBackupSummary(
    val id: Long,
    val reason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("favorite_count") val favoriteCount: Int = 0,
    @SerializedName("playlist_count") val playlistCount: Int = 0
)

data class BackupsResponse(
    val backups: List<BridgeBackupSummary> = emptyList()
)

/** Response of DELETE /user/backups: `{"status": "cleared", "deleted": <int>}`. */
data class ClearBackupsResponse(
    val status: String,
    val deleted: Int = 0
)

/**
 * Response of POST /user/backups/{id}/restore -- main.py returns the SAME
 * shape `GET /user/sync` does (the full settings blob), but only
 * [favorites]/[playlists] are modeled here; everything else in that blob
 * is the iOS-specific settings SyncApi's own doc comment already
 * describes as out of scope for this client.
 */
data class RestoreBackupResponse(
    val favorites: List<BridgeFavorite> = emptyList(),
    val playlists: List<BridgePlaylist> = emptyList()
)

/**
 * Body for POST /user/history (`LogPlayRequest`, main.py ~L1920). Only
 * [title] is required server-side; everything else is optional and
 * unvalidated. [localSongId] is a purely opaque, client-local identifier
 * (Lumisound uses its own `sourceTrackID`; Stash has no equivalent concept
 * on [com.stash.opusplayer.data.Song], and none is needed here -- the
 * server never checks it against anything) -- the local MediaStore song id
 * as a string is a fine value for it. This is a plain INSERT server-side,
 * no dedupe/rate-limit -- callers are responsible for only calling this
 * once per genuine listen (see the 5-second-after-track-start debounce in
 * `MusicPlayerManager`), not on every possible trigger.
 */
data class LogHistoryRequest(
    val title: String,
    val artist: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    @SerializedName("listen_seconds") val listenSeconds: Int? = 0,
    val bpm: Double? = null
)

/** Response of POST /user/history (201): `{"id": "<uuid>", "status": "logged"}`. */
data class LogHistoryResponse(
    val id: String,
    val status: String
)

/**
 * Response of GET /user/achievements -- computed live from play history on
 * every call (main.py ~L13743), no separate achievements table server-side.
 * [badges] are simple string tags (`plays_10`, `hours_1`, `streak_7`,
 * `night_owl`, etc.) -- deliberately not modeled as a closed enum here
 * since the server can add new badge ids without a client update breaking
 * anything; unrecognized ids should just render with a generic fallback.
 */
data class AchievementsResponse(
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Int = 0,
    @SerializedName("current_streak_days") val currentStreakDays: Int = 0,
    @SerializedName("longest_streak_days") val longestStreakDays: Int = 0,
    val badges: List<String> = emptyList()
)

/**
 * Playlists + favorites + backups + history/achievements -- the "basic
 * sync" subset covered in this pass. All require the user's JWT
 * (`get_current_user`), tagged `X-Bridge-Auth-Mode: user`.
 *
 * GET/POST `/user/sync` (the iOS app's full cross-device settings sync) is
 * deliberately NOT modeled here: its push body (`SyncPushRequest`, main.py
 * ~L1707) is a large, iOS-app-specific settings blob (`vinyl_disc_enabled`,
 * `car_mode_enabled`, `now_playing_seeker_style`, `bg_shuffle_interval`,
 * etc.) that doesn't map onto this Android app's own settings model — pushing
 * it wholesale from this client would either silently drop fields or clobber
 * server state with meaningless defaults. A real Android /user/sync
 * integration needs its own field-by-field design, not a blind port of the
 * iOS request shape. Backups (`/user/backups*`) ARE modeled below, since
 * they only ever expose the favorites/playlists subset (never the
 * iOS-settings blob) — see [BridgeBackupSummary]/[RestoreBackupResponse].
 * `/user/history` and `/user/achievements` ARE modeled below too --
 * neither reads or writes the `/user/sync` blob at all (achievements are
 * computed live from `ios_play_history`, confirmed against main.py
 * directly), so they carry none of that scoping risk.
 * Still not modeled: `/user/library/inventory`, `/user/folder-backups`,
 * `/user/stats`, `/user/settings`.
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

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/backups")
    suspend fun listBackups(): Response<BackupsResponse>

    /** Deletes ALL of this user's backup snapshots -- does not touch live favorites/playlists. */
    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/backups")
    suspend fun clearBackups(): Response<ClearBackupsResponse>

    /** Replace-everything restore: server snapshots current state first (reason "pre_restore"), then overwrites live favorites/playlists/settings from the chosen snapshot. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/backups/{backupId}/restore")
    suspend fun restoreBackup(@Path("backupId") backupId: Long): Response<RestoreBackupResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/history")
    suspend fun logHistory(@Body body: LogHistoryRequest): Response<LogHistoryResponse>

    /** [tzOffsetMinutes]: the device's current UTC offset in minutes (e.g. -240 for EDT) -- shifts streak/day-part badge grouping to the user's local calendar day server-side. Omitting it (or passing 0) groups by UTC day instead. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/achievements")
    suspend fun getAchievements(@Query("tz_offset_minutes") tzOffsetMinutes: Int = 0): Response<AchievementsResponse>
}
