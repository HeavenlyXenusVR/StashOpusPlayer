package com.stash.opusplayer.ui.compose.bridge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.BridgeSession

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
    viewModel: BridgeSettingsViewModel = hiltViewModel(),
    onViewMyProfile: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BridgeSettingsContent(
        state = uiState,
        onViewMyProfile = onViewMyProfile,
        onShareListeningActivityChanged = viewModel::setShareListeningActivity,
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
        onTwoFactorCodeChanged = viewModel::onTwoFactorCodeChanged,
        onCompleteTwoFactorLogin = viewModel::completeTwoFactorLogin,
        onCancelTwoFactorLogin = viewModel::cancelTwoFactorLogin,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onSaveDisplayName = viewModel::saveDisplayName,
        onUploadAvatar = viewModel::uploadAvatarFromUri,
        onLoadSessions = viewModel::loadSessions,
        onRevokeSession = viewModel::revokeSession,
        onCurrentPasswordChanged = viewModel::onCurrentPasswordChanged,
        onNewPasswordChanged = viewModel::onNewPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onChangePassword = viewModel::changePassword,
        onDeleteAccountPasswordChanged = viewModel::onDeleteAccountPasswordChanged,
        onRequestDeleteAccountConfirm = viewModel::requestDeleteAccountConfirm,
        onCancelDeleteAccountConfirm = viewModel::cancelDeleteAccountConfirm,
        onConfirmDeleteAccount = viewModel::confirmDeleteAccount,
        modifier = modifier
    )
}

@Composable
private fun BridgeSettingsContent(
    state: BridgeSettingsUiState,
    onViewMyProfile: (String) -> Unit,
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
    onTwoFactorCodeChanged: (String) -> Unit,
    onCompleteTwoFactorLogin: () -> Unit,
    onCancelTwoFactorLogin: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onUploadAvatar: (android.net.Uri) -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String, Boolean) -> Unit,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccountPasswordChanged: (String) -> Unit,
    onRequestDeleteAccountConfirm: () -> Unit,
    onCancelDeleteAccountConfirm: () -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onShareListeningActivityChanged: (Boolean) -> Unit,
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
            onLogout = onLogout,
            onTwoFactorCodeChanged = onTwoFactorCodeChanged,
            onCompleteTwoFactorLogin = onCompleteTwoFactorLogin,
            onCancelTwoFactorLogin = onCancelTwoFactorLogin,
            onDisplayNameChanged = onDisplayNameChanged,
            onSaveDisplayName = onSaveDisplayName
        )

        if (state.isLoggedIn) {
            Divider()
            AvatarSection(
                bitmap = state.avatarBitmap,
                isUploading = state.isUploadingAvatar,
                error = state.avatarError,
                onUpload = onUploadAvatar
            )

            state.userId?.let { userId ->
                TextButton(onClick = { onViewMyProfile(userId) }) {
                    Text("View My Public Profile")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Share Listening Activity", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Lets other signed-in users see your recent plays (title/artist only) on the Discover screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = state.shareListeningActivity,
                    onCheckedChange = onShareListeningActivityChanged,
                    enabled = !state.isUpdatingPrivacy
                )
            }

            Divider()
            SessionsSection(
                sessions = state.sessions,
                isLoading = state.isLoadingSessions,
                onLoad = onLoadSessions,
                onRevoke = onRevokeSession
            )

            Divider()
            ChangePasswordSection(
                currentPassword = state.currentPasswordInput,
                newPassword = state.newPasswordInput,
                confirmPassword = state.confirmPasswordInput,
                isChanging = state.isChangingPassword,
                error = state.passwordChangeError,
                succeeded = state.passwordChangeSucceeded,
                onCurrentPasswordChanged = onCurrentPasswordChanged,
                onNewPasswordChanged = onNewPasswordChanged,
                onConfirmPasswordChanged = onConfirmPasswordChanged,
                onSubmit = onChangePassword
            )

            Divider()
            DeleteAccountSection(
                password = state.deleteAccountPasswordInput,
                isDeleting = state.isDeletingAccount,
                error = state.deleteAccountError,
                showConfirm = state.showDeleteAccountConfirm,
                onPasswordChanged = onDeleteAccountPasswordChanged,
                onRequestConfirm = onRequestDeleteAccountConfirm,
                onCancelConfirm = onCancelDeleteAccountConfirm,
                onConfirmDelete = onConfirmDeleteAccount
            )
        }
    }
}

