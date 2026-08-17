package com.stash.opusplayer.ui.compose.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.FriendActivityEntry
import com.stash.opusplayer.bridge.api.FriendLeaderboardEntry

@Composable
fun FriendActivityScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendActivityViewModel = hiltViewModel(),
    onProfileClick: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Friend Activity", style = MaterialTheme.typography.headlineSmall)

        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.error != null -> Text(text = state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> {
                if (state.leaderboard.isNotEmpty()) {
                    LeaderboardCard(state.leaderboard, onProfileClick)
                }

                Text(text = "Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.activity.isEmpty()) {
                    Text(
                        text = "No recent activity from friends yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.activity.forEach { entry ->
                        ActivityRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(entries: List<FriendLeaderboardEntry>, onProfileClick: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Most Active This Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            entries.take(5).forEachIndexed { index, entry ->
                LeaderboardRow(rank = index + 1, entry = entry, onClick = { onProfileClick(entry.userId) })
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: FriendLeaderboardEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank." },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.username,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "${entry.playCount} play${if (entry.playCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityRow(entry: FriendActivityEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = if (entry.kind == "played") "▶" else "♥", style = MaterialTheme.typography.bodyMedium)
        Column {
            Text(
                text = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            val subtitle = listOfNotNull(entry.title, entry.artist).joinToString(" — ")
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Divider()
}
