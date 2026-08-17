package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.social.FriendsScreen
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the pre-built [FriendsScreen] Compose island -- see [BridgeAccountFragment] for why this shape. */
@AndroidEntryPoint
class FriendsHostFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Friends"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                FriendsScreen(
                    onFriendClick = { userId ->
                        navigateToSettingsScreen(PublicProfileFragment.newInstance(userId))
                    }
                )
            }
        }
    }
}
