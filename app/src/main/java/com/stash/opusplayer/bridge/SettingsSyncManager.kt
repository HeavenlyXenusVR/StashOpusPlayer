package com.stash.opusplayer.bridge

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.stash.opusplayer.bridge.api.SyncApi
import com.stash.opusplayer.bridge.api.SyncPushRequest
import com.stash.opusplayer.bridge.api.SyncSnapshot
import com.stash.opusplayer.ui.appearance.AppearancePreferences
import com.stash.opusplayer.utils.PrefsKeys
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Cross-device settings sync against the shared bridge account, ported
 * from Lumisound's own `AccountService+Sync.swift` push/pull of
 * `GET`/`POST /user/sync`. See
 * [com.stash.opusplayer.bridge.api.SyncSnapshot]/
 * [com.stash.opusplayer.bridge.api.SyncPushRequest] for the wire shapes
 * and the critical favorites/playlists safety constraint on push -- this
 * class is the ONLY intended caller of [SyncApi.postSync].
 *
 * Scope: `audio_settings_json` is synced via
 * [com.stash.opusplayer.audio.settings.AudioSettings.fromPrefs]/
 * [com.stash.opusplayer.audio.settings.AudioSettings.saveToPrefs], which
 * is a genuine live snapshot/facade over [com.stash.opusplayer.audio.EqualizerManager]'s and
 * `MusicService`'s own `SharedPreferences` -- see that class's doc for
 * exactly which fields map to a real engine (crossfade, EQ enabled/bands/
 * preset, bass boost, replaygain, speed/pitch, skip-silence) vs. which are
 * left at class defaults because no engine implementation exists yet
 * (reverb-as-a-setting, spatial/mono/night-mode audio, Auto EQ). What ELSE
 * is synced: [themeColor][SyncSnapshot.themeColor] (accent color) and this app's own
 * Android-specific settings (the whole Appearance block, default grid
 * columns, App Lock), namespaced under a top-level `"android"` key inside
 * `extra_settings_json` -- the same opaque catch-all string field iOS
 * uses for ITS own extras (`extraBackupKeys`). Since the bridge never
 * parses this field server-side (confirmed against main.py: stored/
 * returned as a plain text column), both platforms can read/write their
 * own namespace within the same JSON object without colliding, as long
 * as each preserves the other's top-level keys on every push -- which
 * [pushNow] does by parsing the freshly-pulled blob and only ever
 * replacing the `"android"` key before re-serializing.
 *
 * Merge safety on pull mirrors iOS's own fix for a real production bug
 * (a prior "overwrite to match remote exactly" implementation silently
 * deleted local data). Every synced value here is only ever written
 * locally if this device has never bootstrapped from the account before
 * ([PrefsKeys.SYNC_ANDROID_SETTINGS_BOOTSTRAPPED]), never overwriting a
 * value the user may already have customized on this device. This is
 * coarser than iOS's own per-`UserDefaults`-key presence check, but
 * achieves the same non-destructive guarantee with far less code -- a
 * deliberate simplification, not an oversight.
 */
