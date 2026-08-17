package com.stash.opusplayer.bridge

import com.google.gson.GsonBuilder
import com.stash.opusplayer.bridge.api.AuthApi
import com.stash.opusplayer.bridge.api.FingerprintApi
import com.stash.opusplayer.bridge.api.SocialApi
import com.stash.opusplayer.bridge.api.DiscordVerificationApi
import com.stash.opusplayer.bridge.api.DiscordWebhookApi
import com.stash.opusplayer.bridge.api.DiscoveryApi
import com.stash.opusplayer.bridge.api.StreamingApi
import com.stash.opusplayer.bridge.api.SyncApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Networking for the Lumisound ios-bridge backend. Kept in its own file
 * rather than growing [com.stash.opusplayer.di.AppModule], per that module's
 * own stated convention for feature modules (networking, DataStore, etc.).
 *
 * Retrofit is built once against a fixed placeholder base URL
 * ([BridgeConfig.PLACEHOLDER_BASE_URL]); [BridgeBaseUrlInterceptor] rewrites
 * every request to the actually-configured, self-hosted server address
 * before it leaves the device, since that address is user-editable in
 * Settings and can change after this singleton is already built.
 */
@Module
@InstallIn(SingletonComponent::class)
object BridgeApiModule {

    @Provides
    @Singleton
    fun provideBridgeLoggingInterceptor(): HttpLoggingInterceptor =
        // Matches this project's existing convention for update-checker/YouTube
        // networking (see network/NetworkClient.kt) — full request/response
        // body logging, but with the Authorization header redacted: unlike
        // that GitHub client, every bridge request carries a real bearer
        // credential (the user's login JWT or the operator's API key), which
        // shouldn't land in logcat unconditionally in release builds.
        HttpLoggingInterceptor().apply {
            level = if (com.stash.opusplayer.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

    @Provides
    @Singleton
    fun provideBridgeOkHttpClient(
        config: BridgeConfig,
        tokenStore: BridgeTokenStore,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // 45s (not 30s) to comfortably cover /api/fingerprint/identify's
            // own ~30s server-side fpcalc subprocess timeout plus the
            // AcoustID web-service round trip on top of it -- matches
            // Lumisound's AcoustIDService, which uses the same 45s for this
            // exact call. Harmless slack for every other, much faster route.
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(BridgeBaseUrlInterceptor(config))
            .addInterceptor(BridgeAuthInterceptor(config, tokenStore))
            // Logging last so it observes the final URL/headers actually sent.
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideBridgeRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl(BridgeConfig.PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideStreamingApi(retrofit: Retrofit): StreamingApi = retrofit.create(StreamingApi::class.java)

    @Provides
    @Singleton
    fun provideSocialApi(retrofit: Retrofit): SocialApi = retrofit.create(SocialApi::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)

    @Provides
    @Singleton
    fun provideFingerprintApi(retrofit: Retrofit): FingerprintApi = retrofit.create(FingerprintApi::class.java)

    @Provides
    @Singleton
    fun provideDiscoveryApi(retrofit: Retrofit): DiscoveryApi = retrofit.create(DiscoveryApi::class.java)

    @Provides
    @Singleton
    fun provideDiscordWebhookApi(retrofit: Retrofit): DiscordWebhookApi = retrofit.create(DiscordWebhookApi::class.java)

    @Provides
    @Singleton
    fun provideDiscordVerificationApi(retrofit: Retrofit): DiscordVerificationApi = retrofit.create(DiscordVerificationApi::class.java)
}
