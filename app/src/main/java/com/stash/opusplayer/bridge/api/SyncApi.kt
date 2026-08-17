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
 * Response of GET /user/scrobble (main.py ~L13524). The actual scrobble
 * POST to Last.fm/Libre.fm/ListenBrainz happens entirely server-side,
 * fire-and-forget from POST /user/history -- none of these endpoints ever
 * scrobble anything themselves, they only manage which accounts are linked.
 * `listenbrainzLinked` has no matching username field -- ListenBrainz
 * linking is a bare pasted token, no OAuth-style identity round-trip.
 */
data class ScrobbleLinksResponse(
    @SerializedName("lastfm_linked") val lastfmLinked: Boolean = false,
    @SerializedName("lastfm_username") val lastfmUsername: String? = null,
    @SerializedName("listenbrainz_linked") val listenBrainzLinked: Boolean = false,
    @SerializedName("librefm_linked") val librefmLinked: Boolean = false,
    @SerializedName("librefm_username") val librefmUsername: String? = null,
    val enabled: Boolean = true
)

/**
 * Body for PUT /user/scrobble. Only models the two fields this client ever
 * actually sends (ListenBrainz's pasted token, and the enable/disable
 * toggle) -- the Last.fm/Libre.fm session-key fields the bridge's
 * `ScrobbleLinkRequest` also accepts are only ever set by the bridge
 * itself inside the .../link routes below, never sent directly by a
 * client. Any field left null here is left UNCHANGED server-side, EXCEPT
 * `enabled`, which the bridge always overwrites (defaulting to `true` if
 * omitted) -- so a bare ListenBrainz-token PUT also silently re-enables
 * scrobbling if it was off; harmless in practice since linking is the
 * user opting back in anyway, but worth knowing.
 */
data class ScrobbleLinkUpdateRequest(
    @SerializedName("listenbrainz_token") val listenBrainzToken: String? = null,
    val enabled: Boolean? = null
)

/** Response of POST /user/scrobble/{lastfm,librefm}/request-token. [authUrl] is fully server-constructed -- open it as-is, no client-side URL building. */
data class ScrobbleRequestTokenResponse(
    val token: String,
    @SerializedName("auth_url") val authUrl: String
)

/** Body for POST /user/scrobble/{lastfm,librefm}/link -- the token from the matching request-token call. */
data class ScrobbleLinkTokenRequest(
    val token: String
)

/** Response of POST /user/scrobble/lastfm/link. A 400 (not 200-with-a-flag) is how the bridge signals "not approved yet" -- see SyncApi's doc comment on the link methods. */
data class LastfmLinkResponse(
    @SerializedName("lastfm_username") val lastfmUsername: String? = null
)

/** Response of POST /user/scrobble/librefm/link -- same 400-on-unapproved contract as [LastfmLinkResponse]. */
data class LibrefmLinkResponse(
    @SerializedName("librefm_username") val librefmUsername: String? = null
)

/** One row of GET /user/playlists/{id}/collaborators (main.py ~L13322). */
data class PlaylistCollaborator(
    @SerializedName("user_id") val userId: String,
    val username: String,
    val role: String,
    @SerializedName("added_at") val addedAt: String? = null
)

/** Body for POST /user/playlists/{id}/collaborators (`AddCollaboratorRequest`, main.py ~L2088). [role] must be "editor" or "viewer". */
data class AddCollaboratorRequest(
    val username: String,
    val role: String = "editor"
)

/** Response of POST /user/playlists/{id}/collaborators (status 201 despite the plain-object body). */
data class AddCollaboratorResponse(
    @SerializedName("playlist_id") val playlistId: String,
    val username: String,
    val role: String
)

/**
 * One row of GET /user/playlists/shared-with-me (main.py ~L13382) -- a
 * summary only, deliberately NOT the full [BridgePlaylist] shape: no
 * `tracks` here, fetch those via [SyncApi.getPlaylist] when the user opens
 * one. Owned playlists are never included in this list (those come from
 * [SyncApi.getPlaylists]) -- this is exclusively rows where the caller is a
 * collaborator.
 */
data class SharedWithMePlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val role: String,
    @SerializedName("owner_username") val ownerUsername: String,
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Body for POST /user/playlists/{id}/tracks -- reuses the same shape as
 * `SyncTrack` server-side (main.py ~L1944). [position] is accepted but
 * ignored server-side; the bridge always appends (assigns `MAX(position)+1`
 * for that playlist) regardless of what's sent here.
 */
