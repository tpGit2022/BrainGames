package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit
import kotlin.random.Random

const val SUDOKU_SIZE = 9
const val SUDOKU_CELL_COUNT = SUDOKU_SIZE * SUDOKU_SIZE

enum class SudokuDifficulty(
    val title: String,
    val clueCount: Int,
    val description: String,
) {
    Easy("简单", 42, "42 个提示 · 轻松入门"),
    Medium("中等", 36, "36 个提示 · 稳步推理"),
    Hard("困难", 30, "30 个提示 · 深度思考"),
    Hell("地狱", 25, "25 个提示 · 极限挑战"),
}

data class SudokuPuzzle(
    val cells: IntArray,
    val solution: IntArray,
)

data class SudokuBestRecord(
    val elapsedMillis: Long,
    val mistakes: Int,
)

data class SudokuScoreRecord(
    val difficulty: SudokuDifficulty,
    val elapsedMillis: Long,
    val mistakes: Int,
    val completedAtMillis: Long,
)

fun generateSudokuPuzzle(
    difficulty: SudokuDifficulty,
    random: Random = Random.Default,
): SudokuPuzzle {
    while (true) {
        val solution = generateSudokuSolution(random)
        val puzzle = solution.copyOf()
        val positions = (0 until SUDOKU_CELL_COUNT).shuffled(random)
        var clues = SUDOKU_CELL_COUNT

        for (position in positions) {
            if (clues == difficulty.clueCount) break
            val value = puzzle[position]
            puzzle[position] = 0
            if (countSudokuSolutions(puzzle, limit = 2) == 1) {
                clues -= 1
            } else {
                puzzle[position] = value
            }
        }

        if (clues == difficulty.clueCount) return SudokuPuzzle(puzzle, solution)
    }
}

fun generateSudokuSolution(random: Random = Random.Default): IntArray {
    val rowBands = (0..2).shuffled(random)
    val columnStacks = (0..2).shuffled(random)
    val rows = rowBands.flatMap { band ->
        (0..2).shuffled(random).map { row -> band * 3 + row }
    }
    val columns = columnStacks.flatMap { stack ->
        (0..2).shuffled(random).map { column -> stack * 3 + column }
    }
    val digits = (1..9).shuffled(random)

    return IntArray(SUDOKU_CELL_COUNT) { index ->
        val row = rows[index / SUDOKU_SIZE]
        val column = columns[index % SUDOKU_SIZE]
        digits[(row * 3 + row / 3 + column) % SUDOKU_SIZE]
    }
}

fun countSudokuSolutions(cells: IntArray, limit: Int = 2): Int {
    require(cells.size == SUDOKU_CELL_COUNT) { "A Sudoku board must contain 81 cells" }
    require(limit > 0) { "Solution limit must be positive" }
    val board = cells.copyOf()
    if (!hasValidSudokuGivens(board)) return 0
    return solveAndCount(board, limit)
}

fun isCompletedSudoku(cells: IntArray): Boolean =
    cells.size == SUDOKU_CELL_COUNT && cells.none { it == 0 } && hasValidSudokuGivens(cells)

fun isBetterSudokuScore(
    candidate: SudokuBestRecord,
    previous: SudokuBestRecord?,
): Boolean = previous == null ||
    candidate.mistakes < previous.mistakes ||
    (candidate.mistakes == previous.mistakes && candidate.elapsedMillis < previous.elapsedMillis)

private fun solveAndCount(board: IntArray, limit: Int): Int {
    var bestPosition = -1
    var bestCandidates = 10

    for (position in board.indices) {
        if (board[position] != 0) continue
        val candidates = candidateMask(board, position)
        val candidateCount = Integer.bitCount(candidates)
        if (candidateCount == 0) return 0
        if (candidateCount < bestCandidates) {
            bestCandidates = candidateCount
            bestPosition = position
            if (candidateCount == 1) break
        }
    }

    if (bestPosition == -1) return 1
    var solutions = 0
    var candidates = candidateMask(board, bestPosition)
    while (candidates != 0 && solutions < limit) {
        val bit = candidates and -candidates
        board[bestPosition] = Integer.numberOfTrailingZeros(bit)
        solutions += solveAndCount(board, limit - solutions)
        board[bestPosition] = 0
        candidates = candidates xor bit
    }
    return solutions
}

