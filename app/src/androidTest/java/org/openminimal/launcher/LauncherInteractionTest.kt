package org.openminimal.launcher

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openminimal.launcher.data.LauncherRepository
import org.openminimal.launcher.model.*

/** Emulator-only UI regressions; preserve the emulator's prior launcher configuration. */
@RunWith(AndroidJUnit4::class)
class LauncherInteractionTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var repository: LauncherRepository
    private lateinit var original: LauncherConfig
    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalFavorites = emptyList<String>()
    private var permissionMode: Int? = null

    @Before fun prepare() {
        Assume.assumeTrue("Run UI regressions on an emulator, not the owner's phone", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = LauncherRepository(context)
        original = runBlocking { repository.state.first().config }
        originalFavorites = runBlocking { repository.state.first().favorites }
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }
    @After fun restore() {
        scenario?.close()
        if (::repository.isInitialized) runBlocking {
            val current = repository.state.first().favorites
            current.filterNot { it in originalFavorites }.forEach { repository.toggleFavorite(it) }
            originalFavorites.filterNot { it in current }.forEach { repository.toggleFavorite(it) }
            if (originalFavorites.isNotEmpty()) repository.reorderFavorites(originalFavorites)
        }
        // Favorite mutations can adjust sparse slots/folders. Restore exact configuration last.
        if (::original.isInitialized) runBlocking { repository.configure { original } }
        permissionMode?.let {
            val mode = when(it) { AppOpsManager.MODE_ALLOWED -> "allow"; AppOpsManager.MODE_IGNORED -> "ignore"; AppOpsManager.MODE_ERRORED -> "deny"; else -> "default" }
            device.executeShellCommand("appops set org.openminimal.launcher GET_USAGE_STATS $mode")
        }
    }
    private fun launch(config: LauncherConfig = LauncherConfig(), language: String = "en") {
        runBlocking { repository.configure { config.copy(onboardingComplete = true, language = language, wallpaper = config.wallpaper.copy(syncSystem = false)) } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("home_surface").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun settings() {
        ui.onNodeWithContentDescription("Settings").performClick()
        ui.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    private fun openSection(category: String, section: String) {
        ui.onNodeWithTag("settings_catalog").performScrollToNode(hasTestTag("settings_category:$category"))
        ui.onNodeWithTag("settings_category:$category").performClick()
        ui.onNodeWithTag("settings_link:$section").performClick()
        ui.onNodeWithTag("settings_section:$section").assertIsDisplayed()
    }
    private fun returnHome() {
        repeat(3) { ui.onNodeWithContentDescription("Back").performClick() }
        ui.onNodeWithTag("home_surface").assertIsDisplayed()
    }

    @Test fun homeTextCustomizationAndHiddenDrawerRecovery() {
        launch()
        settings()
        openSection("HOME", "HOME_TEXT")
        ui.onNodeWithText("Customize home text").performScrollTo().performClick()
        ui.onNodeWithTag("heading_input").performTextReplacement("Make room for today")
        ui.onNodeWithTag("footer_input").performTextReplacement("One thing at a time")
        ui.onNodeWithText("Save").performClick()
        returnHome()
        ui.onNodeWithTag("home_heading").assertTextEquals("Make room for today")
        ui.onNodeWithTag("home_footer").performScrollTo().assertTextEquals("One thing at a time")
        runBlocking { repository.configure { it.copy(showAllAppsButton = false, swipeUpApps = false, showHomeHeading = false, showHomeFooter = false, showBottomBar = false, showSettingsButton = false) } }
        scenario?.recreate()
        ui.onNodeWithTag("all_apps_button").assertDoesNotExist()
        ui.onNodeWithTag("home_heading").assertDoesNotExist()
        ui.onNodeWithTag("home_footer").assertDoesNotExist()
        ui.onNodeWithTag("home_surface").performTouchInput { longClick(Offset(width * .98f, height * .85f)) }
        ui.onNodeWithText("All apps").performClick()
        ui.onNodeWithTag("app_drawer").assertIsDisplayed()
        assertEquals("Make room for today", runBlocking { repository.state.first().config.homeHeading })
    }
    @Test fun listHomeHasFixedCapacityAndCannotScrollOrAddPastIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apps = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }
        val config = LauncherConfig(showHomeHeading = true, showAllAppsButton = true, showHomeFooter = true, homeFooter = "Fixed footer", swipeUpApps = false)
        launch(config)
        ui.waitForIdle()
        val capacity = ui.onNodeWithTag("home_favorites").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].toInt()
        Assume.assumeTrue(apps.size > capacity && capacity > 0)
        runBlocking {
            val current = repository.state.first().favorites
            current.filterNot { it in apps.take(capacity).map { app -> app.id } }.forEach { repository.toggleFavorite(it) }
            apps.take(capacity).filterNot { it.id in repository.state.first().favorites }.forEach { repository.toggleFavorite(it.id) }
        }
        scenario?.recreate()
        ui.waitUntil(5000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().size == capacity }
        val before = ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        ui.onNodeWithTag("home_favorites").performTouchInput { swipeUp() }
        val after = ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        assertEquals(before, after)
        ui.onNodeWithTag("all_apps_button").performClick()
        ui.onNodeWithText("Find an app").performTextInput(apps[capacity].label)
        ui.onNode(hasText(apps[capacity].label) and hasClickAction() and !hasSetTextAction())
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
        ui.onNodeWithText("Add to favorites").assertIsNotEnabled()
        ui.onNodeWithText("Home is full for this layout. Remove an item, use a folder, or hide a home element to make room.").assertIsDisplayed()
        assertEquals(capacity, runBlocking { repository.state.first().favorites.size })
    }
    @Test fun iconFoldersCanBeCreatedEditedMovedAndRemoved() { verifyFolderFlow(true) }
    @Test fun textFoldersOpenAsAQuietNamedList() { verifyFolderFlow(false) }
    private fun verifyFolderFlow(iconsOnly: Boolean) {
        launch(LauncherConfig().withAppDisplay(if (iconsOnly) AppDisplay.ICONS else AppDisplay.TEXT))
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().size >= 2 }
        val tags = ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().take(2).map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        val ids = tags.map { it.removePrefix("favorite:") }
        ui.onNodeWithTag(tags[0]).performTouchInput { longClick() }
        ui.onNodeWithText("Add to folder").performClick()
        ui.onNodeWithText("Create folder").performClick()
        ui.onNodeWithTag("folder_editor").assertDoesNotExist()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.isNotEmpty() } }
        val created = runBlocking { repository.state.first().config.homeFolders.single() }
        ui.onNodeWithTag(created.id).performClick()
        ui.onNodeWithContentDescription("Edit folder").performClick()
        ui.onNodeWithTag("folder_name").performTextReplacement("Essentials")
        ui.onNodeWithTag("folder_pick:${ids[1]}").performScrollTo().performClick()
        ui.onNodeWithTag("save_folder").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.singleOrNull()?.let { it.name == "Essentials" && it.appIds.size == 2 } == true } }
        val folder = runBlocking { repository.state.first().config.homeFolders.single() }
        ui.onNodeWithTag(folder.id).performClick()
        ui.onNodeWithTag("folder_sheet").assertIsDisplayed()
        scenario?.recreate()
        ui.onNodeWithTag("folder_sheet").assertIsDisplayed()
        if (iconsOnly) ui.onNodeWithTag("folder_dialog").assertIsDisplayed()
        else ui.onNodeWithTag("folder_dialog").assertDoesNotExist()
        captureReview(if (iconsOnly) "folder-icons" else "folder-text")
        ui.onNodeWithContentDescription("Edit folder").performClick()
        ui.onNodeWithTag("folder_name").performTextReplacement("Daily")
        ui.onNodeWithTag("folder_pick:${ids[1]}").performScrollTo().performClick()
        ui.onNodeWithTag("save_folder").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.single().name == "Daily" } }
        ui.onNodeWithTag(tags[1]).assertExists()
        if (iconsOnly) {
            ui.onNodeWithTag(folder.id).performTouchInput { longClick() }
            ui.onNodeWithText("Edit layout").performClick()
            val from = ui.onNodeWithTag(folder.id).fetchSemanticsNode().boundsInRoot.center
            val to = ui.onNodeWithTag("empty_slot:7").fetchSemanticsNode().boundsInRoot.center
            val home = ui.onNodeWithTag("home_surface").fetchSemanticsNode().boundsInRoot.topLeft
            ui.onNodeWithTag("home_surface").performTouchInput { swipe(from - home, to - home, 600) }
            ui.waitUntil(5000) { runBlocking { repository.state.first().config.favoriteSlots.getOrNull(7) == folder.id } }
            ui.onNodeWithTag("layout_done").performClick()
        }
        ui.onNodeWithTag(folder.id).performTouchInput { longClick() }
        ui.onNodeWithText("Remove folder").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.isEmpty() } }
        assertTrue(runBlocking { repository.state.first().favorites.containsAll(ids) })
        ui.onNodeWithTag(tags[0]).assertExists()
    }
    @Test fun droppingIconCreatesFolderImmediatelyAndCanAddAnotherApp() {
        launch(LauncherConfig().withAppDisplay(AppDisplay.ICONS))
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().size >= 2 }
        val tags = ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().take(3).map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        ui.onNodeWithTag(tags[0]).performTouchInput { longClick() }
        ui.onNodeWithText("Edit layout").performClick()
        val from = ui.onNodeWithTag(tags[0]).fetchSemanticsNode().boundsInRoot.center
        val to = ui.onNodeWithTag(tags[1]).fetchSemanticsNode().boundsInRoot.center
        val origin = ui.onNodeWithTag("home_surface").fetchSemanticsNode().boundsInRoot.topLeft
        ui.onNodeWithTag("home_surface").performTouchInput { swipe(from - origin, to - origin, 250) }
        ui.onNodeWithTag("folder_editor").assertDoesNotExist()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.isNotEmpty() } }
        val folder = runBlocking { repository.state.first().config.homeFolders.single() }
        assertEquals(tags.take(2).map { it.removePrefix("favorite:") }.toSet(), folder.appIds.toSet())
        val third = ui.onNodeWithTag(tags[2]).fetchSemanticsNode().boundsInRoot.center
        val destination = ui.onNodeWithTag(folder.id).fetchSemanticsNode().boundsInRoot.center
        ui.onNodeWithTag("home_surface").performTouchInput { swipe(third - origin, destination - origin, 250) }
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.single().appIds.size == 3 } }
        ui.onNodeWithTag("layout_done").performClick()
        ui.onNodeWithTag(folder.id).performClick()
        ui.onNodeWithTag("folder_dialog").assertIsDisplayed()
        ui.onNodeWithContentDescription("Close").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("folder_dialog").fetchSemanticsNodes().isEmpty() }
        runBlocking { repository.configure { it.copy(drawerAnimation = false) } }
        ui.onNodeWithTag(folder.id).performClick()
        ui.onNodeWithTag("folder_dialog").assertIsDisplayed()
        device.pressBack()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("folder_dialog").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun settingsSearchOpensClockAndKeepsDestinationOnRecreation() {
        launch(); settings()
        ui.onNodeWithTag("settings_search").performTextInput("clock")
        ui.onNodeWithTag("settings_link:CLOCK").performClick()
        ui.onNodeWithTag("live_preview").assertIsDisplayed()
        ui.onNodeWithTag("settings_section:CLOCK").assertIsDisplayed()
        scenario?.recreate()
        ui.onNodeWithTag("settings_section:CLOCK").assertIsDisplayed()
        ui.onNodeWithContentDescription("Back").performClick()
        ui.onNodeWithTag("settings_search").assertTextContains("clock")
    }
    @Test fun clockAppearanceChoicesPersist() {
        launch(); settings()
        openSection("HOME", "CLOCK")
        ui.onNodeWithText("Monospace").performScrollTo().performClick()
        ui.onNodeWithText("Centered").performScrollTo().performClick()
        ui.onNodeWithText("Show date below clock").performScrollTo().performClick()
        ui.waitUntil(5_000) {
            runBlocking {
                repository.state.first().config.let {
                    it.clockFont == ClockFont.MONOSPACE && it.clockAlignment == ClockAlignment.CENTER && !it.showDate
                }
            }
        }
        scenario?.recreate()
        ui.onNodeWithTag("settings_screen").assertIsDisplayed()
        assertEquals(ClockFont.MONOSPACE, runBlocking { repository.state.first().config.clockFont })
    }
    @Test fun usageAppletOpensScreenTimeSettings() {
        launch()
        ui.onNodeWithText("At a glance").performClick()
        ui.onNodeWithTag("usage_applet").performScrollTo().performClick()
        ui.onNodeWithTag("settings_section:USAGE").assertIsDisplayed()
        ui.onNodeWithText("System screen-time settings").assertIsDisplayed()
    }
    @Test fun dailyLimitCanBeSetFromAppMenuAndAppearsInScreenTimeSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        permissionMode = context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        device.executeShellCommand("appops set org.openminimal.launcher GET_USAGE_STATS ignore")
        launch()
        val app = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }.first()
        val previous = runBlocking { repository.state.first().dailyLimits[app.packageName] }
        val previousRule = runBlocking { repository.state.first().focusRules[app.packageName] }
        try {
            ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
            ui.onNodeWithText("Find an app").performTextInput(app.label)
            ui.onNode(hasText(app.label) and hasClickAction() and !hasSetTextAction()).performTouchInput { longClick() }
            ui.onNodeWithText("Focus Gate & daily budget").performClick()
            ui.onNodeWithTag("daily_limit_slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(15f) }
            ui.onNodeWithTag("save_daily_limit").performScrollTo().assertIsEnabled().performClick()
            ui.waitForIdle()
            ui.waitUntil(10_000) { runBlocking { repository.state.first().dailyLimits[app.packageName] == 15 } }
            ui.onNodeWithContentDescription("Back").performClick()
            ui.waitUntil(5_000) { ui.onAllNodesWithTag("home_surface").fetchSemanticsNodes().isNotEmpty() }
            ui.waitUntil(5_000) { ui.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("Settings").performClick()
            openSection("USAGE", "USAGE")
            ui.onNodeWithText(app.label).assertIsDisplayed()
            ui.onNodeWithText("15 minutes each day", substring = true).assertExists()
        } finally {
            runBlocking { repository.setFocusPolicy(app.packageName, previous, previousRule) }
        }
    }
    @Test fun everyLaunchTextGateMustBeCompletedBeforeOpeningApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixturePackage = InstrumentationRegistry.getInstrumentation().context.packageName
        val app = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }
            .first { it.packageName == fixturePackage }
        val previousLimit = runBlocking { repository.state.first().dailyLimits[fixturePackage] }
        val previousRule = runBlocking { repository.state.first().focusRules[fixturePackage] }
        val phrase = "I choose this intentionally"
        try {
            runBlocking {
                repository.setFocusPolicy(fixturePackage, null,
                    AppFocusRule(everyLaunchChallenge = FocusChallenge(FocusChallengeType.TEXT, phrase)))
            }
            launch()
            ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
            ui.onNodeWithText("Find an app").performTextInput(app.label)
            ui.onNode(hasText(app.label) and hasClickAction() and !hasSetTextAction()).performClick()
            ui.onNodeWithText("Pause before opening ${app.label}").assertIsDisplayed()
            device.takeScreenshot(java.io.File(context.cacheDir, "review-focus-gate.png"))
            ui.onNodeWithTag("gate_continue").assertIsNotEnabled()
            ui.onNodeWithTag("gate_phrase_input").performTextInput(phrase)
            ui.onNodeWithTag("gate_continue").performScrollTo().assertIsEnabled().performClick()
            assertTrue(device.wait(Until.hasObject(By.pkg(fixturePackage)), 5_000))
            device.pressHome()
        } finally {
            runBlocking { repository.setFocusPolicy(fixturePackage, previousLimit, previousRule) }
        }
    }
    @Test fun homeClockOpensClockApp() {
        launch()
        ui.onNodeWithTag("home_clock").performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.deskclock")), 5000))
        device.pressBack()
    }
    @Test fun disabledMotionAndHapticsKeepLongPressMenuAndDrawerUsable() {
        launch(LauncherConfig(drawerAnimation = false, pressAnimation = false, hapticFeedback = false, showBottomBar = false))
        ui.onNodeWithTag("home_surface").performTouchInput { longClick(Offset(width * 0.98f, height * 0.85f)) }
        ui.onNodeWithText("Settings").assertIsDisplayed()
        ui.onNodeWithContentDescription("Close").performClick()
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithTag("apps_list").performTouchInput { swipeDown() }
        ui.onNodeWithTag("home_surface").assertIsDisplayed()
    }
    @Test fun collapsedGroupsDisableAlphabetNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apps = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }
        val last = apps.last()
        launch(LauncherConfig(groupApps = true, appGroups = apps.associate { it.id to AppGroup.FINANCE }))
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithTag("group:FINANCE").performClick()
        ui.waitUntil(5000) { ui.onNodeWithTag("group:FINANCE").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "Collapsed" }
        ui.onNodeWithTag("group:FINANCE").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Collapsed"))
        ui.onNodeWithText(apps.first().label).assertDoesNotExist()
        ui.onNodeWithTag("alphabet_rail").assertDoesNotExist()
        ui.onNodeWithTag("group:FINANCE").performClick()
        ui.waitUntil(5000) { ui.onNodeWithTag("group:FINANCE").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "Expanded" }
        ui.onNodeWithText(apps.first().label).assertIsDisplayed()
        assertFalse(runBlocking { repository.state.first().config.collapsedGroups.contains(AppGroup.FINANCE) })
    }
    @Test fun upwardSwipeOpensAppsByDefault() {
        launch()
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithTag("app_drawer").assertIsDisplayed()
    }
    @Test fun downwardSwipeClosesDrawerAtTop() {
        launch()
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithTag("apps_list").performTouchInput { swipeDown() }
        ui.onNodeWithTag("home_surface").assertIsDisplayed()
    }
    @Test fun categoryAndAlphabetPreferencesAreIndependent() {
        launch()
        settings()
        openSection("DRAWER", "DRAWER_ORGANIZATION")
        ui.onNodeWithText("Group apps by category").performClick()
        ui.waitUntil(5000) { ui.onAllNodes(hasText("Alphabet scroller") and !isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Alphabet scroller").assertIsNotEnabled()
        returnHome()
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithTag("alphabet_rail").assertDoesNotExist()
        val config = runBlocking { repository.state.first().config }
        assertTrue(config.groupApps)
        assertTrue(config.showAlphabet) // Stored preference returns when grouping is disabled.
    }
    @Test fun alphabetDragScrollsWithoutClosingDrawer() {
        val savedGroups = AppGroup.entries.toSet()
        launch(LauncherConfig(collapsedGroups = savedGroups))
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.waitUntil(10000) { ui.onAllNodesWithTag("alphabet_rail").fetchSemanticsNodes().isNotEmpty() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apps = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }
        val initials = apps.map { it.label.first().uppercase() }.distinct().sorted()
        val before = ui.onNodeWithTag("alphabet_rail").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        ui.onNodeWithTag("alphabet_rail").performTouchInput { swipe(center, center + Offset(0f, 220f), durationMillis = 500) }
        ui.onNodeWithTag("app_drawer").assertIsDisplayed()
        val after = ui.onNodeWithTag("alphabet_rail").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertTrue(initials.indexOf(after) - initials.indexOf(before) in 1..3)
        ui.onNodeWithTag("alphabet_rail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(100f) }
        ui.onNodeWithText(apps.last().label).assertIsDisplayed()
        captureReview("alphabet")
        assertEquals(savedGroups, runBlocking { repository.state.first().config.collapsedGroups })
    }
    @Test fun manualGroupAssignmentSurvivesTurningGroupingOff() {
        launch(LauncherConfig(groupApps = true))
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }.first()
        ui.onNodeWithText("Find an app").performTextInput(app.label)
        ui.onNode(hasText(app.label) and hasClickAction() and !hasSetTextAction()).performTouchInput { longClick() }
        ui.onNodeWithText("Choose group").performClick()
        ui.onNodeWithText("Finance").performScrollTo().performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.appGroups[app.id] == AppGroup.FINANCE } }
        ui.onNodeWithText("Finance").assertIsDisplayed()
        runBlocking { repository.configure { it.copy(groupApps = false) } }
        ui.onNodeWithText("Finance").assertDoesNotExist()
        assertEquals(AppGroup.FINANCE, runBlocking { repository.state.first().config.appGroups[app.id] })
    }
    @Test fun favoriteEditLayoutPersistsOrderWithoutOpeningDrawer() {
        verifyEditLayout(LauncherConfig())
    }
    @Test fun iconGridEditMovesHorizontallyAndSurvivesRecreation() {
        verifyEditLayout(LauncherConfig().withAppDisplay(AppDisplay.ICONS))
    }
    private fun verifyEditLayout(config: LauncherConfig) {
        launch(config)
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().size >= 3 }
        val firstId = ui.onAllNodes(hasTestTagPrefix("favorite:"))[0].fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("favorite:")
        ui.onNodeWithTag("favorite:$firstId").performTouchInput { longClick() }
        ui.onNodeWithTag("home_actions").assertDoesNotExist()
        ui.onNodeWithText("Edit layout").performClick()
        ui.onNodeWithTag("layout_editor").assertIsDisplayed()
        if (config.iconsOnly) captureReview("layout-editor")
        ui.onNodeWithTag("favorite:$firstId").performTouchInput { longClick() }
        ui.onNodeWithTag("app_actions").assertDoesNotExist()
        val rows = ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes()
        val first = ui.onNodeWithTag("favorite:$firstId").fetchSemanticsNode().boundsInRoot.center
        val targetBounds = rows[2].boundsInRoot
        val target = if (config.iconsOnly) Offset(targetBounds.left + 4f, targetBounds.center.y) else targetBounds.center
        ui.onNodeWithTag("favorite:$firstId").performTouchInput {
            swipe(center, center + target - first, durationMillis = 700)
        }
        ui.onNodeWithTag("app_drawer").assertDoesNotExist()
        ui.waitUntil(5000) { runBlocking { repository.state.first().favorites.indexOf(firstId) >= 1 } }
        scenario?.recreate()
        ui.onNodeWithTag("layout_editor").assertIsDisplayed()
        val topId = runBlocking { repository.state.first().favorites.first() }
        val topBounds = ui.onNodeWithTag("favorite:$topId").fetchSemanticsNode().boundsInRoot
        val top = if (config.iconsOnly) Offset(topBounds.left + 4f, topBounds.center.y) else topBounds.center
        val moved = ui.onNodeWithTag("favorite:$firstId").fetchSemanticsNode().boundsInRoot.center
        ui.onNodeWithTag("favorite:$firstId").performTouchInput {
            swipe(center, center + top - moved, durationMillis = 700)
        }
        ui.waitUntil(5000) { runBlocking { repository.state.first().favorites.firstOrNull() == firstId } }
        ui.onNodeWithTag("layout_done").performClick()
        ui.onNodeWithTag("layout_editor").assertDoesNotExist()
        if (config.iconsOnly) captureReview("icon-home")
        ui.onNodeWithTag("favorite:$firstId").performTouchInput { longClick() }
        ui.onNodeWithTag("app_actions").assertIsDisplayed()
        ui.onNodeWithTag("home_actions").assertDoesNotExist()
    }
    @Test fun iconGridGroupsCollapseAndSettingsPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apps = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }
        launch(LauncherConfig(home = HomeScreenConfig(apps = AppLayoutConfig(display = AppDisplay.ICONS)),
            drawer = AppDrawerConfig(apps = AppLayoutConfig(display = AppDisplay.ICONS, grid = GridLayoutConfig(showLabels = true))), groupApps = true,
            appGroups = apps.associate { it.id to AppGroup.FINANCE }))
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithContentDescription(apps.first().label, useUnmergedTree = true).assertExists()
        ui.onNodeWithText(apps.first().label).assertIsDisplayed()
        captureReview("drawer-icons")
        ui.onNodeWithTag("group:FINANCE").performClick()
        ui.onNodeWithContentDescription(apps.first().label, useUnmergedTree = true).assertDoesNotExist()
        ui.onNodeWithTag("group:FINANCE").performClick()
        ui.onNodeWithContentDescription(apps.first().label, useUnmergedTree = true).performTouchInput { longClick() }
        ui.onNodeWithTag("app_actions").assertIsDisplayed()
        ui.onNodeWithContentDescription("App info").assertIsDisplayed()
        assertTrue(runBlocking { repository.state.first().config.iconsOnly })
    }
    @Test fun favoriteCanMoveIntoEmptyCellAndKeepItsGap() {
        launch(LauncherConfig().withAppDisplay(AppDisplay.ICONS))
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().isNotEmpty() }
        val firstId = ui.onAllNodes(hasTestTagPrefix("favorite:"))[0].fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("favorite:")
        ui.onNodeWithTag("favorite:$firstId").performTouchInput { longClick() }
        ui.onNodeWithText("Edit layout").performClick()
        val from = ui.onNodeWithTag("favorite:$firstId").fetchSemanticsNode().boundsInRoot.center
        val to = ui.onNodeWithTag("empty_slot:7").fetchSemanticsNode().boundsInRoot.center
        ui.onNodeWithTag("favorite:$firstId").performTouchInput { swipe(center, center + to - from, 700) }
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.favoriteSlots.getOrNull(7) == firstId } }
        ui.onNodeWithTag("layout_done").performClick()
        scenario?.recreate()
        val config = runBlocking { repository.state.first().config }
        assertEquals(firstId, config.favoriteSlots[7])
        assertTrue(config.favoriteSlots.take(7).any { it.isEmpty() })
        ui.onNodeWithTag("empty_slot:6").assertExists()
        captureReview("icon-home")
    }
    @Test fun stationaryLongPressOnFavoriteOpensActionsWithoutReordering() {
        launch()
        ui.waitUntil(10_000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().isNotEmpty() }
        val before = runBlocking { repository.state.first().favorites }
        ui.onAllNodes(hasTestTagPrefix("favorite:"))[0].performTouchInput { longClick() }
        ui.onNodeWithText("Focus Gate & daily budget").assertIsDisplayed()
        ui.onNodeWithText("Choose group").assertIsDisplayed()
        captureReview("app-menu")
        ui.onNodeWithTag("home_actions").assertDoesNotExist()
        ui.onNodeWithContentDescription("Close").performClick()
        ui.onNodeWithTag("home_actions").assertDoesNotExist()
        assertEquals(before, runBlocking { repository.state.first().favorites })
    }
    @Test fun nativeShortcutLaunchAndAppInfoAndUninstallCancellation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previousHome = device.executeShellCommand("cmd role get-role-holders android.app.role.HOME").trim().lines().filter { it.isNotBlank() }
        val fixturePackage = InstrumentationRegistry.getInstrumentation().context.packageName
        try {
            device.executeShellCommand("cmd role add-role-holder android.app.role.HOME ${context.packageName}")
            ui.waitUntil(5000) { context.getSystemService(android.content.pm.LauncherApps::class.java).hasShortcutHostPermission() }
            launch()
            fun openFixtureMenu() {
                ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
                ui.onNodeWithText("Find an app").performTextInput("Shortcut fixture")
                ui.onNode(hasText("Shortcut fixture") and hasClickAction() and !hasSetTextAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
            }
            openFixtureMenu()
            try {
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Open sample action").fetchSemanticsNodes().isNotEmpty() }
            } catch (failure: Throwable) {
                android.util.Log.e("ShortcutTest", ui.onAllNodes(isRoot()).printToString())
                device.takeScreenshot(java.io.File(context.cacheDir, "shortcut-failure.png"))
                throw failure
            }
            val app = runBlocking { org.openminimal.launcher.platform.DeviceServices(context).apps() }.first { it.packageName == fixturePackage }
            val loaded = org.openminimal.launcher.platform.readAppShortcuts(context, app)
            assertNotNull(loaded.items.first().icon)
            ui.onNodeWithTag("shortcut:fixture_action5").assertDoesNotExist()
            ui.onNodeWithText("Show all (5)").performScrollTo().performClick()
            ui.onNodeWithTag("shortcut:fixture_action5").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Show fewer").performScrollTo().performClick()
            ui.onNodeWithTag("shortcut:fixture_action5").assertDoesNotExist()
            ui.onNodeWithText("Open sample action").performScrollTo()
            captureReview("native-shortcuts")
            ui.onNodeWithText("Open sample action").performClick()
            assertTrue(device.wait(Until.hasObject(By.pkg(fixturePackage)), 5000))
            device.pressHome()
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
            ui.waitUntil(5000) { ui.onAllNodesWithTag("home_surface").fetchSemanticsNodes().isNotEmpty() }
            openFixtureMenu()
            ui.onNodeWithContentDescription("App info").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 5000))
            device.pressHome()
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
            ui.waitUntil(5000) { ui.onAllNodesWithTag("home_surface").fetchSemanticsNodes().isNotEmpty() }
            openFixtureMenu()
            ui.onNodeWithContentDescription("Uninstall").performClick()
            val cancel = device.wait(Until.findObject(By.res("android:id/button2")), 5000)
            assertNotNull("System uninstall confirmation must offer cancellation", cancel)
            cancel.click()
            assertNotNull(context.packageManager.getPackageInfo(fixturePackage, 0))
        } finally {
            previousHome.forEach { device.executeShellCommand("cmd role add-role-holder android.app.role.HOME $it") }
            if (previousHome.isEmpty()) device.executeShellCommand("cmd role remove-role-holder android.app.role.HOME ${context.packageName}")
        }
    }
    @Test fun independentLayoutsAndLiveClockPreviewPersist() {
        launch(); settings(); openSection("HOME", "HOME_LAYOUT")
        ui.onNodeWithTag("layout_grid").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.home.apps.isGrid } }
        assertEquals(AppDisplay.TEXT_ICONS, runBlocking { repository.state.first().config.drawer.apps.display })
        returnHome()
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        val app = runBlocking { org.openminimal.launcher.platform.DeviceServices(ApplicationProvider.getApplicationContext()).apps() }.first()
        ui.onNodeWithText(app.label).assertIsDisplayed()
        ui.onNodeWithContentDescription("Back").performClick()
        settings(); openSection("HOME", "CLOCK")
        val before = ui.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot.height
        val oldSize = runBlocking { repository.state.first().config.home.clock.size }
        ui.onNodeWithTag("clock_size_slider").performTouchInput {
            down(center); moveTo(Offset(width * .97f, center.y), delayMillis = 250)
        }
        ui.waitForIdle()
        assertTrue(ui.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot.height > before)
        assertEquals(oldSize, runBlocking { repository.state.first().config.home.clock.size })
        ui.onNodeWithTag("clock_size_slider").performTouchInput { up() }
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.home.clock.size > oldSize } }
        scenario?.recreate()
        ui.onNodeWithTag("live_preview").assertIsDisplayed()
        device.takeScreenshot(java.io.File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "review-clock-preview.png"))
    }

    @Test fun customPresetCanBeSavedAppliedRenamedAndDeleted() {
        val oldIds = runBlocking { repository.state.first().customPresets.map { it.id }.toSet() }
        Assume.assumeTrue(oldIds.size < MAX_CUSTOM_PRESETS)
        try {
            launch(); settings(); openSection("PRESETS", "PRESETS")
            ui.onNodeWithTag("save_as_preset").performScrollTo().performClick()
            ui.onNodeWithTag("preset_name").performTextInput("Test setup")
            ui.onNodeWithText("Save").performClick()
            ui.waitUntil(5000) { runBlocking { repository.state.first().customPresets.any { it.id !in oldIds } } }
            val saved = runBlocking { repository.state.first().customPresets.first { it.id !in oldIds } }
            runBlocking { repository.configure { it.copy(palette = ColorPalette.PLUM) } }
            ui.onNodeWithTag("current_preset").performScrollTo().assertTextEquals("Current preset: Custom")
            ui.onNodeWithTag("apply_preset:${saved.id}").performScrollTo().performClick()
            ui.waitUntil(5000) { runBlocking { repository.state.first().config.palette == saved.config.palette } }
            ui.onNode(hasContentDescription("Rename preset") and hasAnyAncestor(hasTestTag("preset:${saved.id}"))).performClick()
            ui.onNodeWithTag("preset_name").performTextReplacement("Renamed setup")
            ui.onNodeWithText("Save").performClick()
            ui.waitUntil(5000) { runBlocking { repository.state.first().customPresets.any { it.id == saved.id && it.name == "Renamed setup" } } }
            ui.onNode(hasContentDescription("Delete preset") and hasAnyAncestor(hasTestTag("preset:${saved.id}"))).performScrollTo().performClick()
            ui.onNodeWithTag("confirm_delete_preset").performClick()
            ui.waitUntil(5000) { runBlocking { repository.state.first().customPresets.none { it.id == saved.id } } }
            assertEquals(saved.config.palette, runBlocking { repository.state.first().config.palette })
        } finally {
            runBlocking { repository.state.first().customPresets.filterNot { it.id in oldIds }.forEach { repository.deletePreset(it.id) } }
        }
    }

    @Test fun folderOrderCanChangeAndMembersCanMoveOut() {
        launch(LauncherConfig().withAppDisplay(AppDisplay.ICONS))
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().size >= 3 }
        val ids = ui.onAllNodes(hasTestTagPrefix("favorite:")).fetchSemanticsNodes().take(3).map {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("favorite:")
        }
        val folder = HomeFolder("folder:order-test", "Order test", ids)
        runBlocking { repository.saveFolder(folder) }
        ui.onNodeWithTag(folder.id).performClick()
        ui.onNodeWithContentDescription("Edit folder").performClick()
        ui.onNodeWithTag("folder_order_toggle").performClick()
        ui.onNode(hasContentDescription("Move down") and hasAnyAncestor(hasTestTag("folder_order:${ids[0]}"))).performClick()
        ui.onNodeWithTag("save_folder").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.first { it.id == folder.id }.appIds.first() == ids[1] } }
        ui.onNodeWithTag(folder.id).performClick()
        val apps = runBlocking { org.openminimal.launcher.platform.DeviceServices(ApplicationProvider.getApplicationContext()).apps() }
        val label = apps.first { it.id == ids[0] }.label
        ui.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("folder_sheet")), useUnmergedTree = true)
            .performTouchInput { longClick() }
        ui.onNodeWithText("Move out of folder").performScrollTo().performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.homeFolders.first { it.id == folder.id }.appIds.none { it == ids[0] } } }
        assertTrue(runBlocking { ids[0] in repository.state.first().favorites })
    }

    @Test fun wallpaperSourceSelectionPersistsWithoutEnablingSystemSync() {
        launch(LauncherConfig(theme = ThemeMode.LIGHT)); settings(); openSection("WALLPAPER", "WALLPAPER")
        ui.onNodeWithTag("choose_wallpaper").performClick()
        ui.onNodeWithText("Gradient").performClick()
        ui.onNodeWithText("Ocean").performScrollTo().performClick()
        ui.onNodeWithText("Ocean").assertIsSelected()
        ui.waitForIdle()
        device.takeScreenshot(java.io.File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "review-wallpaper.png"))
        ui.onNodeWithTag("apply_wallpaper").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.wallpaper.mode == WallpaperMode.GRADIENT } }
        assertFalse(runBlocking { repository.state.first().config.wallpaper.syncSystem })
        assertEquals(0xFFF0F4F7, runBlocking { repository.state.first().config.wallpaper.color })
        ui.onNodeWithTag("choose_wallpaper").performClick()
        ui.onNodeWithText("Theme color").performClick()
        ui.onNodeWithTag("apply_wallpaper").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.wallpaper.mode == WallpaperMode.THEME } }
    }

    private fun captureReview(name: String) {
        ui.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (name == "native-shortcuts" || name.startsWith("folder-")) {
            // Dialog pixel-copy uses a different window origin; capture the composited display.
            device.waitForIdle(500)
            device.takeScreenshot(java.io.File(context.cacheDir, "review-$name.png"))
            return
        }
        val tag = when (name) { "app-menu", "native-shortcuts" -> "app_actions"; "alphabet", "drawer-icons" -> "app_drawer"; else -> "home_surface" }
        java.io.File(context.cacheDir, "review-$name.png").outputStream().use { output ->
            ui.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }
    @Test fun disabledSwipeKeepsHomeAndButtonStillWorks() {
        launch(LauncherConfig(swipeUpApps = false))
        ui.onNodeWithTag("home_surface").performTouchInput { swipeUp() }
        ui.onNodeWithTag("app_drawer").assertDoesNotExist()
        ui.onNodeWithText("All apps").performScrollTo().performClick()
        ui.onNodeWithTag("app_drawer").assertIsDisplayed()
    }
    @Test fun hiddenNavigationHasLongPressSettingsRecovery() {
        launch(LauncherConfig(showBottomBar = false, showSettingsButton = false))
        ui.onNodeWithTag("bottom_bar").assertDoesNotExist()
        ui.onNodeWithTag("home_surface").performTouchInput { longClick(Offset(width * 0.98f, height * 0.85f)) }
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
    @Test fun turkishLongPressMenuUsesSelectedLanguage() {
        launch(LauncherConfig(showBottomBar = false, showSettingsButton = false), language = "tr")
        ui.onNodeWithTag("home_surface").performTouchInput { longClick(Offset(width * 0.5f, height * 0.9f)) }
        ui.onNodeWithText("Ayarlar").assertIsDisplayed().performClick()
        ui.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
    @Test fun hidingAppletsDoesNotLeaveDeadTabsOrExitSettings() {
        launch()
        settings()
        openSection("WIDGETS", "APPLETS")
        listOf("Notes", "Today’s priorities", "Your time today", "Android widgets").forEach { label ->
            ui.onNodeWithText(label).performScrollTo().performClick()
            ui.onNodeWithTag("settings_screen").assertIsDisplayed()
        }
        returnHome()
        ui.onNodeWithText("Notes").assertDoesNotExist()
        ui.onNodeWithText("At a glance").assertDoesNotExist()
        ui.onNodeWithTag("home_surface").assertIsDisplayed()
        assertEquals(listOf("home"), runBlocking { repository.state.first().config.pages().map { it.id } })
    }
    @Test fun grantedUsageAccessDoesNotShowRequestInSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        permissionMode = context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        device.executeShellCommand("appops set org.openminimal.launcher GET_USAGE_STATS allow")
        launch()
        settings()
        openSection("PRIVACY", "PRIVACY")
        ui.onNodeWithText("Allow usage access").assertDoesNotExist()
    }
    @Test fun paletteCanChangeWithoutChangingFocusPages() {
        launch(LauncherConfig().applyPreset(Preset.ABSOLUTE_FOCUS))
        settings()
        openSection("APPEARANCE", "COLORS")
        ui.onNodeWithText("Ocean").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Ocean").performClick()
        ui.waitUntil(5000) { runBlocking { repository.state.first().config.palette == ColorPalette.OCEAN } }
        val config = runBlocking { repository.state.first().config }
        assertEquals(Preset.ABSOLUTE_FOCUS, config.preset)
        assertEquals(listOf("home", "notes"), config.pages().map { it.id })
    }
    @Test fun defaultLauncherButtonOpensSystemRoleConsent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        Assume.assumeFalse(context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME))
        launch()
        settings()
        openSection("BEHAVIOR", "GENERAL")
        ui.onNodeWithText("Set as default launcher").performClick()
        assertTrue("System role consent must be displayed", device.wait(Until.hasObject(By.pkg("com.google.android.permissioncontroller")), 5000) || device.hasObject(By.pkg("com.android.permissioncontroller")))
        assertTrue(device.hasObject(By.text("Open&minimal")))
        device.pressBack()
    }
}
