package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.stash.opusplayer.ui.compose.profile.PublicProfileScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the pre-built [PublicProfileScreen] Compose island for a specific
 * [ARG_USER_ID] -- unlike this app's other settings screens, this one
 * needs an argument, so it uses the standard `newInstance(...)`/`Bundle`
 * Fragment pattern (matching [com.stash.opusplayer.ui.fragments.ArtistSongsFragment])
 * rather than a plain no-arg constructor.
 */
@AndroidEntryPoint
class PublicProfileFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Profile"

    private val userId: String by lazy { arguments?.getString(ARG_USER_ID).orEmpty() }

    companion object {
        private const val ARG_USER_ID = "user_id"

        fun newInstance(userId: String): PublicProfileFragment {
            val fragment = PublicProfileFragment()
            fragment.arguments = Bundle().apply { putString(ARG_USER_ID, userId) }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                if (userId.isNotBlank()) {
                    PublicProfileScreen(userId = userId)
                }
            }
        }
    }
}
