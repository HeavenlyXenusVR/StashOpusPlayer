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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
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
        ScrollableTabRow(selectedTabIndex = state.selectedTab.ordinal) {
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
            Tab(
                selected = state.selectedTab == DiscoveryTab.TRENDING,
                onClick = { viewModel.onTabSelected(DiscoveryTab.TRENDING) },
                text = { Text("Trending") }
            )
            Tab(
                selected = state.selectedTab == DiscoveryTab.COMMUNITY,
                onClick = { viewModel.onTabSelected(DiscoveryTab.COMMUNITY) },
                text = { Text("Community") }
            )
            Tab(
                selected = state.selectedTab == DiscoveryTab.SIMILAR_LISTENERS,
                onClick = { viewModel.onTabSelected(DiscoveryTab.SIMILAR_LISTENERS) },
                text = { Text("Similar Listeners") }
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
            DiscoveryTab.TRENDING -> TrendingContent(
                isLoading = state.isLoadingTrending,
                tracks = state.trending,
                error = state.trendingError
            )
            DiscoveryTab.COMMUNITY -> CommunityActivityContent(
                isLoading = state.isLoadingCommunityActivity,
                activity = state.communityActivity,
                error = state.communityActivityError
            )
            DiscoveryTab.SIMILAR_LISTENERS -> SimilarListenersContent(
                isLoading = state.isLoadingSimilarListeners,
                tracks = state.similarListeners,
                similarListenerCount = state.similarListenerCount,
                reason = state.similarListenersReason,
                error = state.similarListenersError,
                resolvingKey = state.resolvingTitleArtistKey,
                onTrackClick = { title, artist -> viewModel.playTitleArtist(title, artist) }
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

@Composable
private fun TrendingContent(
    isLoading: Boolean,
    tracks: List<com.stash.opusplayer.bridge.api.TrendingTrack>,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "What everyone who's opted in is playing most, last 7 days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            tracks.isEmpty() -> EmptyState(
                title = "Nothing trending yet",
                message = "Check back once more listeners opt in to Share Listening Activity (Settings -> Account & Server)."
            )
            else -> tracks.forEachIndexed { index, track ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(text = track.title, style = MaterialTheme.typography.bodyMedium)
                            track.artist?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "${track.playCount} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${track.listenerCount} listener${if (track.listenerCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun CommunityActivityContent(
    isLoading: Boolean,
    activity: List<com.stash.opusplayer.bridge.api.GlobalActivityEntry>,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Recent plays from everyone who's opted in -- not just friends.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            activity.isEmpty() -> EmptyState(
                title = "Nothing here yet",
                message = "Check back once more listeners opt in to Share Listening Activity."
            )
            else -> activity.forEach { entry ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                Divider()
            }
        }
    }
}

@Composable
private fun SimilarListenersContent(
    isLoading: Boolean,
    tracks: List<com.stash.opusplayer.bridge.api.TrendingTrack>,
    similarListenerCount: Int,
    reason: String?,
    error: String?,
    resolvingKey: String?,
    onTrackClick: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (tracks.isNotEmpty()) {
                "Based on $similarListenerCount listener${if (similarListenerCount == 1) "" else "s"} with taste like yours"
            } else {
                "Recommendations from other listeners whose top artists overlap with yours."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            tracks.isEmpty() -> EmptyState(
                title = "Nothing here yet",
                message = when (reason) {
                    "not_enough_history" -> "Play a few songs first so we can find listeners with similar taste."
                    "no_similar_listeners" -> "No opted-in listeners share your top artists yet -- check back later."
                    else -> "Nothing to show right now."
                }
            )
            else -> tracks.forEach { track ->
                val key = "${track.title}|${track.artist.orEmpty()}"
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = resolvingKey == null) {
                        onTrackClick(track.title, track.artist)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            track.artist?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (resolvingKey == key) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