@Composable
private fun AvatarSection(
    bitmap: android.graphics.Bitmap?,
    isUploading: Boolean,
    error: String?,
    onUpload: (android.net.Uri) -> Unit
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onUpload) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Avatar", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(64.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Your avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        )
                    }
                }
                Button(onClick = { pickerLauncher.launch("image/*") }, enabled = !isUploading) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Change Avatar")
                    }
                }
            }
            Text(
                text = "JPEG or GIF, up to 15MB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SessionsSection(
    sessions: List<BridgeSession>,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onRevoke: (String, Boolean) -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }

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
                Text(text = "Signed-In Devices", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onLoad, enabled = !isLoading) { Text("Refresh") }
            }
            if (isLoading && sessions.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (sessions.isEmpty()) {
                Text(
                    text = "No active sessions found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sessions.forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = (session.deviceName ?: "Unknown device") + if (session.isCurrent) " (this device)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (session.isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Signed in ${session.createdAt ?: "?"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRevoke(session.tokenId, session.isCurrent) }) {
                            Text(if (session.isCurrent) "Sign Out" else "Revoke")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordSection(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isChanging: Boolean,
    error: String?,
    succeeded: Boolean,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Change Password", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Other devices signed into your account will be signed out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChanged,
                label = { Text("Current password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChanged,
                label = { Text("New password (min. 8 characters)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChanged,
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (succeeded) {
                Text(text = "Password updated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSubmit,
                enabled = !isChanging && currentPassword.isNotBlank() && newPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isChanging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Change Password")
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountSection(
    password: String,
    isDeleting: Boolean,
    error: String?,
    showConfirm: Boolean,
    onPasswordChanged: (String) -> Unit,
    onRequestConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Delete Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Permanently deletes your account, favorites, playlists, history, backups, and uploads. This can't be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                label = { Text("Confirm your password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onRequestConfirm,
                enabled = !isDeleting && password.isNotBlank(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Text("Delete Account")
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = onCancelConfirm,
            title = { Text("Delete your account permanently?") },
            text = { Text("This cannot be undone. Everything associated with your account will be gone immediately.") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("Delete Account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelConfirm) { Text("Cancel") }
            }
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
    onLogout: () -> Unit,
    onTwoFactorCodeChanged: (String) -> Unit,
    onCompleteTwoFactorLogin: () -> Unit,
    onCancelTwoFactorLogin: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onSaveDisplayName: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Account", style = MaterialTheme.typography.titleMedium)

            when {
                state.isTwoFactorPending -> TwoFactorForm(
                    code = state.twoFactorCode,
                    isLoading = state.isAuthLoading,
                    onCodeChanged = onTwoFactorCodeChanged,
                    onSubmit = onCompleteTwoFactorLogin,
                    onCancel = onCancelTwoFactorLogin
                )
                state.isLoggedIn -> LoggedInContent(
                    username = state.username,
                    isLoggingOut = state.isLoggingOut,
                    displayNameInput = state.displayNameInput,
                    isSavingDisplayName = state.isSavingDisplayName,
                    displayNameJustSaved = state.displayNameJustSaved,
                    onDisplayNameChanged = onDisplayNameChanged,
                    onSaveDisplayName = onSaveDisplayName,
                    onLogout = onLogout
                )
                else -> LoggedOutContent(
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
    displayNameInput: String,
    isSavingDisplayName: Boolean,
    displayNameJustSaved: Boolean,
    onDisplayNameChanged: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onLogout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

        Divider()

        Text(text = "Display Name", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = displayNameInput,
            onValueChange = onDisplayNameChanged,
            label = { Text("Shown to friends instead of your username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (displayNameJustSaved && !isSavingDisplayName) {
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Button(onClick = onSaveDisplayName, enabled = !isSavingDisplayName) {
                if (isSavingDisplayName) {
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

@Composable
private fun TwoFactorForm(
    code: String,
    isLoading: Boolean,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "This account has two-factor authentication enabled.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onCancel, enabled = !isLoading) {
                Text("Cancel")
            }
            Button(
                onClick = onSubmit,
                enabled = !isLoading && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify")
                }
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
