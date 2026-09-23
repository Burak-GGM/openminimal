package org.openminimal.launcher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openminimal.launcher.R

@Composable internal fun LayoutEditorBar(done: () -> Unit) {
    Surface(Modifier.testTag("layout_editor").fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge, tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Edit, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.edit_layout), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.layout_editor_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = done, modifier = Modifier.testTag("layout_done"), shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable internal fun Modifier.layoutEditIndication(editing: Boolean, motion: Boolean, id: String): Modifier {
    if (!editing) return this
    if (!motion) return this.graphicsLayer { scaleX = .96f; scaleY = .96f }
    val transition = rememberInfiniteTransition(label = "layout_edit")
    val tilt by transition.animateFloat(-.7f, .7f, infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse), label = "edit_tilt")
    return graphicsLayer { rotationZ = tilt * if (id.hashCode() % 2 == 0) 1f else -1f }
}

@Composable internal fun EmptyLayoutCell(editing: Boolean, index: Int) {
    val label = stringResource(R.string.empty_layout_cell, index + 1)
    val color = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().height(76.dp).testTag("empty_slot:$index").semantics { if (editing) contentDescription = label }, contentAlignment = Alignment.Center) {
        if (editing) Canvas(Modifier.size(4.dp)) { drawCircle(color) }
    }
}
