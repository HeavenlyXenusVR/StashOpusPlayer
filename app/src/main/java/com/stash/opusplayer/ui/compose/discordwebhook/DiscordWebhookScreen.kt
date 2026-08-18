package com.stash.opusplayer.ui.compose.discordwebhook

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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

@Composable
fun DiscordWebhookScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscordWebhookViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Discord Webhook", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Posts a \"Now Playing\" message to a Discord channel whenever you start a track. Create an incoming webhook in your server's Channel Settings -> Integrations -> Webhooks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            val status = state.status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (status?.configured == true) {
                        Text(text = "Webhook", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = status.webhookUrl ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Enabled", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = state.enabledInput, onCheckedChange = viewModel::setEnabled)
                        }
                    } else {
                        OutlinedTextField(
                            value = state.webhookUrlInput,
                            onValueChange = viewModel::onWebhookUrlChanged,
                            label = { Text("https://discord.com/api/webhooks/...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.isSaving && state.webhookUrlInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(if (state.didSave) "Saved" else "Save")
                            }
                        }
                    }
                }
            }

            if (status?.configured == true) {
                Button(
                    onClick = viewModel::requestRemoveConfirm,
                    enabled = !state.isRemoving,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Webhook")
                }
            }
        }

        state.error?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (state.showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRemoveConfirm,
            title = { Text("Remove Discord webhook?") },
            text = { Text("Now Playing updates will stop posting to Discord. You can reconnect a webhook later.") },
            confirmButton = { TextButton(onClick = viewModel::confirmRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRemoveConfirm) { Text("Cancel") } }
        )
    }
}
