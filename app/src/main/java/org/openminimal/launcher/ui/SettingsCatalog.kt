package org.openminimal.launcher.ui

import androidx.annotation.StringRes
import org.openminimal.launcher.R

internal enum class SettingsCategory(@param:StringRes val title: Int, @param:StringRes val subtitle: Int) {
    HOME(R.string.home_screen, R.string.home_settings_hint),
    DRAWER(R.string.app_drawer_title, R.string.drawer_settings_hint),
    APPEARANCE(R.string.appearance, R.string.appearance_settings_hint),
    WALLPAPER(R.string.wallpaper, R.string.wallpaper_hint),
    ICONS(R.string.icons_title, R.string.icon_pack_hint),
    WIDGETS(R.string.settings_applets, R.string.applet_visibility_hint),
    GESTURES(R.string.gestures_title, R.string.gestures_settings_hint),
    PRESETS(R.string.presets, R.string.presets_keep_data),
    BEHAVIOR(R.string.settings_general, R.string.behavior_settings_hint),
    PRIVACY(R.string.privacy, R.string.network_prototype),
    USAGE(R.string.screen_time, R.string.usage_settings_hint),
}

/** One localized catalog powers navigation and search. IDs never depend on translated copy. */
internal enum class SettingsSection(val category: SettingsCategory, @param:StringRes val title: Int, @param:StringRes val keywords: Int) {
    HOME_LAYOUT(SettingsCategory.HOME, R.string.home_layout_title, R.string.home_layout_keywords),
    CLOCK(SettingsCategory.HOME, R.string.clock_appearance, R.string.clock_keywords),
    HOME_TEXT(SettingsCategory.HOME, R.string.home_texts, R.string.home_text_keywords),
    DRAWER_LAYOUT(SettingsCategory.DRAWER, R.string.drawer_layout_title, R.string.drawer_layout_keywords),
    DRAWER_ORGANIZATION(SettingsCategory.DRAWER, R.string.drawer_organization, R.string.drawer_group_keywords),
    COLORS(SettingsCategory.APPEARANCE, R.string.colors, R.string.color_keywords),
    TYPOGRAPHY(SettingsCategory.APPEARANCE, R.string.font_size, R.string.font_keywords),
    WALLPAPER(SettingsCategory.WALLPAPER, R.string.choose_wallpaper, R.string.wallpaper_keywords),
    ICONS(SettingsCategory.ICONS, R.string.icons_title, R.string.icon_keywords),
    APPLETS(SettingsCategory.WIDGETS, R.string.applet_visibility, R.string.applet_keywords),
    GESTURES(SettingsCategory.GESTURES, R.string.gestures_title, R.string.gesture_keywords),
    PRESETS(SettingsCategory.PRESETS, R.string.presets, R.string.preset_keywords),
    GENERAL(SettingsCategory.BEHAVIOR, R.string.settings_general, R.string.general_keywords),
    PRIVACY(SettingsCategory.PRIVACY, R.string.privacy, R.string.privacy_keywords),
    USAGE(SettingsCategory.USAGE, R.string.screen_time, R.string.usage_keywords),
}
