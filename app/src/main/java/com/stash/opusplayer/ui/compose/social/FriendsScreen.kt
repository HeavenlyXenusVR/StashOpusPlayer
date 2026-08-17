package com.stash.opusplayer.ui.compose.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.BridgeFriend
import com.stash.opusplayer.bridge.api.FriendRequestEntry

/**
 * Friends screen: current friends, incoming/outgoing friend requests, and a
 * form to send a new request — the full surface [SocialApi] exposes today.
 *
 * There is deliberately no online/offline indicator per friend: neither
 * `BridgeFriend` nor `FriendRequestEntry` carries a presence flag, and
 * `SocialApi.updatePresence` is a one-way heartbeat (push-only, no
 * friends'-presence read), so there's no data to show a dot with. Presence
 * heartbeating itself is intentionally NOT wired up here — it's an
 * app-lifecycle concern, not this screen's job.
 */
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel = hiltViewModel(),
    onFriendClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !uiState.isLoggedIn -> LoggedOutMessage()

            uiState.isLoading && uiState.friends.isEmpty() &&
                uiState.incomingRequests.isEmpty() && uiState.outgoingRequests.isEmpty() ->
                CenteredLoading()

            uiState.errorMessage != null && uiState.friends.isEmpty() &&
                uiState.incomingRequests.isEmpty() && uiState.outgoingRequests.isEmpty() ->
                CenteredError(message = uiState.errorMessage!!, onRetry = viewModel::refresh)

            else -> FriendsContent(
                friends = uiState.friends,
                incomingRequests = uiState.incomingRequests,
                outgoingRequests = uiState.outgoingRequests,
                errorMessage = uiState.errorMessage,
                sendRequestUsername = uiState.sendRequestUsername,
                isSendingRequest = uiState.isSendingRequest,
                sendRequestFeedback = uiState.sendRequestFeedback,
                pendingRequestActionIds = uiState.pendingRequestActionIds,
                onUsernameChange = viewModel::onSendUsernameChange,
                onSendRequest = viewModel::sendFriendRequest,
                onAccept = viewModel::acceptRequest,
                onDecline = viewModel::declineRequest,
                onFriendClick = onFriendClick
            )
        }
    }
}

@Composable
private fun LoggedOutMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Log in to see friends",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun FriendsContent(
    friends: List<BridgeFriend>,
    incomingRequests: List<FriendRequestEntry>,
    outgoingRequests: List<FriendRequestEntry>,
    errorMessage: String?,
    sendRequestUsername: String,
    isSendingRequest: Boolean,
    sendRequestFeedback: String?,
    pendingRequestActionIds: Set<String>,
    onUsernameChange: (String) -> Unit,
    onSendRequest: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onFriendClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Friends",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SendFriendRequestCard(
                username = sendRequestUsername,
                isSending = isSendingRequest,
                feedback = sendRequestFeedback,
                onUsernameChange = onUsernameChange,
                onSendRequest = onSendRequest
            )
        }

        if (incomingRequests.isNotEmpty()) {
            item {
                SectionHeader("Incoming Requests")
            }
            items(incomingRequests, key = { it.requestId }) { request ->
                IncomingRequestRow(
                    request = request,
                    isBusy = pendingRequestActionIds.contains(request.requestId),
                    onAccept = { onAccept(request.requestId) },
                    onDecline = { onDecline(request.requestId) }
                )
            }
        }

        if (outgoingRequests.isNotEmpty()) {
            item {
                SectionHeader("Outgoing Requests")
            }
            items(outgoingRequests, key = { it.requestId }) { request ->
                OutgoingRequestRow(request = request)
            }
        }

        item {
            SectionHeader("Your Friends")
        }

        if (friends.isEmpty()) {
            item {
                Text(
                    text = "No friends yet — send a request above to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(friends, key = { it.userId }) { friend ->
                FriendRow(friend, onClick = { onFriendClick(friend.userId) })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SendFriendRequestCard(
    username: String,
    isSending: Boolean,
    feedback: String?,
    onUsernameChange: (String) -> Unit,
    onSendRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Add a Friend", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSendRequest,
                enabled = username.isNotBlank() && !isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSending) "Sending..." else "Send Request")
            }
            if (feedback != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = feedback, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FriendRow(friend: BridgeFriend, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = friend.displayName?.takeIf { it.isNotBlank() } ?: friend.username,
                style = MaterialTheme.typography.titleMedium
            )
            if (!friend.displayName.isNullOrBlank()) {
                Text(
                    text = "@${friend.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IncomingRequestRow(
    request: FriendRequestEntry,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.displayName?.takeIf { it.isNotBlank() } ?: request.username,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "@${request.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                OutlinedButton(onClick = onDecline) { Text("Decline") }
                Button(onClick = onAccept) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun OutgoingRequestRow(request: FriendRequestEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.displayName?.takeIf { it.isNotBlank() } ?: request.username,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "@${request.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Pending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
