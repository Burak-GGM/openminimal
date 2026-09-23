package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test
import org.openminimal.launcher.model.UsageEvent.Kind.*

class UsageDurationsTest {
    @Test fun clipsSessionAcrossMidnight() {
        val actual = usageDurations(listOf(UsageEvent(80, "reader", RESUME), UsageEvent(130, "reader", PAUSE)), 100, 200)
        assertEquals(mapOf("reader" to 30L), actual)
    }
    @Test fun doesNotDoubleCountTransitionsOrLatePauses() {
        val actual = usageDurations(listOf(
            UsageEvent(100, "a", RESUME), UsageEvent(130, "b", RESUME),
            UsageEvent(132, "a", PAUSE), UsageEvent(150, "b", PAUSE),
        ), 100, 200)
        assertEquals(mapOf("a" to 30L, "b" to 20L), actual)
    }
    @Test fun screenOffEndsOpenSession() {
        val actual = usageDurations(listOf(UsageEvent(120, "a", RESUME), UsageEvent(160, "", SCREEN_OFF)), 100, 300)
        assertEquals(mapOf("a" to 40L), actual)
    }
    @Test fun ongoingSessionIsCountedUntilNow() {
        assertEquals(mapOf("a" to 50L), usageDurations(listOf(UsageEvent(150, "a", RESUME)), 100, 200))
    }
    @Test fun emptyAndOldDataDoNotFabricateUsage() {
        assertTrue(usageDurations(emptyList(), 100, 200).isEmpty())
        assertTrue(usageDurations(listOf(UsageEvent(10, "a", RESUME), UsageEvent(20, "a", PAUSE)), 100, 200).isEmpty())
    }
    @Test fun repeatedResumeDoesNotLoseOrDuplicateTime() {
        assertEquals(mapOf("a" to 100L), usageDurations(listOf(UsageEvent(100, "a", RESUME), UsageEvent(150, "a", RESUME)), 100, 200))
    }
    @Test fun focusPresetPreservesPrivateStateAndConnectionPreference() {
        val before = StoredState(
            config = LauncherConfig(onlinePreference = true, onboardingComplete = true, language = "tr"),
            note = "keep this", tasks = listOf(TaskItem("1", "read")),
            favorites = listOf("app/activity"), widgets = listOf(WidgetSlot(42)),
        )
        val after = before.copy(config = before.config.applyPreset(Preset.ABSOLUTE_FOCUS))
        assertEquals(before.note, after.note)
        assertEquals(before.tasks, after.tasks)
        assertEquals(before.widgets, after.widgets)
        assertEquals(before.favorites, after.favorites)
        assertTrue(after.config.onlinePreference)
        assertEquals("tr", after.config.language)
        assertFalse(after.config.icons)
        assertFalse(after.config.pages().any { it.kind == PageKind.BOARD })
        assertTrue(after.config.applyPreset(Preset.EVERYDAY).pages().any { it.kind == PageKind.BOARD })
    }
}
