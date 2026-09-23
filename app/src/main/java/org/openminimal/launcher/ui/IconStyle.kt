package org.openminimal.launcher.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

private val binaryIcons = object : LruCache<Bitmap, Bitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: Bitmap, value: Bitmap) = value.allocationByteCount
}

/** Quantize RGB to exactly black or white, retaining transparency and icon silhouettes. */
internal fun blackWhiteIcon(source: Bitmap): Bitmap {
    binaryIcons.get(source)?.let { return it }
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val luminance = ((pixel ushr 16 and 255) * 54 + (pixel ushr 8 and 255) * 183 + (pixel and 255) * 19) / 256
        pixels[i] = (pixel and -0x1000000) or if (luminance >= 128) 0x00ffffff else 0
    }
    return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888).also { binaryIcons.put(source, it) }
}

@Composable internal fun rememberStyledIcon(source: Bitmap, blackWhite: Boolean) = remember(source, blackWhite) {
    if (blackWhite) blackWhiteIcon(source) else source
}

@Composable internal fun grayscaleFilter(enabled: Boolean) = remember(enabled) {
    if (enabled) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
}
