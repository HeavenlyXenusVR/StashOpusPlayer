package com.stash.opusplayer.ui.compose.acoustidkey

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
fun AcoustIdApiKeyScreen(viewModel: AcoustIdApiKeyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enables \"Identify Track\" (AcoustID fingerprint lookups) with your own free key from acoustid.org. There's no shared fallback key -- each user brings their own.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "AcoustID API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    state.isLoadingStatus -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.configured -> Text(state.maskedKey ?: "Configured", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("Not set", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!state.configured) {
            OutlinedTextField(
                value = state.keyInput,
                onValueChange = viewModel::onKeyInputChanged,
                label = { Text("Paste AcoustID API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.saveError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (state.keyInput.isNotBlank()) {
                Button(onClick = { viewModel.saveKey() }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Key")
                    }
                }
            }
        } else {
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
