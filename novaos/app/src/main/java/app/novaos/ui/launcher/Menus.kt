package app.novaos.ui.launcher

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaSurface
import app.novaos.design.NovaText
import app.novaos.design.NovaTextField
import app.novaos.model.AppCategories
import app.novaos.model.AppSearch
import app.novaos.model.HomeItem
import app.novaos.model.HomeLayout
import app.novaos.model.ShortcutTarget
import app.novaos.model.SurfaceBackground
import app.novaos.model.TaskItem
import app.novaos.model.newId
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.DrawableImage
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import app.novaos.ui.settings.SettingsIndex
import app.novaos.widgets.NovaWidget
import kotlinx.coroutines.launch

/** Untere Glas-Karte für Menüs. */
@Composable
fun SheetPanel(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).imePadding(), contentAlignment = Alignment.BottomCenter) {
        NovaSurface(modifier.fillMaxWidth().padding(10.dp), RoundedCornerShape(30.dp), SurfaceBackground.GLASS, 0.15f, shadow = 0.5f) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (title != null) { NovaText(title, Nova.type.headline, maxLines = 1); Spacer(Modifier.height(10.dp)) }
                content()
            }
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Nova.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) c.accent else c.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = Nova.type.body, color = if (danger) c.accent else c.text)
    }
}

// ============================================================================ Schnellmenü & Profile

@Composable
fun QuickMenu(vm: LauncherViewModel, actions: LauncherActions, page: Int) {
    SheetPanel(tr("Schnellmenü", "Quick menu")) {
        MenuRow(Icons.Filled.Widgets, tr("Widget hinzufügen", "Add widget")) { vm.open(Overlay.WidgetPicker(page)) }
        MenuRow(Icons.Filled.Edit, tr("Homescreen bearbeiten", "Edit home screen")) { vm.setEdit(true) }
        MenuRow(Icons.Filled.Wallpaper, tr("Hintergrund", "Wallpaper")) { vm.close(); actions.openSettings("wallpaper") }
        MenuRow(Icons.Filled.Palette, tr("Themes", "Themes")) { vm.close(); actions.openSettings("themes") }
        MenuRow(Icons.Filled.Person, tr("Profil wechseln", "Switch profile")) { vm.open(Overlay.ProfileSwitcher) }
        MenuRow(Icons.Filled.Search, tr("Suche", "Search")) { vm.open(Overlay.Search) }
        MenuRow(Icons.Filled.Settings, tr("NovaOS Einstellungen", "NovaOS settings")) { vm.close(); actions.openSettings(null) }
    }
}

@Composable
fun ProfileSwitcher(vm: LauncherViewModel) {
    val c = LocalContainer.current
    val cfg = LocalConfig.current
    SheetPanel(tr("Profil wechseln", "Switch profile")) {
        cfg.profiles.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { c.controller.switchProfile(p.id); vm.close() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(app.novaos.design.hexColor(p.theme.colors.dark.accent, Nova.colors.accent)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), color = Nova.colors.text)
                    Text("${p.kind.label} · ${p.theme.name}", style = Nova.type.caption, color = Nova.colors.textSecondary)
                }
                if (p.id == cfg.activeProfileId) Icon(Icons.Filled.Check, tr("Aktiv", "Active"), tint = Nova.colors.accent)
            }
        }
    }
}

// ============================================================================ App-Menü

