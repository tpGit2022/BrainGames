package com.seeksky.braingames

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun Game2048App(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scoreStore = remember(context) { Game2048ScoreStore(context.applicationContext) }
    var board by remember { mutableStateOf(createGame2048()) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(scoreStore.getBest()) }
    var hasAcknowledgedWin by remember { mutableStateOf(false) }
    var showWinDialog by remember { mutableStateOf(false) }
    var showGameOverDialog by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Game2048Confirmation?>(null) }

    fun newGame() {
        board = createGame2048()
        score = 0
        hasAcknowledgedWin = false
        showWinDialog = false
        showGameOverDialog = false
        confirmation = null
    }

    fun move(direction: Game2048Direction) {
        if (showWinDialog || showGameOverDialog || confirmation != null) return
        val result = move2048(board, direction)
        if (!result.moved) return

        val nextBoard = addRandom2048Tile(result.board)
        val nextScore = score + result.scoreGain
        board = nextBoard
        score = nextScore
        if (scoreStore.updateBest(nextScore)) bestScore = nextScore

        if (!hasAcknowledgedWin && hasWon2048(nextBoard)) showWinDialog = true
        else if (isGameOver2048(nextBoard)) showGameOverDialog = true
    }

    fun requestExit() {
        confirmation = Game2048Confirmation.Exit
    }

    BackHandler(onBack = ::requestExit)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Game2048Header(
                onBack = ::requestExit,
                onNewGame = { confirmation = Game2048Confirmation.Restart },
            )
        },
    ) { contentPadding ->
        Game2048Content(
            board = board,
            score = score,
            bestScore = bestScore,
            onMove = ::move,
            modifier = Modifier.padding(contentPadding),
        )
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("达成 2048！") },
            text = { Text("你已经合成 2048，当前得分 $score。还可以继续挑战更大的数字。") },
            confirmButton = {
                TextButton(onClick = {
                    hasAcknowledgedWin = true
                    showWinDialog = false
                    if (isGameOver2048(board)) showGameOverDialog = true
                }) { Text("继续挑战") }
            },
            dismissButton = { TextButton(onClick = ::newGame) { Text("再来一局") } },
        )
    }

    if (showGameOverDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("本局结束") },
            text = { Text("棋盘已经没有可合并的位置，本局得分 $score。") },
            confirmButton = { TextButton(onClick = ::newGame) { Text("再来一局") } },
            dismissButton = { TextButton(onClick = onNavigateBack) { Text("返回首页") } },
        )
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (pending == Game2048Confirmation.Exit) "退出游戏？" else "重新开始？") },
            text = { Text("当前棋盘和分数不会保留，历史最高分不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    if (pending == Game2048Confirmation.Exit) onNavigateBack() else newGame()
                }) { Text(if (pending == Game2048Confirmation.Exit) "退出" else "重新开始") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("继续游戏") } },
        )
    }
}

private enum class Game2048Confirmation { Exit, Restart }

@Composable
private fun Game2048Header(onBack: () -> Unit, onNewGame: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                "2048",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onNewGame) { Text("新游戏") }
        }
    }
}

@Composable
private fun Game2048Content(
    board: IntArray,
    score: Int,
    bestScore: Int,
    onMove: (Game2048Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Game2048Stat("得分", score, Modifier.weight(1f))
            Game2048Stat("最高分", bestScore, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Game2048Board(
                board = board,
                onMove = onMove,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
        }
        Spacer(Modifier.height(14.dp))
        Game2048DirectionPad(onMove)
    }
}

@Composable
private fun Game2048Stat(label: String, value: Int, modifier: Modifier = Modifier) {
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
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Game2048Board(
    board: IntArray,
    onMove: (Game2048Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragAmount by remember { mutableStateOf(Offset.Zero) }
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragAmount = Offset.Zero },
                    onDragEnd = {
                        val threshold = 36.dp.toPx()
                        val horizontal = abs(dragAmount.x) > abs(dragAmount.y)
                        when {
                            horizontal && dragAmount.x > threshold -> onMove(Game2048Direction.Right)
                            horizontal && dragAmount.x < -threshold -> onMove(Game2048Direction.Left)
                            !horizontal && dragAmount.y > threshold -> onMove(Game2048Direction.Down)
                            !horizontal && dragAmount.y < -threshold -> onMove(Game2048Direction.Up)
                        }
                        dragAmount = Offset.Zero
                    },
                    onDragCancel = { dragAmount = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        dragAmount += amount
                    },
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF8D8178),
        border = BorderStroke(1.dp, Color(0xFF756A62)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            board.toList().chunked(GAME_2048_SIZE).forEach { row ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { value ->
                        Game2048Tile(value, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun Game2048Tile(value: Int, modifier: Modifier = Modifier) {
    val background = when (value) {
        0 -> Color(0xFFB8AEA5)
        2 -> Color(0xFFF2ECE4)
        4 -> Color(0xFFEAE0CC)
        8 -> Color(0xFFF1B56B)
        16 -> Color(0xFFED9258)
        32 -> Color(0xFFE97053)
        64 -> Color(0xFFD94C3D)
        128 -> Color(0xFFE5C45A)
        256 -> Color(0xFFDDB13F)
        512 -> Color(0xFFD59F2B)
        1024 -> Color(0xFFC78424)
        else -> Color(0xFF3C3A4A)
    }
    val foreground = if (value <= 4) Color(0xFF4C4742) else Color.White
    val fontSize = when {
        value < 100 -> 30.sp
        value < 1_000 -> 26.sp
        value < 10_000 -> 21.sp
        else -> 17.sp
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = background) {
        Box(contentAlignment = Alignment.Center) {
            if (value != 0) {
                Text(value.toString(), color = foreground, fontSize = fontSize, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun Game2048DirectionPad(onMove: (Game2048Direction) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { onMove(Game2048Direction.Up) },
            modifier = Modifier.size(width = 64.dp, height = 48.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("↑", fontSize = 24.sp) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { onMove(Game2048Direction.Left) },
                modifier = Modifier.size(width = 64.dp, height = 48.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("←", fontSize = 24.sp) }
            Button(
                onClick = { onMove(Game2048Direction.Down) },
                modifier = Modifier.size(width = 64.dp, height = 48.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("↓", fontSize = 24.sp) }
            Button(
                onClick = { onMove(Game2048Direction.Right) },
                modifier = Modifier.size(width = 64.dp, height = 48.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("→", fontSize = 24.sp) }
        }
    }
}
