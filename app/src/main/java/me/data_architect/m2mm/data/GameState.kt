package me.data_architect.m2mm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_state")
data class GameState(
    @PrimaryKey val id: Int = 1,
    val score: Int = 0,
    val lastUpdate: Long = System.currentTimeMillis()
)
