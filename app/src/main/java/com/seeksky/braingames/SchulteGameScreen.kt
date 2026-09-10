package com.seeksky.braingames

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class GameStatus { Ready, Playing, Finished }

private data class CellFeedback(val isCorrect: Boolean, val sequence: Long)

@Composable
fun SchulteGameApp() {
    val context = LocalContext.current
    val scoreStore = remember(context) { ScoreStore(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var scores by remember { mutableStateOf(scoreStore.getScores()) }
    var selectedSize by rememberSaveable { mutableIntStateOf(5) }
    var status by rememberSaveable { mutableStateOf(GameStatus.Ready) }
    var board by rememberSaveable { mutableStateOf(intArrayOf()) }
    var expectedNumber by rememberSaveable { mutableIntStateOf(1) }
    var errors by rememberSaveable { mutableIntStateOf(0) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var elapsedMillis by rememberSaveable { mutableLongStateOf(0L) }
    var completedScore by remember { mutableStateOf<ScoreRecord?>(null) }
    var isNewBest by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showQuitConfirmation by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        isExporting = true
        coroutineScope.launch {
            val exportSucceeded = withContext(Dispatchers.IO) {
                runCatching {
                    val outputStream = context.contentResolver.openOutputStream(destination)
                        ?: error("Unable to open export destination")
                    outputStream.use(scoreStore::exportDatabase)
                }.isSuccess
            }
            isExporting = false
            Toast.makeText(
                context.applicationContext,
                if (exportSucceeded) "成绩数据库已导出" else "导出失败，请重试",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun startGame() {
        board = createBoard(selectedSize).toIntArray()
        expectedNumber = 1
        errors = 0
        elapsedMillis = 0L
        completedScore = null
        startedAt = SystemClock.elapsedRealtime()
        status = GameStatus.Playing
    }

    fun returnHome() {
        status = GameStatus.Ready
        completedScore = null
    }

    LaunchedEffect(status, startedAt) {
        while (status == GameStatus.Playing) {
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            delay(10L)
        }
    }

    BackHandler(enabled = status == GameStatus.Playing) {
        showQuitConfirmation = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppHeader(
                isPlaying = status == GameStatus.Playing,
                onHistoryClick = { showHistory = true },
            )
        },
    ) { contentPadding ->
        when (status) {
            GameStatus.Ready -> ReadyContent(
                selectedSize = selectedSize,
                scores = scores,
                onSizeSelected = { selectedSize = it },
                onStart = ::startGame,
                modifier = Modifier.padding(contentPadding),
            )

            GameStatus.Playing -> PlayingContent(
                gridSize = selectedSize,
                board = board,
                expectedNumber = expectedNumber,
                elapsedMillis = elapsedMillis,
                errors = errors,
                onNumberClick = { tappedNumber ->
                    val result = evaluateTap(expectedNumber, tappedNumber, selectedSize * selectedSize)
                    if (!result.isCorrect) {
                        errors += 1
                        false
                    } else if (result.isComplete) {
                        val finalElapsed = SystemClock.elapsedRealtime() - startedAt
                        val previousBest = scores
                            .filter { it.gridSize == selectedSize }
                            .minOfOrNull { it.elapsedMillis }
                        val record = ScoreRecord(
                            gridSize = selectedSize,
                            elapsedMillis = finalElapsed,
                            errors = errors,
                            completedAtMillis = System.currentTimeMillis(),
                        )
                        elapsedMillis = finalElapsed
                        isNewBest = previousBest == null || finalElapsed < previousBest
                        scores = scoreStore.add(record)
                        completedScore = record
                        status = GameStatus.Finished
                        true
                    } else {
                        expectedNumber = result.nextNumber
                        true
                    }
                },
                onQuit = { showQuitConfirmation = true },
                modifier = Modifier.padding(contentPadding),
            )

            GameStatus.Finished -> PlayingContent(
                gridSize = selectedSize,
                board = board,
                expectedNumber = selectedSize * selectedSize,
                elapsedMillis = elapsedMillis,
                errors = errors,
                onNumberClick = { false },
                onQuit = ::returnHome,
                enabled = false,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    if (showHistory) {
        HistoryDialog(
            scores = scores,
            isExporting = isExporting,
            onDismiss = { showHistory = false },
            onExport = {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportLauncher.launch("schulte_scores_$timestamp.zip")
            },
            onClear = {
                scoreStore.clear()
                scores = emptyList()
            },
        )
    }

    completedScore?.let { score ->
        ResultDialog(
            score = score,
            isNewBest = isNewBest,
            onPlayAgain = ::startGame,
            onHome = ::returnHome,
        )
    }

    if (showQuitConfirmation) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmation = false },
            title = { Text("结束本局？") },
            text = { Text("当前进度和计时不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirmation = false
                    returnHome()
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirmation = false }) { Text("继续游戏") }
            },
        )
    }
}

@Composable
private fun AppHeader(isPlaying: Boolean, onHistoryClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("S", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "舒尔特表",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (!isPlaying) {
                TextButton(onClick = onHistoryClick) { Text("训练数据") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    selectedSize: Int,
    scores: List<ScoreRecord>,
    onSizeSelected: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bestScore = scores
        .filter { it.gridSize == selectedSize }
        .minByOrNull { it.elapsedMillis }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = "专注，从 1 开始",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "按顺序找出所有数字，尽可能快地完成。保持视线在表格中央，尝试用余光搜索。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("选择难度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (MIN_GRID_SIZE..MAX_GRID_SIZE).chunked(3).forEach { rowSizes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowSizes.forEach { size ->
                        FilterChip(
                            selected = selectedSize == size,
                            onClick = { onSizeSelected(size) },
                            label = {
                                Text(
                                    "$size × $size",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
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
                        bestScore?.let { formatDuration(it.elapsedMillis) } ?: "尚无记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "${selectedSize * selectedSize} 个数字",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("开始训练", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlayingContent(
    gridSize: Int,
    board: IntArray,
    expectedNumber: Int,
    elapsedMillis: Long,
    errors: Int,
    onNumberClick: (Int) -> Boolean,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("用时", formatDuration(elapsedMillis), Modifier.weight(1f))
            StatCard("下一个", if (enabled) expectedNumber.toString() else "完成", Modifier.weight(1f))
            StatCard("错误", errors.toString(), Modifier.weight(1f), errors > 0)
        }

        Spacer(Modifier.weight(1f))
        SchulteBoard(
            gridSize = gridSize,
            board = board,
            onNumberClick = onNumberClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.weight(1f))

        Text(
            text = if (enabled) "请找到数字 $expectedNumber" else "训练完成",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onQuit) { Text(if (enabled) "结束本局" else "返回首页") }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SchulteBoard(
    gridSize: Int,
    board: IntArray,
    onNumberClick: (Int) -> Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val gap = when {
        gridSize <= 4 -> 7.dp
        gridSize <= 6 -> 5.dp
        else -> 3.dp
    }
    val numberSize = when (gridSize) {
        3 -> 32.sp
        4 -> 27.sp
        5 -> 23.sp
        6 -> 19.sp
        7 -> 16.sp
        else -> 14.sp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(if (gridSize <= 5) 10.dp else 7.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            board.asList().chunked(gridSize).forEach { numbers ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    numbers.forEach { number ->
                        var feedback by remember(number) { mutableStateOf<CellFeedback?>(null) }
                        val activeFeedback = feedback
                        val cellColor by animateColorAsState(
                            targetValue = when {
                                activeFeedback?.isCorrect == true -> MaterialTheme.colorScheme.tertiaryContainer
                                activeFeedback?.isCorrect == false -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            animationSpec = androidx.compose.animation.core.tween(180),
                            label = "cellColor",
                        )
                        val borderColor by animateColorAsState(
                            targetValue = when {
                                activeFeedback?.isCorrect == true -> MaterialTheme.colorScheme.tertiary
                                activeFeedback?.isCorrect == false -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            animationSpec = androidx.compose.animation.core.tween(180),
                            label = "cellBorder",
                        )

                        LaunchedEffect(activeFeedback?.sequence) {
                            val currentFeedback = activeFeedback ?: return@LaunchedEffect
                            delay(800L)
                            if (feedback == currentFeedback) feedback = null
                        }

                        Surface(
                            onClick = {
                                feedback = CellFeedback(
                                    isCorrect = onNumberClick(number),
                                    sequence = SystemClock.elapsedRealtimeNanos(),
                                )
                            },
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            shape = RoundedCornerShape(if (gridSize <= 5) 10.dp else 6.dp),
                            color = cellColor,
                            border = BorderStroke(if (activeFeedback != null) 2.dp else 1.dp, borderColor),
                            shadowElevation = if (gridSize <= 5) 1.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = number.toString(),
                                    fontSize = numberSize,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        activeFeedback?.isCorrect == true -> MaterialTheme.colorScheme.onTertiaryContainer
                                        activeFeedback?.isCorrect == false -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultDialog(
    score: ScoreRecord,
    isNewBest: Boolean,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text("✓", modifier = Modifier.padding(14.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                if (isNewBest) "新的最佳成绩！" else "训练完成",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(score.elapsedMillis),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("${score.gridSize} × ${score.gridSize}  ·  错误 ${score.errors} 次")
                Spacer(Modifier.height(8.dp))
                Text(
                    "继续练习，尝试在保持准确的同时缩短用时。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { Button(onClick = onPlayAgain) { Text("再来一局") } },
        dismissButton = { TextButton(onClick = onHome) { Text("返回首页") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDialog(
    scores: List<ScoreRecord>,
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf("statistics") }
    var selectedSize by rememberSaveable { mutableStateOf<Int?>(null) }
    val filteredScores = remember(scores, selectedSize) {
        selectedSize?.let { size -> scores.filter { it.gridSize == size } } ?: scores
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("返回") }
                    Text(
                        "训练数据",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = onExport,
                        enabled = !isExporting,
                    ) { Text(if (isExporting) "导出中" else "导出") }
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = scores.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("清空") }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = selectedSection == "statistics",
                        onClick = { selectedSection = "statistics" },
                        label = {
                            Text("趋势统计", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = selectedSection == "records",
                        onClick = { selectedSection = "records" },
                        label = {
                            Text("训练记录", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (selectedSection == "statistics") {
                    TrainingStatisticsContent(
                        scores = scores,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedSize == null,
                                onClick = { selectedSize = null },
                                label = { Text("全部 ${scores.size}") },
                            )
                            (MIN_GRID_SIZE..MAX_GRID_SIZE).forEach { size ->
                                val count = scores.count { it.gridSize == size }
                                FilterChip(
                                    selected = selectedSize == size,
                                    onClick = { selectedSize = size },
                                    label = { Text("${size}×${size}  $count") },
                                )
                            }
                        }

                        if (filteredScores.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (scores.isEmpty()) "完成一局训练后，成绩会显示在这里。" else "该难度还没有成绩。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                            ) {
                                itemsIndexed(
                                    items = filteredScores,
                                    key = { index, score -> if (score.id != 0L) score.id else "legacy-$index" },
                                ) { index, score ->
                                    HistoryRow(score)
                                    if (index != filteredScores.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部成绩？") },
            text = { Text("此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryRow(score: ScoreRecord) {
    val date = remember(score.completedAtMillis) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(score.completedAtMillis))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                "${score.gridSize}×${score.gridSize}",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(formatDuration(score.elapsedMillis), fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "错误 ${score.errors}",
            style = MaterialTheme.typography.bodySmall,
            color = if (score.errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
