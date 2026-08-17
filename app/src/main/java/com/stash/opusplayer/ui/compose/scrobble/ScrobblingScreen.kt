package com.stash.opusplayer.ui.compose.scrobble

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScrobblingScreen(
    modifier: Modifier = Modifier,
    viewModel: ScrobblingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-retries a pending Last.fm/Libre.fm link when the screen resumes --
    // e.g. the user switched back from approving in the browser. A no-op if
    // nothing's pending; silently falls through to the manual button/error
    // path if the retry still 400s (not approved yet). Plain
    // DisposableEffect + LifecycleEventObserver rather than the newer
    // LifecycleEventEffect composable, to avoid depending on an API that
    // may not exist yet at this project's pinned lifecycle-runtime-compose
    // version.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume = rememberUpdatedState(viewModel::retryPendingLinksOnResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScrobblingContent(
        state = state,
        onStartLastfmLink = viewModel::startLastfmLink,
        onOpenLastfmAuthUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onFinishLastfmLink = { viewModel.finishLastfmLink() },
        onCancelLastfmLink = viewModel::cancelLastfmLink,
        onStartLibrefmLink = viewModel::startLibrefmLink,
        onOpenLibrefmAuthUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onFinishLibrefmLink = { viewModel.finishLibrefmLink() },
        onCancelLibrefmLink = viewModel::cancelLibrefmLink,
        onListenBrainzTokenChanged = viewModel::onListenBrainzTokenChanged,
        onLinkListenBrainz = viewModel::linkListenBrainz,
        onSetEnabled = viewModel::setEnabled,
        onRequestUnlinkConfirm = viewModel::requestUnlinkConfirm,
        onCancelUnlinkConfirm = viewModel::cancelUnlinkConfirm,
        onConfirmUnlinkAll = viewModel::confirmUnlinkAll,
        modifier = modifier
    )
}

@Composable
private fun ScrobblingContent(
    state: ScrobblingViewModel.UiState,
    onStartLastfmLink: () -> Unit,
    onOpenLastfmAuthUrl: (String) -> Unit,
    onFinishLastfmLink: () -> Unit,
    onCancelLastfmLink: () -> Unit,
    onStartLibrefmLink: () -> Unit,
    onOpenLibrefmAuthUrl: (String) -> Unit,
    onFinishLibrefmLink: () -> Unit,
    onCancelLibrefmLink: () -> Unit,
    onListenBrainzTokenChanged: (String) -> Unit,
    onLinkListenBrainz: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUnlinkConfirm: () -> Unit,
    onCancelUnlinkConfirm: () -> Unit,
    onConfirmUnlinkAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Scrobbling", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Scrobbles happen automatically from the shared bridge once linked -- nothing to trigger here beyond connecting an account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val links = state.links
        val anyLinked = links != null && (links.lastfmLinked || links.librefmLinked || links.listenBrainzLinked)

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Scrobble Plays", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (anyLinked) "On for every linked service below." else "Link a service below first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = links?.enabled == true,
                        onCheckedChange = onSetEnabled,
                        enabled = anyLinked && !state.isTogglingEnabled
                    )
                }
            }

            ScrobbleServiceCard(
                serviceName = "Last.fm",
                isLinked = links?.lastfmLinked == true,
                linkedUsername = links?.lastfmUsername,
                pendingAuthUrl = state.pendingLastfmAuthUrl,
                isBusy = state.isLinkingLastfm,
                onConnect = onStartLastfmLink,
                onOpenAuthUrl = onOpenLastfmAuthUrl,
                onFinishLink = onFinishLastfmLink,
                onCancelPending = onCancelLastfmLink
            )

            ScrobbleServiceCard(
                serviceName = "Libre.fm",
                isLinked = links?.librefmLinked == true,
                linkedUsername = links?.librefmUsername,
                pendingAuthUrl = state.pendingLibrefmAuthUrl,
                isBusy = state.isLinkingLibrefm,
                onConnect = onStartLibrefmLink,
                onOpenAuthUrl = onOpenLibrefmAuthUrl,
                onFinishLink = onFinishLibrefmLink,
                onCancelPending = onCancelLibrefmLink
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "ListenBrainz", style = MaterialTheme.typography.titleMedium)
                    if (links?.listenBrainzLinked == true) {
                        Text(text = "Linked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            text = "Paste your user token from listenbrainz.org -> Profile -> Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = state.listenBrainzTokenInput,
                            onValueChange = onListenBrainzTokenChanged,
                            label = { Text("User Token") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = onLinkListenBrainz,
                            enabled = !state.isLinkingListenBrainz && state.listenBrainzTokenInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isLinkingListenBrainz) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Link ListenBrainz")
                            }
                        }
                    }
                }
            }

            if (anyLinked) {
                Divider()
                Button(
                    onClick = onRequestUnlinkConfirm,
                    enabled = !state.isUnlinking,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlink All Scrobbling Accounts")
                }
                Text(
                    text = "The bridge only supports unlinking everything at once -- there's no way to disconnect just one service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.error?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (state.showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = onCancelUnlinkConfirm,
            title = { Text("Unlink all scrobbling accounts?") },
            text = { Text("Disconnects Last.fm, Libre.fm, and ListenBrainz all at once. You can relink any of them later.") },
            confirmButton = { TextButton(onClick = onConfirmUnlinkAll) { Text("Unlink All", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onCancelUnlinkConfirm) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScrobbleServiceCard(
    serviceName: String,
    isLinked: Boolean,
    linkedUsername: String?,
    pendingAuthUrl: String?,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onOpenAuthUrl: (String) -> Unit,
    onFinishLink: () -> Unit,
    onCancelPending: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = serviceName, style = MaterialTheme.typography.titleMedium)
            when {
                isLinked -> Text(
                    text = linkedUsername ?: "Linked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                pendingAuthUrl != null -> {
                    Text(
                        text = "Open $serviceName to approve, then come back and finish linking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onOpenAuthUrl(pendingAuthUrl) }) { Text("Open $serviceName") }
                        TextButton(onClick = onCancelPending, enabled = !isBusy) { Text("Cancel") }
                    }
                    Button(onClick = onFinishLink, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("I've Authorized -- Finish Linking")
                        }
                    }
                }
                else -> Button(onClick = onConnect, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Connect $serviceName")
                    }
                }
            }
        }
    }
}
