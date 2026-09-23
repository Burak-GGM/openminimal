package org.openminimal.launcher.data

import org.json.JSONObject
import org.openminimal.launcher.model.*

/** One codec for reads and writes so adding a setting cannot reset another setting. */
object ConfigCodec {
    fun decode(serialized: String?): LauncherConfig {
        val c = runCatching { JSONObject(serialized ?: "{}") }.getOrDefault(JSONObject())
        val preset = enumValue(c.optString("preset"), Preset.EVERYDAY)
        val defaults = LauncherConfig().applyPreset(preset)
        val legacyDisplay = if (c.optBoolean("iconsOnly", defaults.iconsOnly)) AppDisplay.ICONS else if (c.optBoolean("icons", defaults.icons)) AppDisplay.TEXT_ICONS else AppDisplay.TEXT
        val legacyGrid = GridLayoutConfig(columns = c.optInt("gridColumns", 4).coerceIn(3, 6))
        val h = c.optJSONObject("home") ?: JSONObject()
        val d = c.optJSONObject("drawer") ?: JSONObject()
        val w = c.optJSONObject("wallpaper") ?: c
        val home = HomeScreenConfig(
            apps = PersonalizationCodec.layout(h.optJSONObject("apps"), AppLayoutConfig(display = legacyDisplay, grid = legacyGrid)),
            grouped = h.optBoolean("grouped", c.optBoolean("groupFavorites", false)),
            clock = PersonalizationCodec.clock(h.optJSONObject("clock"), ClockConfig(
                size = c.optInt("clockSize", defaults.clockSize).coerceIn(48, 96),
                font = enumValue(c.optString("clockFont"), defaults.clockFont),
                alignment = enumValue(c.optString("clockAlignment"), defaults.clockAlignment),
                showDate = c.optBoolean("showDate", defaults.showDate),
            )),
            weather = h.optJSONObject("weather")?.let { w -> WeatherPresentation(
                w.optBoolean("visible", false), w.enum("placement", WeatherPlacement.BELOW_CLOCK),
                w.optBoolean("showIcon", true), w.optBoolean("showTemperature", true),
            ) } ?: WeatherPresentation(),
        )
        val drawer = AppDrawerConfig(apps = PersonalizationCodec.layout(d.optJSONObject("apps"), AppDrawerConfig().apps.copy(display = legacyDisplay, grid = legacyGrid.copy(showLabels = true))))
        return defaults.copy(
            preset = preset,
            selectedCustomPresetId = c.optString("selectedCustomPresetId").takeIf { it.isNotBlank() && it != "null" },
            home = home, drawer = drawer,
            wallpaper = WallpaperConfig(
                mode = w.enum(if (w === c) "wallpaperMode" else "mode", WallpaperMode.THEME),
                syncSystem = w.optBoolean(if (w === c) "syncWallpaper" else "syncSystem", true),
                photoRevision = w.optLong(if (w === c) "wallpaperRevision" else "photoRevision", 0).coerceAtLeast(0),
                color = w.optLong("color", 0xFF181B18) or 0xFF000000,
                gradientEnd = w.optLong("gradientEnd", 0xFF526A55) or 0xFF000000,
                overlayPercent = w.optInt("overlayPercent", 45).coerceIn(0, 80),
            ),
            theme = enumValue(c.optString("theme"), ThemeMode.SYSTEM),
            fontScale = c.optDouble("fontScale", 1.0).toFloat().let { if (it.isFinite()) it.coerceIn(0.85f, 1.3f) else 1f },
            advanced = c.optBoolean("advanced"),
            onlinePreference = c.optBoolean("onlinePreference"),
            onboardingComplete = c.optBoolean("onboardingComplete"),
            language = c.optString("language", "system").takeIf { it in setOf("system", "en", "tr") } ?: "system",
            palette = enumValue(c.optString("palette"), defaults.palette),
            darkBackground = enumValue(c.optString("darkBackground"), defaults.darkBackground),
            monochromeIcons = c.optBoolean("monochromeIcons", defaults.monochromeIcons),
            blackWhiteIcons = c.optBoolean("blackWhiteIcons", false),
            favoriteSlots = normalizeFavoriteSlots(c.optJSONArray("favoriteSlots")?.let { a -> (0 until minOf(a.length(), 512)).map { a.optString(it, "") } }.orEmpty()),
            showAllAppsButton = c.optBoolean("showAllAppsButton", true),
            showHomeHeading = c.optBoolean("showHomeHeading", true),
            homeHeading = c.optString("homeHeading", "").take(160),
            showHomeFooter = c.optBoolean("showHomeFooter", true),
            homeFooter = c.optString("homeFooter", "").take(160),
            homeFolders = normalizeHomeFolders(c.optJSONArray("homeFolders")?.let { a ->
                (0 until minOf(a.length(), 64)).mapNotNull { i -> a.optJSONObject(i)?.let { f ->
                    HomeFolder(f.optString("id"), f.optString("name"), f.optJSONArray("apps")?.let { members ->
                        (0 until minOf(members.length(), 512)).map { members.optString(it) }
                    }.orEmpty())
                } }
            }.orEmpty()),
            swipeUpApps = c.optBoolean("swipeUpApps", true),
            showBottomBar = c.optBoolean("showBottomBar", true),
            showSettingsButton = c.optBoolean("showSettingsButton", true),
            showNotes = c.optBoolean("showNotes", defaults.showNotes),
            showTasks = c.optBoolean("showTasks", defaults.showTasks),
            showUsage = c.optBoolean("showUsage", defaults.showUsage),
            showWidgets = c.optBoolean("showWidgets", defaults.showWidgets),
            hapticFeedback = c.optBoolean("hapticFeedback", true),
            drawerAnimation = c.optBoolean("drawerAnimation", true),
            pressAnimation = c.optBoolean("pressAnimation", true),
            alphabetAnimation = c.optBoolean("alphabetAnimation", true),
            compactAlphabet = c.optBoolean("compactAlphabet", true),
            collapsibleGroups = c.optBoolean("collapsibleGroups", true),
            groupWidgets = c.optBoolean("groupWidgets", true),
            monochromeWidgets = c.optBoolean("monochromeWidgets", false),
            themedIcons = c.optBoolean("themedIcons", false),
            collapsedGroups = c.optJSONArray("collapsedGroups")?.let { a -> (0 until a.length()).mapNotNull { i -> AppGroup.entries.firstOrNull { it.name == a.optString(i) } }.toSet() } ?: emptySet(),
            iconPack = c.optString("iconPack", ""),
            groupApps = c.optBoolean("groupApps", false),
            showAlphabet = c.optBoolean("showAlphabet", true),
            appGroups = c.optJSONObject("appGroups")?.let { groups ->
                groups.keys().asSequence().mapNotNull { id ->
                    AppGroup.entries.firstOrNull { it.name == groups.optString(id) }?.let { id to it }
                }.toMap()
            } ?: emptyMap(),
        )
    }

