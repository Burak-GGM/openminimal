package org.openminimal.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.R
import org.openminimal.launcher.model.LauncherConfig

@Composable internal fun HomeTextControls(config: LauncherConfig, model: LauncherViewModel) {
    var edit by rememberSaveable { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.home_texts))
    SettingSwitch(stringResource(R.string.show_home_heading), config.showHomeHeading) { v -> model.configure { it.copy(showHomeHeading = v) } }
    SettingSwitch(stringResource(R.string.show_home_footer), config.showHomeFooter) { v -> model.configure { it.copy(showHomeFooter = v) } }
    TextButton(onClick = { edit = true }) { Text(stringResource(R.string.customize_home_texts)) }
    if (edit) {
        var heading by rememberSaveable { mutableStateOf(config.homeHeading) }
        var footer by rememberSaveable { mutableStateOf(config.homeFooter) }
        AlertDialog(onDismissRequest = { edit = false }, title = { Text(stringResource(R.string.home_texts)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(heading, { heading = it.take(160) }, Modifier.testTag("heading_input"), label = { Text(stringResource(R.string.home_heading)) }, placeholder = { Text(stringResource(R.string.home_eyebrow)) }, maxLines = 3)
                OutlinedTextField(footer, { footer = it.take(160) }, Modifier.testTag("footer_input"), label = { Text(stringResource(R.string.home_footer_label)) }, placeholder = { Text(stringResource(R.string.home_footer)) }, maxLines = 3)
                MutedText(stringResource(R.string.default_text_hint))
            }
        }, confirmButton = { TextButton(onClick = { model.configure { it.copy(homeHeading = heading.trim(), homeFooter = footer.trim()) }; edit = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
