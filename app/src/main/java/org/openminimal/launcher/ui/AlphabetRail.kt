package org.openminimal.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openminimal.launcher.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** Relative movement avoids mapping a tiny rail to the entire alphabet at touch-down. */
@Composable internal fun AlphabetRail(labels: List<String>, selected: Int, compact: Boolean, animate: Boolean, jump: (Int) -> Unit) {
    val latestJump by rememberUpdatedState(jump)
    val current by rememberUpdatedState(selected)
    var dragging by remember { mutableStateOf(false) }
    val position by animateFloatAsState(selected.toFloat(), tween(if (animate) 160 else 0), label = "alphabet_position")
    val step = with(LocalDensity.current) { 36.dp.toPx() }
    val label = stringResource(R.string.alphabet_scroll)
    val height = if (compact) 196.dp else 300.dp
    Box(Modifier.testTag("alphabet_rail").width(48.dp).height(height)
        .clip(CircleShape)
        .pointerInput(labels, step, compact) {
            detectTapGestures { tap ->
                val direction = ((tap.y - size.height / 2f) / step).roundToInt().coerceIn(if (compact) -2 else -3, if (compact) 2 else 3)
                latestJump((current + direction).coerceIn(labels.indices))
            }
        }
        .pointerInput(labels, step) {
            var accumulated = 0f
            var startIndex = 0
            detectDragGestures(
                onDragStart = { startIndex = current; accumulated = 0f; dragging = true },
                onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
            ) { change, amount ->
                change.consume(); accumulated += amount.y
                val index = (startIndex + (accumulated / step).toInt()).coerceIn(labels.indices)
                if (index != current) latestJump(index)
            }
        }
        .semantics {
            contentDescription = label
            stateDescription = labels[selected.coerceIn(labels.indices)]
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..labels.lastIndex.toFloat(), (labels.size - 2).coerceAtLeast(0))
            setProgress { latestJump(it.roundToInt().coerceIn(labels.indices)); true }
            customActions = listOf(
                CustomAccessibilityAction(labels[(selected - 1).coerceAtLeast(0)]) { latestJump((selected - 1).coerceAtLeast(0)); true },
                CustomAccessibilityAction(labels[(selected + 1).coerceAtMost(labels.lastIndex)]) { latestJump((selected + 1).coerceAtMost(labels.lastIndex)); true },
            )
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.width(3.dp).fillMaxHeight(.88f).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f), CircleShape))
        Box(Modifier.size(if (dragging) 40.dp else 36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
        val neighbors = if (compact) 2 else 3
        val middle = position.roundToInt()
        (middle - neighbors..middle + neighbors).filter { it in labels.indices }.forEach { index ->
            val distance = abs(index - position)
            Text(labels[index], Modifier.graphicsLayer {
                translationY = (index - position) * step
                alpha = (1f - distance / (neighbors + 1)).coerceIn(0f, 1f)
                scaleX = if (index == selected) 1f else .8f; scaleY = scaleX
            }, fontSize = 15.sp, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (index == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
