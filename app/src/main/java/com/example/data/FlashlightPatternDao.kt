package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashlightPatternDao {
    @Query("SELECT * FROM flashlight_patterns ORDER BY isPreset DESC, timestamp DESC")
    fun getAllPatterns(): Flow<List<FlashlightPattern>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: FlashlightPattern): Long

    @Delete
    suspend fun deletePattern(pattern: FlashlightPattern)

    @Query("SELECT * FROM flashlight_patterns WHERE id = :id LIMIT 1")
    suspend fun getPatternById(id: Int): FlashlightPattern?

    @Query("SELECT COUNT(*) FROM flashlight_patterns")
    suspend fun getCount(): Int
}
