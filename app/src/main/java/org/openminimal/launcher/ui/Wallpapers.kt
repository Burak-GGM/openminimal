package org.openminimal.launcher.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openminimal.launcher.LauncherViewModel
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.HomeWallpapers
import java.time.LocalDateTime

@Composable internal fun WallpaperSurface(wallpaper: WallpaperConfig, modifier: Modifier = Modifier, photo: Bitmap? = null) {
    Box(modifier.fillMaxSize()) {
        when(wallpaper.mode) {
            WallpaperMode.THEME -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            WallpaperMode.SOLID -> Box(Modifier.fillMaxSize().background(Color(wallpaper.color)))
            WallpaperMode.GRADIENT -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(wallpaper.color), Color(wallpaper.gradientEnd)))))
            WallpaperMode.CUSTOM -> photo?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        if (wallpaper.mode != WallpaperMode.THEME) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = wallpaper.overlayPercent / 100f)))
    }
}

@Composable internal fun HomeWallpaper(config: LauncherConfig) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(null, config.wallpaperMode, config.wallpaperRevision) {
        value = if (config.wallpaperMode == WallpaperMode.CUSTOM) HomeWallpapers(context).preview(config.wallpaperRevision) else null
    }
    WallpaperSurface(config.wallpaper, photo = photo)
}

@Composable internal fun WallpaperControls(config: LauncherConfig, model: LauncherViewModel) {
    var open by remember { mutableStateOf(false) }
    SettingSwitch(stringResource(R.string.sync_wallpaper), config.syncWallpaper) { v -> model.configure { it.copy(wallpaper = it.wallpaper.copy(syncSystem = v)) } }
    Text(stringResource(R.string.wallpaper_home_only), style = MaterialTheme.typography.labelMedium)
    Surface(Modifier.fillMaxWidth().height(260.dp), shape = MaterialTheme.shapes.extraLarge) {
        Box(Modifier.clip(MaterialTheme.shapes.extraLarge)) {
            HomeWallpaper(config)
            HomeClock(config, LocalDateTime.now(), Modifier.padding(24.dp))
        }
    }
    Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("choose_wallpaper")) { Text(stringResource(R.string.choose_wallpaper)) }
    if (open) WallpaperPicker(config, model) { open = false }
}

@Composable private fun WallpaperPicker(config: LauncherConfig, model: LauncherViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val service = remember(context) { HomeWallpapers(context) }
    val themeColor = MaterialTheme.colorScheme.background.toArgb()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val initialColors = launcherColorScheme(config.copy(darkBackground = DarkBackground.CHARCOAL), dark)
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var newPhoto by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(config.wallpaper) }
    LaunchedEffect(Unit) { if (!newPhoto) bitmap = service.preview(draft.photoRevision) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = false
            try { bitmap = service.decode(uri); newPhoto = true; draft = draft.copy(mode = WallpaperMode.CUSTOM) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(stringResource(R.string.wallpaper), { if (!busy) close() })
                Surface(Modifier.weight(1f).fillMaxWidth().testTag("wallpaper_preview"), shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.clip(MaterialTheme.shapes.extraLarge)) {
                        WallpaperSurface(draft, photo = bitmap)
                        HomeClock(config, LocalDateTime.now(), Modifier.padding(24.dp))
                    }
                }
                Column(Modifier.heightIn(max = 290.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EnumChips(draft.mode, WallpaperMode.entries, { when(it) {
                        WallpaperMode.THEME -> R.string.theme_wallpaper; WallpaperMode.CUSTOM -> R.string.choose_photo
                        WallpaperMode.SOLID -> R.string.wallpaper_solid; WallpaperMode.GRADIENT -> R.string.wallpaper_gradient
                    } }) { mode ->
                        if (!busy) {
                            if (mode == WallpaperMode.CUSTOM) picker.launch(arrayOf("image/*"))
                            else {
                                val enteringColor = (mode == WallpaperMode.SOLID || mode == WallpaperMode.GRADIENT) &&
                                    draft.mode != WallpaperMode.SOLID && draft.mode != WallpaperMode.GRADIENT
                                draft = if (enteringColor) draft.copy(mode = mode,
                                    color = initialColors.background.toArgb().toLong() and 0xFFFFFFFF,
                                    gradientEnd = initialColors.primaryContainer.toArgb().toLong() and 0xFFFFFFFF)
                                else draft.copy(mode = mode)
                            }
                        }
                    }
                    if (draft.mode == WallpaperMode.SOLID || draft.mode == WallpaperMode.GRADIENT) {
                        Text(stringResource(R.string.wallpaper_shade), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorPalette.entries.forEach { palette ->
                                val colors = launcherColorScheme(config.copy(palette = palette, darkBackground = DarkBackground.CHARCOAL), dark)
                                val color = colors.background.toArgb().toLong() and 0xFFFFFFFF
                                FilterChip(draft.color == color, { if (!busy) draft = draft.copy(color = color, gradientEnd = colors.primaryContainer.toArgb().toLong() and 0xFFFFFFFF) },
                                    label = { Text(stringResource(palette.title())) },
                                    leadingIcon = { Box(Modifier.size(18.dp).background(colors.primary, CircleShape)) })
                            }
                        }
                    }
                    if (draft.mode != WallpaperMode.THEME) IntSetting(R.string.wallpaper_scrim, draft.overlayPercent, 0..80, "wallpaper_overlay", { draft = draft.copy(overlayPercent = it) }, {})
                    Text(stringResource(R.string.wallpaper_home_only), style = MaterialTheme.typography.bodySmall)
                }
                if (error) Text(stringResource(R.string.wallpaper_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    scope.launch {
                        busy = true; error = false
                        try {
                            val value = if (draft.mode == WallpaperMode.CUSTOM && newPhoto) {
                                val revision = System.currentTimeMillis()
                                service.save(requireNotNull(bitmap), revision)
                                draft.copy(photoRevision = revision)
                            } else draft
                            if (value.syncSystem) service.apply(config.copy(wallpaper = value), themeColor)
                            check(model.commitConfiguration { it.copy(wallpaper = value) })
                            val current = model.state.filterNotNull().first { it.config.wallpaper == value }
                            service.prune((current.customPresets.map { it.config.wallpaper.photoRevision } + value.photoRevision).toSet())
                            close()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }, enabled = !busy && (draft.mode != WallpaperMode.CUSTOM || bitmap != null), modifier = Modifier.fillMaxWidth().testTag("apply_wallpaper")) {
                    Text(stringResource(if (busy) R.string.loading else R.string.apply_wallpaper))
                }
            }
        }
    }
}

internal fun ColorPalette.title() = when(this) {
    ColorPalette.SAGE -> R.string.color_sage; ColorPalette.MONOCHROME -> R.string.monochrome; ColorPalette.OCEAN -> R.string.color_ocean
    ColorPalette.SAND -> R.string.color_sand; ColorPalette.PLUM -> R.string.color_plum
}
