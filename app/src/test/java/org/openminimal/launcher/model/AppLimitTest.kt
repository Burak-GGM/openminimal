package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test

class AppLimitTest {
    @Test fun appIsHiddenAtLimitAndOverrideIsOnlyValidForCurrentDay() {
        val limits = mapOf("example.app" to 30)
        val usage = mapOf("example.app" to 30 * 60_000L)

        assertTrue(appLimitStatus("example.app", limits, emptyMap(), usage, 100)!!.reached)
        assertFalse(appLimitStatus("example.app", limits, mapOf("example.app" to 100), usage, 100)!!.reached)
        assertTrue(appLimitStatus("example.app", limits, mapOf("example.app" to 99), usage, 100)!!.reached)
    }

    @Test fun remainingTimeIsClampedAndUnknownAppsHaveNoLimit() {
        val active = appLimitStatus("example.app", mapOf("example.app" to 15), emptyMap(), mapOf("example.app" to 5 * 60_000L), 10)
        assertEquals(10 * 60_000L, active!!.remainingMillis)
        val exceeded = appLimitStatus("example.app", mapOf("example.app" to 15), emptyMap(), mapOf("example.app" to 20 * 60_000L), 10)
        assertEquals(0, exceeded!!.remainingMillis)
        assertNull(appLimitStatus("other.app", mapOf("example.app" to 15), emptyMap(), emptyMap(), 10))
    }
}
