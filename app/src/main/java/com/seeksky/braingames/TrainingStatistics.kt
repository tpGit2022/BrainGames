package com.seeksky.braingames

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_VISIBLE_SESSIONS = 20

internal data class TrainingMetricPoint(
    val sessionNumber: Int,
    val speedPerSecond: Float,
    val accuracyPercent: Float,
    val elapsedSeconds: Float,
    val errors: Float,
    val completedAtMillis: Long,
)

internal fun buildTrainingMetricPoints(
    scores: List<ScoreRecord>,
    gridSize: Int,
): List<TrainingMetricPoint> = scores
    .filter { it.gridSize == gridSize }
    .sortedWith(compareBy<ScoreRecord> { it.completedAtMillis }.thenBy { it.id })
    .mapIndexed { index, score ->
        val correctTaps = score.gridSize * score.gridSize
        TrainingMetricPoint(
            sessionNumber = index + 1,
            speedPerSecond = correctTaps * 1_000f / score.elapsedMillis.coerceAtLeast(1L),
            accuracyPercent = correctTaps * 100f / (correctTaps + score.errors),
            elapsedSeconds = score.elapsedMillis / 1_000f,
            errors = score.errors.toFloat(),
            completedAtMillis = score.completedAtMillis,
        )
    }

internal fun percentChange(first: Float, latest: Float): Float? =
    if (first == 0f) null else (latest - first) / abs(first) * 100f

