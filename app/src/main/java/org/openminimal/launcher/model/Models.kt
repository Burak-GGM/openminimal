package org.openminimal.launcher.model

enum class AppDisplay { TEXT, TEXT_ICONS, ICONS }

enum class Preset { EVERYDAY, MODERN, MONOCHROME, ABSOLUTE_FOCUS, TEXT_ONLY }
enum class AppGroup { GAMES, HEALTH, FINANCE, PRODUCTIVITY, SOCIAL, MEDIA, MAPS, NEWS, OTHER }
enum class WallpaperMode { THEME, CUSTOM, SOLID, GRADIENT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorPalette { SAGE, MONOCHROME, OCEAN, SAND, PLUM }
enum class DarkBackground { CHARCOAL, PITCH_BLACK }
enum class ClockFont { SYSTEM, MONOSPACE }
enum class ClockAlignment { START, CENTER }
enum class PageKind { HOME, BOARD, FULL_APPLET }
enum class AppletSize { COMPACT, WIDE, FULL }
enum class Capability { LOCAL_STORAGE, USAGE_READ, OPEN_APP, USER_SELECTED_FILES, NETWORK }

// Stable identifiers are independent of translated titles and future rendering engines.
data class AppletDescriptor(
    val id: String,
    val version: Int,
    val supportedSizes: Set<AppletSize>,
    val capabilities: Set<Capability>,
)

object BuiltInApplets {
    val notes = AppletDescriptor("openminimal.notes", 1, setOf(AppletSize.WIDE, AppletSize.FULL), setOf(Capability.LOCAL_STORAGE, Capability.OPEN_APP))
    val tasks = AppletDescriptor("openminimal.tasks", 1, setOf(AppletSize.COMPACT, AppletSize.WIDE), setOf(Capability.LOCAL_STORAGE))
    val usage = AppletDescriptor("openminimal.usage", 1, setOf(AppletSize.WIDE, AppletSize.FULL), setOf(Capability.USAGE_READ))
}

data class PageSpec(val id: String, val kind: PageKind, val appletIds: List<String> = emptyList())

data class LauncherConfig(
    val schemaVersion: Int = 9,
    val preset: Preset = Preset.EVERYDAY,
    val selectedCustomPresetId: String? = null,
    val home: HomeScreenConfig = HomeScreenConfig(),
    val drawer: AppDrawerConfig = AppDrawerConfig(),
    val wallpaper: WallpaperConfig = WallpaperConfig(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1f,
    val advanced: Boolean = false,
    val onlinePreference: Boolean = false,
    val onboardingComplete: Boolean = false,
    val language: String = "system",
    val palette: ColorPalette = ColorPalette.SAGE,
    val darkBackground: DarkBackground = DarkBackground.CHARCOAL,
    val monochromeIcons: Boolean = false,
    val blackWhiteIcons: Boolean = false,
    val showAllAppsButton: Boolean = true,
    val showHomeHeading: Boolean = true,
    val homeHeading: String = "",
    val showHomeFooter: Boolean = true,
    val homeFooter: String = "",
    val homeFolders: List<HomeFolder> = emptyList(),
    val favoriteSlots: List<String> = emptyList(),
    val swipeUpApps: Boolean = true,
    val showBottomBar: Boolean = true,
    val showSettingsButton: Boolean = true,
    val showNotes: Boolean = true,
    val showTasks: Boolean = true,
    val showUsage: Boolean = true,
    val showWidgets: Boolean = true,
    val hapticFeedback: Boolean = true,
    val drawerAnimation: Boolean = true,
    val pressAnimation: Boolean = true,
    val alphabetAnimation: Boolean = true,
    val compactAlphabet: Boolean = true,
    val collapsibleGroups: Boolean = true,
    val collapsedGroups: Set<AppGroup> = emptySet(),
    val groupWidgets: Boolean = true,
    val monochromeWidgets: Boolean = false,
    val themedIcons: Boolean = false,
    val iconPack: String = "",
    val groupApps: Boolean = false,
    val showAlphabet: Boolean = true,
    val appGroups: Map<String, AppGroup> = emptyMap(),
) {
    // Read-only adapters for existing Home/folder code. Drawer must read drawer.apps explicitly.
    val icons get() = home.apps.showIcons
    val iconsOnly get() = home.apps.isGrid
    val gridColumns get() = home.apps.grid.columns
    val groupFavorites get() = home.grouped
    val clockSize get() = home.clock.size
    val clockFont get() = home.clock.font
    val clockAlignment get() = home.clock.alignment
    val showDate get() = home.clock.showDate
    val appDisplay get() = home.apps.display
    val syncWallpaper get() = wallpaper.syncSystem
    val wallpaperMode get() = wallpaper.mode
    val wallpaperRevision get() = wallpaper.photoRevision
    fun withAppDisplay(display: AppDisplay) = copy(home = home.copy(apps = home.apps.copy(display = display)))

    fun applyPreset(value: Preset): LauncherConfig {
        val focus = value == Preset.ABSOLUTE_FOCUS
        val modern = value == Preset.MODERN
        val text = focus || value == Preset.TEXT_ONLY
        val defaults = LauncherConfig()
        return applySnapshot(defaults.copy(
            preset = value, selectedCustomPresetId = null,
            homeHeading = homeHeading, homeFooter = homeFooter,
            home = HomeScreenConfig(
                apps = AppLayoutConfig(display = if (modern) AppDisplay.ICONS else if (text) AppDisplay.TEXT else AppDisplay.TEXT_ICONS,
                    grid = GridLayoutConfig(showLabels = modern)),
                clock = ClockConfig(
                    size = when (value) { Preset.MONOCHROME -> 72; Preset.ABSOLUTE_FOCUS -> 80; else -> 76 },
                    font = if (value == Preset.MONOCHROME) ClockFont.MONOSPACE else ClockFont.SYSTEM,
                    alignment = if (focus) ClockAlignment.CENTER else ClockAlignment.START,
                    showDate = !focus,
                ),
            ),
            drawer = AppDrawerConfig().let { it.copy(apps = it.apps.copy(display = if (modern) AppDisplay.ICONS else if (text) AppDisplay.TEXT else AppDisplay.TEXT_ICONS)) },
            showHomeHeading = !modern && !text, showHomeFooter = !modern && !text,
            palette = if (value == Preset.EVERYDAY || modern) ColorPalette.SAGE else ColorPalette.MONOCHROME,
            darkBackground = if (focus) DarkBackground.PITCH_BLACK else DarkBackground.CHARCOAL,
            monochromeIcons = value == Preset.MONOCHROME, blackWhiteIcons = false,
            showNotes = true, showTasks = !focus, showUsage = !focus, showWidgets = !focus,
        ))
    }
    fun pages(): List<PageSpec> = buildList {
        add(PageSpec("home", PageKind.HOME))
        if (showTasks || showUsage || showWidgets) {
            add(PageSpec("board", PageKind.BOARD, buildList {
                if (showTasks) add(BuiltInApplets.tasks.id)
                if (showNotes) add(BuiltInApplets.notes.id)
                if (showUsage) add(BuiltInApplets.usage.id)
            }))
        }
        if (showNotes) add(PageSpec("notes", PageKind.FULL_APPLET, listOf(BuiltInApplets.notes.id)))
    }
}

data class TaskItem(val id: String, val text: String, val done: Boolean = false)
data class WidgetSlot(val widgetId: Int, val heightDp: Int = 180, val enabled: Boolean = true)
data class StoredState(
    val config: LauncherConfig = LauncherConfig(),
    val customPresets: List<CustomPreset> = emptyList(),
    val note: String = "",
    val tasks: List<TaskItem> = emptyList(),
    val favorites: List<String> = emptyList(),
    val widgets: List<WidgetSlot> = emptyList(),
    val dailyLimits: Map<String, Int> = emptyMap(),
    val limitOverrides: Map<String, Long> = emptyMap(),
    val focusRules: Map<String, AppFocusRule> = emptyMap(),
)

data class LimitStatus(
    val limitMinutes: Int,
    val usedMillis: Long,
    val overriddenToday: Boolean,
) {
    val reached: Boolean get() = !overriddenToday && usedMillis >= limitMinutes * 60_000L
    val remainingMillis: Long get() = (limitMinutes * 60_000L - usedMillis).coerceAtLeast(0)
}

fun appLimitStatus(
    packageName: String,
    limits: Map<String, Int>,
    overrides: Map<String, Long>,
    usage: Map<String, Long>,
    epochDay: Long,
): LimitStatus? {
    val minutes = limits[packageName]?.takeIf { it > 0 } ?: return null
    return LimitStatus(minutes, usage[packageName] ?: 0L, overrides[packageName] == epochDay)
}

// Pure event accounting is tested independently from Android and excludes overlaps.
data class UsageEvent(val timestamp: Long, val packageName: String, val kind: Kind) {
    enum class Kind { RESUME, PAUSE, SCREEN_OFF }
}

fun usageDurations(events: List<UsageEvent>, start: Long, end: Long): Map<String, Long> {
    require(end >= start)
    val result = mutableMapOf<String, Long>()
    var active: String? = null
    var since = start
    fun close(at: Long) {
        active?.let { pkg ->
            val duration = (at.coerceAtMost(end) - since.coerceAtLeast(start)).coerceAtLeast(0)
            if (duration > 0) result[pkg] = result.getOrDefault(pkg, 0) + duration
        }
        active = null
    }
    for (event in events.sortedBy { it.timestamp }) {
        if (event.timestamp > end) break
        when (event.kind) {
            UsageEvent.Kind.RESUME -> {
                close(event.timestamp)
                active = event.packageName
                since = event.timestamp
            }
            UsageEvent.Kind.PAUSE -> if (event.packageName == active) close(event.timestamp)
            UsageEvent.Kind.SCREEN_OFF -> close(event.timestamp)
        }
    }
    close(end)
    return result
}
