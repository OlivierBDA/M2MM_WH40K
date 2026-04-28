package me.data_architect.m2mm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.data_architect.m2mm.data.GameRepository
import me.data_architect.m2mm.data.ScoreHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatsViewModel(private val repository: GameRepository) : ViewModel() {

    private val _historyState = MutableStateFlow<List<ScoreHistory>>(emptyList())
    val historyState: StateFlow<List<ScoreHistory>> = _historyState

    init {
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _historyState.value = repository.getRecentScoreHistory()
        }
    }
}
