package org.openminimal.launcher.model

internal const val EMPTY_SLOT_PREFIX = "empty:"
internal fun isEmptySlot(id: String) = id.startsWith(EMPTY_SLOT_PREFIX)

/** Empty cells are explicit. Missing/limited apps retain their reserved position. */
fun normalizeFavoriteSlots(slots: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return slots.take(512).map { if (it.isNotBlank() && seen.add(it)) it else "" }.dropLastWhile { it.isEmpty() }
}

internal fun favoriteSlotKeys(
    ids: List<String>, saved: List<String>, columns: Int, editing: Boolean,
    capacity: Int? = null,
): List<String> {
    val available = ids.toSet()
    val slots = normalizeFavoriteSlots(saved).map { if (it in available) it else "" }.toMutableList()
    ids.distinct().filterNot { it in slots }.forEach { id ->
        val hole = slots.indexOf("")
        if (hole >= 0) slots[hole] = id else slots.add(id)
    }
    while (slots.lastOrNull() == "") slots.removeAt(slots.lastIndex)
    val limit = capacity ?: 512
    // A smaller viewport must not strand an app in an out-of-bounds saved slot while a visible
    // vacancy exists. This projection leaves the saved layout untouched until the user edits it.
    if (slots.size > limit) {
        val overflow = slots.drop(limit).filter { it.isNotEmpty() }.iterator()
        for (index in 0 until limit) if (slots[index].isEmpty() && overflow.hasNext()) slots[index] = overflow.next()
    }
    if (editing) {
        val size = capacity ?: maxOf(columns * 4, ((slots.size + columns - 1) / columns + 1) * columns).coerceAtMost(limit)
        while (slots.size < size) slots.add("")
    }
    return slots.take(limit).mapIndexed { index, id -> id.ifEmpty { "$EMPTY_SLOT_PREFIX$index" } }
}

/** Home stays a single, non-scrolling page. Folders count as one entry. */
fun homeFavoriteCapacity(config: LauncherConfig, screenHeightDp: Int, occupiedGroups: Int = 0): Int {
    // screenHeightDp includes system bars, while Home is also shortened by Open&minimal's
    // optional bottom navigation. Reserve both before calculating complete rows.
    val pageHeight = screenHeightDp - 24 - if (config.showBottomBar) 64 else 0
    val clock = config.home.clock
    val header = 44 + (if (clock.visible) maxOf(112f, clock.size * 1.2f * config.fontScale).toInt() else 0) +
        maxOf(40, clock.bottomSpacing) + (if (config.showHomeHeading) (38 * config.fontScale).toInt() else 0) +
        (if (config.showDate) (24 * config.fontScale).toInt() else 0)
    val footer = 24 + 16 + (if (config.showAllAppsButton) 68 else 0) + (if (config.showHomeFooter) (28 * config.fontScale).toInt() else 0)
    val available = (pageHeight - header - footer).coerceAtLeast(150)
    val raw = if (config.iconsOnly) {
        val rows = (available / (appEntryHeight(config.home.apps, config.fontScale) + 6)).toInt().coerceIn(1, 6)
        rows * config.gridColumns
    } else {
        (available / maxOf(58f, appEntryHeight(config.home.apps, config.fontScale) + config.home.apps.list.rowSpacing))
            .toInt().coerceIn(1, config.home.apps.list.maxItems)
    }
    val headingCost = if (!config.groupFavorites) 0 else occupiedGroups * if (config.iconsOnly) config.gridColumns else 1
    // On a heavily categorized Home, preserving a two-row minimum would reintroduce clipping:
    // group headings consume real rows too. Keep at least one usable entry/row instead.
    return (raw - headingCost).coerceAtLeast(if (config.iconsOnly) config.gridColumns else 1)
}
