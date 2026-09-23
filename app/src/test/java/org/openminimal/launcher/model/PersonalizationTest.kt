package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test
import org.openminimal.launcher.data.ConfigCodec
import org.openminimal.launcher.data.CustomPresetCodec

class PersonalizationTest {
    @Test fun legacyLayoutMigratesToIndependentScreens() {
        val migrated = ConfigCodec.decode("""{"schemaVersion":8,"iconsOnly":true,"gridColumns":5,"clockSize":62,"showDate":false}""")
        assertTrue(migrated.home.apps.isGrid)
        assertTrue(migrated.drawer.apps.isGrid)
        assertEquals(5, migrated.drawer.apps.grid.columns)
        assertEquals(62, migrated.home.clock.size)
        val changed = migrated.withAppDisplay(AppDisplay.TEXT)
        assertTrue(changed.drawer.apps.isGrid)
        assertEquals(changed, ConfigCodec.decode(ConfigCodec.encode(changed)))
    }
    @Test fun newNestedFieldsWinAndUnknownValuesFallBack() {
        val decoded = ConfigCodec.decode("""{"iconsOnly":true,"home":{"apps":{"display":"TEXT","list":{"textSize":900,"alignment":"FUTURE"}}},"drawer":{"apps":{"display":"TEXT_ICONS"}}}""")
        assertEquals(AppDisplay.TEXT, decoded.home.apps.display)
        assertEquals(AppDisplay.TEXT_ICONS, decoded.drawer.apps.display)
        assertEquals(32, decoded.home.apps.list.textSize)
        assertEquals(HorizontalPlacement.START, decoded.home.apps.list.alignment)
    }
    @Test fun presetStatusUsesStructuralSettingsEquality() {
        val base = LauncherConfig().applyPreset(Preset.MONOCHROME)
        assertEquals(PresetStatus.BuiltIn(Preset.MONOCHROME), base.presetStatus())
        val changed = base.copy(home = base.home.copy(apps = base.home.apps.copy(list = base.home.apps.list.copy(iconSize = 44))))
        assertEquals(PresetStatus.Custom, changed.presetStatus())
        assertEquals(base.presetStatus(), base.copy(language = "tr", onlinePreference = true).presetStatus())
        val saved = CustomPreset("local", "My setup", changed)
        assertEquals(PresetStatus.Saved("local", "My setup"), changed.copy(selectedCustomPresetId = "local").presetStatus(listOf(saved)))
        assertEquals(PresetStatus.Custom, changed.copy(selectedCustomPresetId = "deleted").presetStatus())
    }
    @Test fun presetsPreservePrivateStateAndRoundTripWithoutPrivateMetadata() {
        val current = LauncherConfig(onlinePreference = true, onboardingComplete = true, language = "tr",
            homeFolders = listOf(HomeFolder("folder:1", "Mine", listOf("app"))), favoriteSlots = listOf("", "app"), wallpaper = WallpaperConfig(syncSystem = false))
        val changed = current.applyPreset(Preset.ABSOLUTE_FOCUS)
        assertEquals(current.homeFolders, changed.homeFolders)
        assertEquals(current.favoriteSlots, changed.favoriteSlots)
        assertTrue(changed.onlinePreference)
        assertFalse(changed.syncWallpaper)
        val saved = CustomPreset("id", "Name", changed)
        val roundTrip = CustomPresetCodec.decode(CustomPresetCodec.encode(listOf(saved))).single()
        assertEquals(changed.presetSnapshot(), roundTrip.config)
        assertTrue(roundTrip.config.homeFolders.isEmpty())
        assertFalse(roundTrip.config.onlinePreference)
    }
}
