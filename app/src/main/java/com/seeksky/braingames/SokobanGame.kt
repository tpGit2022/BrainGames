package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit

enum class SokobanDirection(val rowOffset: Int, val columnOffset: Int) {
    Up(-1, 0),
    Down(1, 0),
    Left(0, -1),
    Right(0, 1),
}

data class SokobanPosition(val row: Int, val column: Int) {
    fun move(direction: SokobanDirection): SokobanPosition = SokobanPosition(
        row + direction.rowOffset,
        column + direction.columnOffset,
    )
}

data class SokobanLevel(
    val number: Int,
    val title: String,
    val width: Int,
    val height: Int,
    val walls: Set<SokobanPosition>,
    val targets: Set<SokobanPosition>,
    val initialBoxes: Set<SokobanPosition>,
    val initialPlayer: SokobanPosition,
)

data class SokobanState(
    val levelIndex: Int,
    val player: SokobanPosition,
    val boxes: Set<SokobanPosition>,
    val moves: Int = 0,
    val pushes: Int = 0,
)

data class SokobanMoveResult(
    val state: SokobanState,
    val moved: Boolean,
    val pushed: Boolean,
)

data class SokobanBestRecord(
    val moves: Int,
    val pushes: Int,
    val elapsedMillis: Long,
)

data class SokobanScoreRecord(
    val levelNumber: Int,
    val moves: Int,
    val pushes: Int,
    val elapsedMillis: Long,
    val completedAtMillis: Long,
)

data class SokobanSavedGame(
    val state: SokobanState,
    val elapsedMillis: Long,
)

val SOKOBAN_LEVELS: List<SokobanLevel> = listOf(
    sokobanLevel(
        1,
        "第一步",
        """
            #######
            #_____#
            #_.b@_#
            #_____#
            #######
        """,
    ),
    sokobanLevel(
        2,
        "并肩而行",
        """
            ########
            #______#
            #_.__._#
            #_b__b@#
            #______#
            ########
        """,
    ),
    sokobanLevel(
        3,
        "转过墙角",
        """
            ########
            #______#
            #_.____#
            #_bb#__#
            #___.__#
            #___@__#
            ########
        """,
    ),
    sokobanLevel(
        4,
        "四方归位",
        """
            #########
            #._._._.#
            #b_b_b_b#
            #___@___#
            #########
        """,
    ),
    sokobanLevel(
        5,
        "各有其路",
        """
            ########
            #______#
            #_.____#
            #_b##__#
            #__.b__#
            #___b._#
            #___@__#
            ########
        """,
    ),
    sokobanLevel(
        6,
        "狭长仓库",
        """
            #########
            #___.___#
            #___b___#
            #_###_#_#
            #_.b__#_#
            #__b_.__#
            #___@___#
            #########
        """,
    ),
    sokobanLevel(
        7,
        "交错机关",
        """
            #########
            #_.___._#
            #_b___b_#
            #__###__#
            #_.b_b._#
            #_______#
            #___@___#
            #########
        """,
    ),
    sokobanLevel(
        8,
        "最后仓库",
        """
            #########
            #.#.#.#.#
            #b#b#b#b#
            #_#_#_#_#
            #_______#
            #__@b_._#
            #########
        """,
    ),
)

fun createSokobanState(levelIndex: Int): SokobanState {
    require(levelIndex in SOKOBAN_LEVELS.indices)
    val level = SOKOBAN_LEVELS[levelIndex]
    return SokobanState(levelIndex, level.initialPlayer, level.initialBoxes)
}

fun attemptSokobanMove(
    state: SokobanState,
    direction: SokobanDirection,
): SokobanMoveResult {
    val level = SOKOBAN_LEVELS[state.levelIndex]
    val next = state.player.move(direction)
    if (next in level.walls || next.row !in 0 until level.height || next.column !in 0 until level.width) {
        return SokobanMoveResult(state, moved = false, pushed = false)
    }

    if (next !in state.boxes) {
        return SokobanMoveResult(
            state.copy(player = next, moves = state.moves + 1),
            moved = true,
            pushed = false,
        )
    }

    val beyond = next.move(direction)
    if (
        beyond in level.walls ||
        beyond in state.boxes ||
        beyond.row !in 0 until level.height ||
        beyond.column !in 0 until level.width
    ) {
        return SokobanMoveResult(state, moved = false, pushed = false)
    }
    return SokobanMoveResult(
        state.copy(
            player = next,
            boxes = state.boxes - next + beyond,
            moves = state.moves + 1,
            pushes = state.pushes + 1,
        ),
        moved = true,
        pushed = true,
    )
}

