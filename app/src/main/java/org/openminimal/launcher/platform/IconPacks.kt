package org.openminimal.launcher.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import org.openminimal.launcher.model.AppGroup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPack(val packageName: String, val label: String, val supported: Boolean = true)

/** Common ADW/Nova appfilter format; never loads another application's executable code. */
class IconPacks(private val context: Context) {
    fun installed(): List<IconPack> {
        val pm = context.packageManager
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME")
        @Suppress("DEPRECATION")
        val standard = actions.flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .distinctBy { it.activityInfo.packageName }.mapNotNull {
                runCatching { IconPack(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString()) }.getOrNull()
            }.sortedBy { it.label.lowercase() }
        val nothing = runCatching {
            val info = pm.getApplicationInfo("com.nothing.icon", 0)
            IconPack(info.packageName, info.loadLabel(pm).toString(), supported = false)
        }.getOrNull()
        return (standard + listOfNotNull(nothing)).distinctBy { it.packageName }
    }

    fun load(packageName: String): LoadedIconPack? {
        if (packageName.isBlank()) return null
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val mapping = mutableMapOf<String, String>()
            fun read(parser: XmlPullParser) {
                var count = 0
                while (parser.eventType != XmlPullParser.END_DOCUMENT && count++ < 250_000) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component").orEmpty().removePrefix("ComponentInfo{").removeSuffix("}")
                        val drawable = parser.getAttributeValue(null, "drawable").orEmpty()
                        ComponentName.unflattenFromString(component)?.let { if (drawable.isNotBlank()) mapping[it.flattenToString()] = drawable }
                    }
                    parser.next()
                }
            }
            val xml = resources.getIdentifier("appfilter", "xml", packageName)
            if (xml != 0) resources.getXml(xml).use { read(it) }
            else resources.assets.open("appfilter.xml").use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, "UTF-8")
                read(parser)
            }
            LoadedIconPack(resources, packageName, mapping)
        }.getOrNull()
    }
}

class LoadedIconPack(private val resources: Resources, private val packageName: String, private val mapping: Map<String, String>) {
    fun icon(component: ComponentName): Bitmap? = runCatching {
        val name = mapping[component.flattenToString()] ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) null else resources.getDrawable(id, null).toBitmap(96, 96)
    }.getOrNull()
}

fun platformGroup(category: Int): AppGroup = when (category) {
    ApplicationInfo.CATEGORY_GAME -> AppGroup.GAMES
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppGroup.PRODUCTIVITY
    ApplicationInfo.CATEGORY_SOCIAL -> AppGroup.SOCIAL
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> AppGroup.MEDIA
    ApplicationInfo.CATEGORY_MAPS -> AppGroup.MAPS
    ApplicationInfo.CATEGORY_NEWS -> AppGroup.NEWS
    else -> AppGroup.OTHER
}
