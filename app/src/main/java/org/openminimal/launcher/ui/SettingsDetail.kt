package org.openminimal.launcher.ui

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openminimal.launcher.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.UsageSnapshot

@Composable internal fun SettingsDetail(section: SettingsSection, state: StoredState, usage: UsageSnapshot,
    model: LauncherViewModel, activity: MainActivity) {
    val c = state.config
    when (section) {
        SettingsSection.HOME_LAYOUT, SettingsSection.DRAWER_LAYOUT -> LayoutPersonalization(c, model, section == SettingsSection.HOME_LAYOUT)
        SettingsSection.CLOCK -> ClockPersonalization(c, model)
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (section) {
                SettingsSection.GENERAL -> {
                    if (activity.isDefaultHome.value) MutedText(stringResource(R.string.default_launcher_active))
                    else Button(onClick = activity::requestHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.default_launcher)) }
                    TextButton(onClick = activity::openHomeSettings) { Text(stringResource(R.string.home_system_settings)) }
                    LanguagePicker(c, model)
                    HorizontalDivider()
                    MutedText(stringResource(R.string.prototype_scope))
                }
                SettingsSection.HOME_TEXT -> HomeTextControls(c, model)
                SettingsSection.COLORS -> {
                    EnumChips(c.theme, ThemeMode.entries, { when(it) { ThemeMode.SYSTEM -> R.string.system; ThemeMode.LIGHT -> R.string.light; ThemeMode.DARK -> R.string.dark } }) { v -> model.configure { it.copy(theme = v) } }
                    ColorControls(c, model)
                }
                SettingsSection.TYPOGRAPHY -> {
                    var value by remember(c.fontScale) { mutableFloatStateOf(c.fontScale) }
                    Text(stringResource(R.string.font_size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_eyebrow), fontSize = (22 * value).sp)
                    Slider(value, { value = it }, valueRange = .85f..1.3f,
                        onValueChangeFinished = { model.configure { it.copy(fontScale = value) } })
                }
                SettingsSection.WALLPAPER -> WallpaperControls(c, model)
                SettingsSection.ICONS -> IconSettings(c, model)
                SettingsSection.DRAWER_ORGANIZATION -> {
                    SettingSwitch(stringResource(R.string.group_apps), c.groupApps) { v -> model.configure { it.copy(groupApps = v) } }
                    MutedText(stringResource(R.string.group_apps_hint))
                    SettingSwitch(stringResource(R.string.alphabet_scroll), c.showAlphabet && !c.groupApps, enabled = !c.groupApps) { v -> model.configure { it.copy(showAlphabet = v) } }
                    MutedText(stringResource(if (c.groupApps) R.string.alphabet_grouped_hint else R.string.alphabet_hint))
                    SettingSwitch(stringResource(R.string.compact_alphabet), c.compactAlphabet, enabled = !c.groupApps) { v -> model.configure { it.copy(compactAlphabet = v) } }
                    SettingSwitch(stringResource(R.string.alphabet_animation), c.alphabetAnimation, enabled = !c.groupApps) { v -> model.configure { it.copy(alphabetAnimation = v) } }
                    SettingSwitch(stringResource(R.string.collapsible_groups), c.collapsibleGroups) { v -> model.configure { it.copy(collapsibleGroups = v) } }
                    TextButton(onClick = { model.configure { it.copy(collapsedGroups = emptySet()) } }) { Text(stringResource(R.string.expand_all_groups)) }
                }
                SettingsSection.GESTURES -> {
                    SettingSwitch(stringResource(R.string.show_all_apps_button), c.showAllAppsButton) { v -> model.configure { it.copy(showAllAppsButton = v) } }
                    MutedText(stringResource(R.string.all_apps_recovery_hint))
                    SettingSwitch(stringResource(R.string.swipe_up_apps), c.swipeUpApps) { v -> model.configure { it.copy(swipeUpApps = v) } }
                    MutedText(stringResource(R.string.swipe_close_hint))
                    SettingSwitch(stringResource(R.string.drawer_animation), c.drawerAnimation) { v -> model.configure { it.copy(drawerAnimation = v) } }
                    SettingSwitch(stringResource(R.string.press_animation), c.pressAnimation) { v -> model.configure { it.copy(pressAnimation = v) } }
                    SettingSwitch(stringResource(R.string.haptic_feedback), c.hapticFeedback) { v -> model.configure { it.copy(hapticFeedback = v) } }
                    SettingSwitch(stringResource(R.string.show_bottom_bar), c.showBottomBar) { v -> model.configure { it.copy(showBottomBar = v) } }
                    SettingSwitch(stringResource(R.string.show_settings_button), c.showSettingsButton) { v -> model.configure { it.copy(showSettingsButton = v) } }
                    MutedText(stringResource(R.string.hidden_settings_hint))
                }
                SettingsSection.APPLETS -> {
                    MutedText(stringResource(R.string.applet_visibility_hint))
                    SettingSwitch(stringResource(R.string.notes), c.showNotes) { v -> model.configure { it.copy(showNotes = v) } }
                    SettingSwitch(stringResource(R.string.priority), c.showTasks) { v -> model.configure { it.copy(showTasks = v) } }
                    SettingSwitch(stringResource(R.string.usage_title), c.showUsage) { v -> model.configure { it.copy(showUsage = v) } }
                    SettingSwitch(stringResource(R.string.widgets), c.showWidgets) { v -> model.configure { it.copy(showWidgets = v) } }
                    SettingSwitch(stringResource(R.string.group_widgets), c.groupWidgets) { v -> model.configure { it.copy(groupWidgets = v) } }
                    SettingSwitch(stringResource(R.string.monochrome_widgets), c.monochromeWidgets) { v -> model.configure { it.copy(monochromeWidgets = v) } }
                    MutedText(stringResource(R.string.monochrome_widgets_hint))
                    state.widgets.forEach { slot ->
                        val info = remember(slot.widgetId) { AppWidgetManager.getInstance(activity).getAppWidgetInfo(slot.widgetId) }
                        SettingSwitch(info?.loadLabel(activity.packageManager) ?: stringResource(R.string.widget_missing), slot.enabled) { model.toggleWidget(slot.widgetId, it) }
                    }
                }
                SettingsSection.PRIVACY -> {
                    var showPolicy by remember { mutableStateOf(false) }
                    Choice(stringResource(R.string.offline), stringResource(R.string.offline_body), !c.onlinePreference) { model.configure { it.copy(onlinePreference = false) } }
                    Choice(stringResource(R.string.online), stringResource(R.string.online_body), c.onlinePreference) { model.configure { it.copy(onlinePreference = true) } }
                    MutedText(stringResource(R.string.network_prototype))
                    if (usage.checked && !usage.granted) OutlinedButton(onClick = activity::requestUsage) { Text(stringResource(R.string.grant_usage)) }
                    OutlinedButton(onClick = { showPolicy = true }) { Text(stringResource(R.string.privacy_policy)) }
                    if (showPolicy) AlertDialog(
                        onDismissRequest = { showPolicy = false },
                        title = { Text(stringResource(R.string.privacy_policy)) },
                        text = { Text(stringResource(R.string.privacy_policy_summary)) },
                        confirmButton = { TextButton(onClick = { showPolicy = false }) { Text(stringResource(R.string.close)) } },
                    )
                }
                SettingsSection.USAGE -> {
                    val apps by model.apps.collectAsStateWithLifecycle()
                    ScreenTimeSettings(state, apps, usage, model, activity)
                }
                SettingsSection.PRESETS -> PresetControls(state, model)
                else -> Unit
            }
        }
    }
}

