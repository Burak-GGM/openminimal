package org.openminimal.launcher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*

@Composable internal fun <T> EnumChips(selected: T, values: List<T>, label: (T) -> Int, change: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { change(value) }, label = { Text(stringResource(label(value))) }) }
    }
}

@Composable internal fun IntSetting(@StringRes title: Int, value: Int, range: IntRange, tag: String,
    update: (Int) -> Unit, commit: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value.toFloat(), { update(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChangeFinished = commit, modifier = Modifier.testTag(tag))
    }
}

private fun weightLabel(value: TextWeight) = when(value) {
    TextWeight.LIGHT -> R.string.weight_light; TextWeight.REGULAR -> R.string.weight_regular
    TextWeight.MEDIUM -> R.string.weight_medium; TextWeight.BOLD -> R.string.weight_bold
}

@Composable internal fun LayoutPersonalization(config: LauncherConfig, model: LauncherViewModel, home: Boolean) {
    val persisted = if (home) config.home.apps else config.drawer.apps
    var draft by remember(persisted) { mutableStateOf(persisted) }
    val apps by model.apps.collectAsStateWithLifecycle()
    val preview = if (home) config.copy(home = config.home.copy(apps = draft)) else config.copy(drawer = config.drawer.copy(apps = draft))
    val commit: () -> Unit = { val value = draft; model.configure {
        if (home) it.copy(home = it.home.copy(apps = value)) else it.copy(drawer = it.drawer.copy(apps = value))
    }; Unit }
    Column(Modifier.fillMaxSize()) {
        PersonalizationPreview(preview, apps, home)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!draft.isGrid, { draft = draft.copy(display = AppDisplay.TEXT_ICONS); commit() }, label = { Text(stringResource(R.string.list_mode)) }, modifier = Modifier.testTag("layout_list"))
                FilterChip(draft.isGrid, { draft = draft.copy(display = AppDisplay.ICONS); commit() }, label = { Text(stringResource(R.string.grid_mode)) }, modifier = Modifier.testTag("layout_grid"))
            }
            if (draft.isGrid) {
                IntSetting(R.string.grid_columns, draft.grid.columns, 3..6, "grid_columns", { draft = draft.copy(grid = draft.grid.copy(columns = it)) }, commit)
                IntSetting(R.string.icon_size, draft.grid.iconSize, 32..64, "grid_icon_size", { draft = draft.copy(grid = draft.grid.copy(iconSize = it)) }, commit)
                SettingSwitch(stringResource(R.string.show_grid_labels), draft.grid.showLabels) { draft = draft.copy(grid = draft.grid.copy(showLabels = it)); commit() }
            } else {
                SettingSwitch(stringResource(R.string.show_list_icons), draft.showIcons) { draft = draft.copy(display = if (it) AppDisplay.TEXT_ICONS else AppDisplay.TEXT); commit() }
                IntSetting(R.string.app_text_size, draft.list.textSize, 14..32, "list_text_size", { draft = draft.copy(list = draft.list.copy(textSize = it)) }, commit)
                SectionTitle(stringResource(R.string.font_weight))
                EnumChips(draft.list.weight, TextWeight.entries, ::weightLabel) { draft = draft.copy(list = draft.list.copy(weight = it)); commit() }
                if (draft.showIcons) {
                    IntSetting(R.string.icon_size, draft.list.iconSize, 24..56, "list_icon_size", { draft = draft.copy(list = draft.list.copy(iconSize = it)) }, commit)
                    IntSetting(R.string.icon_text_gap, draft.list.iconGap, 4..32, "list_icon_gap", { draft = draft.copy(list = draft.list.copy(iconGap = it)) }, commit)
                }
                IntSetting(R.string.row_spacing, draft.list.rowSpacing, 0..24, "list_row_spacing", { draft = draft.copy(list = draft.list.copy(rowSpacing = it)) }, commit)
                if (home) {
                    IntSetting(R.string.list_width, draft.list.widthPercent, 60..100, "list_width", { draft = draft.copy(list = draft.list.copy(widthPercent = it)) }, commit)
                    SectionTitle(stringResource(R.string.list_alignment))
                    EnumChips(draft.list.alignment, HorizontalPlacement.entries, { when(it) { HorizontalPlacement.START -> R.string.align_start; HorizontalPlacement.CENTER -> R.string.align_center; HorizontalPlacement.END -> R.string.align_end } }) { draft = draft.copy(list = draft.list.copy(alignment = it)); commit() }
                    SectionTitle(stringResource(R.string.list_placement))
                    EnumChips(draft.list.placement, VerticalPlacement.entries, { when(it) { VerticalPlacement.TOP -> R.string.position_top; VerticalPlacement.CENTER -> R.string.position_center; VerticalPlacement.BOTTOM -> R.string.position_bottom } }) { draft = draft.copy(list = draft.list.copy(placement = it)); commit() }
                    IntSetting(R.string.maximum_items, draft.list.maxItems, 1..20, "list_max_items", { draft = draft.copy(list = draft.list.copy(maxItems = it)) }, commit)
                }
            }
            if (home) {
                MutedText(stringResource(R.string.single_page_hint))
                MutedText(stringResource(R.string.reorder_favorites_hint))
                SettingSwitch(stringResource(R.string.group_favorites), config.home.grouped) { value -> model.configure { it.copy(home = it.home.copy(grouped = value)) } }
            }
        }
    }
}

