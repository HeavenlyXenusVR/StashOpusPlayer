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
import com.stash.opusplayer.data.database.MusicDatabase
import com.stash.opusplayer.tempo.BpmCacheService
import kotlinx.coroutines.launch

/**
 * Manual "Analyze Tempo" screen, ported from Lumisound's Practice Mode BPM
 * badge concept (simplified to a library-wide batch here rather than a
 * per-track Now Playing readout, since Stash has no metronome/practice-mode
 * UI yet to hang a live per-track BPM display off of). The periodic
 * [com.stash.opusplayer.work.BpmAnalysisWorker] already fills this in
 * gradually in the background; this screen is the on-demand "do it now, and
 * show me the results" surface, plus visibility into how much of the
 * library has a tempo yet.
 */
class TempoAnalyzerFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Tempo (BPM)"

    private lateinit var repository: MusicRepository
    private lateinit var statusView: TextView
    private lateinit var resultsSection: LinearLayout
    private var isAnalyzing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = MusicRepository(requireContext())

        val (scrollView, content) = createSettingsPage(
            title = "Tempo (BPM)",
            subtitle = "On-device tempo detection -- feeds Mood Playlists' Energetic/Chill/Focus/Sleep grouping. A background pass also fills this in gradually on its own."
        )

        statusView = addBodyText(content, "Checking analyzed count...")
        addActionButton(content, "Analyze Library Now") { analyzeAll() }
        resultsSection = addSettingsSection(content, "Analyzed Tracks", null)

        refresh()
        return scrollView
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = repository.getAllSongs()
            val bpmBySongId = BpmCacheService.cachedBpmBySongId(requireContext())
            statusView.text = "${bpmBySongId.size} of ${songs.size} track${if (songs.size == 1) "" else "s"} analyzed."

            resultsSection.removeAllViews()
            val analyzed = songs.filter { bpmBySongId.containsKey(it.id) }
                .sortedByDescending { bpmBySongId[it.id] }
            if (analyzed.isEmpty()) {
                addBodyText(resultsSection, "No tracks analyzed yet.")
                return@launch
            }
            analyzed.take(200).forEach { song ->
                val bpm = bpmBySongId[song.id] ?: return@forEach
                addSettingsTile(
                    resultsSection,
                    title = song.displayName,
                    summary = song.artistName.ifBlank { "Unknown Artist" },
                    buttonLabel = "%.1f BPM".format(bpm)
                ) { /* display-only */ }
            }
            if (analyzed.size > 200) {
                addBodyText(resultsSection, "+ ${analyzed.size - 200} more not shown.")
            }
        }
    }

    private fun analyzeAll() {
        if (isAnalyzing) return
        isAnalyzing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = repository.getAllSongs()
            val bpmBySongId = BpmCacheService.cachedBpmBySongId(requireContext())
            val pending = songs.filter { !bpmBySongId.containsKey(it.id) }
            if (pending.isEmpty()) {
                Toast.makeText(requireContext(), "Everything's already analyzed.", Toast.LENGTH_SHORT).show()
                isAnalyzing = false
                return@launch
            }

            Toast.makeText(requireContext(), "Analyzing ${pending.size} track${if (pending.size == 1) "" else "s"}...", Toast.LENGTH_SHORT).show()
            var succeeded = 0
            for ((index, song) in pending.withIndex()) {
                val entity = MusicDatabase.getDatabase(requireContext()).songDao().getSongById(song.id) ?: continue
                if (BpmCacheService.getOrAnalyze(requireContext(), entity) != null) succeeded++
                if (index % 10 == 0) {
                    statusView.text = "Analyzing... $index of ${pending.size}"
                }
            }
            isAnalyzing = false
            Toast.makeText(requireContext(), "Analyzed $succeeded of ${pending.size} track${if (pending.size == 1) "" else "s"}.", Toast.LENGTH_LONG).show()
            refresh()
        }
    }
}
