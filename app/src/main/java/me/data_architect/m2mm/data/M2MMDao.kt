package me.data_architect.m2mm.data

import androidx.room.*

@Dao
interface M2MMDao {
    @Query("SELECT * FROM game_state WHERE id = 1")
    suspend fun getGameState(): GameState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGameState(state: GameState)

    @Insert
    suspend fun insertLog(log: ActivityLog)

    @Query("SELECT activityId, MAX(timestamp) as timestamp FROM activity_log GROUP BY activityId")
    suspend fun getLastActivityDates(): List<LastActivityDate>

    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<ActivityLog>

    @Insert
    suspend fun insertScoreHistory(history: ScoreHistory)

    @Query("SELECT * FROM score_history WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getScoreHistory(since: Long): List<ScoreHistory>

    @Query("SELECT score FROM score_history WHERE timestamp <= :timestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getScoreAtTimestamp(timestamp: Long): Int?
}

data class LastActivityDate(
    val activityId: String,
    val timestamp: Long
)
