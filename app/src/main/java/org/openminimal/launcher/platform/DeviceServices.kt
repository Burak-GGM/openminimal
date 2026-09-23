package org.openminimal.launcher.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openminimal.launcher.model.UsageEvent
import org.openminimal.launcher.model.usageDurations
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId

data class InstalledApp(val id: String, val packageName: String, val label: String, val icon: Bitmap, val group: org.openminimal.launcher.model.AppGroup = org.openminimal.launcher.model.AppGroup.OTHER)
data class UsageSnapshot(val granted: Boolean = false, val durations: Map<String, Long> = emptyMap(), val unavailable: Boolean = false, val checked: Boolean = false)

class DeviceServices(private val context: Context) {
    suspend fun iconPacks() = withContext(Dispatchers.IO) { IconPacks(context).installed() }
    suspend fun apps(iconPack: String = "", themedIcons: Boolean = false): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val pack = IconPacks(context).load(iconPack)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            if (info.activityInfo.packageName == context.packageName) return@mapNotNull null
            runCatching {
                InstalledApp(
                    ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString(),
                    info.activityInfo.packageName, info.loadLabel(pm).toString(),
                    pack?.icon(ComponentName(info.activityInfo.packageName, info.activityInfo.name)) ?: themedIcon(info.loadIcon(pm), themedIcons),
                    platformGroup(info.activityInfo.applicationInfo.category),
                )
            }.getOrNull()
        }.distinctBy { it.id }.sortedWith(compareBy(Collator.getInstance()) { it.label })
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    suspend fun usage(): UsageSnapshot = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext UsageSnapshot(checked = true)
        runCatching {
            val end = System.currentTimeMillis()
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val source = context.getSystemService(UsageStatsManager::class.java).queryEvents(start - 86_400_000, end)
                ?: return@withContext UsageSnapshot(granted = true, unavailable = true, checked = true)
            val events = mutableListOf<UsageEvent>()
            val event = UsageEvents.Event()
            while (source.hasNextEvent()) {
                source.getNextEvent(event)
                val kind = when (event.eventType) {
                    1 -> UsageEvent.Kind.RESUME
                    2 -> UsageEvent.Kind.PAUSE
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Kind.SCREEN_OFF
                    else -> null
                }
                if (kind != null) events.add(UsageEvent(event.timeStamp, event.packageName.orEmpty(), kind))
            }
            UsageSnapshot(true, usageDurations(events, start, end).filterKeys { it != context.packageName }, checked = true)
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            UsageSnapshot(granted = true, unavailable = true, checked = true)
        }
    }
}

private fun themedIcon(drawable: android.graphics.drawable.Drawable, enabled: Boolean): Bitmap {
    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 && drawable is android.graphics.drawable.AdaptiveIconDrawable) {
        val mono = drawable.monochrome
        if (mono != null) {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawCircle(48f, 48f, 46f, android.graphics.Paint(3).apply { color = 0xffe8e8e3.toInt() })
            mono.mutate().apply { setTint(0xff222522.toInt()); setBounds(14, 14, 82, 82); draw(canvas) }
            return bitmap
        }
    }
    return drawable.toBitmap(96, 96)
}
