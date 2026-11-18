package app.novaos.ui.launcher

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novaos.design.DotMatrixText
import app.novaos.design.Nova
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaSlider
import app.novaos.design.NovaSurface
import app.novaos.design.hexColor
import app.novaos.model.CcGrid
import app.novaos.model.CcLayout
import app.novaos.model.CcTile
import app.novaos.model.CcTileType
import app.novaos.model.DotIntensity
import app.novaos.system.MediaHub
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import app.novaos.widgets.formatDate
import app.novaos.widgets.formatTime
import app.novaos.widgets.rememberNow

fun tileIcon(t: CcTileType): ImageVector = when (t) {
    CcTileType.WIFI -> Icons.Filled.Wifi
    CcTileType.BLUETOOTH -> Icons.Filled.Bluetooth
    CcTileType.AIRPLANE -> Icons.Filled.AirplanemodeActive
    CcTileType.INTERNET -> Icons.Filled.Language
    CcTileType.NFC -> Icons.Filled.Nfc
    CcTileType.LOCATION -> Icons.Filled.LocationOn
    CcTileType.HOTSPOT -> Icons.Filled.WifiTethering
    CcTileType.FLASHLIGHT -> Icons.Filled.FlashlightOn
    CcTileType.DND -> Icons.Filled.DoNotDisturbOn
    CcTileType.ROTATION -> Icons.Filled.ScreenRotation
    CcTileType.BRIGHTNESS -> Icons.Filled.BrightnessMedium
    CcTileType.VOLUME -> Icons.Filled.VolumeUp
    CcTileType.MEDIA -> Icons.Filled.MusicNote
    CcTileType.BATTERY -> Icons.Filled.BatteryFull
    CcTileType.SETTINGS -> Icons.Filled.Settings
    CcTileType.NOVA_SETTINGS -> Icons.Filled.Tune
    CcTileType.LOCK -> Icons.Filled.Lock
    CcTileType.PROFILE -> Icons.Filled.Person
    CcTileType.SHORTCUT -> Icons.Filled.Star
}

/** Nova Control Center: eigenes Panel innerhalb von NovaOS (kein Ersatz für die Android-Schnelleinstellungen). */
@Composable
fun ControlCenterPanel(vm: LauncherViewModel, actions: LauncherActions) {
    val c = LocalContainer.current
    val profile = LocalProfile.current
    val style = profile.theme.controlCenter
    val state by c.controls.state.collectAsState()
    val now by rememberNow()
    LaunchedEffect(Unit) { c.controls.refresh() }
    val columns = when (style.layout) {
        CcLayout.COMPACT -> 5
        CcLayout.ANDROID_CLASSIC -> 2
        else -> style.columns.coerceIn(2, 6)
    }
    val tiles = profile.controlCenterTiles
    val placed = remember(tiles, columns) { CcGrid.arrange(tiles.map { if (style.layout == CcLayout.ANDROID_CLASSIC) it.copy(span = if (it.type in wideTypes) 2 else 1, height = if (it.type == CcTileType.MEDIA) 2 else 1) else it }, columns) }
    val rows = CcGrid.rowCount(placed)
    val accent = hexColor(style.activeColor, Nova.colors.accent)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -30) vm.close() } },
    ) {
        NovaSurface(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(10.dp),
            RoundedCornerShape(32.dp), style.background, style.transparency, shadow = 0.5f,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (Nova.style.dotsAtLeast(DotIntensity.BALANCED)) DotMatrixText(formatTime(now, true), dotSize = 3.dp, color = Nova.colors.text)
                        else Text(formatTime(now, true), style = Nova.type.headline, color = Nova.colors.text)
                        Text(formatDate(now), style = Nova.type.caption, color = Nova.colors.textSecondary)
                    }
                    NovaIconButton(Icons.Filled.Edit, tr("Kontrollzentrum bearbeiten", "Edit control center"), { actions.openSettings("control_center") })
                    NovaIconButton(Icons.Filled.Settings, tr("Android-Einstellungen", "Android settings"), { actions.startSafely(Intent(android.provider.Settings.ACTION_SETTINGS)) })
                }
                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = if (style.layout == CcLayout.COMPACT) 8.dp else 10.dp
                    val cell = (maxWidth - gap * (columns - 1)) / columns
                    val rowH = when (style.layout) {
                        CcLayout.COMPACT -> cell
                        CcLayout.ANDROID_CLASSIC -> 64.dp
                        CcLayout.GLASS_PANEL -> cell * 0.82f
                        else -> cell
                    }
                    Box(Modifier.fillMaxWidth().height(rowH * rows + gap * (rows - 1).coerceAtLeast(0))) {
                        placed.forEach { p ->
                            Box(
                                Modifier.offset((cell + gap) * p.col, (rowH + gap) * p.row)
                                    .size(cell * p.span + gap * (p.span - 1), rowH * p.height + gap * (p.height - 1)),
                            ) { Tile(p.tile, style.layout, style.tileRoundness, accent, actions, state, rowH) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("WLAN, Bluetooth, Flugmodus, NFC und Standort öffnen die Android-Schnellpanels – Apps dürfen sie nicht direkt schalten.",
                        "Wi-Fi, Bluetooth, airplane mode, NFC and location open Android panels – apps may not toggle them directly."),
                    style = Nova.type.caption, color = Nova.colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Nova.colors.text.copy(alpha = 0.3f)))
            }
        }
    }
}

