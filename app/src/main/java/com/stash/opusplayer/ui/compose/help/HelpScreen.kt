package com.stash.opusplayer.ui.compose.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Ported from `SettingsHelpView.swift`/`SettingsHelpDetailView.swift` --
 * same two-level flow (tap a category, see its topics), kept as one
 * Compose island with internal list/detail state rather than a
 * NavigationLink push, matching this app's established flat-Compose-island
 * pattern for related sub-screens (see e.g. `PublicProfileScreen`). See
 * [HelpContent]'s doc for why the content itself isn't a line-for-line
 * transcription of the iOS source.
 */
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    var selectedCategory by remember { mutableStateOf<HelpCategory?>(null) }

    val category = selectedCategory
    if (category == null) {
        HelpCategoryList(
            categories = HelpContent.categories,
            onCategoryClick = { selectedCategory = it },
            modifier = modifier
        )
    } else {
        HelpTopicList(
            category = category,
            onBack = { selectedCategory = null },
            modifier = modifier
        )
    }
}

@Composable
private fun HelpCategoryList(
    categories: List<HelpCategory>,
    onCategoryClick: (HelpCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        items(categories) { category ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onCategoryClick(category) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = category.icon, style = MaterialTheme.typography.titleLarge)
                    Column {
                        Text(text = category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${category.topics.size} topics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpTopicList(
    category: HelpCategory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("< Back") }
                Text(
                    text = "${category.icon}  ${category.title}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Divider()
        }
        items(category.topics) { topic ->
            Column(modifier = Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = topic.icon)
                    Text(text = topic.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = topic.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Divider()
        }
    }
}
