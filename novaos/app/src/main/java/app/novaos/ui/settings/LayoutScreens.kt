package app.novaos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.novaos.design.ChoiceRow
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaSurface
import app.novaos.design.NovaText
import app.novaos.design.NovaTextField
import app.novaos.design.SettingRow
import app.novaos.design.SliderRow
import app.novaos.design.SwitchRow
import app.novaos.model.AppSearch
import app.novaos.model.BackgroundEffect
import app.novaos.model.DockPosition
import app.novaos.model.DockShape
import app.novaos.model.DrawerFolder
import app.novaos.model.DrawerStyle
import app.novaos.model.FolderPreview
import app.novaos.model.GestureAction
import app.novaos.model.GestureClassifier
import app.novaos.model.GestureType
import app.novaos.model.HomeItem
import app.novaos.model.IconShape
import app.novaos.model.IndicatorStyle
import app.novaos.model.PageTransition
import app.novaos.model.ScrollBehavior
import app.novaos.model.SearchBarStyle
import app.novaos.model.SimpleGestureActions
import app.novaos.model.SortMode
import app.novaos.model.SurfaceBackground
import app.novaos.model.label
import app.novaos.model.newId
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import kotlin.math.roundToInt

/** Auswahl einer App (für Gesten, Widgets, Ordner). */
@Composable
fun AppPickerDialog(title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = LocalContainer.current
    val apps by c.apps.apps.collectAsState()
    var q by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        NovaSurface(Modifier.fillMaxWidth().heightIn(max = 560.dp), RoundedCornerShape(28.dp), SurfaceBackground.SOLID, 0f) {
            Column(Modifier.padding(16.dp)) {
                NovaText(title, Nova.type.headline)
                Spacer(Modifier.height(10.dp))
                NovaTextField(q, { q = it }, Modifier.fillMaxWidth(), tr("Suchen …", "Search …"))
                Spacer(Modifier.height(8.dp))
                val list = if (q.isBlank()) apps.map { it.toEntry() } else AppSearch.search(apps.map { it.toEntry() }, q)
                LazyColumn {
                    items(list, key = { it.key }) { a ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(a.key) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(a.key, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, style = Nova.type.body, color = Nova.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================ Homescreen

@Composable
fun HomeSettingsScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val p = LocalProfile.current
    val l = p.layout
    val home = p.theme.home
    val dock = p.theme.dock
    var pickDock by remember { mutableStateOf(false) }
    SettingsScreen(tr("Homescreen", "Home screen"), preview = true) {
        Group(tr("Raster", "Grid")) {
            SliderRow(tr("Spalten", "Columns"), l.columns.toFloat(), { v -> ctrl.setGrid(v.roundToInt(), l.rows) }, 3f..7f, { "${it.roundToInt()}" }, steps = 3)
            SliderRow(tr("Zeilen", "Rows"), l.rows.toFloat(), { v -> ctrl.setGrid(l.columns, v.roundToInt()) }, 4f..9f, { "${it.roundToInt()}" }, steps = 4)
            SwitchRow(tr("Freier Layoutmodus", "Free layout mode"), tr("Elemente unabhängig vom Raster positionieren und skalieren", "Place and size elements freely"), l.freeLayout, { on -> ctrl.editLayout { it.copy(freeLayout = on) } })
            SliderRow(tr("Icon-Größe", "Icon size"), l.iconSizeDp, { v -> ctrl.editLayout { it.copy(iconSizeDp = v) } }, 36f..84f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Icon-Abstände", "Icon spacing"), l.iconSpacingDp, { v -> ctrl.editLayout { it.copy(iconSpacingDp = v) } }, 0f..24f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Widget-Abstände", "Widget spacing"), l.widgetSpacingDp, { v -> ctrl.editLayout { it.copy(widgetSpacingDp = v) } }, 0f..32f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Seitenränder", "Side margins"), l.marginHorizontalDp, { v -> ctrl.editLayout { it.copy(marginHorizontalDp = v) } }, 0f..40f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Ränder oben/unten", "Top/bottom margins"), l.marginVerticalDp, { v -> ctrl.editLayout { it.copy(marginVerticalDp = v) } }, 0f..40f, { "${it.roundToInt()} dp" })
            SwitchRow(tr("App-Namen anzeigen", "Show app labels"), null, l.showLabels, { on -> ctrl.editLayout { it.copy(showLabels = on) } })
        }
        Group(tr("Seiten", "Pages")) {
            l.pages.forEachIndexed { i, page ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Seite ${i + 1}", "Page ${i + 1}") + if (i == l.defaultPage) tr(" (Start)", " (home)") else "", style = Nova.type.body, color = Nova.colors.text)
                        Text(tr("${page.items.size} Elemente", "${page.items.size} items"), style = Nova.type.caption, color = Nova.colors.textSecondary)
                    }
                    NovaIconButton(Icons.Filled.Home, tr("Als Startseite", "Set as home page"), { ctrl.editLayout { it.copy(defaultPage = i) } }, tint = if (i == l.defaultPage) Nova.colors.accent else Nova.colors.text)
                    NovaIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Nach links", "Move left"), { ctrl.movePage(i, i - 1) })
                    NovaIconButton(Icons.AutoMirrored.Filled.ArrowForward, tr("Nach rechts", "Move right"), { ctrl.movePage(i, i + 1) })
                    if (l.pages.size > 1) NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.removePage(i) })
                }
            }
            SettingRow(tr("Seite hinzufügen", "Add page"), onClick = { ctrl.addPage() })
        }
        Group(tr("Übergänge & Scrollen", "Transitions & scrolling")) {
            ChoiceRow(tr("Seitenübergang", "Page transition"), PageTransition.entries, home.pageTransition, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(pageTransition = v)) } })
            ChoiceRow(tr("Scrollverhalten", "Scroll behavior"), ScrollBehavior.entries, home.scroll, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(scroll = v)) } })
            SwitchRow(tr("Endlos blättern", "Loop pages"), null, home.loopPages, { on -> ctrl.editTheme { it.copy(home = it.home.copy(loopPages = on)) } })
            ChoiceRow(tr("Seitenindikator", "Page indicator"), IndicatorStyle.entries, home.indicator, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(indicator = v)) } })
        }
        Group(tr("Statusanzeigen & Hintergrund", "Status & background")) {
            SwitchRow(tr("NovaOS-Statuszeile", "NovaOS status line"), tr("Datum und Akku über dem Homescreen", "Date and battery above the home screen"), home.showStatusLine, { on -> ctrl.editTheme { it.copy(home = it.home.copy(showStatusLine = on)) } })
            if (home.showStatusLine) {
                SwitchRow(tr("Datum", "Date"), null, home.statusShowDate, { on -> ctrl.editTheme { it.copy(home = it.home.copy(statusShowDate = on)) } })
                SwitchRow(tr("Akku", "Battery"), null, home.statusShowBattery, { on -> ctrl.editTheme { it.copy(home = it.home.copy(statusShowBattery = on)) } })
            }
            ChoiceRow(tr("Hintergrundeffekt", "Background effect"), BackgroundEffect.entries, home.backgroundEffect, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(backgroundEffect = v)) } })
            InfoText(tr("Die Android-Statusleiste selbst kann ein Launcher nicht ersetzen; NovaOS ergänzt eine eigene Zeile.", "A launcher cannot replace Android’s status bar; NovaOS adds its own line."))
        }
        Group(tr("Dock", "Dock")) {
            ChoiceRow(tr("Position", "Position"), DockPosition.entries, dock.position, { it.label }, { v -> ctrl.editTheme { it.copy(dock = it.dock.copy(position = v)) } })
            ChoiceRow(tr("Form", "Shape"), DockShape.entries, dock.shape, { it.label }, { v -> ctrl.editTheme { it.copy(dock = it.dock.copy(shape = v)) } })
            ChoiceRow(tr("Hintergrund", "Background"), SurfaceBackground.entries, dock.background, { it.label }, { v -> ctrl.editTheme { it.copy(dock = it.dock.copy(background = v)) } })
            SliderRow(tr("Höhe", "Height"), dock.heightDp, { v -> ctrl.editTheme { it.copy(dock = it.dock.copy(heightDp = v)) } }, 52f..110f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Transparenz", "Transparency"), dock.transparency, { v -> ctrl.editTheme { it.copy(dock = it.dock.copy(transparency = v)) } })
            SliderRow(tr("Max. Apps", "Max apps"), dock.maxApps.toFloat(), { v -> ctrl.editTheme { it.copy(dock = it.dock.copy(maxApps = v.roundToInt())) } }, 2f..7f, { "${it.roundToInt()}" }, steps = 4)
            SwitchRow(tr("Namen im Dock", "Labels in dock"), null, dock.showLabels, { on -> ctrl.editTheme { it.copy(dock = it.dock.copy(showLabels = on)) } })
            l.dockApps.forEach { k ->
                SettingRow(c.apps.find(k)?.label ?: k, trailing = { NovaIconButton(Icons.Filled.Delete, tr("Entfernen", "Remove"), { ctrl.removeFromDock(k) }) })
            }
            if (l.dockApps.size < dock.maxApps) SettingRow(tr("App zum Dock hinzufügen", "Add app to dock"), onClick = { pickDock = true })
        }
        Group(tr("Ordner", "Folders")) {
            val f = home.folder
            ChoiceRow(tr("Form", "Shape"), listOf(IconShape.SQUIRCLE, IconShape.CIRCLE, IconShape.ROUNDED_SQUARE, IconShape.HEXAGON), f.shape, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(folder = it.home.folder.copy(shape = v))) } })
            ChoiceRow(tr("Vorschau", "Preview"), FolderPreview.entries, f.preview, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(folder = it.home.folder.copy(preview = v))) } })
            ChoiceRow(tr("Hintergrund", "Background"), SurfaceBackground.entries, f.background, { it.label }, { v -> ctrl.editTheme { it.copy(home = it.home.copy(folder = it.home.folder.copy(background = v))) } })
            ChoiceRow(tr("Öffnungsanimation", "Open animation"), app.novaos.model.AnimationStyle.entries, f.openAnimation, { it.label }, { v ->
                ctrl.editTheme { t -> t.copy(home = t.home.copy(folder = t.home.folder.copy(openAnimation = v)), animation = t.animation.copy(overrides = t.animation.overrides + (app.novaos.model.AnimArea.FOLDER to v))) }
            })
            SliderRow(tr("Spalten im Ordner", "Folder columns"), f.columns.toFloat(), { v -> ctrl.editTheme { it.copy(home = it.home.copy(folder = it.home.folder.copy(columns = v.roundToInt()))) } }, 3f..5f, { "${it.roundToInt()}" }, steps = 1)
            InfoText(tr("Ordner entstehen im Bearbeitungsmodus, wenn du eine App auf eine andere ziehst.", "Folders are created in edit mode by dragging one app onto another."))
        }
    }
    if (pickDock) AppPickerDialog(tr("App für das Dock", "App for the dock"), { pickDock = false }) { k -> ctrl.addToDock(k); pickDock = false }
}

