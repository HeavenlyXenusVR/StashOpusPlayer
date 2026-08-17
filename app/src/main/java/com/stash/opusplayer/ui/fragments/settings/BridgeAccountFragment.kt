package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.bridge.BridgeSettingsScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * First Fragment-hosts-Compose bridge in this codebase -- everything else here
 * is View/XML, but [BridgeSettingsScreen] (sign in/up, server config, display
 * name) was already built as a self-contained Compose island with its own
 * Hilt-provided `hiltViewModel()`, so hosting it via a plain [ComposeView]
 * inside a normal [NavigableSettingsFragment] is the smallest way to make it
 * reachable rather than rebuilding it as XML/Views.
 *
 * `@AndroidEntryPoint` is required here (not just on the Activity) because
 * `hiltViewModel()` resolves its Hilt component from the nearest
 * `ViewModelStoreOwner` in the composition, which for a Fragment-hosted
 * `ComposeView` is this Fragment itself.
 */
@AndroidEntryPoint
class BridgeAccountFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Account & Server"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                BridgeSettingsScreen(
                    onViewMyProfile = { userId ->
                        navigateToSettingsScreen(PublicProfileFragment.newInstance(userId))
                    }
                )
            }
        }
    }
}
