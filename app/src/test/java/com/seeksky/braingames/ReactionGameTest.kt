package com.seeksky.braingames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReactionGameTest {
    @Test
    fun vowelCheck_isCaseInsensitive() {
        "AEIOUaeiou".forEach { assertTrue(isVowel(it)) }
        "BCDFGxyz".forEach { assertFalse(isVowel(it)) }
    }

    @Test
    fun createdTrial_matchesRequestedAnswer() {
        val random = Random(7)

        ReactionRule.entries.forEach { rule ->
            listOf(true, false).forEach { expectedAnswer ->
                repeat(100) {
                    val trial = createReactionTrial(rule, expectedAnswer, random)
                    val actualAnswer = when (rule) {
                        ReactionRule.NumberIsEven -> trial.number % 2 == 0
                        ReactionRule.LetterIsVowel -> isVowel(trial.letter)
                    }
                    assertEquals(expectedAnswer, actualAnswer)
                    assertEquals(expectedAnswer, trial.correctAnswer)
                }
            }
        }
    }

    @Test
    fun round_isBalancedUniqueAndAvoidsLongRuleStreaks() {
        val trials = generateReactionRound(Random(18))

        assertEquals(REACTION_TRIAL_COUNT, trials.size)
        assertEquals(
            REACTION_TRIAL_COUNT,
            trials.map { Triple(it.number, it.letter, it.rule) }.distinct().size,
        )
        ReactionRule.entries.forEach { rule ->
            assertEquals(5, trials.count { it.rule == rule && it.correctAnswer })
            assertEquals(5, trials.count { it.rule == rule && !it.correctAnswer })
        }
        assertTrue(trials.windowed(4).none { window ->
            window.map { it.rule }.distinct().size == 1
        })
        assertTrue(trials.windowed(4).none { window ->
            window.map { it.correctAnswer }.distinct().size == 1
        })
    }

    @Test
    fun betterScore_prioritizesAccuracyThenReactionTime() {
        val previous = ReactionBestRecord(correctAnswers = 18, averageReactionMillis = 620L)

        assertTrue(isBetterReactionScore(ReactionBestRecord(19, 900L), previous))
        assertTrue(isBetterReactionScore(ReactionBestRecord(18, 619L), previous))
        assertFalse(isBetterReactionScore(ReactionBestRecord(18, 621L), previous))
        assertFalse(isBetterReactionScore(ReactionBestRecord(17, 400L), previous))
    }
}
