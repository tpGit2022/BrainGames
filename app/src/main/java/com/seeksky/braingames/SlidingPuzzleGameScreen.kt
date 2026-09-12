package com.seeksky.braingames

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

private enum class SlidingPuzzleStatus { Ready, Playing, Finished }

@Composable
fun SlidingPuzzleGameApp(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scoreStore = remember(context) { SlidingPuzzleScoreStore(context.applicationContext) }
    var scores by remember { mutableStateOf(scoreStore.getScores()) }
    var status by remember { mutableStateOf(SlidingPuzzleStatus.Ready) }
    var board by remember { mutableStateOf(SLIDING_PUZZLE_SOLUTION.copyOf()) }
    var moves by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var completedRecord by remember { mutableStateOf<SlidingPuzzleBestRecord?>(null) }
    var isNewBest by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showQuitConfirmation by remember { mutableStateOf(false) }

    val bestRecord = scores
        .minWithOrNull(compareBy<SlidingPuzzleScoreRecord> { it.moves }.thenBy { it.elapsedMillis })

    fun startGame() {
        board = createSlidingPuzzle()
        moves = 0
        elapsedMillis = 0L
        completedRecord = null
        startedAt = SystemClock.elapsedRealtime()
        status = SlidingPuzzleStatus.Playing
    }

    fun returnToHome() {
        status = SlidingPuzzleStatus.Ready
        completedRecord = null
    }

    fun finishGame(finalMoves: Int) {
        val finalElapsed = SystemClock.elapsedRealtime() - startedAt
        val record = SlidingPuzzleBestRecord(finalMoves, finalElapsed)
        val score = SlidingPuzzleScoreRecord(
            moves = finalMoves,
            elapsedMillis = finalElapsed,
            completedAtMillis = System.currentTimeMillis(),
        )
        elapsedMillis = finalElapsed
        isNewBest = scoreStore.add(score)
        scores = scoreStore.getScores()
        completedRecord = record
        status = SlidingPuzzleStatus.Finished
    }

    LaunchedEffect(status, startedAt) {
        while (status == SlidingPuzzleStatus.Playing) {
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            delay(100L)
        }
    }

    BackHandler(enabled = status == SlidingPuzzleStatus.Playing) {
        showQuitConfirmation = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SlidingPuzzleHeader(
                isPlaying = status == SlidingPuzzleStatus.Playing,
                onBack = {
                    if (status == SlidingPuzzleStatus.Playing) showQuitConfirmation = true
                    else onNavigateBack()
                },
                onDataClick = { showData = true },
            )
        },
    ) { contentPadding ->
        when (status) {
            SlidingPuzzleStatus.Ready -> SlidingPuzzleReadyContent(
                bestRecord = bestRecord?.let { SlidingPuzzleBestRecord(it.moves, it.elapsedMillis) },
                onStart = ::startGame,
                modifier = Modifier.padding(contentPadding),
            )

            SlidingPuzzleStatus.Playing,
            SlidingPuzzleStatus.Finished,
            -> SlidingPuzzlePlayingContent(
                board = board,
                moves = moves,
                elapsedMillis = elapsedMillis,
                enabled = status == SlidingPuzzleStatus.Playing,
                onTileClick = { position ->
                    if (status != SlidingPuzzleStatus.Playing) return@SlidingPuzzlePlayingContent
                    val movedBoard = slideTile(board, position) ?: return@SlidingPuzzlePlayingContent
                    val finalMoves = moves + 1
                    board = movedBoard
                    moves = finalMoves
                    if (isSlidingPuzzleSolved(movedBoard)) finishGame(finalMoves)
                },
                onQuit = { showQuitConfirmation = true },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    completedRecord?.let { record ->
        SlidingPuzzleResultDialog(
            record = record,
            isNewBest = isNewBest,
            onPlayAgain = ::startGame,
            onHome = ::returnToHome,
        )
    }

    if (showData) {
        SlidingPuzzleTrainingDataDialog(
            scores = scores,
            onDismiss = { showData = false },
            onClear = {
                scoreStore.clear()
                scores = emptyList()
            },
        )
    }

    if (showQuitConfirmation) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmation = false },
            title = { Text("结束本局？") },
            text = { Text("当前排列、步数和计时不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirmation = false
                    returnToHome()
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirmation = false }) { Text("继续游戏") }
            },
        )
    }
}

@Composable
private fun SlidingPuzzleHeader(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onDataClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(if (isPlaying) "结束" else "返回") }
            Text(
                "数字华容道",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (isPlaying) Spacer(Modifier.size(64.dp))
            else TextButton(onClick = onDataClick) { Text("数据") }
        }
    }
}

@Composable
private fun SlidingPuzzleReadyContent(
    bestRecord: SlidingPuzzleBestRecord?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            "挪出一条归位之路",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "点击空格上、下、左、右相邻的数字，让它滑入空位。用尽可能少的步数将 1 至 8 依次排好。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(24.dp))
        Text("目标排列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        SlidingPuzzleGoalPreview()

        Spacer(Modifier.height(22.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("最佳成绩", style = MaterialTheme.typography.labelLarge)
                    Text(
                        bestRecord?.let { "${it.moves} 步" } ?: "尚无记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                bestRecord?.let {
                    Text(formatSlidingPuzzleDuration(it.elapsedMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("开始挑战", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SlidingPuzzleGoalPreview() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SLIDING_PUZZLE_SOLUTION.toList().chunked(SLIDING_PUZZLE_SIZE).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { value ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.9f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (value == 0) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (value != 0) Text(value.toString(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidingPuzzlePlayingContent(
    board: IntArray,
    moves: Int,
    elapsedMillis: Long,
    enabled: Boolean,
    onTileClick: (Int) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SlidingPuzzleStatCard("步数", moves.toString(), Modifier.weight(1f))
            SlidingPuzzleStatCard("用时", formatSlidingPuzzleDuration(elapsedMillis), Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))
        SlidingPuzzleBoard(
            board = board,
            enabled = enabled,
            onTileClick = onTileClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "点击空位旁边的数字进行移动",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onQuit, enabled = enabled) { Text("结束本局") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SlidingPuzzleStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SlidingPuzzleBoard(
    board: IntArray,
    enabled: Boolean,
    onTileClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            board.toList().chunked(SLIDING_PUZZLE_SIZE).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEachIndexed { columnIndex, value ->
                        val position = rowIndex * SLIDING_PUZZLE_SIZE + columnIndex
                        val movable = enabled && canSlideTile(board, position)
                        Surface(
                            onClick = { onTileClick(position) },
                            enabled = movable,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (value == SLIDING_PUZZLE_EMPTY) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            border = if (movable) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                            } else {
                                null
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (value != SLIDING_PUZZLE_EMPTY) {
                                    Text(
                                        value.toString(),
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidingPuzzleResultDialog(
    record: SlidingPuzzleBestRecord,
    isNewBest: Boolean,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (isNewBest) "新纪录！" else "排列完成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${record.moves} 步",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text("用时 ${formatSlidingPuzzleDuration(record.elapsedMillis)}")
            }
        },
        confirmButton = { TextButton(onClick = onPlayAgain) { Text("再来一局") } },
        dismissButton = { TextButton(onClick = onHome) { Text("返回首页") } },
    )
}

fun formatSlidingPuzzleDuration(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    val minutes = safeMillis / 60_000
    val seconds = (safeMillis / 1_000) % 60
    return "%02d:%02d".format(minutes, seconds)
}
