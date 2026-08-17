package com.stash.opusplayer.ui.compose.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.BridgePlaylist
import com.stash.opusplayer.bridge.api.BridgePlaylistTrack
import com.stash.opusplayer.bridge.api.PlaylistCollaborator
import com.stash.opusplayer.bridge.api.SharedWithMePlaylist

@Composable
fun CloudPlaylistsScreen(
    modifier: Modifier = Modifier,
    viewModel: CloudPlaylistsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.selectedPlaylistId != null) {
        CloudPlaylistDetailContent(
            state = state,
            currentUsername = viewModel.currentUsername,
            onBack = viewModel::closeDetail,
            onNewCollaboratorUsernameChanged = viewModel::onNewCollaboratorUsernameChanged,
            onNewCollaboratorRoleChanged = viewModel::onNewCollaboratorRoleChanged,
            onAddCollaborator = viewModel::addCollaborator,
            onRemoveCollaborator = viewModel::removeCollaborator,
            onRequestLeaveConfirm = viewModel::requestLeaveConfirm,
            onCancelLeaveConfirm = viewModel::cancelLeaveConfirm,
            onConfirmLeavePlaylist = viewModel::confirmLeavePlaylist,
            modifier = modifier
        )
    } else {
        CloudPlaylistsListContent(
            state = state,
            onOpenPlaylist = viewModel::openPlaylist,
            modifier = modifier
        )
    }
}

@Composable
private fun CloudPlaylistsListContent(
    state: CloudPlaylistsViewModel.UiState,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Cloud Playlists", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Playlists synced to your account -- shared with Lumisound. Invite collaborators to a playlist you own from its detail screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLoadingList) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(text = "Your Playlists", style = MaterialTheme.typography.titleMedium)
            if (state.ownPlaylists.isEmpty()) {
                Text(
                    text = "No cloud playlists yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.ownPlaylists.forEach { playlist ->
                    PlaylistRow(
                        title = playlist.name,
                        subtitle = "${playlist.tracks.size} tracks",
                        onClick = { onOpenPlaylist(playlist.id) }
                    )
                }
            }

            Divider()

            Text(text = "Shared with You", style = MaterialTheme.typography.titleMedium)
            if (state.sharedPlaylists.isEmpty()) {
                Text(
                    text = "No one has shared a playlist with you yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.sharedPlaylists.forEach { playlist ->
                    PlaylistRow(
                        title = playlist.name,
                        subtitle = "by ${playlist.ownerUsername} · ${playlist.role.replaceFirstChar { it.uppercase() }}",
                        onClick = { onOpenPlaylist(playlist.id) }
                    )
                }
            }
        }

        state.listError?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CloudPlaylistDetailContent(
    state: CloudPlaylistsViewModel.UiState,
    currentUsername: String?,
    onBack: () -> Unit,
    onNewCollaboratorUsernameChanged: (String) -> Unit,
    onNewCollaboratorRoleChanged: (Boolean) -> Unit,
    onAddCollaborator: () -> Unit,
    onRemoveCollaborator: (String) -> Unit,
    onRequestLeaveConfirm: () -> Unit,
    onCancelLeaveConfirm: () -> Unit,
    onConfirmLeavePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlist: BridgePlaylist? = state.detailPlaylist
    val isOwner = playlist?.role == "owner" || playlist?.role == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text(text = playlist?.name ?: "Playlist", style = MaterialTheme.typography.headlineSmall)
        }

        if (state.isLoadingDetail) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (playlist != null) {
            playlist.description?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            if (playlist.role != null && playlist.role != "owner") {
                Text(
                    text = "Shared by another user · your role: ${playlist.role.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(text = "Tracks (${playlist.tracks.size})", style = MaterialTheme.typography.titleMedium)
            if (playlist.tracks.isEmpty()) {
                Text(text = "No tracks yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                playlist.tracks.sortedBy { it.position ?: 0 }.forEach { track ->
                    TrackRow(track)
                }
            }

            Divider()

            Text(text = "Collaborators", style = MaterialTheme.typography.titleMedium)
            if (state.isLoadingCollaborators) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (state.collaborators.isEmpty()) {
                Text(
                    text = "No collaborators yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.collaborators.forEach { collaborator ->
                    CollaboratorRow(
                        collaborator = collaborator,
                        canRemove = isOwner || collaborator.username == currentUsername,
                        isRemoving = state.removingCollaboratorUserId == collaborator.userId,
                        onRemove = { onRemoveCollaborator(collaborator.userId) }
                    )
                }
            }

            if (isOwner) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Invite a Collaborator", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.newCollaboratorUsername,
                            onValueChange = onNewCollaboratorUsernameChanged,
                            label = { Text("Username") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoleToggleButton(
                                label = "Editor",
                                selected = state.newCollaboratorIsEditor,
                                onClick = { onNewCollaboratorRoleChanged(true) }
                            )
                            RoleToggleButton(
                                label = "Viewer",
                                selected = !state.newCollaboratorIsEditor,
                                onClick = { onNewCollaboratorRoleChanged(false) }
                            )
                        }
                        Button(
                            onClick = onAddCollaborator,
                            enabled = !state.isAddingCollaborator && state.newCollaboratorUsername.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isAddingCollaborator) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Add Collaborator")
                            }
                        }
                    }
                }
            } else {
                TextButton(onClick = onRequestLeaveConfirm) {
                    Text("Leave This Playlist", color = MaterialTheme.colorScheme.error)
                }
            }

            state.collaboratorActionError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        state.detailError?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (state.showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = onCancelLeaveConfirm,
            title = { Text("Leave this playlist?") },
            text = { Text("You'll lose access unless the owner invites you again.") },
            confirmButton = { TextButton(onClick = onConfirmLeavePlaylist) { Text("Leave", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onCancelLeaveConfirm) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RoleToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, colors = ButtonDefaults.outlinedButtonColors()) { Text(label) }
    }
}

@Composable
private fun TrackRow(track: BridgePlaylistTrack) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = track.title ?: "Untitled", style = MaterialTheme.typography.bodyMedium)
            val subtitle = listOfNotNull(track.artist, track.album).joinToString(" — ")
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        track.durationSeconds?.takeIf { it > 0 }?.let { seconds ->
            Text(
                text = "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CollaboratorRow(
    collaborator: PlaylistCollaborator,
    canRemove: Boolean,
    isRemoving: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = collaborator.username, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = collaborator.role.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canRemove) {
            if (isRemoving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
