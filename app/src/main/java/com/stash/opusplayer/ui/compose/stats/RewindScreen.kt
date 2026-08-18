package com.stash.opusplayer.ui.compose.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stash.opusplayer.bridge.api.TopArtistStat
import com.stash.opusplayer.bridge.api.TopTrackStat

@Composable
fun RewindScreen(viewModel: RewindViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedMode.ordinal) {
            RewindMode.entries.forEach { mode ->
                Tab(
                    selected = state.selectedMode == mode,
                    onClick = { viewModel.onModeSelected(mode) },
                    text = {
                        Text(
                            when (mode) {
                                RewindMode.ALL_TIME -> "All Time"
                                RewindMode.THIS_MONTH -> "This Month"
                                RewindMode.THIS_YEAR -> "This Year"
                            }
                        )
                    }
                )
            }
        }

        when (state.selectedMode) {
            RewindMode.ALL_TIME -> RewindModeContent(
                isLoading = state.isLoadingAllTime,
                error = state.allTimeError,
                onRetry = { viewModel.loadAllTime() }
            ) {
                AllTimeContent(state.allTime)
            }
            RewindMode.THIS_MONTH -> RewindModeContent(
                isLoading = state.isLoadingMonth,
                error = state.monthError,
                onRetry = { viewModel.loadMonth() }
            ) {
                MonthContent(state.month)
            }
            RewindMode.THIS_YEAR -> RewindModeContent(
                isLoading = state.isLoadingYear,
                error = state.yearError,
                onRetry = { viewModel.loadYear() }
            ) {
                YearContent(state.year)
            }
        }
    }
}

@Composable
private fun RewindModeContent(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error)
                Text(
                    "Tap to retry",
                    modifier = Modifier.padding(top = 8.dp).clickable { onRetry() }
                )
            }
        }
        else -> content()
    }
}

@Composable
private fun AllTimeContent(stats: com.stash.opusplayer.bridge.api.AccountStatsResponse?) {
    if (stats == null) return
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatsSummaryCard(totalPlays = stats.totalPlays, totalListenSeconds = stats.totalListenSeconds) }
        if (stats.topArtists.isNotEmpty()) {
            item { SectionHeader("Top Artists") }
            items(stats.topArtists) { TopArtistRow(it) }
        }
        if (stats.topTracks.isNotEmpty()) {
            item { SectionHeader("Top Tracks") }
            items(stats.topTracks) { TopTrackRow(it) }
        }
    }
}

@Composable
private fun MonthContent(month: com.stash.opusplayer.bridge.api.MonthInReviewResponse?) {
    if (month == null) return
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatsSummaryCard(totalPlays = month.totalPlays, totalListenSeconds = month.totalListenSeconds) }
        item { DistinctCountsRow(month.distinctArtists, month.distinctTracks, month.averageBpm) }
        if (month.topArtists.isNotEmpty()) {
            item { SectionHeader("Top Artists") }
            items(month.topArtists) { TopArtistRow(it) }
        }
        if (month.topTracks.isNotEmpty()) {
            item { SectionHeader("Top Tracks") }
            items(month.topTracks) { TopTrackRow(it) }
        }
    }
}

@Composable
private fun YearContent(year: com.stash.opusplayer.bridge.api.YearInReviewResponse?) {
    if (year == null) return
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatsSummaryCard(totalPlays = year.totalPlays, totalListenSeconds = year.totalListenSeconds) }
        item { DistinctCountsRow(year.distinctArtists, year.distinctTracks, year.averageBpm) }
        if (year.topArtists.isNotEmpty()) {
            item { SectionHeader("Top Artists") }
            items(year.topArtists) { TopArtistRow(it) }
        }
        if (year.topTracks.isNotEmpty()) {
            item { SectionHeader("Top Tracks") }
            items(year.topTracks) { TopTrackRow(it) }
        }
    }
}

@Composable
private fun StatsSummaryCard(totalPlays: Int, totalListenSeconds: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$totalPlays plays", fontWeight = FontWeight.Bold)
            Text(formatListenDuration(totalListenSeconds))
        }
    }
}

@Composable
private fun DistinctCountsRow(distinctArtists: Int, distinctTracks: Int, averageBpm: Double?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$distinctArtists artists  ·  $distinctTracks tracks")
            if (averageBpm != null) {
                Text("Average tempo: ${averageBpm.toInt()} BPM")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun TopArtistRow(stat: TopArtistStat) {
    Column {
        Text(stat.artist)
        Text("${stat.playCount} plays")
    }
    Divider(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TopTrackRow(stat: TopTrackStat) {
    Column {
        Text(stat.title, fontWeight = FontWeight.Medium)
        Text(listOfNotNull(stat.artist, "${stat.playCount} plays").joinToString("  ·  "))
    }
    Divider(modifier = Modifier.padding(top = 8.dp))
}

private fun formatListenDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "$hours hr $minutes min listened" else "$minutes min listened"
}
