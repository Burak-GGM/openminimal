@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.openminimal.launcher.ui

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openminimal.launcher.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*

@Composable internal fun FolderTile(folder: HomeFolder, members: List<InstalledApp>, config: LauncherConfig,
    editing: Boolean, modifier: Modifier = Modifier, open: () -> Unit, options: () -> Unit) {
    val feedback = rememberPressFeedback(config.hapticFeedback)
    val count = pluralStringResource(R.plurals.folder_app_count, members.size, members.size)
    val tile = modifier.fillMaxWidth().testTag(folder.id).clip(MaterialTheme.shapes.medium)
        .combinedClickable(enabled = !editing, onClick = open, onLongClick = { feedback(); options() }, hapticFeedbackEnabled = false)
        .semantics { contentDescription = folder.name; stateDescription = count }
        .layoutEditIndication(editing, config.pressAnimation, folder.id)
    if (config.iconsOnly) Column(tile.heightIn(min = 76.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FolderPreview(members, config, config.home.apps.grid.iconSize)
        Text(folder.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else Row(tile.heightIn(min = 56.dp).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (config.icons) { FolderPreview(members, config, config.home.apps.list.iconSize); Spacer(Modifier.width(config.home.apps.list.iconGap.dp)) }
        Text(folder.name, Modifier.weight(1f), fontSize = config.home.apps.list.textSize.sp, fontWeight = FontWeight(config.home.apps.list.weight.value), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(members.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(start = 8.dp).size(18.dp))
    }
}

@Composable private fun FolderPreview(apps: List<InstalledApp>, config: LauncherConfig, size: Int) {
    Surface(Modifier.size(size.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            apps.take(4).chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                pair.forEach { app -> Image(rememberStyledIcon(app.icon, config.blackWhiteIcons).asImageBitmap(), null, Modifier.size(((size - 13) / 2f).dp), colorFilter = grayscaleFilter(config.monochromeIcons && !config.blackWhiteIcons)) }
            } }
        }
    }
}

@Composable internal fun FolderSheet(folder: HomeFolder, members: List<InstalledApp>, state: StoredState, usage: UsageSnapshot,
    model: LauncherViewModel, activity: MainActivity, close: () -> Unit, edit: () -> Unit) {
    if (state.config.iconsOnly) {
        val visible = remember(folder.id) { MutableTransitionState(false).apply { targetState = true } }
        val duration = if (state.config.drawerAnimation) 180 else 0
        val dismiss = { visible.targetState = false }
        LaunchedEffect(visible.currentState, visible.isIdle, visible.targetState) {
            if (visible.isIdle && !visible.currentState && !visible.targetState) close()
        }
        Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                AnimatedVisibility(visibleState = visible,
                    enter = fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = .9f),
                    exit = fadeOut(tween(duration)) + scaleOut(tween(duration), targetScale = .94f)) {
                    Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("folder_dialog"),
                        shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp, shadowElevation = 12.dp) {
                        FolderContents(folder, members, state, usage, model, activity, dismiss, edit)
                    }
                }
            }
        }
    } else ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FolderContents(folder, members, state, usage, model, activity, close, edit)
    }
}

@Composable private fun FolderContents(folder: HomeFolder, members: List<InstalledApp>, state: StoredState, usage: UsageSnapshot,
    model: LauncherViewModel, activity: MainActivity, close: () -> Unit, edit: () -> Unit) {
    Column(Modifier.testTag("folder_sheet").fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(folder.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = edit) { Icon(Icons.Default.Edit, stringResource(R.string.edit_folder)) }
            IconButton(onClick = close) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
        if (members.isEmpty()) MutedText(stringResource(R.string.folder_unavailable))
        LazyVerticalGrid(columns = AppGridCells(if (state.config.iconsOnly) state.config.gridColumns else 1), modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(members, key = { it.id }) { app -> AppRow(app, state, usage, model, activity, layout = state.config.home.apps.copy(grid = state.config.home.apps.grid.copy(showLabels = true))) }
        }
    }
}

/** One explicit editor works for icon and text layouts, and never deletes an application. */
@Composable internal fun FolderEditor(model: LauncherViewModel, folder: HomeFolder, close: () -> Unit) {
    val apps by model.apps.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(folder.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(folder.appIds) }
    var ordering by rememberSaveable { mutableStateOf(false) }
    val id = rememberSaveable { folder.id }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.edit_folder)) }, text = {
        Column(Modifier.testTag("folder_editor"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, Modifier.testTag("folder_name"), label = { Text(stringResource(R.string.folder_name)) }, singleLine = true)
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_apps)) }, singleLine = true)
            Text(pluralStringResource(R.plurals.folder_selected_count, selected.size, selected.size), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { ordering = !ordering }, modifier = Modifier.testTag("folder_order_toggle")) { Text(stringResource(if (ordering) R.string.folder_select_apps else R.string.folder_order)) }
            if (ordering) FolderOrderList(selected, apps, model) { selected = it }
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                if (filtered.isEmpty()) item { MutedText(stringResource(R.string.no_apps)) }
                items(filtered, key = { it.id }) { app ->
                    Row(Modifier.fillMaxWidth().testTag("folder_pick:${app.id}").toggleable(value = app.id in selected, role = Role.Checkbox) {
                        selected = if (it) selected + app.id else selected - app.id
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(app.id in selected, null)
                        Text(app.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            MutedText(stringResource(R.string.folder_members_hint))
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && selected.isNotEmpty(), onClick = { model.saveFolder(HomeFolder(id, name.trim(), selected)); close() }, modifier = Modifier.testTag("save_folder")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun FolderOrderList(selected: List<String>, apps: List<InstalledApp>, model: LauncherViewModel, change: (List<String>) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val drag = rememberFavoriteDrag(selected, grid)
    val feedback = rememberPressFeedback(state?.config?.hapticFeedback == true)
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val move: (Int, Int) -> Unit = { from, to -> change(drag.order.toMutableList().apply { add(to, removeAt(from)) }) }
    MutedText(stringResource(R.string.folder_order_hint))
    LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(1), state = grid,
        modifier = Modifier.fillMaxWidth().height(220.dp).testTag("folder_order_list").editFavoriteLayout(true, drag, grid, change, feedback)) {
        items(drag.order, key = { it }) { id ->
            val index = drag.order.indexOf(id)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("folder_order:$id")
                .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (state?.config?.pressAnimation == true) tween(150) else null)
                .zIndex(if (drag.dragging == id) 1f else 0f)
                .graphicsLayer { translationY = drag.offset(id, grid).y }
                .semantics { customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index, index - 1); true })
                    if (index < drag.order.lastIndex) add(CustomAccessibilityAction(moveDown) { move(index, index + 1); true })
                } }, verticalAlignment = Alignment.CenterVertically) {
                Text(apps.firstOrNull { it.id == id }?.label ?: stringResource(R.string.action_unavailable), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, moveUp) }
                IconButton(onClick = { move(index, index + 1) }, enabled = index < drag.order.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, moveDown) }
            }
        }
    }
}
