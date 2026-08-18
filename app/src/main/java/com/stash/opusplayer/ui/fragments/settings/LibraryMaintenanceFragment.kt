package com.stash.opusplayer.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stash.opusplayer.data.database.CorruptFileEntity
import com.stash.opusplayer.data.database.MusicDatabase
import com.stash.opusplayer.data.database.RecentlyDeletedEntity
import com.stash.opusplayer.library.RecentlyDeletedService
import com.stash.opusplayer.work.CorruptFileFinderWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * "Library maintenance" screen -- the corrupt-file finder and recently-deleted
 * trash both live here, ported from Lumisound's CorruptFilesView and
 * RecentlyDeletedView. Periodic ID3 tag refresh ([com.stash.opusplayer.work.MetadataTagRefreshWorker])
 * has no UI of its own to speak of (same as its Swift original, which also
 * runs silently in the background) so it isn't represented on this screen.
 */
class LibraryMaintenanceFragment : NavigableSettingsFragment() {

    override val screenTitle: String = "Library Maintenance"

    private lateinit var db: MusicDatabase
    private lateinit var corruptStatusView: TextView
    private lateinit var corruptListSection: LinearLayout
    private lateinit var trashListSection: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        db = MusicDatabase.getDatabase(requireContext())

        val (scrollView, content) = createSettingsPage(
            title = "Library Maintenance",
            subtitle = "Find and clean up broken audio files, and recover anything moved to trash within the last 30 days."
        )

        buildCorruptFileSection(content)
        buildRecentlyDeletedSection(content)

        refreshCorruptFiles()
        refreshTrash()

