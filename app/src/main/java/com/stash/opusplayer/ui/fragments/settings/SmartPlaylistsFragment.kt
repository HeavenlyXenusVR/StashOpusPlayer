package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.stash.opusplayer.data.MusicRepository
import com.stash.opusplayer.data.database.SmartPlaylistEntity
import com.stash.opusplayer.lua.LuaSmartPlaylistEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First UI surface for [LuaSmartPlaylistEngine]: create a named rule script,
 * preview how many of the current library's songs it matches before saving,
 * and browse/delete saved rules. There's no dedicated "view matched songs"
 * list yet (that's a `SongsAdapter`/click-through integration, separate
 * scope from getting the engine itself usable) — Preview surfaces the match
 * count as a toast, which is enough to tell a working rule from a broken one.
 */
class SmartPlaylistsFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Smart Playlists"

    private lateinit var repository: MusicRepository
    private lateinit var nameField: TextInputEditText
    private lateinit var scriptField: TextInputEditText
    private lateinit var listSection: LinearLayout

    private val defaultScriptTemplate = """
        -- Matches any song shorter than 3 minutes. Edit this, or write your
        -- own -- every song is passed to matches() with: id, title, artist,
        -- album, genre, year, duration_ms, favorite.
        function matches(song)
            return song.duration_ms < 180000
        end
    """.trimIndent()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = MusicRepository(requireContext())

        val (scrollView, content) = createSettingsPage(
            title = "Smart Playlists",
            subtitle = "Rule scripts that decide playlist membership from the current library, instead of a fixed track list."
        )

        buildCreateSection(content)
        listSection = addSettingsSection(
            content,
            "Saved Rules",
            "Tap Preview to see how many songs currently match. Delete removes the rule -- it never touches the songs themselves."
        )

        viewLifecycleOwner.lifecycleScope.launch {
            repository.getSmartPlaylists().collectLatest { playlists ->
                renderSavedPlaylists(playlists)
            }
        }

        return scrollView
    }

    private fun buildCreateSection(parent: LinearLayout) {
        val section = addSettingsSection(
            parent,
            "New Smart Playlist",
            "Write a `matches(song)` function returning true/false. Errors and non-matches are treated the same -- a broken script just matches nothing."
        )

        nameField = addTextInputControl(
            section,
            title = "Name",
            summary = "",
            hint = "e.g. Short Songs",
            initialText = ""
        )

        scriptField = addTextInputControl(
            section,
            title = "Rule script",
            summary = "",
            hint = "Lua",
            initialText = defaultScriptTemplate,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        ).apply {
            minLines = 6
            isSingleLine = false
        }

        addChipButtonRow(
            section,
            listOf(
                "Preview" to { previewCurrentScript() },
                "Save" to { saveCurrentScript() }
            )
        )
    }

    private fun currentScriptText(): String = scriptField.text?.toString().orEmpty()

    private fun previewCurrentScript() {
        val script = currentScriptText()
        if (script.isBlank()) {
            Toast.makeText(requireContext(), "Write a rule script first.", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = repository.getAllSongs()
            val matched = withContext(Dispatchers.Default) {
                LuaSmartPlaylistEngine.filterSongIds(script, songs)
            }
            if (matched == null) {
                Toast.makeText(requireContext(), "Script failed to run -- check for a missing `matches` function or a syntax error.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Matches ${matched.size} of ${songs.size} song(s).", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveCurrentScript() {
        val name = nameField.text?.toString()?.trim().orEmpty()
        val script = currentScriptText()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Give it a name first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (script.isBlank()) {
            Toast.makeText(requireContext(), "Write a rule script first.", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.createSmartPlaylist(name, script)
            nameField.setText("")
            Toast.makeText(requireContext(), "\"$name\" saved.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSavedPlaylists(playlists: List<SmartPlaylistEntity>) {
        // addSettingsSection already added its header/summary once, above --
        // this only replaces the tiles underneath as the Flow re-emits.
        listSection.removeAllViews()
        if (playlists.isEmpty()) {
            addBodyText(listSection, "No smart playlists yet.")
            return
        }
        playlists.forEach { playlist ->
            addSettingsTile(
                listSection,
                title = playlist.name,
                summary = "Tap to preview its current match count.",
                buttonLabel = "Preview"
            ) {
                previewSavedPlaylist(playlist)
            }
            addChipButtonRow(listSection, listOf("Delete \"${playlist.name}\"" to { deleteSavedPlaylist(playlist) }))
        }
    }

    private fun previewSavedPlaylist(playlist: SmartPlaylistEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = repository.getAllSongs()
            val matched = withContext(Dispatchers.Default) {
                LuaSmartPlaylistEngine.filterSongIds(playlist.luaScript, songs)
            }
            if (matched == null) {
                Toast.makeText(requireContext(), "\"${playlist.name}\" failed to run.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "\"${playlist.name}\" matches ${matched.size} of ${songs.size} song(s).", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteSavedPlaylist(playlist: SmartPlaylistEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteSmartPlaylist(playlist.id)
            Toast.makeText(requireContext(), "\"${playlist.name}\" deleted.", Toast.LENGTH_SHORT).show()
        }
    }
}