// ============================================================================ App Drawer

@Composable
fun DrawerSettingsScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val p = LocalProfile.current
    val d = p.theme.drawer
    val apps by c.apps.apps.collectAsState()
    var newFolder by remember { mutableStateOf("") }
    var addToFolder by remember { mutableStateOf<String?>(null) }
    fun e(f: (app.novaos.model.DrawerSpec) -> app.novaos.model.DrawerSpec) = ctrl.editTheme { it.copy(drawer = f(it.drawer)) }
    SettingsScreen(tr("App Drawer", "App drawer")) {
        Group(tr("Darstellung", "Appearance")) {
            ChoiceRow(tr("Stil", "Style"), DrawerStyle.entries, d.style, { it.label }, { v -> e { it.copy(style = v) } })
            SliderRow(tr("Spalten", "Columns"), d.columns.toFloat(), { v -> e { it.copy(columns = v.roundToInt()) } }, 3f..7f, { "${it.roundToInt()}" }, steps = 3)
            SliderRow(tr("Icon-Größe", "Icon size"), d.iconSizeDp, { v -> e { it.copy(iconSizeDp = v) } }, 36f..80f, { "${it.roundToInt()} dp" })
            SliderRow(tr("Icon-Abstand", "Icon spacing"), d.iconSpacingDp, { v -> e { it.copy(iconSpacingDp = v) } }, 0f..32f, { "${it.roundToInt()} dp" })
            ChoiceRow(tr("Hintergrund", "Background"), SurfaceBackground.entries, d.background, { it.label }, { v -> e { it.copy(background = v) } })
            SliderRow(tr("Transparenz", "Transparency"), d.transparency, { v -> e { it.copy(transparency = v) } })
            ChoiceRow(tr("Suchleiste", "Search bar"), SearchBarStyle.entries, d.searchBar, { it.label }, { v -> e { it.copy(searchBar = v) } })
            SwitchRow(tr("Tastatur sofort öffnen", "Open keyboard immediately"), null, d.searchAutoFocus, { on -> e { it.copy(searchAutoFocus = on) } })
            SwitchRow(tr("Kategorien anzeigen", "Show categories"), null, d.showCategories, { on -> e { it.copy(showCategories = on) } })
            SwitchRow(tr("Buchstabenleiste", "Alphabet index"), null, d.showAlphabetIndex, { on -> e { it.copy(showAlphabetIndex = on) } })
            SwitchRow(tr("App-Namen", "App labels"), null, d.showLabels, { on -> e { it.copy(showLabels = on) } })
            ChoiceRow(tr("Scrollverhalten", "Scroll behavior"), ScrollBehavior.entries, d.scroll, { it.label }, { v -> e { it.copy(scroll = v) } })
            ChoiceRow(tr("Animationsstil", "Animation style"), app.novaos.model.AnimationStyle.entries, p.theme.animation.styleFor(app.novaos.model.AnimArea.OPEN), { it.label }, { v ->
                ctrl.editTheme { t -> t.copy(animation = t.animation.copy(overrides = t.animation.overrides + (app.novaos.model.AnimArea.OPEN to v) + (app.novaos.model.AnimArea.CLOSE to v))) }
            })
        }
        Group(tr("Sortierung", "Sorting")) {
            ChoiceRow(tr("Sortieren nach", "Sort by"), SortMode.entries, d.sortMode, { it.label }, { v -> e { it.copy(sortMode = v) } })
            if (d.sortMode == SortMode.CUSTOM) {
                InfoText(tr("Eigene Reihenfolge: Pfeile verschieben eine App nach oben.", "Custom order: arrows move an app up."))
                val order = AppSearch.sort(apps.filter { it.key !in p.hiddenApps }.map { it.toEntry() }, SortMode.CUSTOM, emptyMap(), p.customOrder)
                order.forEachIndexed { i, a ->
                    SettingRow(a.label, trailing = {
                        if (i > 0) NovaIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Nach oben", "Move up"), {
                            val keys = order.map { it.key }.toMutableList(); keys.add(i - 1, keys.removeAt(i))
                            ctrl.editProfile { it.copy(customOrder = keys) }
                        })
                    })
                }
            }
        }
        Group(tr("Ausgeblendete Apps", "Hidden apps")) {
            InfoText(tr("Ausgeblendete Apps erscheinen nur im Launcher nicht. Sie bleiben installiert und in den Android-Einstellungen sichtbar.", "Hidden apps only disappear from the launcher; they stay installed."))
            val hidden = apps.filter { it.key in p.hiddenApps }
            if (hidden.isEmpty()) InfoText(tr("Keine", "None"))
            hidden.forEach { a -> SettingRow(a.label, trailing = { Text(tr("Anzeigen", "Show"), color = Nova.colors.accent, modifier = Modifier.clickable { ctrl.toggleHidden(a.key) }.padding(8.dp)) }) }
        }
        Group(tr("App-Ordner im Drawer", "Drawer folders")) {
            p.drawerFolders.forEach { f ->
                SettingRow(f.name, tr("${f.apps.size} Apps", "${f.apps.size} apps"), trailing = {
                    Row {
                        Text("+ App", color = Nova.colors.accent, modifier = Modifier.clickable { addToFolder = f.id }.padding(8.dp))
                        NovaIconButton(Icons.Filled.Delete, tr("Ordner löschen", "Delete folder"), { ctrl.editProfile { pr -> pr.copy(drawerFolders = pr.drawerFolders.filter { it.id != f.id }) } })
                    }
                })
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaTextField(newFolder, { newFolder = it }, Modifier.weight(1f), tr("Neuer Ordnername", "New folder name"))
                Spacer(Modifier.width(8.dp))
                NovaButton(tr("Anlegen", "Create"), {
                    if (newFolder.isNotBlank()) { ctrl.editProfile { it.copy(drawerFolders = it.drawerFolders + DrawerFolder(newId("folder"), newFolder.trim(), emptyList())) }; newFolder = "" }
                })
            }
        }
    }
    addToFolder?.let { fid ->
        AppPickerDialog(tr("App zum Ordner", "Add app to folder"), { addToFolder = null }) { k ->
            ctrl.editProfile { pr -> pr.copy(drawerFolders = pr.drawerFolders.map { if (it.id == fid) it.copy(apps = (it.apps + k).distinct()) else it }) }
            addToFolder = null
        }
    }
}

