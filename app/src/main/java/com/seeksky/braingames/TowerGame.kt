package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit
import kotlin.math.max

const val TOWER_GRID_SIZE = 11
const val TOWER_FLOOR_COUNT = 5

enum class TowerDirection(val rowOffset: Int, val columnOffset: Int) {
    Up(-1, 0),
    Down(1, 0),
    Left(0, -1),
    Right(0, 1),
}

enum class TowerTile(val symbol: Char) {
    Floor('.'),
    Wall('#'),
    YellowDoor('Y'),
    BlueDoor('C'),
    RedDoor('R'),
    UpStairs('u'),
    DownStairs('d'),
    YellowKey('y'),
    BlueKey('c'),
    RedKey('r'),
    RedPotion('h'),
    BluePotion('H'),
    AttackGem('a'),
    DefenseGem('f'),
    Sword('s'),
    Shield('S'),
    MonsterBook('B'),
    Shop('$'),
    Npc('N'),
    Exit('E'),
    Slime('g'),
    Bat('t'),
    Skeleton('o'),
    Knight('p'),
    Wizard('w'),
    Boss('x');

    companion object {
        fun fromSymbol(symbol: Char): TowerTile = entries.firstOrNull { it.symbol == symbol }
            ?: error("Unknown tower tile: $symbol")
    }
}

enum class TowerMonster(
    val tile: TowerTile,
    val title: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val gold: Int,
    val experience: Int,
) {
    Slime(TowerTile.Slime, "绿色史莱姆", 40, 18, 2, 3, 1),
    Bat(TowerTile.Bat, "小蝙蝠", 55, 22, 5, 5, 2),
    Skeleton(TowerTile.Skeleton, "骷髅士兵", 90, 28, 8, 8, 4),
    Knight(TowerTile.Knight, "铁甲卫兵", 120, 36, 12, 12, 6),
    Wizard(TowerTile.Wizard, "初级法师", 110, 42, 16, 16, 8),
    Boss(TowerTile.Boss, "魔王", 220, 50, 24, 50, 30);

    companion object {
        fun fromTile(tile: TowerTile): TowerMonster? = entries.firstOrNull { it.tile == tile }
    }
}

enum class TowerUpgrade(val title: String) {
    Health("生命 +400"),
    Attack("攻击 +3"),
    Defense("防御 +3"),
}

data class TowerPosition(val row: Int, val column: Int)

data class TowerStats(
    val hp: Int = 1_000,
    val attack: Int = 10,
    val defense: Int = 10,
    val gold: Int = 0,
    val experience: Int = 0,
    val yellowKeys: Int = 0,
    val blueKeys: Int = 0,
    val redKeys: Int = 0,
)

data class TowerGameState(
    val floor: Int = 1,
    val position: TowerPosition = TowerPosition(9, 1),
    val stats: TowerStats = TowerStats(),
    val clearedTiles: Set<String> = emptySet(),
    val shopPurchases: Int = 0,
    val hasMonsterBook: Boolean = false,
    val highestFloor: Int = 1,
)

data class TowerFloor(
    val number: Int,
    val name: String,
    val rows: List<String>,
)

sealed interface TowerEvent {
    data class Message(val text: String) : TowerEvent
    data class Battle(val monster: TowerMonster, val damage: Int) : TowerEvent
    data class FloorChanged(val floor: Int) : TowerEvent
    data object OpenShop : TowerEvent
    data object Victory : TowerEvent
    data object None : TowerEvent
}

data class TowerMoveResult(
    val state: TowerGameState,
    val event: TowerEvent,
    val changed: Boolean,
)

data class TowerShopResult(
    val state: TowerGameState,
    val purchased: Boolean,
    val message: String,
)

data class TowerSavedGame(
    val state: TowerGameState,
    val elapsedMillis: Long,
)

