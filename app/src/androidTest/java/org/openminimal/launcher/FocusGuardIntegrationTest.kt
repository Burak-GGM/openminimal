package org.openminimal.launcher

import android.app.AppOpsManager
import android.app.UiAutomation
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openminimal.launcher.data.LauncherRepository
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.DeviceServices

/** Uses unsuppressed accessibility; ordinary UiAutomation disables the service under test. */
@RunWith(AndroidJUnit4::class)
class FocusGuardIntegrationTest {
    @Test fun budgetExpiryInterruptsAnAlreadyOpenApp() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        fun awaitCondition(timeout: Long, condition: () -> Boolean): Boolean {
            val end = SystemClock.elapsedRealtime() + timeout
            while (SystemClock.elapsedRealtime() < end) {
                if (condition()) return true
                SystemClock.sleep(250)
            }
            return false
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LauncherRepository(context)
        val fixture = instrumentation.context.packageName
        val previous = runBlocking { repository.state.first() }
        val services = shell("settings get secure enabled_accessibility_services")
        val enabled = shell("settings get secure accessibility_enabled")
        @Suppress("DEPRECATION")
        val previousMode = context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        val service = "${context.packageName}/org.openminimal.launcher.FocusGuardService"
        val serviceList = (services.takeUnless { it == "null" }.orEmpty().split(':').filter { it.isNotBlank() } + service).distinct().joinToString(":")
        try {
            shell("input keyevent KEYCODE_HOME")
            shell("appops set ${context.packageName} GET_USAGE_STATS allow")
            val usage = runBlocking { DeviceServices(context).usage() }
            assertTrue(usage.granted && !usage.unavailable)
            val minutes = ((usage.durations[fixture] ?: 0L) / 60_000L).toInt() + 1
            runBlocking {
                repository.configure { it.copy(language = "en") }
                repository.setDailyLimit(fixture, minutes)
                repository.setFocusPolicy(fixture, minutes, AppFocusRule(
                    limitReachedBehavior = LimitReachedBehavior.FOCUS_GATE,
                    limitChallenge = FocusChallenge(FocusChallengeType.WAIT, waitSeconds = 10),
                ))
            }
            shell("settings put secure enabled_accessibility_services $serviceList")
            shell("settings put secure accessibility_enabled 1")
            assertTrue("Live guard must bind", awaitCondition(10_000) {
                shell("dumpsys accessibility").contains("Service[label=Live Focus Guard")
            })
            shell("am start -W -n $fixture/org.openminimal.launcher.FixtureIconActivity")
            assertTrue("Fixture must initially open below its budget", awaitCondition(5_000) {
                shell("dumpsys window").lineSequence().any { it.contains("mCurrentFocus=") && it.contains("FixtureIconActivity") }
            })
            assertTrue("Budget expiry must show the gate without another launch", awaitCondition(75_000) {
                shell("dumpsys window").lineSequence().any { it.contains("mCurrentFocus=") && it.contains("FocusGateActivity") }
            })
        } finally {
            shell("input keyevent KEYCODE_HOME")
            if (services == "null") shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $services")
            if (enabled == "null") shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $enabled")
            val mode = when (previousMode) {
                AppOpsManager.MODE_ALLOWED -> "allow"
                AppOpsManager.MODE_IGNORED -> "ignore"
                AppOpsManager.MODE_ERRORED -> "deny"
                else -> "default"
            }
            shell("appops set ${context.packageName} GET_USAGE_STATS $mode")
            runBlocking {
                repository.setDailyLimit(fixture, previous.dailyLimits[fixture])
                repository.setFocusPolicy(fixture, previous.dailyLimits[fixture], previous.focusRules[fixture])
                previous.limitOverrides[fixture]?.let { repository.overrideDailyLimit(fixture, it) }
                repository.configure { previous.config }
            }
        }
    }
}
