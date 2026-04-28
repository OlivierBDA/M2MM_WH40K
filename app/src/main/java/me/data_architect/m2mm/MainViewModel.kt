package me.data_architect.m2mm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.data_architect.m2mm.data.GameRepository
import me.data_architect.m2mm.data.LevelDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState

    private val _pointFeedback = MutableStateFlow<Int?>(null)
    val pointFeedback: StateFlow<Int?> = _pointFeedback

    private var lastLevelName: String? = null

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.applyDailyDecay()
            val levelDetails = repository.getLevelDetails()
            val lastActivityDates = repository.getLastActivityDates()
            _uiState.value = MainUiState.Success(levelDetails, lastActivityDates)
            
            // Auto wallpaper sync if level changed
            if (levelDetails.currentLevel.name != lastLevelName) {
                lastLevelName = levelDetails.currentLevel.name
                WallpaperHelper.updateWallpaper(repository.context, levelDetails.currentLevel.image)
            }
            WidgetRefreshHelper.refreshAllWidgets(repository.context)
        }
    }

    fun recordActivity(activityId: String) {
        viewModelScope.launch {
            val points = repository.recordActivity(activityId)
            _pointFeedback.value = points
            refreshData()
            
            // Auto-reset feedback after 2 seconds
            kotlinx.coroutines.delay(2000)
            _pointFeedback.value = null
        }
    }

    fun getConfig() = repository.config()
}

sealed class MainUiState {
    object Loading : MainUiState()
    data class Success(val levelDetails: LevelDetails, val lastActivityDates: Map<String, Long>) : MainUiState()
}
