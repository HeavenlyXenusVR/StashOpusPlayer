package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.scrobble.ScrobblingScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the pre-built [ScrobblingScreen] Compose island. Kept as its own
 * screen (not folded into `BridgeAccountFragment`/`BridgeSettingsScreen`)
 * since that screen is already large after the Account Management chunk
 * and this is a distinct concern (third-party service linking, not
 * account identity/security) -- mirrors Lumisound keeping
 * `ScrobblingView.swift` separate from its main account view too.
 */
@AndroidEntryPoint
class ScrobblingFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Scrobbling"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                ScrobblingScreen()
            }
        }
    }
}
