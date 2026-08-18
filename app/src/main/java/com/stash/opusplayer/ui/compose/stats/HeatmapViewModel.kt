package com.stash.opusplayer.ui.compose.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.DayStat
import com.stash.opusplayer.bridge.api.StatsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs `Settings -> Listening Heatmap`, ported from
 * `ListeningHeatmapView.swift`. Days with zero plays are omitted by the
 * server rather than zero-filled (see [StatsApi.getHeatmap]'s doc), so the
 * grid is built by indexing [UiState.days] by date string rather than by
 * assuming one entry per day.
 */
@HiltViewModel
class HeatmapViewModel @Inject constructor(
    private val statsApi: StatsApi
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val days: List<DayStat> = emptyList(),
        val error: String? = null,
        val rangeDays: Int = 365
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(rangeDays: Int = _uiState.value.rangeDays) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, rangeDays = rangeDays) }
            try {
                val response = statsApi.getHeatmap(days = rangeDays)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoading = false, days = response.body() ?: emptyList()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load the heatmap (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }
}
