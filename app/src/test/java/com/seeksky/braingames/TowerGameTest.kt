package com.seeksky.braingames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.PriorityQueue

class TowerGameTest {
    @Test
    fun floors_haveValidDimensionsSymbolsAndStairs() {
        assertEquals(TOWER_FLOOR_COUNT, TOWER_FLOORS.size)
        TOWER_FLOORS.forEachIndexed { index, floor ->
            assertEquals(index + 1, floor.number)
            assertEquals(TOWER_GRID_SIZE, floor.rows.size)
            floor.rows.forEach { row ->
                assertEquals(TOWER_GRID_SIZE, row.length)
                row.forEach { assertNotNull(TowerTile.fromSymbol(it)) }
            }
            if (floor.number < TOWER_FLOOR_COUNT) {
                assertTrue(floor.rows.any { TowerTile.UpStairs.symbol in it })
            }
            if (floor.number > 1) {
                assertTrue(floor.rows.any { TowerTile.DownStairs.symbol in it })
            }
        }
    }

    @Test
    fun battleDamage_usesDeterministicClassicFormula() {
        val stats = TowerStats(attack = 10, defense = 10)

        assertEquals(32, calculateTowerBattleDamage(stats, TowerMonster.Slime))
        assertNull(calculateTowerBattleDamage(stats.copy(attack = 2), TowerMonster.Slime))
        assertEquals(0, calculateTowerBattleDamage(stats.copy(defense = 100), TowerMonster.Slime))
    }

    @Test
    fun collectingItem_updatesStatsAndClearsMapCell() {
        val state = TowerGameState(position = TowerPosition(8, 1))

        val result = attemptTowerMove(state, TowerDirection.Right)

        assertTrue(result.changed)
        assertTrue(result.state.hasMonsterBook)
        assertEquals(TowerPosition(8, 2), result.state.position)
        assertEquals(TowerTile.Floor, towerTileAt(result.state, result.state.position))
    }

    @Test
    fun doors_requireAndConsumeMatchingKey() {
        val state = TowerGameState(position = TowerPosition(3, 3))

        val blocked = attemptTowerMove(state, TowerDirection.Right)
        val opened = attemptTowerMove(
            state.copy(stats = state.stats.copy(yellowKeys = 1)),
            TowerDirection.Right,
        )

        assertFalse(blocked.changed)
        assertTrue(opened.changed)
        assertEquals(0, opened.state.stats.yellowKeys)
        assertEquals(TowerTile.Floor, towerTileAt(opened.state, TowerPosition(3, 4)))
    }

    @Test
    fun winningBattle_awardsResourcesAndMovesPlayer() {
        val state = TowerGameState(position = TowerPosition(2, 1))

        val result = attemptTowerMove(state, TowerDirection.Right)

        assertTrue(result.changed)
        assertTrue(result.event is TowerEvent.Battle)
        assertEquals(968, result.state.stats.hp)
        assertEquals(3, result.state.stats.gold)
        assertEquals(1, result.state.stats.experience)
    }

    @Test
    fun stairsMovePlayerToCounterpartOnAdjacentFloor() {
        val state = TowerGameState(position = TowerPosition(1, 8))

        val result = attemptTowerMove(state, TowerDirection.Right)

        assertTrue(result.changed)
        assertEquals(2, result.state.floor)
        assertEquals(TowerPosition(9, 1), result.state.position)
        assertEquals(2, result.state.highestFloor)
    }

    @Test
    fun shopPriceIncreasesAndPurchaseUpdatesChosenStat() {
        val state = TowerGameState(stats = TowerStats(gold = 50))

        val result = buyTowerUpgrade(state, TowerUpgrade.Attack)

        assertTrue(result.purchased)
        assertEquals(30, result.state.stats.gold)
        assertEquals(13, result.state.stats.attack)
        assertEquals(30, towerShopCost(result.state))
    }

    @Test
    fun duration_formatsMinutesAndHours() {
        assertEquals("01:05", formatTowerDuration(65_000L))
        assertEquals("1:01:01", formatTowerDuration(3_661_000L))
    }

    @Test
    fun campaign_hasACompleteWinningRoute() {
        val pending = PriorityQueue<TowerGameState>(
            compareByDescending<TowerGameState> { it.highestFloor }
                .thenByDescending { it.clearedTiles.size }
                .thenByDescending { it.stats.attack + it.stats.defense }
                .thenByDescending { it.stats.hp }
                .thenByDescending { it.stats.yellowKeys + it.stats.blueKeys + it.stats.redKeys },
        )
        val visited = mutableSetOf<TowerGameState>()
        pending += TowerGameState()

        while (pending.isNotEmpty() && visited.size < 500_000) {
            val state = pending.remove()
            if (!visited.add(state)) continue

            TowerDirection.entries.forEach { direction ->
                val result = attemptTowerMove(state, direction)
                if (result.event == TowerEvent.Victory) return
                if (result.changed && result.state !in visited) pending += result.state
                if (result.event == TowerEvent.OpenShop) {
                    TowerUpgrade.entries.forEach { upgrade ->
                        val purchase = buyTowerUpgrade(state, upgrade)
                        if (purchase.purchased && purchase.state !in visited) pending += purchase.state
                    }
                }
            }
        }

        throw AssertionError("No winning route found after exploring ${visited.size} states")
    }
}
