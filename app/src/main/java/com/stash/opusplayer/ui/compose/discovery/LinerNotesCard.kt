package com.stash.opusplayer.ui.compose.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Ported from Lumisound's `AlbumLinerNotesCard` (embedded in
 * `AlbumDetailView.swift`). Renders nothing if [blurb] is null -- callers
 * should only place this once [com.stash.opusplayer.discovery.LinerNotesService.fetchLinerNotes]
 * resolves to a non-null value, same convention as [ArtistBioCard].
 */
@Composable
fun LinerNotesCard(blurb: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Liner Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = blurb, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
