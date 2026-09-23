package org.openminimal.launcher

import android.app.Activity
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.openminimal.launcher.data.LauncherRepository
import org.openminimal.launcher.model.LauncherConfig
import org.openminimal.launcher.platform.IconPacks
import org.openminimal.launcher.platform.themeWallpaper

class FixtureIconActivity : Activity()
class FixtureWidgetProvider : AppWidgetProvider()

@RunWith(AndroidJUnit4::class)
class PlatformIntegrationTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Before fun emulatorOnly() { Assume.assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish")) }

    @Test fun blackWhiteIconsContainOnlyBinaryRgbAndKeepAlpha() {
        val original = android.graphics.Bitmap.createBitmap(intArrayOf(0xff446677.toInt(), 0xffdddddd.toInt(), 0x80505050.toInt(), 0), 4, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val result = org.openminimal.launcher.ui.blackWhiteIcon(original)
        for (x in 0..3) {
            val pixel = result.getPixel(x, 0)
            assertTrue((pixel and 0xffffff) == 0 || (pixel and 0xffffff) == 0xffffff)
            assertEquals(original.getPixel(x, 0) ushr 24, pixel ushr 24)
        }
        assertEquals(0xff000000.toInt(), result.getPixel(0, 0))
        assertEquals(0xffffffff.toInt(), result.getPixel(1, 0))
        assertEquals(0xff446677.toInt(), original.getPixel(0, 0))
    }

    @Test fun installedIconPackMapsComponentsAndFallsBackForMissingIcons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtures = InstrumentationRegistry.getInstrumentation().context.packageName
        val service = IconPacks(context)
        assertTrue(service.installed().any { it.packageName == fixtures })
        val pack = service.load(fixtures)
        assertNotNull(pack)
        val bitmap = pack!!.icon(ComponentName("com.android.settings", "com.android.settings.Settings"))
        assertNotNull(bitmap)
        assertEquals(android.graphics.Color.rgb(0, 192, 128), bitmap!!.getPixel(48, 48))
        assertNull(pack.icon(ComponentName("missing.app", "missing.app.Main")))
        assertNull(service.load("missing.icon.pack"))
    }

    @Test fun themeWallpaperUsesDisplayShapeAndExactColor() {
        val color = android.graphics.Color.rgb(25, 155, 90)
        val bitmap = themeWallpaper(1080, 2412, color)
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(2412, bitmap.height)
            assertEquals(color, bitmap.getPixel(540, 1206))
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun savedWallpaperRevisionsKeepDifferentPhotosForLocalPresets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = org.openminimal.launcher.platform.HomeWallpapers(context)
        val revision = System.currentTimeMillis()
        val red = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val blue = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        try {
            runBlocking {
                service.save(red, revision)
                service.save(blue, revision + 1)
                val first = requireNotNull(service.preview(revision))
                val second = requireNotNull(service.preview(revision + 1))
                assertTrue(android.graphics.Color.red(first.getPixel(10, 10)) > 240)
                assertTrue(android.graphics.Color.blue(first.getPixel(10, 10)) < 10)
                assertTrue(android.graphics.Color.blue(second.getPixel(10, 10)) > 240)
                first.recycle(); second.recycle()
            }
        } finally {
            red.recycle(); blue.recycle()
            java.io.File(context.filesDir, "home-wallpaper-$revision.jpg").delete()
            java.io.File(context.filesDir, "home-wallpaper-${revision + 1}.jpg").delete()
        }
    }

    @Test fun widgetPickerGroupsHumanLabelsAndResizeControlsStayHiddenUntilEditing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LauncherRepository(context)
        val old = runBlocking { repository.state.first().config }
        val fixtures = InstrumentationRegistry.getInstrumentation().context.packageName
        var widgetId = -1
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val manager = AppWidgetManager.getInstance(context)
        val previouslyAllowed = false
        device.executeShellCommand("appwidget grantbind --package ${context.packageName}")
        runBlocking { repository.configure { LauncherConfig(onboardingComplete = true, wallpaper = org.openminimal.launcher.model.WallpaperConfig(syncSystem = false), monochromeWidgets = true, language = "en", showNotes = false, showTasks = false, showUsage = false) } }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            ui.waitUntil(10000) { ui.onAllNodesWithTag("home_surface").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("At a glance").performClick()
            ui.onNodeWithText("Add a widget").performScrollTo().performClick()
            ui.onNodeWithTag("widget_picker_list").performScrollToNode(hasTestTag("widget_group:$fixtures"))
            ui.onNodeWithText("Sample agenda").assertDoesNotExist()
            ui.onNodeWithTag("widget_group:$fixtures").performScrollTo().performClick()
            ui.onNodeWithText("Sample agenda").assertIsDisplayed()
            ui.onNodeWithTag("widget_group:$fixtures").performClick()
            ui.onNodeWithText("Sample agenda").assertDoesNotExist()
            ui.onNodeWithText("Search widgets").performTextInput("Sample agenda")
            ui.waitUntil(10000) { ui.onAllNodesWithText("Sample agenda").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Widget & Icon Fixtures").assertIsDisplayed()
            ui.onNodeWithText(fixtures).assertDoesNotExist()
            ui.onNodeWithContentDescription("Widget preview").assertIsDisplayed()
            val preview = ui.onNodeWithContentDescription("Widget preview", useUnmergedTree = true).captureToImage().toPixelMap()
            val color = preview[preview.width / 2, preview.height / 2]
            assertEquals(color.red, color.green, 0.02f)
            ui.onNode(hasText("Sample agenda") and hasClickAction() and !hasSetTextAction()).performClick()
            ui.waitUntil(5000) { runBlocking { repository.state.first().widgets.isNotEmpty() } }
            widgetId = runBlocking { repository.state.first().widgets.last().widgetId }
            ui.onNodeWithText("Shorter").assertDoesNotExist()
            ui.onNodeWithTag("widget_content:$widgetId").performScrollTo()
            val content = ui.onNodeWithTag("widget_content:$widgetId").captureToImage().toPixelMap()
            val gray = content[content.width / 2, content.height / 2]
            assertEquals(gray.red, gray.green, 0.02f)
            ui.onNodeWithTag("edit_widget:$widgetId").performScrollTo().performClick()
            ui.onNodeWithText("Taller").performScrollTo().performClick()
            ui.waitUntil(5000) { runBlocking { repository.state.first().widgets.last().heightDp > 180 } }
            ui.onNodeWithText("Done").performScrollTo().performClick()
            ui.onNodeWithText("Shorter").assertDoesNotExist()
        } finally {
            if (widgetId >= 0) scenario.onActivity { it.removeWidget(widgetId) }
            scenario.close()
            runBlocking { repository.configure { old }; repository.widgets { it.filterNot { slot -> slot.widgetId == widgetId } } }
            if (!previouslyAllowed) device.executeShellCommand("appwidget revokebind --package ${context.packageName}")
        }
    }
}
