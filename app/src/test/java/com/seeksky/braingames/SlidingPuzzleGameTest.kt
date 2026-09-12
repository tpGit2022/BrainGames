package com.seeksky.braingames

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlidingPuzzleGameTest {
    @Test
    fun generatedBoards_areShuffledValidAndSolvable() {
        repeat(100) { seed ->
            val board = createSlidingPuzzle(Random(seed))

            assertEquals((0..8).toList(), board.sorted())
            assertFalse(isSlidingPuzzleSolved(board))
            assertTrue(isSolvableSlidingPuzzle(board))
        }
    }

    @Test
    fun onlyTilesNextToTheEmptyCellCanMove() {
        val board = intArrayOf(
            1, 2, 3,
            4, 0, 5,
            6, 7, 8,
        )

        assertTrue(canSlideTile(board, 1))
        assertTrue(canSlideTile(board, 3))
        assertTrue(canSlideTile(board, 5))
        assertTrue(canSlideTile(board, 7))
        assertFalse(canSlideTile(board, 0))
        assertFalse(canSlideTile(board, 4))
        assertFalse(canSlideTile(board, 8))
    }

    @Test
    fun slidingTile_returnsNewBoardWithoutChangingOriginal() {
        val board = intArrayOf(
            1, 2, 3,
            4, 5, 6,
            7, 0, 8,
        )

        val moved = slideTile(board, 8)

        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 0), moved)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6, 7, 0, 8), board)
        assertNull(slideTile(board, 0))
    }

    @Test
    fun solvedAndUnsolvableBoards_areRecognized() {
        val unsolvable = intArrayOf(2, 1, 3, 4, 5, 6, 7, 8, 0)

        assertTrue(isSlidingPuzzleSolved(SLIDING_PUZZLE_SOLUTION))
        assertTrue(isSolvableSlidingPuzzle(SLIDING_PUZZLE_SOLUTION))
        assertFalse(isSolvableSlidingPuzzle(unsolvable))
    }

    @Test
    fun betterScore_prioritizesMovesThenTime() {
        val previous = SlidingPuzzleBestRecord(moves = 80, elapsedMillis = 60_000L)

        assertTrue(isBetterSlidingPuzzleScore(SlidingPuzzleBestRecord(79, 90_000L), previous))
        assertTrue(isBetterSlidingPuzzleScore(SlidingPuzzleBestRecord(80, 59_999L), previous))
        assertFalse(isBetterSlidingPuzzleScore(SlidingPuzzleBestRecord(81, 40_000L), previous))
        assertFalse(isBetterSlidingPuzzleScore(SlidingPuzzleBestRecord(80, 60_001L), previous))
    }

    @Test
    fun scoreCodec_roundTripsRecordsAndSkipsInvalidValues() {
        val records = listOf(
            SlidingPuzzleScoreRecord(48, 30_000L, 100L),
            SlidingPuzzleScoreRecord(90, 70_000L, 200L),
        )

        assertEquals(records, SlidingPuzzleScoreCodec.decode(SlidingPuzzleScoreCodec.encode(records)))
        assertTrue(SlidingPuzzleScoreCodec.decode("0,1000,100;20,-1,100").isEmpty())
    }

    @Test
    fun metricPoints_orderSessionsAndConvertValues() {
        val records = listOf(
            SlidingPuzzleScoreRecord(48, 30_000L, 300L),
            SlidingPuzzleScoreRecord(80, 70_000L, 100L),
            SlidingPuzzleScoreRecord(60, 50_000L, 200L),
        )

        val points = buildSlidingPuzzleMetricPoints(records)

        assertEquals(listOf(1, 2, 3), points.map { it.sessionNumber })
        assertEquals(listOf(80f, 60f, 48f), points.map { it.moves })
        assertEquals(listOf(70f, 50f, 30f), points.map { it.elapsedSeconds })
    }
}
