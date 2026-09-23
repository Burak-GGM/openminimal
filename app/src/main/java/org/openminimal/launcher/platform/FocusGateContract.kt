package org.openminimal.launcher.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import org.openminimal.launcher.FocusGateActivity
import org.openminimal.launcher.model.*

object FocusGateSession {
    private val bypassUntil = mutableMapOf<String, Long>()

    @Synchronized fun allowNext(packageName: String) {
        bypassUntil[packageName] = android.os.SystemClock.elapsedRealtime() + 12_000L
    }

    @Synchronized fun consume(packageName: String): Boolean {
        val until = bypassUntil.remove(packageName) ?: return false
        return until >= android.os.SystemClock.elapsedRealtime()
    }
}

internal object FocusGateContract {
    private const val PACKAGE = "package"
    private const val COMPONENT = "component"
    private const val LABEL = "label"
    private const val SHORTCUT = "shortcut"
    private const val BLOCKED = "blocked"
    private const val REASON = "reason"
    private const val TYPE = "type"
    private const val PHRASE = "phrase"
    private const val DIFFICULTY = "difficulty"
    private const val WAIT = "wait"

    fun intent(context: Context, packageName: String, component: String?, label: String,
        decision: FocusGateDecision, shortcut: ShortcutInfo? = null): Intent {
        val challenge = (decision as? FocusGateDecision.Challenge)?.challenge
        val reason = when (decision) {
            is FocusGateDecision.Blocked -> decision.reason
            is FocusGateDecision.Challenge -> decision.reason
            FocusGateDecision.Allow -> error("An allowed launch does not need a gate")
        }
        return Intent(context, FocusGateActivity::class.java).apply {
            putExtra(PACKAGE, packageName)
            putExtra(COMPONENT, component)
            putExtra(LABEL, label)
            putExtra(SHORTCUT, shortcut)
            putExtra(BLOCKED, decision is FocusGateDecision.Blocked)
            putExtra(REASON, reason.name)
            challenge?.let {
                putExtra(TYPE, it.type.name)
                putExtra(PHRASE, it.phrase)
                putExtra(DIFFICULTY, it.difficulty.name)
                putExtra(WAIT, it.waitSeconds)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun target(intent: Intent) = GateTarget(
        packageName = intent.getStringExtra(PACKAGE).orEmpty(),
        component = intent.getStringExtra(COMPONENT),
        label = intent.getStringExtra(LABEL).orEmpty(),
        shortcut = @Suppress("DEPRECATION") intent.getParcelableExtra(SHORTCUT) as? ShortcutInfo,
        blocked = intent.getBooleanExtra(BLOCKED, false),
        reason = intent.getStringExtra(REASON).enumOr(FocusGateReason.EVERY_LAUNCH),
        challenge = intent.getStringExtra(TYPE)?.let {
            FocusChallenge(
                type = it.enumOr(FocusChallengeType.TEXT),
                phrase = intent.getStringExtra(PHRASE).orEmpty(),
                difficulty = intent.getStringExtra(DIFFICULTY).enumOr(FocusDifficulty.MEDIUM),
                waitSeconds = intent.getIntExtra(WAIT, 10).coerceIn(10, 60),
            )
        },
    )

    private inline fun <reified T : Enum<T>> String?.enumOr(fallback: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: fallback
}

internal data class GateTarget(
    val packageName: String,
    val component: String?,
    val label: String,
    val shortcut: ShortcutInfo?,
    val blocked: Boolean,
    val reason: FocusGateReason,
    val challenge: FocusChallenge?,
)
