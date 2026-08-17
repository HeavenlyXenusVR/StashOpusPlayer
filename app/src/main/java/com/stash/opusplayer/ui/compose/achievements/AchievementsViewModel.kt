package com.stash.opusplayer.ui.compose.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stash.opusplayer.bridge.api.SyncApi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementsUiState(
    val isLoading: Boolean = true,
    val totalPlays: Int = 0,
    val totalListenSeconds: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val badges: List<String> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val syncApi: SyncApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Device's current UTC offset in minutes -- shifts streak/day-part
                // badge grouping to the user's local calendar day server-side,
                // matching what tz_offset_minutes is for (see SyncApi's doc comment
                // on getAchievements).
                val tzOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
                val response = syncApi.getAchievements(tzOffsetMinutes)
                if (response.isSuccessful) {
                    val body = response.body()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            totalPlays = body?.totalPlays ?: 0,
                            totalListenSeconds = body?.totalListenSeconds ?: 0,
                            currentStreakDays = body?.currentStreakDays ?: 0,
                            longestStreakDays = body?.longestStreakDays ?: 0,
                            badges = body?.badges.orEmpty()
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load achievements (HTTP ${response.code()}).") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Check your connection and sign-in.") }
            }
        }
    }
}
