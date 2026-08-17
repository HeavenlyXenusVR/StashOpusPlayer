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
        SubscriptionsContent(state = state, viewModel = viewModel, modifier = modifier)
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
                    onClick = { viewModel.playEpisode(episode) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: PodcastEpisode, isPlaying: Boolean, onClick: () -> Unit) {
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
        }
        if (isPlaying) {
            Text(text = "▶", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
