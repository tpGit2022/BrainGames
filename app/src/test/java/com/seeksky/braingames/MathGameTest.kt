package com.seeksky.braingames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathGameTest {
    @Test
    fun easyQuestions_useTwoDigitAdditionOrSubtraction() {
        val random = Random(12)

        repeat(200) {
            val question = generateMathQuestion(MathDifficulty.Easy, random)

            assertTrue(question.expression.matches(Regex("\\d{2} [+-] \\d{2}")))
            assertEquals(1, question.operationCount)
            assertTrue(question.answer >= 0)
            assertValidOptions(question)
        }
    }

    @Test
    fun everyDifficulty_generatesTheExpectedNumberOfOperations() {
        val random = Random(23)
        val expectedOperations = mapOf(
            MathDifficulty.Easy to 1,
            MathDifficulty.Medium to 1,
            MathDifficulty.Hard to 2,
            MathDifficulty.Hell to 3,
        )

        expectedOperations.forEach { (difficulty, operationCount) ->
            repeat(200) {
                val question = generateMathQuestion(difficulty, random)

                assertEquals(operationCount, question.operationCount)
                assertEquals(operationCount, question.expression.count { it in "+-×÷" })
                if (difficulty == MathDifficulty.Hell) {
                    assertTrue(question.expression.contains('('))
                }
                assertValidOptions(question)
            }
        }
    }

    @Test
    fun round_containsTenUniqueQuestions() {
        MathDifficulty.entries.forEach { difficulty ->
            val questions = generateMathRound(difficulty, random = Random(difficulty.ordinal))

            assertEquals(MATH_QUESTION_COUNT, questions.size)
            assertEquals(MATH_QUESTION_COUNT, questions.map { it.expression }.distinct().size)
        }
    }

    @Test
    fun betterScore_prioritizesAccuracyThenSpeed() {
        val previous = MathBestRecord(correctAnswers = 8, elapsedMillis = 20_000L)

        assertTrue(isBetterMathScore(MathBestRecord(9, 30_000L), previous))
        assertTrue(isBetterMathScore(MathBestRecord(8, 19_999L), previous))
        assertFalse(isBetterMathScore(MathBestRecord(8, 20_001L), previous))
        assertFalse(isBetterMathScore(MathBestRecord(7, 10_000L), previous))
    }

    @Test
    fun scoreCodec_roundTripsValidRecordsAndSkipsInvalidValues() {
        val records = listOf(
            MathScoreRecord(MathDifficulty.Easy, 8, 12_300L, 100L),
            MathScoreRecord(MathDifficulty.Hell, 10, 24_500L, 200L),
        )

        assertEquals(records, MathScoreCodec.decode(MathScoreCodec.encode(records)))
        assertTrue(MathScoreCodec.decode("Unknown,8,1000,100;Easy,99,1000,100").isEmpty())
    }

    @Test
    fun metricPoints_filterDifficultyAndOrderSessions() {
        val records = listOf(
            MathScoreRecord(MathDifficulty.Easy, 9, 9_000L, 300L),
            MathScoreRecord(MathDifficulty.Hard, 8, 15_000L, 100L),
            MathScoreRecord(MathDifficulty.Easy, 7, 12_000L, 200L),
        )

        val points = buildMathMetricPoints(records, MathDifficulty.Easy)

        assertEquals(listOf(1, 2), points.map { it.sessionNumber })
        assertEquals(listOf(70f, 90f), points.map { it.accuracyPercent })
        assertEquals(listOf(12f, 9f), points.map { it.secondaryValue })
        assertEquals(listOf(3f, 1f), points.map { it.errors })
    }

    private fun assertValidOptions(question: MathQuestion) {
        assertEquals(4, question.options.size)
        assertEquals(4, question.options.distinct().size)
        assertTrue(question.answer in question.options)
    }
}
