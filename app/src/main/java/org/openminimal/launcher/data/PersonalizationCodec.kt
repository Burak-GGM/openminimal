package org.openminimal.launcher.data

import org.json.JSONObject
import org.json.JSONArray
import org.openminimal.launcher.model.*

internal inline fun <reified T : Enum<T>> JSONObject.enum(key: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == optString(key) } ?: fallback

/** Missing nested fields inherit migrated legacy values; new screen settings then evolve independently. */
internal object PersonalizationCodec {
    fun layout(j: JSONObject?, fallback: AppLayoutConfig): AppLayoutConfig {
        j ?: return fallback
        val l = j.optJSONObject("list") ?: JSONObject()
        val g = j.optJSONObject("grid") ?: JSONObject()
        val old = fallback.list
        return AppLayoutConfig(
            display = j.enum("display", fallback.display),
            list = ListLayoutConfig(
                textSize = l.optInt("textSize", old.textSize).coerceIn(14, 32),
                weight = l.enum("weight", old.weight),
                iconSize = l.optInt("iconSize", old.iconSize).coerceIn(24, 56),
                iconGap = l.optInt("iconGap", old.iconGap).coerceIn(4, 32),
                rowSpacing = l.optInt("rowSpacing", old.rowSpacing).coerceIn(0, 24),
                widthPercent = l.optInt("widthPercent", old.widthPercent).coerceIn(60, 100),
                alignment = l.enum("alignment", old.alignment),
                placement = l.enum("placement", old.placement),
                maxItems = l.optInt("maxItems", old.maxItems).coerceIn(1, 20),
            ),
            grid = GridLayoutConfig(
                columns = g.optInt("columns", fallback.grid.columns).coerceIn(3, 6),
                iconSize = g.optInt("iconSize", fallback.grid.iconSize).coerceIn(32, 64),
                showLabels = g.optBoolean("showLabels", fallback.grid.showLabels),
            ),
        )
    }

    fun layout(value: AppLayoutConfig): JSONObject = JSONObject().apply {
        put("display", value.display.name)
        put("list", JSONObject().apply {
            val l = value.list
            put("textSize", l.textSize); put("weight", l.weight.name); put("iconSize", l.iconSize)
            put("iconGap", l.iconGap); put("rowSpacing", l.rowSpacing); put("widthPercent", l.widthPercent)
            put("alignment", l.alignment.name); put("placement", l.placement.name); put("maxItems", l.maxItems)
        })
        put("grid", JSONObject().put("columns", value.grid.columns).put("iconSize", value.grid.iconSize).put("showLabels", value.grid.showLabels))
    }

    fun clock(j: JSONObject?, fallback: ClockConfig): ClockConfig {
        j ?: return fallback
        return ClockConfig(
            visible = j.optBoolean("visible", fallback.visible),
            size = j.optInt("size", fallback.size).coerceIn(40, 112),
            font = j.enum("font", fallback.font), weight = j.enum("weight", fallback.weight),
            alignment = j.enum("alignment", fallback.alignment), format = j.enum("format", fallback.format),
            showDate = j.optBoolean("showDate", fallback.showDate), dateStyle = j.enum("dateStyle", fallback.dateStyle),
            showWeekday = j.optBoolean("showWeekday", fallback.showWeekday),
            bottomSpacing = j.optInt("bottomSpacing", fallback.bottomSpacing).coerceIn(8, 64),
        )
    }
    fun clock(c: ClockConfig) = JSONObject().apply {
        put("visible", c.visible); put("size", c.size); put("font", c.font.name); put("weight", c.weight.name)
        put("alignment", c.alignment.name); put("format", c.format.name); put("showDate", c.showDate)
        put("dateStyle", c.dateStyle.name); put("showWeekday", c.showWeekday); put("bottomSpacing", c.bottomSpacing)
    }
}

object CustomPresetCodec {
    fun decode(serialized: String?): List<CustomPreset> {
        val array = runCatching { JSONArray(serialized ?: "[]") }.getOrDefault(JSONArray())
        return (0 until minOf(array.length(), MAX_CUSTOM_PRESETS)).mapNotNull { i ->
            val p = array.optJSONObject(i) ?: return@mapNotNull null
            val name = p.optString("name").trim().take(48)
            val id = p.optString("id")
            if (id.isBlank() || name.isBlank() || p.optInt("version", 1) != 1) null
            else CustomPreset(id, name, ConfigCodec.decode(p.optJSONObject("config")?.toString()).presetSnapshot())
        }.distinctBy { it.id }
    }
    fun encode(values: List<CustomPreset>): String = JSONArray().apply {
        values.take(MAX_CUSTOM_PRESETS).forEach { p -> put(JSONObject().apply {
            put("version", p.version); put("id", p.id); put("name", p.name.trim().take(48))
            put("config", JSONObject(ConfigCodec.encode(p.config.presetSnapshot())))
        }) }
    }.toString()
}
