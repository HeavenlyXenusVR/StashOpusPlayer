package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import com.stash.opusplayer.ui.compose.podcasts.PodcastsScreen
import com.stash.opusplayer.ui.compose.podcasts.PodcastsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the pre-built [PodcastsScreen] Compose island -- see
 * [BridgeAccountFragment] for why this shape. Also owns the OPML-import
 * document picker: `ACTION_OPEN_DOCUMENT` is inherently an
 * `ActivityResultLauncher` concern (must be registered before the Fragment
 * reaches `STARTED`), so it can't live inside the Compose tree the way
 * everything else on this screen does. [PodcastsViewModel] is fetched here
 * via the standard Hilt `by viewModels()` delegate -- the SAME instance
 * [PodcastsScreen]'s internal `hiltViewModel()` call resolves to, since
 * both share this Fragment as their `ViewModelStoreOwner` -- so the picked
 * file's text reaches the exact view-model instance backing the screen.
 */
@AndroidEntryPoint
class PodcastsFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Podcasts"

    private val viewModel: PodcastsViewModel by viewModels()

    private val opmlPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) viewModel.importOpml(text)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                PodcastsScreen(
                    onImportOpmlClick = { opmlPickerLauncher.launch(arrayOf("*/*")) }
                )
            }
        }
    }
}
