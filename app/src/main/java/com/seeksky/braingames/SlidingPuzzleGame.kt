package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.random.Random

const val SLIDING_PUZZLE_SIZE = 3
const val SLIDING_PUZZLE_CELL_COUNT = SLIDING_PUZZLE_SIZE * SLIDING_PUZZLE_SIZE
const val SLIDING_PUZZLE_EMPTY = 0

val SLIDING_PUZZLE_SOLUTION: IntArray = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 0)

data class SlidingPuzzleBestRecord(
    val moves: Int,
    val elapsedMillis: Long,
)

data class SlidingPuzzleScoreRecord(
    val moves: Int,
    val elapsedMillis: Long,
    val completedAtMillis: Long,
)

fun createSlidingPuzzle(
    random: Random = Random.Default,
    shuffleMoves: Int = DEFAULT_SHUFFLE_MOVES,
): IntArray {
    require(shuffleMoves > 0) { "Shuffle moves must be positive" }

    var board: IntArray
    do {
        board = SLIDING_PUZZLE_SOLUTION.copyOf()
        var emptyPosition = board.lastIndex
        var previousEmptyPosition = -1

        repeat(shuffleMoves) {
            val candidates = adjacentPositions(emptyPosition)
            val preferredCandidates = candidates.filter { it != previousEmptyPosition }
            val tilePosition = (preferredCandidates.ifEmpty { candidates }).random(random)
            board[emptyPosition] = board[tilePosition]
            board[tilePosition] = SLIDING_PUZZLE_EMPTY
            previousEmptyPosition = emptyPosition
            emptyPosition = tilePosition
        }
    } while (isSlidingPuzzleSolved(board))

    return board
}

fun canSlideTile(board: IntArray, tilePosition: Int): Boolean {
    requireValidSlidingBoard(board)
    if (tilePosition !in board.indices || board[tilePosition] == SLIDING_PUZZLE_EMPTY) return false
    val emptyPosition = board.indexOf(SLIDING_PUZZLE_EMPTY)
    val rowDistance = abs(tilePosition / SLIDING_PUZZLE_SIZE - emptyPosition / SLIDING_PUZZLE_SIZE)
    val columnDistance = abs(tilePosition % SLIDING_PUZZLE_SIZE - emptyPosition % SLIDING_PUZZLE_SIZE)
    return rowDistance + columnDistance == 1
}

fun slideTile(board: IntArray, tilePosition: Int): IntArray? {
    if (!canSlideTile(board, tilePosition)) return null
    val emptyPosition = board.indexOf(SLIDING_PUZZLE_EMPTY)
    return board.copyOf().also { movedBoard ->
        movedBoard[emptyPosition] = movedBoard[tilePosition]
        movedBoard[tilePosition] = SLIDING_PUZZLE_EMPTY
    }
}

fun isSlidingPuzzleSolved(board: IntArray): Boolean =
    board.size == SLIDING_PUZZLE_CELL_COUNT && board.contentEquals(SLIDING_PUZZLE_SOLUTION)

fun isSolvableSlidingPuzzle(board: IntArray): Boolean {
    if (!isValidSlidingBoard(board)) return false
    val values = board.filter { it != SLIDING_PUZZLE_EMPTY }
    var inversions = 0
    for (first in values.indices) {
        for (second in first + 1 until values.size) {
            if (values[first] > values[second]) inversions += 1
        }
    }
    return inversions % 2 == 0
}

fun isBetterSlidingPuzzleScore(
    candidate: SlidingPuzzleBestRecord,
    previous: SlidingPuzzleBestRecord?,
): Boolean = previous == null ||
    candidate.moves < previous.moves ||
    (candidate.moves == previous.moves && candidate.elapsedMillis < previous.elapsedMillis)

private fun adjacentPositions(position: Int): List<Int> = buildList {
    val row = position / SLIDING_PUZZLE_SIZE
    val column = position % SLIDING_PUZZLE_SIZE
    if (row > 0) add(position - SLIDING_PUZZLE_SIZE)
    if (row < SLIDING_PUZZLE_SIZE - 1) add(position + SLIDING_PUZZLE_SIZE)
    if (column > 0) add(position - 1)
    if (column < SLIDING_PUZZLE_SIZE - 1) add(position + 1)
}

private fun requireValidSlidingBoard(board: IntArray) {
    require(isValidSlidingBoard(board)) { "Board must contain each value from 0 to 8 exactly once" }
}

private fun isValidSlidingBoard(board: IntArray): Boolean =
    board.size == SLIDING_PUZZLE_CELL_COUNT && board.sorted() == (0..8).toList()

class SlidingPuzzleScoreStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sliding_puzzle_scores",
        Context.MODE_PRIVATE,
    )

    fun getBest(): SlidingPuzzleBestRecord? {
        if (!preferences.contains(KEY_BEST_MOVES) || !preferences.contains(KEY_BEST_ELAPSED)) {
            return null
        }
        return SlidingPuzzleBestRecord(
            moves = preferences.getInt(KEY_BEST_MOVES, 0),
            elapsedMillis = preferences.getLong(KEY_BEST_ELAPSED, 0L),
        )
    }

    fun getScores(): List<SlidingPuzzleScoreRecord> = SlidingPuzzleScoreCodec
        .decode(preferences.getString(KEY_SCORES, null).orEmpty())
        .sortedByDescending { it.completedAtMillis }

    fun add(score: SlidingPuzzleScoreRecord): Boolean {
        val candidate = SlidingPuzzleBestRecord(score.moves, score.elapsedMillis)
        val isNewBest = isBetterSlidingPuzzleScore(candidate, getBest())
        preferences.edit {
            putString(KEY_SCORES, SlidingPuzzleScoreCodec.encode(getScores() + score))
            if (isNewBest) {
                putInt(KEY_BEST_MOVES, score.moves)
                putLong(KEY_BEST_ELAPSED, score.elapsedMillis)
            }
        }
        return isNewBest
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_BEST_MOVES = "best_moves"
        const val KEY_BEST_ELAPSED = "best_elapsed"
        const val KEY_SCORES = "score_records"
    }
}

internal object SlidingPuzzleScoreCodec {
    fun encode(scores: List<SlidingPuzzleScoreRecord>): String = scores.joinToString(";") { score ->
        listOf(score.moves, score.elapsedMillis, score.completedAtMillis).joinToString(",")
    }

    fun decode(value: String): List<SlidingPuzzleScoreRecord> {
        if (value.isBlank()) return emptyList()
        return value.split(';').mapNotNull { encodedScore ->
            val values = encodedScore.split(',')
            if (values.size != 3) return@mapNotNull null
            val moves = values[0].toIntOrNull() ?: return@mapNotNull null
            val elapsedMillis = values[1].toLongOrNull() ?: return@mapNotNull null
            val completedAtMillis = values[2].toLongOrNull() ?: return@mapNotNull null
            if (moves <= 0 || elapsedMillis < 0L || completedAtMillis < 0L) return@mapNotNull null
            SlidingPuzzleScoreRecord(moves, elapsedMillis, completedAtMillis)
        }
    }
}

private const val DEFAULT_SHUFFLE_MOVES = 160
