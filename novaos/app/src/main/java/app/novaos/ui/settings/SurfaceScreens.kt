package app.novaos.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.novaos.data.FileStore
import app.novaos.design.ChoiceRow
import app.novaos.design.ColorPickerRow
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.SettingRow
import app.novaos.design.SliderRow
import app.novaos.design.SwitchRow
import app.novaos.model.CcLayout
import app.novaos.model.CcTile
import app.novaos.model.CcTileType
import app.novaos.model.GeneratedWallpaper
import app.novaos.model.GroupingStyle
import app.novaos.model.ShortcutTarget
import app.novaos.model.SurfaceBackground
import app.novaos.model.WallpaperSource
import app.novaos.model.WallpaperSpec
import app.novaos.model.defaultCcTiles
import app.novaos.model.newId
import app.novaos.system.NotificationHub
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ============================================================================ Kontrollzentrum

@Composable
fun ControlCenterSettingsScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val p = LocalProfile.current
    val s = p.theme.controlCenter
    val tiles = p.controlCenterTiles
    var pickApp by remember { mutableStateOf(false) }
    fun setTiles(t: List<CcTile>) = ctrl.editProfile { it.copy(controlCenterTiles = t) }
    SettingsScreen(tr("Kontrollzentrum", "Control center")) {
        Group(tr("Layout & Design", "Layout & design")) {
            ChoiceRow(tr("Layout", "Layout"), CcLayout.entries, s.layout, { it.label }, { v -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(layout = v)) } })
            SliderRow(tr("Spalten", "Columns"), s.columns.toFloat(), { v -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(columns = v.roundToInt())) } }, 2f..6f, { "${it.roundToInt()}" }, steps = 3)
            ChoiceRow(tr("Hintergrund", "Background"), SurfaceBackground.entries, s.background, { it.label }, { v -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(background = v)) } })
            SliderRow(tr("Transparenz", "Transparency"), s.transparency, { v -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(transparency = v)) } })
            SliderRow(tr("Rundung der Kacheln", "Tile roundness"), s.tileRoundness, { v -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(tileRoundness = v)) } })
            ColorPickerRow(tr("Farbe aktiver Kacheln", "Active tile color"), s.activeColor, { v -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(activeColor = v)) } }, allowNone = true, onNone = { ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(activeColor = null)) } })
        }
        Group(tr("Kacheln (Reihenfolge)", "Tiles (order)")) {
            tiles.forEachIndexed { i, t ->
                Column {
                    SettingRow(
                        t.label ?: t.type.label,
                        listOfNotNull(
                            if (t.type.direct) tr("Direkt schaltbar", "Direct toggle") else tr("Öffnet Android-Panel", "Opens Android panel"),
                            t.type.permission.takeIf { it != app.novaos.model.CcPermission.NONE }?.let { tr("Freigabe: ", "Needs: ") + it.label },
                        ).joinToString(" · "),
                        icon = app.novaos.ui.launcher.tileIcon(t.type),
                        trailing = {
                            Row {
                                if (i > 0) NovaIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Nach vorne", "Move earlier"), { setTiles(tiles.toMutableList().apply { add(i - 1, removeAt(i)) }) })
                                if (i < tiles.size - 1) NovaIconButton(Icons.AutoMirrored.Filled.ArrowForward, tr("Nach hinten", "Move later"), { setTiles(tiles.toMutableList().apply { add(i + 1, removeAt(i)) }) })
                                NovaIconButton(Icons.Filled.Delete, tr("Entfernen", "Remove"), { setTiles(tiles.filter { it.id != t.id }) })
                            }
                        },
                    )
                    Row(Modifier.padding(start = 64.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4).forEach { span ->
                            Text("${span}×", color = if (t.span == span) Nova.colors.accent else Nova.colors.textSecondary, style = Nova.type.caption,
                                modifier = Modifier.clickable { setTiles(tiles.map { if (it.id == t.id) it.copy(span = span) else it }) }.padding(6.dp))
                        }
                        Text(if (t.height == 2) tr("Hoch", "Tall") else tr("Normal", "Normal"), color = Nova.colors.textSecondary, style = Nova.type.caption,
                            modifier = Modifier.clickable { setTiles(tiles.map { if (it.id == t.id) it.copy(height = if (it.height == 2) 1 else 2) else it }) }.padding(6.dp))
                    }
                }
            }
        }
        Group(tr("Kachel hinzufügen", "Add tile")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcTileType.entries.filter { it != CcTileType.SHORTCUT }.forEach { type ->
                    NovaButton(type.label, { setTiles(tiles + CcTile(newId("cc"), type, span = if (type == CcTileType.BRIGHTNESS || type == CcTileType.VOLUME || type == CcTileType.MEDIA) 4 else 1)) }, primary = false, icon = Icons.Filled.Add)
                }
            }
            SettingRow(tr("Eigener Schnellzugriff (App)", "Own shortcut (app)"), icon = Icons.Filled.Add, onClick = { pickApp = true })
            SettingRow(tr("Standard wiederherstellen", "Restore defaults"), onClick = { setTiles(defaultCcTiles()) })
        }
        InfoText(tr("Android erlaubt normalen Apps nicht, WLAN, Bluetooth, Flugmodus, NFC oder Standort direkt zu schalten. Diese Kacheln öffnen das offizielle Systempanel. Helligkeit und Drehen benötigen „Systemeinstellungen ändern“, Nicht stören den „Nicht stören“-Zugriff, Sperren den Geräteadministrator.",
            "Android does not allow apps to toggle Wi-Fi, Bluetooth, airplane mode, NFC or location directly; those tiles open the system panel."))
    }
    if (pickApp) AppPickerDialog(tr("App für Schnellzugriff", "App for shortcut"), { pickApp = false }) { k ->
        setTiles(tiles + CcTile(newId("cc"), CcTileType.SHORTCUT, target = ShortcutTarget.App(k), label = c.apps.find(k)?.label))
        pickApp = false
    }
}

