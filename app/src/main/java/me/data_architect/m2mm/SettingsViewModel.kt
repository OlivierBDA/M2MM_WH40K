package me.data_architect.m2mm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.data_architect.m2mm.data.GameConfig
import me.data_architect.m2mm.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: GameRepository) : ViewModel() {

    private val _configState = MutableStateFlow<GameConfig>(repository.config())
    val configState: StateFlow<GameConfig> = _configState

    fun updateThreshold(activityId: String, label: String, newValue: Int) {
        val currentConfig = _configState.value
        val newActivities = currentConfig.activities.map { activity ->
            if (activity.id == activityId) {
                val newThresholds = activity.day_thresholds.toMutableMap()
                newThresholds[label] = newValue
                activity.copy(day_thresholds = newThresholds)
            } else {
                activity
            }
        }
        _configState.value = currentConfig.copy(activities = newActivities)
    }

    fun updatePoints(activityId: String, newPoints: Int) {
        val currentConfig = _configState.value
        val newActivities = currentConfig.activities.map { activity ->
            if (activity.id == activityId) {
                activity.copy(points = newPoints)
            } else {
                activity
            }
        }
        _configState.value = currentConfig.copy(activities = newActivities)
    }

    fun updateWidgetVisibility(activityId: String, visible: Boolean) {
        val currentConfig = _configState.value
        val newActivities = currentConfig.activities.map { activity ->
            if (activity.id == activityId) {
                activity.copy(show_in_widget = visible)
            } else {
                activity
            }
        }
        _configState.value = currentConfig.copy(activities = newActivities)
    }

    fun updateDailyDecay(newValue: Int) {
        _configState.value = _configState.value.copy(daily_decay_points = newValue)
    }

    fun updateLlmApiKey(newValue: String) {
        _configState.value = _configState.value.copy(llm_api_key = newValue)
    }

    fun updateUseLocalLlm(newValue: Boolean) {
        _configState.value = _configState.value.copy(use_local_llm = newValue)
    }

    fun saveConfig() {
        viewModelScope.launch {
            repository.saveConfig(_configState.value)
            WidgetRefreshHelper.refreshAllWidgets(repository.context)
        }
    }
}