private fun candidateMask(board: IntArray, position: Int): Int {
    val row = position / SUDOKU_SIZE
    val column = position % SUDOKU_SIZE
    var used = 0
    for (index in 0 until SUDOKU_SIZE) {
        used = used or (1 shl board[row * SUDOKU_SIZE + index])
        used = used or (1 shl board[index * SUDOKU_SIZE + column])
    }
    val boxRow = row / 3 * 3
    val boxColumn = column / 3 * 3
    for (rowOffset in 0..2) {
        for (columnOffset in 0..2) {
            used = used or (1 shl board[(boxRow + rowOffset) * SUDOKU_SIZE + boxColumn + columnOffset])
        }
    }
    return ALL_DIGITS_MASK and used.inv()
}

private fun hasValidSudokuGivens(board: IntArray): Boolean {
    for (position in board.indices) {
        val value = board[position]
        if (value !in 0..9) return false
        if (value == 0) continue
        board[position] = 0
        val valid = candidateMask(board, position) and (1 shl value) != 0
        board[position] = value
        if (!valid) return false
    }
    return true
}

class SudokuScoreStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sudoku_game_scores",
        Context.MODE_PRIVATE,
    )

    fun getBest(difficulty: SudokuDifficulty): SudokuBestRecord? {
        val elapsedKey = "${difficulty.name}_elapsed"
        val mistakesKey = "${difficulty.name}_mistakes"
        if (!preferences.contains(elapsedKey) || !preferences.contains(mistakesKey)) return null
        return SudokuBestRecord(
            elapsedMillis = preferences.getLong(elapsedKey, 0L),
            mistakes = preferences.getInt(mistakesKey, 0),
        )
    }

    fun getScores(): List<SudokuScoreRecord> = SudokuScoreCodec
        .decode(preferences.getString(KEY_SCORES, null).orEmpty())
        .sortedByDescending { it.completedAtMillis }

    fun add(score: SudokuScoreRecord): Boolean {
        val candidate = SudokuBestRecord(score.elapsedMillis, score.mistakes)
        val isNewBest = isBetterSudokuScore(candidate, getBest(score.difficulty))
        preferences.edit {
            putString(KEY_SCORES, SudokuScoreCodec.encode(getScores() + score))
            if (isNewBest) {
                putLong("${score.difficulty.name}_elapsed", score.elapsedMillis)
                putInt("${score.difficulty.name}_mistakes", score.mistakes)
            }
        }
        return isNewBest
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_SCORES = "score_records"
    }
}

internal object SudokuScoreCodec {
    fun encode(scores: List<SudokuScoreRecord>): String = scores.joinToString(";") { score ->
        listOf(
            score.difficulty.name,
            score.elapsedMillis,
            score.mistakes,
            score.completedAtMillis,
        ).joinToString(",")
    }

    fun decode(value: String): List<SudokuScoreRecord> {
        if (value.isBlank()) return emptyList()
        return value.split(';').mapNotNull { encodedScore ->
            val values = encodedScore.split(',')
            if (values.size != 4) return@mapNotNull null
            val difficulty = SudokuDifficulty.entries.find { it.name == values[0] }
                ?: return@mapNotNull null
            val elapsedMillis = values[1].toLongOrNull() ?: return@mapNotNull null
            val mistakes = values[2].toIntOrNull() ?: return@mapNotNull null
            val completedAtMillis = values[3].toLongOrNull() ?: return@mapNotNull null
            if (elapsedMillis < 0L || mistakes < 0 || completedAtMillis < 0L) {
                return@mapNotNull null
            }
            SudokuScoreRecord(difficulty, elapsedMillis, mistakes, completedAtMillis)
        }
    }
}

private const val ALL_DIGITS_MASK = 0b11_1111_1110
