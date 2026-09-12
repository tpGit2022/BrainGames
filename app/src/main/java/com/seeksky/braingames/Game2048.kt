package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit
import kotlin.random.Random

const val GAME_2048_SIZE = 4
const val GAME_2048_CELL_COUNT = GAME_2048_SIZE * GAME_2048_SIZE
const val GAME_2048_TARGET = 2048

enum class Game2048Direction { Up, Down, Left, Right }

data class Game2048MoveResult(
    val board: IntArray,
    val scoreGain: Int,
    val moved: Boolean,
)

fun createGame2048(random: Random = Random.Default): IntArray =
    addRandom2048Tile(addRandom2048Tile(IntArray(GAME_2048_CELL_COUNT), random), random)

fun addRandom2048Tile(board: IntArray, random: Random = Random.Default): IntArray {
    requireValid2048Board(board)
    val emptyPositions = board.indices.filter { board[it] == 0 }
    if (emptyPositions.isEmpty()) return board.copyOf()

    return board.copyOf().also { newBoard ->
        val position = emptyPositions[random.nextInt(emptyPositions.size)]
        newBoard[position] = if (random.nextInt(10) == 0) 4 else 2
    }
}

fun move2048(board: IntArray, direction: Game2048Direction): Game2048MoveResult {
    requireValid2048Board(board)
    val movedBoard = IntArray(GAME_2048_CELL_COUNT)
    var scoreGain = 0

    repeat(GAME_2048_SIZE) { line ->
        val positions = linePositions2048(line, direction)
        val (merged, lineScore) = merge2048Line(positions.map { board[it] })
        positions.forEachIndexed { index, position -> movedBoard[position] = merged[index] }
        scoreGain += lineScore
    }

    return Game2048MoveResult(
        board = movedBoard,
        scoreGain = scoreGain,
        moved = !movedBoard.contentEquals(board),
    )
}

fun merge2048Line(line: List<Int>): Pair<List<Int>, Int> {
    require(line.size == GAME_2048_SIZE) { "A 2048 line must contain four cells" }
    require(line.all { it == 0 || isPowerOfTwo(it) }) { "Tiles must be zero or powers of two" }

    val compacted = line.filter { it != 0 }
    val merged = mutableListOf<Int>()
    var score = 0
    var index = 0
    while (index < compacted.size) {
        if (index + 1 < compacted.size && compacted[index] == compacted[index + 1]) {
            val value = compacted[index] * 2
            merged += value
            score += value
            index += 2
        } else {
            merged += compacted[index]
            index += 1
        }
    }
    while (merged.size < GAME_2048_SIZE) merged += 0
    return merged to score
}

fun hasWon2048(board: IntArray): Boolean = board.any { it >= GAME_2048_TARGET }

fun isGameOver2048(board: IntArray): Boolean {
    requireValid2048Board(board)
    if (board.any { it == 0 }) return false
    return Game2048Direction.entries.none { move2048(board, it).moved }
}

private fun linePositions2048(line: Int, direction: Game2048Direction): List<Int> =
    when (direction) {
        Game2048Direction.Left -> List(GAME_2048_SIZE) { line * GAME_2048_SIZE + it }
        Game2048Direction.Right -> List(GAME_2048_SIZE) { line * GAME_2048_SIZE + GAME_2048_SIZE - 1 - it }
        Game2048Direction.Up -> List(GAME_2048_SIZE) { it * GAME_2048_SIZE + line }
        Game2048Direction.Down -> List(GAME_2048_SIZE) { (GAME_2048_SIZE - 1 - it) * GAME_2048_SIZE + line }
    }

private fun requireValid2048Board(board: IntArray) {
    require(board.size == GAME_2048_CELL_COUNT) { "A 2048 board must contain 16 cells" }
    require(board.all { it == 0 || isPowerOfTwo(it) }) { "Tiles must be zero or powers of two" }
}

private fun isPowerOfTwo(value: Int): Boolean = value > 0 && value and (value - 1) == 0

class Game2048ScoreStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "game_2048_scores",
        Context.MODE_PRIVATE,
    )

    fun getBest(): Int = preferences.getInt(KEY_BEST_SCORE, 0)

    fun updateBest(score: Int): Boolean {
        if (score <= getBest()) return false
        preferences.edit { putInt(KEY_BEST_SCORE, score) }
        return true
    }

    private companion object {
        const val KEY_BEST_SCORE = "best_score"
    }
}
