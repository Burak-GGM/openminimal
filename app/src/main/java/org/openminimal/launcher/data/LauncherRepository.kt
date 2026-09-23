package org.openminimal.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import org.openminimal.launcher.model.*

private val Context.launcherData by preferencesDataStore("openminimal")

class LauncherRepository(context: Context) {
    private val store = context.applicationContext.launcherData
    private val configKey = stringPreferencesKey("config_v1")
    private val presetsKey = stringPreferencesKey("custom_presets_v1")
    private val noteKey = stringPreferencesKey("note")
    private val tasksKey = stringPreferencesKey("tasks_v1")
    private val favoritesKey = stringPreferencesKey("favorites_v1")
    private val widgetsKey = stringPreferencesKey("widgets_v1")
    private val limitsKey = stringPreferencesKey("daily_limits_v1")
    private val limitOverridesKey = stringPreferencesKey("limit_overrides_v1")
    private val focusRulesKey = stringPreferencesKey("focus_rules_v1")

    val state = store.data.map { prefs ->
        StoredState(
            config = ConfigCodec.decode(prefs[configKey]),
            customPresets = CustomPresetCodec.decode(prefs[presetsKey]),
            note = prefs[noteKey].orEmpty(),
            tasks = array(prefs[tasksKey]).objects().mapNotNull { t ->
                val id = t.optString("id")
                val text = t.optString("text")
                if (id.isBlank() || text.isBlank()) null else TaskItem(id, text, t.optBoolean("done"))
            },
            favorites = array(prefs[favoritesKey]).let { a -> (0 until a.length()).map { a.getString(it) } },
            widgets = array(prefs[widgetsKey]).objects().mapNotNull { w ->
                val id = w.optInt("id", -1)
                if (id < 0) null else WidgetSlot(id, w.optInt("height", 180).coerceIn(100, 500), w.optBoolean("enabled", true))
            },
            dailyLimits = objectMap(prefs[limitsKey]) { value -> value.toIntOrNull()?.coerceIn(1, 1_440) },
            limitOverrides = objectMap(prefs[limitOverridesKey]) { value -> value.toLongOrNull() },
            focusRules = FocusRuleCodec.decode(prefs[focusRulesKey]),
        )
    }

    suspend fun configure(transform: (LauncherConfig) -> LauncherConfig) {
        store.edit { p ->
            p[configKey] = ConfigCodec.encode(transform(ConfigCodec.decode(p[configKey])))
        }
    }

    suspend fun savePreset(name: String, id: String = java.util.UUID.randomUUID().toString()) {
        require(name.isNotBlank())
        store.edit { p ->
            val saved = CustomPresetCodec.decode(p[presetsKey])
            check(saved.size < MAX_CUSTOM_PRESETS)
            val current = ConfigCodec.decode(p[configKey])
            p[presetsKey] = CustomPresetCodec.encode(saved + CustomPreset(id, name.trim().take(48), current.presetSnapshot()))
            p[configKey] = ConfigCodec.encode(current.copy(selectedCustomPresetId = id))
        }
    }
    suspend fun renamePreset(id: String, name: String) {
        require(name.isNotBlank())
        store.edit { p -> p[presetsKey] = CustomPresetCodec.encode(CustomPresetCodec.decode(p[presetsKey]).map {
            if (it.id == id) it.copy(name = name.trim().take(48)) else it
        }) }
    }
    suspend fun deletePreset(id: String) {
        store.edit { p -> p[presetsKey] = CustomPresetCodec.encode(CustomPresetCodec.decode(p[presetsKey]).filterNot { it.id == id }) }
    }
    suspend fun applyCustomPreset(id: String) {
        store.edit { p ->
            val saved = CustomPresetCodec.decode(p[presetsKey]).firstOrNull { it.id == id } ?: return@edit
            val current = ConfigCodec.decode(p[configKey])
            p[configKey] = ConfigCodec.encode(current.applySnapshot(saved.config).copy(selectedCustomPresetId = id))
        }
    }

