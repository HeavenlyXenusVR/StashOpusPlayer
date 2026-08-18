package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.stash.opusplayer.ui.compose.ytdlpcookies.YtdlpCookiesScreen
import com.stash.opusplayer.ui.compose.ytdlpcookies.YtdlpCookiesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ported from `CookiesFileView.swift`. The Compose screen only ever sees
 * already-read text; picking the file and reading it off the Storage
 * Access Framework `Uri` happens here, same `GetContent()` +
 * `openInputStream().readBytes()` convention `PlaylistsFragment`'s M3U
 * import already uses.
 */
@AndroidEntryPoint
class YtdlpCookiesFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "yt-dlp Cookies"

    private val viewModel: YtdlpCookiesViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { readAndUpload(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                YtdlpCookiesScreen(
                    viewModel = viewModel,
                    onPickFile = { filePickerLauncher.launch("*/*") }
                )
            }
        }
    }

    private fun readAndUpload(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    String(bytes, Charsets.UTF_8)
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Couldn't read that file.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.uploadCookies(text)
        }
    }
}
