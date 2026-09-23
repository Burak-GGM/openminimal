package org.openminimal.launcher.model

const val FOLDER_PREFIX = "folder:"
data class HomeFolder(val id: String, val name: String, val appIds: List<String>)

fun normalizeHomeFolders(folders: List<HomeFolder>): List<HomeFolder> {
    val ids = mutableSetOf<String>()
    val members = mutableSetOf<String>()
    return folders.take(64).mapNotNull { folder ->
        if (!folder.id.startsWith(FOLDER_PREFIX) || !ids.add(folder.id) || folder.name.isBlank()) return@mapNotNull null
        val apps = folder.appIds.take(512).filter { it.isNotBlank() && !it.startsWith(FOLDER_PREFIX) && !isEmptySlot(it) && members.add(it) }
        if (apps.isEmpty()) null else folder.copy(name = folder.name.trim().take(60), appIds = apps)
    }
}

/** Favorites remain real application IDs; folders are a home-only projection. */
fun homeEntryIds(favorites: List<String>, folders: List<HomeFolder>): List<String> {
    val membership = folders.flatMap { f -> f.appIds.map { it to f.id } }.toMap()
    return favorites.map { membership[it] ?: it }.distinct()
}

fun StoredState.saveHomeFolder(folder: HomeFolder): StoredState {
    val requested = normalizeHomeFolders(listOf(folder)).firstOrNull() ?: return this
    val updated = normalizeHomeFolders(config.homeFolders.filterNot { it.id == folder.id }.map {
        it.copy(appIds = it.appIds.filterNot { app -> app in requested.appIds })
    } + requested)
    val nextFavorites = (favorites + requested.appIds).distinct()
    val oldKeys = favoriteSlotKeys(homeEntryIds(favorites, config.homeFolders), config.favoriteSlots, config.gridColumns, false)
        .map { if (isEmptySlot(it)) "" else it }.toMutableList()
    val nextIds = homeEntryIds(nextFavorites, updated).toSet()
    var anchor = oldKeys.indexOf(folder.id)
    if (anchor < 0) anchor = requested.appIds.firstNotNullOfOrNull { app -> oldKeys.indexOf(app).takeIf { it >= 0 } } ?: -1
    oldKeys.indices.forEach { if (oldKeys[it] !in nextIds) oldKeys[it] = "" }
    if (anchor >= 0) oldKeys[anchor] = folder.id else if (folder.id !in oldKeys) oldKeys.add(folder.id)
    // Removed members remain favorites, returning to vacant home cells.
    return copy(favorites = nextFavorites, config = config.copy(homeFolders = updated, favoriteSlots = normalizeFavoriteSlots(oldKeys)))
}

fun StoredState.removeHomeFolder(id: String): StoredState {
    val folder = config.homeFolders.firstOrNull { it.id == id } ?: return this
    val slots = config.favoriteSlots.flatMap { if (it == id) folder.appIds else listOf(it) }
    return copy(config = config.copy(homeFolders = config.homeFolders.filterNot { it.id == id }, favoriteSlots = normalizeFavoriteSlots(slots)))
}
