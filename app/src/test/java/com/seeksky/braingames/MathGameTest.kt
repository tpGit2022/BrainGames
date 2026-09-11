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

    private fun assertValidOptions(question: MathQuestion) {
        assertEquals(4, question.options.size)
        assertEquals(4, question.options.distinct().size)
        assertTrue(question.answer in question.options)
    }
}
