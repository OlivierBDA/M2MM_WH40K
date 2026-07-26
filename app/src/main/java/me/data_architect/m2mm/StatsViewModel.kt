package me.data_architect.m2mm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.data_architect.m2mm.data.ActivityConfig
import me.data_architect.m2mm.data.GameRepository
import me.data_architect.m2mm.data.ScoreHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ActivityStat(
    val activity: ActivityConfig,
    val lastDate: Long?,
    val count30Days: Int
)

data class StatsUiState(
    val scoreHistory: List<ScoreHistory> = emptyList(),
    val activityStats: List<ActivityStat> = emptyList()
)

class StatsViewModel(private val repository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            val history = repository.getRecentScoreHistory()
            val config = repository.config()
            val lastDates = repository.getLastActivityDates()
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val allLogs = repository.getAllLogs()
            val logs30Days = allLogs.filter { it.timestamp >= thirtyDaysAgo }
            val counts30Days = logs30Days.groupingBy { it.activityId }.eachCount()

            val stats = config.activities.map { activity ->
                ActivityStat(
                    activity = activity,
                    lastDate = lastDates[activity.id],
                    count30Days = counts30Days[activity.id] ?: 0
                )
            }

            _uiState.value = StatsUiState(
                scoreHistory = history,
                activityStats = stats
            )
        }
    }
}
