package com.stash.opusplayer.ui.compose.discordverify

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiscordVerificationScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscordVerificationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-checks verification status whenever the screen resumes -- e.g.
    // returning from the Chrome Custom Tab after approving/cancelling on
    // Discord's consent screen. MainActivity's own intent-filter for the
    // fixed `lumisound://discord-verify` redirect (see its manifest entry
    // and onNewIntent) is what actually brings the app back to the
    // foreground; this effect is what makes THIS screen reflect the result
    // once it does, without any direct signaling between the two.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume = rememberUpdatedState(viewModel::load)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.pendingAuthorizeUrl) {
        val url = state.pendingAuthorizeUrl ?: return@LaunchedEffect
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        viewModel.consumePendingAuthorizeUrl()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Discord Verification", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Link your Discord account to prove it's really you -- separate from the Discord webhook, which just posts messages and doesn't verify identity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            val status = state.status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status?.verified == true) {
                        Text(text = "Verified", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        status.discordUsername?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(text = "Not linked", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = viewModel::startVerify,
                            enabled = !state.isStartingVerify,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isStartingVerify) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Verify with Discord")
                            }
                        }
                    }
                }
            }

            if (status?.verified == true) {
                Button(
                    onClick = viewModel::requestUnlinkConfirm,
                    enabled = !state.isUnlinking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Verification")
                }
            }
        }

        state.error?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (state.showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelUnlinkConfirm,
            title = { Text("Remove Discord verification?") },
            text = { Text("You can link Discord again later.") },
            confirmButton = { TextButton(onClick = viewModel::confirmUnlink) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelUnlinkConfirm) { Text("Cancel") } }
        )
    }
}
