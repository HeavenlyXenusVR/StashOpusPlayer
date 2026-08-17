package com.stash.opusplayer.ui.compose.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.BridgeBackupSummary

/**
 * Ported from Lumisound's BackupHistoryView -- lists automatic snapshots
 * the shared bridge takes of favorites+playlists, restorable on demand.
 * Since Stash never pushes its own `/user/sync` (see SyncApi's doc
 * comment), this list is populated by whatever OTHER client on the same
 * account has pushed a sync -- concretely, Lumisound -- or by this
 * screen's own restore action, which itself creates a `pre_restore`
 * snapshot as a side effect. An empty list on a Stash-only account is
 * expected, not a bug -- the empty state says so directly.
 */
@Composable
fun BackupHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackupHistoryContent(
        state = state,
        onRefresh = viewModel::loadBackups,
        onRequestRestoreConfirm = viewModel::requestRestoreConfirm,
        onCancelRestoreConfirm = viewModel::cancelRestoreConfirm,
        onConfirmRestore = viewModel::confirmRestore,
        onRequestClearConfirm = viewModel::requestClearConfirm,
        onCancelClearConfirm = viewModel::cancelClearConfirm,
        onConfirmClear = viewModel::confirmClear,
        modifier = modifier
    )
}

@Composable
private fun BackupHistoryContent(
    state: BackupHistoryUiState,
    onRefresh: () -> Unit,
    onRequestRestoreConfirm: (Long) -> Unit,
    onCancelRestoreConfirm: () -> Unit,
    onConfirmRestore: () -> Unit,
    onRequestClearConfirm: () -> Unit,
    onCancelClearConfirm: () -> Unit,
    onConfirmClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Backup History", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Snapshots of your favorites and playlists, taken automatically by the shared bridge -- including ones made from Lumisound on another device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Refresh") }
            TextButton(onClick = onRequestClearConfirm, enabled = state.backups.isNotEmpty() && !state.isClearing) {
                Text("Clear All")
            }
        }

        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.backups.isEmpty() -> Text(
                text = "No backups yet. Backups appear here after your account is used with Lumisound, or after your first restore here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.backups.forEach { backup ->
                BackupRow(backup = backup, isRestoring = state.isRestoring, onRestore = { onRequestRestoreConfirm(backup.id) })
            }
        }

        state.restoreMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (state.pendingRestoreId != null) {
        AlertDialog(
            onDismissRequest = onCancelRestoreConfirm,
            title = { Text("Restore this backup?") },
            text = {
                Text("This replaces the LIVE favorites and playlists on your account (on every device) with this snapshot's contents, then merges matching tracks into your local library here.")
            },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = onCancelRestoreConfirm) { Text("Cancel") } }
        )
    }

    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = onCancelClearConfirm,
            title = { Text("Clear all backups?") },
            text = { Text("Deletes every snapshot in this list. Your live favorites and playlists aren't affected.") },
            confirmButton = { TextButton(onClick = onConfirmClear) { Text("Clear All") } },
            dismissButton = { TextButton(onClick = onCancelClearConfirm) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BackupRow(backup: BridgeBackupSummary, isRestoring: Boolean, onRestore: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${backup.favoriteCount} favorite${if (backup.favoriteCount == 1) "" else "s"}, " +
                        "${backup.playlistCount} playlist${if (backup.playlistCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = backup.createdAt ?: "Unknown date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                backup.reason?.let {
                    Text(text = "Reason: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onRestore, enabled = !isRestoring) {
                if (isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Restore")
                }
            }
        }
    }
}
