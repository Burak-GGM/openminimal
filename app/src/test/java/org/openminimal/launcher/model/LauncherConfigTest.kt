package org.openminimal.launcher.model

import org.junit.Assert.*
import org.junit.Test
import org.openminimal.launcher.data.ConfigCodec
import org.openminimal.launcher.ui.launcherColorScheme
import androidx.compose.ui.graphics.Color

class LauncherConfigTest {
    @Test fun appDisplayMigrationAndBounds() {
        assertEquals(AppDisplay.TEXT, ConfigCodec.decode("""{"icons":false}""").appDisplay)
        assertEquals(AppDisplay.TEXT_ICONS, ConfigCodec.decode("{}").appDisplay)
        assertEquals(6, ConfigCodec.decode("""{"gridColumns":99}""").gridColumns)
        assertEquals(3, ConfigCodec.decode("""{"gridColumns":0}""").gridColumns)
        AppDisplay.entries.forEach { mode ->
            val config = LauncherConfig().withAppDisplay(mode)
            assertEquals(mode, ConfigCodec.decode(ConfigCodec.encode(config)).appDisplay)
        }
    }
    @Test fun migratesOldFocusWithoutEnablingHiddenApplets() {
        val old = """{"preset":"ABSOLUTE_FOCUS","theme":"DARK","icons":true,"language":"tr","onlinePreference":true,"onboardingComplete":true}"""
        val migrated = ConfigCodec.decode(old)
        assertEquals(DarkBackground.PITCH_BLACK, migrated.darkBackground)
        assertEquals(ColorPalette.MONOCHROME, migrated.palette)
        assertTrue(migrated.icons)
        assertTrue(migrated.onlinePreference)
        assertTrue(migrated.swipeUpApps)
        assertEquals(listOf("home", "notes"), migrated.pages().map { it.id })
        assertEquals("tr", migrated.language)
    }
    @Test fun independentPreferencesSurviveSavingAnotherSetting() {
        val customized = LauncherConfig().applyPreset(Preset.ABSOLUTE_FOCUS).copy(
            palette = ColorPalette.OCEAN, darkBackground = DarkBackground.CHARCOAL,
            home = HomeScreenConfig(clock = ClockConfig(size = 54, font = ClockFont.MONOSPACE, alignment = ClockAlignment.CENTER, showDate = false),
                apps = AppLayoutConfig(display = AppDisplay.ICONS, grid = GridLayoutConfig(columns = 5)), grouped = true),
            showBottomBar = false, showSettingsButton = false, swipeUpApps = false,
            hapticFeedback = false, drawerAnimation = false, pressAnimation = false,
            alphabetAnimation = false, compactAlphabet = false, collapsibleGroups = false,
            collapsedGroups = setOf(AppGroup.FINANCE), monochromeWidgets = true, groupWidgets = false,
            blackWhiteIcons = true, favoriteSlots = listOf("", "app/a", "", "app/b"),
            themedIcons = true, wallpaper = WallpaperConfig(syncSystem = false, mode = WallpaperMode.CUSTOM, photoRevision = 12345),
            iconPack = "test.icons", groupApps = true, showAlphabet = false, appGroups = mapOf("test/app" to AppGroup.FINANCE),
            showNotes = false, showTasks = true, showWidgets = true, monochromeIcons = true,
        )
        val saved = ConfigCodec.decode(ConfigCodec.encode(customized)).copy(language = "tr")
        val restored = ConfigCodec.decode(ConfigCodec.encode(saved))
        assertEquals(customized.copy(language = "tr"), restored)
    }
    @Test fun allAppletsCanBeDisabledAndReenabledInFocus() {
        val none = LauncherConfig().applyPreset(Preset.ABSOLUTE_FOCUS).copy(showNotes = false)
        assertEquals(listOf("home"), none.pages().map { it.id })
        val tasks = none.copy(showTasks = true)
        assertEquals(listOf("home", "board"), tasks.pages().map { it.id })
        assertEquals(listOf(BuiltInApplets.tasks.id), tasks.pages()[1].appletIds)
        assertEquals(listOf("home", "notes"), none.copy(showNotes = true).pages().map { it.id })
    }
    @Test fun colorChangeDoesNotChangePresetOrAppletSelection() {
        val initial = LauncherConfig().applyPreset(Preset.ABSOLUTE_FOCUS)
        val changed = initial.copy(palette = ColorPalette.PLUM, darkBackground = DarkBackground.CHARCOAL)
        assertEquals(initial.preset, changed.preset)
        assertEquals(initial.pages(), changed.pages())
        assertNotEquals(launcherColorScheme(initial, true).primary, launcherColorScheme(changed, true).primary)
    }
    @Test fun focusDarkIsTrueBlackAndCanBeOverridden() {
        val focus = LauncherConfig().applyPreset(Preset.ABSOLUTE_FOCUS)
        assertEquals(Color.Black, launcherColorScheme(focus, true).background)
        assertEquals(Color.Black, launcherColorScheme(focus, true).surface)
        assertNotEquals(Color.Black, launcherColorScheme(focus.copy(darkBackground = DarkBackground.CHARCOAL), true).background)
        assertNotEquals(Color.Black, launcherColorScheme(focus, false).background)
    }
    @Test fun unknownConfigurationValuesFallBackSafely() {
        val config = ConfigCodec.decode("""{"palette":"FUTURE","darkBackground":"UNKNOWN","fontScale":50}""")
        assertEquals(ColorPalette.SAGE, config.palette)
        assertEquals(1.3f, config.fontScale)
        assertTrue(config.showBottomBar)
        assertTrue(config.swipeUpApps)
        assertEquals(76, config.clockSize)
    }

    @Test fun presetsProvideUsefulClockDefaults() {
        val everyday = LauncherConfig().applyPreset(Preset.EVERYDAY)
        val monochrome = LauncherConfig().applyPreset(Preset.MONOCHROME)
        val focus = LauncherConfig().applyPreset(Preset.ABSOLUTE_FOCUS)
        assertEquals(ClockFont.SYSTEM, everyday.clockFont)
        assertEquals(ClockFont.MONOSPACE, monochrome.clockFont)
        assertEquals(ClockAlignment.CENTER, focus.clockAlignment)
        assertFalse(focus.showDate)
    }
}