private val wideTypes = setOf(CcTileType.BRIGHTNESS, CcTileType.VOLUME, CcTileType.MEDIA)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(tile: CcTile, layout: CcLayout, roundness: Float, accent: Color, actions: LauncherActions, state: app.novaos.system.ControlState, rowH: Dp) {
    val c = LocalContainer.current
    val config = LocalConfig.current
    val colors = Nova.colors
    val type = tile.type
    val active = c.controls.isActive(type, state)
    val needsPermission = !c.controls.permissionGranted(type.permission)
    val label = tile.label ?: when (type) {
        CcTileType.PROFILE -> config.activeProfile.name
        CcTileType.BATTERY -> "${c.systemInfo.battery.collectAsState().value.level} %"
        else -> type.label
    }
    val bg by animateColorAsState(if (active) accent else colors.text.copy(alpha = if (layout == CcLayout.GLASS_PANEL) 0.12f else 0.09f), label = "tile")
    val fg = if (active) colors.onAccent else colors.text
    val shape: Shape = RoundedCornerShape(percent = (roundness.coerceIn(0f, 1f) * 50).toInt())

    when (type) {
        CcTileType.BRIGHTNESS, CcTileType.VOLUME -> {
            if (tile.span >= 2) {
                SliderTile(type, if (type == CcTileType.BRIGHTNESS) state.brightness else state.volume, shape, needsPermission) { v ->
                    if (type == CcTileType.BRIGHTNESS) c.controls.setBrightness(v)?.let { actions.startSafely(it) } else c.controls.setVolume(v)
                }
                return
            }
        }
        CcTileType.MEDIA -> if (tile.span >= 2) { MediaTile(shape); return }
        else -> Unit
    }

    val onClick: () -> Unit = {
        when (type) {
            CcTileType.NOVA_SETTINGS -> actions.openSettings(null)
            CcTileType.PROFILE -> c.controller.nextProfile()
            CcTileType.SHORTCUT -> tile.target?.let { actions.openTarget(it) }
            else -> c.controls.toggle(type)?.let { actions.startSafely(it) }
        }
    }
    val onLong: () -> Unit = {
        val intent = when (type) {
            CcTileType.WIFI -> Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            CcTileType.BLUETOOTH -> Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            CcTileType.DND -> Intent(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
            CcTileType.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            CcTileType.BRIGHTNESS, CcTileType.ROTATION -> Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
            else -> null
        }
        intent?.let { actions.startSafely(it) }
    }
    val icon = if (type == CcTileType.BATTERY && c.systemInfo.battery.collectAsState().value.charging) Icons.Filled.BatteryChargingFull else tileIcon(type)
    val semantics = Modifier.semantics { contentDescription = label; stateDescription = if (active) tr("an", "on") else tr("aus", "off") }

    when (layout) {
        CcLayout.NOTHING_GRID, CcLayout.COMPACT, CcLayout.CUSTOM -> {
            val round = if (layout == CcLayout.CUSTOM) shape else if (tile.span > 1) RoundedCornerShape(percent = 50) else CircleShape
            Column(Modifier.fillMaxSize().then(semantics), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(round).background(bg).combinedClickable(onClick = onClick, onLongClick = onLong),
                    contentAlignment = Alignment.Center,
                ) {
                    if (tile.span > 1 && layout != CcLayout.COMPACT) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = fg)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, color = fg, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(statusText(type, state, active), color = fg.copy(alpha = 0.7f), style = Nova.type.caption, maxLines = 1)
                            }
                        }
                    } else Icon(icon, label, tint = fg, modifier = Modifier.size(if (layout == CcLayout.COMPACT) 20.dp else 24.dp))
                    if (needsPermission) Icon(Icons.Filled.LockOpen, null, tint = colors.accent, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(12.dp))
                }
                if (tile.span == 1 && layout != CcLayout.COMPACT) Text(label, style = Nova.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        CcLayout.GLASS_PANEL, CcLayout.ANDROID_CLASSIC -> {
            val sh = if (layout == CcLayout.ANDROID_CLASSIC) RoundedCornerShape(percent = (roundness * 50).toInt().coerceAtLeast(20)) else RoundedCornerShape((roundness * 28).dp + 8.dp)
            Box(Modifier.fillMaxSize().clip(sh).background(bg).combinedClickable(onClick = onClick, onLongClick = onLong).then(semantics).padding(12.dp)) {
                if (layout == CcLayout.ANDROID_CLASSIC || tile.span > 1) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = fg)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, color = fg, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(statusText(type, state, active), color = fg.copy(alpha = 0.7f), style = Nova.type.caption, maxLines = 1)
                        }
                    }
                } else {
                    Icon(icon, null, tint = fg, modifier = Modifier.align(Alignment.TopStart))
                    Text(label, color = fg, style = Nova.type.caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                }
                if (needsPermission) Icon(Icons.Filled.LockOpen, tr("Freigabe nötig", "Permission needed"), tint = colors.accent, modifier = Modifier.align(Alignment.TopEnd).size(14.dp))
            }
        }
    }
}

