package com.stash.opusplayer.ui.compose.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stash.opusplayer.bridge.api.DayStat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Ported from `ListeningHeatmapView.swift` -- a GitHub-contributions-style
 * grid, one column per week, one cell per day, shaded by play count for
 * that day. The server omits zero-play days rather than zero-filling
 * (see [com.stash.opusplayer.bridge.api.StatsApi.getHeatmap]), so the grid
 * is built locally from [HeatmapViewModel.UiState.rangeDays] and only the
 * cells with a matching [DayStat] get shaded.
 */
@Composable
fun HeatmapScreen(viewModel: HeatmapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedDay by remember { mutableStateOf<DayStat?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "")
                    Text("Tap to retry", modifier = Modifier.padding(top = 8.dp).clickable { viewModel.load() })
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val byDate = remember(state.days) { state.days.associateBy { it.date } }
                val maxPlays = remember(state.days) { state.days.maxOfOrNull { it.plays } ?: 0 }

                Text(
                    selectedDay?.let { "${it.date}: ${it.plays} plays" }
                        ?: "Last ${state.rangeDays} days -- tap a day for details",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val weeks = buildWeeks(state.rangeDays)
                    weeks.forEach { week ->
                        Column {
                            week.forEach { date ->
                                val stat = date?.let { byDate[it.format(DateTimeFormatter.ISO_LOCAL_DATE)] }
                                HeatmapCell(
                                    plays = stat?.plays ?: 0,
                                    maxPlays = maxPlays,
                                    isPlaceholder = date == null,
                                    onClick = { if (stat != null) selectedDay = stat }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(plays: Int, maxPlays: Int, isPlaceholder: Boolean, onClick: () -> Unit) {
    val color = when {
        isPlaceholder -> Color.Transparent
        plays <= 0 -> MaterialTheme.colorScheme.surfaceVariant
        else -> {
            val intensity = if (maxPlays > 0) (plays.toFloat() / maxPlays.toFloat()).coerceIn(0.15f, 1f) else 0.15f
            MaterialTheme.colorScheme.primary.copy(alpha = intensity)
        }
    }
    Box(
        modifier = Modifier
            .padding(1.dp)
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .then(if (!isPlaceholder) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/** One column per calendar week, oldest first, padded with nulls so every column has 7 entries (Sun-Sat). */
private fun buildWeeks(rangeDays: Int): List<List<LocalDate?>> {
    val today = LocalDate.now()
    val start = today.minusDays((rangeDays - 1).toLong())
    val startOfFirstWeek = start.minusDays(start.dayOfWeek.value.toLong() % 7)

    val allDays = generateSequence(startOfFirstWeek) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { if (it.isBefore(start)) null else it }
        .toList()

    return allDays.chunked(7)
}
