package com.stash.opusplayer.bridge

import com.stash.opusplayer.bridge.api.StreamingApi
import javax.inject.Inject

/**
 * Resolves playback through the Lumisound ios-bridge backend's /api/stream
 * endpoint (main.py ~L2069) instead of local yt-dlp/Lavalink extraction.
 *
 * The bridge does the yt-dlp extraction server-side and hands back a direct
 * CDN URL. main.py's own comment on the sibling /api/stream/proxy endpoint
 * notes that URL is bound to the IP that extracted it — i.e. the *server's*
 * IP, not the device's — which is fine here since the device just plays
 * whatever URL it's handed (the same as today's direct-play path for the
 * Local/yt-dlp resolver). If a deployment's CDN starts rejecting the app's
 * IP for that URL, switch this to call /api/stream/proxy instead (NOT yet
 * modeled in [StreamingApi] — see its class doc), which re-streams the bytes
 * through the bridge rather than handing back a CDN URL directly.
 */
class BridgeStreamResolver @Inject constructor(
    private val streamingApi: StreamingApi
) : PlaybackSourceResolver {

    override suspend fun resolve(request: PlaybackRequest): Result<PlaybackResolution> {
        return try {
            val response = streamingApi.stream(
                id = request.sourceId,
                source = request.source,
                url = request.url,
                format = request.preferredFormat
            )
            if (!response.isSuccessful) {
                return Result.failure(
                    IllegalStateException(
                        "Bridge /api/stream returned HTTP ${response.code()} for " +
                            "${request.source}:${request.sourceId}"
                    )
                )
            }
            val body = response.body()
            if (body == null || body.url.isBlank()) {
                Result.failure(IllegalStateException("Bridge /api/stream returned no playable URL"))
            } else {
                Result.success(PlaybackResolution(streamUrl = body.url, sourceLabel = "Bridge"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
