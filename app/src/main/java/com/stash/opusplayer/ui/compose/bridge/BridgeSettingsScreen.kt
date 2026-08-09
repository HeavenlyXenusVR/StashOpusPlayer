package com.stash.opusplayer.ui.compose.bridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Entry point for the Bridge Settings screen. Wires the Hilt-provided
 * [BridgeSettingsViewModel] to the stateless [BridgeSettingsContent] below —
 * kept as a thin, single-purpose stateful wrapper so the actual layout stays
 * easy to reason about (and, if this project adds Compose previews later,
 * previewable without a Hilt graph).
 */
@Composable
fun BridgeSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: BridgeSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BridgeSettingsContent(
        state = uiState,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveConfig = viewModel::saveServerConfig,
        onAuthModeChanged = viewModel::onAuthModeChanged,
        onLoginUsernameChanged = viewModel::onLoginUsernameChanged,
        onLoginPasswordChanged = viewModel::onLoginPasswordChanged,
        onRegisterUsernameChanged = viewModel::onRegisterUsernameChanged,
        onRegisterPasswordChanged = viewModel::onRegisterPasswordChanged,
        onRegisterEmailChanged = viewModel::onRegisterEmailChanged,
        onLogin = viewModel::login,
        onRegister = viewModel::register,
        onLogout = viewModel::logout,
        modifier = modifier
    )
}

@Composable
private fun BridgeSettingsContent(
    state: BridgeSettingsUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSaveConfig: () -> Unit,
    onAuthModeChanged: (AuthMode) -> Unit,
    onLoginUsernameChanged: (String) -> Unit,
    onLoginPasswordChanged: (String) -> Unit,
    onRegisterUsernameChanged: (String) -> Unit,
    onRegisterPasswordChanged: (String) -> Unit,
    onRegisterEmailChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = "Bridge Settings", style = MaterialTheme.typography.headlineSmall)

        ServerConfigSection(
            baseUrl = state.baseUrlInput,
            apiKey = state.apiKeyInput,
            isConfigured = state.isConfigured,
            isSaving = state.isSavingConfig,
            justSaved = state.configJustSaved,
            onBaseUrlChanged = onBaseUrlChanged,
            onApiKeyChanged = onApiKeyChanged,
            onSave = onSaveConfig
        )

        Divider()

        AccountSection(
            state = state,
            onAuthModeChanged = onAuthModeChanged,
            onLoginUsernameChanged = onLoginUsernameChanged,
            onLoginPasswordChanged = onLoginPasswordChanged,
            onRegisterUsernameChanged = onRegisterUsernameChanged,
            onRegisterPasswordChanged = onRegisterPasswordChanged,
            onRegisterEmailChanged = onRegisterEmailChanged,
            onLogin = onLogin,
            onRegister = onRegister,
            onLogout = onLogout
        )
    }
}

@Composable
private fun ServerConfigSection(
    baseUrl: String,
    apiKey: String,
    isConfigured: Boolean,
    isSaving: Boolean,
    justSaved: Boolean,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Server", style = MaterialTheme.typography.titleMedium)
                ConfiguredBadge(isConfigured = isConfigured)
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Bridge base URL") },
                placeholder = { Text("https://your-server.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("Operator API key (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (justSaved && !isSaving) {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Button(onClick = onSave, enabled = !isSaving) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfiguredBadge(isConfigured: Boolean) {
    val containerColor = if (isConfigured) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isConfigured) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = containerColor, contentColor = contentColor) {
        Text(
            text = if (isConfigured) "Configured" else "Not configured",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AccountSection(
    state: BridgeSettingsUiState,
    onAuthModeChanged: (AuthMode) -> Unit,
    onLoginUsernameChanged: (String) -> Unit,
    onLoginPasswordChanged: (String) -> Unit,
    onRegisterUsernameChanged: (String) -> Unit,
    onRegisterPasswordChanged: (String) -> Unit,
    onRegisterEmailChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onLogout: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Account", style = MaterialTheme.typography.titleMedium)

            if (state.isLoggedIn) {
                LoggedInContent(
                    username = state.username,
                    isLoggingOut = state.isLoggingOut,
                    onLogout = onLogout
                )
            } else {
                LoggedOutContent(
                    state = state,
                    onAuthModeChanged = onAuthModeChanged,
                    onLoginUsernameChanged = onLoginUsernameChanged,
                    onLoginPasswordChanged = onLoginPasswordChanged,
                    onRegisterUsernameChanged = onRegisterUsernameChanged,
                    onRegisterPasswordChanged = onRegisterPasswordChanged,
                    onRegisterEmailChanged = onRegisterEmailChanged,
                    onLogin = onLogin,
                    onRegister = onRegister
                )
            }

            state.authError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.authInfo?.let { info ->
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun LoggedInContent(
    username: String?,
    isLoggingOut: Boolean,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Signed in as",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = username ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
        }
        Button(onClick = onLogout, enabled = !isLoggingOut) {
            if (isLoggingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Log Out")
            }
        }
    }
}

@Composable
private fun LoggedOutContent(
    state: BridgeSettingsUiState,
    onAuthModeChanged: (AuthMode) -> Unit,
    onLoginUsernameChanged: (String) -> Unit,
    onLoginPasswordChanged: (String) -> Unit,
    onRegisterUsernameChanged: (String) -> Unit,
    onRegisterPasswordChanged: (String) -> Unit,
    onRegisterEmailChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onAuthModeChanged(AuthMode.LOGIN) }) {
                Text(
                    text = "Log In",
                    fontWeight = if (state.authMode == AuthMode.LOGIN) FontWeight.Bold else FontWeight.Normal
                )
            }
            TextButton(onClick = { onAuthModeChanged(AuthMode.REGISTER) }) {
                Text(
                    text = "Register",
                    fontWeight = if (state.authMode == AuthMode.REGISTER) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        when (state.authMode) {
            AuthMode.LOGIN -> LoginForm(
                username = state.loginUsername,
                password = state.loginPassword,
                isLoading = state.isAuthLoading,
                onUsernameChanged = onLoginUsernameChanged,
                onPasswordChanged = onLoginPasswordChanged,
                onSubmit = onLogin
            )
            AuthMode.REGISTER -> RegisterForm(
                username = state.registerUsername,
                password = state.registerPassword,
                email = state.registerEmail,
                isLoading = state.isAuthLoading,
                onUsernameChanged = onRegisterUsernameChanged,
                onPasswordChanged = onRegisterPasswordChanged,
                onEmailChanged = onRegisterEmailChanged,
                onSubmit = onRegister
            )
        }
    }
}

@Composable
private fun LoginForm(
    username: String,
    password: String,
    isLoading: Boolean,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChanged,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSubmit,
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Log In")
            }
        }
    }
}

@Composable
private fun RegisterForm(
    username: String,
    password: String,
    email: String,
    isLoading: Boolean,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChanged,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChanged,
            label = { Text("Email (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSubmit,
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Register")
            }
        }
    }
}
