package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [FlashlightPattern::class], version = 1, exportSchema = false)
abstract class FlashlightDatabase : RoomDatabase() {
    abstract fun patternDao(): FlashlightPatternDao

    companion object {
        @Volatile
        private var INSTANCE: FlashlightDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): FlashlightDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlashlightDatabase::class.java,
                    "flashlight_database"
                )
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.patternDao())
                }
            }
        }

        suspend fun populateDatabase(dao: FlashlightPatternDao) {
            // Check if already populated to prevent duplicates
            if (dao.getCount() == 0) {
                // 1. SOS (3 short, 3 long, 3 short)
                // short = 200ms, long = 600ms
                dao.insertPattern(
                    FlashlightPattern(
                        name = "SOS Distress",
                        patternString = "200,200,200,200,200,400,600,200,600,200,600,400,200,200,200,200,200,1200",
                        isPreset = true,
                        description = "International distress signal (3 Short, 3 Long, 3 Short)",
                        timestamp = System.currentTimeMillis() - 1000
                    )
                )

                // 2. Disco Strobe (High Frequency)
                dao.insertPattern(
                    FlashlightPattern(
                        name = "High-Freq Strobe",
                        patternString = "80,80,80,80,80,80,80,80,80,80",
                        isPreset = true,
                        description = "Fast, energetic strobe for parties or warning signaling",
                        timestamp = System.currentTimeMillis() - 2000
                    )
                )

                // 3. Heartbeat Pulse
                dao.insertPattern(
                    FlashlightPattern(
                        name = "Heartbeat",
                        patternString = "150,200,150,600",
                        isPreset = true,
                        description = "Double pulse simulating a calm resting heart rate",
                        timestamp = System.currentTimeMillis() - 3000
                    )
                )

                // 4. Beacon Signal
                dao.insertPattern(
                    FlashlightPattern(
                        name = "Lighthouse Beacon",
                        patternString = "1000,1500",
                        isPreset = true,
                        description = "Slow, high-visibility hazard caution signal",
                        timestamp = System.currentTimeMillis() - 4000
                    )
                )
            }
        }
    }
}
