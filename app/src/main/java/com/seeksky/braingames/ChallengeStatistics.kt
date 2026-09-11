package com.seeksky.braingames

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val MAX_CHALLENGE_CHART_SESSIONS = 20

private enum class TrainingDataSection { Statistics, Records }

internal data class ChallengeMetricPoint(
    val sessionNumber: Int,
    val accuracyPercent: Float,
    val secondaryValue: Float,
    val errors: Float,
    val completedAtMillis: Long,
)

internal fun buildMathMetricPoints(
    scores: List<MathScoreRecord>,
    difficulty: MathDifficulty,
): List<ChallengeMetricPoint> = scores
    .filter { it.difficulty == difficulty }
    .sortedBy { it.completedAtMillis }
    .mapIndexed { index, score ->
        ChallengeMetricPoint(
            sessionNumber = index + 1,
            accuracyPercent = score.correctAnswers * 100f / MATH_QUESTION_COUNT,
            secondaryValue = score.elapsedMillis / 1_000f,
            errors = (MATH_QUESTION_COUNT - score.correctAnswers).toFloat(),
            completedAtMillis = score.completedAtMillis,
        )
    }

internal fun buildReactionMetricPoints(
    scores: List<ReactionScoreRecord>,
): List<ChallengeMetricPoint> = scores
    .sortedBy { it.completedAtMillis }
    .mapIndexed { index, score ->
        ChallengeMetricPoint(
            sessionNumber = index + 1,
            accuracyPercent = score.correctAnswers * 100f / REACTION_TRIAL_COUNT,
            secondaryValue = score.averageReactionMillis.toFloat(),
            errors = (REACTION_TRIAL_COUNT - score.correctAnswers).toFloat(),
            completedAtMillis = score.completedAtMillis,
        )
    }

@Composable
internal fun MathTrainingDataDialog(
    scores: List<MathScoreRecord>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    TrainingDataDialog(
        hasScores = scores.isNotEmpty(),
        onDismiss = onDismiss,
        onClear = onClear,
        statisticsContent = { MathStatisticsContent(scores) },
        recordsContent = { MathRecordsContent(scores) },
    )
}

@Composable
internal fun ReactionTrainingDataDialog(
    scores: List<ReactionScoreRecord>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    TrainingDataDialog(
        hasScores = scores.isNotEmpty(),
        onDismiss = onDismiss,
        onClear = onClear,
        statisticsContent = { ReactionStatisticsContent(scores) },
        recordsContent = { ReactionRecordsContent(scores) },
    )
}