    suspend fun note(text: String) { store.edit { it[noteKey] = text } }
    suspend fun tasks(transform: (List<TaskItem>) -> List<TaskItem>) {
        store.edit { p ->
            val current = array(p[tasksKey]).objects().map { TaskItem(it.getString("id"), it.getString("text"), it.optBoolean("done")) }
            p[tasksKey] = JSONArray().apply {
                transform(current).forEach { t -> put(JSONObject().put("id", t.id).put("text", t.text).put("done", t.done)) }
            }.toString()
        }
    }
    suspend fun toggleFavorite(id: String, capacity: Int = Int.MAX_VALUE): Boolean {
        var changed = false
        store.edit { p ->
            val a = array(p[favoritesKey])
            val list = (0 until a.length()).map { a.getString(it) }.toMutableList()
            if (list.remove(id)) {
                changed = true
                val config = ConfigCodec.decode(p[configKey])
                val folders = normalizeHomeFolders(config.homeFolders.map { it.copy(appIds = it.appIds - id) })
                p[configKey] = ConfigCodec.encode(config.copy(homeFolders = folders))
            } else {
                val config = ConfigCodec.decode(p[configKey])
                if (homeEntryIds(list, config.homeFolders).size < capacity) {
                    list.add(id)
                    changed = true
                }
            }
            p[favoritesKey] = JSONArray(list).toString()
        }
        return changed
    }
    suspend fun reorderFavorites(ids: List<String>, slots: List<String>? = null) {
        store.edit { p ->
            val a = array(p[favoritesKey])
            val current = (0 until a.length()).map { a.getString(it) }
            val config = ConfigCodec.decode(p[configKey])
            val expanded = ids.flatMap { id -> config.homeFolders.firstOrNull { it.id == id }?.appIds ?: listOf(id) }
            // Default suggestions become explicit favorites when first rearranged.
            val ordered = if (current.isEmpty()) expanded.distinct() else expanded.filter { it in current }.distinct() + current.filterNot { it in expanded }
            p[favoritesKey] = JSONArray(ordered).toString()
            if (slots != null) {
                val retained = config.favoriteSlots.drop(slots.size).map { if (it in slots) "" else it }
                p[configKey] = ConfigCodec.encode(config.copy(favoriteSlots = normalizeFavoriteSlots(slots + retained)))
            }
        }
    }
    suspend fun mergeIntoFolder(source: String, target: String, name: String, suggestions: List<String>) = editHome { state ->
        val current = if (state.favorites.isEmpty()) state.copy(favorites = suggestions) else state
        if (source !in current.favorites || source == target) current else {
            val existing = current.config.homeFolders.firstOrNull { it.id == target }
            if (existing != null) current.saveHomeFolder(existing.copy(appIds = (existing.appIds + source).distinct()))
            else if (target in current.favorites) current.saveHomeFolder(HomeFolder(FOLDER_PREFIX + java.util.UUID.randomUUID(), name, listOf(target, source)))
            else current
        }
    }
    suspend fun saveFolder(folder: HomeFolder) = editHome { it.saveHomeFolder(folder) }
    suspend fun removeFolder(id: String) = editHome { it.removeHomeFolder(id) }
    suspend fun removeFromFolder(folderId: String, appId: String) = editHome { state ->
        val folder = state.config.homeFolders.firstOrNull { it.id == folderId } ?: return@editHome state
        val remaining = folder.appIds - appId
        if (remaining.isEmpty()) state.removeHomeFolder(folderId) else state.saveHomeFolder(folder.copy(appIds = remaining))
    }
    private suspend fun editHome(transform: (StoredState) -> StoredState) {
        store.edit { p ->
            val a = array(p[favoritesKey])
            val current = StoredState(config = ConfigCodec.decode(p[configKey]), favorites = (0 until a.length()).map { a.getString(it) })
            val next = transform(current)
            p[configKey] = ConfigCodec.encode(next.config)
            p[favoritesKey] = JSONArray(next.favorites).toString()
        }
    }
    suspend fun widgets(transform: (List<WidgetSlot>) -> List<WidgetSlot>) {
        store.edit { p ->
            val current = array(p[widgetsKey]).objects().map { WidgetSlot(it.getInt("id"), it.optInt("height", 180), it.optBoolean("enabled", true)) }
            p[widgetsKey] = JSONArray().apply {
                transform(current).distinctBy { it.widgetId }.forEach { put(JSONObject().put("id", it.widgetId).put("height", it.heightDp).put("enabled", it.enabled)) }
            }.toString()
        }
    }

    suspend fun setDailyLimit(packageName: String, minutes: Int?) {
        require(packageName.isNotBlank())
        store.edit { prefs ->
            val values = objectMap(prefs[limitsKey]) { it.toIntOrNull() }.toMutableMap()
            if (minutes == null) values.remove(packageName) else values[packageName] = minutes.coerceIn(1, 1_440)
            prefs[limitsKey] = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }.toString()
            val overrides = objectMap(prefs[limitOverridesKey]) { it.toLongOrNull() }.toMutableMap()
            overrides.remove(packageName)
            prefs[limitOverridesKey] = JSONObject().apply { overrides.forEach { (key, value) -> put(key, value) } }.toString()
        }
    }

    suspend fun overrideDailyLimit(packageName: String, epochDay: Long) {
        require(packageName.isNotBlank())
        store.edit { prefs ->
            val values = objectMap(prefs[limitOverridesKey]) { it.toLongOrNull() }.filterValues { it >= epochDay }.toMutableMap()
            values[packageName] = epochDay
            prefs[limitOverridesKey] = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }.toString()
        }
    }

    suspend fun setFocusPolicy(packageName: String, minutes: Int?, rule: AppFocusRule?) {
        require(packageName.isNotBlank())
        store.edit { prefs ->
            val limits = objectMap(prefs[limitsKey]) { it.toIntOrNull() }.toMutableMap()
            if (minutes == null) limits.remove(packageName) else limits[packageName] = minutes.coerceIn(1, 1_440)
            prefs[limitsKey] = JSONObject().apply { limits.forEach { (key, value) -> put(key, value) } }.toString()
            val rules = FocusRuleCodec.decode(prefs[focusRulesKey]).toMutableMap()
            if (rule == null) rules.remove(packageName) else rules[packageName] = rule
            prefs[focusRulesKey] = FocusRuleCodec.encode(rules)
            if (minutes == null) {
                val overrides = objectMap(prefs[limitOverridesKey]) { it.toLongOrNull() }.toMutableMap()
                overrides.remove(packageName)
                prefs[limitOverridesKey] = JSONObject().apply { overrides.forEach { (key, value) -> put(key, value) } }.toString()
            }
        }
    }

    private fun array(s: String?) = runCatching { JSONArray(s ?: "[]") }.getOrDefault(JSONArray())
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun <T> objectMap(serialized: String?, value: (String) -> T?): Map<String, T> {
        val source = runCatching { JSONObject(serialized ?: "{}") }.getOrDefault(JSONObject())
        return source.keys().asSequence().mapNotNull { key -> value(source.optString(key))?.let { key to it } }.toMap()
    }
}
