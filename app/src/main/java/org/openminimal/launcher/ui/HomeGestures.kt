package org.openminimal.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Only the home page owns this gesture; board scrolling and note editing stay native. */
@Composable
fun Modifier.swipeUpToApps(enabled: Boolean, onOpen: () -> Unit): Modifier {
    val open by rememberUpdatedState(onOpen)
    return if (!enabled) this else pointerInput(Unit) {
        val threshold = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size != 1) break
                val change = event.changes.first()
                if (!change.pressed || change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                val delta = change.position - down.position
                // Lock direction before intercepting a horizontal pager gesture.
                if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) break
                if (delta.y > viewConfiguration.touchSlop) break
                if (-delta.y >= threshold && -delta.y > abs(delta.x) * 1.5f) {
                    change.consume()
                    open()
                    break
                }
            }
        }
    }
}

/** Dismiss only at the top; downward list scrolling must remain available. */
@Composable
fun Modifier.swipeDownToHome(enabled: Boolean, atTop: () -> Boolean, onClose: () -> Unit): Modifier {
    val close by rememberUpdatedState(onClose)
    val top by rememberUpdatedState(atTop)
    return if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!top()) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size != 1) break
                val change = event.changes.first()
                if (!change.pressed) break
                val delta = change.position - down.position
                if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y)) break
                if (delta.y < -viewConfiguration.touchSlop) break
                if (delta.y >= 64.dp.toPx() && delta.y > abs(delta.x) * 1.5f) {
                    change.consume(); close(); break
                }
            }
        }
    }
}
