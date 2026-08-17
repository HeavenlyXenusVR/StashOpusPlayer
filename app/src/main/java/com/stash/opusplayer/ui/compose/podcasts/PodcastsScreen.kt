package com.stash.opusplayer.ui.compose.podcasts

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.stash.opusplayer.bridge.api.PodcastEpisode
import com.stash.opusplayer.bridge.api.PodcastSearchResult
import com.stash.opusplayer.bridge.api.PodcastSubscription

@Composable
fun PodcastsScreen(
    modifier: Modifier = Modifier,
    viewModel: PodcastsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.selectedFeedUrl != null) {
        EpisodesContent(state = state, viewModel = viewModel, modifier = modifier)
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == PodcastsTab.SUBSCRIPTIONS,
                    onClick = { viewModel.onTabSelected(PodcastsTab.SUBSCRIPTIONS) },
                    text = { Text("Subscriptions") }
                )
                Tab(
                    selected = state.selectedTab == PodcastsTab.DISCOVER,
                    onClick = { viewModel.onTabSelected(PodcastsTab.DISCOVER) },
                    text = { Text("Discover") }
                )
            }
            when (state.selectedTab) {
                PodcastsTab.SUBSCRIPTIONS -> SubscriptionsContent(state = state, viewModel = viewModel)
                PodcastsTab.DISCOVER -> DiscoverContent(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SubscriptionsContent(
    state: PodcastsViewModel.UiState,
    viewModel: PodcastsViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Podcasts", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Add a Podcast", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.feedUrlInput,
                    onValueChange = viewModel::onFeedUrlChanged,
                    label = { Text("RSS Feed URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = viewModel::subscribe,
                    enabled = !state.isSubscribing && state.feedUrlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSubscribing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Subscribe")
                    }
                }
                state.subscribeError?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        when {
            state.isLoadingSubscriptions -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.subscriptionsError != null -> Text(text = state.subscriptionsError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.subscriptions.isEmpty() -> Text(
                text = "No podcasts yet -- paste an RSS feed URL above to subscribe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.subscriptions.forEach { sub ->
                SubscriptionRow(
                    subscription = sub,
                    onOpen = { viewModel.openFeed(sub.feedUrl) },
                    onToggleMute = { viewModel.toggleMute(sub) },
                    onUnsubscribe = { viewModel.unsubscribe(sub.id) }
                )
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: PodcastSubscription,
    onOpen: () -> Unit,
    onToggleMute: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subscription.title?.takeIf { it.isNotBlank() } ?: subscription.feedUrl,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggleMute) { Text(if (subscription.notificationsMuted) "Unmute" else "Mute") }
            TextButton(onClick = onUnsubscribe) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun DiscoverContent(state: PodcastsViewModel.UiState, viewModel: PodcastsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                label = { Text("Search podcasts") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = viewModel::searchPodcasts, enabled = !state.isSearching && state.searchQuery.isNotBlank()) {
                if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Search")
                }
            }
        }

        if (state.hasSearched) {
            Text(text = "Results", style = MaterialTheme.typography.titleMedium)
            when {
                state.searchError != null -> Text(text = state.searchError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                state.searchResults.isEmpty() -> Text(
                    text = "No podcasts found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> state.searchResults.forEach { result ->
                    PodcastResultRow(
                        result = result,
                        isSubscribing = state.subscribingFeedUrls.contains(result.feedUrl),
                        onSubscribe = { viewModel.subscribeToResult(result) }
                    )
                }
            }
            Divider()
        }

        Text(text = "Trending", style = MaterialTheme.typography.titleMedium)
        when {
            state.isLoadingTrending -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.trendingError != null -> Text(text = state.trendingError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.trending.isEmpty() -> Text(
                text = "Nothing trending right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.trending.forEach { result ->
                PodcastResultRow(
                    result = result,
                    isSubscribing = state.subscribingFeedUrls.contains(result.feedUrl),
                    onSubscribe = { viewModel.subscribeToResult(result) }
                )
            }
        }
    }
}

@Composable
private fun PodcastResultRow(result: PodcastSearchResult, isSubscribing: Boolean, onSubscribe: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title?.takeIf { it.isNotBlank() } ?: result.feedUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                result.artist?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isSubscribing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onSubscribe) { Text("Subscribe") }
            }
        }
    }
}

@Composable
private fun EpisodesContent(
    state: PodcastsViewModel.UiState,
    viewModel: PodcastsViewModel,
    modifier: Modifier = Modifier
) {
    val subscription = state.subscriptions.firstOrNull { it.feedUrl == state.selectedFeedUrl }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::closeFeed) { Text("< Back") }
            Text(
                text = subscription?.title?.takeIf { it.isNotBlank() } ?: "Episodes",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        when {
            state.isLoadingEpisodes -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.episodesError != null -> Text(text = state.episodesError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.episodes.isEmpty() -> Text(
                text = "No episodes found in this feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.episodes.forEach { episode ->
                EpisodeRow(
                    episode = episode,
                    isPlaying = state.playingGuid == episode.guid,
                    progress = state.progressByGuid[episode.guid],
                    onClick = { viewModel.playEpisode(episode) },
                    onChaptersClick = { viewModel.openChapters(episode) }
                )
                Divider()
            }
        }
    }

    if (state.chaptersEpisodeGuid != null) {
        val chapterEpisode = state.episodes.firstOrNull { it.guid == state.chaptersEpisodeGuid }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::closeChapters,
            title = { Text("Chapters") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        state.isLoadingChapters -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        state.chaptersError != null -> Text(text = state.chaptersError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        state.chapters.isEmpty() -> Text(
                            text = "No chapters found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> state.chapters.forEach { chapter ->
                            val secs = chapter.startTimeSeconds.toInt()
                            val timeText = if (secs >= 3600) {
                                "%d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
                            } else {
                                "%d:%02d".format(secs / 60, secs % 60)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = chapterEpisode != null) {
                                        chapterEpisode?.let { viewModel.playFromChapter(it, chapter) }
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = chapter.title, style = MaterialTheme.typography.bodyMedium)
                                Text(text = timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeChapters) { Text("Close") }
            }
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    progress: com.stash.opusplayer.bridge.api.PodcastEpisodeProgress?,
    onClick: () -> Unit,
    onChaptersClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title?.takeIf { it.isNotBlank() } ?: "Untitled Episode",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
            )
            val durationText = episode.durationSeconds?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
            val subtitle = listOfNotNull(episode.publishedAt?.take(10), durationText).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                progress?.completed == true -> Text(
                    text = "Played",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                progress != null && progress.positionSeconds > 5 -> {
                    val pos = progress.positionSeconds.toInt()
                    Text(
                        text = "Resume at %d:%02d".format(pos / 60, pos % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (episode.chaptersUrl != null) {
            TextButton(onClick = onChaptersClick) { Text("Chapters") }
        }
        if (isPlaying) {
            Text(text = "▶", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
