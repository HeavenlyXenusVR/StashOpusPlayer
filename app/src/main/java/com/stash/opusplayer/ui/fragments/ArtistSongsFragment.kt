package com.stash.opusplayer.ui.fragments

import android.content.Intent
import android.net.Uri
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
import com.stash.opusplayer.bridge.api.ArtistBioResponse
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.databinding.FragmentArtistSongsBinding
import com.stash.opusplayer.discovery.ArtistBioService
import com.stash.opusplayer.ui.MainActivity
import com.stash.opusplayer.ui.adapters.SongAdapter
import com.stash.opusplayer.ui.compose.discovery.ArtistBioCard
import com.stash.opusplayer.utils.MetadataExtractor
import kotlinx.coroutines.launch

class ArtistSongsFragment : Fragment() {
    
    private var _binding: FragmentArtistSongsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var songAdapter: SongAdapter
    private lateinit var metadataExtractor: MetadataExtractor
    
    private var artistName: String = ""
    private var songs: List<Song> = emptyList()

    private val bioState = mutableStateOf<ArtistBioResponse?>(null)
    
    companion object {
        private const val ARG_ARTIST_NAME = "artist_name"
        private const val ARG_SONGS = "songs"
        
        fun newInstance(artistName: String, songs: ArrayList<Song>): ArtistSongsFragment {
            val fragment = ArtistSongsFragment()
            val args = Bundle().apply {
                putString(ARG_ARTIST_NAME, artistName)
                putParcelableArrayList(ARG_SONGS, songs)
            }
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            artistName = it.getString(ARG_ARTIST_NAME, "")
            songs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableArrayList(ARG_SONGS, Song::class.java) ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableArrayList<Song>(ARG_SONGS) ?: emptyList()
            }
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
        setupRecyclerView()
        setupBioCard()

        // If no songs were provided, fetch quickly by artist and schedule background metadata scan
        if (songs.isEmpty() && artistName.isNotBlank()) {
            binding.emptyStateText.text = "Loading songs…"
            binding.emptyStateText.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val repo = com.stash.opusplayer.data.MusicRepository(requireContext())
                    val quick = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.getSongsByArtistDirect(artistName) }
                    songs = quick
                    // Schedule background metadata scan for these files (best-effort)
                    runCatching {
                        val paths = quick.map { it.path }
                        com.stash.opusplayer.work.MetadataScanWorker.scheduleMetadataScan(requireContext(), paths, forceRescan = false)
                    }
                } catch (_: Exception) { }
                loadSongs()
            }
        } else {
            loadSongs()
        }
    }
    
    /**
     * Ported from Lumisound's `ArtistDetailView` bio card. Uses a raw
     * [mutableStateOf] (not `remember`) as a Fragment field so a plain
     * coroutine started from [onViewCreated] can update it and trigger
     * recomposition -- this fragment isn't Compose-hosted, [ArtistBioCard]
     * is just one composable island inside an otherwise-View screen.
     */
    private fun setupBioCard() {
        binding.artistBioComposeView.setContent {
            MaterialTheme {
                val bio by bioState
                bio?.let {
                    ArtistBioCard(
                        bio = it,
                        onOpenWikipedia = { url ->
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                    )
                }
            }
        }
        if (artistName.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                bioState.value = ArtistBioService.fetchBio(requireContext(), artistName)
            }
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song ->
                val list = songAdapter.currentList
                val index = list.indexOfFirst { it.id == song.id }.let { if (it >= 0) it else 0 }
                (activity as? MainActivity)?.playSongsStartingFrom(list, index, "Artist: ${artistName}")
            },
            onFavoriteToggle = { song ->
                (activity as? MainActivity)?.toggleFavorite(song)
            },
            onAddToPlaylist = { song ->
                (activity as? MainActivity)?.addToPlaylist(song)
            },
            onPlayNext = { song -> (activity as? MainActivity)?.playNext(song) },
            onAddToQueue = { song -> (activity as? MainActivity)?.addToQueueTail(song) },
            onShowFeedback = { message -> (activity as? MainActivity)?.showPlayingBanner(message) },
            metadataExtractor = metadataExtractor
        )
        
        binding.recyclerView.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }
    
    private fun loadSongs() {
        binding.titleText.text = artistName
        songAdapter.submitList(songs)
        
        if (songs.isNotEmpty()) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.emptyStateText.text = "No songs found for this artist"
            binding.emptyStateText.visibility = View.VISIBLE
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
