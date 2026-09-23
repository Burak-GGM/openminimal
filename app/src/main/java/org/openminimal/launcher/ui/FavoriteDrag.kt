package org.openminimal.launcher.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.delay
import org.openminimal.launcher.model.isEmptySlot
import org.openminimal.launcher.model.FOLDER_PREFIX

/** Grid-relative coordinates stay stable while keyed items move underneath the finger. */
@Stable
internal class FavoriteDragState(private val slots: Boolean = false) {
    private var lastTargetCell = -1
    private var dropTarget: String? = null
    var hoverTarget by mutableStateOf<String?>(null)
    var folderTarget by mutableStateOf<String?>(null)
    var draggableIds: Set<String> = emptySet()
    var order by mutableStateOf<List<String>>(emptyList())
    var dragging by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    private var grabOffset = Offset.Zero

    fun start(position: Offset, grid: LazyGridState) {
        val item = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            !isEmptySlot(it.key.toString()) && it.key in draggableIds && it.key in order && position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
                position.y >= it.offset.y && position.y < it.offset.y + it.size.height
        } ?: return
        dropTarget = null; hoverTarget = null; folderTarget = null
        lastTargetCell = item.index
        dragging = item.key as String
        pointer = position
        grabOffset = position - item.offset.toOffset()
    }

    fun move(position: Offset, grid: LazyGridState) {
        pointer = position
        val id = dragging ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && it.key in order && position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
                position.y >= it.offset.y && position.y < it.offset.y + it.size.height
        }
        if (slots) {
            dropTarget = target?.key as? String
            hoverTarget = target?.takeIf {
                !id.startsWith(FOLDER_PREFIX) && it.key in draggableIds &&
                    position.x in (it.offset.x + it.size.width * .2f)..(it.offset.x + it.size.width * .8f) &&
                    position.y in (it.offset.y + it.size.height * .2f)..(it.offset.y + it.size.height * .8f)
            }?.key as? String
            folderTarget = hoverTarget
            return
        }
        if (target == null || target.index == lastTargetCell) return
        val from = order.indexOf(id)
        val to = order.indexOf(target.key)
        if (from >= 0 && to >= 0) {
            lastTargetCell = target.index
            order = order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun completeDrop() {
        if (!slots) return
        val from = order.indexOf(dragging)
        val to = order.indexOf(dropTarget)
        if (from >= 0 && to >= 0) order = order.toMutableList().apply {
            val value = this[from]; this[from] = this[to]; this[to] = value
        }
        dropTarget = null
    }

    fun offset(id: String, grid: LazyGridState): Offset = if (id != dragging) Offset.Zero else
        pointer - grabOffset - (grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset?.toOffset() ?: Offset.Zero)
}

@Composable
internal fun rememberFavoriteDrag(ids: List<String>, grid: LazyGridState, slots: Boolean = false, draggableIds: List<String> = ids): FavoriteDragState {
    val drag = remember(slots) { FavoriteDragState(slots).apply { order = ids } }
    SideEffect { drag.draggableIds = draggableIds.toSet() }
    LaunchedEffect(ids) { if (drag.dragging == null) drag.order = ids }
    LaunchedEffect(drag.dragging) {
        if (drag.dragging != null) while (true) {
            val info = grid.layoutInfo
            val edge = 100f
            val scroll = when {
                drag.pointer.y < info.viewportStartOffset + edge -> -14f
                drag.pointer.y > info.viewportEndOffset - edge -> 14f
                else -> 0f
            }
            if (scroll != 0f) { grid.scrollBy(scroll); drag.move(drag.pointer, grid) }
            delay(16)
        }
    }
    return drag
}

@Composable
internal fun Modifier.editFavoriteLayout(
    editing: Boolean, drag: FavoriteDragState, grid: LazyGridState,
    commit: (List<String>) -> Unit, feedback: () -> Unit, merge: (String, String) -> Unit = { _, _ -> },
): Modifier {
    val combine by rememberUpdatedState(merge)
    val save by rememberUpdatedState(commit)
    val vibrate by rememberUpdatedState(feedback)
    return if (!editing) this else pointerInput(drag, grid) {
        fun finish(applyDrop: Boolean = false) {
            val source = drag.dragging
            val target = drag.folderTarget
            if (applyDrop && source != null && target != null) combine(source, target)
            else {
                if (applyDrop) drag.completeDrop()
                if (source != null) save(drag.order)
            }
            drag.dragging = null; drag.hoverTarget = null; drag.folderTarget = null
        }
        try {
            detectDragGestures(
                orientationLock = null,
                onDragStart = { down, _, _ -> drag.start(down.position, grid); if (drag.dragging != null) vibrate() },
                onDragEnd = { _ -> finish(true) }, onDragCancel = { finish() },
            ) { change, _ ->
                if (drag.dragging != null) {
                    change.consume()
                    drag.move(change.position, grid)
                }
            }
        } finally { finish() }
    }
}
