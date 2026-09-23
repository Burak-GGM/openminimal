package org.openminimal.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.StoredState
import org.openminimal.launcher.platform.UsageSnapshot

@Composable internal fun SettingsScreen(state: StoredState, usage: UsageSnapshot, model: LauncherViewModel,
    activity: MainActivity, initialSection: SettingsSection? = null, back: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(initialSection?.let { "section:${it.name}" } ?: "root") }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val section = SettingsSection.entries.firstOrNull { destination == "section:${it.name}" }
    val category = SettingsCategory.entries.firstOrNull { destination == "category:${it.name}" }
    val goBack: () -> Unit = {
        when {
            section != null && section == initialSection -> back()
            section != null -> destination = if (query.isNotBlank()) "root" else "category:${section.category.name}"
            category != null -> destination = "root"
            query.isNotBlank() -> query = ""
            else -> back()
        }
    }
    BackHandler(destination != "root" || query.isNotBlank(), goBack)
    Column(Modifier.testTag("settings_screen").fillMaxSize().safeDrawingPadding().imePadding()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            Header(stringResource(section?.title ?: category?.title ?: R.string.settings), goBack)
        }
        if (section == null && category == null) OutlinedTextField(query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("settings_search"),
            singleLine = true, label = { Text(stringResource(R.string.search_settings)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) } },
            shape = MaterialTheme.shapes.extraLarge,
        )
        AnimatedContent(destination, modifier = Modifier.weight(1f), transitionSpec = {
            val ms = if (state.config.drawerAnimation) 160 else 0
            fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
        }, label = "settings_destination") { target ->
            val active = SettingsSection.entries.firstOrNull { target == "section:${it.name}" }
            val group = SettingsCategory.entries.firstOrNull { target == "category:${it.name}" }
            when {
                active != null -> Column(Modifier.fillMaxSize().testTag("settings_section:${active.name}")) {
                    SettingsDetail(active, state, usage, model, activity)
                }
                else -> {
                    val catalog = SettingsSection.entries.map { it to (stringResource(it.title) + " " + stringResource(it.keywords) + " " + stringResource(it.category.title)) }
                    val results = catalog.filter { (_, text) -> query.trim().split(Regex("\\s+")).all { text.contains(it, ignoreCase = true) } }.map { it.first }
                    LazyColumn(Modifier.fillMaxSize().testTag("settings_catalog"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (group != null || query.isNotBlank()) {
                            val entries = if (group != null) SettingsSection.entries.filter { it.category == group } else results
                            if (entries.isEmpty()) item { MutedText(stringResource(R.string.no_settings_found)) }
                            items(entries, key = { it.name }) { entry ->
                                SettingsLink(stringResource(entry.title), stringResource(entry.category.title), "settings_link:${entry.name}") { focus.clearFocus(); destination = "section:${entry.name}" }
                            }
                        } else items(SettingsCategory.entries, key = { it.name }) { entry ->
                            SettingsLink(stringResource(entry.title), stringResource(entry.subtitle), "settings_category:${entry.name}") {
                                destination = "category:${entry.name}"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsLink(title: String, subtitle: String, tag: String, click: () -> Unit) {
    Surface(onClick = click, modifier = Modifier.fillMaxWidth().testTag(tag), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.padding(start = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
