package com.stash.opusplayer.ui.compose.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stash.opusplayer.bridge.api.ProfileComment

@Composable
fun PublicProfileScreen(
    userId: String,
    modifier: Modifier = Modifier,
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.load(userId) }

    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.uploadBannerFromUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        BannerHeader(
            bannerBitmap = state.bannerBitmap,
            isLoadingBanner = state.isLoadingBanner,
            isSelf = viewModel.isSelfProfile(),
            isUploading = state.isUploadingBanner,
            onPickBanner = { bannerPicker.launch("image/*") },
            onRemoveBanner = viewModel::removeBanner
        )

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                state.wasBlocked -> Text(
                    text = "This profile isn't available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                state.error != null -> Text(text = state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                state.profile != null -> {
                    val profile = state.profile!!
                    Column {
                        Text(
                            text = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "@${profile.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (profile.isFriend) {
                            Text(text = "Friend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    profile.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }

                    profile.memberSince?.let {
                        Text(
                            text = "Member since ${it.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    profile.listeningStreak?.let { streak ->
                        if (streak.currentStreakDays > 0 || streak.longestStreakDays > 0) {
                            Text(
                                text = "🔥 ${streak.currentStreakDays} day streak (longest: ${streak.longestStreakDays})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (profile.badges.isNotEmpty()) {
                        BadgeRow(profile.badges)
                    }

                    state.bannerError?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    if (profile.isFriend && !viewModel.isSelfProfile()) {
                        state.compatibility?.let { compat ->
                            Divider()
                            MusicMatchCard(compat)
                        }
                    }

                    if (profile.showGuestbook || viewModel.isSelfProfile()) {
                        Divider()
                        GuestbookSection(
                            state = state,
                            profile = profile,
                            isSelf = viewModel.isSelfProfile(),
                            currentUsername = viewModel.currentUsername,
                            onNewCommentChanged = viewModel::onNewCommentChanged,
                            onPostComment = viewModel::postComment,
                            onDeleteComment = viewModel::deleteComment
                        )
                    }

                    if (profile.isFriend && !viewModel.isSelfProfile()) {
                        Divider()
                        TextButton(onClick = viewModel::requestBlockConfirm, enabled = !state.isBlocking) {
                            Text("Block User", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (state.showBlockConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelBlockConfirm,
            title = { Text("Block this user?") },
            text = { Text("You'll no longer see each other's profiles, and any friendship or pending request will be removed.") },
            confirmButton = { TextButton(onClick = viewModel::confirmBlock) { Text("Block", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelBlockConfirm) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BannerHeader(
    bannerBitmap: android.graphics.Bitmap?,
    isLoadingBanner: Boolean,
    isSelf: Boolean,
    isUploading: Boolean,
    onPickBanner: () -> Unit,
    onRemoveBanner: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .then(
                if (bannerBitmap == null) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    )
                } else {
                    Modifier
                }
            )
    ) {
        bannerBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Profile banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        if (isLoadingBanner) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(20.dp),
                strokeWidth = 2.dp
            )
        }

        if (isSelf) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bannerBitmap != null) {
                    OutlinedButton(onClick = onRemoveBanner, enabled = !isUploading) { Text("Remove") }
                }
                Button(onClick = onPickBanner, enabled = !isUploading) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (bannerBitmap == null) "Add Banner" else "Change Banner")
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(badges: List<com.stash.opusplayer.bridge.api.ProfileBadge>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        badges.forEach { badge ->
            val tierColor = when (badge.tier) {
                "gold" -> androidx.compose.ui.graphics.Color(0xFFFFD700)
                "silver" -> androidx.compose.ui.graphics.Color(0xFFC0C0C0)
                "bronze" -> androidx.compose.ui.graphics.Color(0xFFCD7F32)
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            Surface(shape = MaterialTheme.shapes.extraLarge, color = tierColor.copy(alpha = 0.25f)) {
                Text(
                    text = badge.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MusicMatchCard(compatibility: com.stash.opusplayer.bridge.api.MusicCompatibility) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Music Match", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (compatibility.insufficientData) {
            Text(
                text = "Not enough listening history yet to compute a match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "${compatibility.score}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = "match", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(compatibility.score.coerceIn(0, 100) / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (compatibility.sharedArtists.isNotEmpty()) {
                Text(text = "Shared Artists: ${compatibility.sharedArtists.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
            if (compatibility.sharedGenres.isNotEmpty()) {
                Text(text = "Shared Genres: ${compatibility.sharedGenres.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GuestbookSection(
    state: PublicProfileViewModel.UiState,
    profile: com.stash.opusplayer.bridge.api.PublicSocialProfile,
    isSelf: Boolean,
    currentUsername: String?,
    onNewCommentChanged: (String) -> Unit,
    onPostComment: () -> Unit,
    onDeleteComment: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Guestbook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (profile.isFriend && !isSelf) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.newCommentBody,
                    onValueChange = onNewCommentChanged,
                    label = { Text("Leave a message") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onPostComment, enabled = !state.isPostingComment && state.newCommentBody.isNotBlank()) {
                    if (state.isPostingComment) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Post")
                    }
                }
            }
            state.commentError?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        when {
            state.isLoadingComments -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            state.comments.isEmpty() -> Text(
                text = if (isSelf) "No messages yet -- friends can leave one here." else "No messages yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.comments.forEach { comment ->
                CommentRow(
                    comment = comment,
                    canDelete = isSelf || comment.authorUsername == currentUsername,
                    isDeleting = state.deletingCommentId == comment.id,
                    onDelete = { onDeleteComment(comment.id) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun CommentRow(comment: ProfileComment, canDelete: Boolean, isDeleting: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.authorDisplayName?.takeIf { it.isNotBlank() } ?: comment.authorUsername,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
        }
        if (canDelete && !isDeleting) {
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

