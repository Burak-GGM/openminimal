@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.openminimal.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openminimal.launcher.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun OpenMinimalApp(model: LauncherViewModel, activity: MainActivity) {
    val state by model.state.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val usage by model.usage.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val config = state?.config ?: LauncherConfig()
    OpenMinimalTheme(config) {
        val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
        LaunchedEffect(config.onboardingComplete, config.wallpaper, backgroundColor) {
            if (config.onboardingComplete && config.syncWallpaper) activity.applyHomeWallpaper(config, backgroundColor)
        }
        val light = MaterialTheme.colorScheme.background.red > 0.5f
        SideEffect {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val s = state
            when {
                s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.app_name)) }
                !config.onboardingComplete -> Onboarding(config, model)
                else -> Launcher(s, apps, usage, model, activity)
            }
            if (error) AlertDialog(
                onDismissRequest = model::dismissError,
                text = { Text(stringResource(R.string.storage_error)) },
                confirmButton = { TextButton(onClick = model::dismissError) { Text(stringResource(R.string.close)) } },
            )
        }
    }
}

@Composable
private fun Onboarding(config: LauncherConfig, model: LauncherViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            repeat(3) { index -> Box(Modifier.padding(start = 6.dp).size(if (index == step) 9.dp else 6.dp).background(
                if (index == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 44.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(when(step) { 0 -> R.string.welcome_title; 1 -> R.string.choose_preset; else -> R.string.privacy_title }),
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
            MutedText(stringResource(when(step) { 0 -> R.string.welcome_body; 1 -> R.string.preset_hint; else -> R.string.privacy_body }))
            Spacer(Modifier.height(12.dp))
            when(step) {
                0 -> {
                    Choice(stringResource(R.string.setup_simple), stringResource(R.string.setup_simple_body), !config.advanced) { model.configure { it.copy(advanced = false) } }
                    Choice(stringResource(R.string.setup_advanced), stringResource(R.string.setup_advanced_body), config.advanced) { model.configure { it.copy(advanced = true) } }
                    LanguagePicker(config, model)
                }
                1 -> {
                    Preset.entries.forEach { preset ->
                        Choice(stringResource(preset.title()), stringResource(preset.description()), config.preset == preset) { model.configure { it.applyPreset(preset) } }
                    }
                    if (config.advanced) AppearanceControls(config, model)
                }
                2 -> {
                    Choice(stringResource(R.string.offline), stringResource(R.string.offline_body), !config.onlinePreference) { model.configure { it.copy(onlinePreference = false) } }
                    Choice(stringResource(R.string.online), stringResource(R.string.online_body), config.onlinePreference) { model.configure { it.copy(onlinePreference = true) } }
                    MutedText(stringResource(R.string.network_prototype))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step < 2) step++ else model.configure { it.copy(onboardingComplete = true) } },
                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 16.dp)) {
                Text(stringResource(if (step == 2) R.string.start else R.string.continue_label))
            }
        }
    }
}

