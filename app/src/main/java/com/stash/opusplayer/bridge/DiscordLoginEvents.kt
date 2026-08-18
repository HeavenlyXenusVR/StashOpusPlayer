package com.stash.opusplayer.bridge

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Outcome of the `lumisound://discord-login` OAuth callback, mirroring the query params main.py's `_complete_discord_login` redirects with. */
sealed interface DiscordLoginOutcome {
    data class SignedIn(val token: String) : DiscordLoginOutcome
    data class RequiresTwoFactor(val pendingToken: String) : DiscordLoginOutcome
    data class Failed(val reason: String?) : DiscordLoginOutcome
}

/**
 * Bridges MainActivity's `lumisound://discord-login` deep-link handler
 * (Activity-scoped, receives the OAuth redirect) to
 * [com.stash.opusplayer.ui.compose.bridge.BridgeSettingsViewModel]
 * (Fragment/NavGraph-scoped, owns the login UI state). The two have no
 * other way to talk to each other: the deep link can land while
 * BridgeSettingsScreen is already composed with its ViewModel already
 * constructed, so the ViewModel's own init-time `tokenStore.isLoggedIn()`
 * read would miss a sign-in that completes afterward.
 */
@Singleton
class DiscordLoginEvents @Inject constructor() {
    private val _outcomes = MutableSharedFlow<DiscordLoginOutcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<DiscordLoginOutcome> = _outcomes

    suspend fun emit(outcome: DiscordLoginOutcome) {
        _outcomes.emit(outcome)
    }
}
