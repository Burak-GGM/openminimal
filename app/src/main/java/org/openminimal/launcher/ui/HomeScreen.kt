@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package org.openminimal.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.MainActivity
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import java.time.LocalDateTime

@Composable
internal fun HomeScreen(state: StoredState, apps: List<InstalledApp>, usage: UsageSnapshot, model: LauncherViewModel, activity: MainActivity, editing: Boolean, setEditing: (Boolean) -> Unit, openDrawer: () -> Unit, openSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) { lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { now = LocalDateTime.now(); delay(60_000 - System.currentTimeMillis() % 60_000) } } }
    val epochDay = java.time.LocalDate.now().toEpochDay()
    val availableApps = apps.filterNot { app ->
        usage.granted && appHiddenByLimit(app.packageName, state.focusRules, state.dailyLimits, state.limitOverrides, usage.durations, epochDay)
    }
    val favorites = if (state.favorites.isEmpty()) availableApps.take(5) else state.favorites.mapNotNull { id -> availableApps.firstOrNull { it.id == id } }
    val feedback = rememberPressFeedback(state.config.hapticFeedback)
    BackHandler(editing) { setEditing(false) }
    val favoriteList = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val freeGrid = state.config.iconsOnly && (!state.config.groupFavorites || editing)
    val homeIds = homeEntryIds(state.favorites.ifEmpty { favorites.map { it.id } }, state.config.homeFolders)
    val folderById = state.config.homeFolders.associateBy { it.id }
    val density = LocalDensity.current
    val layout = state.config.home.apps
    var gridHeight by remember { mutableFloatStateOf(500f) }
    var gridWidth by remember { mutableFloatStateOf(320f) }
    var headerHeight by remember { mutableFloatStateOf(200f) }
    var footerHeight by remember { mutableFloatStateOf(120f) }
    val rowHeight = appEntryHeight(layout, density.fontScale)
    val gap = if (layout.isGrid) 6f else layout.list.rowSpacing.toFloat()
    val columns = if (layout.isGrid) fittedGridColumns(layout.grid.columns, gridWidth) else 1
    val headingHeight = 28f + 20f * density.fontScale
    val groupOf: ((String) -> String)? = if (!state.config.groupFavorites || editing) null else { id ->
        if (id in folderById) "folders" else favorites.firstOrNull { it.id == id }?.let { (state.config.appGroups[id] ?: it.group).name } ?: "new"
    }
    val availableHeight = (gridHeight - headerHeight - footerHeight - (if (editing) 28f else 60f) - gap * 2).coerceAtLeast(0f)
    val maximum = if (layout.isGrid) columns * 10 else layout.list.maxItems
    val candidates = homeIds + List(maximum) { "prospective:$it" }
    val capacity = fitHomeEntries(candidates, availableHeight, columns, rowHeight, gap, maximum, groupOf, headingHeight).size
    val visibleHomeIds = homeIds.take(capacity)
    val keys = if (freeGrid) favoriteSlotKeys(visibleHomeIds, state.config.favoriteSlots, columns, editing, capacity / columns * columns) else visibleHomeIds
    SideEffect { model.homeMeasured(if (freeGrid) capacity / columns * columns else capacity) }
    val usedHeight = homeEntriesHeight(keys, columns, rowHeight, gap, groupOf, headingHeight)
    val listOffset = if (layout.isGrid || editing) 0f else when(layout.list.placement) {
        VerticalPlacement.TOP -> 0f; VerticalPlacement.CENTER -> (availableHeight - usedHeight) / 2
        VerticalPlacement.BOTTOM -> availableHeight - usedHeight
    }.coerceAtLeast(0f)
    val drag = rememberFavoriteDrag(keys, favoriteList, freeGrid, favorites.map { it.id } + folderById.keys)
    val commitLayout: (List<String>) -> Unit = { if (freeGrid) model.saveFavoriteLayout(it) else model.reorderFavorites(it) }
    val appById = favorites.associateBy { it.id }
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var folderOptions by rememberSaveable { mutableStateOf<String?>(null) }
    var editFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var handledHome by rememberSaveable { mutableIntStateOf(activity.homeRequest.intValue) }
    LaunchedEffect(activity.homeRequest.intValue) {
        if (handledHome != activity.homeRequest.intValue) {
            openFolder = null; folderOptions = null; editFolder = null
            handledHome = activity.homeRequest.intValue
        }
    }
    val newFolderName = stringResource(R.string.new_folder)
    val folderDropReady = stringResource(R.string.folder_drop_ready)
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    var shortcut by remember { mutableStateOf(false) }
    val shortcutLabel = stringResource(R.string.reveal_settings)
    Box(Modifier.testTag("home_surface").fillMaxSize()
        .swipeUpToApps(state.config.swipeUpApps && !editing, openDrawer)
        .pointerInput(editing, state.config.hapticFeedback) {
            if (!editing) detectTapGestures(onLongPress = { feedback(); shortcut = true })
        }
        .semantics { if (!editing) onLongClick(shortcutLabel) { feedback(); shortcut = true; true } }
    ) {
    HomeWallpaper(state.config)
    Column {
    if (editing) LayoutEditorBar { setEditing(false) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = AppGridCells(if (state.config.iconsOnly) state.config.gridColumns else 1),
        modifier = Modifier.fillMaxSize().testTag("home_favorites").semantics { stateDescription = capacity.toString() }.padding(horizontal = 30.dp)
            .onSizeChanged { gridHeight = it.height / density.density; gridWidth = it.width / density.density }
            .editFavoriteLayout(editing, drag, favoriteList, commitLayout, feedback) { source, target -> model.mergeIntoFolder(source, target, newFolderName, favorites.map { it.id }) },
        state = favoriteList, userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(gap.dp),
        contentPadding = PaddingValues(top = if (editing) 12.dp else 44.dp, bottom = 16.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column {
                HomeClock(state.config, now, Modifier.onSizeChanged { headerHeight = it.height / density.density }, openClock = if (editing) null else activity::openClock)
                Spacer(Modifier.height(listOffset.dp))
            }
        }
        val groups = if (state.config.groupFavorites && !editing) {
            listOf(R.string.home_folders to drag.order.filter { it in folderById }) + AppGroup.entries.map { group ->
                group.title() to drag.order.filter { id -> appById[id]?.let { (state.config.appGroups[id] ?: it.group) == group } == true }
            }
        } else listOf(null to drag.order)
        groups.forEach { (title, memberKeys) ->
            if (title != null && memberKeys.isNotEmpty()) item(key = "home_group:$title", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(stringResource(title), Modifier.height(headingHeight.dp).padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
            }
            items(memberKeys.size, key = { memberKeys[it] }) { itemIndex ->
                val id = memberKeys[itemIndex]
                val app = appById[id]
                val folder = folderById[id]
                if (app == null && folder == null) {
                    Box(Modifier.height(rowHeight.dp)) { EmptyLayoutCell(editing, itemIndex) }
                    return@items
                }
                val index = drag.order.indexOf(id)
                val itemModifier = Modifier.height(rowHeight.dp)
                    .then(if (drag.folderTarget == id) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (state.config.pressAnimation && drag.dragging != id) tween(180) else null)
                    .zIndex(if (drag.dragging == id) 1f else 0f)
                    .graphicsLayer {
                        val offset = drag.offset(id, favoriteList)
                        translationX = offset.x; translationY = offset.y
                        alpha = if (drag.dragging == id) 0.75f else 1f
                    }.semantics {
                        if (drag.folderTarget == id) stateDescription = folderDropReady
                        customActions = if (!editing) emptyList() else buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUp) { commitLayout(drag.order.toMutableList().apply { add(index - 1, removeAt(index)) }); true })
                            if (index < drag.order.lastIndex) add(CustomAccessibilityAction(moveDown) { commitLayout(drag.order.toMutableList().apply { add(index + 1, removeAt(index)) }); true })
                        }
                    }
                Box(itemModifier, contentAlignment = when(layout.list.alignment) { HorizontalPlacement.START -> Alignment.CenterStart; HorizontalPlacement.CENTER -> Alignment.Center; HorizontalPlacement.END -> Alignment.CenterEnd }) {
                val entryModifier = Modifier.fillMaxWidth(if (layout.isGrid) 1f else layout.list.widthPercent / 100f)
                if (folder != null) FolderTile(folder, folder.appIds.mapNotNull { appById[it] }, state.config, editing, entryModifier,
                    open = { openFolder = id }, options = { folderOptions = id })
                else if (app != null) AppRow(app, state, usage, model, activity, large = true, editing = editing,
                    editLayout = { setEditing(true) }, homeCapacity = capacity,
                    modifier = entryModifier.testTag("favorite:$id"))
                }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.onSizeChanged { footerHeight = it.height / density.density }) {
            Spacer(Modifier.height(24.dp))
            if (state.config.showAllAppsButton) {
                OutlinedButton(onClick = openDrawer, enabled = !editing, modifier = Modifier.testTag("all_apps_button")) { Icon(Icons.Default.Menu, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.all_apps)) }
                Spacer(Modifier.height(20.dp))
            }
            if (editing) MutedText(stringResource(if (freeGrid) R.string.folder_drag_hint else R.string.layout_edit_hint))
            else if (state.config.showHomeFooter) Text(state.config.homeFooter.ifBlank { stringResource(if (state.favorites.isEmpty()) R.string.favorites_hint else R.string.home_footer) }, Modifier.testTag("home_footer"), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
        openFolder?.let { id -> folderById[id]?.let { folder ->
            FolderSheet(folder, folder.appIds.mapNotNull { appById[it] }, state, usage, model, activity,
                close = { openFolder = null }, edit = { openFolder = null; editFolder = id })
        } }
        folderOptions?.let { id -> folderById[id]?.let { folder ->
            ActionSheet(folder.name, { folderOptions = null }) {
                TextButton(onClick = { folderOptions = null; editFolder = id }) { Text(stringResource(R.string.edit_folder)) }
                TextButton(onClick = { folderOptions = null; setEditing(true) }) { Text(stringResource(R.string.edit_layout)) }
                TextButton(onClick = { folderOptions = null; model.removeFolder(id) }) { Text(stringResource(R.string.remove_folder)) }
                MutedText(stringResource(R.string.remove_folder_hint))
            }
        } }
        editFolder?.let { id -> folderById[id]?.let { FolderEditor(model, it, close = { editFolder = null }) } }
        if (shortcut) ActionSheet(stringResource(R.string.home), { shortcut = false }, Modifier.testTag("home_actions")) {
            ListItem(headlineContent = { Text(stringResource(R.string.all_apps)) }, leadingContent = { Icon(Icons.Default.Menu, null) },
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { shortcut = false; openDrawer() })
            ListItem(headlineContent = { Text(stringResource(R.string.settings)) }, leadingContent = { Icon(Icons.Default.Settings, null) },
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { shortcut = false; openSettings() })
        }
    }

}