@Composable
private fun Launcher(state: StoredState, apps: List<InstalledApp>, usage: UsageSnapshot, model: LauncherViewModel, activity: MainActivity) {
    var route by rememberSaveable { mutableStateOf("home") }
    var layoutEditing by rememberSaveable { mutableStateOf(false) }
    val pages = state.config.pages()
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    var handledHomeRequest by rememberSaveable { mutableIntStateOf(activity.homeRequest.intValue) }
    var knownPages by rememberSaveable { mutableStateOf(pages.joinToString("|") { it.id }) }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { model.refreshUsage(); delay(60_000) }
        }
    }
    LaunchedEffect(activity.homeRequest.intValue) {
        if (handledHomeRequest != activity.homeRequest.intValue) {
            handledHomeRequest = activity.homeRequest.intValue
            route = "home"
            layoutEditing = false
            pager.scrollToPage(0)
        }
    }
    LaunchedEffect(pages) {
        val currentPages = pages.joinToString("|") { it.id }
        if (knownPages != currentPages) {
            knownPages = currentPages
            pager.scrollToPage(0)
        }
    }
    BackHandler(route != "home" || pager.currentPage != 0) { route = "home"; scope.launch { pager.animateScrollToPage(0) } }
    AnimatedContent(targetState = route, modifier = Modifier.fillMaxSize(), transitionSpec = {
        if (state.config.drawerAnimation && (initialState == "apps" || targetState == "apps")) {
            (fadeIn(tween(180)) + slideInVertically(tween(210)) { if (targetState == "apps") it / 12 else -it / 24 }) togetherWith
                (fadeOut(tween(140)) + slideOutVertically(tween(180)) { if (initialState == "apps") it / 12 else -it / 24 })
        } else EnterTransition.None togetherWith ExitTransition.None
    }, label = "launcher_route") { visibleRoute ->
    when(visibleRoute) {
        "apps" -> AppDrawer(state, apps, usage, model, activity, openUsage = { route = "settings_usage" }) { route = "home" }
        "settings", "settings_usage" -> SettingsScreen(state, usage, model, activity, initialSection = if (visibleRoute == "settings_usage") SettingsSection.USAGE else null) { route = "home" }
        else -> Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            HorizontalPager(state = pager, userScrollEnabled = !layoutEditing, modifier = Modifier.weight(1f), key = { pages[it].id }) { index ->
                when(pages[index].kind) {
                    PageKind.HOME -> HomeScreen(state, apps, usage, model, activity,
                        editing = layoutEditing, setEditing = { layoutEditing = it },
                        openDrawer = { route = "apps" },
                        openSettings = { route = "settings" },
                    )
                    PageKind.BOARD -> BoardScreen(state, apps, usage, model, activity, openUsage = { route = "settings_usage" }) { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }
                    PageKind.FULL_APPLET -> NotesScreen(state.note, model, activity)
                }
            }
            if (state.config.showBottomBar && !layoutEditing) Row(Modifier.testTag("bottom_bar").fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                pages.forEachIndexed { index, page ->
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(index) } }, modifier = Modifier.semantics { selected = index == pager.currentPage; role = Role.Tab }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text(stringResource(when(page.kind) { PageKind.HOME -> R.string.home; PageKind.BOARD -> R.string.board; else -> R.string.notes }),
                            color = if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (index == pager.currentPage) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.config.showSettingsButton) IconButton(onClick = { route = "settings" }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            }
        }
    }
    }
}



@Composable
private fun BoardScreen(state: StoredState, apps: List<InstalledApp>, usage: UsageSnapshot, model: LauncherViewModel, activity: MainActivity, openUsage: () -> Unit, openNotes: () -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 32.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(stringResource(R.string.board_title), style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(8.dp)); MutedText(stringResource(R.string.board_body)) }
        if (state.config.showTasks) item { AppletCard { TaskApplet(state.tasks, model) } }
        if (state.config.showNotes) item { AppletCard {
            SectionTitle(stringResource(R.string.quick_note))
            Text(state.note.ifBlank { stringResource(R.string.note_empty) }, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = openNotes) { Text(stringResource(R.string.open_notes)) }
        } }
        if (state.config.showUsage) item { Box(Modifier.testTag("usage_applet").clip(MaterialTheme.shapes.large).clickable(onClick = openUsage)) { AppletCard { UsageApplet(usage, apps, activity) } } }
        items(if (state.config.showWidgets) state.widgets.filter { it.enabled } else emptyList(), key = { "widget:${it.widgetId}" }) { slot -> WidgetCard(slot, model, activity, state.config.monochromeWidgets) }
        if (state.config.showWidgets) item {
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_widget)) }
            Spacer(Modifier.height(6.dp)); MutedText(stringResource(R.string.widget_hint))
        }
    }
    if (picker) WidgetPicker(activity, { picker = false }, state.config.groupWidgets, state.config.monochromeWidgets) { info -> picker = false; activity.addWidget(info) }
}

@Composable
private fun TaskApplet(tasks: List<TaskItem>, model: LauncherViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    SectionTitle(stringResource(R.string.priority))
    if (tasks.isEmpty()) MutedText(stringResource(R.string.no_tasks))
    tasks.forEach { task ->
        key(task.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(task.done, { model.toggleTask(task.id) })
                Text(task.text, Modifier.weight(1f), textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { model.removeTask(task.id) }) { Icon(Icons.Default.Close, stringResource(R.string.remove), Modifier.size(18.dp)) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true,
            placeholder = { Text(stringResource(R.string.task_hint), style = MaterialTheme.typography.bodyMedium) })
        IconButton(onClick = { model.addTask(text); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
    }
}

@Composable
private fun NotesScreen(initial: String, model: LauncherViewModel, activity: MainActivity) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.notes), style = MaterialTheme.typography.headlineLarge)
        MutedText(stringResource(R.string.note_local))
        OutlinedTextField(draft, { draft = it; model.note(it) }, Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(stringResource(R.string.note_hint)) }, shape = MaterialTheme.shapes.large)
        TextButton(onClick = { activity.openObsidian(draft) }, enabled = draft.isNotBlank()) { Text(stringResource(R.string.open_obsidian)) }
        MutedText(stringResource(R.string.obsidian_hint))
    }
}

