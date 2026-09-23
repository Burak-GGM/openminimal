package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test
import org.openminimal.launcher.data.ConfigCodec

class FavoriteSlotsTest {
    @Test fun holesSurviveSavingAndNewFavoritesUseVacancies() {
        val saved = listOf("a", "", "", "b")
        val config = ConfigCodec.decode(ConfigCodec.encode(LauncherConfig(favoriteSlots = saved)))
        assertEquals(saved, config.favoriteSlots)
        assertEquals(listOf("a", "empty:1", "empty:2", "b"), favoriteSlotKeys(listOf("a", "b"), saved, 4, false))
        assertEquals(listOf("a", "c", "empty:2", "b"), favoriteSlotKeys(listOf("a", "b", "c"), saved, 4, false))
    }
    @Test fun hiddenFavoritesReservePositionsAndEditingOffersVacancies() {
        val keys = favoriteSlotKeys(listOf("a", "limited", "b"), listOf("a", "", "limited", "b"), 4, true)
        assertEquals("limited", keys[2])
        assertTrue(keys.size >= 16)
        assertEquals(keys.size, keys.distinct().size)
    }
    @Test fun duplicateAndRemovedEntriesDoNotCollapseInternalHoles() {
        assertEquals(listOf("a", "", "", "b"), normalizeFavoriteSlots(listOf("a", "a", "", "b", "")))
        assertEquals(listOf("empty:0", "b"), favoriteSlotKeys(listOf("b"), listOf("removed", "b"), 4, false))
    }
    @Test fun homeCapacityFitsOnePageAndDiffersByLayout() {
        val base = LauncherConfig(showHomeHeading = true, showAllAppsButton = true, showHomeFooter = true)
        val list = homeFavoriteCapacity(base, 808)
        val grid = homeFavoriteCapacity(base.withAppDisplay(AppDisplay.ICONS), 808)
        assertEquals(5, list)
        assertEquals(12, grid)
        assertTrue(grid > list)
        assertTrue(homeFavoriteCapacity(base.copy(showHomeHeading = false, showHomeFooter = false), 808) > list)
    }

    @Test fun editorUsesExactCapacityWithoutAddingAnotherRow() {
        val keys = favoriteSlotKeys(listOf("a", "b"), emptyList(), 4, editing = true, capacity = 12)
        assertEquals(12, keys.size)
        assertEquals("a", keys.first())
        assertEquals("empty:11", keys.last())
    }

    @Test fun manyGroupHeadingsCannotForceHomePastOnePage() {
        val groupedGrid = LauncherConfig(
            home = HomeScreenConfig(apps = AppLayoutConfig(display = AppDisplay.ICONS), grouped = true),
            showHomeHeading = true,
            showAllAppsButton = true,
            showHomeFooter = true,
        )
        assertEquals(4, homeFavoriteCapacity(groupedGrid, 808, occupiedGroups = 5))
        assertEquals(1, homeFavoriteCapacity(groupedGrid.withAppDisplay(AppDisplay.TEXT_ICONS), 808, occupiedGroups = 5))
    }
}
