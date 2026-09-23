package org.openminimal.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*

@Composable internal fun PresetControls(state: StoredState, model: LauncherViewModel) {
    val status = state.config.presetStatus(state.customPresets)
    val label = when(status) {
        is PresetStatus.BuiltIn -> stringResource(status.preset.title())
        is PresetStatus.Saved -> status.name
        PresetStatus.Custom -> stringResource(R.string.custom_label)
    }
    Text(stringResource(R.string.current_preset, label), Modifier.testTag("current_preset"), style = MaterialTheme.typography.titleMedium)
    MutedText(stringResource(R.string.presets_keep_data))
    Preset.entries.forEach { preset ->
        Choice(stringResource(preset.title()), stringResource(preset.description()), status == PresetStatus.BuiltIn(preset)) { model.configure { it.applyPreset(preset) } }
    }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var delete by rememberSaveable { mutableStateOf<String?>(null) }
    Button(onClick = { name = ""; editor = "new" }, enabled = state.customPresets.size < MAX_CUSTOM_PRESETS, modifier = Modifier.fillMaxWidth().testTag("save_as_preset")) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save_as_preset))
    }
    MutedText(stringResource(R.string.preset_limit, MAX_CUSTOM_PRESETS))
    SectionTitle(stringResource(R.string.saved_presets))
    if (state.customPresets.isEmpty()) MutedText(stringResource(R.string.no_saved_presets))
    state.customPresets.forEach { preset ->
        Surface(modifier = Modifier.testTag("preset:${preset.id}"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { model.applyCustomPreset(preset.id) }, modifier = Modifier.weight(1f).testTag("apply_preset:${preset.id}")) { Text(stringResource(R.string.apply_preset)) }
                    IconButton(onClick = { name = preset.name; editor = preset.id }) { Icon(Icons.Default.Edit, stringResource(R.string.rename_preset)) }
                    IconButton(onClick = { delete = preset.id }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_preset)) }
                }
            }
        }
    }
    if (editor != null) AlertDialog(onDismissRequest = { editor = null }, title = { Text(stringResource(if (editor == "new") R.string.save_as_preset else R.string.rename_preset)) },
        text = { OutlinedTextField(name, { name = it.take(48) }, singleLine = true, label = { Text(stringResource(R.string.preset_name)) }, modifier = Modifier.testTag("preset_name")) },
        confirmButton = { TextButton(onClick = { if (editor == "new") model.savePreset(name) else model.renamePreset(requireNotNull(editor), name); editor = null }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(R.string.cancel)) } })
    delete?.let { id -> state.customPresets.firstOrNull { it.id == id }?.let { preset ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text(stringResource(R.string.delete_preset)) },
            text = { Text(stringResource(R.string.delete_preset_message, preset.name)) },
            confirmButton = { TextButton(onClick = { model.deletePreset(id); delete = null }, modifier = Modifier.testTag("confirm_delete_preset")) { Text(stringResource(R.string.delete_preset)) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.cancel)) } })
    } }
}
