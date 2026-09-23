package org.openminimal.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openminimal.launcher.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.InstalledApp
import org.openminimal.launcher.platform.UsageSnapshot
import java.text.Collator

internal fun AppGroup.title() = when(this) {
    AppGroup.GAMES -> R.string.group_games; AppGroup.HEALTH -> R.string.group_health
    AppGroup.FINANCE -> R.string.group_finance; AppGroup.PRODUCTIVITY -> R.string.group_productivity
    AppGroup.SOCIAL -> R.string.group_social; AppGroup.MEDIA -> R.string.group_media
    AppGroup.MAPS -> R.string.group_maps; AppGroup.NEWS -> R.string.group_news; AppGroup.OTHER -> R.string.group_other
}
private data class DrawerEntry(val key: String, val app: InstalledApp? = null, val group: AppGroup? = null)

@Composable
internal fun AppDrawer(state: StoredState, apps: List<InstalledApp>, usage: UsageSnapshot, model: LauncherViewModel, activity: MainActivity, openUsage: () -> Unit, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val epochDay = java.time.LocalDate.now().toEpochDay()
    val hidden = remember(apps, usage, state.focusRules, state.dailyLimits, state.limitOverrides, epochDay) {
        if (!usage.granted) emptyList() else apps.filter { app ->
            appHiddenByLimit(app.packageName, state.focusRules, state.dailyLimits, state.limitOverrides, usage.durations, epochDay)
        }
    }
    val available = remember(apps, hidden) {
        val hiddenIds = hidden.mapTo(mutableSetOf()) { it.id }
        apps.filterNot { it.id in hiddenIds }
    }
    val filtered = remember(available, query, locale) { available.filter { it.label.lowercase(locale).contains(query.trim().lowercase(locale)) }.sortedWith(compareBy(Collator.getInstance(locale)) { it.label }) }
    val allEntries = remember(filtered, state.config.groupApps, state.config.appGroups) {
        if (!state.config.groupApps) filtered.map { DrawerEntry(it.id, app = it) }
        else AppGroup.entries.flatMap { group ->
            val members = filtered.filter { (state.config.appGroups[it.id] ?: it.group) == group }
            if (members.isEmpty()) emptyList() else listOf(DrawerEntry("group:$group", group = group)) + members.map { DrawerEntry(it.id, app = it) }
        }
    }
    val entries = remember(allEntries, state.config.groupApps, state.config.collapsibleGroups, state.config.collapsedGroups, query) {
        allEntries.filter { entry -> entry.app == null || !state.config.groupApps || query.isNotBlank() || !state.config.collapsibleGroups || (state.config.appGroups[entry.app.id] ?: entry.app.group) !in state.config.collapsedGroups }
    }
    val letters = remember(allEntries, locale) {
        allEntries.mapIndexedNotNull { index, entry -> entry.app?.label?.trim()?.firstOrNull()?.let {
            (if (it.isLetter()) it.toString().uppercase(locale) else "#") to index
        } }.groupBy({ it.first }, { it.second }).mapValues { it.value.first() }.toSortedMap(Collator.getInstance(locale))
    }
    val list = rememberLazyGridState()
    val favoriteGroups = remember(apps, state.favorites, state.config.appGroups, state.config.homeFolders, state.config.groupFavorites) {
        if (!state.config.groupFavorites) 0 else {
            val groups = state.favorites.mapNotNull { id -> apps.firstOrNull { it.id == id } }
                .map { state.config.appGroups[it.id] ?: it.group }.distinct().size
            groups + if (state.config.homeFolders.isNotEmpty()) 1 else 0
        }
    }
    val measuredHomeCapacity by model.homeCapacity.collectAsStateWithLifecycle()
    val drawerHomeCapacity = measuredHomeCapacity ?: homeFavoriteCapacity(
        state.config,
        (LocalWindowInfo.current.containerSize.height / LocalDensity.current.density).toInt(),
        favoriteGroups + if (state.config.groupFavorites) 1 else 0,
    )
    val listDragged by list.interactionSource.collectIsDraggedAsState()
    var followList by remember { mutableStateOf(true) }
    LaunchedEffect(listDragged) { if (listDragged) followList = true }
    var pendingJump by remember { mutableStateOf<String?>(null) }
    var selectedLetter by remember { mutableIntStateOf(0) }
    LaunchedEffect(entries, pendingJump) {
        val target = pendingJump ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.key == target }
        if (index >= 0) { list.scrollToItem(index); pendingJump = null }
    }
    LaunchedEffect(list, entries, letters, locale) {
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.isScrollInProgress, followList) }
            .distinctUntilChanged()
            .collect { (firstVisibleItemIndex, scrolling, follow) ->
                if (scrolling && follow) {
                    val app = entries.drop(firstVisibleItemIndex).firstNotNullOfOrNull { it.app }
                    val initial = app?.label?.trim()?.firstOrNull()?.toString()?.uppercase(locale)
                    val index = letters.keys.indexOf(initial)
                    if (index >= 0) selectedLetter = index
                }
            }
    }
    LaunchedEffect(query, state.config.groupApps) { list.scrollToItem(0) }
    Column(Modifier.testTag("app_drawer").fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp)
        ) {
        Box(Modifier.fillMaxWidth().swipeDownToHome(state.config.swipeUpApps, { true }, back)) { Header(stringResource(R.string.all_apps), back) }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.search_apps)) }, shape = MaterialTheme.shapes.large)
        AnimatedVisibility(hidden.isNotEmpty()) {
            AssistChip(
                onClick = openUsage,
                label = { Text(pluralStringResource(R.plurals.apps_hidden_by_limits, hidden.size, hidden.size)) },
                leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            LazyVerticalGrid(columns = AppGridCells(if (state.config.drawer.apps.isGrid) state.config.drawer.apps.grid.columns else 1), modifier = Modifier.weight(1f).fillMaxHeight().testTag("apps_list").swipeDownToHome(state.config.swipeUpApps, { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }, back), state = list,
                verticalArrangement = Arrangement.spacedBy((if (state.config.drawer.apps.isGrid) 6 else state.config.drawer.apps.list.rowSpacing).dp)) {
                if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { MutedText(stringResource(R.string.no_apps)) }
                itemsIndexed(entries, span = { _, entry -> GridItemSpan(if (entry.app == null) maxLineSpan else 1) }, key = { _, entry -> entry.key }, contentType = { _, entry -> if (entry.app == null) "heading" else "app" }) { _, entry ->
                    if (entry.app != null) AppRow(entry.app, state, usage, model, activity, homeCapacity = drawerHomeCapacity)
                    else {
                        val group = entry.group!!
                        val collapsed = state.config.collapsibleGroups && group in state.config.collapsedGroups && query.isBlank()
                        val status = stringResource(if (collapsed) R.string.group_collapsed else R.string.group_expanded)
                        Row(Modifier.fillMaxWidth().testTag("group:$group").semantics { stateDescription = status }
                            .clickable(enabled = state.config.collapsibleGroups && query.isBlank()) { model.configure { it.copy(collapsedGroups = if (collapsed) it.collapsedGroups - group else it.collapsedGroups + group) } }
                            .padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(group.title()), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (state.config.collapsibleGroups) Icon(if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (state.config.showAlphabet && !state.config.groupApps && letters.isNotEmpty()) {
                val labels = letters.keys.toList()
                AlphabetRail(labels, selectedLetter.coerceIn(labels.indices), state.config.compactAlphabet, state.config.alphabetAnimation) { index ->
                    followList = false
                    selectedLetter = index
                    val app = allEntries[letters.getValue(labels[index])].app!!
                    pendingJump = app.id
                }
            }
        }
    }
}
