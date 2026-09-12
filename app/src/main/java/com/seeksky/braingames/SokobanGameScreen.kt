package com.seeksky.braingames

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class SokobanScreenStatus { Ready, Playing }

private data class SokobanCompletion(
    val levelNumber: Int,
    val moves: Int,
    val pushes: Int,
    val elapsedMillis: Long,
    val newBest: Boolean,
)

@Composable
fun SokobanGameApp(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val progressStore = remember(context) { SokobanProgressStore(context.applicationContext) }
    var status by remember { mutableStateOf(SokobanScreenStatus.Ready) }
    var state by remember { mutableStateOf(createSokobanState(0)) }
    var undoHistory by remember { mutableStateOf<List<SokobanState>>(emptyList()) }
    var scores by remember { mutableStateOf(progressStore.getScores()) }
    var highestUnlocked by remember { mutableIntStateOf(progressStore.getHighestUnlocked()) }
    var savedGame by remember { mutableStateOf(progressStore.loadCurrent()) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var completion by remember { mutableStateOf<SokobanCompletion?>(null) }
    var showData by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    fun startLevel(levelIndex: Int, saved: SokobanSavedGame? = null) {
        val restoredElapsed = saved?.elapsedMillis ?: 0L
        state = saved?.state ?: createSokobanState(levelIndex)
        undoHistory = emptyList()
        elapsedMillis = restoredElapsed
        startedAt = SystemClock.elapsedRealtime() - restoredElapsed
        completion = null
        status = SokobanScreenStatus.Playing
        progressStore.saveCurrent(state, restoredElapsed)
        savedGame = SokobanSavedGame(state, restoredElapsed)
    }

    fun returnToReady() {
        if (status == SokobanScreenStatus.Playing && completion == null) {
            val currentElapsed = SystemClock.elapsedRealtime() - startedAt
            progressStore.saveCurrent(state, currentElapsed)
        }
        status = SokobanScreenStatus.Ready
        completion = null
        savedGame = progressStore.loadCurrent()
        highestUnlocked = progressStore.getHighestUnlocked()
        scores = progressStore.getScores()
    }

    fun finishLevel(finalState: SokobanState) {
        val finalElapsed = SystemClock.elapsedRealtime() - startedAt
        val levelNumber = finalState.levelIndex + 1
        val record = SokobanScoreRecord(
            levelNumber = levelNumber,
            moves = finalState.moves,
            pushes = finalState.pushes,
            elapsedMillis = finalElapsed,
            completedAtMillis = System.currentTimeMillis(),
        )
        val newBest = progressStore.addScore(record)
        progressStore.unlock(levelNumber + 1)
        progressStore.clearCurrent()
        elapsedMillis = finalElapsed
        scores = progressStore.getScores()
        highestUnlocked = progressStore.getHighestUnlocked()
        savedGame = null
        completion = SokobanCompletion(
            levelNumber,
            finalState.moves,
            finalState.pushes,
            finalElapsed,
            newBest,
        )
    }

    fun move(direction: SokobanDirection) {
        if (status != SokobanScreenStatus.Playing || completion != null) return
        val beforeMove = state
        val result = attemptSokobanMove(beforeMove, direction)
        if (!result.moved) return
        undoHistory = (undoHistory + beforeMove).takeLast(500)
        state = result.state
        val currentElapsed = SystemClock.elapsedRealtime() - startedAt
        elapsedMillis = currentElapsed
        if (isSokobanSolved(result.state)) {
            finishLevel(result.state)
        } else {
            progressStore.saveCurrent(result.state, currentElapsed)
            savedGame = SokobanSavedGame(result.state, currentElapsed)
        }
    }

    fun undo() {
        val previous = undoHistory.lastOrNull() ?: return
        undoHistory = undoHistory.dropLast(1)
        state = previous
        val currentElapsed = SystemClock.elapsedRealtime() - startedAt
        elapsedMillis = currentElapsed
        progressStore.saveCurrent(previous, currentElapsed)
        savedGame = SokobanSavedGame(previous, currentElapsed)
    }

    fun resetLevel() {
        state = createSokobanState(state.levelIndex)
        undoHistory = emptyList()
        elapsedMillis = 0L
        startedAt = SystemClock.elapsedRealtime()
        progressStore.saveCurrent(state, 0L)
        savedGame = SokobanSavedGame(state, 0L)
    }

    LaunchedEffect(status, startedAt) {
        while (status == SokobanScreenStatus.Playing && completion == null) {
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            delay(200L)
        }
    }

    BackHandler(enabled = status == SokobanScreenStatus.Playing) {
        showLeaveConfirmation = true
    }

    Scaffold(
        containerColor = Color(0xFF17151D),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SokobanHeader(
                isPlaying = status == SokobanScreenStatus.Playing,
                onBack = {
                    if (status == SokobanScreenStatus.Playing) showLeaveConfirmation = true
                    else onNavigateBack()
                },
                onData = { showData = true },
            )
        },
    ) { contentPadding ->
        when (status) {
            SokobanScreenStatus.Ready -> SokobanReadyContent(
                highestUnlocked = highestUnlocked,
                savedGame = savedGame,
                bestForLevel = progressStore::getBest,
                onContinue = { savedGame?.let { startLevel(it.state.levelIndex, it) } },
                onStartLevel = { startLevel(it) },
                modifier = Modifier.padding(contentPadding),
            )

            SokobanScreenStatus.Playing -> SokobanPlayingContent(
                state = state,
                elapsedMillis = elapsedMillis,
                canUndo = undoHistory.isNotEmpty() && completion == null,
                enabled = completion == null,
                onMove = ::move,
                onUndo = ::undo,
                onReset = ::resetLevel,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    completion?.let { result ->
        SokobanCompletionDialog(
            completion = result,
            hasNext = result.levelNumber < SOKOBAN_LEVELS.size,
            onNext = { startLevel(result.levelNumber) },
            onReplay = { startLevel(result.levelNumber - 1) },
            onLevels = ::returnToReady,
        )
    }

    if (showData) {
        SokobanTrainingDataDialog(
            scores = scores,
            onDismiss = { showData = false },
            onClear = {
                progressStore.clearScores()
                scores = emptyList()
            },
        )
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("返回关卡列表？") },
            text = { Text("当前箱子位置、步数和用时会自动保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirmation = false
                    returnToReady()
                }) { Text("保存并返回") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) { Text("继续游戏") }
            },
        )
    }
}

