package com.stash.opusplayer.ui.compose.youtubekey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun YoutubeApiKeyScreen(viewModel: YoutubeApiKeyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Supply your own YouTube Data API v3 key so full playlists (over about 205 tracks) resolve completely instead of being capped. Optional -- without one, a shared server key is used.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    state.isLoadingStatus -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.configured -> Text(state.maskedKey ?: "Configured", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("No key configured -- using the shared server key.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!state.configured || state.exposureWarning != null) {
            OutlinedTextField(
                value = state.keyInput,
                onValueChange = viewModel::onKeyInputChanged,
                label = { Text("AIza...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.saveError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.saveKey() },
                enabled = !state.isSaving && state.keyInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (state.justSaved) "Saved" else "Save")
                }
            }
        }

        if (state.configured) {
            Divider()

            Button(onClick = { viewModel.validateKey() }, enabled = !state.isValidating, modifier = Modifier.fillMaxWidth()) {
                if (state.isValidating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Validate API Key")
                }
            }
            state.validationResult?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = { viewModel.checkKeyExposure() }, enabled = !state.isCheckingExposure, modifier = Modifier.fillMaxWidth()) {
                if (state.isCheckingExposure) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Check for Key Exposure")
                }
            }
            state.exposureWarning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("No signs")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }

            Divider()

            Button(
                onClick = { viewModel.deleteKey() },
                enabled = !state.isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Remove Key")
                }
            }
        }
    }
}
