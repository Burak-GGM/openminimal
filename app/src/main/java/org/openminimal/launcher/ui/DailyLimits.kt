@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.openminimal.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.MainActivity
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.InstalledApp
import org.openminimal.launcher.platform.UsageSnapshot
import java.time.LocalDate

@Composable
internal fun limitSummary(minutes: Int?, usedMillis: Long?): String = when (minutes) {
    null -> stringResource(R.string.no_daily_limit)
    else -> if (usedMillis == null) pluralStringResource(R.plurals.daily_limit_value, minutes, minutes)
    else stringResource(R.string.daily_limit_summary, minutes, duration(usedMillis))
}

@Composable
internal fun FocusRuleEditor(
    appName: String,
    currentMinutes: Int?,
    currentRule: AppFocusRule?,
    usageGranted: Boolean,
    close: () -> Unit,
    save: (Int?, AppFocusRule?) -> Unit,
) {
    val defaultPhrase = stringResource(R.string.challenge_default_phrase)
    var budgetEnabled by remember(appName, currentMinutes, currentRule) { mutableStateOf(currentMinutes != null || currentRule == null) }
    var minutes by remember(appName, currentMinutes) { mutableFloatStateOf((currentMinutes ?: 30).toFloat()) }
    var rule by remember(appName, currentRule, defaultPhrase) {
        mutableStateOf((currentRule ?: AppFocusRule()).let { value ->
            value.copy(
                limitChallenge = value.limitChallenge.withDefaultPhrase(defaultPhrase),
                everyLaunchChallenge = value.everyLaunchChallenge?.withDefaultPhrase(defaultPhrase),
            )
        })
    }
    var everyLaunch by remember(appName, currentRule) { mutableStateOf(currentRule?.everyLaunchChallenge != null) }
    val limitGate = budgetEnabled && rule.limitReachedBehavior == LimitReachedBehavior.FOCUS_GATE
    val everyChallenge = rule.everyLaunchChallenge ?: FocusChallenge(phrase = defaultPhrase)
    val valid = (!limitGate || rule.limitChallenge.valid()) && (!everyLaunch || everyChallenge.valid())

    ActionSheet(appName, close) {
        Text(stringResource(R.string.focus_gate_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch(stringResource(R.string.daily_budget_enabled), budgetEnabled) { budgetEnabled = it }
        if (budgetEnabled) {
            Text(pluralStringResource(R.plurals.daily_limit_value, minutes.toInt(), minutes.toInt()), style = MaterialTheme.typography.headlineSmall)
            Slider(
                value = minutes,
                onValueChange = { minutes = (it / 5).toInt().times(5).toFloat() },
                valueRange = 5f..240f,
                steps = 46,
                modifier = Modifier.testTag("daily_limit_slider"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { value ->
                    FilterChip(value == minutes.toInt(), { minutes = value.toFloat() }, label = { Text(pluralStringResource(R.plurals.duration_minutes, value, value)) })
                }
            }
            if (!usageGranted) WarningCard(stringResource(R.string.limit_needs_usage))
            SectionTitle(stringResource(R.string.after_budget))
            Choice(stringResource(R.string.hide_until_removed), stringResource(R.string.hide_until_removed_hint),
                rule.limitReachedBehavior == LimitReachedBehavior.HIDE) { rule = rule.copy(limitReachedBehavior = LimitReachedBehavior.HIDE) }
            Choice(stringResource(R.string.require_focus_gate), stringResource(R.string.focus_gate_intro),
                rule.limitReachedBehavior == LimitReachedBehavior.FOCUS_GATE) { rule = rule.copy(limitReachedBehavior = LimitReachedBehavior.FOCUS_GATE) }
            if (rule.limitReachedBehavior == LimitReachedBehavior.FOCUS_GATE) {
                FocusChallengeControls(rule.limitChallenge) { rule = rule.copy(limitChallenge = it) }
            }
        }
        HorizontalDivider()
        SettingSwitch(stringResource(R.string.every_launch_gate), everyLaunch) { enabled ->
            everyLaunch = enabled
            rule = rule.copy(everyLaunchChallenge = if (enabled) everyChallenge else null)
        }
        MutedText(stringResource(R.string.every_launch_gate_hint))
        if (everyLaunch) FocusChallengeControls(everyChallenge) { rule = rule.copy(everyLaunchChallenge = it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (currentMinutes != null || currentRule != null) TextButton(onClick = { save(null, null) }) { Text(stringResource(R.string.remove_focus_rule)) }
            Button(onClick = {
                val storedRule = rule.copy(everyLaunchChallenge = if (everyLaunch) everyChallenge else null).let {
                    if ((!budgetEnabled || it.limitReachedBehavior == LimitReachedBehavior.HIDE) && it.everyLaunchChallenge == null) null else it
                }
                save(if (budgetEnabled) minutes.toInt() else null, storedRule)
            }, enabled = valid && (budgetEnabled || everyLaunch), modifier = Modifier.testTag("save_daily_limit")) { Text(stringResource(R.string.save)) }
        }
    }
}

private fun FocusChallenge.withDefaultPhrase(fallback: String) = if (phrase.isBlank()) copy(phrase = fallback) else this
private fun FocusChallenge.valid() = type != FocusChallengeType.TEXT || phrase.isNotBlank()

@Composable
private fun FocusChallengeControls(value: FocusChallenge, change: (FocusChallenge) -> Unit) {
    SectionTitle(stringResource(R.string.challenge_type))
    EnumChips(value.type, FocusChallengeType.entries, { type -> when (type) {
        FocusChallengeType.TEXT -> R.string.challenge_text
        FocusChallengeType.MATH -> R.string.challenge_math
        FocusChallengeType.MEMORY -> R.string.challenge_memory
        FocusChallengeType.WAIT -> R.string.challenge_wait
    } }) { change(value.copy(type = it)) }
    when (value.type) {
        FocusChallengeType.TEXT -> OutlinedTextField(
            value.phrase,
            { change(value.copy(phrase = it.take(240))) },
            modifier = Modifier.fillMaxWidth().testTag("focus_phrase"),
            label = { Text(stringResource(R.string.challenge_phrase)) },
            minLines = 2,
        )
        FocusChallengeType.MATH, FocusChallengeType.MEMORY -> {
            SectionTitle(stringResource(R.string.challenge_difficulty))
            EnumChips(value.difficulty, FocusDifficulty.entries, { difficulty -> when (difficulty) {
                FocusDifficulty.EASY -> R.string.difficulty_easy
                FocusDifficulty.MEDIUM -> R.string.difficulty_medium
                FocusDifficulty.HARD -> R.string.difficulty_hard
            } }) { change(value.copy(difficulty = it)) }
            if (value.type == FocusChallengeType.MATH) MutedText(stringResource(when (value.difficulty) {
                FocusDifficulty.EASY -> R.string.math_easy_hint
                FocusDifficulty.MEDIUM -> R.string.math_medium_hint
                FocusDifficulty.HARD -> R.string.math_hard_hint
            }))
        }
        FocusChallengeType.WAIT -> {
            SectionTitle(stringResource(R.string.wait_duration))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 30, 60).forEach { seconds ->
                    FilterChip(value.waitSeconds == seconds, { change(value.copy(waitSeconds = seconds)) }, label = { Text(stringResource(R.string.wait_seconds, seconds)) })
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
internal fun ScreenTimeSettings(
    state: StoredState,
    apps: List<InstalledApp>,
    usage: UsageSnapshot,
    model: LauncherViewModel,
    activity: MainActivity,
) {
    val epochDay = LocalDate.now().toEpochDay()
    var overridePackage by remember { mutableStateOf<String?>(null) }
    var editingPackage by remember { mutableStateOf<String?>(null) }
    var choosingApp by remember { mutableStateOf(false) }
    var discloseGuard by remember { mutableStateOf(false) }

    if (usage.checked && usage.granted && !usage.unavailable) {
        Text(duration(usage.durations.values.sum()), style = MaterialTheme.typography.displayMedium)
        MutedText(stringResource(R.string.usage_estimate))
    }
    when {
        !usage.checked -> CircularProgressIndicator(Modifier.size(28.dp))
        !usage.granted -> WarningCard(stringResource(R.string.limit_needs_usage))
        usage.unavailable -> MutedText(stringResource(R.string.usage_unavailable))
    }
    if (!usage.granted) Button(onClick = activity::requestUsage) { Text(stringResource(R.string.grant_usage)) }
    TextButton(onClick = activity::openSystemScreenTime) { Text(stringResource(R.string.system_screen_time)) }

    HorizontalDivider()
    SectionTitle(stringResource(R.string.live_guard))
    Text(stringResource(if (activity.focusGuardEnabled.value) R.string.live_guard_on else R.string.live_guard_off),
        color = if (activity.focusGuardEnabled.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold)
    MutedText(stringResource(R.string.live_guard_summary))
    OutlinedButton(onClick = { if (activity.focusGuardEnabled.value) activity.openAccessibilitySettings() else discloseGuard = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (activity.focusGuardEnabled.value) R.string.manage_live_guard else R.string.enable_live_guard))
    }

    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SectionTitle(stringResource(R.string.focus_gate_and_budget))
            MutedText(stringResource(R.string.focus_gate_intro))
        }
        FilledTonalIconButton(onClick = { choosingApp = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_focus_rule)) }
    }
    val packages = (state.dailyLimits.keys + state.focusRules.keys).distinct().sortedBy { packageName ->
        apps.firstOrNull { it.packageName == packageName }?.label?.lowercase() ?: packageName
    }
    if (packages.isEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null) }
                Spacer(Modifier.width(14.dp)); MutedText(stringResource(R.string.no_focus_rules))
            }
        }
    }
    packages.forEach { packageName ->
        val app = apps.firstOrNull { it.packageName == packageName }
        val appName = app?.label ?: packageName
        val minutes = state.dailyLimits[packageName]
        val rule = state.focusRules[packageName]
        val status = appLimitStatus(packageName, state.dailyLimits, state.limitOverrides, usage.durations, epochDay)
        Surface(onClick = { editingPackage = packageName }, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app?.let { Image(it.icon.asImageBitmap(), null, Modifier.size(36.dp)) }
                    Column(Modifier.weight(1f).padding(start = if (app == null) 0.dp else 12.dp)) {
                        Text(appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        minutes?.let { Text(if (usage.granted && !usage.unavailable) stringResource(R.string.daily_limit_summary, it, duration(status?.usedMillis ?: 0))
                            else pluralStringResource(R.plurals.daily_limit_value, it, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        rule?.everyLaunchChallenge?.let { Text(stringResource(R.string.gate_active_every_launch), color = MaterialTheme.colorScheme.primary) }
                        if (minutes != null) Text(stringResource(if (rule?.limitReachedBehavior == LimitReachedBehavior.FOCUS_GATE) R.string.gate_active_after_limit else R.string.app_hidden_after_limit),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editingPackage = packageName }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_focus_rule)) }
                    IconButton(onClick = { model.setFocusPolicy(packageName, null, null) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_focus_rule)) }
                }
                when {
                    status?.overriddenToday == true -> Text(stringResource(R.string.limit_overridden_today), color = MaterialTheme.colorScheme.primary)
                    status?.reached == true && rule?.limitReachedBehavior == LimitReachedBehavior.FOCUS_GATE ->
                        TextButton(onClick = { overridePackage = packageName }, modifier = Modifier.testTag("override_limit:$packageName")) { Text(stringResource(R.string.unlock_for_today)) }
                    status != null && usage.granted && !usage.unavailable -> LinearProgressIndicator(
                        progress = { (status.usedMillis.toFloat() / (status.limitMinutes * 60_000L)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(end = 18.dp),
                    )
                }
            }
        }
    }

    if (discloseGuard) AlertDialog(
        onDismissRequest = { discloseGuard = false },
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(stringResource(R.string.live_guard_disclosure_title)) },
        text = { Text(stringResource(R.string.live_guard_disclosure)) },
        dismissButton = { TextButton(onClick = { discloseGuard = false }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button(onClick = { discloseGuard = false; activity.openAccessibilitySettings() }) { Text(stringResource(R.string.continue_to_settings)) } },
    )
    if (choosingApp) FocusAppPicker(apps, close = { choosingApp = false }) { packageName -> choosingApp = false; editingPackage = packageName }
    editingPackage?.let { packageName ->
        val appName = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
        FocusRuleEditor(appName, state.dailyLimits[packageName], state.focusRules[packageName], usage.granted && !usage.unavailable,
            close = { editingPackage = null }) { minutes, rule ->
            model.setFocusPolicy(packageName, minutes, rule); editingPackage = null
        }
    }
    overridePackage?.let { packageName ->
        val appName = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
        AlertDialog(
            onDismissRequest = { overridePackage = null },
            icon = { Icon(Icons.Default.DateRange, null) },
            title = { Text(stringResource(R.string.unlock_limit_title, appName)) },
            text = { Text(stringResource(R.string.unlock_limit_warning)) },
            dismissButton = { TextButton(onClick = { overridePackage = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { Button(onClick = { model.overrideDailyLimit(packageName, epochDay); overridePackage = null }) { Text(stringResource(R.string.unlock_for_today)) } },
        )
    }
}

@Composable
private fun FocusAppPicker(apps: List<InstalledApp>, close: () -> Unit, select: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.add_focus_rule)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text(stringResource(R.string.search_focus_apps)) })
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(visible, key = { it.id }) { app ->
                        Row(Modifier.fillMaxWidth().clickable { select(app.packageName) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon.asImageBitmap(), null, Modifier.size(36.dp)); Text(app.label, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
    )
}
