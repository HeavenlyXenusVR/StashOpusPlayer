package com.stash.opusplayer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.databinding.FragmentArtistSongsBinding
import com.stash.opusplayer.discovery.LinerNotesService
import com.stash.opusplayer.ui.MainActivity
import com.stash.opusplayer.ui.adapters.SongAdapter
import com.stash.opusplayer.ui.compose.discovery.LinerNotesCard
import com.stash.opusplayer.utils.MetadataExtractor
import kotlinx.coroutines.launch

class FolderDetailFragment : Fragment() {
    private var currentColumns: Int = 1
    private var gridDecoration: RecyclerView.ItemDecoration? = null
    private var _binding: FragmentArtistSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter
    private lateinit var metadataExtractor: MetadataExtractor

    private var folderTitle: String = ""
    private var songs: List<Song> = emptyList()
    private var isAlbum: Boolean = false
    private val linerNotesState = mutableStateOf<String?>(null)

    companion object {
        private const val ARG_FOLDER_TITLE = "folder_title"
        private const val ARG_SONGS = "songs"
        private const val ARG_IS_ALBUM = "is_album"

        fun newInstance(title: String, songs: ArrayList<Song>, isAlbum: Boolean = false): FolderDetailFragment {
            val f = FolderDetailFragment()
            val args = Bundle().apply {
                putString(ARG_FOLDER_TITLE, title)
                putParcelableArrayList(ARG_SONGS, songs)
                putBoolean(ARG_IS_ALBUM, isAlbum)
            }
            f.arguments = args
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folderTitle = it.getString(ARG_FOLDER_TITLE, "")
            songs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableArrayList(ARG_SONGS, Song::class.java) ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableArrayList<Song>(ARG_SONGS) ?: emptyList()
            }
            isAlbum = it.getBoolean(ARG_IS_ALBUM, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArtistSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        metadataExtractor = MetadataExtractor(requireContext())
        // Resolve initial columns from prefs
        val prefs = requireContext().getSharedPreferences("settings", 0)
        currentColumns = com.stash.opusplayer.utils.PrefsUtils.resolveColumnsForScreen(
            prefs,
            com.stash.opusplayer.utils.PrefsKeys.FOLDER_DETAIL_VIEW_COLUMNS,
            com.stash.opusplayer.utils.PrefsKeys.DEFAULT_FOLDER_DETAIL_VIEW_COLUMNS,
            1
        )
        applyAdaptiveHeaderSizing()
        setupRecycler()
        setupLayoutToggle()
        setupSortButton()
        setupLinerNotesCard()
        bindData()
    }

    /**
     * Ported from `AlbumDetailView.swift`'s `AlbumLinerNotesCard`. Only
     * shown when this screen was reached as an album grouping (see
     * `MainActivity`'s `ACTION_GO_TO_ALBUM` handler) -- this fragment
     * doubles as a generic folder browser (see class doc/[newInstance]),
     * and a folder full of unrelated tracks has no single artist/album
     * pair to look up liner notes for.
     */
    private fun setupLinerNotesCard() {
        val artistName = songs.firstOrNull()?.artist.orEmpty()
        if (!isAlbum || artistName.isBlank() || folderTitle.isBlank()) return

        binding.artistBioComposeView.setContent {
            MaterialTheme {
                val blurb by linerNotesState
                blurb?.let { LinerNotesCard(blurb = it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            linerNotesState.value = LinerNotesService.fetchLinerNotes(requireContext(), artistName, folderTitle)
        }
    }

    private fun setupRecycler() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                val list = songAdapter.currentList
                val index = list.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
                (activity as? MainActivity)?.playSongsStartingFrom(list, index, "Folder: ${folderTitle}")
            },
            onFavoriteToggle = { song -> (activity as? MainActivity)?.toggleFavorite(song) },
            onAddToPlaylist = { song -> (activity as? MainActivity)?.addToPlaylist(song) },
            onPlayNext = { song -> (activity as? MainActivity)?.playNext(song) },
            onAddToQueue = { song -> (activity as? MainActivity)?.addToQueueTail(song) },
            onShowFeedback = { message -> (activity as? MainActivity)?.showPlayingBanner(message) },
            metadataExtractor = metadataExtractor
        )
        songAdapter.setListStyle(SongAdapter.ListStyle.FOLDER_CARD)
        binding.recyclerView.adapter = songAdapter
        binding.recyclerView.clipToPadding = false
        val bottomPadding = com.stash.opusplayer.ui.appearance.ThemeManager.scaleDp(requireContext(), 24)
        binding.recyclerView.setPadding(0, 0, 0, bottomPadding)
        applyColumns(currentColumns)
    }

    private fun setupLayoutToggle() {
        updateLayoutButtonIcon()
        binding.layoutButton.setOnClickListener {
            val lm = binding.recyclerView.layoutManager
            val firstPos = when (lm) {
                is LinearLayoutManager -> lm.findFirstVisibleItemPosition()
                is GridLayoutManager -> lm.findFirstVisibleItemPosition()
                else -> 0
            }
            currentColumns = when (currentColumns) { 1 -> 2; 2 -> 3; else -> 1 }
            val prefs = requireContext().getSharedPreferences("settings", 0)
            prefs.edit().putInt(com.stash.opusplayer.utils.PrefsKeys.FOLDER_DETAIL_VIEW_COLUMNS, currentColumns).apply()
            applyColumns(currentColumns)
            updateLayoutButtonIcon()
            try { binding.recyclerView.scrollToPosition(firstPos) } catch (_: Exception) {}
        }
        binding.layoutButton.setOnLongClickListener { v ->
            try { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) {}
            val choices = arrayOf(
                getString(com.stash.opusplayer.R.string.layout_list),
                getString(com.stash.opusplayer.R.string.layout_two_columns),
                getString(com.stash.opusplayer.R.string.layout_three_columns)
            )
            val selectedIndex = when (currentColumns) { 1 -> 0; 2 -> 1; else -> 2 }
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(com.stash.opusplayer.R.string.choose_layout_title)
                .setSingleChoiceItems(choices, selectedIndex) { dialog, which ->
                    val newCols = when (which) { 0 -> 1; 1 -> 2; else -> 3 }
                    if (newCols != currentColumns) {
                        val lm = binding.recyclerView.layoutManager
                        val firstPos = when (lm) {
                            is LinearLayoutManager -> lm.findFirstVisibleItemPosition()
                            is GridLayoutManager -> lm.findFirstVisibleItemPosition()
                            else -> 0
                        }
                        currentColumns = newCols
                        val prefs = requireContext().getSharedPreferences("settings", 0)
                        prefs.edit().putInt(com.stash.opusplayer.utils.PrefsKeys.FOLDER_DETAIL_VIEW_COLUMNS, currentColumns).apply()
                        applyColumns(currentColumns)
                        updateLayoutButtonIcon()
                        try { binding.recyclerView.scrollToPosition(firstPos) } catch (_: Exception) {}
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(com.stash.opusplayer.R.string.cancel, null)
                .show()
            true
        }
    }

    private fun updateLayoutButtonIcon() {
        val iconRes = if (currentColumns == 1) com.stash.opusplayer.R.drawable.ic_view_list else com.stash.opusplayer.R.drawable.ic_view_grid
        try { binding.layoutButton.setIconResource(iconRes) } catch (_: Exception) {}
    }

    private fun applyColumns(cols: Int) {
        gridDecoration?.let { binding.recyclerView.removeItemDecoration(it) }
        if (cols == 1) {
            binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
            songAdapter.setColumns(1)
            gridDecoration = null
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), cols)
            songAdapter.setColumns(cols)
            val spacing = resources.getDimensionPixelSize(com.stash.opusplayer.R.dimen.grid_spacing)
            gridDecoration = com.stash.opusplayer.ui.widgets.GridSpacingItemDecoration(cols, spacing, true)
            binding.recyclerView.addItemDecoration(gridDecoration!!)
        }
    }

    private fun setupSortButton() {
        binding.sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun applyAdaptiveHeaderSizing() {
        val context = requireContext()
        val buttonSize = com.stash.opusplayer.ui.appearance.ThemeManager.scaleDp(context, 44)
        val titleSize = com.stash.opusplayer.ui.appearance.ThemeManager.scaleSp(context, 24f)

        binding.titleText.textSize = titleSize
        binding.layoutButton.layoutParams = binding.layoutButton.layoutParams.apply {
            width = buttonSize
            height = buttonSize
        }
        binding.sortButton.layoutParams = binding.sortButton.layoutParams.apply {
            width = buttonSize
            height = buttonSize
        }
    }
    
    private fun showSortDialog() {
        val sortOptions = arrayOf(
            getString(com.stash.opusplayer.R.string.sort_by_title),
            getString(com.stash.opusplayer.R.string.sort_by_artist),
            getString(com.stash.opusplayer.R.string.sort_by_album),
            getString(com.stash.opusplayer.R.string.sort_by_duration)
        )
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(com.stash.opusplayer.R.string.choose_sort_title))
            .setItems(sortOptions) { _, which ->
                val sorted = when (which) {
                    0 -> songs.sortedBy { it.title.lowercase() }
                    1 -> songs.sortedBy { it.artist.lowercase() }
                    2 -> songs.sortedBy { it.album.lowercase() }
                    3 -> songs.sortedBy { it.duration }
                    else -> songs
                }
                songAdapter.submitList(sorted)
                (activity as? MainActivity)?.showPlayingBanner("Sorted by ${sortOptions[which]}")
            }
            .setNegativeButton(getString(com.stash.opusplayer.R.string.cancel), null)
            .show()
    }
    
    private fun bindData() {
        binding.titleText.text = folderTitle.ifBlank { "Folder" }
        songAdapter.submitList(songs)
        if (songs.isNotEmpty()) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.emptyStateText.text = "No songs found in this folder"
            binding.emptyStateText.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
