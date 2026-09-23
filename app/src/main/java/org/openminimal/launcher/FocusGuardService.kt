package org.openminimal.launcher

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.openminimal.launcher.data.LauncherRepository
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import java.time.LocalDate

class FocusGuardService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: LauncherRepository
    private lateinit var device: DeviceServices
    private var latest: StoredState? = null
    private var currentPackage: String? = null
    private var guardJob: Job? = null

    override fun onServiceConnected() {
        repository = LauncherRepository(this)
        device = DeviceServices(this)
        scope.launch { repository.state.collectLatest { latest = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::repository.isInitialized) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == currentPackage) return
        currentPackage = packageName
        guardJob?.cancel()
        if (packageName == this.packageName || packageName == "com.android.systemui") return
        val bypassed = FocusGateSession.consume(packageName)
        guardJob = scope.launch { evaluate(packageName, bypassed) }
    }

    private suspend fun evaluate(packageName: String, bypassed: Boolean) {
        delay(120)
        val state = latest ?: repository.state.first()
        if (packageName !in state.dailyLimits && packageName !in state.focusRules) return
        val usage = if (packageName in state.dailyLimits) device.usage() else UsageSnapshot()
        if (!bypassed) {
            when (val decision = focusGateDecision(packageName, state.focusRules, state.dailyLimits,
                state.limitOverrides, usage.durations, LocalDate.now().toEpochDay())) {
                FocusGateDecision.Allow -> Unit
                else -> { showGate(packageName, decision); return }
            }
        }
        scheduleBudgetEnd(packageName, state, usage)
    }

    private suspend fun scheduleBudgetEnd(packageName: String, state: StoredState, initial: UsageSnapshot) {
        if (!initial.granted || initial.unavailable) return
        val status = appLimitStatus(packageName, state.dailyLimits, state.limitOverrides, initial.durations, LocalDate.now().toEpochDay()) ?: return
        if (status.overriddenToday || status.reached) return
        delay(status.remainingMillis.coerceAtLeast(1_000L) + 250L)
        if (currentPackage != packageName || !interactive()) return
        val freshState = latest ?: state
        val usage = device.usage()
        if (!usage.granted || usage.unavailable) return
        val freshStatus = appLimitStatus(packageName, freshState.dailyLimits, freshState.limitOverrides,
            usage.durations, LocalDate.now().toEpochDay()) ?: return
        if (freshStatus.overriddenToday) return
        if (!freshStatus.reached) {
            scheduleBudgetEnd(packageName, freshState, usage)
            return
        }
        val decision = focusGateDecision(packageName, freshState.focusRules, freshState.dailyLimits,
            freshState.limitOverrides, usage.durations, LocalDate.now().toEpochDay())
        if (decision is FocusGateDecision.Allow) scheduleBudgetEnd(packageName, freshState, usage)
        else showGate(packageName, decision)
    }

    private fun showGate(packageName: String, decision: FocusGateDecision) {
        if (currentPackage != packageName || !interactive()) return
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        currentPackage = this.packageName
        runCatching {
            startActivity(FocusGateContract.intent(this, packageName, launch?.component?.flattenToString(), label, decision))
        }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    private fun interactive(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    override fun onInterrupt() = Unit
    override fun onDestroy() { guardJob?.cancel(); scope.cancel(); super.onDestroy() }
}
