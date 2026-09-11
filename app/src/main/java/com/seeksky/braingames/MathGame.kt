package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

const val MATH_QUESTION_COUNT = 10

enum class MathDifficulty(
    val title: String,
    val rule: String,
) {
    Easy("简单", "两位数加减 · 1 步"),
    Medium("中等", "完整四则运算 · 1 步"),
    Hard("困难", "混合运算 · 2 步"),
    Hell("地狱", "括号混合运算 · 3 步"),
}

data class MathQuestion(
    val expression: String,
    val answer: Int,
    val options: List<Int>,
    val operationCount: Int,
)

data class MathBestRecord(
    val correctAnswers: Int,
    val elapsedMillis: Long,
)

data class MathScoreRecord(
    val difficulty: MathDifficulty,
    val correctAnswers: Int,
    val elapsedMillis: Long,
    val completedAtMillis: Long,
)

fun generateMathQuestion(
    difficulty: MathDifficulty,
    random: Random = Random.Default,
): MathQuestion {
    val expression = when (difficulty) {
        MathDifficulty.Easy -> generateEasyExpression(random)
        MathDifficulty.Medium -> generateMediumExpression(random)
        MathDifficulty.Hard -> generateHardExpression(random)
        MathDifficulty.Hell -> generateHellExpression(random)
    }
    return MathQuestion(
        expression = expression.text,
        answer = expression.value,
        options = createAnswerOptions(expression.value, difficulty, random),
        operationCount = expression.operationCount,
    )
}

internal fun generateMathRound(
    difficulty: MathDifficulty,
    questionCount: Int = MATH_QUESTION_COUNT,
    random: Random = Random.Default,
): List<MathQuestion> {
    require(questionCount > 0) { "Question count must be positive" }
    val questions = mutableListOf<MathQuestion>()
    val expressions = mutableSetOf<String>()
    while (questions.size < questionCount) {
        val question = generateMathQuestion(difficulty, random)
        if (expressions.add(question.expression)) questions += question
    }
    return questions
}

fun isBetterMathScore(candidate: MathBestRecord, previous: MathBestRecord?): Boolean =
    previous == null ||
        candidate.correctAnswers > previous.correctAnswers ||
        (candidate.correctAnswers == previous.correctAnswers &&
            candidate.elapsedMillis < previous.elapsedMillis)

private data class GeneratedExpression(
    val text: String,
    val value: Int,
    val operationCount: Int,
)

private fun generateEasyExpression(random: Random): GeneratedExpression {
    val first = random.nextInt(10, 100)
    val second = random.nextInt(10, 100)
    return if (random.nextBoolean()) {
        GeneratedExpression("$first + $second", first + second, 1)
    } else {
        val larger = max(first, second)
        val smaller = minOf(first, second)
        GeneratedExpression("$larger - $smaller", larger - smaller, 1)
    }
}

private fun generateMediumExpression(random: Random): GeneratedExpression = when (random.nextInt(4)) {
    0 -> {
        val first = random.nextInt(100, 500)
        val second = random.nextInt(20, 300)
        GeneratedExpression("$first + $second", first + second, 1)
    }

    1 -> {
        val first = random.nextInt(100, 700)
        val second = random.nextInt(20, first + 1)
        GeneratedExpression("$first - $second", first - second, 1)
    }

    2 -> {
        val first = random.nextInt(11, 40)
        val second = random.nextInt(2, 10)
        GeneratedExpression("$first × $second", first * second, 1)
    }

    else -> {
        val quotient = random.nextInt(11, 50)
        val divisor = random.nextInt(2, 10)
        GeneratedExpression("${quotient * divisor} ÷ $divisor", quotient, 1)
    }
}

private fun generateHardExpression(random: Random): GeneratedExpression = when (random.nextInt(6)) {
    0 -> {
        val a = random.nextInt(8, 30)
        val b = random.nextInt(2, 10)
        val c = random.nextInt(10, 80)
        GeneratedExpression("$a × $b + $c", a * b + c, 2)
    }

    1 -> {
        val a = random.nextInt(10, 50)
        val b = random.nextInt(10, 50)
        val c = random.nextInt(2, 8)
        GeneratedExpression("($a + $b) × $c", (a + b) * c, 2)
    }

    2 -> {
        val a = random.nextInt(12, 35)
        val b = random.nextInt(3, 10)
        val product = a * b
        val c = random.nextInt(10, product)
        GeneratedExpression("$a × $b - $c", product - c, 2)
    }

    3 -> {
        val quotient = random.nextInt(12, 60)
        val divisor = random.nextInt(2, 10)
        val c = random.nextInt(10, 80)
        GeneratedExpression("${quotient * divisor} ÷ $divisor + $c", quotient + c, 2)
    }

    4 -> {
        val a = random.nextInt(20, 100)
        val b = random.nextInt(3, 15)
        val c = random.nextInt(2, 10)
        GeneratedExpression("$a + $b × $c", a + b * c, 2)
    }

    else -> {
        val quotient = random.nextInt(10, 50)
        val divisor = random.nextInt(2, 10)
        val a = random.nextInt(10, quotient * divisor)
        val b = quotient * divisor - a
        GeneratedExpression("($a + $b) ÷ $divisor", quotient, 2)
    }
}

