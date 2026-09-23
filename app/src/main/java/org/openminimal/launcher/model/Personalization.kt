package org.openminimal.launcher.model

enum class HorizontalPlacement { START, CENTER, END }
enum class VerticalPlacement { TOP, CENTER, BOTTOM }
enum class TextWeight(val value: Int) { LIGHT(300), REGULAR(400), MEDIUM(500), BOLD(700) }
enum class ClockFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }
enum class DateStyle { LONG, SHORT, NUMERIC }

data class ListLayoutConfig(
    val textSize: Int = 23,
    val weight: TextWeight = TextWeight.REGULAR,
    val iconSize: Int = 32,
    val iconGap: Int = 18,
    val rowSpacing: Int = 6,
    val widthPercent: Int = 100,
    val alignment: HorizontalPlacement = HorizontalPlacement.START,
    val placement: VerticalPlacement = VerticalPlacement.TOP,
    val maxItems: Int = 10,
)

data class GridLayoutConfig(val columns: Int = 4, val iconSize: Int = 48, val showLabels: Boolean = false)
data class AppLayoutConfig(
    val display: AppDisplay = AppDisplay.TEXT_ICONS,
    val list: ListLayoutConfig = ListLayoutConfig(),
    val grid: GridLayoutConfig = GridLayoutConfig(),
) {
    val isGrid get() = display == AppDisplay.ICONS
    val showIcons get() = display != AppDisplay.TEXT
}

data class ClockConfig(
    val visible: Boolean = true,
    val size: Int = 76,
    val font: ClockFont = ClockFont.SYSTEM,
    val weight: TextWeight = TextWeight.LIGHT,
    val alignment: ClockAlignment = ClockAlignment.START,
    val format: ClockFormat = ClockFormat.SYSTEM,
    val showDate: Boolean = true,
    val dateStyle: DateStyle = DateStyle.LONG,
    val showWeekday: Boolean = true,
    val bottomSpacing: Int = 32,
)

// Presentation only. A future provider supplies data separately; no fabricated weather or networking.
enum class WeatherPlacement { BELOW_CLOCK, BESIDE_CLOCK }
data class WeatherPresentation(
    val visible: Boolean = false,
    val placement: WeatherPlacement = WeatherPlacement.BELOW_CLOCK,
    val showIcon: Boolean = true,
    val showTemperature: Boolean = true,
)

data class HomeScreenConfig(
    val apps: AppLayoutConfig = AppLayoutConfig(),
    val clock: ClockConfig = ClockConfig(),
    val weather: WeatherPresentation = WeatherPresentation(),
    val grouped: Boolean = false,
)

data class AppDrawerConfig(
    val apps: AppLayoutConfig = AppLayoutConfig(
        list = ListLayoutConfig(textSize = 18, iconSize = 36),
        grid = GridLayoutConfig(showLabels = true),
    ),
)

data class WallpaperConfig(
    val mode: WallpaperMode = WallpaperMode.THEME,
    val syncSystem: Boolean = true,
    val photoRevision: Long = 0,
    val color: Long = 0xFF181B18,
    val gradientEnd: Long = 0xFF526A55,
    val overlayPercent: Int = 45,
)

/** Privacy, content, device bindings and ephemeral category collapse are not style presets. */
fun LauncherConfig.presetSnapshot(): LauncherConfig = copy(
    schemaVersion = 9, preset = Preset.EVERYDAY, selectedCustomPresetId = null,
    advanced = false, onlinePreference = false, onboardingComplete = false, language = "system",
    homeFolders = emptyList(), favoriteSlots = emptyList(), appGroups = emptyMap(),
    collapsedGroups = emptySet(), wallpaper = wallpaper.copy(syncSystem = true),
)

/** Apply only settings captured by a preset, never private content or platform consent. */
fun LauncherConfig.applySnapshot(snapshot: LauncherConfig): LauncherConfig = snapshot.copy(
    schemaVersion = 9, advanced = advanced, onlinePreference = onlinePreference,
    onboardingComplete = onboardingComplete, language = language, homeFolders = homeFolders,
    favoriteSlots = favoriteSlots, appGroups = appGroups, collapsedGroups = collapsedGroups,
    wallpaper = snapshot.wallpaper.copy(syncSystem = wallpaper.syncSystem),
)

const val MAX_CUSTOM_PRESETS = 12
data class CustomPreset(val id: String, val name: String, val config: LauncherConfig, val version: Int = 1)
sealed interface PresetStatus {
    data class BuiltIn(val preset: Preset) : PresetStatus
    data class Saved(val id: String, val name: String) : PresetStatus
    data object Custom : PresetStatus
}

fun LauncherConfig.presetStatus(saved: List<CustomPreset> = emptyList()): PresetStatus {
    val selected = selectedCustomPresetId?.let { id -> saved.firstOrNull { it.id == id } }
    val reference = if (selectedCustomPresetId != null) selected?.config else LauncherConfig().applyPreset(preset)
    if (reference == null || presetSnapshot() != reference.presetSnapshot()) return PresetStatus.Custom
    return if (selected != null) PresetStatus.Saved(selected.id, selected.name) else PresetStatus.BuiltIn(preset)
}
