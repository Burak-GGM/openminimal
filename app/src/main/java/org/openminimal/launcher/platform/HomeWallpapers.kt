package org.openminimal.launcher.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openminimal.launcher.model.LauncherConfig
import org.openminimal.launcher.model.WallpaperMode
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun themeWallpaper(widthPixels: Int, heightPixels: Int, color: Int): Bitmap =
    Bitmap.createBitmap(
        widthPixels.coerceIn(360, 1440),
        heightPixels.coerceIn(640, 2560),
        Bitmap.Config.ARGB_8888,
    ).apply { eraseColor(color) }

class HomeWallpapers(private val context: Context) {
    companion object { private val mutex = Mutex() }
    private val saved = File(context.filesDir, "home-wallpaper.jpg")
    private val preferences = context.getSharedPreferences("wallpaper_sync", Context.MODE_PRIVATE)
    suspend fun decode(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            require(opts.outWidth > 0 && opts.outHeight > 0)
            opts.inSampleSize = 1
            while (maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > 2048) opts.inSampleSize *= 2
            opts.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)!!.use { requireNotNull(BitmapFactory.decodeStream(it, null, opts)) }
        }
    }
    suspend fun save(bitmap: Bitmap, revision: Long) = withContext(Dispatchers.IO) { mutex.withLock {
        require(revision > 0)
        val destination = File(context.filesDir, "home-wallpaper-$revision.jpg")
        val temp = File(context.filesDir, "home-wallpaper.tmp")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            try {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    } }
    suspend fun preview(revision: Long = 0): Bitmap? = withContext(Dispatchers.IO) {
        val source = File(context.filesDir, "home-wallpaper-$revision.jpg").takeIf { it.exists() } ?: saved
        if (source.exists()) BitmapFactory.decodeFile(source.path) else null
    }
    suspend fun prune(keep: Set<Long>) = withContext(Dispatchers.IO) { mutex.withLock {
        context.filesDir.listFiles()?.forEach { file ->
            val revision = Regex("home-wallpaper-(\\d+)\\.jpg").matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            if (revision != null && revision !in keep) file.delete()
        }
    } }
    suspend fun apply(config: LauncherConfig, color: Int) = withContext(Dispatchers.IO) { mutex.withLock {
        val manager = WallpaperManager.getInstance(context)
        check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed)
        val w = config.wallpaper
        val signature = "${w.mode}:${w.photoRevision}:${w.color}:${w.gradientEnd}:$color"
        if (preferences.getString("signature", null) == signature && preferences.getInt("wallpaperId", -1) == manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)) return@withLock
        val bitmap = if (config.wallpaperMode == WallpaperMode.CUSTOM) requireNotNull(preview(w.photoRevision)) else {
            // A display-shaped source makes OEM recents/wallpaper pipelines replace their cached
            // photo thumbnail instead of treating a tiny solid bitmap as an unchanged placeholder.
            val metrics = context.resources.displayMetrics
            themeWallpaper(metrics.widthPixels, metrics.heightPixels, if (w.mode == WallpaperMode.THEME) color else w.color.toInt()).apply {
                if (w.mode == WallpaperMode.GRADIENT) Canvas(this).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, height.toFloat(), w.color.toInt(), w.gradientEnd.toInt(), Shader.TileMode.CLAMP)
                })
            }
        }
        try {
            // FLAG_SYSTEM preserves an existing lock wallpaper, including a shared static wallpaper.
            val id = manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
            check(id > 0)
            preferences.edit().putString("signature", signature).putInt("wallpaperId", id).apply()
        } finally { bitmap.recycle() }
    } }
}
