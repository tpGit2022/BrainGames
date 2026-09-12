package com.seeksky.braingames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuGameTest {
    @Test
    fun generatedSolution_isACompletedSudoku() {
        repeat(20) { seed ->
            val solution = generateSudokuSolution(Random(seed))

            assertTrue(isCompletedSudoku(solution))
            assertEquals((1..9).toSet(), solution.toSet())
        }
    }

    @Test
    fun generatedPuzzles_matchDifficultyAndHaveUniqueSolutions() {
        SudokuDifficulty.entries.forEach { difficulty ->
            val puzzle = generateSudokuPuzzle(difficulty, Random(100 + difficulty.ordinal))

            assertEquals(difficulty.clueCount, puzzle.cells.count { it != 0 })
            assertEquals(1, countSudokuSolutions(puzzle.cells))
            assertTrue(isCompletedSudoku(puzzle.solution))
            puzzle.cells.forEachIndexed { index, value ->
                assertTrue(value == 0 || value == puzzle.solution[index])
            }
        }
    }

    @Test
    fun solutionCounter_rejectsInvalidBoardAndAcceptsSolvedBoard() {
        val solution = generateSudokuSolution(Random(7))
        val invalid = solution.copyOf().also { it[0] = it[1] }

        assertEquals(1, countSudokuSolutions(solution))
        assertEquals(0, countSudokuSolutions(invalid))
        assertFalse(isCompletedSudoku(invalid))
    }

    @Test
    fun betterScore_prioritizesMistakesThenTime() {
        val previous = SudokuBestRecord(elapsedMillis = 120_000L, mistakes = 1)

        assertTrue(isBetterSudokuScore(SudokuBestRecord(180_000L, 0), previous))
        assertTrue(isBetterSudokuScore(SudokuBestRecord(119_999L, 1), previous))
        assertFalse(isBetterSudokuScore(SudokuBestRecord(90_000L, 2), previous))
        assertFalse(isBetterSudokuScore(SudokuBestRecord(120_001L, 1), previous))
    }

    @Test
    fun scoreCodec_roundTripsValidRecordsAndSkipsInvalidValues() {
        val records = listOf(
            SudokuScoreRecord(SudokuDifficulty.Easy, 90_000L, 0, 100L),
            SudokuScoreRecord(SudokuDifficulty.Hell, 240_000L, 3, 200L),
        )

        assertEquals(records, SudokuScoreCodec.decode(SudokuScoreCodec.encode(records)))
        assertTrue(SudokuScoreCodec.decode("Unknown,1000,0,100;Easy,-1,0,100").isEmpty())
    }
}
