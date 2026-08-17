package com.stash.opusplayer.identify

import android.content.Context
import com.google.gson.JsonParser
import com.stash.opusplayer.bridge.BridgeTokenStore
import com.stash.opusplayer.bridge.api.FingerprintApi
import com.stash.opusplayer.clip.ClipExportService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Track identification via the shared Lumisound bridge's AcoustID proxy,
 * ported from Lumisound's AcoustIDService.swift. No fingerprinting happens
 * on-device on either platform -- the client's only job is trimming/
 * re-encoding a representative clip and uploading it; the bridge runs the
 * real Chromaprint (`fpcalc`) analysis and queries api.acoustid.org.
 *
 * Reuses [ClipExportService]'s decode/encode pipeline (built for Clip
 * Maker) rather than a second implementation -- the only difference here
 * is a 120s cap (matching the Swift original) instead of Make Clip's own
 * 60s UI limit, and the clip is always from the start of the track rather
 * than a user-picked range.
 *
 * Uses Hilt's [EntryPointAccessors] rather than constructor injection since
 * this is a plain `object`, not a Hilt-managed class -- lets call sites that
 * aren't themselves `@AndroidEntryPoint` (like [com.stash.opusplayer.ui.NowPlayingActivity])
 * reach the same singleton [FingerprintApi]/[BridgeTokenStore] instances
 * every bridge-backed screen already shares, without converting the whole
 * Activity into a Hilt entry point for this one feature.
 */
object AcoustIdService {

    private const val MAX_CLIP_SECONDS = 120

    sealed class IdentifyError : Exception() {
        object NotLoggedIn : IdentifyError()
        object TrimFailed : IdentifyError()
        object NotMatched : IdentifyError()
        data class Server(val detail: String) : IdentifyError()
    }

    data class Match(val title: String, val artist: String?, val album: String?, val score: Double)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun fingerprintApi(): FingerprintApi
        fun bridgeTokenStore(): BridgeTokenStore
    }

    suspend fun identify(context: Context, path: String, durationMs: Long, title: String): Match =
        withContext(Dispatchers.IO) {
            val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
            if (!deps.bridgeTokenStore().isLoggedIn()) throw IdentifyError.NotLoggedIn

            val clipEndMs = minOf(durationMs, MAX_CLIP_SECONDS * 1000L)
            val clipFile = try {
                ClipExportService.exportClip(context, path, 0, clipEndMs, title, maxClipSeconds = MAX_CLIP_SECONDS)
            } catch (e: Exception) {
                throw IdentifyError.TrimFailed
            }
            val bytes = try {
                clipFile.readBytes()
            } finally {
                clipFile.delete()
            }

            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val response = try {
                deps.fingerprintApi().identify("m4a", body)
            } catch (e: Exception) {
                throw IdentifyError.Server("Network error -- check your connection and bridge server settings.")
            }
            if (!response.isSuccessful) {
                throw IdentifyError.Server(extractErrorDetail(response) ?: "Server error (HTTP ${response.code()})")
            }
            val result = response.body() ?: throw IdentifyError.NotMatched
            if (!result.matched || result.title.isNullOrBlank()) throw IdentifyError.NotMatched

            Match(result.title, result.artist, result.album, result.score ?: 0.0)
        }

    /** FastAPI's standard `{"detail": "..."}` error shape, same convention BridgeSettingsViewModel already parses. */
    private fun extractErrorDetail(response: Response<*>): String? {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.get("detail")?.asString
        }.getOrNull()
    }
}
