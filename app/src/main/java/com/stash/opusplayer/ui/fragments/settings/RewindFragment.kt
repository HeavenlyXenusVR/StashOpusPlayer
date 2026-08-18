package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.stats.RewindScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RewindFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Rewind"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                RewindScreen()
            }
        }
    }
}
