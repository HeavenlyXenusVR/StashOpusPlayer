package com.stash.opusplayer.ui.compose.ytdlpcookies

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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.YtdlpCookiesValidation

@Composable
fun YtdlpCookiesScreen(
    viewModel: YtdlpCookiesViewModel = hiltViewModel(),
    onPickFile: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Upload your own YouTube session cookies to let this account's Browse & Stream / Discovery requests resolve age-restricted or login-required videos. Export a cookies.txt file from your browser (e.g. the \"Get cookies.txt LOCALLY\" extension) while signed into YouTube.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "The file is stored on the server for your account only and is never shown back to you or anyone else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    state.isLoadingStatus -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.configured -> {
                        Text("Configured", color = MaterialTheme.colorScheme.primary)
                        state.updatedAt?.let {
                            Text("Last updated: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> Text("No cookies uploaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Button(onClick = onPickFile, enabled = !state.isUploading, modifier = Modifier.fillMaxWidth()) {
            if (state.isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (state.configured) "Replace Cookies File" else "Upload Cookies File")
            }
        }
        state.uploadError?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (state.configured) {
            Divider()

            Button(onClick = { viewModel.validate() }, enabled = !state.isValidating, modifier = Modifier.fillMaxWidth()) {
                if (state.isValidating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Validate Cookies")
                }
            }
            state.validationError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.validation?.let { ValidationResultCard(it) }

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
                    Text("Remove Cookies")
                }
            }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteConfirm() },
            title = { Text("Remove uploaded cookies?") },
            text = { Text("Age-restricted/login-required YouTube content will stop resolving until you upload a new cookies.txt file.") },
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

@Composable
private fun ValidationResultCard(validation: YtdlpCookiesValidation) {
    val (label, color) = when (validation.status) {
        "valid" -> "Valid" to MaterialTheme.colorScheme.primary
        "valid_no_age_restriction" -> "Valid (no age-restriction bypass)" to MaterialTheme.colorScheme.primary
        "expired" -> "Expired" to MaterialTheme.colorScheme.error
        "incomplete" -> "Incomplete" to MaterialTheme.colorScheme.error
        "missing" -> "Missing" to MaterialTheme.colorScheme.error
        else -> "Invalid" to MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = label, fontWeight = FontWeight.Bold, color = color)
            Text(text = validation.detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (validation.ageRestrictionReady) "✓ Age-restriction ready" else "✗ Not age-restriction ready",
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = "${validation.cookieCount} cookies found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (validation.missing.isNotEmpty()) {
                Text(text = "Missing: ${validation.missing.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