data class AddPlaylistTrackRequest(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = 0
)

/** Response of POST /user/playlists/{id}/tracks (201): `{"id": "<uuid>", "position": <int>}` -- just the new track's id/position, not the full track object. */
data class AddPlaylistTrackResponse(
    val id: String,
    val position: Int
)

/**
 * Response of GET /user/sync (main.py's `_build_sync_snapshot`) and shape
 * echoed back on POST. [favorites]/[playlists] are deliberately typed as
 * opaque [com.google.gson.JsonElement] rather than fully modeled structs --
 * see [SyncPushRequest]'s doc for why: this client only ever echoes them
 * back verbatim on push, never constructs or reads into them, so there is
 * zero risk of an incomplete field mapping silently dropping data on a
 * round trip.
 *
 * Of the ~24 settings fields the bridge actually supports, only the ones
 * with a real Android equivalent are modeled here: [themeColor] (accent
 * color) and [extraSettingsJson] (this client's own Android-specific
 * settings bag, namespaced under an `"android"` key inside the same JSON
 * string iOS uses for ITS catch-all extras -- see
 * [com.stash.opusplayer.bridge.SettingsSyncManager]'s doc for the merge
 * strategy). Fields with no Android equivalent at all (`vinyl_disc_enabled`,
 * `car_mode_enabled`, `now_playing_seeker_style`, `bg_shuffle_interval`,
 * etc.) are NOT modeled and always sent as `null` on push -- the server's
 * `CASE WHEN %s IS NULL THEN <existing> ELSE %s END` upsert (confirmed
 * against main.py directly) preserves whatever iOS last set for those,
 * so omitting them here never clobbers iOS's own state.
 */
data class SyncSnapshot(
    val favorites: com.google.gson.JsonElement? = null,
    val playlists: com.google.gson.JsonElement? = null,
    @SerializedName("theme_color") val themeColor: String? = null,
    @SerializedName("extra_settings_json") val extraSettingsJson: String? = null
)

/**
 * Body for POST /user/sync. **[favorites]/[playlists] are NOT optional and
 * have NO safe default** -- confirmed against main.py's `sync_push`
 * directly: the handler unconditionally `DELETE`s the user's existing
 * favorites/playlists rows before checking whether the request supplied
 * any to re-insert, so an omitted (or empty-list) field wipes them with no
 * restore. This is a fundamentally different safety model than the other
 * settings fields (which use preserve-if-null semantics) and is NOT a
 * quirk this client can route around -- every caller of
 * [SyncApi.postSync] MUST first [SyncApi.getSync] and pass its
 * [SyncSnapshot.favorites]/[SyncSnapshot.playlists] straight through
 * unmodified. [com.stash.opusplayer.bridge.SettingsSyncManager] is the
 * only intended caller and does exactly this -- do not call
 * [SyncApi.postSync] directly from anywhere else.
 */
data class SyncPushRequest(
    val favorites: com.google.gson.JsonElement,
    val playlists: com.google.gson.JsonElement,
    @SerializedName("theme_color") val themeColor: String? = null,
    @SerializedName("extra_settings_json") val extraSettingsJson: String? = null
)

/**
 * Playlists + favorites + backups + history/achievements + cross-device
 * settings sync. All require the user's JWT (`get_current_user`), tagged
 * `X-Bridge-Auth-Mode: user`.
 *
 * GET/POST `/user/sync` (the iOS app's full cross-device settings sync) is
 * now partially modeled -- see [SyncSnapshot]/[SyncPushRequest]'s docs for
 * exactly which of its ~24 fields have a real Android equivalent and the
 * critical favorites/playlists data-safety constraint on push. Backups
 * (`/user/backups*`) ARE modeled below too, since
 * they only ever expose the favorites/playlists subset (never the
 * iOS-settings blob) — see [BridgeBackupSummary]/[RestoreBackupResponse].
 * `/user/history` and `/user/achievements` ARE modeled below too --
 * neither reads or writes the `/user/sync` blob at all (achievements are
 * computed live from `ios_play_history`, confirmed against main.py
 * directly), so they carry none of that scoping risk. `/user/scrobble*`
 * (Last.fm/Libre.fm/ListenBrainz account linking) is also modeled --
 * the actual scrobble POST to those services is entirely server-side,
 * fire-and-forget from `/user/history`; these routes only ever manage
 * which accounts are linked, never scrobble anything themselves.
 * Collaborative playlists (`/user/playlists/{id}/collaborators*`,
 * `/user/playlists/shared-with-me`, `/user/playlists/{id}/tracks`) are also
 * modeled -- no bridge changes were needed, this app just never had UI for
 * cloud-playlist browsing at all before this pass.
 * Still not modeled: `/user/library/inventory`, `/user/folder-backups`,
 * `/user/stats`, `/user/settings`.
 */
