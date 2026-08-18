package com.stash.opusplayer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.stash.opusplayer.databinding.FragmentPlaylistsBinding
import com.stash.opusplayer.data.MusicRepository
import com.stash.opusplayer.mood.M3UImportService
import com.stash.opusplayer.ui.appearance.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: MusicRepository
    private lateinit var adapter: PlaylistsAdapter

    private val m3uPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importM3u(it) } }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = MusicRepository(requireContext())
        setupRecycler()
        applyAdaptiveChrome()
        binding.createButton.setOnClickListener { promptCreate() }
        binding.importM3uButton.setOnClickListener { m3uPickerLauncher.launch("*/*") }
        observePlaylists()
    }

    private fun setupRecycler() {
        adapter = PlaylistsAdapter { playlistId ->
            parentFragmentManager.beginTransaction()
                .replace(com.stash.opusplayer.R.id.main_content, PlaylistDetailFragment.newInstance(playlistId))
                .addToBackStack(null)
                .commit()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.clipToPadding = false
        binding.recyclerView.setPadding(0, ThemeManager.scaleDp(requireContext(), 4), 0, ThemeManager.scaleDp(requireContext(), 28))
    }

    private fun applyAdaptiveChrome() {
        val context = requireContext()
        val outerPadding = ThemeManager.scaleDp(context, 10)
        val buttonHeight = ThemeManager.scaleDp(context, 34)
        val buttonHorizontalPadding = ThemeManager.scaleDp(context, 10)

        binding.root.setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
        binding.playlistsHeaderTitle.textSize = ThemeManager.scaleSp(context, 16f)
        binding.playlistsHeaderIcon.layoutParams = binding.playlistsHeaderIcon.layoutParams.apply {
            width = ThemeManager.scaleDp(context, 24)
            height = ThemeManager.scaleDp(context, 24)
        }
        binding.playlistsHeader.layoutParams = (binding.playlistsHeader.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            bottomMargin = ThemeManager.scaleDp(context, 10)
        } ?: binding.playlistsHeader.layoutParams

        binding.createButton.layoutParams = binding.createButton.layoutParams.apply {
            height = buttonHeight
        }
        binding.createButton.minimumHeight = buttonHeight
        binding.createButton.iconSize = ThemeManager.scaleDp(context, 12)
        binding.createButton.textSize = ThemeManager.scaleSp(context, 10.5f)
        binding.createButton.insetTop = 0
        binding.createButton.insetBottom = 0
        binding.createButton.setPadding(buttonHorizontalPadding, 0, buttonHorizontalPadding, 0)

        binding.emptyStateText.textSize = ThemeManager.scaleSp(context, 14f)
        binding.emptyStateText.gravity = android.view.Gravity.CENTER
        binding.emptyStateText.setPadding(
            ThemeManager.scaleDp(context, 16),
            ThemeManager.scaleDp(context, 16),
            ThemeManager.scaleDp(context, 16),
            ThemeManager.scaleDp(context, 16)
        )
    }

    private fun observePlaylists() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getPlaylists().collectLatest { list ->
                val b = _binding ?: return@collectLatest
                if (list.isNotEmpty()) {
                    adapter.submitList(list)
                    b.recyclerView.visibility = View.VISIBLE
                    b.emptyStateText.visibility = View.GONE
                } else {
                    b.recyclerView.visibility = View.GONE
                    b.emptyStateText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun promptCreate() {
        val edit = android.widget.EditText(requireContext())
        edit.hint = "Playlist name"
        edit.textSize = ThemeManager.scaleSp(requireContext(), 14f)
        val horizontalPadding = ThemeManager.scaleDp(requireContext(), 16)
        val verticalPadding = ThemeManager.scaleDp(requireContext(), 10)
        edit.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Create playlist")
            .setView(edit)
            .setPositiveButton("Create") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.createPlaylist(name)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importM3u(uri: android.net.Uri) {
        val name = queryDisplayName(uri) ?: "Imported Playlist"
        val playlistName = name.substringBeforeLast('.').ifBlank { "Imported Playlist" }
        Toast.makeText(requireContext(), "Importing \"$playlistName\"...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val library = repository.getAllSongs()
            val result = M3UImportService.import(requireContext(), uri, library)
            if (result.totalEntries == 0) {
                Toast.makeText(requireContext(), "That file has no playable entries.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            repository.createPlaylist(playlistName, result.matchedSongs)
            Toast.makeText(
                requireContext(),
                "Imported \"$playlistName\" -- ${result.matchedSongs.size} of ${result.totalEntries} tracks matched.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return runCatching {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Adapter for playlists list
private class PlaylistsAdapter(
    val onClick: (Long) -> Unit
) : androidx.recyclerview.widget.ListAdapter<com.stash.opusplayer.data.database.PlaylistWithCount, PlaylistsViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<com.stash.opusplayer.data.database.PlaylistWithCount>() {
        override fun areItemsTheSame(
oldItem: com.stash.opusplayer.data.database.PlaylistWithCount,
newItem: com.stash.opusplayer.data.database.PlaylistWithCount
        ): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(
oldItem: com.stash.opusplayer.data.database.PlaylistWithCount,
newItem: com.stash.opusplayer.data.database.PlaylistWithCount
        ): Boolean = oldItem == newItem
    }
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistsViewHolder {
        val row = com.stash.opusplayer.databinding.ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaylistsViewHolder(row, onClick)
    }
    override fun onBindViewHolder(holder: PlaylistsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private class PlaylistsViewHolder(
    private val binding: com.stash.opusplayer.databinding.ItemPlaylistBinding,
    private val onClick: (Long) -> Unit
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
    fun bind(item: com.stash.opusplayer.data.database.PlaylistWithCount) {
        applyAdaptiveSizing()
        binding.playlistName.text = item.name
        binding.playlistCount.text = "${item.songCount} song${if (item.songCount == 1) "" else "s"}"
        binding.root.setOnClickListener { onClick(item.id) }
    }

    private fun applyAdaptiveSizing() {
        val context = binding.root.context
        val density = context.resources.displayMetrics.density
        val scale = ThemeManager.getAdaptiveUiScale(context)

        fun px(baseDp: Int): Int = (baseDp * density * scale).toInt().coerceAtLeast(1)

        val rootLayoutParams = binding.root.layoutParams as? ViewGroup.MarginLayoutParams
        rootLayoutParams?.apply {
            marginStart = px(6)
            topMargin = px(5)
            marginEnd = px(6)
            bottomMargin = px(5)
        }
        if (rootLayoutParams != null) {
            binding.root.layoutParams = rootLayoutParams
        }

        binding.root.minimumHeight = px(70)
        binding.root.setPadding(px(16), px(8), px(16), px(8))

        binding.playlistIcon.layoutParams = binding.playlistIcon.layoutParams.apply {
            width = px(34)
            height = px(34)
        }
        binding.playlistArrow.layoutParams = binding.playlistArrow.layoutParams.apply {
            width = px(18)
            height = px(18)
        }

        binding.playlistName.textSize = ThemeManager.scaleSp(context, 15f)
        binding.playlistCount.textSize = ThemeManager.scaleSp(context, 11f)
    }
}
