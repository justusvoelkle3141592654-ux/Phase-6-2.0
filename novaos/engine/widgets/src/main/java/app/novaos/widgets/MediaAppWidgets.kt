package app.novaos.widgets

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.novaos.design.DotBar
import app.novaos.design.Nova
import app.novaos.model.AppCategories
import app.novaos.model.ShortcutTarget
import app.novaos.model.WidgetInstance
import app.novaos.system.MediaHub
import app.novaos.system.MediaState

// ============================================================================ Medien

@Composable
private fun Art(state: MediaState?, modifier: Modifier) {
    val look = LocalWidgetLook.current
    val bmp = remember(state?.art) { state?.art?.asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(look.text.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.MusicNote, null, tint = look.secondary)
    }
}

@Composable
private fun Controls(state: MediaState?, size: Dp = 36.dp) {
    val look = LocalWidgetLook.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable
        fun B(icon: ImageVector, desc: String, primary: Boolean = false, onClick: () -> Unit) {
            Box(
                Modifier.size(size).clip(CircleShape).background(if (primary) look.text else Color.Transparent).clickable(onClickLabel = desc, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, desc, tint = if (primary) Nova.colors.background else look.text, modifier = Modifier.size(size * 0.55f)) }
        }
        B(Icons.Filled.SkipPrevious, if (germanLike) "Zurück" else "Previous") { MediaHub.previous() }
        B(if (state?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state?.playing == true) "Pause" else "Play", primary = true) { MediaHub.playPause() }
        B(Icons.Filled.SkipNext, if (germanLike) "Weiter" else "Next") { MediaHub.next() }
    }
}

@Composable
private fun mediaProgress(state: MediaState?): Float {
    val now by rememberNow(everySecond = state?.playing == true)
    if (state == null || state.durationMs <= 0) return 0f
    @Suppress("UNUSED_VARIABLE") val tick = now
    return (state.currentPosition(android.os.SystemClock.elapsedRealtime()) / state.durationMs.toFloat()).coerceIn(0f, 1f)
}

@Composable
fun BoxWithConstraintsScope.MusicWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val look = LocalWidgetLook.current
    val state by MediaHub.state.collectAsState()
    val ctx = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { MediaHub.refresh(ctx) }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Art(state, Modifier.fillMaxHeight().aspectRatio(1f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state?.title?.ifBlank { null } ?: (if (germanLike) "Keine Wiedergabe" else "Nothing playing"), style = look.title, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state?.artist.orEmpty(), style = look.body, color = look.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (H > 70.dp && state != null) {
                Spacer(Modifier.height(4.dp))
                DotBar(mediaProgress(state), Modifier.fillMaxWidth().height(6.dp), look.accent, look.text.copy(alpha = 0.12f), 30)
            }
        }
        Spacer(Modifier.width(8.dp))
        Controls(state, min(H * 0.55f, 38.dp))
    }
}

@Composable
fun BoxWithConstraintsScope.AlbumArtWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val look = LocalWidgetLook.current
    val state by MediaHub.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Art(state, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            Text(state?.title ?: (if (germanLike) "Keine Wiedergabe" else "Nothing playing"), style = look.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state?.artist.orEmpty(), style = look.body, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
            if (H > 160.dp && state != null) {
                Spacer(Modifier.height(4.dp))
                DotBar(mediaProgress(state), Modifier.fillMaxWidth().height(6.dp), look.accent, Color.White.copy(alpha = 0.2f), 30)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Controls(state) }
            }
        }
    }
}

@Composable
fun BoxWithConstraintsScope.PlaylistWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val look = LocalWidgetLook.current
    val state by MediaHub.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Text(if (germanLike) "Warteschlange" else "Queue", style = look.title, color = look.text)
        val q = state?.queue.orEmpty()
        if (q.isEmpty()) Text(
            if (state == null) (if (germanLike) "Keine Wiedergabe" else "Nothing playing") else (if (germanLike) "Die App stellt keine Warteschlange bereit" else "App provides no queue"),
            style = look.body, color = look.secondary,
        )
        q.take(look.maxItems + 1).forEachIndexed { i, title ->
            Text(
                "${i + 1}. $title", style = look.body.copy(fontWeight = if (title == state?.title) FontWeight.Bold else FontWeight.Normal),
                color = if (title == state?.title) look.accent else look.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { MediaHub.skipToQueueItem(i) }.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AppIconRow(keys: List<String>, emptyText: String) {
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    if (keys.isEmpty()) { Centered { Text(emptyText, style = look.body, color = look.secondary) }; return }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val perRow = (maxWidth.value / 64).toInt().coerceIn(1, 8)
        val rows = (maxHeight.value / 72).toInt().coerceAtLeast(1)
        val icon = min(maxWidth / perRow - 12.dp, maxHeight / rows - 12.dp).coerceIn(28.dp, 64.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            keys.take(perRow * rows).chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { k -> Box(Modifier.clickable(onClickLabel = host.apps.find(k)?.label) { host.actions.launchApp(k) }) { host.appIcon(k, icon) } }
                    repeat(perRow - row.size) { Spacer(Modifier.size(icon)) }
                }
            }
        }
    }
}

