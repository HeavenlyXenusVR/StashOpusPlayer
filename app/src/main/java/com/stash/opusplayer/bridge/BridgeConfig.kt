package com.stash.opusplayer.bridge

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bridgeConfigDataStore by preferencesDataStore(name = "bridge_config")

/**
 * Holds the self-hosted Lumisound ios-bridge server's base URL and (optional)
 * operator API key (IOS_BRIDGE_API_KEY — see main.py ~L78/~L744). Both are
 * plain, user-typed Settings-screen values rather than secrets on the level
 * of the login JWT (see BridgeTokenStore), so they're persisted via plain
 * Jetpack DataStore instead of EncryptedSharedPreferences — same tradeoff
 * this project already makes for the Lavalink server address/password in
 * YouTubePlaybackSettings.
 *
 * There is deliberately no baked-in default base URL: the bridge is
 * self-hosted per-operator, so a blank value means "not configured yet" and
 * callers should prompt the user to enter their server's address before using
 * any Bridge-backed feature (see [isConfigured]).
 */
@Singleton
class BridgeConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("bridge_base_url")
        val API_KEY = stringPreferencesKey("bridge_api_key")
    }

    val baseUrlFlow: Flow<String> =
        context.bridgeConfigDataStore.data.map { it[Keys.BASE_URL].orEmpty() }

    val apiKeyFlow: Flow<String> =
        context.bridgeConfigDataStore.data.map { it[Keys.API_KEY].orEmpty() }

    suspend fun getBaseUrl(): String = baseUrlFlow.first()

    suspend fun getApiKey(): String = apiKeyFlow.first()

    suspend fun setBaseUrl(url: String) {
        context.bridgeConfigDataStore.edit { it[Keys.BASE_URL] = normalizeBaseUrl(url) }
    }

    suspend fun setApiKey(key: String) {
        context.bridgeConfigDataStore.edit { it[Keys.API_KEY] = key.trim() }
    }

    suspend fun isConfigured(): Boolean = getBaseUrl().isNotBlank()

    companion object {
        /**
         * Placeholder Retrofit base URL — never actually dialed. Retrofit
         * requires *some* valid absolute URL at construction time, but the
         * real target is only known at request time (self-hosted,
         * user-configurable, and can change in Settings after the Retrofit
         * singleton is already built). [BridgeBaseUrlInterceptor] rewrites
         * every request's scheme/host/port to the real, currently-configured
         * server before it leaves the device.
         */
        const val PLACEHOLDER_BASE_URL = "https://bridge.invalid/"

        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isBlank()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return url.removeSuffix("/")
        }
    }
}