@Singleton
class SettingsSyncManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val syncApi: SyncApi,
    private val tokenStore: BridgeTokenStore
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pushJob: Job? = null
    private val gson = Gson()

    /** Call once after login/register succeeds, and once at app start if already logged in. No-op past this device's first-ever bootstrap. */
    fun pullOnce() {
        syncScope.launch { pullAndApplyIfNeeded() }
    }

    /** Debounced (2s) -- call after any locally-changed setting this class syncs, so a burst of edits (e.g. dragging a slider) doesn't fire a request per tick. */
    fun schedulePush() {
        pushJob?.cancel()
        pushJob = syncScope.launch {
            delay(2000)
            pushNow()
        }
    }

    private suspend fun pullAndApplyIfNeeded() {
        if (!tokenStore.isLoggedIn()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (prefs.getBoolean(PrefsKeys.SYNC_ANDROID_SETTINGS_BOOTSTRAPPED, false)) return

        val response = runCatching { syncApi.getSync() }.getOrNull() ?: return
        if (!response.isSuccessful) return
        val snapshot = response.body() ?: return

        applySnapshot(snapshot)
        prefs.edit().putBoolean(PrefsKeys.SYNC_ANDROID_SETTINGS_BOOTSTRAPPED, true).apply()
    }

    private fun applySnapshot(snapshot: SyncSnapshot) {
        val androidJson = runCatching {
            snapshot.extraSettingsJson?.let { JSONObject(it) }?.optJSONObject("android")
        }.getOrNull()

        var appearance = AppearancePreferences.fromPrefs(appContext)
        var appearanceChanged = false

        val remoteAppearanceJson = androidJson?.optString("appearance_json")
        if (!remoteAppearanceJson.isNullOrBlank()) {
            appearance = runCatching { AppearancePreferences.fromJson(remoteAppearanceJson) }.getOrDefault(appearance)
            appearanceChanged = true
        } else {
            // No Android appearance blob synced yet from any device (e.g. this is a
            // brand new account, or only Lumisound-iOS has ever synced) -- still
            // adopt just the accent color so the two platforms start out visually
            // aligned, matching iOS's own theme_color bootstrap.
            snapshot.themeColor?.let { hex ->
                parseHexColor(hex)?.let {
                    appearance = appearance.copy(accentColor = it)
                    appearanceChanged = true
                }
            }
        }
        if (appearanceChanged) appearance.saveToPrefs(appContext)

        snapshot.audioSettingsJson?.let { json ->
            runCatching {
                gson.fromJson(json, com.stash.opusplayer.audio.settings.AudioSettings::class.java)
            }.getOrNull()?.saveToPrefs(appContext)
        }

        if (androidJson != null) {
            // Grid column defaults live in the separate "settings"-named
            // SharedPreferences file (see LibrarySettingsFragment/
            // MusicLibraryFragment/FoldersFragment/FolderDetailFragment --
            // all read/write via Fragment.settingsPrefs(), NOT
            // PreferenceManager.getDefaultSharedPreferences). App Lock is
            // on the default file (confirmed against AppLockManager).
            val columnsPrefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val columnsEditor = columnsPrefs.edit()
            if (androidJson.has("default_songs_view_columns")) {
                columnsEditor.putInt(PrefsKeys.DEFAULT_SONGS_VIEW_COLUMNS, androidJson.optInt("default_songs_view_columns"))
            }
            if (androidJson.has("default_folders_view_columns")) {
                columnsEditor.putInt(PrefsKeys.DEFAULT_FOLDERS_VIEW_COLUMNS, androidJson.optInt("default_folders_view_columns"))
            }
            if (androidJson.has("default_folder_detail_view_columns")) {
                columnsEditor.putInt(PrefsKeys.DEFAULT_FOLDER_DETAIL_VIEW_COLUMNS, androidJson.optInt("default_folder_detail_view_columns"))
            }
            columnsEditor.apply()

            if (androidJson.has("app_lock_enabled")) {
                val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                defaultPrefs.edit()
                    .putBoolean(PrefsKeys.APP_LOCK_ENABLED, androidJson.optBoolean("app_lock_enabled"))
                    .apply()
            }
        }
    }

    /**
     * Always GETs first and echoes [SyncSnapshot.favorites]/
     * [SyncSnapshot.playlists] straight through unmodified -- see
     * [SyncPushRequest]'s doc for why this is not optional.
     */
    private suspend fun pushNow() {
        if (!tokenStore.isLoggedIn()) return
        val current = runCatching { syncApi.getSync() }.getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return
        val favorites = current.favorites ?: com.google.gson.JsonArray()
        val playlists = current.playlists ?: com.google.gson.JsonArray()

        val existingExtra = runCatching { current.extraSettingsJson?.let { JSONObject(it) } }.getOrNull() ?: JSONObject()
        existingExtra.put("android", buildAndroidSettingsJson())

        val appearance = AppearancePreferences.fromPrefs(appContext)
        val hex = String.format("#%06X", 0xFFFFFF and appearance.accentColor)
        val audioSettingsJson = gson.toJson(com.stash.opusplayer.audio.settings.AudioSettings.fromPrefs(appContext))

        runCatching {
            syncApi.postSync(
                SyncPushRequest(
                    favorites = favorites,
                    playlists = playlists,
                    themeColor = hex,
                    audioSettingsJson = audioSettingsJson,
                    extraSettingsJson = existingExtra.toString()
                )
            )
        }
    }

    private fun buildAndroidSettingsJson(): JSONObject {
        val columnsPrefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val appearance = AppearancePreferences.fromPrefs(appContext)
        return JSONObject().apply {
            put("appearance_json", appearance.toJson())
            put("default_songs_view_columns", columnsPrefs.getInt(PrefsKeys.DEFAULT_SONGS_VIEW_COLUMNS, 1))
            put("default_folders_view_columns", columnsPrefs.getInt(PrefsKeys.DEFAULT_FOLDERS_VIEW_COLUMNS, 1))
            put("default_folder_detail_view_columns", columnsPrefs.getInt(PrefsKeys.DEFAULT_FOLDER_DETAIL_VIEW_COLUMNS, 1))
            put("app_lock_enabled", defaultPrefs.getBoolean(PrefsKeys.APP_LOCK_ENABLED, false))
        }
    }

    private fun parseHexColor(hex: String): Int? = runCatching {
        Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    }.getOrNull()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settingsSyncManager(): SettingsSyncManager
    }

    companion object {
        /** For call sites in plain (non-Hilt) classes, e.g. [AppearancePreferences.saveToPrefs] -- same [EntryPointAccessors] pattern as [com.stash.opusplayer.history.PlayHistoryLogger]. */
        fun schedulePushFrom(context: Context) {
            runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
                    .settingsSyncManager()
                    .schedulePush()
            }
        }
    }
}