        return scrollView
    }

    private fun buildCorruptFileSection(parent: LinearLayout) {
        val section = addSettingsSection(
            parent,
            "Corrupt File Finder",
            "Periodically checks every indexed song for header corruption or a truncated end. Deleting here is permanent -- unlike the trash below, a broken file can't be restored."
        )

        corruptStatusView = addBodyText(section, "Checking for flagged files...")

        addChipButtonRow(
            section,
            listOf(
                "Scan Now" to {
                    CorruptFileFinderWorker.scanNow(requireContext())
                    Toast.makeText(requireContext(), "Scanning your library...", Toast.LENGTH_SHORT).show()
                    viewLifecycleOwner.lifecycleScope.launch {
                        kotlinx.coroutines.delay(TimeUnit.SECONDS.toMillis(4))
                        refreshCorruptFiles()
                    }
                }
            )
        )

        corruptListSection = addSettingsSection(parent, "Flagged Files", null)

        addActionButton(parent, "Delete All Flagged Files", outlined = true) {
            confirmDeleteAllCorrupt()
        }
    }

    private fun buildRecentlyDeletedSection(parent: LinearLayout) {
        addSettingsSection(
            parent,
            "Recently Deleted",
            "Songs moved to trash from a song's menu stay here for 30 days before being purged automatically."
        )

        trashListSection = addSettingsSection(parent, "Trash", null)

        addActionButton(parent, "Empty Trash", outlined = true) {
            confirmEmptyTrash()
        }
    }

    private fun refreshCorruptFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val flagged = db.corruptFileDao().getAll()
            if (view == null) return@launch

            corruptStatusView.text = if (flagged.isEmpty()) {
                "No corrupt files found."
            } else {
                "${flagged.size} file${if (flagged.size == 1) "" else "s"} flagged."
            }

            corruptListSection.removeAllViews()
            if (flagged.isEmpty()) {
                addBodyText(corruptListSection, "Nothing flagged right now.")
                return@launch
            }
            flagged.forEach { entry -> renderCorruptFileTile(entry) }
        }
    }

    private fun renderCorruptFileTile(entry: CorruptFileEntity) {
        addSettingsTile(
            corruptListSection,
            title = entry.title.ifBlank { entry.path.substringAfterLast('/') },
            summary = entry.reason,
            buttonLabel = "Delete"
        ) {
            deleteCorruptFile(entry)
        }
    }

    private fun deleteCorruptFile(entry: CorruptFileEntity) {
        val uri = android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, entry.songId
        )
        (activity as? com.stash.opusplayer.ui.MainActivity)?.requestMediaDelete(uri) { deleted ->
            viewLifecycleOwner.lifecycleScope.launch {
                if (deleted) {
                    db.corruptFileDao().deleteBySongId(entry.songId)
                    db.songDao().deleteById(entry.songId)
                    Toast.makeText(requireContext(), "Deleted \"${entry.title}\".", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Delete was cancelled.", Toast.LENGTH_SHORT).show()
                }
                refreshCorruptFiles()
            }
        }
    }

    private fun confirmDeleteAllCorrupt() {
        viewLifecycleOwner.lifecycleScope.launch {
            val flagged = db.corruptFileDao().getAll()
            if (flagged.isEmpty()) {
                Toast.makeText(requireContext(), "Nothing to delete.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete ${flagged.size} corrupt file${if (flagged.size == 1) "" else "s"}?")
                .setMessage("This permanently deletes these files from your device. This can't be undone.")
                .setPositiveButton("Delete") { _, _ -> deleteAllCorrupt(flagged) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deleteAllCorrupt(flagged: List<CorruptFileEntity>) {
        viewLifecycleOwner.lifecycleScope.launch {
            var deletedCount = 0
            for (entry in flagged) {
                val uri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, entry.songId
                )
                val deleted = suspendDeleteRequest(uri)
                if (deleted) {
                    db.corruptFileDao().deleteBySongId(entry.songId)
                    db.songDao().deleteById(entry.songId)
                    deletedCount++
                }
            }
            Toast.makeText(requireContext(), "Deleted $deletedCount of ${flagged.size} files.", Toast.LENGTH_SHORT).show()
            refreshCorruptFiles()
        }
    }

    private suspend fun suspendDeleteRequest(uri: android.net.Uri): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val mainActivity = activity as? com.stash.opusplayer.ui.MainActivity
            if (mainActivity == null) {
                continuation.resumeWith(Result.success(false))
                return@suspendCancellableCoroutine
            }
            mainActivity.requestMediaDelete(uri) { deleted ->
                if (continuation.isActive) continuation.resumeWith(Result.success(deleted))
            }
        }

    private fun refreshTrash() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = db.recentlyDeletedDao().getAll()
            if (view == null) return@launch

            trashListSection.removeAllViews()
            if (entries.isEmpty()) {
                addBodyText(trashListSection, "Trash is empty.")
                return@launch
            }
            entries.forEach { entry -> renderTrashTile(entry) }
        }
    }

    private fun renderTrashTile(entry: RecentlyDeletedEntity) {
        val daysLeft = RecentlyDeletedService.daysRemaining(entry)
        addSettingsTile(
            trashListSection,
            title = entry.title.ifBlank { "Untitled" },
            summary = "${entry.artist.ifBlank { "Unknown Artist" }} • $daysLeft day${if (daysLeft == 1L) "" else "s"} left",
            buttonLabel = "Restore"
        ) {
            restoreTrashEntry(entry)
        }
        addChipButtonRow(trashListSection, listOf("Delete Forever: \"${entry.title}\"" to {
            purgeTrashEntry(entry)
        }))
    }

    private fun restoreTrashEntry(entry: RecentlyDeletedEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val restored = RecentlyDeletedService.restore(requireContext(), entry, db.recentlyDeletedDao())
            if (restored) {
                Toast.makeText(requireContext(), "\"${entry.title}\" restored to Music/Restored.", Toast.LENGTH_LONG).show()
                runCatching {
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.stash.opusplayer.work.LibraryRescanWorker>().build()
                    androidx.work.WorkManager.getInstance(requireContext()).enqueue(request)
                }
            } else {
                Toast.makeText(requireContext(), "Couldn't restore \"${entry.title}\".", Toast.LENGTH_SHORT).show()
            }
            refreshTrash()
        }
    }

    private fun purgeTrashEntry(entry: RecentlyDeletedEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete \"${entry.title}\" forever?")
            .setMessage("This permanently removes it from trash. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    RecentlyDeletedService.purgeForever(requireContext(), entry, db.recentlyDeletedDao())
                    refreshTrash()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmEmptyTrash() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = db.recentlyDeletedDao().getAll()
            if (entries.isEmpty()) {
                Toast.makeText(requireContext(), "Trash is already empty.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Empty trash?")
                .setMessage("Permanently deletes all ${entries.size} item${if (entries.size == 1) "" else "s"} in trash. This can't be undone.")
                .setPositiveButton("Empty Trash") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        RecentlyDeletedService.purgeAll(requireContext(), db.recentlyDeletedDao())
                        refreshTrash()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