@Composable
private fun UsageApplet(usage: UsageSnapshot, apps: List<InstalledApp>, activity: MainActivity) {
    SectionTitle(stringResource(R.string.usage_title))
    when {
        !usage.checked -> CircularProgressIndicator(Modifier.size(24.dp))
        !usage.granted -> {
            MutedText(stringResource(R.string.usage_access_body))
            TextButton(onClick = activity::requestUsage) { Text(stringResource(R.string.grant_usage)) }
        }
        usage.unavailable -> MutedText(stringResource(R.string.usage_unavailable))
        else -> {
            Text(duration(usage.durations.values.sum()), style = MaterialTheme.typography.headlineLarge)
            MutedText(stringResource(R.string.usage_estimate))
            val rows = usage.durations.entries.sortedByDescending { it.value }.take(4)
            if (rows.isEmpty()) MutedText(stringResource(R.string.usage_empty))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(apps.firstOrNull { it.packageName == row.key }?.label ?: row.key, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(12.dp)); Text(duration(row.value), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) pluralStringResource(R.plurals.duration_minutes, minutes.toInt(), minutes) else stringResource(R.string.duration_hours, minutes / 60, minutes % 60)
}

@Composable
internal fun AppearanceControls(config: LauncherConfig, model: LauncherViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        ThemeMode.entries.forEach { theme ->
            FilterChip(config.theme == theme, { model.configure { it.copy(theme = theme) } }, label = { Text(stringResource(when(theme) { ThemeMode.SYSTEM -> R.string.system; ThemeMode.LIGHT -> R.string.light; ThemeMode.DARK -> R.string.dark })) })
        }
    }
    AppDisplayControls(config, model)
    SettingSwitch(stringResource(R.string.monochrome_icons), config.monochromeIcons) { value -> model.configure { it.copy(monochromeIcons = value, blackWhiteIcons = if (value) false else it.blackWhiteIcons) } }
    SettingSwitch(stringResource(R.string.black_white_icons), config.blackWhiteIcons) { value -> model.configure { it.copy(blackWhiteIcons = value, monochromeIcons = if (value) false else it.monochromeIcons) } }
    Text(stringResource(R.string.font_size), style = MaterialTheme.typography.labelLarge)
    var scale by remember(config.fontScale) { mutableFloatStateOf(config.fontScale) }
    Slider(scale, { scale = it }, valueRange = 0.85f..1.3f, steps = 8, onValueChangeFinished = { model.configure { it.copy(fontScale = scale) } })
}

@Composable
internal fun LanguagePicker(config: LauncherConfig, model: LauncherViewModel) {
    Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        listOf("system" to R.string.system, "en" to R.string.english, "tr" to R.string.turkish).forEach { (id, label) ->
            FilterChip(config.language == id, { model.configure { it.copy(language = id) } }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
internal fun Header(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
internal fun Choice(title: String, body: String, selected: Boolean, click: () -> Unit) {
    Surface(onClick = click, modifier = Modifier.semantics { this.selected = selected; role = Role.RadioButton }, shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold); MutedText(body)
            }
            if (selected) { Spacer(Modifier.width(12.dp)); Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
internal fun AppletCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable internal fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
@Composable internal fun MutedText(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
internal fun Preset.title() = when(this) { Preset.EVERYDAY -> R.string.everyday; Preset.MODERN -> R.string.modern_preset; Preset.MONOCHROME -> R.string.monochrome; Preset.ABSOLUTE_FOCUS -> R.string.absolute_focus; Preset.TEXT_ONLY -> R.string.text_only_preset }
internal fun Preset.description() = when(this) { Preset.EVERYDAY -> R.string.everyday_body; Preset.MODERN -> R.string.modern_preset_body; Preset.MONOCHROME -> R.string.monochrome_body; Preset.ABSOLUTE_FOCUS -> R.string.absolute_focus_body; Preset.TEXT_ONLY -> R.string.text_only_preset_body }
