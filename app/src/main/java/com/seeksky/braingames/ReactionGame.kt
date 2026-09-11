package com.seeksky.braingames

import android.content.Context
import androidx.core.content.edit
import kotlin.random.Random

const val REACTION_TRIAL_COUNT = 20

enum class ReactionRule(val question: String) {
    NumberIsEven("数字是偶数吗？"),
    LetterIsVowel("字母是元音吗？"),
}

data class ReactionTrial(
    val number: Int,
    val letter: Char,
    val rule: ReactionRule,
    val correctAnswer: Boolean,
)

data class ReactionBestRecord(
    val correctAnswers: Int,
    val averageReactionMillis: Long,
)

fun isVowel(letter: Char): Boolean = letter.uppercaseChar() in "AEIOU"

fun createReactionTrial(
    rule: ReactionRule,
    expectedAnswer: Boolean,
    random: Random = Random.Default,
): ReactionTrial {
    val number = when (rule) {
        ReactionRule.NumberIsEven -> randomNumber(expectedAnswer, random)
        ReactionRule.LetterIsVowel -> random.nextInt(0, 10)
    }
    val letter = when (rule) {
        ReactionRule.NumberIsEven -> randomLetter(random)
        ReactionRule.LetterIsVowel -> randomLetter(expectedAnswer, random)
    }
    return ReactionTrial(number, letter, rule, expectedAnswer)
}

internal fun generateReactionRound(
    random: Random = Random.Default,
): List<ReactionTrial> {
    val trialKinds = buildList {
        ReactionRule.entries.forEach { rule ->
            repeat(REACTION_TRIAL_COUNT / 4) {
                add(rule to true)
                add(rule to false)
            }
        }
    }

    var shuffledKinds = trialKinds.shuffled(random)
    repeat(100) {
        if (!hasLongStreak(shuffledKinds)) {
            return createUniqueTrials(shuffledKinds, random)
        }
        shuffledKinds = trialKinds.shuffled(random)
    }
    val fallbackKinds = buildList {
        val numberKinds = trialKinds.filter { it.first == ReactionRule.NumberIsEven }
        val letterKinds = trialKinds.filter { it.first == ReactionRule.LetterIsVowel }
        numberKinds.indices.forEach { index ->
            add(numberKinds[index])
            add(letterKinds[index])
        }
    }
    return createUniqueTrials(fallbackKinds, random)
}

fun isBetterReactionScore(
    candidate: ReactionBestRecord,
    previous: ReactionBestRecord?,
): Boolean = previous == null ||
    candidate.correctAnswers > previous.correctAnswers ||
    (candidate.correctAnswers == previous.correctAnswers &&
        candidate.averageReactionMillis < previous.averageReactionMillis)

private fun createUniqueTrials(
    kinds: List<Pair<ReactionRule, Boolean>>,
    random: Random,
): List<ReactionTrial> {
    val used = mutableSetOf<Triple<Int, Char, ReactionRule>>()
    return kinds.map { (rule, answer) ->
        var trial: ReactionTrial
        do {
            trial = createReactionTrial(rule, answer, random)
        } while (!used.add(Triple(trial.number, trial.letter, trial.rule)))
        trial
    }
}

private fun hasLongStreak(kinds: List<Pair<ReactionRule, Boolean>>): Boolean =
    kinds.windowed(4).any { window ->
        window.map { it.first }.distinct().size == 1 ||
            window.map { it.second }.distinct().size == 1
    }

private fun randomNumber(isEven: Boolean, random: Random): Int {
    val candidates = if (isEven) intArrayOf(0, 2, 4, 6, 8) else intArrayOf(1, 3, 5, 7, 9)
    return candidates[random.nextInt(candidates.size)]
}

private fun randomLetter(random: Random): Char =
    random.nextInt('A'.code, 'Z'.code + 1).toChar()

private fun randomLetter(isVowel: Boolean, random: Random): Char {
    val candidates = if (isVowel) "AEIOU" else "BCDFGHJKLMNPQRSTVWXYZ"
    return candidates[random.nextInt(candidates.length)]
}

class ReactionScoreStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "reaction_game_scores",
        Context.MODE_PRIVATE,
    )

    fun getBest(): ReactionBestRecord? {
        if (!preferences.contains(KEY_CORRECT) || !preferences.contains(KEY_AVERAGE)) return null
        return ReactionBestRecord(
            correctAnswers = preferences.getInt(KEY_CORRECT, 0),
            averageReactionMillis = preferences.getLong(KEY_AVERAGE, 0L),
        )
    }

    fun updateBest(candidate: ReactionBestRecord): Boolean {
        if (!isBetterReactionScore(candidate, getBest())) return false
        preferences.edit {
            putInt(KEY_CORRECT, candidate.correctAnswers)
            putLong(KEY_AVERAGE, candidate.averageReactionMillis)
        }
        return true
    }

    private companion object {
        const val KEY_CORRECT = "correct_answers"
        const val KEY_AVERAGE = "average_reaction_millis"
    }
}