val TOWER_FLOORS: List<TowerFloor> = listOf(
    TowerFloor(
        1,
        "觉醒之间",
        listOf(
            "###########",
            "#...#....u#",
            "#.g.#.###.#",
            "#...Y.y...#",
            "###.#.###.#",
            "#h..#..a..#",
            "#.###.###.#",
            "#...g.....#",
            "#.B.#.y...#",
            "#...#.....#",
            "###########",
        ),
    ),
    TowerFloor(
        2,
        "幽暗回廊",
        listOf(
            "###########",
            "#..y#....u#",
            "#.g.#.t...#",
            "#...Y..#..#",
            "#...#.....#",
            "#h..#..f..#",
            "#.###C###.#",
            "#..t..c...#",
            "#.###.###.#",
            "#d....s...#",
            "###########",
        ),
    ),
    TowerFloor(
        3,
        "骸骨牢城",
        listOf(
            "###########",
            "#u...#....#",
            "#.o..#.r..#",
            "#.###R###.#",
            "#...#.....#",
            "#.a.#.p...#",
            "#.###.###.#",
            "#y..w..H..#",
            "#.###Y###.#",
            "#....S...d#",
            "###########",
        ),
    ),
    TowerFloor(
        4,
        "贤者大厅",
        listOf(
            "###########",
            "#...#....u#",
            "#.p.#.w...#",
            "#...C.....#",
            "###.#.###.#",
            "#$..#..a..#",
            "#.###.###.#",
            "#..o..N...#",
            "#.###Y###.#",
            "#d...H....#",
            "###########",
        ),
    ),
    TowerFloor(
        5,
        "魔王殿",
        listOf(
            "###########",
            "#####E#####",
            "#...#x#...#",
            "#...p.p...#",
            "#.###R###.#",
            "#h..w.w..H#",
            "#.###.###.#",
            "#..a...f..#",
            "#.###.###.#",
            "#y...r...d#",
            "###########",
        ),
    ),
)

fun towerTileAt(state: TowerGameState, position: TowerPosition): TowerTile {
    val floor = TOWER_FLOORS[state.floor - 1]
    val baseTile = TowerTile.fromSymbol(floor.rows[position.row][position.column])
    return if (tileKey(state.floor, position) in state.clearedTiles && baseTile.isClearable()) {
        TowerTile.Floor
    } else {
        baseTile
    }
}

fun attemptTowerMove(state: TowerGameState, direction: TowerDirection): TowerMoveResult {
    val target = TowerPosition(
        row = state.position.row + direction.rowOffset,
        column = state.position.column + direction.columnOffset,
    )
    if (target.row !in 0 until TOWER_GRID_SIZE || target.column !in 0 until TOWER_GRID_SIZE) {
        return state.blocked("前方没有道路")
    }

    val tile = towerTileAt(state, target)
    return when (tile) {
        TowerTile.Wall -> state.blocked("坚固的墙挡住了去路")
        TowerTile.Floor -> state.movedTo(target)
        TowerTile.YellowDoor -> openDoor(state, target, tile, "黄钥匙")
        TowerTile.BlueDoor -> openDoor(state, target, tile, "蓝钥匙")
        TowerTile.RedDoor -> openDoor(state, target, tile, "红钥匙")
        TowerTile.UpStairs -> changeFloor(state, state.floor + 1, TowerTile.DownStairs)
        TowerTile.DownStairs -> changeFloor(state, state.floor - 1, TowerTile.UpStairs)
        TowerTile.Shop -> TowerMoveResult(state, TowerEvent.OpenShop, changed = false)
        TowerTile.Npc -> TowerMoveResult(
            state,
            TowerEvent.Message("贤者：真正的勇者会在开战前计算损伤。善用怪物图鉴吧。"),
            changed = false,
        )
        TowerTile.Exit -> TowerMoveResult(state.copy(position = target), TowerEvent.Victory, changed = true)
        TowerTile.YellowKey,
        TowerTile.BlueKey,
        TowerTile.RedKey,
        TowerTile.RedPotion,
        TowerTile.BluePotion,
        TowerTile.AttackGem,
        TowerTile.DefenseGem,
        TowerTile.Sword,
        TowerTile.Shield,
        TowerTile.MonsterBook,
        -> collectItem(state, target, tile)
        TowerTile.Slime,
        TowerTile.Bat,
        TowerTile.Skeleton,
        TowerTile.Knight,
        TowerTile.Wizard,
        TowerTile.Boss,
        -> fightMonster(state, target, checkNotNull(TowerMonster.fromTile(tile)))
    }
}

