package org.openminimal.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openminimal.launcher.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.WidgetSlot
import java.text.Collator

private data class WidgetChoice(val info: AppWidgetProviderInfo, val title: String, val appName: String)

@Composable
internal fun WidgetPicker(activity: MainActivity, close: () -> Unit, grouped: Boolean = true, monochrome: Boolean = false, choose: (AppWidgetProviderInfo) -> Unit) {
    var providers by remember { mutableStateOf<List<WidgetChoice>?>(null) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        providers = withContext(Dispatchers.IO) {
            val pm = activity.packageManager
            AppWidgetManager.getInstance(activity).installedProviders.mapNotNull { info -> runCatching {
                WidgetChoice(info, info.loadLabel(pm), pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString())
            }.getOrNull() }.sortedWith(compareBy(Collator.getInstance()) { it.title })
        }
    }
    val groups = remember(providers, query) { providers.orEmpty().filter { it.title.contains(query, true) || it.appName.contains(query, true) }
        .groupBy { it.info.provider.packageName }.values.sortedWith(compareBy(Collator.getInstance()) { it.first().appName }) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.testTag("widget_picker").safeDrawingPadding().padding(horizontal = 24.dp)) {
                Header(stringResource(R.string.choose_widget), close)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.search_widgets)) })
                LazyColumn(
                    modifier = Modifier.testTag("widget_picker_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    if (providers == null) item { CircularProgressIndicator() }
                    else if (groups.isEmpty()) item { MutedText(stringResource(R.string.widget_empty)) }
                    groups.forEach { choices ->
                        val packageName = choices.first().info.provider.packageName
                        val show = !grouped || query.isNotBlank() || packageName in expanded
                        if (grouped) item(key = "app:$packageName") {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag("widget_group:$packageName").clickable { expanded = if (packageName in expanded) expanded - packageName else expanded + packageName }) {
                                ListItem(headlineContent = { Text(choices.first().appName) }, supportingContent = { Text(pluralStringResource(R.plurals.widget_count, choices.size, choices.size)) }, trailingContent = { Icon(if (show) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null) })
                            }
                        }
                        if (show) items(choices, key = { it.info.provider.flattenToString() }) { choice ->
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable { choose(choice.info) }) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(choice.title, style = MaterialTheme.typography.titleMedium)
                                    WidgetPreview(choice.info, activity, monochrome)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, activity: MainActivity, monochrome: Boolean) {
    var preview by remember(info.provider) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(info.provider) {
        preview = withContext(Dispatchers.IO) { runCatching {
            info.loadPreviewImage(activity, activity.resources.displayMetrics.densityDpi)?.let { drawable ->
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val scale = minOf(1f, 640f / maxOf(w, h))
                drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
        val bitmap = preview
        if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.widget_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = if (monochrome) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
        else {
            val icon by produceState<Bitmap?>(null, info.provider) { value = withContext(Dispatchers.IO) { runCatching { info.loadIcon(activity, activity.resources.displayMetrics.densityDpi)?.toBitmap(64, 64) }.getOrNull() } }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp)) }
                MutedText(stringResource(R.string.widget_preview_missing))
            }
        }
    }
}

@Composable
internal fun WidgetCard(slot: WidgetSlot, model: LauncherViewModel, activity: MainActivity, monochrome: Boolean = false) {
    val info = remember(slot.widgetId) { AppWidgetManager.getInstance(activity).getAppWidgetInfo(slot.widgetId) }
    val monoPaint = remember(monochrome) { if (monochrome) android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) }) } else null }
    var editing by rememberSaveable(slot.widgetId) { mutableStateOf(false) }
    AppletCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(info?.loadLabel(activity.packageManager) ?: stringResource(R.string.widget_missing), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { editing = !editing }, modifier = Modifier.testTag("edit_widget:${slot.widgetId}")) { Icon(Icons.Default.Edit, stringResource(R.string.edit_widget)) }
        }
        if (info != null) {
            val density = LocalDensity.current.density
            val resizable = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
            val minHeight = ((if (resizable && info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) / density).toInt().coerceAtLeast(40)
            val maxHeight = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) (info.maxResizeHeight / density).toInt().coerceAtLeast(minHeight) else maxOf(500, minHeight)
            val height = if (resizable) slot.heightDp.coerceIn(minHeight, maxHeight) else (info.minHeight / density).toInt().coerceAtLeast(40)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth.value.toInt()
                val minimumWidth = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
                if (minimumWidth / density > width) MutedText(stringResource(R.string.widget_too_wide))
                else AndroidView(
                    factory = { activity.widgetHost.createView(activity, slot.widgetId, info).apply { setPadding(0, 0, 0, 0) } },
                    modifier = Modifier.testTag("widget_content:${slot.widgetId}").fillMaxWidth().height(height.dp),
                    update = { view ->
                        view.setLayerType(if (monochrome) android.view.View.LAYER_TYPE_HARDWARE else android.view.View.LAYER_TYPE_NONE, monoPaint)
                        val dimensions = width to height
                        if (view.tag != dimensions) {
                            if (Build.VERSION.SDK_INT >= 31) view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.toFloat(), height.toFloat())))
                            else { @Suppress("DEPRECATION") view.updateAppWidgetSize(Bundle(), width, height, width, height) }
                            view.tag = dimensions
                        }
                    },
                )
            }
            if (editing) {
                if (resizable) Row {
                    TextButton(onClick = { model.resizeWidget(slot.widgetId, (height - 60).coerceAtLeast(minHeight)) }, enabled = height > maxOf(100, minHeight)) { Text(stringResource(R.string.widget_smaller)) }
                    TextButton(onClick = { model.resizeWidget(slot.widgetId, (height + 60).coerceAtMost(maxHeight)) }, enabled = height < minOf(500, maxHeight)) { Text(stringResource(R.string.widget_larger)) }
                } else MutedText(stringResource(R.string.widget_fixed_size))
            }
        }
        if (editing) Row {
            TextButton(onClick = { activity.removeWidget(slot.widgetId) }) { Text(stringResource(R.string.remove)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editing = false }) { Text(stringResource(R.string.done_editing)) }
        }
    }
}