fun isSokobanSolved(state: SokobanState): Boolean =
    state.boxes == SOKOBAN_LEVELS[state.levelIndex].targets

fun isBetterSokobanScore(
    candidate: SokobanBestRecord,
    previous: SokobanBestRecord?,
): Boolean = previous == null ||
    candidate.pushes < previous.pushes ||
    (candidate.pushes == previous.pushes && candidate.moves < previous.moves) ||
    (candidate.pushes == previous.pushes && candidate.moves == previous.moves &&
        candidate.elapsedMillis < previous.elapsedMillis)

private fun sokobanLevel(number: Int, title: String, encoded: String): SokobanLevel {
    val rows = encoded.trimIndent().lines().filter { it.isNotBlank() }
    require(rows.isNotEmpty())
    val width = rows.first().length
    require(rows.all { it.length == width }) { "Sokoban level $number must be rectangular" }
    val walls = mutableSetOf<SokobanPosition>()
    val targets = mutableSetOf<SokobanPosition>()
    val boxes = mutableSetOf<SokobanPosition>()
    var player: SokobanPosition? = null

    rows.forEachIndexed { row, values ->
        values.forEachIndexed { column, symbol ->
            val position = SokobanPosition(row, column)
            when (symbol) {
                '#' -> walls += position
                '.' -> targets += position
                'b' -> boxes += position
                '@' -> {
                    require(player == null) { "Sokoban level $number has multiple players" }
                    player = position
                }
                '*' -> {
                    targets += position
                    boxes += position
                }
                '+' -> {
                    targets += position
                    player = position
                }
                '_' -> Unit
                else -> error("Unknown Sokoban tile '$symbol' in level $number")
            }
        }
    }
    require(boxes.size == targets.size) { "Sokoban level $number needs one target per box" }
    return SokobanLevel(
        number = number,
        title = title,
        width = width,
        height = rows.size,
        walls = walls,
        targets = targets,
        initialBoxes = boxes,
        initialPlayer = requireNotNull(player) { "Sokoban level $number needs a player" },
    )
}

class SokobanProgressStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sokoban_progress",
        Context.MODE_PRIVATE,
    )

    fun getHighestUnlocked(): Int = preferences.getInt(KEY_UNLOCKED, 1)
        .coerceIn(1, SOKOBAN_LEVELS.size)

    fun unlock(levelNumber: Int) {
        if (levelNumber <= getHighestUnlocked()) return
        preferences.edit { putInt(KEY_UNLOCKED, levelNumber.coerceAtMost(SOKOBAN_LEVELS.size)) }
    }

    fun getBest(levelNumber: Int): SokobanBestRecord? {
        val prefix = "level_${levelNumber}_"
        if (!preferences.contains("${prefix}moves")) return null
        return SokobanBestRecord(
            moves = preferences.getInt("${prefix}moves", 0),
            pushes = preferences.getInt("${prefix}pushes", 0),
            elapsedMillis = preferences.getLong("${prefix}elapsed", 0L),
        )
    }

    fun addScore(score: SokobanScoreRecord): Boolean {
        val candidate = SokobanBestRecord(score.moves, score.pushes, score.elapsedMillis)
        val prefix = "level_${score.levelNumber}_"
        val isNewBest = isBetterSokobanScore(candidate, getBest(score.levelNumber))
        preferences.edit {
            putString(KEY_SCORES, SokobanScoreCodec.encode(getScores() + score))
            if (isNewBest) {
                putInt("${prefix}moves", score.moves)
                putInt("${prefix}pushes", score.pushes)
                putLong("${prefix}elapsed", score.elapsedMillis)
            }
        }
        return isNewBest
    }

    fun getScores(): List<SokobanScoreRecord> = SokobanScoreCodec
        .decode(preferences.getString(KEY_SCORES, null).orEmpty())
        .sortedByDescending { it.completedAtMillis }

    fun saveCurrent(state: SokobanState, elapsedMillis: Long) {
        preferences.edit {
            putBoolean(KEY_HAS_CURRENT, true)
            putInt("current_level", state.levelIndex)
            putInt("current_player_row", state.player.row)
            putInt("current_player_column", state.player.column)
            putString("current_boxes", state.boxes.joinToString(";") { "${it.row},${it.column}" })
            putInt("current_moves", state.moves)
            putInt("current_pushes", state.pushes)
            putLong("current_elapsed", elapsedMillis.coerceAtLeast(0L))
        }
    }

    fun loadCurrent(): SokobanSavedGame? {
        if (!preferences.getBoolean(KEY_HAS_CURRENT, false)) return null
        val levelIndex = preferences.getInt("current_level", -1)
        if (levelIndex !in SOKOBAN_LEVELS.indices) return null
        val level = SOKOBAN_LEVELS[levelIndex]
        val player = SokobanPosition(
            preferences.getInt("current_player_row", -1),
            preferences.getInt("current_player_column", -1),
        )
        val boxes = preferences.getString("current_boxes", null)
            ?.split(';')
            ?.mapNotNull { encodedPosition ->
                val values = encodedPosition.split(',')
                if (values.size != 2) return@mapNotNull null
                val row = values[0].toIntOrNull() ?: return@mapNotNull null
                val column = values[1].toIntOrNull() ?: return@mapNotNull null
                SokobanPosition(row, column)
            }
            ?.toSet()
            ?: return null
        if (
            player.row !in 0 until level.height ||
            player.column !in 0 until level.width ||
            player in level.walls ||
            boxes.size != level.targets.size ||
            boxes.any { it in level.walls }
        ) {
            return null
        }
        return SokobanSavedGame(
            state = SokobanState(
                levelIndex = levelIndex,
                player = player,
                boxes = boxes,
                moves = preferences.getInt("current_moves", 0).coerceAtLeast(0),
                pushes = preferences.getInt("current_pushes", 0).coerceAtLeast(0),
            ),
            elapsedMillis = preferences.getLong("current_elapsed", 0L).coerceAtLeast(0L),
        )
    }

    fun clearCurrent() {
        preferences.edit { putBoolean(KEY_HAS_CURRENT, false) }
    }

    fun clearScores() {
        preferences.edit {
            remove(KEY_SCORES)
            SOKOBAN_LEVELS.forEach { level ->
                val prefix = "level_${level.number}_"
                remove("${prefix}moves")
                remove("${prefix}pushes")
                remove("${prefix}elapsed")
            }
        }
    }

    fun clearAll() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_UNLOCKED = "highest_unlocked"
        const val KEY_SCORES = "score_records"
        const val KEY_HAS_CURRENT = "has_current"
    }
}

internal object SokobanScoreCodec {
    fun encode(scores: List<SokobanScoreRecord>): String = scores.joinToString(";") { score ->
        listOf(
            score.levelNumber,
            score.moves,
            score.pushes,
            score.elapsedMillis,
            score.completedAtMillis,
        ).joinToString(",")
    }

    fun decode(value: String): List<SokobanScoreRecord> {
        if (value.isBlank()) return emptyList()
        return value.split(';').mapNotNull { encodedScore ->
            val values = encodedScore.split(',')
            if (values.size != 5) return@mapNotNull null
            val level = values[0].toIntOrNull() ?: return@mapNotNull null
            val moves = values[1].toIntOrNull() ?: return@mapNotNull null
            val pushes = values[2].toIntOrNull() ?: return@mapNotNull null
            val elapsed = values[3].toLongOrNull() ?: return@mapNotNull null
            val completed = values[4].toLongOrNull() ?: return@mapNotNull null
            if (
                level !in 1..SOKOBAN_LEVELS.size ||
                moves <= 0 || pushes <= 0 || pushes > moves || elapsed < 0L || completed < 0L
            ) return@mapNotNull null
            SokobanScoreRecord(level, moves, pushes, elapsed, completed)
        }
    }
}
