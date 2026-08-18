package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.discordverify.DiscordVerificationScreen
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the pre-built [DiscordVerificationScreen] Compose island -- see [BridgeAccountFragment] for why this shape. */
@AndroidEntryPoint
class DiscordVerificationFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Discord Verification"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                DiscordVerificationScreen()
            }
        }
    }
}
