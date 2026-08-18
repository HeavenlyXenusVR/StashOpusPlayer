package com.stash.opusplayer.ui.compose.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stash.opusplayer.bridge.api.ArtistBioResponse

/**
 * Ported from Lumisound's `ArtistBioCard` (embedded in `ArtistDetailView.swift`).
 * Truncates to 4 lines with a Read More/Show Less toggle, matching the
 * Swift original. Renders nothing if [bio] is null -- callers should only
 * place this once [ArtistBioService.fetchBio] resolves to a non-null value
 * (a not-found/logged-out/error result is already filtered to null there).
 */
@Composable
fun ArtistBioCard(bio: ArtistBioResponse, onOpenWikipedia: (String) -> Unit, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val facts = listOfNotNull(
                bio.artistType,
                bio.country,
                yearRange(bio.beginDate, bio.endDate)
            ).filter { it.isNotBlank() }
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = bio.bio.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4
            )
            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text(if (isExpanded) "Show Less" else "Read More")
            }

            if (!bio.wikipediaUrl.isNullOrBlank()) {
                TextButton(onClick = { onOpenWikipedia(bio.wikipediaUrl) }) {
                    Text("Wikipedia")
                }
            }

            if (bio.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bio.tags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Mirrors the Swift original: first 4 chars of each ISO date as a year, "present" if no end date. */
private fun yearRange(beginDate: String?, endDate: String?): String? {
    val begin = beginDate?.take(4)?.takeIf { it.isNotBlank() } ?: return null
    val end = endDate?.take(4)?.takeIf { it.isNotBlank() } ?: "present"
    return "$begin–$end"
}
