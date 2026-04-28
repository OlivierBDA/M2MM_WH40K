package me.data_architect.m2mm.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GameRepository(val context: Context, private val dao: M2MMDao) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        encodeDefaults = true
    }
    
    private val userConfigFile = context.getFileStreamPath("user_config.json")

    fun config() = loadConfig()

    fun loadConfig(): GameConfig {
        if (!userConfigFile.exists()) {
            copyDefaultConfig()
        }
        return try {
            userConfigFile.bufferedReader().use { json.decodeFromString<GameConfig>(it.readText()) }
        } catch (e: Exception) {
            // Fallback to assets if corrupted
            val configString = context.assets.open("config.json").bufferedReader().use { it.readText() }
            json.decodeFromString<GameConfig>(configString)
        }
    }

    private fun copyDefaultConfig() {
        context.assets.open("config.json").use { input ->
            context.openFileOutput("user_config.json", Context.MODE_PRIVATE).use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun saveConfig(newConfig: GameConfig) = withContext(Dispatchers.IO) {
        val configString = json.encodeToString(GameConfig.serializer(), newConfig)
        context.openFileOutput("user_config.json", Context.MODE_PRIVATE).use { output ->
            output.write(configString.toByteArray())
        }
        // Force reload of lazy config (Note: lazy isn't ideal here, but for this step we'll just restart or force refresh)
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
