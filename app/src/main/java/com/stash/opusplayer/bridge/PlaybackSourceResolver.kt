package com.stash.opusplayer.bridge

/**
 * A minimal, source-agnostic description of what needs to be resolved to a
 * playable stream URL — the common shape both the existing YouTube-only
 * [com.stash.opusplayer.youtube.YouTubePlaybackResolver] and this new
 * bridge-backed resolver can be driven from, without forcing either
 * implementation to know about the other's native model (`YouTubeVideo` vs
 * [com.stash.opusplayer.bridge.api.BridgeTrack]).
 */
data class PlaybackRequest(
    /** The track/video id as understood by [source] (e.g. a YouTube video id). */
    val sourceId: String,
    /** "youtube", "soundcloud", or "bandcamp" — matches /api/stream's own
     * `source` query param (main.py ~L2073). */
    val source: String = "youtube",
    /** Full URL — required by the bridge for soundcloud/bandcamp, ignored for
     * youtube (main.py builds the youtube URL itself from [sourceId]). */
    val url: String? = null,
    /** "mp3", "m4a", "flac", "opus", or "best" — matches /api/stream's own
     * `format` query param. */
    val preferredFormat: String = "m4a"
)

/**
 * Result of resolving a [PlaybackRequest] to something actually playable.
 * Deliberately mirrors [com.stash.opusplayer.youtube.ResolvedPlayback]'s
 * shape (a stream URL plus a human-readable source label for UI/debugging)
 * so call sites that already branch on a resolver's output don't need two
 * different result shapes once a later settings screen lets a user pick
 * Bridge vs. Local (`YouTubePlaybackResolver`) vs. Auto — see
 * `YouTubePlaybackBackend` / `YouTubePlaybackSettings` for the existing
 * 3-way picker this is meant to sit alongside.
 */
data class PlaybackResolution(
    val streamUrl: String,
    val sourceLabel: String
)

/**
 * Shared contract for anything that can turn a [PlaybackRequest] into a
 * playable stream.
 *
 * [com.stash.opusplayer.youtube.YouTubePlaybackResolver] (local yt-dlp /
 * Lavalink extraction) predates this interface and is intentionally left
 * exactly as-is — it does not implement this interface and is not modified
 * by this change. [BridgeStreamResolver] is the new alternative that
 * resolves via the Lumisound ios-bridge backend's /api/stream endpoint
 * instead of extracting locally on-device.
 *
 * Wiring an actual Bridge/Local/Auto picker in front of both resolvers (the
 * way `YouTubePlaybackSettings` already lets a user pick yt-dlp/Lavalink/
 * Auto for the local resolver) is separate, later work — this interface only
 * defines the common shape both sides can be driven through.
 */
interface PlaybackSourceResolver {
    suspend fun resolve(request: PlaybackRequest): Result<PlaybackResolution>
}
