package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    @Test fun fullRowsAccountForEveryCategoryHeading() {
        val ids = listOf("game1", "work1", "work2", "health1", "media1")
        val groups: (String) -> String = { it.dropLast(1) }
        val fitted = fitHomeEntries(ids, 290f, 4, 76f, 6f, 20, groups, 48f)
        assertEquals(listOf("game1", "work1", "work2"), fitted)
        assertTrue(homeEntriesHeight(fitted, 4, 76f, 6f, groups, 48f) <= 290)
    }
    @Test fun accessibleTextReducesCapacityWithoutShrinkingTargets() {
        val layout = AppLayoutConfig(display = AppDisplay.TEXT, list = ListLayoutConfig(textSize = 32))
        val height = appEntryHeight(layout, 2f)
        assertTrue(height > 90)
        assertEquals(2, fitHomeEntries((1..20).map { "$it" }, 240f, 1, height, 12f, 20).size)
        assertEquals(0, fitHomeEntries(listOf("a"), 20f, 1, height, 12f, 20).size)
    }
    @Test fun gridBoundsUseActualColumnsAndSavedVacanciesRemainReachable() {
        assertEquals(3, fittedGridColumns(6, 180f))
        val visible = favoriteSlotKeys(listOf("a", "b"), listOf("a", "", "", "", "b"), 2, true, capacity = 4)
        assertTrue("b" in visible)
        assertEquals(4, visible.size)
    }
}