private fun statusText(t: CcTileType, s: app.novaos.system.ControlState, active: Boolean): String = when (t) {
    CcTileType.WIFI, CcTileType.INTERNET -> if (s.wifiConnected) tr("Verbunden", "Connected") else tr("Nicht verbunden", "Not connected")
    CcTileType.BLUETOOTH -> if (s.bluetoothOn) tr("An", "On") else tr("Aus", "Off")
    CcTileType.NFC -> if (!s.nfcAvailable) tr("Nicht vorhanden", "Unavailable") else if (s.nfcOn) tr("An", "On") else tr("Aus", "Off")
    CcTileType.BRIGHTNESS -> if (s.autoBrightness) tr("Automatisch", "Automatic") else "${(s.brightness * 100).toInt()} %"
    else -> if (active) tr("An", "On") else tr("Aus", "Off")
}

@Composable
private fun SliderTile(type: CcTileType, value: Float, shape: Shape, needsPermission: Boolean, onChange: (Float) -> Unit) {
    val colors = Nova.colors
    Row(
        Modifier.fillMaxSize().clip(shape).background(colors.text.copy(alpha = 0.09f)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tileIcon(type), type.label, tint = colors.text)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { NovaSlider(value, onChange, description = type.label) }
        if (needsPermission) Icon(Icons.Filled.LockOpen, tr("Freigabe nötig", "Permission needed"), tint = colors.accent, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MediaTile(shape: Shape) {
    val colors = Nova.colors
    val m by MediaHub.state.collectAsState()
    val art = remember(m?.art) { m?.art?.asImageBitmap() }
    Row(
        Modifier.fillMaxSize().clip(shape).background(colors.text.copy(alpha = 0.09f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(colors.text.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Filled.MusicNote, null, tint = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m?.title ?: tr("Keine Wiedergabe", "Nothing playing"), style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m?.artist.orEmpty(), style = Nova.type.caption, color = colors.textSecondary, maxLines = 1)
        }
        NovaIconButton(Icons.Filled.SkipPrevious, tr("Zurück", "Previous"), { MediaHub.previous() }, size = 38.dp)
        NovaIconButton(if (m?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (m?.playing == true) "Pause" else "Play", { MediaHub.playPause() }, size = 42.dp)
        NovaIconButton(Icons.Filled.SkipNext, tr("Weiter", "Next"), { MediaHub.next() }, size = 38.dp)
    }
}
