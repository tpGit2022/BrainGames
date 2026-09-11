package com.seeksky.braingames

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MathGameStatus { Ready, Playing, Finished }

@Composable
fun MathGameApp(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scoreStore = remember(context) { MathScoreStore(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var difficulty by remember { mutableStateOf(MathDifficulty.Easy) }
    var status by remember { mutableStateOf(MathGameStatus.Ready) }
    var questions by remember { mutableStateOf(emptyList<MathQuestion>()) }
    var questionIndex by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var completedRecord by remember { mutableStateOf<MathBestRecord?>(null) }
    var isNewBest by remember { mutableStateOf(false) }
    var showQuitConfirmation by remember { mutableStateOf(false) }
    var bestRecord by remember(difficulty) { mutableStateOf(scoreStore.getBest(difficulty)) }

    fun startGame() {
        questions = generateMathRound(difficulty)
        questionIndex = 0
        correctAnswers = 0
        selectedAnswer = null
        elapsedMillis = 0L
        completedRecord = null
        startedAt = SystemClock.elapsedRealtime()
        status = MathGameStatus.Playing
    }

    fun returnToSetup() {
        status = MathGameStatus.Ready
        completedRecord = null
        selectedAnswer = null
    }

    fun finishGame(finalCorrectAnswers: Int) {
        val finalElapsed = SystemClock.elapsedRealtime() - startedAt
        val record = MathBestRecord(finalCorrectAnswers, finalElapsed)
        elapsedMillis = finalElapsed
        isNewBest = scoreStore.updateBest(difficulty, record)
        if (isNewBest) bestRecord = record
        completedRecord = record
        status = MathGameStatus.Finished
    }

    LaunchedEffect(status, startedAt) {
        while (status == MathGameStatus.Playing) {
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            delay(50L)
        }
    }

    BackHandler(enabled = status == MathGameStatus.Playing) {
        showQuitConfirmation = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            MathHeader(
                isPlaying = status == MathGameStatus.Playing,
                onBack = {
                    if (status == MathGameStatus.Playing) showQuitConfirmation = true
                    else onNavigateBack()
                },
            )
        },
    ) { contentPadding ->
        when (status) {
            MathGameStatus.Ready -> MathReadyContent(
                difficulty = difficulty,
                bestRecord = bestRecord,
                onDifficultySelected = { selectedDifficulty ->
                    difficulty = selectedDifficulty
                },
                onStart = ::startGame,
                modifier = Modifier.padding(contentPadding),
            )

            MathGameStatus.Playing,
            MathGameStatus.Finished,
            -> if (questions.isNotEmpty()) {
                MathPlayingContent(
                    question = questions[questionIndex],
                    questionNumber = questionIndex + 1,
                    correctAnswers = correctAnswers,
                    elapsedMillis = elapsedMillis,
                    selectedAnswer = selectedAnswer,
                    enabled = status == MathGameStatus.Playing && selectedAnswer == null,
                    onAnswer = { answer ->
                        if (selectedAnswer != null) return@MathPlayingContent
                        selectedAnswer = answer
                        val nextCorrect = correctAnswers + if (answer == questions[questionIndex].answer) 1 else 0
                        correctAnswers = nextCorrect
                        coroutineScope.launch {
                            delay(650L)
                            if (status != MathGameStatus.Playing) return@launch
                            if (questionIndex == questions.lastIndex) {
                                finishGame(nextCorrect)
                            } else {
                                questionIndex += 1
                                selectedAnswer = null
                            }
                        }
                    },
                    onQuit = { showQuitConfirmation = true },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }

    completedRecord?.let { record ->
        MathResultDialog(
            difficulty = difficulty,
            record = record,
            isNewBest = isNewBest,
            onPlayAgain = ::startGame,
            onSetup = ::returnToSetup,
        )
    }

    if (showQuitConfirmation) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmation = false },
            title = { Text("结束本局？") },
            text = { Text("当前答题进度不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirmation = false
                    returnToSetup()
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirmation = false }) { Text("继续答题") }
            },
        )
    }
}

