package com.stash.opusplayer.ui.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.stash.opusplayer.R
import com.stash.opusplayer.ui.MainActivity
import com.stash.opusplayer.ui.appearance.AppearanceFragment
import com.stash.opusplayer.ui.customization.VisualCustomizationFragment
import com.stash.opusplayer.ui.fragments.settings.AchievementsFragment
import com.stash.opusplayer.ui.fragments.settings.BackupHistoryFragment
import com.stash.opusplayer.ui.fragments.settings.BridgeAccountFragment
import com.stash.opusplayer.ui.fragments.settings.CloudPlaylistsFragment
import com.stash.opusplayer.ui.fragments.settings.DiscoveryFragment
import com.stash.opusplayer.ui.fragments.settings.FriendsHostFragment
import com.stash.opusplayer.ui.fragments.settings.LibraryMaintenanceFragment
import com.stash.opusplayer.ui.fragments.settings.LibrarySettingsFragment
import com.stash.opusplayer.ui.fragments.settings.MoodPlaylistsFragment
import com.stash.opusplayer.ui.fragments.settings.ScrobblingFragment
import com.stash.opusplayer.ui.fragments.settings.TempoAnalyzerFragment
import com.stash.opusplayer.ui.fragments.settings.StreamingBrowseHostFragment
import com.stash.opusplayer.ui.fragments.settings.PlaybackSettingsFragment
import com.stash.opusplayer.ui.fragments.settings.SmartPlaylistsFragment
import com.stash.opusplayer.ui.fragments.settings.StreamingSettingsFragment
import com.stash.opusplayer.ui.fragments.settings.addActionButton
import com.stash.opusplayer.ui.fragments.settings.addBodyText
import com.stash.opusplayer.ui.fragments.settings.addChipButtonRow
import com.stash.opusplayer.ui.fragments.settings.addSettingsSection
import com.stash.opusplayer.ui.fragments.settings.addSettingsTile
import com.stash.opusplayer.ui.fragments.settings.addSpinnerControl
import com.stash.opusplayer.ui.fragments.settings.addSwitchControl
import com.stash.opusplayer.ui.fragments.settings.createSettingsPage
import com.stash.opusplayer.ui.fragments.settings.navigateToSettingsScreen
import com.stash.opusplayer.ui.fragments.settings.updateSettingsActionBar
import com.stash.opusplayer.updates.UpdateFrequency

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val (scrollView, content) = createSettingsPage(
            title = "Settings",
            subtitle = "A rebuilt settings hub for playback, library, streaming, visuals, maintenance, and updates."
        )

        buildQuickActions(content)
        buildCoreNavigation(content)
        buildUpdateControls(content)

        return scrollView
    }

    override fun onResume() {
        super.onResume()
        updateSettingsActionBar(getString(R.string.menu_settings), false)
    }

    private fun buildQuickActions(parent: LinearLayout) {
        parent.addView(TextView(requireContext()).apply {
            text = "Quick Actions"
            textSize = 18f
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (8 * requireContext().resources.displayMetrics.density).toInt())
        })

        addChipButtonRow(
            parent,
            listOf(
                "Playback" to { openSettingsScreen(PlaybackSettingsFragment(), "Playback Settings") },
                "Appearance" to { openSettingsScreen(AppearanceFragment(), "Appearance") },
                "Motion" to { openSettingsScreen(VisualCustomizationFragment(), "Animations & Background") },
                "Rescan Library" to { startLibraryRescan() },
                "Check Updates" to { checkForUpdates(force = true) }
            )
        )
    }

    private fun buildCoreNavigation(parent: LinearLayout) {
        val section = addSettingsSection(
            parent,
            "Core Areas",
            "Each card opens a focused screen instead of forcing everything through the old broken mega-fragment."
        )

        addSettingsTile(
            section,
            title = "Playback & Queue",
            summary = "Speed, pitch, reverb, crossfade, ReplayGain, and playback recovery tools."
        ) {
            openSettingsScreen(PlaybackSettingsFragment(), "Playback Settings")
        }

        addSettingsTile(
            section,
            title = "Audio Effects",
            summary = "Equalizer, bass boost, virtualizer, and loudness controls."
        ) {
            openSettingsScreen(EqualizerFragment(), "Equalizer")
        }

        addSettingsTile(
            section,
            title = "Appearance & Layout",
            summary = "Theme colors, mini-player styles, and now playing layout themes."
        ) {
            openSettingsScreen(AppearanceFragment(), "Appearance")
        }

        addSettingsTile(
            section,
            title = "Animations & Background",
            summary = "Stable animation controls, background mode presets, and photo tuning."
        ) {
            openSettingsScreen(VisualCustomizationFragment(), "Animations & Background")
        }

        addSettingsTile(
            section,
            title = "Library & Scanning",
            summary = "Folder access, default layouts, artwork cleanup, and scan scheduling."
        ) {
            openSettingsScreen(LibrarySettingsFragment(), "Library Settings")
        }

        addSettingsTile(
            section,
            title = "Library Maintenance",
            summary = "Find and delete corrupt audio files, and restore anything recently moved to trash."
        ) {
            openSettingsScreen(LibraryMaintenanceFragment(), "Library Maintenance")
        }

        addSettingsTile(
            section,
            title = "Tempo (BPM)",
            summary = "On-device tempo detection -- powers Mood Playlists' BPM tier."
        ) {
            openSettingsScreen(TempoAnalyzerFragment(), "Tempo (BPM)")
        }

        addSettingsTile(
            section,
            title = "Mood Playlists",
            summary = "Energetic, Chill, Focus, and Sleep -- auto-grouped from your library."
        ) {
            openSettingsScreen(MoodPlaylistsFragment(), "Mood Playlists")
        }

        addSettingsTile(
            section,
            title = "Smart Playlists",
            summary = "Lua-scripted rules that decide playlist membership from the current library."
        ) {
            openSettingsScreen(SmartPlaylistsFragment(), "Smart Playlists")
        }

        addSettingsTile(
            section,
            title = "Streaming & Downloads",
            summary = "YouTube API key, Lavalink routing, extractor health, and yt-dlp updates."
        ) {
            openSettingsScreen(StreamingSettingsFragment(), "Streaming & Downloads")
        }

        addSettingsTile(
            section,
            title = "Account & Server",
            summary = "Sign in with the same account you use on Lumisound -- both apps talk to the same server."
        ) {
            openSettingsScreen(BridgeAccountFragment(), "Account & Server")
        }

        addSettingsTile(
            section,
            title = "Scrobbling",
            summary = "Link Last.fm, Libre.fm, or ListenBrainz -- scrobbles happen automatically once linked."
        ) {
            openSettingsScreen(ScrobblingFragment(), "Scrobbling")
        }

        addSettingsTile(
            section,
            title = "Achievements",
            summary = "Badges, streaks, and listening stats -- shared with Lumisound."
        ) {
            openSettingsScreen(AchievementsFragment(), "Achievements")
        }

        addSettingsTile(
            section,
            title = "Discover",
            summary = "Discover Mix (new tracks based on your top artists) and On This Day (what you played on this date in past years)."
        ) {
            openSettingsScreen(DiscoveryFragment(), "Discover")
        }

        addSettingsTile(
            section,
            title = "Cloud Playlists",
            summary = "Browse playlists synced to your account and playlists shared with you -- manage collaborators, shared with Lumisound."
        ) {
            openSettingsScreen(CloudPlaylistsFragment(), "Cloud Playlists")
        }

        addSettingsTile(
            section,
            title = "Backup History",
            summary = "Restore favorites and playlists from an automatic snapshot -- shared with Lumisound."
        ) {
            openSettingsScreen(BackupHistoryFragment(), "Backup History")
        }

        addSettingsTile(
            section,
            title = "Friends",
            summary = "Friend requests and presence, shared with your Lumisound account."
        ) {
            openSettingsScreen(FriendsHostFragment(), "Friends")
        }

        addSettingsTile(
            section,
            title = "Browse & Stream",
            summary = "Browse and stream from the bridge server."
        ) {
            openSettingsScreen(StreamingBrowseHostFragment(), "Browse & Stream")
        }
    }

    private fun buildUpdateControls(parent: LinearLayout) {
        val section = addSettingsSection(
            parent,
            "Updates",
            "The update controls are now visible on the first screen instead of being buried inside the old general tab."
        )

        val updateManager = (activity as? MainActivity)?.getUpdateManager()
        val updatePreferences = updateManager?.getUpdatePreferences()

        addSwitchControl(
            section,
            title = "Automatic update checks",
            summary = "Let the app check in the background and decide when to surface new releases.",
            checked = updatePreferences?.autoCheckEnabled ?: true
        ) { enabled ->
            updateManager?.getUpdatePreferences()?.let {
                updateManager.updatePreferences(it.copy(autoCheckEnabled = enabled))
            }
        }

        val frequencySpinner = addSpinnerControl(
            section,
            title = "Check frequency",
            summary = "How often background checks should run when automatic updates are enabled.",
            entries = listOf("Daily", "Weekly", "Monthly", "Never")
        )
        val currentFrequency = updatePreferences?.checkFrequency ?: UpdateFrequency.DAILY
        val frequencyIndex = when (currentFrequency) {
            UpdateFrequency.DAILY -> 0
            UpdateFrequency.WEEKLY -> 1
            UpdateFrequency.MONTHLY -> 2
            UpdateFrequency.NEVER -> 3
        }
        var frequencyHydrated = false
        frequencySpinner.setSelection(frequencyIndex)
        frequencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!frequencyHydrated) {
                    frequencyHydrated = true
                    return
                }
                updateManager?.getUpdatePreferences()?.let {
                    val frequency = when (position) {
                        1 -> UpdateFrequency.WEEKLY
                        2 -> UpdateFrequency.MONTHLY
                        3 -> UpdateFrequency.NEVER
                        else -> UpdateFrequency.DAILY
                    }
                    updateManager?.updatePreferences(it.copy(checkFrequency = frequency))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        addActionButton(section, "Check for updates now") {
            checkForUpdates(force = true)
        }
    }

    private fun openSettingsScreen(fragment: Fragment, title: String) {
        updateSettingsActionBar(title, true)
        navigateToSettingsScreen(fragment)
    }

    private fun startLibraryRescan() {
        runCatching {
            val request = androidx.work.OneTimeWorkRequestBuilder<com.stash.opusplayer.work.LibraryRescanWorker>().build()
            androidx.work.WorkManager.getInstance(requireContext()).enqueue(request)
            Toast.makeText(requireContext(), "Library rescan started.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(requireContext(), "Unable to start a library rescan.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkForUpdates(force: Boolean) {
        runCatching {
            (activity as? MainActivity)?.getUpdateManager()?.checkForUpdates(requireActivity(), forceCheck = force)
        }.onFailure {
            Toast.makeText(requireContext(), "Update check could not be started.", Toast.LENGTH_SHORT).show()
        }
    }
}
