package com.stash.opusplayer.ui.compose.discordrpc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiscordRpcScreen(viewModel: DiscordRpcViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Shows what you're playing as your Discord status. Actual Rich Presence runs through a small desktop program you install separately -- this screen just sets it up.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Enable Rich Presence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!state.isDiscordVerified) {
                        Text(
                            text = "Verify your Discord account first (Settings -> Discord Verification).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    enabled = state.isDiscordVerified && !state.isTogglingEnabled
                )
            }

            state.generatedToken?.let { token ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "One-time setup token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(text = token, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        Text(
                            text = "Run this on the computer where Discord is open: download discord-rpc from the project's GitHub, then ./install.sh <token> (or install-macos.sh / install-windows.ps1).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(token)) }) {
                            Text("Copy Token")
                        }
                    }
                }
            }

            if (state.configured) {
                Button(onClick = { viewModel.generateNewToken() }, enabled = !state.isGeneratingToken, modifier = Modifier.fillMaxWidth()) {
                    if (state.isGeneratingToken) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Generate New Token")
                    }
                }
            }

            Divider()

            Text(text = "Advanced: Custom Discord App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.clientIdInput,
                onValueChange = viewModel::onClientIdChanged,
                label = { Text("Discord Application Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.largeImageInput,
                onValueChange = viewModel::onLargeImageChanged,
                label = { Text("Large image art asset name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.smallImageInput,
                onValueChange = viewModel::onSmallImageChanged,
                label = { Text("Small status icon asset name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show \"Listen on...\" Button")
                Switch(checked = state.showButtonsInput, onCheckedChange = viewModel::onShowButtonsChanged)
            }
            state.customAppError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.saveCustomApp() },
                enabled = !state.isSavingCustomApp && state.clientIdInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSavingCustomApp) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Use Custom Discord App")
                }
            }
            if (state.isCustom) {
                TextButton(onClick = { viewModel.switchBackToSharedApp() }, enabled = !state.isSavingCustomApp) {
                    Text("Switch Back to Shared App")
                }
            }

            if (state.configured) {
                Divider()
                Button(
                    onClick = { viewModel.requestDeleteConfirm() },
                    enabled = !state.isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Remove Registration")
                    }
                }
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteConfirm() },
            title = { Text("Remove Rich Presence registration?") },
            text = { Text("Your desktop daemon's setup token will stop working and Rich Presence will be disabled.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteConfirm() }) { Text("Cancel") }
            }
        )
    }
}
