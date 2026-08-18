package com.stash.opusplayer.ui.compose.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.AccountStatsResponse
import com.stash.opusplayer.bridge.api.MonthInReviewResponse
import com.stash.opusplayer.bridge.api.StatsApi
import com.stash.opusplayer.bridge.api.YearInReviewResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which recap is currently shown. */
enum class RewindMode { ALL_TIME, THIS_MONTH, THIS_YEAR }

/**
 * Backs `Settings -> Rewind`, ported from Lumisound's `RewindView.swift`
 * (`AccountService+Stats.swift` for the fetch layer). All three modes map
 * to a plain GET with no explicit period argument -- year/month always
 * default to "current" server-side when omitted, matching how the Swift
 * original never lets the user pick a past period either.
 *
 * Deliberately does NOT model "Share My Rewind" -- iOS rasterizes the
 * recap card via `ImageRenderer` and pushes it through the share sheet, a
 * pure on-device rendering feature with no bridge contract; only the
 * underlying stats data is ported here.
 */
@HiltViewModel
class RewindViewModel @Inject constructor(
    private val statsApi: StatsApi
) : ViewModel() {

    data class UiState(
        val selectedMode: RewindMode = RewindMode.ALL_TIME,

        val isLoadingAllTime: Boolean = true,
        val allTime: AccountStatsResponse? = null,
        val allTimeError: String? = null,

        val isLoadingMonth: Boolean = true,
        val month: MonthInReviewResponse? = null,
        val monthError: String? = null,

        val isLoadingYear: Boolean = true,
        val year: YearInReviewResponse? = null,
        val yearError: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadAllTime()
        loadMonth()
        loadYear()
    }

    fun onModeSelected(mode: RewindMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun loadAllTime() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAllTime = true, allTimeError = null) }
            try {
                val response = statsApi.getStats()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingAllTime = false, allTime = response.body()) }
                } else {
                    _uiState.update { it.copy(isLoadingAllTime = false, allTimeError = "Couldn't load stats (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingAllTime = false, allTimeError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun loadMonth() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMonth = true, monthError = null) }
            try {
                val response = statsApi.getMonthInReview()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingMonth = false, month = response.body()) }
                } else {
                    _uiState.update { it.copy(isLoadingMonth = false, monthError = "Couldn't load this month's stats (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMonth = false, monthError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }

    fun loadYear() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingYear = true, yearError = null) }
            try {
                val response = statsApi.getYearInReview()
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isLoadingYear = false, year = response.body()) }
                } else {
                    _uiState.update { it.copy(isLoadingYear = false, yearError = "Couldn't load this year's stats (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingYear = false, yearError = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }
}
