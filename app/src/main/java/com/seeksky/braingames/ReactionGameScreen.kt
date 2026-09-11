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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ReactionGameStatus { Ready, Playing, Finished }

@Composable
fun ReactionGameApp(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scoreStore = remember(context) { ReactionScoreStore(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var scores by remember { mutableStateOf(scoreStore.getScores()) }
    var status by remember { mutableStateOf(ReactionGameStatus.Ready) }
    var trials by remember { mutableStateOf(emptyList<ReactionTrial>()) }
    var trialIndex by remember { mutableIntStateOf(0) }
    var correctAnswers by remember { mutableIntStateOf(0) }
    var totalReactionMillis by remember { mutableLongStateOf(0L) }
    var presentedAt by remember { mutableLongStateOf(0L) }
    var selectedAnswer by remember { mutableStateOf<Boolean?>(null) }
    var completedRecord by remember { mutableStateOf<ReactionBestRecord?>(null) }
    var bestRecord by remember { mutableStateOf(scoreStore.getBest()) }
    var isNewBest by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showQuitConfirmation by remember { mutableStateOf(false) }

    fun startGame() {
        trials = generateReactionRound()
        trialIndex = 0
        correctAnswers = 0
        totalReactionMillis = 0L
        presentedAt = 0L
        selectedAnswer = null
        completedRecord = null
        status = ReactionGameStatus.Playing
    }

    fun returnToReady() {
        status = ReactionGameStatus.Ready
        selectedAnswer = null
        completedRecord = null
        presentedAt = 0L
    }

    fun finishGame(finalCorrect: Int, finalTotalReactionMillis: Long) {
        val record = ReactionBestRecord(
            correctAnswers = finalCorrect,
            averageReactionMillis = finalTotalReactionMillis / REACTION_TRIAL_COUNT,
        )
        val score = ReactionScoreRecord(
            correctAnswers = record.correctAnswers,
            averageReactionMillis = record.averageReactionMillis,
            completedAtMillis = System.currentTimeMillis(),
        )
        isNewBest = scoreStore.add(score)
        scores = scoreStore.getScores()
        if (isNewBest) bestRecord = record
        completedRecord = record
        status = ReactionGameStatus.Finished
    }

    LaunchedEffect(status, trialIndex, selectedAnswer) {
        if (status == ReactionGameStatus.Playing && selectedAnswer == null) {
            presentedAt = SystemClock.elapsedRealtime()
        }
    }

    BackHandler(enabled = status == ReactionGameStatus.Playing) {
        showQuitConfirmation = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ReactionHeader(
                isPlaying = status == ReactionGameStatus.Playing,
                onBack = {
                    if (status == ReactionGameStatus.Playing) showQuitConfirmation = true
                    else onNavigateBack()
                },
                onDataClick = { showData = true },
            )
        },
    ) { contentPadding ->
        when (status) {
            ReactionGameStatus.Ready -> ReactionReadyContent(
                bestRecord = bestRecord,
                onStart = ::startGame,
                modifier = Modifier.padding(contentPadding),
            )

            ReactionGameStatus.Playing,
            ReactionGameStatus.Finished,
            -> if (trials.isNotEmpty()) {
                val answeredCount = trialIndex + if (selectedAnswer != null) 1 else 0
                ReactionPlayingContent(
                    trial = trials[trialIndex],
                    trialNumber = trialIndex + 1,
                    correctAnswers = correctAnswers,
                    averageReactionMillis = if (answeredCount == 0) null else {
                        totalReactionMillis / answeredCount
                    },
                    selectedAnswer = selectedAnswer,
                    enabled = status == ReactionGameStatus.Playing &&
                        selectedAnswer == null && presentedAt > 0L,
                    onAnswer = { answer ->
                        if (selectedAnswer != null || presentedAt == 0L) {
                            return@ReactionPlayingContent
                        }
                        val reactionMillis = SystemClock.elapsedRealtime() - presentedAt
                        val isCorrect = answer == trials[trialIndex].correctAnswer
                        val nextCorrect = correctAnswers + if (isCorrect) 1 else 0
                        val nextTotalReaction = totalReactionMillis + reactionMillis
                        selectedAnswer = answer
                        correctAnswers = nextCorrect
                        totalReactionMillis = nextTotalReaction
                        coroutineScope.launch {
                            delay(450L)
                            if (status != ReactionGameStatus.Playing) return@launch
                            if (trialIndex == trials.lastIndex) {
                                finishGame(nextCorrect, nextTotalReaction)
                            } else {
                                trialIndex += 1
                                presentedAt = 0L
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
        ReactionResultDialog(
            record = record,
            isNewBest = isNewBest,
            onPlayAgain = ::startGame,
            onReady = ::returnToReady,
        )
    }

    if (showData) {
        ReactionTrainingDataDialog(
            scores = scores,
            onDismiss = { showData = false },
            onClear = {
                scoreStore.clear()
                scores = emptyList()
                bestRecord = null
            },
        )
    }

    if (showQuitConfirmation) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmation = false },
            title = { Text("结束本局？") },
            text = { Text("当前反应训练不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirmation = false
                    returnToReady()
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirmation = false }) { Text("继续训练") }
            },
        )
    }
}

@Composable
private fun ReactionHeader(
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
                "双重判断",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (isPlaying) {
                Spacer(Modifier.size(64.dp))
            } else {
                TextButton(onClick = onDataClick) { Text("数据") }
            }
        }
    }
}

@Composable
private fun ReactionReadyContent(
    bestRecord: ReactionBestRecord?,
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
            "看对规则，再出手",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "每次会出现一个数字字母组合。问题会随机切换，判断数字是否为偶数，或字母是否为元音。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(28.dp))
        RuleRow(symbol = "8", question = "数字是偶数吗？", answer = "是")
        Spacer(Modifier.height(10.dp))
        RuleRow(symbol = "E", question = "字母是元音吗？", answer = "是")

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
                    Text("最佳成绩", style = MaterialTheme.typography.labelLarge)
                    Text(
                        bestRecord?.let { "${it.correctAnswers} / $REACTION_TRIAL_COUNT" } ?: "尚无记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                bestRecord?.let {
                    Text(
                        "平均 ${formatReactionTime(it.averageReactionMillis)}",
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
            Text("开始训练", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RuleRow(symbol: String, question: String, answer: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Text(question, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(answer, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReactionPlayingContent(
    trial: ReactionTrial,
    trialNumber: Int,
    correctAnswers: Int,
    averageReactionMillis: Long?,
    selectedAnswer: Boolean?,
    enabled: Boolean,
    onAnswer: (Boolean) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numberColor = MaterialTheme.colorScheme.primary
    val letterColor = MaterialTheme.colorScheme.tertiary

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
            ReactionStatCard("进度", "$trialNumber / $REACTION_TRIAL_COUNT", Modifier.weight(1f))
            ReactionStatCard("答对", correctAnswers.toString(), Modifier.weight(1f))
            ReactionStatCard(
                "平均反应",
                averageReactionMillis?.let(::formatReactionTime) ?: "--",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { trialNumber / REACTION_TRIAL_COUNT.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.secondary,
        )

        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = numberColor)) {
                        append(trial.number.toString())
                    }
                    withStyle(SpanStyle(color = letterColor)) {
                        append(trial.letter.toString())
                    }
                },
                fontSize = 76.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            trial.rule.question,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            selectedAnswer?.let { answer ->
                val correct = answer == trial.correctAnswer
                Text(
                    if (correct) "正确" else "正确答案：${if (trial.correctAnswer) "是" else "否"}",
                    color = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReactionAnswerButton(
                text = "是",
                answer = true,
                correctAnswer = trial.correctAnswer,
                selectedAnswer = selectedAnswer,
                enabled = enabled,
                onClick = { onAnswer(true) },
                modifier = Modifier.weight(1f),
            )
            ReactionAnswerButton(
                text = "否",
                answer = false,
                correctAnswer = trial.correctAnswer,
                selectedAnswer = selectedAnswer,
                enabled = enabled,
                onClick = { onAnswer(false) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onQuit) { Text("结束本局") }
    }
}

@Composable
private fun ReactionAnswerButton(
    text: String,
    answer: Boolean,
    correctAnswer: Boolean,
    selectedAnswer: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wasSelected = selectedAnswer == answer
    val revealCorrect = selectedAnswer != null && answer == correctAnswer
    val backgroundTarget = when {
        revealCorrect -> MaterialTheme.colorScheme.tertiaryContainer
        wasSelected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderTarget = when {
        revealCorrect -> MaterialTheme.colorScheme.tertiary
        wasSelected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val background by animateColorAsState(backgroundTarget, label = "reactionAnswerBackground")
    val border by animateColorAsState(borderTarget, label = "reactionAnswerBorder")

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(8.dp),
        color = background,
        border = BorderStroke(if (revealCorrect || wasSelected) 2.dp else 1.dp, border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 22.sp,
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
private fun ReactionStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ReactionResultDialog(
    record: ReactionBestRecord,
    isNewBest: Boolean,
    onPlayAgain: () -> Unit,
    onReady: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    record.correctAnswers.toString(),
                    modifier = Modifier.padding(14.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        title = {
            Text(
                if (isNewBest) "新的最佳成绩！" else "训练完成",
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
                    "${record.correctAnswers} / $REACTION_TRIAL_COUNT",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                Text("平均反应 ${formatReactionTime(record.averageReactionMillis)}")
                Spacer(Modifier.height(8.dp))
                Text(
                    reactionResultMessage(record.correctAnswers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { Button(onClick = onPlayAgain) { Text("再来一局") } },
        dismissButton = { TextButton(onClick = onReady) { Text("返回") } },
    )
}

internal fun formatReactionTime(milliseconds: Long): String = "$milliseconds 毫秒"

private fun reactionResultMessage(correctAnswers: Int): String = when (correctAnswers) {
    REACTION_TRIAL_COUNT -> "判断全部正确，下一局试着进一步缩短反应时间。"
    in 16 until REACTION_TRIAL_COUNT -> "表现很稳，规则切换时再多留意当前问题。"
    in 10..15 -> "速度之外也要保持准确，先看问题再判断组合。"
    else -> "放慢一点，先锁定问题问的是数字还是字母。"
}
