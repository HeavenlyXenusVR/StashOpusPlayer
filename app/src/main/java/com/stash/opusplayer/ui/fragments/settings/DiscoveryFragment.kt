package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.discovery.DiscoveryScreen
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the pre-built [DiscoveryScreen] Compose island -- see [BridgeAccountFragment] for why this shape. */
@AndroidEntryPoint
class DiscoveryFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Discover"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                DiscoveryScreen()
            }
        }
    }
}
