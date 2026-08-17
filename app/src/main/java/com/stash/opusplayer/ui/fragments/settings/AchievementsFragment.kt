package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.achievements.AchievementsScreen
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the pre-built [AchievementsScreen] Compose island -- see [BridgeAccountFragment] for why this shape. */
@AndroidEntryPoint
class AchievementsFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Achievements"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                AchievementsScreen()
            }
        }
    }
}
