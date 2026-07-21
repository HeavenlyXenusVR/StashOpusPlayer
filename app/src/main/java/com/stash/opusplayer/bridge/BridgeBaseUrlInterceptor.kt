package com.stash.opusplayer.bridge

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every outgoing bridge request's scheme/host/port to the
 * user-configured server ([BridgeConfig.baseUrlFlow]), so Retrofit's own
 * base URL can stay a fixed placeholder ([BridgeConfig.PLACEHOLDER_BASE_URL])
 * even though the real target is only known at request time and can change
 * (self-hosted, edited in Settings) after the Retrofit singleton has already
 * been built by Hilt.
 *
 * Note: only scheme/host/port are replaced, not the path — if a deployment
 * serves the bridge from a non-root path (e.g. `https://example.com/bridge`),
 * that prefix is not currently supported. Not encountered in the reference
 * backend (ios-bridge/main.py mounts every route at the app root), so left
 * unhandled rather than guessed at.
 *
 * If no base URL is configured yet, the request is left pointed at the
 * placeholder host, which will simply fail to connect. Callers should check
 * [BridgeConfig.isConfigured] before using any bridge-backed feature and
 * prompt setup instead of relying on this to fail gracefully.
 */
class BridgeBaseUrlInterceptor(private val config: BridgeConfig) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val configuredBaseUrl = runBlocking { config.getBaseUrl() }
        val configuredHttpUrl = configuredBaseUrl.toHttpUrlOrNull()

        val request = if (configuredHttpUrl != null) {
            val rewrittenUrl = original.url.newBuilder()
                .scheme(configuredHttpUrl.scheme)
                .host(configuredHttpUrl.host)
                .port(configuredHttpUrl.port)
                .build()
            original.newBuilder().url(rewrittenUrl).build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