@Composable private fun IconSettings(c: LauncherConfig, model: LauncherViewModel) {
    val packs by model.iconPacks.collectAsStateWithLifecycle()
    SettingSwitch(stringResource(R.string.monochrome_icons), c.monochromeIcons) { v -> model.configure { it.copy(monochromeIcons = v, blackWhiteIcons = if (v) false else it.blackWhiteIcons) } }
    SettingSwitch(stringResource(R.string.black_white_icons), c.blackWhiteIcons) { v -> model.configure { it.copy(blackWhiteIcons = v, monochromeIcons = if (v) false else it.monochromeIcons) } }
    SectionTitle(stringResource(R.string.icon_pack))
    MutedText(stringResource(R.string.icon_pack_hint))
    Choice(stringResource(R.string.system_icons), stringResource(R.string.system_icons_hint), c.iconPack.isEmpty()) { model.configure { it.copy(iconPack = "") } }
    if (android.os.Build.VERSION.SDK_INT >= 33) SettingSwitch(stringResource(R.string.themed_icons), c.themedIcons) { v -> model.configure { it.copy(themedIcons = v) } }
    packs.forEach { pack ->
        if (pack.supported) Choice(pack.label, stringResource(R.string.installed_icon_pack), c.iconPack == pack.packageName) { model.configure { it.copy(iconPack = pack.packageName) } }
        else { SectionTitle(pack.label); MutedText(stringResource(R.string.nothing_icon_explanation)) }
    }
    if (packs.isEmpty()) MutedText(stringResource(R.string.no_icon_packs))
    if (c.iconPack.isNotBlank() && packs.none { it.packageName == c.iconPack }) MutedText(stringResource(R.string.icon_pack_missing))
}
