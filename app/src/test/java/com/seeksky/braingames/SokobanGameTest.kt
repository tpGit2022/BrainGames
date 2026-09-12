package com.seeksky.braingames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class SokobanGameTest {
    @Test
    fun levels_haveMatchingBoxesAndTargetsAndAreSolvable() {
        assertEquals(8, SOKOBAN_LEVELS.size)
        SOKOBAN_LEVELS.forEachIndexed { levelIndex, level ->
            assertEquals(level.targets.size, level.initialBoxes.size)
            assertTrue("Level ${level.number} has no solution", canSolve(levelIndex))
        }
    }

    @Test
    fun playerCanWalkButCannotCrossWalls() {
        val state = createSokobanState(0)

        val walk = attemptSokobanMove(state, SokobanDirection.Up)
        val wall = attemptSokobanMove(walk.state, SokobanDirection.Up)

        assertTrue(walk.moved)
        assertFalse(walk.pushed)
        assertFalse(wall.moved)
    }

    @Test
    fun pushingBoxUpdatesMovesPushesAndCompletion() {
        val state = createSokobanState(0)

        val result = attemptSokobanMove(state, SokobanDirection.Left)

        assertTrue(result.moved)
        assertTrue(result.pushed)
        assertEquals(1, result.state.moves)
        assertEquals(1, result.state.pushes)
        assertTrue(isSokobanSolved(result.state))
    }

    @Test
    fun boxCannotBePushedThroughAnotherBoxOrWall() {
        val level = SOKOBAN_LEVELS[1]
        val blockedByWall = SokobanState(
            levelIndex = 1,
            player = SokobanPosition(1, 2),
            boxes = setOf(SokobanPosition(1, 1), SokobanPosition(3, 5)),
        )

        val result = attemptSokobanMove(blockedByWall, SokobanDirection.Left)

        assertFalse(result.moved)
        assertEquals(level.targets.size, result.state.boxes.size)
    }

    @Test
    fun betterScorePrioritizesPushesThenMovesThenTime() {
        val previous = SokobanBestRecord(moves = 40, pushes = 12, elapsedMillis = 60_000L)

        assertTrue(isBetterSokobanScore(SokobanBestRecord(50, 11, 90_000L), previous))
        assertTrue(isBetterSokobanScore(SokobanBestRecord(39, 12, 90_000L), previous))
        assertTrue(isBetterSokobanScore(SokobanBestRecord(40, 12, 59_999L), previous))
        assertFalse(isBetterSokobanScore(SokobanBestRecord(30, 13, 30_000L), previous))
    }

    @Test
    fun scoreCodecRoundTripsAndRejectsInvalidRecords() {
        val records = listOf(
            SokobanScoreRecord(1, 12, 3, 10_000L, 100L),
            SokobanScoreRecord(8, 90, 25, 80_000L, 200L),
        )

        assertEquals(records, SokobanScoreCodec.decode(SokobanScoreCodec.encode(records)))
        assertTrue(SokobanScoreCodec.decode("0,10,2,1000,100;1,2,3,1000,100").isEmpty())
    }

    @Test
    fun metricPointsFilterLevelAndUseChronologicalOrder() {
        val records = listOf(
            SokobanScoreRecord(2, 40, 12, 20_000L, 300L),
            SokobanScoreRecord(1, 18, 6, 8_000L, 200L),
            SokobanScoreRecord(2, 35, 10, 15_000L, 100L),
        )

        val points = buildSokobanMetricPoints(records, 2)

        assertEquals(2, points.size)
        assertEquals(10f, points[0].pushes)
        assertEquals(12f, points[1].pushes)
        assertEquals(15f, points[0].elapsedSeconds)
        assertEquals(listOf(1, 2), points.map { it.sessionNumber })
    }

    private fun canSolve(levelIndex: Int): Boolean {
        val level = SOKOBAN_LEVELS[levelIndex]
        val initial = createSokobanState(levelIndex)
        val pending = ArrayDeque<SearchState>()
        val visited = mutableSetOf<SearchState>()
        val initialSearchState = canonicalState(initial.player, initial.boxes, level)
        pending += initialSearchState
        visited += initialSearchState

        while (pending.isNotEmpty() && visited.size < 2_000) {
            val current = pending.removeFirst()
            if (current.boxes == level.targets) return true
            val reachable = reachablePositions(current.player, current.boxes, level)
            current.boxes.forEach { box ->
                SokobanDirection.entries.forEach { direction ->
                    val destination = box.move(direction)
                    val standingPosition = SokobanPosition(
                        box.row - direction.rowOffset,
                        box.column - direction.columnOffset,
                    )
                    if (
                        standingPosition in reachable &&
                        destination !in level.walls &&
                        destination !in current.boxes &&
                        destination.row in 0 until level.height &&
                        destination.column in 0 until level.width
                    ) {
                        val boxes = current.boxes - box + destination
                        val next = canonicalState(box, boxes, level)
                        if (visited.add(next)) pending += next
                    }
                }
            }
        }
        return false
    }

    private fun canonicalState(
        player: SokobanPosition,
        boxes: Set<SokobanPosition>,
        level: SokobanLevel,
    ): SearchState {
        val representative = reachablePositions(player, boxes, level)
            .minWith(compareBy<SokobanPosition> { it.row }.thenBy { it.column })
        return SearchState(representative, boxes)
    }

    private fun reachablePositions(
        player: SokobanPosition,
        boxes: Set<SokobanPosition>,
        level: SokobanLevel,
    ): Set<SokobanPosition> {
        val pending = ArrayDeque<SokobanPosition>()
        val visited = mutableSetOf<SokobanPosition>()
        pending += player
        while (pending.isNotEmpty()) {
            val position = pending.removeFirst()
            if (!visited.add(position)) continue
            SokobanDirection.entries.forEach { direction ->
                val next = position.move(direction)
                if (
                    next.row in 0 until level.height &&
                    next.column in 0 until level.width &&
                    next !in level.walls &&
                    next !in boxes &&
                    next !in visited
                ) {
                    pending += next
                }
            }
        }
        return visited
    }

    private data class SearchState(
        val player: SokobanPosition,
        val boxes: Set<SokobanPosition>,
    )
}
