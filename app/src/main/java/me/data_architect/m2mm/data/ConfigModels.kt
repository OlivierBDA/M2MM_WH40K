package me.data_architect.m2mm.data

import kotlinx.serialization.Serializable

@Serializable
data class GameConfig(
    val daily_decay_points: Int,
    val levels: List<LevelConfig>,
    val activities: List<ActivityConfig>,
    val status_levels: Map<String, List<StatusLevel>>,
    val widget_progression_thresholds: Map<String, Int> = emptyMap()
)

@Serializable
data class LevelConfig(
    val name: String,
    val threshold: Int,
    val image: String,
    val legion_number: String? = null,
    val legion_name: String? = null,
    val legion_insignia: String? = null
)

@Serializable
data class ActivityConfig(
    val id: String,
    val name: String,
    val icon: String,
    val points: Int,
    val type: String, // "good" or "bad"
    val align: String = "left", // "left" or "right"
    val order: Int = 99,
    val day_thresholds: Map<String, Int> = emptyMap(),
    val show_in_widget: Boolean = false
)

@Serializable
data class StatusLevel(
    val label: String,
    val emoji: String,
    val color: String
)

@Serializable
data class ActivitiesConfig(
    val daily_decay_points: Int,
    val activities: List<ActivityConfig>
)

@Serializable
data class LevelsConfig(
    val levels: List<LevelConfig>
)

@Serializable
data class ParametersConfig(
    val status_levels: Map<String, List<StatusLevel>>,
    val widget_progression_thresholds: Map<String, Int> = emptyMap()
)
