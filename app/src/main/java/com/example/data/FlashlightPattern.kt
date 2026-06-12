package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flashlight_patterns")
data class FlashlightPattern(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val patternString: String, // Comma-separated list of durations: "on_ms,off_ms,on_ms,off_ms..."
    val isPreset: Boolean = false,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