@Composable
internal fun TrainingStatisticsContent(
    scores: List<ScoreRecord>,
    modifier: Modifier = Modifier,
) {
    val availableSizes = remember(scores) {
        scores.groupingBy { it.gridSize }.eachCount().toSortedMap()
    }
    var selectedSize by rememberSaveable {
        mutableIntStateOf(scores.firstOrNull()?.gridSize ?: 5)
    }

    if (scores.isEmpty()) {
        EmptyStatistics(modifier)
        return
    }

    val activeSize = selectedSize.takeIf { it in availableSizes } ?: scores.first().gridSize

    val allPoints = remember(scores, activeSize) {
        buildTrainingMetricPoints(scores, activeSize)
    }
    val visiblePoints = allPoints.takeLast(MAX_VISIBLE_SESSIONS)
    val latest = allPoints.last()
    val first = allPoints.first()

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableSizes.forEach { (size, count) ->
                    FilterChip(
                        selected = activeSize == size,
                        onClick = { selectedSize = size },
                        label = { Text("${size}×${size}  $count") },
                    )
                }
            }
        }

        item {
            StatisticsSummary(
                latest = latest,
                sessionCount = allPoints.size,
                averageSeconds = allPoints.map { it.elapsedSeconds }.average().toFloat(),
            )
        }

        if (allPoints.size > 1) {
            item { ProgressSummary(first = first, latest = latest) }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "再完成一次 ${activeSize}×${activeSize} 训练，就能看到变化趋势。",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item {
            val suffix = if (allPoints.size > MAX_VISIBLE_SESSIONS) {
                " · 显示最近 $MAX_VISIBLE_SESSIONS 次"
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
                title = "训练速度",
                subtitle = "每秒完成的数字数，越高越好",
                values = visiblePoints.map { it.speedPerSecond },
                pointNumbers = visiblePoints.map { it.sessionNumber },
                valueFormatter = { "${formatMetric(it, 2)} 个/秒" },
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item {
            LineChartCard(
                title = "准确率",
                subtitle = "正确点击 ÷ 全部点击",
                values = visiblePoints.map { it.accuracyPercent },
                pointNumbers = visiblePoints.map { it.sessionNumber },
                valueFormatter = { "${formatMetric(it, 1)}%" },
                color = MaterialTheme.colorScheme.tertiary,
                fixedRange = accuracyBounds(visiblePoints.map { it.accuracyPercent }),
            )
        }

        item {
            BarChartCard(
                title = "错误次数",
                subtitle = "每次训练中的错误点击，越低越好",
                values = visiblePoints.map { it.errors },
                pointNumbers = visiblePoints.map { it.sessionNumber },
                color = MaterialTheme.colorScheme.error,
            )
        }

        item {
            val latestDate = remember(latest.completedAtMillis) {
                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    .format(Date(latest.completedAtMillis))
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
private fun EmptyStatistics(modifier: Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("↗", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("还没有可统计的数据", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "完成训练后，这里会展示速度、准确率和错误次数的变化。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatisticsSummary(
    latest: TrainingMetricPoint,
    sessionCount: Int,
    averageSeconds: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                label = "最近速度",
                value = "${formatMetric(latest.speedPerSecond, 2)} 个/秒",
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "最近准确率",
                value = "${formatMetric(latest.accuracyPercent, 1)}%",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                label = "平均用时",
                value = formatDuration((averageSeconds * 1_000).toLong()),
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "累计训练",
                value = "$sessionCount 次",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProgressSummary(first: TrainingMetricPoint, latest: TrainingMetricPoint) {
    val speedChange = percentChange(first.speedPerSecond, latest.speedPerSecond) ?: 0f
    val durationChange = percentChange(first.elapsedSeconds, latest.elapsedSeconds) ?: 0f
    val accuracyChange = latest.accuracyPercent - first.accuracyPercent

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("相比第一次", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                directionalChangeLabel("速度提升", "速度下降", "速度基本持平", speedChange, "%"),
                fontWeight = FontWeight.Bold,
                color = changeColor(speedChange, positiveIsGood = true),
            )
            Text(
                directionalChangeLabel(
                    "准确率提升",
                    "准确率下降",
                    "准确率基本持平",
                    accuracyChange,
                    " 个百分点",
                ),
                fontWeight = FontWeight.Bold,
                color = changeColor(accuracyChange, positiveIsGood = true),
            )
            Text(
                directionalChangeLabel("用时增加", "用时缩短", "用时基本持平", durationChange, "%"),
                fontWeight = FontWeight.Bold,
                color = changeColor(durationChange, positiveIsGood = false),
            )
        }
    }
}

@Composable
private fun changeColor(change: Float, positiveIsGood: Boolean): Color {
    val improved = if (positiveIsGood) change > 0f else change < 0f
    return when {
        abs(change) < 0.05f -> MaterialTheme.colorScheme.onPrimaryContainer
        improved -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}

private fun directionalChangeLabel(
    positiveLabel: String,
    negativeLabel: String,
    flatLabel: String,
    change: Float,
    suffix: String,
): String {
    if (abs(change) < 0.05f) return flatLabel
    val label = if (change > 0f) positiveLabel else negativeLabel
    return "$label ${formatMetric(abs(change), 1)}$suffix"
}

@Composable
private fun LineChartCard(
    title: String,
    subtitle: String,
    values: List<Float>,
    pointNumbers: List<Int>,
    valueFormatter: (Float) -> String,
    color: Color,
    fixedRange: ClosedFloatingPointRange<Float>? = null,
) {
    val bounds = fixedRange ?: paddedBounds(values)
    var selectedIndex by remember(values) { mutableIntStateOf(values.lastIndex) }
    MetricChartCard(
        title = title,
        subtitle = "$subtitle · 点按数据点查看",
        displayValue = "#${pointNumbers[selectedIndex]} · ${valueFormatter(values[selectedIndex])}",
    ) {
        ChartWithAxisLabels(
            values = values,
            pointNumbers = pointNumbers,
            bounds = bounds,
            valueFormatter = valueFormatter,
        ) {
            LineChart(
                values = values,
                bounds = bounds,
                color = color,
                modifier = Modifier.fillMaxSize(),
                description = "$title，${values.mapIndexed { index, value -> "第${pointNumbers[index]}次${valueFormatter(value)}" }}",
                selectedIndex = selectedIndex,
                onPointSelected = { selectedIndex = it },
            )
        }
    }
}

@Composable
private fun BarChartCard(
    title: String,
    subtitle: String,
    values: List<Float>,
    pointNumbers: List<Int>,
    color: Color,
) {
    val upper = max(1f, values.maxOrNull() ?: 1f)
    val bounds = 0f..upper
    var selectedIndex by remember(values) { mutableIntStateOf(values.lastIndex) }
    MetricChartCard(
        title = title,
        subtitle = "$subtitle · 点按柱形查看",
        displayValue = "#${pointNumbers[selectedIndex]} · ${values[selectedIndex].toInt()} 次",
    ) {
        ChartWithAxisLabels(
            values = values,
            pointNumbers = pointNumbers,
            bounds = bounds,
            valueFormatter = { it.toInt().toString() },
        ) {
            BarChart(
                values = values,
                upperBound = upper,
                color = color,
                modifier = Modifier.fillMaxSize(),
                description = "$title，${values.mapIndexed { index, value -> "第${pointNumbers[index]}次${value.toInt()}次" }}",
                selectedIndex = selectedIndex,
                onBarSelected = { selectedIndex = it },
            )
        }
    }
}

@Composable
private fun MetricChartCard(
    title: String,
    subtitle: String,
    displayValue: String,
    chart: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    displayValue,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            chart()
        }
    }
}

@Composable
private fun ChartWithAxisLabels(
    values: List<Float>,
    pointNumbers: List<Int>,
    bounds: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    chart: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
    ) {
        Column(
            modifier = Modifier
                .width(62.dp)
                .fillMaxHeight()
                .padding(vertical = 2.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(valueFormatter(bounds.endInclusive), style = MaterialTheme.typography.labelSmall)
            Text(valueFormatter((bounds.start + bounds.endInclusive) / 2f), style = MaterialTheme.typography.labelSmall)
            Text(valueFormatter(bounds.start), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { chart() }
            Row(Modifier.fillMaxWidth()) {
                Text("#${pointNumbers.first()}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                if (values.size > 2) {
                    Text("训练次数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }
                Text("#${pointNumbers.last()}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LineChart(
    values: List<Float>,
    bounds: ClosedFloatingPointRange<Float>,
    color: Color,
    description: String,
    selectedIndex: Int,
    onPointSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = color.copy(alpha = 0.12f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val selected = if (values.size == 1) {
                        0
                    } else {
                        (offset.x / size.width * values.lastIndex).roundToInt()
                            .coerceIn(values.indices)
                    }
                    onPointSelected(selected)
                }
            }
            .semantics { contentDescription = description },
    ) {
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        val plotInset = 7.dp.toPx()
        val plotWidth = (size.width - plotInset * 2f).coerceAtLeast(0f)
        val plotHeight = (size.height - plotInset * 2f).coerceAtLeast(0f)
        fun point(index: Int, value: Float): Offset {
            val fraction = ((value - bounds.start) / (bounds.endInclusive - bounds.start)).coerceIn(0f, 1f)
            val x = if (values.size == 1) {
                size.width / 2f
            } else {
                plotInset + plotWidth * index / values.lastIndex
            }
            return Offset(x, plotInset + plotHeight * (1f - fraction))
        }

        val points = values.mapIndexed(::point)
        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fillPath = Path().apply {
                moveTo(points.first().x, size.height)
                lineTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height)
                close()
            }
            drawPath(fillPath, fillColor)
            drawPath(linePath, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEachIndexed { index, point ->
            val selected = index == selectedIndex
            drawCircle(surfaceColor, (if (selected) 7.dp else 5.dp).toPx(), point)
            drawCircle(color, (if (selected) 4.5.dp else 3.dp).toPx(), point)
        }
    }
}

@Composable
private fun BarChart(
    values: List<Float>,
    upperBound: Float,
    color: Color,
    description: String,
    selectedIndex: Int,
    onBarSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .pointerInput(values) {
                detectTapGestures { offset ->
                    val selected = (offset.x / size.width * values.size).toInt()
                        .coerceIn(values.indices)
                    onBarSelected(selected)
                }
            }
            .semantics { contentDescription = description },
    ) {
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val slotWidth = size.width / values.size
        val barWidth = (slotWidth * 0.58f).coerceAtMost(28.dp.toPx())
        values.forEachIndexed { index, value ->
            val barHeight = if (value == 0f) 2.dp.toPx() else size.height * value / upperBound
            val left = slotWidth * index + (slotWidth - barWidth) / 2f
            drawRect(
                color = when {
                    index == selectedIndex -> color
                    value == 0f -> color.copy(alpha = 0.35f)
                    else -> color.copy(alpha = 0.68f)
                },
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )
        }
    }
}

private fun paddedBounds(values: List<Float>): ClosedFloatingPointRange<Float> {
    val minimum = values.minOrNull() ?: 0f
    val maximum = values.maxOrNull() ?: 1f
    if (minimum == maximum) {
        val padding = max(0.5f, abs(minimum) * 0.1f)
        return (minimum - padding).coerceAtLeast(0f)..(maximum + padding)
    }
    val padding = (maximum - minimum) * 0.12f
    return (minimum - padding).coerceAtLeast(0f)..(maximum + padding)
}

private fun accuracyBounds(values: List<Float>): ClosedFloatingPointRange<Float> {
    val padded = paddedBounds(values)
    val lower = padded.start.coerceIn(0f, 99f)
    val upper = padded.endInclusive.coerceIn(lower + 1f, 100f)
    return lower..upper
}

private fun formatMetric(value: Float, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)
