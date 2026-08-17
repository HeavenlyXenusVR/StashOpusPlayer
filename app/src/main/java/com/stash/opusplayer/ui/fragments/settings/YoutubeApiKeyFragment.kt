package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.youtubekey.YoutubeApiKeyScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class YoutubeApiKeyFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "YouTube API Key"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                YoutubeApiKeyScreen()
            }
        }
    }
}
