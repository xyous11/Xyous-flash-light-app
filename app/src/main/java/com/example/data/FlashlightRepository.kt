package com.example.data

import kotlinx.coroutines.flow.Flow

class FlashlightRepository(private val patternDao: FlashlightPatternDao) {
    val allPatterns: Flow<List<FlashlightPattern>> = patternDao.getAllPatterns()

    suspend fun insertPattern(pattern: FlashlightPattern): Long {
        return patternDao.insertPattern(pattern)
    }

    suspend fun deletePattern(pattern: FlashlightPattern) {
        patternDao.deletePattern(pattern)
    }

    suspend fun getPatternById(id: Int): FlashlightPattern? {
        return patternDao.getPatternById(id)
    }
}
