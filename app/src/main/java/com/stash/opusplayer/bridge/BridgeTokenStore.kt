package com.stash.opusplayer.bridge

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted, narrowly-scoped storage for the bridge login credential: the
 * session JWT returned by /auth/login or /auth/register (see auth.py —
 * TOKEN_EXPIRE_DAYS = 30, a plain 30-day token with NO refresh endpoint
 * anywhere on the server) plus the username it belongs to, so the UI can show
 * "signed in as ...".
 *
 * Deliberately does NOT hold [BridgeConfig]'s base URL / API key — those are
 * typed into a Settings field in plaintext already, matching how this
 * project already stores the Lavalink server address/password
 * (YouTubePlaybackSettings), so routing them through encryption would be
 * inconsistent without adding real protection. This store's only job is the
 * actual bearer credential.
 */
@Singleton
class BridgeTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Small in-memory cache so BridgeAuthInterceptor (which runs on OkHttp's
    // dispatcher threads for every single request) doesn't pay a decrypt on
    // every call — invalidated on any write.
    @Volatile
    private var cachedToken: String? = null

    /**
     * Synchronous read — safe to call from an OkHttp [okhttp3.Interceptor]
     * (plain background thread, not a coroutine context). Under the hood
     * this is still a regular [android.content.SharedPreferences] read
     * (decryption happens in-process against the already-open prefs file),
     * so it's not a network or disk-heavy call.
     */
    fun getToken(): String? {
        cachedToken?.let { return it }
        val stored = prefs.getString(KEY_TOKEN, null)
        cachedToken = stored
        return stored
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun saveSession(token: String, username: String?) {
        cachedToken = token
        val editor = prefs.edit().putString(KEY_TOKEN, token)
        if (username != null) {
            editor.putString(KEY_USERNAME, username)
        }
        editor.apply()
    }

    /**
     * Clears the stored token without attempting any refresh — there is no
     * refresh endpoint on the server (confirmed: no `/auth/refresh` route
     * exists in main.py). Called by [BridgeAuthInterceptor] on a 401 from a
     * user-JWT-gated request, and should also be called from any explicit
     * "log out" action. The app should treat a cleared token as "prompt the
     * user to log in again", not attempt to silently recover.
     */
    fun clearToken() {
        cachedToken = null
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    companion object {
        private const val PREFS_FILE = "bridge_secure_prefs"
        private const val KEY_TOKEN = "bridge_jwt"
        private const val KEY_USERNAME = "bridge_username"
    }
}
