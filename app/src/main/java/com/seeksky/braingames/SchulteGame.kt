package com.seeksky.braingames

import kotlin.random.Random

const val MIN_GRID_SIZE = 3
const val MAX_GRID_SIZE = 8

data class TapResult(
    val nextNumber: Int,
    val isCorrect: Boolean,
    val isComplete: Boolean,
)

data class ScoreRecord(
    val gridSize: Int,
    val elapsedMillis: Long,
    val errors: Int,
    val completedAtMillis: Long,
    val id: Long = 0L,
)

fun createBoard(size: Int, random: Random = Random.Default): List<Int> {
    require(size in MIN_GRID_SIZE..MAX_GRID_SIZE) {
        "Grid size must be between $MIN_GRID_SIZE and $MAX_GRID_SIZE"
    }
    return (1..size * size).shuffled(random)
}

fun evaluateTap(expectedNumber: Int, tappedNumber: Int, cellCount: Int): TapResult {
    require(expectedNumber in 1..cellCount)
    if (tappedNumber != expectedNumber) {
        return TapResult(
            nextNumber = expectedNumber,
            isCorrect = false,
            isComplete = false,
        )
    }

    val complete = expectedNumber == cellCount
    return TapResult(
        nextNumber = if (complete) expectedNumber else expectedNumber + 1,
        isCorrect = true,
        isComplete = complete,
    )
}

fun formatDuration(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0)
    val minutes = safeMillis / 60_000
    val seconds = (safeMillis / 1_000) % 60
    val hundredths = (safeMillis % 1_000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}
