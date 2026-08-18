package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.playlists.CloudPlaylistsScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the pre-built [CloudPlaylistsScreen] Compose island -- see
 * [BridgeAccountFragment] for why this shape. List and detail are both
 * inside that one composable (internal state, no Fragment-level nav).
 */
@AndroidEntryPoint
class CloudPlaylistsFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Cloud Playlists"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                CloudPlaylistsScreen()
            }
        }
    }
}