@Composable
private fun TrainingDataDialog(
    hasScores: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    statisticsContent: @Composable () -> Unit,
    recordsContent: @Composable () -> Unit,
) {
    var selectedSection by rememberSaveable { mutableStateOf(TrainingDataSection.Statistics) }
    var confirmClear by remember { mutableStateOf(false) }

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
                        onClick = { confirmClear = true },
                        enabled = hasScores,
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
                        selected = selectedSection == TrainingDataSection.Statistics,
                        onClick = { selectedSection = TrainingDataSection.Statistics },
                        label = {
                            Text("趋势统计", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = selectedSection == TrainingDataSection.Records,
                        onClick = { selectedSection = TrainingDataSection.Records },
                        label = {
                            Text("训练记录", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selectedSection == TrainingDataSection.Statistics) {
                    statisticsContent()
                } else {
                    recordsContent()
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
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MathStatisticsContent(scores: List<MathScoreRecord>) {
    val availableDifficulties = remember(scores) {
        MathDifficulty.entries.associateWith { difficulty ->
            scores.count { it.difficulty == difficulty }
        }.filterValues { it > 0 }
    }
    var selectedDifficultyName by rememberSaveable {
        mutableStateOf(scores.firstOrNull()?.difficulty?.name ?: MathDifficulty.Easy.name)
    }
    val selectedDifficulty = selectedDifficultyName
        .let { name -> MathDifficulty.entries.firstOrNull { it.name == name } }
        ?.takeIf { it in availableDifficulties }
        ?: scores.firstOrNull()?.difficulty

    if (selectedDifficulty == null) {
        EmptyChallengeStatistics("完成逻辑运算后，这里会展示正确率、用时和错误题数的变化。")
        return
    }

    val points = remember(scores, selectedDifficulty) {
        buildMathMetricPoints(scores, selectedDifficulty)
    }
    ChallengeStatisticsContent(
        points = points,
        secondarySummaryLabel = "最近用时",
        secondaryChartTitle = "完成用时",
        secondaryChartSubtitle = "完成十道题的总用时，越低越好",
        secondaryFormatter = { "${formatChallengeMetric(it, 1)} 秒" },
        secondaryProgressLabel = "用时",
        filterContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableDifficulties.forEach { (difficulty, count) ->
                    FilterChip(
                        selected = selectedDifficulty == difficulty,
                        onClick = { selectedDifficultyName = difficulty.name },
                        label = { Text("${difficulty.title}  $count") },
                    )
                }
            }
        },
    )
}

@Composable
private fun ReactionStatisticsContent(scores: List<ReactionScoreRecord>) {
    val points = remember(scores) { buildReactionMetricPoints(scores) }
    if (points.isEmpty()) {
        EmptyChallengeStatistics("完成双重判断后，这里会展示正确率、反应时间和错误题数的变化。")
        return
    }
    ChallengeStatisticsContent(
        points = points,
        secondarySummaryLabel = "最近反应",
        secondaryChartTitle = "平均反应时间",
        secondaryChartSubtitle = "每题平均作答时间，越低越好",
        secondaryFormatter = { "${it.toLong()} 毫秒" },
        secondaryProgressLabel = "反应时间",
    )
}

@Composable
private fun ChallengeStatisticsContent(
    points: List<ChallengeMetricPoint>,
    secondarySummaryLabel: String,
    secondaryChartTitle: String,
    secondaryChartSubtitle: String,
    secondaryFormatter: (Float) -> String,
    secondaryProgressLabel: String,
    filterContent: (@Composable () -> Unit)? = null,
) {
    val visiblePoints = points.takeLast(MAX_CHALLENGE_CHART_SESSIONS)
    val first = points.first()
    val latest = points.last()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        filterContent?.let { content -> item { content() } }
        item {
            ChallengeSummary(
                latest = latest,
                sessionCount = points.size,
                averageAccuracy = points.map { it.accuracyPercent }.average().toFloat(),
                secondarySummaryLabel = secondarySummaryLabel,
                secondaryFormatter = secondaryFormatter,
            )
        }
        if (points.size > 1) {
            item {
                ChallengeProgressSummary(
                    first = first,
                    latest = latest,
                    secondaryLabel = secondaryProgressLabel,
                )
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "再完成一次训练，就能看到变化趋势。",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        item {
            val suffix = if (points.size > MAX_CHALLENGE_CHART_SESSIONS) {
                " · 显示最近 $MAX_CHALLENGE_CHART_SESSIONS 次"
            } else {
                ""
            }
            Text(
                "每次训练的变化$suffix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            LineChartCard(
                title = "正确率",
                subtitle = "正确答案占全部题目的比例，越高越好",
                values = visiblePoints.map { it.accuracyPercent },
                pointNumbers = visiblePoints.map { it.sessionNumber },
                valueFormatter = { "${formatChallengeMetric(it, 1)}%" },
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        item {
            LineChartCard(
                title = secondaryChartTitle,
                subtitle = secondaryChartSubtitle,
                values = visiblePoints.map { it.secondaryValue },
                pointNumbers = visiblePoints.map { it.sessionNumber },
                valueFormatter = secondaryFormatter,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            BarChartCard(
                title = "错误题数",
                subtitle = "每次训练中的错误答案，越低越好",
                values = visiblePoints.map { it.errors },
                pointNumbers = visiblePoints.map { it.sessionNumber },
                color = MaterialTheme.colorScheme.error,
            )
        }
        item {
            val latestDate = remember(latest.completedAtMillis) {
                formatChallengeDate(latest.completedAtMillis, "yyyy/MM/dd HH:mm")
            }
            Text(
                "图表按完成时间从左到右排列 · 最近训练 $latestDate",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChallengeSummary(
    latest: ChallengeMetricPoint,
    sessionCount: Int,
    averageAccuracy: Float,
    secondarySummaryLabel: String,
    secondaryFormatter: (Float) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChallengeSummaryCard(
                label = "最近正确率",
                value = "${formatChallengeMetric(latest.accuracyPercent, 1)}%",
                modifier = Modifier.weight(1f),
            )
            ChallengeSummaryCard(
                label = secondarySummaryLabel,
                value = secondaryFormatter(latest.secondaryValue),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChallengeSummaryCard(
                label = "平均正确率",
                value = "${formatChallengeMetric(averageAccuracy, 1)}%",
                modifier = Modifier.weight(1f),
            )
            ChallengeSummaryCard(
                label = "累计训练",
                value = "$sessionCount 次",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChallengeSummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChallengeProgressSummary(
    first: ChallengeMetricPoint,
    latest: ChallengeMetricPoint,
    secondaryLabel: String,
) {
    val accuracyChange = latest.accuracyPercent - first.accuracyPercent
    val secondaryChange = percentChange(first.secondaryValue, latest.secondaryValue) ?: 0f
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("相比第一次", style = MaterialTheme.typography.labelLarge)
            Text(
                challengeChangeLabel(
                    positiveLabel = "正确率提升",
                    negativeLabel = "正确率下降",
                    flatLabel = "正确率基本持平",
                    change = accuracyChange,
                    suffix = " 个百分点",
                ),
                fontWeight = FontWeight.Bold,
                color = challengeChangeColor(accuracyChange, positiveIsGood = true),
            )
            Text(
                challengeChangeLabel(
                    positiveLabel = "$secondaryLabel 增加",
                    negativeLabel = "$secondaryLabel 缩短",
                    flatLabel = "$secondaryLabel 基本持平",
                    change = secondaryChange,
                    suffix = "%",
                ),
                fontWeight = FontWeight.Bold,
                color = challengeChangeColor(secondaryChange, positiveIsGood = false),
            )
        }
    }
}

@Composable
private fun challengeChangeColor(change: Float, positiveIsGood: Boolean): Color {
    val improved = if (positiveIsGood) change > 0f else change < 0f
    return when {
        abs(change) < 0.05f -> MaterialTheme.colorScheme.onPrimaryContainer
        improved -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}

private fun challengeChangeLabel(
    positiveLabel: String,
    negativeLabel: String,
    flatLabel: String,
    change: Float,
    suffix: String,
): String {
    if (abs(change) < 0.05f) return flatLabel
    val label = if (change > 0f) positiveLabel else negativeLabel
    return "$label ${formatChallengeMetric(abs(change), 1)}$suffix"
}

@Composable
private fun EmptyChallengeStatistics(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有可统计的数据", fontWeight = FontWeight.Bold)
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MathRecordsContent(scores: List<MathScoreRecord>) {
    var selectedDifficultyName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDifficulty = selectedDifficultyName?.let { name ->
        MathDifficulty.entries.firstOrNull { it.name == name }
    }
    val filteredScores = remember(scores, selectedDifficulty) {
        selectedDifficulty?.let { difficulty -> scores.filter { it.difficulty == difficulty } } ?: scores
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedDifficulty == null,
                onClick = { selectedDifficultyName = null },
                label = { Text("全部 ${scores.size}") },
            )
            MathDifficulty.entries.forEach { difficulty ->
                val count = scores.count { it.difficulty == difficulty }
                FilterChip(
                    selected = selectedDifficulty == difficulty,
                    onClick = { selectedDifficultyName = difficulty.name },
                    label = { Text("${difficulty.title}  $count") },
                )
            }
        }
        ChallengeRecordsList(
            isEmpty = filteredScores.isEmpty(),
            emptyMessage = if (scores.isEmpty()) "完成一局逻辑运算后，成绩会显示在这里。" else "该难度还没有成绩。",
        ) {
            itemsIndexed(filteredScores) { index, score ->
                ChallengeHistoryRow(
                    badge = score.difficulty.title,
                    primary = "${score.correctAnswers} / $MATH_QUESTION_COUNT",
                    secondary = formatChallengeDate(score.completedAtMillis, "MM/dd HH:mm"),
                    trailing = formatMathDuration(score.elapsedMillis),
                )
                if (index != filteredScores.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReactionRecordsContent(scores: List<ReactionScoreRecord>) {
    ChallengeRecordsList(
        isEmpty = scores.isEmpty(),
        emptyMessage = "完成一局双重判断后，成绩会显示在这里。",
    ) {
        itemsIndexed(scores) { index, score ->
            ChallengeHistoryRow(
                badge = "${score.correctAnswers}题",
                primary = "${score.correctAnswers} / $REACTION_TRIAL_COUNT",
                secondary = formatChallengeDate(score.completedAtMillis, "MM/dd HH:mm"),
                trailing = "平均 ${formatReactionTime(score.averageReactionMillis)}",
            )
            if (index != scores.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun ChallengeRecordsList(
    isEmpty: Boolean,
    emptyMessage: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    if (isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            content = content,
        )
    }
}

@Composable
private fun ChallengeHistoryRow(
    badge: String,
    primary: String,
    secondary: String,
    trailing: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                badge,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(primary, fontWeight = FontWeight.Bold)
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            trailing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatChallengeDate(timestamp: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))

private fun formatChallengeMetric(value: Float, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)
