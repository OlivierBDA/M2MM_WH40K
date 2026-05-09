package me.data_architect.m2mm.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GameRepository(val context: Context, private val dao: M2MMDao) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        encodeDefaults = true
    }
    
    private val oldUserConfigFile = context.getFileStreamPath("user_config.json")
    private val activitiesFile = context.getFileStreamPath("activities.json")

    fun config() = loadConfig()

    fun loadConfig(): GameConfig {
        migrateOldConfigIfNeeded()

        if (!activitiesFile.exists()) {
            copyDefaultActivities()
        }

        val levelsConfig = try {
            val levelsString = context.assets.open("levels.json").bufferedReader().use { it.readText() }
            json.decodeFromString<LevelsConfig>(levelsString)
        } catch (e: Exception) {
            LevelsConfig(emptyList())
        }

        val parametersConfig = try {
            val paramsString = context.assets.open("parameters.json").bufferedReader().use { it.readText() }
            json.decodeFromString<ParametersConfig>(paramsString)
        } catch (e: Exception) {
            ParametersConfig(emptyMap())
        }

        val activitiesConfig = try {
            activitiesFile.bufferedReader().use { json.decodeFromString<ActivitiesConfig>(it.readText()) }
        } catch (e: Exception) {
            val defaultString = context.assets.open("activities.json").bufferedReader().use { it.readText() }
            json.decodeFromString<ActivitiesConfig>(defaultString)
        }

        return GameConfig(
            daily_decay_points = activitiesConfig.daily_decay_points,
            activities = activitiesConfig.activities,
            levels = levelsConfig.levels,
            status_levels = parametersConfig.status_levels,
            widget_progression_thresholds = parametersConfig.widget_progression_thresholds,
            llm_api_key = activitiesConfig.llm_api_key,
            use_local_llm = activitiesConfig.use_local_llm,
            coach_notification_time = parametersConfig.coach_notification_time
        )
    }

    private fun migrateOldConfigIfNeeded() {
        if (oldUserConfigFile.exists() && !activitiesFile.exists()) {
            try {
                val oldConfigString = oldUserConfigFile.bufferedReader().use { it.readText() }
                // old user_config.json contains full GameConfig structure
                val oldConfig = json.decodeFromString<GameConfig>(oldConfigString)
                val newActivities = ActivitiesConfig(
                    daily_decay_points = oldConfig.daily_decay_points,
                    activities = oldConfig.activities,
                    llm_api_key = oldConfig.llm_api_key,
                    use_local_llm = oldConfig.use_local_llm
                )
                val newActivitiesString = json.encodeToString(ActivitiesConfig.serializer(), newActivities)
                context.openFileOutput("activities.json", Context.MODE_PRIVATE).use { output ->
                    output.write(newActivitiesString.toByteArray())
                }
                oldUserConfigFile.delete()
            } catch (e: Exception) {
                // Fallback handled by copyDefaultActivities
            }
        }
    }

    private fun copyDefaultActivities() {
        context.assets.open("activities.json").use { input ->
            context.openFileOutput("activities.json", Context.MODE_PRIVATE).use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun saveConfig(newConfig: GameConfig) = withContext(Dispatchers.IO) {
        val activitiesConfig = ActivitiesConfig(
            daily_decay_points = newConfig.daily_decay_points,
            activities = newConfig.activities,
            llm_api_key = newConfig.llm_api_key,
            use_local_llm = newConfig.use_local_llm
        )
        val configString = json.encodeToString(ActivitiesConfig.serializer(), activitiesConfig)
        context.openFileOutput("activities.json", Context.MODE_PRIVATE).use { output ->
            output.write(configString.toByteArray())
        }
    }

    private val coachHistoryFile = context.getFileStreamPath("coach_history.json")

    suspend fun getCoachHistory(): List<String> = withContext(Dispatchers.IO) {
        try {
            if (coachHistoryFile.exists()) {
                val jsonString = coachHistoryFile.bufferedReader().use { it.readText() }
                json.decodeFromString<List<String>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addCoachHistory(message: String) = withContext(Dispatchers.IO) {
        val currentHistory = getCoachHistory().toMutableList()
        currentHistory.add(message)
        // Ne conserver que les 7 dernières phrases
        while (currentHistory.size > 7) {
            currentHistory.removeAt(0)
        }
        val jsonString = json.encodeToString(currentHistory)
        context.openFileOutput("coach_history.json", Context.MODE_PRIVATE).use { output ->
            output.write(jsonString.toByteArray())
        }
    }

    suspend fun getGameState(): GameState = withContext(Dispatchers.IO) {
        dao.getGameState() ?: GameState()
    }

    suspend fun getLevelDetails(): LevelDetails = withContext(Dispatchers.IO) {
        val state = dao.getGameState() ?: GameState()
        val score = state.score
        
        val currentConfig = config()
        val sortedLevels = currentConfig.levels.sortedBy { it.threshold }
        var currentLevel = sortedLevels.first()
        var nextLevelThreshold = currentLevel.threshold

        for (i in sortedLevels.indices) {
            val level = sortedLevels[i]
            if (score >= level.threshold) {
                currentLevel = level
                nextLevelThreshold = if (i + 1 < sortedLevels.size) {
                    sortedLevels[i + 1].threshold
                } else {
                    level.threshold
                }
            } else {
                if (nextLevelThreshold == sortedLevels.first().threshold) {
                    nextLevelThreshold = level.threshold
                }
                break
            }
        }

        val achievedLegions = sortedLevels
            .filter { score >= it.threshold }
            .filter { it.legion_number != null && it.legion_name != null && it.legion_insignia != null }
            .map { Legion(it.legion_number!!, it.legion_name!!, it.legion_insignia!!) }
            .distinctBy { it.number }

        LevelDetails(currentLevel, score, nextLevelThreshold, achievedLegions)
    }

    suspend fun recordActivity(activityId: String): Int = withContext(Dispatchers.IO) {
        val currentConfig = config()
        val activity = currentConfig.activities.find { it.id == activityId } ?: return@withContext 0
        val points = activity.points
        
        val currentState = dao.getGameState() ?: GameState()
        val newScore = maxOf(0, currentState.score + points)
        
        dao.saveGameState(currentState.copy(score = newScore, lastUpdate = System.currentTimeMillis()))
        dao.insertLog(ActivityLog(activityId = activityId, pointsAwarded = points))
        dao.insertScoreHistory(ScoreHistory(score = newScore))
        
        points
    }

    suspend fun applyDailyDecay() = withContext(Dispatchers.IO) {
        val state = dao.getGameState() ?: return@withContext
        val lastUpdate = state.lastUpdate
        val now = System.currentTimeMillis()
        
        val diffInMillis = now - lastUpdate
        val daysPassed = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        
        if (daysPassed > 0) {
            val decay = config().daily_decay_points * daysPassed
            val newScore = maxOf(0, state.score + decay)
            dao.saveGameState(state.copy(score = newScore, lastUpdate = now))
            dao.insertScoreHistory(ScoreHistory(score = newScore, timestamp = now))
        }
    }

    suspend fun getRecentScoreHistory(): List<ScoreHistory> = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.getScoreHistory(thirtyDaysAgo)
    }

    suspend fun getRecentActivityLogs(): List<ActivityLog> = withContext(Dispatchers.IO) {
        dao.getAllLogs().filter { it.timestamp >= System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000) }
    }

    suspend fun getScoreAtTimestamp(timestamp: Long): Int = withContext(Dispatchers.IO) {
        dao.getScoreAtTimestamp(timestamp) ?: 0
    }

    suspend fun getLastActivityDates(): Map<String, Long> = withContext(Dispatchers.IO) {
        dao.getLastActivityDates().associate { it.activityId to it.timestamp }
    }
}

data class Legion(val number: String, val name: String, val insignia: String)

data class LevelDetails(
    val currentLevel: LevelConfig,
    val score: Int,
    val nextLevelThreshold: Int,
    val achievedLegions: List<Legion> = emptyList()
)
