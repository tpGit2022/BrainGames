package com.seeksky.braingames

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Game2048Test {
    @Test
    fun newGame_containsTwoValidTiles() {
        repeat(50) { seed ->
            val board = createGame2048(Random(seed))

            assertEquals(GAME_2048_CELL_COUNT, board.size)
            assertEquals(2, board.count { it != 0 })
            assertTrue(board.all { it == 0 || it == 2 || it == 4 })
        }
    }

    @Test
    fun mergeLine_combinesEachTileOnlyOnce() {
        assertEquals(listOf(4, 4, 0, 0) to 8, merge2048Line(listOf(2, 2, 2, 2)))
        assertEquals(listOf(4, 2, 0, 0) to 4, merge2048Line(listOf(2, 2, 2, 0)))
        assertEquals(listOf(4, 8, 0, 0) to 12, merge2048Line(listOf(2, 2, 4, 4)))
    }

    @Test
    fun moves_workInAllDirectionsWithoutChangingOriginal() {
        val board = intArrayOf(
            2, 0, 2, 2,
            0, 4, 0, 4,
            2, 0, 0, 0,
            2, 0, 0, 0,
        )

        val left = move2048(board, Game2048Direction.Left)
        val right = move2048(board, Game2048Direction.Right)
        val up = move2048(board, Game2048Direction.Up)
        val down = move2048(board, Game2048Direction.Down)

        assertArrayEquals(intArrayOf(4, 2, 0, 0, 8, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0), left.board)
        assertArrayEquals(intArrayOf(0, 0, 2, 4, 0, 0, 0, 8, 0, 0, 0, 2, 0, 0, 0, 2), right.board)
        assertArrayEquals(intArrayOf(4, 4, 2, 2, 2, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0), up.board)
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2, 4, 4, 2, 4), down.board)
        assertEquals(12, left.scoreGain)
        assertArrayEquals(intArrayOf(2, 0, 2, 2, 0, 4, 0, 4, 2, 0, 0, 0, 2, 0, 0, 0), board)
    }

    @Test
    fun unchangedMove_reportsThatNothingMoved() {
        val board = intArrayOf(
            2, 4, 8, 16,
            32, 64, 128, 256,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )

        assertFalse(move2048(board, Game2048Direction.Left).moved)
        assertTrue(move2048(board, Game2048Direction.Down).moved)
    }

    @Test
    fun winAndGameOver_areRecognized() {
        val gameOver = intArrayOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        val mergeAvailable = gameOver.copyOf().also { it[1] = 2 }
        val won = gameOver.copyOf().also { it[0] = GAME_2048_TARGET }

        assertTrue(isGameOver2048(gameOver))
        assertFalse(isGameOver2048(mergeAvailable))
        assertTrue(hasWon2048(won))
        assertFalse(hasWon2048(gameOver))
    }
}
