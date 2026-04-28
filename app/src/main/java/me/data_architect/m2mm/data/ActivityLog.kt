package me.data_architect.m2mm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_log")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: String,
    val pointsAwarded: Int,
    val timestamp: Long = System.currentTimeMillis()
)