// ============================================================================ Gesten

@Composable
fun GesturesScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val p = LocalProfile.current
    var pickAppFor by remember { mutableStateOf<GestureType?>(null) }
    var pickWidgetFor by remember { mutableStateOf<GestureType?>(null) }
    val gestureNav = remember { runCatching { android.provider.Settings.Secure.getInt(c.context.contentResolver, "navigation_mode", 2) == 2 }.getOrDefault(true) }
    SettingsScreen(tr("Gesten", "Gestures")) {
        InfoText(tr("Jeder Geste kann eine Aktion zugeordnet werden. Gesten, die in den Android-Systembereichen (Bildschirmränder) beginnen, gehören dem System.", "Assign an action to each gesture. Gestures starting at the system edges belong to Android."))
        GestureType.entries.forEach { g ->
            val current = p.gestures[g] ?: GestureAction.None
            Group(g.label) {
                if (g.hint.isNotBlank()) InfoText(g.hint)
                GestureClassifier.conflicts(g, gestureNav)?.let { InfoText("⚠ $it") }
                val options = SimpleGestureActions + listOf(GestureAction.LaunchApp(""), GestureAction.OpenWidget(""))
                ChoiceRow(tr("Aktion", "Action"), options, options.firstOrNull { it::class == current::class } ?: GestureAction.None, { it.label() }, { a ->
                    when (a) {
                        is GestureAction.LaunchApp -> pickAppFor = g
                        is GestureAction.OpenWidget -> pickWidgetFor = g
                        else -> ctrl.editProfile { it.copy(gestures = it.gestures + (g to a)) }
                    }
                })
                when (current) {
                    is GestureAction.LaunchApp -> InfoText(tr("App: ", "App: ") + (c.apps.find(current.appKey)?.label ?: current.appKey))
                    is GestureAction.OpenWidget -> InfoText(tr("Widget: ", "Widget: ") + (c.controller.findItem(current.widgetItemId)?.second?.let { (it as? HomeItem.Widget)?.widget?.type?.label } ?: "–"))
                    GestureAction.LockScreen -> PermissionBadge(tr("Geräteadministrator", "Device admin"), c.controls.isAdminActive()) { c.apps.startIntent(c.controls.adminRequestIntent()) }
                    else -> Unit
                }
            }
        }
        Group {
            SettingRow(tr("Standardgesten wiederherstellen", "Restore default gestures"), onClick = { ctrl.editProfile { it.copy(gestures = app.novaos.model.defaultGestures()) } })
        }
    }
    pickAppFor?.let { g -> AppPickerDialog(tr("App für „${g.label}“", "App for “${g.label}”"), { pickAppFor = null }) { k -> ctrl.editProfile { it.copy(gestures = it.gestures + (g to GestureAction.LaunchApp(k))) }; pickAppFor = null } }
    pickWidgetFor?.let { g ->
        val widgets = p.layout.pages.flatMap { it.items }.filterIsInstance<HomeItem.Widget>()
        Dialog(onDismissRequest = { pickWidgetFor = null }) {
            NovaSurface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), SurfaceBackground.SOLID, 0f) {
                Column(Modifier.padding(16.dp)) {
                    NovaText(tr("Widget wählen", "Choose widget"), Nova.type.headline)
                    if (widgets.isEmpty()) InfoText(tr("Keine Widgets auf dem Homescreen", "No widgets on the home screen"))
                    widgets.forEach { w -> SettingRow(w.widget.type.label, onClick = { ctrl.editProfile { it.copy(gestures = it.gestures + (g to GestureAction.OpenWidget(w.id))) }; pickWidgetFor = null }) }
                }
            }
        }
    }
}