    fun encode(n: LauncherConfig): String = JSONObject().apply {
        put("wallpaper", JSONObject().apply {
            val w = n.wallpaper
            put("mode", w.mode.name); put("syncSystem", w.syncSystem); put("photoRevision", w.photoRevision)
            put("color", w.color); put("gradientEnd", w.gradientEnd); put("overlayPercent", w.overlayPercent)
        })
        put("home", JSONObject().apply {
            put("apps", PersonalizationCodec.layout(n.home.apps)); put("clock", PersonalizationCodec.clock(n.home.clock))
            put("grouped", n.home.grouped)
            put("weather", JSONObject().put("visible", n.home.weather.visible).put("placement", n.home.weather.placement.name)
                .put("showIcon", n.home.weather.showIcon).put("showTemperature", n.home.weather.showTemperature))
        })
        put("drawer", JSONObject().put("apps", PersonalizationCodec.layout(n.drawer.apps)))
        n.selectedCustomPresetId?.let { put("selectedCustomPresetId", it) }
        put("showAllAppsButton", n.showAllAppsButton)
        put("showHomeHeading", n.showHomeHeading); put("homeHeading", n.homeHeading)
        put("showHomeFooter", n.showHomeFooter); put("homeFooter", n.homeFooter)
        put("homeFolders", org.json.JSONArray().apply { n.homeFolders.forEach { f ->
            put(JSONObject().put("id", f.id).put("name", f.name).put("apps", org.json.JSONArray(f.appIds)))
        } })
        put("schemaVersion", n.schemaVersion); put("preset", n.preset.name)
        put("theme", n.theme.name); put("fontScale", n.fontScale)
        put("advanced", n.advanced); put("onlinePreference", n.onlinePreference)
        put("onboardingComplete", n.onboardingComplete); put("language", n.language)
        put("palette", n.palette.name); put("darkBackground", n.darkBackground.name)
        put("blackWhiteIcons", n.blackWhiteIcons); put("favoriteSlots", org.json.JSONArray(n.favoriteSlots))
        put("monochromeIcons", n.monochromeIcons); put("swipeUpApps", n.swipeUpApps)
        put("showBottomBar", n.showBottomBar); put("showSettingsButton", n.showSettingsButton)
        put("showNotes", n.showNotes); put("showTasks", n.showTasks)
        put("showUsage", n.showUsage); put("showWidgets", n.showWidgets)
        put("hapticFeedback", n.hapticFeedback)
        put("drawerAnimation", n.drawerAnimation)
        put("pressAnimation", n.pressAnimation)
        put("alphabetAnimation", n.alphabetAnimation)
        put("compactAlphabet", n.compactAlphabet)
        put("collapsibleGroups", n.collapsibleGroups)
        put("groupWidgets", n.groupWidgets)
        put("monochromeWidgets", n.monochromeWidgets)
        put("themedIcons", n.themedIcons)
        put("collapsedGroups", org.json.JSONArray(n.collapsedGroups.map { it.name }))
        put("iconPack", n.iconPack); put("groupApps", n.groupApps); put("showAlphabet", n.showAlphabet)
        put("appGroups", JSONObject().apply { n.appGroups.forEach { (id, group) -> put(id, group.name) } })
    }.toString()

    private inline fun <reified T : Enum<T>> enumValue(s: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == s } ?: fallback
}
