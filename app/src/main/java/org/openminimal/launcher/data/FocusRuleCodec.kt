package org.openminimal.launcher.data

import org.json.JSONObject
import org.openminimal.launcher.model.*

internal object FocusRuleCodec {
    fun decode(serialized: String?): Map<String, AppFocusRule> {
        val root = runCatching { JSONObject(serialized ?: "{}") }.getOrDefault(JSONObject())
        // Pre-release builds wrote the package map directly. Keep accepting it so local
        // development installs migrate without losing rules.
        val source = root.optJSONObject("rules") ?: root
        return source.keys().asSequence().mapNotNull { packageName ->
            if (packageName.isBlank()) return@mapNotNull null
            val value = source.optJSONObject(packageName) ?: return@mapNotNull null
            packageName to AppFocusRule(
                limitReachedBehavior = value.enum("limitBehavior", LimitReachedBehavior.HIDE),
                limitChallenge = challenge(value.optJSONObject("limitChallenge")),
                everyLaunchChallenge = value.optJSONObject("everyLaunch")?.let(::challenge),
            )
        }.take(512).toMap()
    }

    fun encode(values: Map<String, AppFocusRule>): String = JSONObject().apply {
        put("version", 1)
        put("rules", JSONObject().apply {
            values.entries.take(512).forEach { (packageName, rule) ->
                if (packageName.isNotBlank()) put(packageName, JSONObject().apply {
                    put("limitBehavior", rule.limitReachedBehavior.name)
                    put("limitChallenge", challenge(rule.limitChallenge))
                    rule.everyLaunchChallenge?.let { put("everyLaunch", challenge(it)) }
                })
            }
        })
    }.toString()

    private fun challenge(value: JSONObject?): FocusChallenge {
        value ?: return FocusChallenge()
        return FocusChallenge(
            type = value.enum("type", FocusChallengeType.TEXT),
            phrase = value.optString("phrase").take(240),
            difficulty = value.enum("difficulty", FocusDifficulty.MEDIUM),
            waitSeconds = value.optInt("waitSeconds", 10).let { if (it in setOf(10, 30, 60)) it else 10 },
        )
    }

    private fun challenge(value: FocusChallenge) = JSONObject().apply {
        put("type", value.type.name)
        put("phrase", value.phrase.take(240))
        put("difficulty", value.difficulty.name)
        put("waitSeconds", value.waitSeconds.coerceIn(10, 60))
    }
}
