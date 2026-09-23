package org.openminimal.launcher.model

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.openminimal.launcher.data.FocusRuleCodec

class FocusGateTest {
    @Test fun reachedBudgetCanHideOrChallengeWithoutChangingEveryLaunchOrder() {
        val packageName = "example.social"
        val limits = mapOf(packageName to 15)
        val usage = mapOf(packageName to 15 * 60_000L)
        assertTrue(focusGateDecision(packageName, emptyMap(), limits, emptyMap(), usage, 10) is FocusGateDecision.Blocked)

        val math = FocusChallenge(FocusChallengeType.MATH, difficulty = FocusDifficulty.HARD)
        val rule = AppFocusRule(LimitReachedBehavior.FOCUS_GATE, math,
            FocusChallenge(FocusChallengeType.WAIT, waitSeconds = 30))
        assertEquals(FocusGateDecision.Challenge(FocusGateReason.DAILY_LIMIT, math),
            focusGateDecision(packageName, mapOf(packageName to rule), limits, emptyMap(), usage, 10))
        assertEquals(FocusGateDecision.Challenge(FocusGateReason.EVERY_LAUNCH, rule.everyLaunchChallenge!!),
            focusGateDecision(packageName, mapOf(packageName to rule), limits, emptyMap(), emptyMap(), 10))
        assertEquals(FocusGateDecision.Challenge(FocusGateReason.EVERY_LAUNCH, rule.everyLaunchChallenge),
            focusGateDecision(packageName, mapOf(packageName to rule), limits, mapOf(packageName to 10), usage, 10))
    }

    @Test fun hideProjectionOnlyAppliesToReachedNonOverriddenBudgets() {
        val packageName = "example.social"
        assertTrue(appHiddenByLimit(packageName, emptyMap(), mapOf(packageName to 5), emptyMap(), mapOf(packageName to 400_000), 20))
        assertFalse(appHiddenByLimit(packageName, emptyMap(), mapOf(packageName to 5), mapOf(packageName to 20), mapOf(packageName to 400_000), 20))
        val visibleRule = AppFocusRule(limitReachedBehavior = LimitReachedBehavior.FOCUS_GATE)
        assertFalse(appHiddenByLimit(packageName, mapOf(packageName to visibleRule), mapOf(packageName to 5), emptyMap(), mapOf(packageName to 400_000), 20))
    }

    @Test fun focusRulesRoundTripWithBoundedChallengeValues() {
        val values = mapOf("example.app" to AppFocusRule(
            limitReachedBehavior = LimitReachedBehavior.FOCUS_GATE,
            limitChallenge = FocusChallenge(FocusChallengeType.TEXT, "A deliberate choice"),
            everyLaunchChallenge = FocusChallenge(FocusChallengeType.WAIT, waitSeconds = 30),
        ))
        assertEquals(values, FocusRuleCodec.decode(FocusRuleCodec.encode(values)))
        assertEquals(FocusChallengeType.MATH, FocusRuleCodec.decode(
            """{"legacy.app":{"everyLaunch":{"type":"MATH"}}}""",
        )["legacy.app"]?.everyLaunchChallenge?.type)
        val malformed = FocusRuleCodec.decode("""{"example":{"everyLaunch":{"type":"FUTURE","waitSeconds":999}}}""")
        assertEquals(FocusChallengeType.TEXT, malformed.getValue("example").everyLaunchChallenge?.type)
        assertEquals(10, malformed.getValue("example").everyLaunchChallenge?.waitSeconds)
    }

    @Test fun generatedMathHasCorrectIntegerAnswerAtEveryDifficulty() {
        FocusDifficulty.entries.forEachIndexed { index, difficulty ->
            repeat(30) { sample ->
                val problem = mathProblem(difficulty, Random(index * 1000 + sample))
                val parts = problem.expression.split(' ')
                val left = parts[0].toInt()
                val right = parts[2].toInt()
                val evaluated = when (parts[1]) {
                    "+" -> left + right
                    "−" -> left - right
                    "×" -> left * right
                    "÷" -> left / right
                    else -> error("Unexpected operator")
                }
                assertEquals(problem.answer, evaluated)
            }
        }
        assertEquals(listOf(3, 4, 5), FocusDifficulty.entries.map(::memoryRounds))
    }
}