private fun generateHellExpression(random: Random): GeneratedExpression = when (random.nextInt(6)) {
    0 -> {
        val a = random.nextInt(12, 50)
        val b = random.nextInt(10, 40)
        val c = random.nextInt(3, 9)
        val d = random.nextInt(20, 100)
        GeneratedExpression("($a + $b) × $c - $d", (a + b) * c - d, 3)
    }

    1 -> {
        val a = random.nextInt(8, 25)
        val b = random.nextInt(3, 10)
        val c = random.nextInt(8, 25)
        val d = random.nextInt(3, 10)
        GeneratedExpression("($a × $b) + ($c × $d)", a * b + c * d, 3)
    }

    2 -> {
        val a = random.nextInt(10, 35)
        val b = random.nextInt(10, 35)
        val d = random.nextInt(2, 12)
        val c = random.nextInt(d + 1, 18)
        GeneratedExpression("($a + $b) × ($c - $d)", (a + b) * (c - d), 3)
    }

    3 -> {
        val quotient = random.nextInt(15, 60)
        val divisor = random.nextInt(2, 10)
        val c = random.nextInt(6, 20)
        val d = random.nextInt(3, 9)
        GeneratedExpression(
            "(${quotient * divisor} ÷ $divisor) + ($c × $d)",
            quotient + c * d,
            3,
        )
    }

    4 -> {
        val a = random.nextInt(8, 22)
        val b = random.nextInt(3, 10)
        val divisor = random.nextInt(2, 9)
        val baseProduct = a * b
        val quotient = baseProduct / divisor + random.nextInt(2, 10)
        val c = quotient * divisor - baseProduct
        GeneratedExpression("($a × $b + $c) ÷ $divisor", quotient, 3)
    }

    else -> {
        val a = random.nextInt(10, 35)
        val b = random.nextInt(8, 30)
        val c = random.nextInt(5, 25)
        val d = random.nextInt(2, 7)
        GeneratedExpression("($a + $b + $c) × $d", (a + b + c) * d, 3)
    }
}

private fun createAnswerOptions(
    answer: Int,
    difficulty: MathDifficulty,
    random: Random,
): List<Int> {
    val scale = max(1, abs(answer) / 12)
    val offsets = buildList {
        addAll(listOf(1, -1, 2, -2, 5, -5, 10, -10))
        addAll(listOf(scale, -scale, scale * 2, -scale * 2, scale + 3, -scale - 3))
    }.shuffled(random)

    val values = linkedSetOf(answer)
    offsets.forEach { offset ->
        val candidate = answer + offset
        if (difficulty == MathDifficulty.Hell || candidate >= 0) values += candidate
    }
    while (values.size < 4) {
        val spread = max(6, scale * 3)
        val candidate = answer + random.nextInt(-spread, spread + 1)
        if ((difficulty == MathDifficulty.Hell || candidate >= 0) && candidate != answer) {
            values += candidate
        }
    }
    return values.take(4).shuffled(random)
}

class MathScoreStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "math_game_scores",
        Context.MODE_PRIVATE,
    )

    fun getBest(difficulty: MathDifficulty): MathBestRecord? {
        val correctKey = "${difficulty.name}_correct"
        val elapsedKey = "${difficulty.name}_elapsed"
        if (!preferences.contains(correctKey) || !preferences.contains(elapsedKey)) return null
        return MathBestRecord(
            correctAnswers = preferences.getInt(correctKey, 0),
            elapsedMillis = preferences.getLong(elapsedKey, 0L),
        )
    }

    fun getScores(): List<MathScoreRecord> = MathScoreCodec
        .decode(preferences.getString(KEY_SCORES, null).orEmpty())
        .sortedByDescending { it.completedAtMillis }

    fun add(score: MathScoreRecord): Boolean {
        val candidate = MathBestRecord(score.correctAnswers, score.elapsedMillis)
        val isNewBest = isBetterMathScore(candidate, getBest(score.difficulty))
        val scores = getScores() + score
        preferences.edit {
            putString(KEY_SCORES, MathScoreCodec.encode(scores))
            if (isNewBest) {
                putInt("${score.difficulty.name}_correct", score.correctAnswers)
                putLong("${score.difficulty.name}_elapsed", score.elapsedMillis)
            }
        }
        return isNewBest
    }

    fun updateBest(difficulty: MathDifficulty, candidate: MathBestRecord): Boolean {
        if (!isBetterMathScore(candidate, getBest(difficulty))) return false
        preferences.edit {
            putInt("${difficulty.name}_correct", candidate.correctAnswers)
            putLong("${difficulty.name}_elapsed", candidate.elapsedMillis)
        }
        return true
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val KEY_SCORES = "score_records"
    }
}

internal object MathScoreCodec {
    fun encode(scores: List<MathScoreRecord>): String = scores.joinToString(";") { score ->
        listOf(
            score.difficulty.name,
            score.correctAnswers,
            score.elapsedMillis,
            score.completedAtMillis,
        ).joinToString(",")
    }

    fun decode(value: String): List<MathScoreRecord> {
        if (value.isBlank()) return emptyList()
        return value.split(';').mapNotNull { encodedScore ->
            val values = encodedScore.split(',')
            if (values.size != 4) return@mapNotNull null
            val difficulty = runCatching { MathDifficulty.valueOf(values[0]) }.getOrNull()
                ?: return@mapNotNull null
            val correctAnswers = values[1].toIntOrNull() ?: return@mapNotNull null
            val elapsedMillis = values[2].toLongOrNull() ?: return@mapNotNull null
            val completedAtMillis = values[3].toLongOrNull() ?: return@mapNotNull null
            if (correctAnswers !in 0..MATH_QUESTION_COUNT || elapsedMillis < 0L || completedAtMillis < 0L) {
                return@mapNotNull null
            }
            MathScoreRecord(difficulty, correctAnswers, elapsedMillis, completedAtMillis)
        }
    }
}
