package com.stash.opusplayer.ui.compose.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import com.stash.opusplayer.bridge.api.BlockedUser

@Composable
fun BlockedUsersScreen(
    modifier: Modifier = Modifier,
    viewModel: BlockedUsersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Blocked Users", style = MaterialTheme.typography.headlineSmall)

        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.error != null -> Text(text = state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.blocked.isEmpty() -> Text(
                text = "You haven't blocked anyone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.blocked.forEach { user ->
                BlockedUserRow(
                    user = user,
                    isUnblocking = state.unblockingUserId == user.userId,
                    onUnblock = { viewModel.unblock(user.userId) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun BlockedUserRow(user: BlockedUser, isUnblocking: Boolean, onUnblock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = user.displayName?.takeIf { it.isNotBlank() } ?: user.username, style = MaterialTheme.typography.bodyMedium)
        if (isUnblocking) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onUnblock) { Text("Unblock") }
        }
    }
}
