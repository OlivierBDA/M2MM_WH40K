package me.data_architect.m2mm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GameState::class, ActivityLog::class, ScoreHistory::class], version = 2)
abstract class M2MMDatabase : RoomDatabase() {
    abstract fun dao(): M2MMDao

    companion object {
        @Volatile
        private var INSTANCE: M2MMDatabase? = null

        fun getDatabase(context: Context): M2MMDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    M2MMDatabase::class.java,
                    "m2mm_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