@Composable
private fun SokobanHeader(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onData: () -> Unit,
) {
    Surface(color = Color(0xFF17151D)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(if (isPlaying) "关卡" else "大厅") }
            Text(
                "推箱子",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFD166),
                fontFamily = FontFamily.Monospace,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onData) { Text("数据") }
        }
    }
}

@Composable
private fun SokobanReadyContent(
    highestUnlocked: Int,
    savedGame: SokobanSavedGame?,
    bestForLevel: (Int) -> SokobanBestRecord?,
    onContinue: () -> Unit,
    onStartLevel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            "把仓库整理得井井有条",
            color = Color(0xFFFFD166),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "推动木箱覆盖所有标记点。箱子只能推、不能拉，先想好退路再行动。",
            color = Color(0xFFD7D2E2),
        )
        savedGame?.let { saved ->
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "继续第 ${saved.state.levelIndex + 1} 关 · ${saved.state.moves} 步",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("选择关卡", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        SOKOBAN_LEVELS.chunked(2).forEach { rowLevels ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowLevels.forEach { level ->
                    val unlocked = level.number <= highestUnlocked
                    val best = bestForLevel(level.number)
                    Card(
                        onClick = { onStartLevel(level.number - 1) },
                        enabled = unlocked,
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF282330),
                            disabledContainerColor = Color(0xFF211E27),
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (best != null) Color(0xFFFFD166) else Color(0xFF51465F),
                        ),
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text(
                                if (unlocked) "第 ${level.number} 关" else "🔒 第 ${level.number} 关",
                                color = if (unlocked) Color.White else Color(0xFF77717F),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                level.title,
                                color = if (unlocked) Color(0xFFD7D2E2) else Color(0xFF77717F),
                                fontSize = 13.sp,
                            )
                            Text(
                                best?.let { "最佳 ${it.pushes} 推 / ${it.moves} 步" }
                                    ?: if (unlocked) "尚未完成" else "完成前一关解锁",
                                color = if (best != null) Color(0xFFFFD166) else Color(0xFF9992A6),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                if (rowLevels.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "像素素材：Kenney Tiny Dungeon · CC0",
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF9992A6),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SokobanPlayingContent(
    state: SokobanState,
    elapsedMillis: Long,
    canUndo: Boolean,
    enabled: Boolean,
    onMove: (SokobanDirection) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = SOKOBAN_LEVELS[state.levelIndex]
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${level.number} 关 · ${level.title}",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFD166),
                fontWeight = FontWeight.Bold,
            )
            Text(formatSokobanDuration(elapsedMillis), color = Color(0xFFD7D2E2), fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SokobanStat("移动", "${state.moves} 步", Modifier.weight(1f))
            SokobanStat("推动", "${state.pushes} 次", Modifier.weight(1f))
            SokobanStat("已归位", "${state.boxes.count { it in level.targets }}/${level.targets.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        SokobanBoard(state, enabled, onMove, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        SokobanDirectionPad(enabled, onMove)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onUndo, enabled = canUndo) { Text("撤销一步") }
            OutlinedButton(onClick = onReset, enabled = enabled) { Text("重置关卡") }
        }
        Spacer(Modifier.height(12.dp))
        Text("也可以点击勇者相邻的格子移动", color = Color(0xFF9992A6), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SokobanStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(7.dp), color = Color(0xFF282330)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFFAAA3B7), fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SokobanBoard(
    state: SokobanState,
    enabled: Boolean,
    onMove: (SokobanDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = SOKOBAN_LEVELS[state.levelIndex]
    Surface(
        modifier = modifier.aspectRatio(level.width.toFloat() / level.height),
        color = Color(0xFF743B38),
        border = BorderStroke(2.dp, Color(0xFF8E7A9F)),
    ) {
        Column {
            repeat(level.height) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(level.width) { column ->
                        val position = SokobanPosition(row, column)
                        val direction = sokobanDirectionBetween(state.player, position)
                        SokobanCell(
                            isWall = position in level.walls,
                            isTarget = position in level.targets,
                            hasBox = position in state.boxes,
                            hasPlayer = position == state.player,
                            enabled = enabled && direction != null,
                            onClick = { direction?.let(onMove) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SokobanCell(
    isWall: Boolean,
    isTarget: Boolean,
    hasBox: Boolean,
    hasPlayer: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clickable(enabled = enabled, onClick = onClick)) {
        SokobanSprite(R.drawable.tower_floor, null, Modifier.fillMaxSize())
        if (isWall) {
            SokobanSprite(R.drawable.tower_wall, "墙", Modifier.fillMaxSize())
        } else {
            if (isTarget && !hasBox) SokobanSprite(R.drawable.sokoban_target, "目标", Modifier.fillMaxSize())
            if (hasBox) SokobanSprite(R.drawable.sokoban_box, "木箱", Modifier.fillMaxSize())
            if (isTarget && hasBox) SokobanSprite(R.drawable.sokoban_target, "已归位", Modifier.fillMaxSize())
            if (hasPlayer) SokobanSprite(R.drawable.tower_player, "搬运工", Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SokobanDirectionPad(enabled: Boolean, onMove: (SokobanDirection) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SokobanDirectionButton("▲", enabled) { onMove(SokobanDirection.Up) }
        Row {
            SokobanDirectionButton("◀", enabled) { onMove(SokobanDirection.Left) }
            Spacer(Modifier.size(44.dp))
            SokobanDirectionButton("▶", enabled) { onMove(SokobanDirection.Right) }
        }
        SokobanDirectionButton("▼", enabled) { onMove(SokobanDirection.Down) }
    }
}

@Composable
private fun SokobanDirectionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFF51465F),
        border = BorderStroke(1.dp, Color(0xFF8E7A9F)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SokobanCompletionDialog(
    completion: SokobanCompletion,
    hasNext: Boolean,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onLevels: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (completion.newBest) "新纪录！" else "仓库整理完成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "第 ${completion.levelNumber} 关完成",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text("${completion.pushes} 次推动 · ${completion.moves} 步移动")
                Text("用时 ${formatSokobanDuration(completion.elapsedMillis)}")
            }
        },
        confirmButton = {
            TextButton(onClick = if (hasNext) onNext else onReplay) {
                Text(if (hasNext) "下一关" else "再次挑战")
            }
        },
        dismissButton = { TextButton(onClick = onLevels) { Text("关卡列表") } },
    )
}

@Composable
private fun SokobanSprite(
    @DrawableRes drawable: Int,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = ImageBitmap.imageResource(drawable),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}

private fun sokobanDirectionBetween(
    from: SokobanPosition,
    to: SokobanPosition,
): SokobanDirection? = when {
    abs(from.row - to.row) + abs(from.column - to.column) != 1 -> null
    to.row < from.row -> SokobanDirection.Up
    to.row > from.row -> SokobanDirection.Down
    to.column < from.column -> SokobanDirection.Left
    else -> SokobanDirection.Right
}

fun formatSokobanDuration(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    val minutes = safeMillis / 60_000
    val seconds = (safeMillis / 1_000) % 60
    return "%02d:%02d".format(minutes, seconds)
}
