package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test
import org.openminimal.launcher.data.ConfigCodec

class HomeFoldersTest {
    private val folder = HomeFolder("folder:work", "Work", listOf("a", "b"))
    @Test fun foldersAndCustomCopyRoundTripAndSurvivePresets() {
        val c = LauncherConfig(homeFolders = listOf(folder), homeHeading = "Odak zamanı", homeFooter = "One thing", showHomeHeading = false, showHomeFooter = false, showAllAppsButton = false)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        assertEquals(c.homeFolders, c.applyPreset(Preset.ABSOLUTE_FOCUS).homeFolders)
        assertEquals(c.homeHeading, c.applyPreset(Preset.MONOCHROME).homeHeading)
        assertTrue(ConfigCodec.decode("{}").showAllAppsButton)
    }
    @Test fun movingBetweenFoldersNeverDuplicatesOrLosesMembers() {
        val start = StoredState(favorites = listOf("a", "b", "c"), note = "keep", tasks = listOf(TaskItem("1", "keep", false)))
        val grouped = start.saveHomeFolder(folder)
        assertEquals(listOf("folder:work", "c"), homeEntryIds(grouped.favorites, grouped.config.homeFolders))
        val moved = grouped.saveHomeFolder(HomeFolder("folder:other", "Other", listOf("b", "c")))
        assertEquals(listOf("a"), moved.config.homeFolders.first { it.id == folder.id }.appIds)
        assertEquals(start.favorites.toSet(), moved.favorites.toSet())
        assertEquals(start.note, moved.note)
        assertEquals(start.tasks, moved.tasks)
        val ungrouped = moved.removeHomeFolder("folder:other")
        assertEquals(listOf("folder:work", "b", "c"), homeEntryIds(ungrouped.favorites, ungrouped.config.homeFolders))
    }
    @Test fun folderReplacesAnchorAndKeepsUnrelatedGaps() {
        val start = StoredState(favorites = listOf("a", "b", "c"), config = LauncherConfig(favoriteSlots = listOf("a", "", "b", "", "c")))
        val result = start.saveHomeFolder(folder)
        assertEquals(listOf(folder.id, "", "", "", "c"), result.config.favoriteSlots)
        val edited = result.saveHomeFolder(folder.copy(appIds = listOf("a")))
        val slots = favoriteSlotKeys(homeEntryIds(edited.favorites, edited.config.homeFolders), edited.config.favoriteSlots, 4, false)
        assertEquals("b", slots[1])
        assertEquals("c", slots[4])
    }
    @Test fun malformedFoldersCannotNestOrDuplicateApplications() {
        val normalized = normalizeHomeFolders(listOf(folder, HomeFolder("folder:two", "Two", listOf("b", "c", folder.id)), HomeFolder("bad", "Bad", listOf("d"))))
        assertEquals(listOf("c"), normalized.last().appIds)
        assertEquals(2, normalized.size)
        assertEquals(listOf(folder.id), homeEntryIds(listOf("a", "b"), listOf(folder)))
    }
}