@Composable
private fun MathHeader(
    isPlaying: Boolean,
    onBack: () -> Unit,
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
                "逻辑运算",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(64.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MathReadyContent(
    difficulty: MathDifficulty,
    bestRecord: MathBestRecord?,
    onDifficultySelected: (MathDifficulty) -> Unit,
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
            "十题，看看你能走多快",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "看清运算顺序，从四个答案中作出判断。答错也会继续，最终成绩由正确数和用时共同决定。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(28.dp))
        Text("选择难度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MathDifficulty.entries.chunked(2).forEach { difficulties ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    difficulties.forEach { item ->
                        FilterChip(
                            selected = difficulty == item,
                            onClick = { onDifficultySelected(item) },
                            label = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(item.title, fontWeight = FontWeight.Bold)
                                    Text(item.rule, style = MaterialTheme.typography.labelSmall)
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
                        bestRecord?.let { "${it.correctAnswers} / $MATH_QUESTION_COUNT" } ?: "尚无记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                bestRecord?.let {
                    Text(
                        formatMathDuration(it.elapsedMillis),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
private fun MathPlayingContent(
    question: MathQuestion,
    questionNumber: Int,
    correctAnswers: Int,
    elapsedMillis: Long,
    selectedAnswer: Int?,
    enabled: Boolean,
    onAnswer: (Int) -> Unit,
    onQuit: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MathStatCard("题目", "$questionNumber / $MATH_QUESTION_COUNT", Modifier.weight(1f))
            MathStatCard("答对", correctAnswers.toString(), Modifier.weight(1f))
            MathStatCard("用时", formatMathDuration(elapsedMillis), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { questionNumber / MATH_QUESTION_COUNT.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.tertiary,
        )

        Spacer(Modifier.weight(1f))
        Text(
            "计算结果",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            question.expression,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 34.sp,
            lineHeight = 46.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text("= ?", fontSize = 24.sp, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            question.options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowOptions.forEach { option ->
                        AnswerOption(
                            value = option,
                            isCorrectAnswer = option == question.answer,
                            selectedAnswer = selectedAnswer,
                            enabled = enabled,
                            onClick = { onAnswer(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onQuit) { Text("结束本局") }
    }
}

@Composable
private fun AnswerOption(
    value: Int,
    isCorrectAnswer: Boolean,
    selectedAnswer: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wasSelected = selectedAnswer == value
    val revealCorrect = selectedAnswer != null && isCorrectAnswer
    val targetColor = when {
        revealCorrect -> MaterialTheme.colorScheme.tertiaryContainer
        wasSelected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val targetBorder = when {
        revealCorrect -> MaterialTheme.colorScheme.tertiary
        wasSelected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val backgroundColor by animateColorAsState(targetColor, label = "answerBackground")
    val borderColor by animateColorAsState(targetBorder, label = "answerBorder")

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(if (revealCorrect || wasSelected) 2.dp else 1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                value.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    revealCorrect -> MaterialTheme.colorScheme.onTertiaryContainer
                    wasSelected -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun MathStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
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
private fun MathResultDialog(
    difficulty: MathDifficulty,
    record: MathBestRecord,
    isNewBest: Boolean,
    onPlayAgain: () -> Unit,
    onSetup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    if (record.correctAnswers == MATH_QUESTION_COUNT) "✓" else record.correctAnswers.toString(),
                    modifier = Modifier.padding(14.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        title = {
            Text(
                if (isNewBest) "新的最佳成绩！" else "挑战完成",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${record.correctAnswers} / $MATH_QUESTION_COUNT",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text("${difficulty.title} · ${formatMathDuration(record.elapsedMillis)}")
                Spacer(Modifier.height(8.dp))
                Text(
                    resultMessage(record.correctAnswers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { Button(onClick = onPlayAgain) { Text("再来一局") } },
        dismissButton = { TextButton(onClick = onSetup) { Text("调整难度") } },
    )
}

internal fun formatMathDuration(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000f
    return "%.1f 秒".format(totalSeconds)
}

private fun resultMessage(correctAnswers: Int): String = when (correctAnswers) {
    MATH_QUESTION_COUNT -> "全部正确，下一步试着在保持准确的同时缩短用时。"
    in 7 until MATH_QUESTION_COUNT -> "已经很接近满分，留意括号和先乘除后加减。"
    in 4..6 -> "节奏不错，先保证运算顺序，再逐步提高速度。"
    else -> "放慢一点，把每道题拆成一步一步来判断。"
}