interface SyncApi {

    /** See [SyncSnapshot]'s doc. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/sync")
    suspend fun getSync(): Response<SyncSnapshot>

    /** See [SyncPushRequest]'s doc -- callers MUST echo back a prior [getSync] response's favorites/playlists verbatim, never omit or synthesize them. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/sync")
    suspend fun postSync(@Body body: SyncPushRequest): Response<Unit>

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

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/scrobble")
    suspend fun getScrobbleLinks(): Response<ScrobbleLinksResponse>

    /** Sets the ListenBrainz token and/or the enable/disable toggle -- see [ScrobbleLinkUpdateRequest]'s doc comment for the "omitted fields are left unchanged, except enabled" caveat. */
    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/scrobble")
    suspend fun updateScrobbleLinks(@Body body: ScrobbleLinkUpdateRequest): Response<Unit>

    /** Unlinks EVERY service at once (Last.fm + Libre.fm + ListenBrainz) -- the bridge has no per-service unlink route, only this blanket one. */
    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/scrobble")
    suspend fun unlinkAllScrobbling(): Response<Unit>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/scrobble/lastfm/request-token")
    suspend fun lastfmRequestToken(): Response<ScrobbleRequestTokenResponse>

    /** 400 (not a flag in a 200 response) means the user hasn't approved the auth_url yet -- expected during normal use, not necessarily an error to alarm the user about on a first attempt. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/scrobble/lastfm/link")
    suspend fun lastfmLink(@Body body: ScrobbleLinkTokenRequest): Response<LastfmLinkResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/scrobble/librefm/request-token")
    suspend fun librefmRequestToken(): Response<ScrobbleRequestTokenResponse>

    /** Same 400-on-unapproved contract as [lastfmLink]. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/scrobble/librefm/link")
    suspend fun librefmLink(@Body body: ScrobbleLinkTokenRequest): Response<LibrefmLinkResponse>

    /** Owner-only; 403 if the caller doesn't own [playlistId]. Upserts if [username] is already a collaborator (changes their role). */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/playlists/{playlistId}/collaborators")
    suspend fun addCollaborator(
        @Path("playlistId") playlistId: String,
        @Body body: AddCollaboratorRequest
    ): Response<AddCollaboratorResponse>

    /** Owner or any collaborator can list -- 404 if the caller has no relationship to [playlistId] at all. */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/playlists/{playlistId}/collaborators")
    suspend fun getCollaborators(@Path("playlistId") playlistId: String): Response<List<PlaylistCollaborator>>

    /** Owner can remove anyone; a collaborator can only remove themselves ([collabUserId] == their own id) -- 403 otherwise. */
    @Headers("X-Bridge-Auth-Mode: user")
    @DELETE("user/playlists/{playlistId}/collaborators/{collabUserId}")
    suspend fun removeCollaborator(
        @Path("playlistId") playlistId: String,
        @Path("collabUserId") collabUserId: String
    ): Response<Unit>

    /** Playlists where the caller is a collaborator (never owned ones -- see [SharedWithMePlaylist]). */
    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/playlists/shared-with-me")
    suspend fun getSharedWithMePlaylists(): Response<List<SharedWithMePlaylist>>

    /** Owner or editor only -- 403 for viewers, 404 if the caller has no relationship to [playlistId]. Always appends to the end regardless of any position sent. */
    @Headers("X-Bridge-Auth-Mode: user")
    @POST("user/playlists/{playlistId}/tracks")
    suspend fun addPlaylistTrack(
        @Path("playlistId") playlistId: String,
        @Body body: AddPlaylistTrackRequest
    ): Response<AddPlaylistTrackResponse>
}