// ============================================================================ Benachrichtigungen

@Composable
fun NotificationSettingsScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val n = LocalProfile.current.theme.notifications
    val ctx = LocalContext.current
    fun e(f: (app.novaos.model.NotificationSpec) -> app.novaos.model.NotificationSpec) = ctrl.editTheme { it.copy(notifications = f(it.notifications)) }
    SettingsScreen(tr("Benachrichtigungen", "Notifications")) {
        PermissionBadge(tr("Benachrichtigungszugriff", "Notification access"), NotificationHub.isAccessGranted(ctx)) { c.apps.startIntent(NotificationHub.accessSettingsIntent(ctx)) }
        InfoText(tr("Das Nova Notification Center ist eine eigene Oberfläche (Wischgeste). Den geschützten Android-Benachrichtigungsschatten kann ein Launcher nicht ersetzen – er bleibt zusätzlich verfügbar.",
            "The Nova Notification Center is an own surface; Android’s protected notification shade stays available."))
        Group(tr("Karten", "Cards")) {
            SliderRow(tr("Kartenform (Radius)", "Card shape (radius)"), n.cardCornerDp, { v -> e { it.copy(cardCornerDp = v) } }, 0f..36f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Kartenabstand", "Card spacing"), n.cardSpacingDp, { v -> e { it.copy(cardSpacingDp = v) } }, 0f..24f, { "${it.roundToInt()} dp" })
            ChoiceRow(tr("Hintergrund", "Background"), SurfaceBackground.entries, n.background, { it.label }, { v -> e { it.copy(background = v) } })
            SliderRow(tr("Transparenz", "Transparency"), n.transparency, { v -> e { it.copy(transparency = v) } })
            SliderRow(tr("Schriftgröße", "Font size"), n.fontScale, { v -> e { it.copy(fontScale = v) } }, 0.7f..1.5f, { "${(it * 100).roundToInt()} %" })
            SliderRow(tr("Icon-Größe", "Icon size"), n.iconSizeDp, { v -> e { it.copy(iconSizeDp = v) } }, 20f..56f, { "${it.roundToInt()} dp" })
        }
        Group(tr("Verhalten", "Behavior")) {
            ChoiceRow(tr("Gruppierung", "Grouping"), GroupingStyle.entries, n.grouping, { it.label }, { v -> e { it.copy(grouping = v) } })
            SwitchRow(tr("Nachrichtenvorschau", "Message preview"), null, n.showPreviews, { on -> e { it.copy(showPreviews = on) } })
            SwitchRow(tr("Neueste zuerst", "Newest first"), null, n.newestFirst, { on -> e { it.copy(newestFirst = on) } })
            ChoiceRow(tr("Animation", "Animation"), app.novaos.model.AnimationStyle.entries, n.animation, { it.label }, { v ->
                ctrl.editTheme { t -> t.copy(notifications = t.notifications.copy(animation = v), animation = t.animation.copy(overrides = t.animation.overrides + (app.novaos.model.AnimArea.NOTIFICATIONS to v))) }
            })
            SwitchRow(tr("App-Badges", "App badges"), tr("Zähler an App-Icons", "Counters on icons"), c.config.config.value?.general?.showNotificationBadges ?: true, { on -> ctrl.editGeneral { it.copy(showNotificationBadges = on) } })
        }
    }
}

