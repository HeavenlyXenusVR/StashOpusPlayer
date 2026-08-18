package com.stash.opusplayer.ui.compose.achievements

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Ported from Lumisound's achievements/stats surface (`GET /user/achievements`)
 * -- badges, listening totals, and streaks computed live from play history
 * on the shared bridge. Depends on this app actually logging plays first
 * (see [com.stash.opusplayer.history.PlayHistoryLogger]) -- a brand new
 * account, or one that's only ever used Lumisound while this app's history
 * logging was off, will show zeros/no badges until enough plays land.
 */
@Composable
fun AchievementsScreen(
    modifier: Modifier = Modifier,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AchievementsContent(state = state, onRefresh = viewModel::load, modifier = modifier)
}

@Composable
private fun AchievementsContent(
    state: AchievementsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Achievements", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Refresh") }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            StatsRow(state)

            Text(text = "Badges", style = MaterialTheme.typography.titleMedium)
            if (state.badges.isEmpty()) {
                Text(
                    text = "No badges yet -- keep listening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.badges.forEach { badgeId ->
                    val display = BadgeInfo.displayFor(badgeId)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = display.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = display.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        state.error?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatsRow(state: AchievementsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatLine("Total plays", state.totalPlays.toString())
            StatLine("Time listened", formatListenTime(state.totalListenSeconds))
            StatLine("Current streak", "${state.currentStreakDays} day${if (state.currentStreakDays == 1) "" else "s"}")
            StatLine("Longest streak", "${state.longestStreakDays} day${if (state.longestStreakDays == 1) "" else "s"}")
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun formatListenTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
