package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.social.BlockedUsersScreen
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the pre-built [BlockedUsersScreen] Compose island -- see [BridgeAccountFragment] for why this shape. */
@AndroidEntryPoint
class BlockedUsersFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Blocked Users"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                BlockedUsersScreen()
            }
        }
    }
}