fun calculateTowerBattleDamage(stats: TowerStats, monster: TowerMonster): Int? {
    val playerDamage = stats.attack - monster.defense
    if (playerDamage <= 0) return null
    val rounds = (monster.hp + playerDamage - 1) / playerDamage
    val monsterDamage = max(0, monster.attack - stats.defense)
    val totalDamage = monsterDamage.toLong() * max(0, rounds - 1)
    return totalDamage.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

fun towerShopCost(state: TowerGameState): Int = 20 + state.shopPurchases * 10

fun buyTowerUpgrade(state: TowerGameState, upgrade: TowerUpgrade): TowerShopResult {
    val cost = towerShopCost(state)
    if (state.stats.gold < cost) {
        return TowerShopResult(state, purchased = false, message = "金币不足，还需要 ${cost - state.stats.gold} 枚")
    }
    val paidStats = state.stats.copy(gold = state.stats.gold - cost)
    val upgradedStats = when (upgrade) {
        TowerUpgrade.Health -> paidStats.copy(hp = paidStats.hp + 400)
        TowerUpgrade.Attack -> paidStats.copy(attack = paidStats.attack + 3)
        TowerUpgrade.Defense -> paidStats.copy(defense = paidStats.defense + 3)
    }
    return TowerShopResult(
        state = state.copy(stats = upgradedStats, shopPurchases = state.shopPurchases + 1),
        purchased = true,
        message = "购买成功：${upgrade.title}",
    )
}

private fun openDoor(
    state: TowerGameState,
    target: TowerPosition,
    door: TowerTile,
    keyName: String,
): TowerMoveResult {
    val stats = state.stats
    val hasKey = when (door) {
        TowerTile.YellowDoor -> stats.yellowKeys > 0
        TowerTile.BlueDoor -> stats.blueKeys > 0
        TowerTile.RedDoor -> stats.redKeys > 0
        else -> false
    }
    if (!hasKey) return state.blocked("需要一把$keyName")
    val updatedStats = when (door) {
        TowerTile.YellowDoor -> stats.copy(yellowKeys = stats.yellowKeys - 1)
        TowerTile.BlueDoor -> stats.copy(blueKeys = stats.blueKeys - 1)
        TowerTile.RedDoor -> stats.copy(redKeys = stats.redKeys - 1)
        else -> stats
    }
    return TowerMoveResult(
        state = state.copy(
            position = target,
            stats = updatedStats,
            clearedTiles = state.clearedTiles + tileKey(state.floor, target),
        ),
        event = TowerEvent.Message("使用${keyName}打开了门"),
        changed = true,
    )
}

private fun collectItem(
    state: TowerGameState,
    target: TowerPosition,
    tile: TowerTile,
): TowerMoveResult {
    var stats = state.stats
    var hasMonsterBook = state.hasMonsterBook
    val message = when (tile) {
        TowerTile.YellowKey -> {
            stats = stats.copy(yellowKeys = stats.yellowKeys + 1)
            "获得黄钥匙"
        }
        TowerTile.BlueKey -> {
            stats = stats.copy(blueKeys = stats.blueKeys + 1)
            "获得蓝钥匙"
        }
        TowerTile.RedKey -> {
            stats = stats.copy(redKeys = stats.redKeys + 1)
            "获得红钥匙"
        }
        TowerTile.RedPotion -> {
            stats = stats.copy(hp = stats.hp + 200)
            "生命 +200"
        }
        TowerTile.BluePotion -> {
            stats = stats.copy(hp = stats.hp + 500)
            "生命 +500"
        }
        TowerTile.AttackGem -> {
            stats = stats.copy(attack = stats.attack + 3)
            "攻击 +3"
        }
        TowerTile.DefenseGem -> {
            stats = stats.copy(defense = stats.defense + 3)
            "防御 +3"
        }
        TowerTile.Sword -> {
            stats = stats.copy(attack = stats.attack + 10)
            "获得铁剑，攻击 +10"
        }
        TowerTile.Shield -> {
            stats = stats.copy(defense = stats.defense + 10)
            "获得铁盾，防御 +10"
        }
        TowerTile.MonsterBook -> {
            hasMonsterBook = true
            "获得怪物图鉴，可预览战斗损伤"
        }
        else -> error("Tile $tile is not an item")
    }
    return TowerMoveResult(
        state = state.copy(
            position = target,
            stats = stats,
            clearedTiles = state.clearedTiles + tileKey(state.floor, target),
            hasMonsterBook = hasMonsterBook,
        ),
        event = TowerEvent.Message(message),
        changed = true,
    )
}

private fun fightMonster(
    state: TowerGameState,
    target: TowerPosition,
    monster: TowerMonster,
): TowerMoveResult {
    val damage = calculateTowerBattleDamage(state.stats, monster)
        ?: return state.blocked("攻击力不足，无法伤害${monster.title}")
    if (damage >= state.stats.hp) {
        return state.blocked("预计损失 $damage 点生命，当前无法战胜${monster.title}")
    }
    val updatedStats = state.stats.copy(
        hp = state.stats.hp - damage,
        gold = state.stats.gold + monster.gold,
        experience = state.stats.experience + monster.experience,
    )
    return TowerMoveResult(
        state = state.copy(
            position = target,
            stats = updatedStats,
            clearedTiles = state.clearedTiles + tileKey(state.floor, target),
        ),
        event = TowerEvent.Battle(monster, damage),
        changed = true,
    )
}

private fun changeFloor(
    state: TowerGameState,
    targetFloor: Int,
    arrivalTile: TowerTile,
): TowerMoveResult {
    if (targetFloor !in 1..TOWER_FLOOR_COUNT) return state.blocked("楼梯已经到了尽头")
    val destination = findTowerTile(targetFloor, arrivalTile)
    return TowerMoveResult(
        state = state.copy(
            floor = targetFloor,
            position = destination,
            highestFloor = max(state.highestFloor, targetFloor),
        ),
        event = TowerEvent.FloorChanged(targetFloor),
        changed = true,
    )
}

private fun findTowerTile(floorNumber: Int, tile: TowerTile): TowerPosition {
    val floor = TOWER_FLOORS[floorNumber - 1]
    floor.rows.forEachIndexed { row, values ->
        val column = values.indexOf(tile.symbol)
        if (column >= 0) return TowerPosition(row, column)
    }
    error("Floor $floorNumber does not contain $tile")
}

private fun TowerGameState.movedTo(target: TowerPosition): TowerMoveResult =
    TowerMoveResult(copy(position = target), TowerEvent.None, changed = true)

private fun TowerGameState.blocked(message: String): TowerMoveResult =
    TowerMoveResult(this, TowerEvent.Message(message), changed = false)

private fun TowerTile.isClearable(): Boolean = this !in setOf(
    TowerTile.Floor,
    TowerTile.Wall,
    TowerTile.UpStairs,
    TowerTile.DownStairs,
    TowerTile.Shop,
    TowerTile.Npc,
    TowerTile.Exit,
)

private fun tileKey(floor: Int, position: TowerPosition): String =
    "$floor:${position.row * TOWER_GRID_SIZE + position.column}"

class TowerSaveStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "tower_game_save",
        Context.MODE_PRIVATE,
    )

    fun hasSave(): Boolean = preferences.getBoolean(KEY_HAS_SAVE, false)

    fun save(state: TowerGameState, elapsedMillis: Long) {
        preferences.edit {
            putBoolean(KEY_HAS_SAVE, true)
            putInt("floor", state.floor)
            putInt("row", state.position.row)
            putInt("column", state.position.column)
            putInt("hp", state.stats.hp)
            putInt("attack", state.stats.attack)
            putInt("defense", state.stats.defense)
            putInt("gold", state.stats.gold)
            putInt("experience", state.stats.experience)
            putInt("yellow_keys", state.stats.yellowKeys)
            putInt("blue_keys", state.stats.blueKeys)
            putInt("red_keys", state.stats.redKeys)
            putStringSet("cleared_tiles", state.clearedTiles.toSet())
            putInt("shop_purchases", state.shopPurchases)
            putBoolean("monster_book", state.hasMonsterBook)
            putInt("highest_floor", state.highestFloor)
            putLong("elapsed", elapsedMillis.coerceAtLeast(0L))
        }
    }

    fun load(): TowerSavedGame? {
        if (!hasSave()) return null
        val floor = preferences.getInt("floor", 1)
        val row = preferences.getInt("row", 9)
        val column = preferences.getInt("column", 1)
        if (floor !in 1..TOWER_FLOOR_COUNT || row !in 0 until TOWER_GRID_SIZE || column !in 0 until TOWER_GRID_SIZE) {
            return null
        }
        val stats = TowerStats(
            hp = preferences.getInt("hp", 1_000),
            attack = preferences.getInt("attack", 10),
            defense = preferences.getInt("defense", 10),
            gold = preferences.getInt("gold", 0),
            experience = preferences.getInt("experience", 0),
            yellowKeys = preferences.getInt("yellow_keys", 0),
            blueKeys = preferences.getInt("blue_keys", 0),
            redKeys = preferences.getInt("red_keys", 0),
        )
        if (stats.hp <= 0 || stats.attack < 0 || stats.defense < 0 || stats.gold < 0) return null
        return TowerSavedGame(
            state = TowerGameState(
                floor = floor,
                position = TowerPosition(row, column),
                stats = stats,
                clearedTiles = preferences.getStringSet("cleared_tiles", emptySet()).orEmpty().toSet(),
                shopPurchases = preferences.getInt("shop_purchases", 0).coerceAtLeast(0),
                hasMonsterBook = preferences.getBoolean("monster_book", false),
                highestFloor = preferences.getInt("highest_floor", floor).coerceIn(floor, TOWER_FLOOR_COUNT),
            ),
            elapsedMillis = preferences.getLong("elapsed", 0L).coerceAtLeast(0L),
        )
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_HAS_SAVE = "has_save"
    }
}
