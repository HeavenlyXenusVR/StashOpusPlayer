package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.help.HelpScreen

class HelpFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Help & Feature Guide"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                HelpScreen()
            }
        }
    }
}