@Composable internal fun ClockPersonalization(config: LauncherConfig, model: LauncherViewModel) {
    var draft by remember(config.home.clock) { mutableStateOf(config.home.clock) }
    val apps by model.apps.collectAsStateWithLifecycle()
    val commit: () -> Unit = { val value = draft; model.configure { it.copy(home = it.home.copy(clock = value)) }; Unit }
    Column(Modifier.fillMaxSize()) {
        PersonalizationPreview(config.copy(home = config.home.copy(clock = draft)), apps, true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingSwitch(stringResource(R.string.show_clock), draft.visible) { draft = draft.copy(visible = it); commit() }
            if (draft.visible) {
                IntSetting(R.string.clock_appearance, draft.size, 40..112, "clock_size_slider", { draft = draft.copy(size = it) }, commit)
                SectionTitle(stringResource(R.string.clock_font))
                EnumChips(draft.font, ClockFont.entries, { if (it == ClockFont.MONOSPACE) R.string.font_monospace else R.string.font_system }) { draft = draft.copy(font = it); commit() }
                SectionTitle(stringResource(R.string.font_weight))
                EnumChips(draft.weight, TextWeight.entries, ::weightLabel) { draft = draft.copy(weight = it); commit() }
                SectionTitle(stringResource(R.string.time_format))
                EnumChips(draft.format, ClockFormat.entries, { when(it) { ClockFormat.SYSTEM -> R.string.system; ClockFormat.TWELVE_HOUR -> R.string.time_12; ClockFormat.TWENTY_FOUR_HOUR -> R.string.time_24 } }) { draft = draft.copy(format = it); commit() }
            }
            SectionTitle(stringResource(R.string.clock_alignment))
            EnumChips(draft.alignment, ClockAlignment.entries, { if (it == ClockAlignment.CENTER) R.string.align_center else R.string.align_start }) { draft = draft.copy(alignment = it); commit() }
            SettingSwitch(stringResource(R.string.show_date), draft.showDate) { draft = draft.copy(showDate = it); commit() }
            if (draft.showDate) {
                SectionTitle(stringResource(R.string.date_format))
                EnumChips(draft.dateStyle, DateStyle.entries, { when(it) { DateStyle.LONG -> R.string.date_long; DateStyle.SHORT -> R.string.date_short; DateStyle.NUMERIC -> R.string.date_numeric } }) { draft = draft.copy(dateStyle = it); commit() }
                SettingSwitch(stringResource(R.string.show_weekday), draft.showWeekday) { draft = draft.copy(showWeekday = it); commit() }
            }
            IntSetting(R.string.clock_spacing, draft.bottomSpacing, 8..64, "clock_spacing", { draft = draft.copy(bottomSpacing = it) }, commit)
        }
    }
}
