package org.openminimal.launcher.model

import kotlin.random.Random

enum class FocusChallengeType { TEXT, MATH, MEMORY, WAIT }
enum class FocusDifficulty { EASY, MEDIUM, HARD }
enum class LimitReachedBehavior { HIDE, FOCUS_GATE }
enum class FocusGateReason { DAILY_LIMIT, EVERY_LAUNCH }

data class FocusChallenge(
    val type: FocusChallengeType = FocusChallengeType.TEXT,
    val phrase: String = "",
    val difficulty: FocusDifficulty = FocusDifficulty.MEDIUM,
    val waitSeconds: Int = 10,
)

/** Per-application friction. Stored outside visual presets and never grants platform access. */
data class AppFocusRule(
    val limitReachedBehavior: LimitReachedBehavior = LimitReachedBehavior.HIDE,
    val limitChallenge: FocusChallenge = FocusChallenge(),
    val everyLaunchChallenge: FocusChallenge? = null,
)

sealed interface FocusGateDecision {
    data object Allow : FocusGateDecision
    data class Blocked(val reason: FocusGateReason = FocusGateReason.DAILY_LIMIT) : FocusGateDecision
    data class Challenge(val reason: FocusGateReason, val challenge: FocusChallenge) : FocusGateDecision
}

fun focusGateDecision(
    packageName: String,
    rules: Map<String, AppFocusRule>,
    limits: Map<String, Int>,
    overrides: Map<String, Long>,
    usage: Map<String, Long>,
    epochDay: Long,
): FocusGateDecision {
    val rule = rules[packageName]
    val status = appLimitStatus(packageName, limits, overrides, usage, epochDay)
    if (status?.reached == true) {
        return if (rule?.limitReachedBehavior == LimitReachedBehavior.FOCUS_GATE) {
            FocusGateDecision.Challenge(FocusGateReason.DAILY_LIMIT, rule.limitChallenge)
        } else FocusGateDecision.Blocked()
    }
    return rule?.everyLaunchChallenge?.let {
        FocusGateDecision.Challenge(FocusGateReason.EVERY_LAUNCH, it)
    } ?: FocusGateDecision.Allow
}

fun appHiddenByLimit(
    packageName: String,
    rules: Map<String, AppFocusRule>,
    limits: Map<String, Int>,
    overrides: Map<String, Long>,
    usage: Map<String, Long>,
    epochDay: Long,
): Boolean = focusGateDecision(packageName, rules, limits, overrides, usage, epochDay) is FocusGateDecision.Blocked

data class MathProblem(val expression: String, val answer: Int)

fun mathProblem(difficulty: FocusDifficulty, random: Random = Random.Default): MathProblem = when (difficulty) {
    FocusDifficulty.EASY -> {
        val first = random.nextInt(10, 100)
        val second = random.nextInt(10, 100)
        if (random.nextBoolean()) MathProblem("$first + $second", first + second)
        else {
            val high = maxOf(first, second)
            val low = minOf(first, second)
            MathProblem("$high − $low", high - low)
        }
    }
    FocusDifficulty.MEDIUM -> when (random.nextInt(3)) {
        0 -> {
            val first = random.nextInt(100, 900)
            val second = random.nextInt(100, 900)
            MathProblem("$first + $second", first + second)
        }
        1 -> {
            val first = random.nextInt(100, 900)
            val second = random.nextInt(100, 900)
            val high = maxOf(first, second)
            val low = minOf(first, second)
            MathProblem("$high − $low", high - low)
        }
        else -> {
            val first = random.nextInt(11, 40)
            val second = random.nextInt(3, 20)
            MathProblem("$first × $second", first * second)
        }
    }
    FocusDifficulty.HARD -> when (random.nextInt(4)) {
        0 -> {
            val first = random.nextInt(300, 1_500)
            val second = random.nextInt(300, 1_500)
            MathProblem("$first + $second", first + second)
        }
        1 -> {
            val first = random.nextInt(300, 1_500)
            val second = random.nextInt(300, 1_500)
            val high = maxOf(first, second)
            val low = minOf(first, second)
            MathProblem("$high − $low", high - low)
        }
        2 -> {
            val first = random.nextInt(20, 80)
            val second = random.nextInt(11, 40)
            MathProblem("$first × $second", first * second)
        }
        else -> {
            val divisor = random.nextInt(4, 25)
            val answer = random.nextInt(12, 80)
            MathProblem("${divisor * answer} ÷ $divisor", answer)
        }
    }
}

fun memoryRounds(difficulty: FocusDifficulty): Int = when (difficulty) {
    FocusDifficulty.EASY -> 3
    FocusDifficulty.MEDIUM -> 4
    FocusDifficulty.HARD -> 5
}
