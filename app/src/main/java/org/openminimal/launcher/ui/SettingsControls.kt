package org.openminimal.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*

@Composable
fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ColorControls(config: LauncherConfig, model: LauncherViewModel) {
    Text(stringResource(R.string.colors), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.colors_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPalette.entries.forEach { palette ->
            FilterChip(selected = config.palette == palette,
                onClick = { model.configure { it.copy(palette = palette) } },
                leadingIcon = { Box(Modifier.size(16.dp).background(launcherColorScheme(config.copy(palette = palette), false).primary, CircleShape)) },
                label = { Text(stringResource(when(palette) {
                    ColorPalette.SAGE -> R.string.color_sage
                    ColorPalette.MONOCHROME -> R.string.monochrome
                    ColorPalette.OCEAN -> R.string.color_ocean
                    ColorPalette.SAND -> R.string.color_sand
                    ColorPalette.PLUM -> R.string.color_plum
                })) },
            )
        }
    }
    Text(stringResource(R.string.dark_background), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        DarkBackground.entries.forEach { background ->
            FilterChip(config.darkBackground == background, { model.configure { it.copy(darkBackground = background) } },
                label = { Text(stringResource(if (background == DarkBackground.PITCH_BLACK) R.string.pitch_black else R.string.charcoal)) })
        }
    }
    Text(stringResource(R.string.dark_background_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}


@Composable
fun AppDisplayControls(config: LauncherConfig, model: LauncherViewModel) {
    SectionTitle(stringResource(R.string.app_display))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppDisplay.entries.forEach { display ->
            FilterChip(config.appDisplay == display, { model.configure { it.withAppDisplay(display) } },
                modifier = Modifier.testTag("app_display:$display"),
                label = { Text(stringResource(when (display) {
                    AppDisplay.TEXT -> R.string.display_text
                    AppDisplay.TEXT_ICONS -> R.string.display_text_icons
                    AppDisplay.ICONS -> R.string.display_icons
                })) })
        }
    }
    if (config.iconsOnly) {
        Text(stringResource(R.string.grid_columns), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (3..6).forEach { columns ->
                FilterChip(config.gridColumns == columns, { model.configure { it.copy(home = it.home.copy(apps = it.home.apps.copy(grid = it.home.apps.grid.copy(columns = columns)))) } },
                    label = { Text(columns.toString()) })
            }
        }
    }
}