// ============================================================================ Wallpaper

@Composable
fun WallpaperScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val p = LocalProfile.current
    val w = p.wallpaper
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Int?>(null) } // null = alle Seiten
    fun e(f: (WallpaperSpec) -> WallpaperSpec) = ctrl.editProfile { it.copy(wallpaper = f(it.wallpaper)) }
    fun setSource(s: WallpaperSource) = e { spec -> if (target == null) spec.copy(source = s) else spec.copy(perPage = spec.perPage + (target!! to s)) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { c.files.importImage(uri, FileStore.WALLPAPERS, 2560) }
            if (path != null) setSource(WallpaperSource.Image(path))
        }
    }
    val current = if (target == null) w.source else w.perPage[target!!] ?: w.source
    val accent = p.theme.colors.dark.accent
    SettingsScreen(tr("Wallpaper Engine", "Wallpaper engine"), preview = true) {
        Group(tr("Gilt für", "Applies to")) {
            ChoiceRow(tr("Seite", "Page"), listOf<Int?>(null) + p.layout.pages.indices.toList(), target, { it?.let { i -> tr("Seite ${i + 1}", "Page ${i + 1}") } ?: tr("Alle Seiten", "All pages") }, { target = it })
            if (target != null && w.perPage.containsKey(target!!)) SettingRow(tr("Eigenen Hintergrund dieser Seite entfernen", "Remove page wallpaper"), onClick = { e { it.copy(perPage = it.perPage - target!!) } })
        }
        Group(tr("Quelle", "Source")) {
            SettingRow(tr("Eigenes Bild importieren", "Import own image"), tr("Fotoauswahl – keine Speicherberechtigung nötig", "Photo picker – no storage permission"), icon = Icons.Filled.Photo,
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            SettingRow(tr("System-Hintergrund verwenden", "Use system wallpaper"), tr("Glaseffekte nutzen dann eine Tönung statt Unschärfe", "Glass uses tint instead of blur"), onClick = { setSource(WallpaperSource.System) })
            ColorPickerRow(tr("Einfarbig", "Solid color"), (current as? WallpaperSource.Solid)?.color, { setSource(WallpaperSource.Solid(it)) })
        }
        Group(tr("NovaOS-Designs", "NovaOS designs")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GeneratedWallpaper.entries.forEach { g ->
                    val src = WallpaperSource.Generated(g, "#000000", "#2A2A2E", accent)
                    val bmp = produceState<ImageBitmap?>(null, g, accent) { value = withContext(Dispatchers.Default) { c.wallpapers.generate(src, 120, 260).asImageBitmap() } }.value
                    val sel = (current as? WallpaperSource.Generated)?.pattern == g
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { setSource(src) }) {
                        Box(Modifier.size(60.dp, 130.dp).clip(RoundedCornerShape(14.dp)).border(if (sel) 2.dp else 1.dp, if (sel) Nova.colors.accent else Nova.colors.border, RoundedCornerShape(14.dp))) {
                            if (bmp != null) Image(bmp, g.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(g.label, style = Nova.type.caption, color = Nova.colors.textSecondary)
                    }
                }
            }
            (current as? WallpaperSource.Generated)?.let { gsrc ->
                ColorPickerRow(tr("Grundfarbe", "Base color"), gsrc.colorA, { setSource(gsrc.copy(colorA = it)) })
                ColorPickerRow(tr("Zweite Farbe", "Second color"), gsrc.colorB, { setSource(gsrc.copy(colorB = it)) })
                ColorPickerRow(tr("Akzent", "Accent"), gsrc.accent, { setSource(gsrc.copy(accent = it)) })
                SliderRow(tr("Variation", "Variation"), gsrc.seed.toFloat(), { setSource(gsrc.copy(seed = it.roundToInt())) }, 1f..99f, { "${it.roundToInt()}" })
            }
        }
        Group(tr("Zuschneiden & Effekte", "Crop & effects")) {
            SliderRow(tr("Zoom", "Zoom"), w.zoom, { v -> e { it.copy(zoom = v) } }, 1f..3f, { "%.2f×".format(it) })
            SliderRow(tr("Position horizontal", "Horizontal position"), w.offsetX, { v -> e { it.copy(offsetX = v) } }, -1f..1f, { "%.2f".format(it) })
            SliderRow(tr("Position vertikal", "Vertical position"), w.offsetY, { v -> e { it.copy(offsetY = v) } }, -1f..1f, { "%.2f".format(it) })
            SliderRow(tr("Unschärfe", "Blur"), w.blur, { v -> e { it.copy(blur = v) } }, 0f..25f, { "${it.roundToInt()}" })
            SliderRow(tr("Helligkeit", "Brightness"), w.brightness, { v -> e { it.copy(brightness = v) } }, -1f..1f, { "%.2f".format(it) })
            SliderRow(tr("Kontrast", "Contrast"), w.contrast, { v -> e { it.copy(contrast = v) } }, 0.5f..1.5f, { "%.2f".format(it) })
            ColorPickerRow(tr("Farbüberlagerung", "Color overlay"), w.overlayColor, { v -> e { it.copy(overlayColor = v) } })
            SliderRow(tr("Stärke der Überlagerung", "Overlay strength"), w.overlayAlpha, { v -> e { it.copy(overlayAlpha = v) } })
            SettingRow(tr("Effekte zurücksetzen", "Reset effects"), onClick = { e { it.copy(zoom = 1f, offsetX = 0f, offsetY = 0f, blur = 0f, brightness = 0f, contrast = 1f, overlayAlpha = 0f) } })
        }
        Group(tr("Dynamische Farben", "Dynamic colors")) {
            SwitchRow(tr("Akzent aus dem Hintergrund", "Accent from wallpaper"), null, p.theme.colors.dynamicFromWallpaper, { on -> ctrl.editTheme { it.copy(colors = it.colors.copy(dynamicFromWallpaper = on)) } })
        }
        Group(tr("Android", "Android")) {
            SettingRow(tr("Auch als System-Hintergrund setzen", "Also set as system wallpaper"), tr("Für Sperrbildschirm und andere Apps (offizielle WallpaperManager-API)", "For lock screen and other apps"), onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        val dm = ctx.resources.displayMetrics
                        val r = c.wallpapers.render(w, w.source, dm.widthPixels, dm.heightPixels) ?: return@withContext false
                        runCatching { android.app.WallpaperManager.getInstance(ctx).setBitmap(r.sharp); true }.getOrDefault(false)
                    }
                    android.widget.Toast.makeText(ctx, if (ok) tr("System-Hintergrund gesetzt", "System wallpaper set") else tr("Nicht möglich (System-Hintergrund gewählt?)", "Not possible"), android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            InfoText(tr("Animierte Hintergründe gibt es bewusst nicht, um den Akku zu schonen.", "No animated wallpapers – to save battery."))
        }
    }
}
