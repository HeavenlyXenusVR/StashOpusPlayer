package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.stash.opusplayer.data.MusicRepository
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.mood.MoodBucket
import com.stash.opusplayer.mood.MoodClassifier
import com.stash.opusplayer.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Ported from Lumisound's MoodPlaylistsView -- 4 fixed mood buckets
 * (Energetic/Chill/Focus/Sleep), classified from metadata/genre/title
 * keyword heuristics (see [MoodClassifier]) rather than real audio
 * analysis, same as the Swift original's non-BPM tiers. Like the Swift
 * version, classification is ephemeral/in-memory and recomputed each visit
 * ("Re-analyze") rather than cached -- but unlike the Swift version (which
 * only offers "Play"), each bucket also gets a "Save as Playlist" action
 * since Stash already has persisted-playlist infrastructure Lumisound's
 * ephemeral-only design didn't have a reason to use.
 */
class MoodPlaylistsFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Mood Playlists"

    private lateinit var repository: MusicRepository
    private lateinit var resultsSection: LinearLayout
    private var buckets: Map<MoodBucket, List<Song>> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = MusicRepository(requireContext())

        val (scrollView, content) = createSettingsPage(
            title = "Mood Playlists",
            subtitle = "Your library, grouped into Energetic, Chill, Focus, and Sleep -- based on genre and title keywords, re-analyzed on demand."
        )

        addActionButton(content, "Re-analyze") { analyze() }
        resultsSection = addSettingsSection(content, "Moods", null)
        addBodyText(resultsSection, "Tap Re-analyze to classify your library.")

        return scrollView
    }

    private fun analyze() {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = repository.getAllSongs()
            buckets = songs.groupBy { MoodClassifier.classify(it) }
            renderBuckets()
        }
    }

    private fun renderBuckets() {
        resultsSection.removeAllViews()
        if (buckets.values.all { it.isEmpty() }) {
            addBodyText(resultsSection, "Nothing to classify -- your library looks empty.")
            return
        }
        for (bucket in MoodBucket.entries) {
            val songs = buckets[bucket].orEmpty()
            if (songs.isEmpty()) continue
            renderBucketTile(bucket, songs)
        }
    }

    private fun renderBucketTile(bucket: MoodBucket, songs: List<Song>) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, requireContext().dp(14))
        }
        row.addView(TextView(requireContext()).apply {
            text = "${bucket.displayName} (${songs.size} song${if (songs.size == 1) "" else "s"})"
            textSize = requireContext().sp(15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        resultsSection.addView(row)

        addChipButtonRow(
            resultsSection,
            listOf(
                "Play" to {
                    (activity as? MainActivity)?.playSongsStartingFrom(songs, 0, bucket.displayName)
                },
                "Save as Playlist" to {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.createPlaylist(bucket.displayName, songs)
                        Toast.makeText(requireContext(), "Saved \"${bucket.displayName}\" as a playlist.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )
    }
}