@Composable
fun BoxWithConstraintsScope.MediaLinksWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val apps by host.apps.apps.collectAsState()
    AppIconRow(apps.filter { it.category == AppCategories.MEDIA }.map { it.key }, if (germanLike) "Keine Medien-Apps gefunden" else "No media apps")
}

@Composable
fun BoxWithConstraintsScope.FavoriteAppsWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val apps by host.apps.apps.collectAsState()
    val category = instance.config["category"]
    val keys = when {
        category != null -> apps.filter { it.category == category }.map { it.key }
        instance.config["apps"] != null -> instance.config["apps"]!!.split('|').filter { k -> apps.any { it.key == k } }
        else -> host.favorites.filter { k -> apps.any { it.key == k } }
    }
    AppIconRow(keys, if (category != null) "Keine Apps in „$category“" else if (germanLike) "Favoriten im App Drawer festlegen" else "Set favorites in the drawer")
}

@Composable
fun BoxWithConstraintsScope.AppShortcutsWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val key = instance.config["app"]
    val version by host.apps.shortcutVersion.collectAsState()
    if (key == null) { Centered { Text(if (germanLike) "App im Widget-Editor wählen" else "Choose an app in the editor", style = look.body, color = look.secondary) }; return }
    val app = host.apps.find(key)
    if (!host.apps.hasShortcutAccess()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { host.appIcon(key, 32.dp); Spacer(Modifier.width(8.dp)); Text(app?.label.orEmpty(), style = look.title, color = look.text) }
            Text(if (germanLike) "Verknüpfungen erst als Standard-Startbildschirm verfügbar" else "Shortcuts require NovaOS as default home", style = look.body, color = look.secondary)
        }
        return
    }
    val list = remember(key, version) { host.apps.shortcuts(key) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) { host.appIcon(key, 28.dp); Spacer(Modifier.width(8.dp)); Text(app?.label.orEmpty(), style = look.title, color = look.text, maxLines = 1) }
        Spacer(Modifier.height(4.dp))
        if (list.isEmpty()) Text(if (germanLike) "Diese App bietet keine Verknüpfungen" else "No shortcuts", style = look.body, color = look.secondary)
        list.take(look.maxItems).forEach { s ->
            Text(
                s.shortLabel?.toString().orEmpty(), style = look.body, color = look.text, maxLines = 1,
                modifier = Modifier.fillMaxWidth().clickable { host.actions.openTarget(ShortcutTarget.DeepShortcut(s.`package`, s.id, app?.userSerial ?: 0)) }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun BoxWithConstraintsScope.AppInfoWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val ctx = LocalContext.current
    val key = instance.config["app"]
    val app = key?.let { host.apps.find(it) }
    if (app == null) { Centered { Text(if (germanLike) "App im Widget-Editor wählen" else "Choose an app", style = look.body, color = look.secondary) }; return }
    val info = remember(app.packageName) { runCatching { ctx.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull() }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable { host.actions.launchApp(app.key) }) { host.appIcon(app.key, min(H * 0.8f, 48.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = look.title, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${if (germanLike) "Version" else "Version"} ${info?.versionName ?: "–"}", style = look.body, color = look.secondary, maxLines = 1)
            if (look.detailed && info != null) Text("${if (germanLike) "Aktualisiert" else "Updated"} ${formatDate(info.lastUpdateTime, false)}", style = look.body, color = look.secondary, maxLines = 1)
        }
        Icon(Icons.Filled.Info, if (germanLike) "App-Info" else "App info", tint = look.text, modifier = Modifier.clickable { host.actions.openAppDetails(app.key) })
    }
}

/** Feste Systemverknüpfungen plus vom Benutzer gewählte Apps. */
@Composable
fun BoxWithConstraintsScope.AppLinksWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val links = listOf(
        Triple(Icons.Filled.Wifi, "WLAN", Settings.ACTION_WIFI_SETTINGS),
        Triple(Icons.Filled.Bluetooth, "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
        Triple(Icons.Filled.BatteryStd, if (germanLike) "Akku" else "Battery", Intent.ACTION_POWER_USAGE_SUMMARY),
        Triple(Icons.Filled.Smartphone, "Display", Settings.ACTION_DISPLAY_SETTINGS),
    )
    val extra = instance.config["apps"]?.split('|')?.filter { it.isNotBlank() }.orEmpty()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        links.take((4 - extra.size).coerceAtLeast(0) + if (W > 300.dp) 1 else 0).forEach { (icon, label, action) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { host.actions.openTarget(ShortcutTarget.SystemSettings(action, label)) }) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(look.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Icon(icon, label, tint = look.text) }
                Text(label, style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = look.secondary, maxLines = 1)
            }
        }
        extra.forEach { k -> Box(Modifier.clickable { host.actions.launchApp(k) }) { host.appIcon(k, 40.dp) } }
    }
}
