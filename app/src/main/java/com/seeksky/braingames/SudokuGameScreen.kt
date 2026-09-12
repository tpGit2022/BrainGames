package com.seeksky.braingames

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SudokuGameStatus { Ready, Playing, Finished }

@Composable
fun SudokuGameApp(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scoreStore = remember(context) { SudokuScoreStore(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var scores by remember { mutableStateOf(scoreStore.getScores()) }
    var difficulty by remember { mutableStateOf(SudokuDifficulty.Easy) }
    var status by remember { mutableStateOf(SudokuGameStatus.Ready) }
    var isGenerating by remember { mutableStateOf(false) }
    var puzzle by remember { mutableStateOf<IntArray?>(null) }
    var solution by remember { mutableStateOf<IntArray?>(null) }
    var board by remember { mutableStateOf(IntArray(SUDOKU_CELL_COUNT)) }
    var selectedCell by remember { mutableIntStateOf(-1) }
    var errorCell by remember { mutableIntStateOf(-1) }
    var mistakes by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var completedRecord by remember { mutableStateOf<SudokuBestRecord?>(null) }
    var isNewBest by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showQuitConfirmation by remember { mutableStateOf(false) }

    val bestRecord = scores
        .filter { it.difficulty == difficulty }
        .minWithOrNull(compareBy<SudokuScoreRecord> { it.mistakes }.thenBy { it.elapsedMillis })

    fun startGame() {
        if (isGenerating) return
        isGenerating = true
        completedRecord = null
        coroutineScope.launch {
            val generated = withContext(Dispatchers.Default) {
                generateSudokuPuzzle(difficulty)
            }
            puzzle = generated.cells
            solution = generated.solution
            board = generated.cells.copyOf()
            selectedCell = generated.cells.indexOfFirst { it == 0 }
            errorCell = -1
            mistakes = 0
            elapsedMillis = 0L
            startedAt = SystemClock.elapsedRealtime()
            isGenerating = false
            status = SudokuGameStatus.Playing
        }
    }

    fun returnToSetup() {
        status = SudokuGameStatus.Ready
        completedRecord = null
        selectedCell = -1
        errorCell = -1
    }

    fun finishGame() {
        val finalElapsed = SystemClock.elapsedRealtime() - startedAt
        val record = SudokuBestRecord(finalElapsed, mistakes)
        val score = SudokuScoreRecord(
            difficulty = difficulty,
            elapsedMillis = finalElapsed,
            mistakes = mistakes,
            completedAtMillis = System.currentTimeMillis(),
        )
        elapsedMillis = finalElapsed
        isNewBest = scoreStore.add(score)
        scores = scoreStore.getScores()
        completedRecord = record
        status = SudokuGameStatus.Finished
    }

    LaunchedEffect(status, startedAt) {
        while (status == SudokuGameStatus.Playing) {
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            delay(100L)
        }
    }

    BackHandler(enabled = status == SudokuGameStatus.Playing) {
        showQuitConfirmation = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SudokuHeader(
                isPlaying = status == SudokuGameStatus.Playing,
                onBack = {
                    if (status == SudokuGameStatus.Playing) showQuitConfirmation = true
                    else onNavigateBack()
                },
                onDataClick = { showData = true },
            )
        },
    ) { contentPadding ->
        when (status) {
            SudokuGameStatus.Ready -> SudokuReadyContent(
                difficulty = difficulty,
                bestRecord = bestRecord?.let { SudokuBestRecord(it.elapsedMillis, it.mistakes) },
                isGenerating = isGenerating,
                onDifficultySelected = { difficulty = it },
                onStart = ::startGame,
                modifier = Modifier.padding(contentPadding),
            )

            SudokuGameStatus.Playing,
            SudokuGameStatus.Finished,
            -> {
                val currentPuzzle = puzzle
                val currentSolution = solution
                if (currentPuzzle != null && currentSolution != null) {
                    SudokuPlayingContent(
                        difficulty = difficulty,
                        puzzle = currentPuzzle,
                        board = board,
                        selectedCell = selectedCell,
                        errorCell = errorCell,
                        mistakes = mistakes,
                        elapsedMillis = elapsedMillis,
                        enabled = status == SudokuGameStatus.Playing,
                        onCellSelected = { selectedCell = it },
                        onNumberSelected = { number ->
                            if (
                                status != SudokuGameStatus.Playing ||
                                selectedCell !in board.indices ||
                                currentPuzzle[selectedCell] != 0
                            ) {
                                return@SudokuPlayingContent
                            }
                            if (number == 0) {
                                board = board.copyOf().also { it[selectedCell] = 0 }
                            } else if (currentSolution[selectedCell] == number) {
                                board = board.copyOf().also { it[selectedCell] = number }
                                errorCell = -1
                                if (board.contentEquals(currentSolution)) finishGame()
                            } else {
                                mistakes += 1
                                val failedCell = selectedCell
                                errorCell = failedCell
                                coroutineScope.launch {
                                    delay(450L)
                                    if (errorCell == failedCell) errorCell = -1
                                }
                            }
                        },
                        onQuit = { showQuitConfirmation = true },
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
        }
    }

    completedRecord?.let { record ->
        SudokuResultDialog(
            difficulty = difficulty,
            record = record,
            isNewBest = isNewBest,
            onPlayAgain = ::startGame,
            onSetup = ::returnToSetup,
        )
    }

    if (showData) {
        SudokuDataDialog(
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
            text = { Text("当前棋盘进度不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirmation = false
                    returnToSetup()
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirmation = false }) { Text("继续游戏") }
            },
        )
    }
}

@Composable
private fun SudokuHeader(
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
                "数独",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SudokuReadyContent(
    difficulty: SudokuDifficulty,
    bestRecord: SudokuBestRecord?,
    isGenerating: Boolean,
    onDifficultySelected: (SudokuDifficulty) -> Unit,
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
            "九宫之间，唯一答案",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "让每一行、每一列和每个九宫格都填入 1 至 9，数字不能重复。每道题均有唯一解。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(28.dp))
        Text("选择难度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SudokuDifficulty.entries.chunked(2).forEach { difficulties ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    difficulties.forEach { item ->
                        FilterChip(
                            selected = difficulty == item,
                            onClick = { onDifficultySelected(item) },
                            enabled = !isGenerating,
                            label = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(item.title, fontWeight = FontWeight.Bold)
                                    Text(item.description, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
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
                    Text("本难度最佳", style = MaterialTheme.typography.labelLarge)
                    Text(
                        bestRecord?.let { formatSudokuDuration(it.elapsedMillis) } ?: "尚无记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                bestRecord?.let {
                    Text("错误 ${it.mistakes} 次", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            enabled = !isGenerating,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(10.dp))
                Text("正在生成唯一解题目…", fontWeight = FontWeight.Bold)
            } else {
                Text("开始挑战", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SudokuPlayingContent(
    difficulty: SudokuDifficulty,
    puzzle: IntArray,
    board: IntArray,
    selectedCell: Int,
    errorCell: Int,
    mistakes: Int,
    elapsedMillis: Long,
    enabled: Boolean,
    onCellSelected: (Int) -> Unit,
    onNumberSelected: (Int) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SudokuStatCard("难度", difficulty.title, Modifier.weight(1f))
            SudokuStatCard("错误", mistakes.toString(), Modifier.weight(1f))
            SudokuStatCard("用时", formatSudokuDuration(elapsedMillis), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        SudokuBoard(
            puzzle = puzzle,
            board = board,
            selectedCell = selectedCell,
            errorCell = errorCell,
            enabled = enabled,
            onCellSelected = onCellSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        SudokuNumberPad(
            board = board,
            enabled = enabled && selectedCell in board.indices && puzzle[selectedCell] == 0,
            onNumberSelected = onNumberSelected,
        )
        TextButton(onClick = onQuit, enabled = enabled) { Text("结束本局") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SudokuStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SudokuBoard(
    puzzle: IntArray,
    board: IntArray,
    selectedCell: Int,
    errorCell: Int,
    enabled: Boolean,
    onCellSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedValue = board.getOrElse(selectedCell) { 0 }
    val lineColor = MaterialTheme.colorScheme.outline
    val strongLineColor = MaterialTheme.colorScheme.onSurface
    val relatedColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val sameValueColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    val selectedColor = MaterialTheme.colorScheme.primaryContainer
    val errorColor = MaterialTheme.colorScheme.errorContainer

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.matchParentSize()) {
            repeat(SUDOKU_SIZE) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(SUDOKU_SIZE) { column ->
                        val position = row * SUDOKU_SIZE + column
                        val isRelated = selectedCell >= 0 && (
                            row == selectedCell / SUDOKU_SIZE ||
                                column == selectedCell % SUDOKU_SIZE ||
                                row / 3 == selectedCell / SUDOKU_SIZE / 3 &&
                                column / 3 == selectedCell % SUDOKU_SIZE / 3
                            )
                        val cellColor = when {
                            position == errorCell -> errorColor
                            position == selectedCell -> selectedColor
                            selectedValue != 0 && board[position] == selectedValue -> sameValueColor
                            isRelated -> relatedColor
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(cellColor)
                                .clickable(enabled = enabled) { onCellSelected(position) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (board[position] != 0) {
                                Text(
                                    board[position].toString(),
                                    color = if (puzzle[position] != 0) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    fontSize = 19.sp,
                                    fontWeight = if (puzzle[position] != 0) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val cellSize = size.width / SUDOKU_SIZE
            for (index in 0..SUDOKU_SIZE) {
                val strong = index % 3 == 0
                val offset = index * cellSize
                drawLine(
                    color = if (strong) strongLineColor else lineColor,
                    start = androidx.compose.ui.geometry.Offset(offset, 0f),
                    end = androidx.compose.ui.geometry.Offset(offset, size.height),
                    strokeWidth = if (strong) 2.8.dp.toPx() else 0.8.dp.toPx(),
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = if (strong) strongLineColor else lineColor,
                    start = androidx.compose.ui.geometry.Offset(0f, offset),
                    end = androidx.compose.ui.geometry.Offset(size.width, offset),
                    strokeWidth = if (strong) 2.8.dp.toPx() else 0.8.dp.toPx(),
                    cap = StrokeCap.Square,
                )
            }
        }
    }
}

@Composable
private fun SudokuNumberPad(
    board: IntArray,
    enabled: Boolean,
    onNumberSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..9).chunked(3).forEach { numbers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                numbers.forEach { number ->
                    val numberComplete = board.count { it == number } >= SUDOKU_SIZE
                    Surface(
                        onClick = { onNumberSelected(number) },
                        enabled = enabled && !numberComplete,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(number.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = { onNumberSelected(0) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("清除当前格") }
    }
}

@Composable
private fun SudokuResultDialog(
    difficulty: SudokuDifficulty,
    record: SudokuBestRecord,
    isNewBest: Boolean,
    onPlayAgain: () -> Unit,
    onSetup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (isNewBest) "新纪录！" else "完成数独") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${difficulty.title}难度")
                Text(
                    formatSudokuDuration(record.elapsedMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("错误 ${record.mistakes} 次")
            }
        },
        confirmButton = { TextButton(onClick = onPlayAgain) { Text("再来一局") } },
        dismissButton = { TextButton(onClick = onSetup) { Text("选择难度") } },
    )
}

@Composable
private fun SudokuDataDialog(
    scores: List<SudokuScoreRecord>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数独成绩") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SudokuDifficulty.entries.forEach { difficulty ->
                    val best = scores
                        .filter { it.difficulty == difficulty }
                        .minWithOrNull(compareBy<SudokuScoreRecord> { it.mistakes }.thenBy { it.elapsedMillis })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(difficulty.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(
                            best?.let { "${formatSudokuDuration(it.elapsedMillis)} · 错误 ${it.mistakes}" }
                                ?: "尚无记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (scores.isNotEmpty()) {
                    Text("共完成 ${scores.size} 局", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {
            if (scores.isNotEmpty()) TextButton(onClick = onClear) { Text("清空数据") }
        },
    )
}

fun formatSudokuDuration(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    val hours = safeMillis / 3_600_000
    val minutes = (safeMillis / 60_000) % 60
    val seconds = (safeMillis / 1_000) % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
