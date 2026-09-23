package org.openminimal.launcher.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openminimal.launcher.MainActivity
import org.openminimal.launcher.R
import org.openminimal.launcher.model.LauncherConfig
import org.openminimal.launcher.platform.*

/** Compact, scrollable launcher actions with explicit system-owned operations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppActionSheet(
    app: InstalledApp, activity: MainActivity, favorite: Boolean, config: LauncherConfig,
    close: () -> Unit, editLayout: (() -> Unit)?, toggleFavorite: () -> Unit,
    favoriteCapacityReached: Boolean,
    chooseGroup: () -> Unit, editLimit: () -> Unit, createFolder: () -> Unit,
    openShortcut: (android.content.pm.ShortcutInfo) -> Unit = activity::openShortcut,
    removeFromFolder: (() -> Unit)? = null,
) {
    var expanded by remember(app.id) { mutableStateOf(false) }
    val appIcon = rememberStyledIcon(app.icon, config.blackWhiteIcons)
    val filter = grayscaleFilter(config.monochromeIcons && !config.blackWhiteIcons)
    val shortcuts by produceState<AppShortcuts?>(null, app.id, activity.isDefaultHome.value) {
        value = withContext(Dispatchers.IO) { readAppShortcuts(activity, app) }
    }
    val removable = remember(app.packageName) {
        runCatching {
            @Suppress("DEPRECATION")
            activity.packageManager.getApplicationInfo(app.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
        }.getOrDefault(false)
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.testTag("app_actions").fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(appIcon.asImageBitmap(), null, Modifier.size(36.dp), colorFilter = filter)
                Text(app.label, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { close(); activity.openAppInfo(app.packageName) }) { Icon(Icons.Default.Info, stringResource(R.string.app_info)) }
                if (removable) IconButton(onClick = { close(); activity.requestUninstall(app.packageName) }) { Icon(Icons.Default.Delete, stringResource(R.string.uninstall_app), tint = MaterialTheme.colorScheme.error) }
                IconButton(onClick = close) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            Spacer(Modifier.height(4.dp))
            editLayout?.let { ActionRow(stringResource(R.string.edit_layout), Icons.Default.Edit, action = it) }
            ActionRow(
                stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                Icons.Default.Star,
                enabled = !favoriteCapacityReached,
                action = toggleFavorite,
            )
            if (favoriteCapacityReached) MutedText(stringResource(R.string.home_favorites_full))
            ActionRow(stringResource(R.string.add_to_folder), Icons.Default.Add, action = createFolder)
            removeFromFolder?.let { ActionRow(stringResource(R.string.remove_from_folder), Icons.Default.Home, action = it) }
            ActionRow(stringResource(R.string.assign_group), Icons.Default.Menu, action = chooseGroup)
            ActionRow(stringResource(R.string.focus_gate_and_budget), Icons.Default.DateRange, action = editLimit)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.app_shortcuts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val result = shortcuts
            when {
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                !result.access -> ActionRow(stringResource(R.string.shortcuts_need_default), Icons.Default.Home) { close(); activity.requestHome() }
                result.unavailable -> MutedText(stringResource(R.string.action_unavailable))
                result.items.isEmpty() -> MutedText(stringResource(R.string.no_app_shortcuts))
                else -> {
                    val visible = if (expanded) result.items else result.items.take(4)
                    visible.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { shortcut ->
                                val icon = rememberStyledIcon(shortcut.icon ?: app.icon, config.blackWhiteIcons)
                                Surface(Modifier.weight(1f).testTag("shortcut:${shortcut.info.id}"), shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.clickable { close(); openShortcut(shortcut.info) }
                                        .heightIn(min = 86.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Image(icon.asImageBitmap(), null, Modifier.size(28.dp), colorFilter = filter)
                                        Text(shortcut.info.shortLabel?.toString().orEmpty(), style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (result.items.size > 4) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (expanded) R.string.show_fewer_shortcuts else R.string.show_all_shortcuts, result.items.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = action).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyMedium, color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
