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
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.MainActivity
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import java.time.LocalDateTime

@Composable
internal fun AppRow(
    app: InstalledApp,
    state: StoredState,
    usage: UsageSnapshot,
    model: LauncherViewModel,
    activity: MainActivity,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    editing: Boolean = false,
    editLayout: (() -> Unit)? = null,
    homeCapacity: Int? = null,
    layout: AppLayoutConfig = if (large) state.config.home.apps else state.config.drawer.apps,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var limitEditor by remember { mutableStateOf(false) }
    var folderPicker by remember { mutableStateOf(false) }
    val feedback = rememberPressFeedback(state.config.hapticFeedback)
    val interaction = remember { MutableInteractionSource() }
    val favorite = app.id in state.favorites
    val measuredHomeCapacity by model.homeCapacity.collectAsStateWithLifecycle()
    val favoriteCapacity = homeCapacity ?: measuredHomeCapacity ?: homeFavoriteCapacity(state.config, (LocalWindowInfo.current.containerSize.height / LocalDensity.current.density).toInt())
    val favoriteCapacityReached = !favorite && homeEntryIds(state.favorites, state.config.homeFolders).size >= favoriteCapacity
    Box(modifier) {
        val optionsLabel = stringResource(R.string.app_options)
        val click = if (editing) Modifier else Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            hapticFeedbackEnabled = false,
            onClick = { activity.openAppGuarded(app.id, app.packageName, app.label, state, usage) },
            onClickLabel = stringResource(R.string.open_app, app.label),
            onLongClick = { feedback(); focusManager.clearFocus(); menu = true },
            onLongClickLabel = optionsLabel,
        )
        val tile = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(click).pressScale(interaction, state.config.pressAnimation).layoutEditIndication(editing, state.config.pressAnimation, app.id)
        AppEntryContent(app, state.config, layout, tile, favorite = favorite && !large)
        if (menu) AppActionSheet(app, activity, favorite, state.config,
            close = { menu = false },
            editLayout = editLayout?.let { edit -> { menu = false; edit() } },
            toggleFavorite = { model.favorite(app.id, favoriteCapacity); menu = false },
            favoriteCapacityReached = favoriteCapacityReached,
            chooseGroup = { menu = false; grouping = true },
            editLimit = { menu = false; limitEditor = true },
            createFolder = { menu = false; folderPicker = true },
            openShortcut = { shortcut -> activity.openShortcutGuarded(shortcut, app.label, state, usage) },
            removeFromFolder = state.config.homeFolders.firstOrNull { app.id in it.appIds }?.let { folder -> {
                model.removeFromFolder(folder.id, app.id); menu = false
            } },
        )
    }
    if (folderPicker) ActionSheet(stringResource(R.string.add_to_folder), { folderPicker = false }) {
        val defaultName = stringResource(R.string.new_folder)
        TextButton(enabled = !favoriteCapacityReached, onClick = { model.saveFolder(HomeFolder(FOLDER_PREFIX + java.util.UUID.randomUUID(), defaultName, listOf(app.id))); folderPicker = false }) { Text(stringResource(R.string.create_folder)) }
        state.config.homeFolders.forEach { folder ->
            TextButton(onClick = { model.saveFolder(folder.copy(appIds = (folder.appIds + app.id).distinct())); folderPicker = false }) { Text(folder.name) }
        }
        if (favoriteCapacityReached) MutedText(stringResource(R.string.home_favorites_full))
    }
    if (grouping) AlertDialog(onDismissRequest = { grouping = false }, title = { Text(stringResource(R.string.assign_group)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick = { model.configure { it.copy(appGroups = it.appGroups - app.id) }; grouping = false }) { Text(stringResource(R.string.automatic)) }
            AppGroup.entries.forEach { group -> TextButton(onClick = { model.configure { it.copy(appGroups = it.appGroups + (app.id to group)) }; grouping = false }) { Text(stringResource(group.title())) } }
        }
    }, confirmButton = { TextButton(onClick = { grouping = false }) { Text(stringResource(R.string.close)) } })
    if (limitEditor) FocusRuleEditor(
        appName = app.label,
        currentMinutes = state.dailyLimits[app.packageName],
        currentRule = state.focusRules[app.packageName],
        usageGranted = usage.granted && !usage.unavailable,
        close = { limitEditor = false },
        save = { minutes, rule -> model.setFocusPolicy(app.packageName, minutes, rule); limitEditor = false },
    )
}
