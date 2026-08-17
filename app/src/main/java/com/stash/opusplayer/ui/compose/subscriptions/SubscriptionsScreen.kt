package com.stash.opusplayer.ui.compose.subscriptions

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
import androidx.compose.material3.Snackbar
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
import com.stash.opusplayer.bridge.api.ArtistSubscription
import com.stash.opusplayer.bridge.api.BridgeTrack
import com.stash.opusplayer.bridge.api.SubscriptionFeedItem

@Composable
fun SubscriptionsScreen(
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == SubscriptionsTab.CHANNELS,
                onClick = { viewModel.onTabSelected(SubscriptionsTab.CHANNELS) },
                text = { Text("Channels") }
            )
            Tab(
                selected = state.selectedTab == SubscriptionsTab.FEED,
                onClick = { viewModel.onTabSelected(SubscriptionsTab.FEED) },
                text = { Text(if (state.unreadCount > 0) "Feed (${state.unreadCount.coerceAtMost(99)})" else "Feed") }
            )
        }

        when (state.selectedTab) {
            SubscriptionsTab.CHANNELS -> ChannelsContent(state, viewModel)
            SubscriptionsTab.FEED -> FeedContent(state, viewModel)
        }

        state.playbackError?.let {
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
        }
    }
}

@Composable
private fun ChannelsContent(state: SubscriptionsViewModel.UiState, viewModel: SubscriptionsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Subscribe to a Channel", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.channelUrlInput,
                    onValueChange = viewModel::onChannelUrlChanged,
                    label = { Text("Channel URL, @handle, or name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.channelNameInput,
                    onValueChange = viewModel::onChannelNameChanged,
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = viewModel::subscribe,
                    enabled = !state.isSubscribing && state.channelUrlInput.isNotBlank(),
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

        if (state.subscriptions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Your Channels", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::checkAll, enabled = !state.isCheckingAll) { Text("Check All") }
            }
        }

        when {
            state.isLoadingSubscriptions -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.subscriptionsError != null -> Text(text = state.subscriptionsError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.subscriptions.isEmpty() -> Text(
                text = "No subscriptions yet -- follow a YouTube channel above to get notified of new uploads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.subscriptions.forEach { sub ->
                SubscriptionRow(
                    subscription = sub,
                    isChecking = state.checkingSubscriptionIds.contains(sub.id),
                    newTracks = state.newTracksBySubscriptionId[sub.id].orEmpty(),
                    resolvingKey = state.resolvingKey,
                    onCheck = { viewModel.checkSubscription(sub.id) },
                    onToggleMute = { viewModel.toggleMute(sub) },
                    onUnsubscribe = { viewModel.unsubscribe(sub.id) },
                    onTrackClick = viewModel::playNewTrack
                )
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: ArtistSubscription,
    isChecking: Boolean,
    newTracks: List<BridgeTrack>,
    resolvingKey: String?,
    onCheck: () -> Unit,
    onToggleMute: () -> Unit,
    onUnsubscribe: () -> Unit,
    onTrackClick: (BridgeTrack) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.channelName?.takeIf { it.isNotBlank() } ?: subscription.channelUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (subscription.isStale) {
                        Text(
                            text = "Inactive -- no new uploads in a while",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        subscription.uploadFrequencyLabel?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onCheck) { Text("Check") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onToggleMute) {
                    Text(if (subscription.notificationsMuted) "Unmute" else "Mute")
                }
                TextButton(onClick = onUnsubscribe) {
                    Text("Unsubscribe", color = MaterialTheme.colorScheme.error)
                }
            }

            if (newTracks.isNotEmpty()) {
                Divider()
                Text(text = "New", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                newTracks.forEach { track ->
                    val key = "new:${track.id}"
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = resolvingKey == null) { onTrackClick(track) }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = track.title, style = MaterialTheme.typography.bodySmall)
                            Text(text = track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (resolvingKey == key) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedContent(state: SubscriptionsViewModel.UiState, viewModel: SubscriptionsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.feed.any { !it.isRead }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::markAllFeedRead) { Text("Mark All Read") }
            }
        }

        when {
            state.isLoadingFeed -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            state.feedError != null -> Text(text = state.feedError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            state.feed.isEmpty() -> Text(
                text = "Nothing here yet -- new uploads from your subscribed channels will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.feed.forEach { item ->
                FeedRow(
                    item = item,
                    isResolving = state.resolvingKey == "feed:${item.id}",
                    resolvingAny = state.resolvingKey != null,
                    onClick = { viewModel.playFeedItem(item) },
                    onDelete = { viewModel.deleteFeedItem(item.id) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun FeedRow(
    item: SubscriptionFeedItem,
    isResolving: Boolean,
    resolvingAny: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val track = item.track
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !resolvingAny && track != null, onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (!item.isRead) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(8.dp)
                ) {}
            }
            Column {
                Text(text = track?.title ?: "Unknown track", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = item.channelName?.takeIf { it.isNotBlank() } ?: track?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onDelete) { Text("Dismiss") }
        }
    }
}
