package org.openminimal.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.openminimal.launcher.R
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.InstalledApp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun HorizontalPlacement.alignment(): Alignment.Horizontal = when (this) {
    HorizontalPlacement.START -> Alignment.Start
    HorizontalPlacement.CENTER -> Alignment.CenterHorizontally
    HorizontalPlacement.END -> Alignment.End
}

/** Visual-only application entry. Real app actions and previews wrap this exact renderer. */
@Composable internal fun AppEntryContent(app: InstalledApp, config: LauncherConfig, layout: AppLayoutConfig,
    modifier: Modifier = Modifier, favorite: Boolean = false) {
    val icon = rememberStyledIcon(app.icon, config.blackWhiteIcons)
    val monochrome = grayscaleFilter(config.monochromeIcons && !config.blackWhiteIcons)
    if (layout.isGrid) {
        Column(modifier.fillMaxWidth().heightIn(min = (layout.grid.iconSize + if (layout.grid.showLabels) 56 else 28).dp)
            .padding(vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = app.label },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            Image(icon.asImageBitmap(), null, Modifier.size(layout.grid.iconSize.dp).clip(MaterialTheme.shapes.small), colorFilter = monochrome)
            if (layout.grid.showLabels) Text(app.label, Modifier.fillMaxWidth().padding(horizontal = 2.dp), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    } else {
        val l = layout.list
        Row(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (layout.showIcons) {
                Image(icon.asImageBitmap(), null, Modifier.size(l.iconSize.dp).clip(MaterialTheme.shapes.small), colorFilter = monochrome)
                Spacer(Modifier.width(l.iconGap.dp))
            }
            Text(app.label, Modifier.weight(1f), fontSize = l.textSize.sp, lineHeight = (l.textSize * 1.3f).sp,
                fontWeight = FontWeight(l.weight.value), maxLines = 1, overflow = TextOverflow.Ellipsis,
                // Alignment places the complete list block on Home. Keep every row internally
                // start-aligned so icons, labels and the configured gap form two stable columns.
                textAlign = TextAlign.Start)
            if (favorite) Text("•", Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Shared clock/date presentation; previews have no click callback or platform side effects. */
@Composable internal fun HomeClock(config: LauncherConfig, now: LocalDateTime, modifier: Modifier = Modifier, openClock: (() -> Unit)? = null) {
    val c = config.home.clock
    val locale = LocalConfiguration.current.locales[0]
    val is24 = when(c.format) { ClockFormat.SYSTEM -> DateFormat.is24HourFormat(LocalContext.current); ClockFormat.TWELVE_HOUR -> false; ClockFormat.TWENTY_FOUR_HOUR -> true }
    Column(modifier.fillMaxWidth().padding(bottom = c.bottomSpacing.dp), horizontalAlignment = if (c.alignment == ClockAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start) {
        if (config.showHomeHeading) {
            Text(config.homeHeading.ifBlank { stringResource(R.string.home_eyebrow) }, Modifier.testTag("home_heading"),
                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
        if (c.visible) BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = if (c.alignment == ClockAlignment.CENTER) Alignment.Center else Alignment.CenterStart) {
            val factor = if (c.font == ClockFont.MONOSPACE) 3.1f else 2.8f
            val size = minOf(c.size.toFloat(), maxWidth.value / (factor * LocalDensity.current.fontScale)).coerceAtLeast(24f)
            Text(now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", locale)),
                fontSize = size.sp, lineHeight = (size * 1.18f).sp, letterSpacing = (-2).sp,
                fontFamily = if (c.font == ClockFont.MONOSPACE) FontFamily.Monospace else FontFamily.SansSerif,
                fontWeight = FontWeight(c.weight.value), maxLines = 1, softWrap = false,
                modifier = Modifier.testTag("home_clock").then(if (openClock == null) Modifier else Modifier.clickable(onClickLabel = stringResource(R.string.open_clock), onClick = openClock)))
        }
        if (c.showDate) {
            val date = when(c.dateStyle) { DateStyle.LONG -> "d MMMM"; DateStyle.SHORT -> "d MMM"; DateStyle.NUMERIC -> "dd.MM.yyyy" }
            Text(now.format(DateTimeFormatter.ofPattern((if (c.showWeekday) "EEEE, " else "") + date, locale)),
                Modifier.testTag("home_date"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun PersonalizationPreview(config: LauncherConfig, apps: List<InstalledApp>, home: Boolean, modifier: Modifier = Modifier) {
    val layout = if (home) config.home.apps else config.drawer.apps
    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.live_preview), style = MaterialTheme.typography.labelLarge)
        val previewHeight = (LocalWindowInfo.current.containerSize.height / LocalDensity.current.density * .28f).coerceIn(100f, 220f)
        Surface(Modifier.fillMaxWidth().height(previewHeight.dp).testTag("live_preview"), shape = MaterialTheme.shapes.extraLarge,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * .68f, density.fontScale)) {
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge)) {
                    if (home) HomeWallpaper(config)
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        if (home) HomeClock(config, LocalDateTime.now())
                        if (layout.isGrid) Row(Modifier.fillMaxWidth()) {
                            apps.take(layout.grid.columns).forEach { AppEntryContent(it, config, layout, Modifier.weight(1f)) }
                        } else Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = when(layout.list.placement) {
                            VerticalPlacement.TOP -> Alignment.TopStart; VerticalPlacement.CENTER -> Alignment.CenterStart; VerticalPlacement.BOTTOM -> Alignment.BottomStart
                        }) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = layout.list.alignment.alignment()) {
                                apps.take(minOf(2, layout.list.maxItems)).forEach { app ->
                                    AppEntryContent(app, config, layout, Modifier.fillMaxWidth(layout.list.widthPercent / 100f))
                                    Spacer(Modifier.height(layout.list.rowSpacing.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
