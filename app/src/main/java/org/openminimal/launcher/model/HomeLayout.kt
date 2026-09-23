package org.openminimal.launcher.model

import kotlin.math.ceil

/** Shared row metrics keep the fitted viewport consistent with app and folder renderers. */
fun appEntryHeight(layout: AppLayoutConfig, fontScale: Float): Float = if (layout.isGrid) {
    layout.grid.iconSize + 20f + if (layout.grid.showLabels) 6f + 32f * fontScale else 8f
} else maxOf(48f, layout.list.textSize * 1.3f * fontScale + 16f,
    if (layout.showIcons) layout.list.iconSize + 16f else 0f)

fun fittedGridColumns(requested: Int, widthDp: Float): Int = requested.coerceIn(1, ((widthDp + 4) / 52).toInt().coerceAtLeast(1))

fun homeEntriesHeight(ids: List<String>, columns: Int, rowHeight: Float, gap: Float,
    groupOf: ((String) -> String)? = null, headingHeight: Float = 0f): Float {
    if (ids.isEmpty()) return 0f
    val sizes = groupOf?.let { group -> ids.groupingBy(group).eachCount().values } ?: listOf(ids.size)
    val rows = sizes.sumOf { ceil(it.toDouble() / columns).toInt() }
    return rows * (rowHeight + gap) - gap + if (groupOf == null) 0f else sizes.size * (headingHeight + gap)
}

/** Choose a prefix that fits complete category rows; even one category per app cannot overflow. */
fun fitHomeEntries(ids: List<String>, availableHeight: Float, columns: Int, rowHeight: Float,
    gap: Float, maximum: Int, groupOf: ((String) -> String)? = null, headingHeight: Float = 0f): List<String> {
    var count = 0
    while (count < minOf(ids.size, maximum) && homeEntriesHeight(ids.take(count + 1), columns, rowHeight, gap, groupOf, headingHeight) <= availableHeight) count++
    return ids.take(count)
}