@Composable
fun AppMenu(vm: LauncherViewModel, actions: LauncherActions, menu: Overlay.AppMenu, page: Int) {
    val c = LocalContainer.current
    val profile = LocalProfile.current
    val app = c.apps.find(menu.appKey)
    val density = LocalDensity.current
    val shortcuts = remember(menu.appKey) { c.apps.shortcuts(menu.appKey) }
    var choosingCategory by remember { mutableStateOf(false) }
    SheetPanel(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(menu.appKey, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(app?.label ?: menu.appKey, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), color = Nova.colors.text)
                Text((profile.categoryOverrides[menu.appKey] ?: app?.category).orEmpty(), style = Nova.type.caption, color = Nova.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (shortcuts.isNotEmpty()) {
            shortcuts.take(4).forEach { s: ShortcutInfo ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        c.apps.startShortcut(s.`package`, s.id, app?.userSerial ?: 0, null); vm.close()
                    }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawableImage(remember(s.id) { c.apps.shortcutIcon(s, (density.density * 160).toInt()) }, 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(s.shortLabel?.toString().orEmpty(), style = Nova.type.body, color = Nova.colors.text, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.Shortcut, tr("Auf Homescreen", "Pin to home"), tint = Nova.colors.textSecondary,
                        modifier = Modifier.size(20.dp).clickable {
                            c.controller.addShortcut(s.shortLabel?.toString() ?: "", ShortcutTarget.DeepShortcut(s.`package`, s.id, app?.userSerial ?: 0), page)
                            vm.close()
                        },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Nova.colors.text.copy(alpha = 0.1f)))
        }
        if (choosingCategory) {
            (AppCategories.all + listOf(tr("Standard", "Default"))).forEach { cat ->
                MenuRow(Icons.Filled.Category, cat) {
                    c.controller.setCategory(menu.appKey, if (cat == tr("Standard", "Default")) null else cat); vm.close()
                }
            }
            return@SheetPanel
        }
        if (menu.homeItemId == null && !menu.fromDock) MenuRow(Icons.Filled.Home, tr("Zum Homescreen hinzufügen", "Add to home screen")) { c.controller.addApp(menu.appKey, page); vm.close() }
        if (menu.fromDock) MenuRow(Icons.Filled.Delete, tr("Aus dem Dock entfernen", "Remove from dock")) { c.controller.removeFromDock(menu.appKey); vm.close() }
        else MenuRow(Icons.Filled.Dock, tr("Zum Dock hinzufügen", "Add to dock")) { c.controller.addToDock(menu.appKey); vm.close() }
        val fav = menu.appKey in profile.favorites
        MenuRow(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, if (fav) tr("Aus Favoriten entfernen", "Remove favorite") else tr("Zu Favoriten", "Add to favorites")) { c.controller.toggleFavorite(menu.appKey); vm.close() }
        MenuRow(Icons.Filled.Brush, tr("Icon anpassen", "Customize icon")) { vm.close(); actions.openSettings("icon_app", menu.appKey) }
        MenuRow(Icons.Filled.Category, tr("Kategorie ändern", "Change category")) { choosingCategory = true }
        val hidden = menu.appKey in profile.hiddenApps
        MenuRow(if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (hidden) tr("Wieder anzeigen", "Show again") else tr("Im Launcher ausblenden", "Hide in launcher")) { c.controller.toggleHidden(menu.appKey); vm.close() }
        if (menu.homeItemId != null) MenuRow(Icons.Filled.Delete, tr("Vom Homescreen entfernen", "Remove from home")) { c.controller.removeItem(menu.homeItemId); vm.close() }
        MenuRow(Icons.Filled.Info, tr("App-Info", "App info")) { c.apps.openAppDetails(menu.appKey); vm.close() }
        if (app?.isSystem != true) MenuRow(Icons.Filled.Delete, tr("Deinstallieren …", "Uninstall …"), danger = true) {
            c.apps.requestUninstall(menu.appKey); vm.close()
        }
    }
}


// ============================================================================ Element-Menü (Widgets, Ordner, Verknüpfungen)

@Composable
fun ItemMenu(vm: LauncherViewModel, actions: LauncherActions, widgetHost: NovaWidgetHost, itemId: String) {
    val c = LocalContainer.current
    val found = c.controller.findItem(itemId)
    if (found == null) { LaunchedEffect(Unit) { vm.close() }; return }
    val (_, item) = found
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf((item as? HomeItem.Folder)?.name ?: "") }
    SheetPanel(
        when (item) {
            is HomeItem.Widget -> item.widget.type.label
            is HomeItem.Folder -> item.name
            is HomeItem.Shortcut -> item.label
            is HomeItem.App -> c.apps.find(item.appKey)?.label ?: "App"
            is HomeItem.AndroidWidget -> tr("Android-Widget", "Android widget")
        },
    ) {
        when (item) {
            is HomeItem.Widget -> {
                MenuRow(Icons.Filled.Edit, tr("Widget bearbeiten", "Edit widget")) { vm.close(); actions.editWidget(item.id) }
                MenuRow(Icons.Filled.Widgets, tr("Vergrößert öffnen", "Open enlarged")) { vm.open(Overlay.WidgetPanel(item.id)) }
            }
            is HomeItem.Folder -> {
                if (renaming) {
                    NovaTextField(name, { name = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    NovaButton(tr("Speichern", "Save"), { c.controller.renameFolder(item.id, name.ifBlank { item.name }); vm.close() })
                } else MenuRow(Icons.Filled.Edit, tr("Umbenennen", "Rename")) { renaming = true }
            }
            is HomeItem.App -> MenuRow(Icons.Filled.Brush, tr("Icon anpassen", "Customize icon")) { vm.close(); actions.openSettings("icon_app", item.appKey) }
            else -> Unit
        }
        MenuRow(Icons.Filled.Edit, tr("Homescreen bearbeiten", "Edit home screen")) { vm.setEdit(true) }
        MenuRow(Icons.Filled.Delete, tr("Entfernen", "Remove"), danger = true) {
            if (item is HomeItem.AndroidWidget) runCatching { widgetHost.deleteAppWidgetId(item.appWidgetId) }
            c.controller.removeItem(item.id); vm.close()
        }
    }
}

// ============================================================================ Ordner

@Composable
fun FolderOverlay(vm: LauncherViewModel, actions: LauncherActions, itemId: String) {
    val c = LocalContainer.current
    val profile = LocalProfile.current
    val folder = c.controller.findItem(itemId)?.second as? HomeItem.Folder
    if (folder == null) { LaunchedEffect(Unit) { vm.close() }; return }
    val fs = profile.theme.home.folder
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        NovaSurface(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp), fs.background, 0.2f, shadow = 0.5f) {
            Column(Modifier.padding(18.dp)) {
                NovaTextField(name, { name = it; c.controller.renameFolder(folder.id, it) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                val cols = fs.columns.coerceIn(3, 5)
                folder.apps.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { k ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().aspectRatio(0.85f)) { AppCell(vm, actions, k, HomeLayout(iconSizeDp = 52f), editMode = false) }
                                Text(tr("Herausnehmen", "Remove"), style = Nova.type.caption, color = Nova.colors.textSecondary, maxLines = 1,
                                    modifier = Modifier.clickable { c.controller.removeFromFolder(folder.id, k) })
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ============================================================================ Widget vergrößert

@Composable
fun WidgetPanel(vm: LauncherViewModel, itemId: String) {
    val c = LocalContainer.current
    val item = c.controller.findItem(itemId)?.second as? HomeItem.Widget
    if (item == null) { LaunchedEffect(Unit) { vm.close() }; return }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(20.dp), contentAlignment = Alignment.Center) {
        NovaWidget(item.widget.copy(style = item.widget.style.copy(layout = app.novaos.model.WidgetLayoutVariant.DETAILED, density = app.novaos.model.InfoDensity.HIGH)), item.id, Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

// ============================================================================ Suche

@Composable
fun SearchOverlay(vm: LauncherViewModel, actions: LauncherActions) {
    val c = LocalContainer.current
    val profile = LocalProfile.current
    val apps by vm.apps.collectAsState()
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val visible = apps.filter { it.key !in profile.hiddenApps }.map { it.toEntry() }
    val results = if (q.isBlank()) emptyList() else AppSearch.search(visible, q).take(8)
    val settings = if (q.isBlank()) emptyList() else SettingsIndex.search(q).take(5)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding().padding(16.dp)) {
        SearchBar(q, { q = it }, profile.theme.drawer.searchBar, Modifier.fillMaxWidth().focusRequester(focus), tr("Apps, Einstellungen, Web …", "Apps, settings, web …"))
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.key }) { a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { actions.launchApp(a.key); vm.close() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(a.key, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, style = Nova.type.body, color = Nova.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(settings, key = { "s-" + it.route + it.title }) { s ->
                MenuRow(Icons.Filled.Settings, "${s.title} – ${s.category}") { vm.close(); actions.openSettings(s.route) }
            }
            if (q.isNotBlank()) {
                item {
                    MenuRow(Icons.Filled.Search, tr("Im Web suchen: „$q“", "Search web: “$q”")) {
                        actions.startSafely(android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)); vm.close()
                    }
                    MenuRow(Icons.Filled.Task, tr("Als Aufgabe speichern: „$q“", "Save as task: “$q”")) {
                        scope.launch { c.productivity.update { d -> d.copy(tasks = d.tasks + TaskItem(newId("task"), q.trim(), createdAtMs = System.currentTimeMillis())) } }
                        actions.toast(tr("Aufgabe gespeichert", "Task saved")); vm.close()
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun BoxScope.unused() = Unit
