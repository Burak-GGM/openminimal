package org.openminimal.launcher.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

internal data class AppShortcut(val info: ShortcutInfo, val icon: Bitmap?)

internal data class AppShortcuts(val access: Boolean, val items: List<AppShortcut> = emptyList(), val unavailable: Boolean = false)

internal fun readAppShortcuts(context: Context, app: InstalledApp): AppShortcuts = try {
    val launcher = context.getSystemService(LauncherApps::class.java)
    if (!launcher.hasShortcutHostPermission()) AppShortcuts(access = false)
    else {
        val query = LauncherApps.ShortcutQuery().setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val component = ComponentName.unflattenFromString(app.id)
        AppShortcuts(true, launcher.getShortcuts(query, Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled && (it.activity == null || it.activity == component) }
            .distinctBy { it.id }.sortedWith(compareBy<ShortcutInfo> { !it.isDeclaredInManifest }.thenBy { it.rank }).map { info ->
                AppShortcut(info, runCatching { launcher.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)?.toBitmap(96, 96) }.getOrNull())
            })
    }
} catch (_: SecurityException) { AppShortcuts(false) }
catch (_: IllegalStateException) { AppShortcuts(true, unavailable = true) }
