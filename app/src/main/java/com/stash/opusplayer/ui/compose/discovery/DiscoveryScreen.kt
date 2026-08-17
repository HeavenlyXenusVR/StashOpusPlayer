package com.stash.opusplayer.ui.compose.discovery

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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.BridgeTrack

@Composable
fun DiscoveryScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == DiscoveryTab.DISCOVER_MIX,
                onClick = { viewModel.onTabSelected(DiscoveryTab.DISCOVER_MIX) },
                text = { Text("Discover Mix") }
            )
            Tab(
                selected = state.selectedTab == DiscoveryTab.ON_THIS_DAY,
                onClick = { viewModel.onTabSelected(DiscoveryTab.ON_THIS_DAY) },
                text = { Text("On This Day") }
            )
        }

        when (state.selectedTab) {
            DiscoveryTab.DISCOVER_MIX -> DiscoverMixContent(
                isLoading = state.isLoadingDiscoverMix,
                tracks = state.discoverMix,
                error = state.discoverMixError,
                resolvingTrackId = state.resolvingTrackId,
                onTrackClick = viewModel::playTrack
            )
            DiscoveryTab.ON_THIS_DAY -> OnThisDayContent(
                isLoading = state.isLoadingOnThisDay,
                groups = state.onThisDay,
                error = state.onThisDayError,
                resolvingTrackId = state.resolvingTrackId,
                onTrackClick = viewModel::playTrack
            )
        }

        state.playbackError?.let {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
        }
    }
}

@Composable
private fun DiscoverMixContent(
    isLoading: Boolean,
    tracks: List<BridgeTrack>,
    error: String?,
    resolvingTrackId: String?,
    onTrackClick: (BridgeTrack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "New tracks based on the artists you play most.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            tracks.isEmpty() -> EmptyState(
                title = "Nothing to discover yet",
                message = "Play a few songs and your Discover Mix will fill up with new tracks based on your favorite artists."
            )
            else -> tracks.forEach { track ->
                TrackRow(
                    track = track,
                    isResolving = resolvingTrackId == track.id,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
private fun OnThisDayContent(
    isLoading: Boolean,
    groups: List<com.stash.opusplayer.bridge.api.OnThisDayGroup>,
    error: String?,
    resolvingTrackId: String?,
    onTrackClick: (BridgeTrack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            groups.isEmpty() -> EmptyState(
                title = "Nothing from past years yet",
                message = "Once you've been listening for a year or more, tracks you played on this date will show up here."
            )
            else -> groups.forEach { group ->
                Text(
                    text = "${group.yearsAgo} YEAR${if (group.yearsAgo == 1) "" else "S"} AGO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                group.tracks.forEach { track ->
                    TrackRow(
                        track = track,
                        isResolving = resolvingTrackId == track.id,
                        onClick = { onTrackClick(track) }
                    )
                }
                Divider()
            }
        }
    }
}

@Composable
private fun TrackRow(track: BridgeTrack, isResolving: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !isResolving, onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isResolving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
