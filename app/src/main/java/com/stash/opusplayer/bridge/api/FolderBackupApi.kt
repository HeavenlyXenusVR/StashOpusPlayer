package com.stash.opusplayer.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT

/**
 * One track inside a backed-up watched folder (`FolderBackupTrack`, main.py
 * ~L2000). [sourceTrackId] is the `LUMISOUND_ID`-style identifier (e.g.
 * "youtube:dQw4w9WgXcQ") iOS uses to auto-redownload a track on restore --
 * always null from this client for now, since [com.stash.opusplayer.data.Song]/
 * `SongEntity` don't persist a durable source id anywhere yet (only
 * transiently known inside `VideoDownloadManager` at download time). This
 * means every track in a Stash-pushed folder backup is informational only
 * (title/artist/duration for reference after a reinstall), never
 * auto-redownloadable -- a real limitation, not an oversight, and one
 * Lumisound's own client doesn't work around either (there is no dedicated
 * restore UI on iOS, just push/fetch -- see [FolderBackupApi]'s own doc).
 */
data class FolderBackupTrack(
    val filename: String,
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Double? = 0.0,
    @SerializedName("source_track_id") val sourceTrackId: String? = null
)

/** One watched folder's backup entry -- [folderPath] is a display label here, not literally "relative to Documents" (Android has no equivalent concept; SAF tree folders use their [androidx.documentfile.provider.DocumentFile] display name instead). */
data class FolderBackupEntry(
    @SerializedName("folder_path") val folderPath: String,
    val tracks: List<FolderBackupTrack> = emptyList()
)

data class FolderBackupPushRequest(
    val folders: List<FolderBackupEntry> = emptyList()
)

/** Response of PUT /user/folder-backups: `{"status": "synced", "folders": <int>}`. */
data class FolderBackupPushResponse(
    val status: String,
    val folders: Int = 0
)

/** Response of GET /user/folder-backups: `{"folders": [{folder_path, tracks, updated_at}]}`. */
data class FolderBackupGetResponse(
    val folders: List<FolderBackupEntrySnapshot> = emptyList()
)

data class FolderBackupEntrySnapshot(
    @SerializedName("folder_path") val folderPath: String,
    val tracks: List<FolderBackupTrack> = emptyList(),
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Folder structure backup, ported from Lumisound's
 * `AccountService+FolderBackup.swift`. Wholesale replace-on-push (like the
 * already-shipped Cloud Backups feature, but a separate bridge table --
 * `ios_user_folder_backups`, not `ios_backups`), metadata-only -- no audio
 * bytes ever cross the wire, matching how every other backup feature in
 * this app works. Notably, iOS itself has **no dedicated restore UI** for
 * this -- `AccountService+FolderBackup.swift` only ever pushes and fetches,
 * there's no "restore your folders?" screen on either platform in this
 * pass. [com.stash.opusplayer.backup.FolderBackupService] mirrors that
 * scope exactly: push on folder add/remove/rescan, plus a simple read-only
 * viewer screen (a small addition beyond the iOS original, since a bridge-
 * backed feature with literally zero visible UI didn't seem worth shipping
 * silently).
 */
interface FolderBackupApi {

    @Headers("X-Bridge-Auth-Mode: user")
    @PUT("user/folder-backups")
    suspend fun pushFolderBackups(@Body body: FolderBackupPushRequest): Response<FolderBackupPushResponse>

    @Headers("X-Bridge-Auth-Mode: user")
    @GET("user/folder-backups")
    suspend fun getFolderBackups(): Response<FolderBackupGetResponse>
}
