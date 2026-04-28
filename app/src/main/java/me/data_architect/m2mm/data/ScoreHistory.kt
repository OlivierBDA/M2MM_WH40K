package me.data_architect.m2mm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "score_history")
data class ScoreHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val score: Int,
    val timestamp: Long = System.currentTimeMillis()
)
