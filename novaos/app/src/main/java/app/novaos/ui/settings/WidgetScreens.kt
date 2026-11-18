package app.novaos.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novaos.design.ChoiceRow
import app.novaos.design.ColorPickerRow
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaIcons
import app.novaos.design.NovaSurface
import app.novaos.design.NovaTextField
import app.novaos.design.SettingRow
import app.novaos.design.SliderRow
import app.novaos.design.SwitchRow
import app.novaos.design.hexColor
import app.novaos.design.widgetShape
import app.novaos.model.AppCategories
import app.novaos.model.CustomWidgetDef
import app.novaos.model.DataField
import app.novaos.model.ElementKind
import app.novaos.model.FontChoice
import app.novaos.model.HomeItem
import app.novaos.model.InfoDensity
import app.novaos.model.NovaExport
import app.novaos.model.ProgressSource
import app.novaos.model.ShortcutTarget
import app.novaos.model.SurfaceBackground
import app.novaos.model.TemplateCategory
import app.novaos.model.TextAlign
import app.novaos.model.WidgetAnimation
import app.novaos.model.WidgetElement
import app.novaos.model.WidgetInstance
import app.novaos.model.WidgetLayoutVariant
import app.novaos.model.WidgetShape
import app.novaos.model.WidgetStyle
import app.novaos.model.WidgetTap
import app.novaos.model.WidgetTemplate
import app.novaos.model.WidgetType
import app.novaos.model.newId
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import app.novaos.widgets.CustomWidgetCanvas
import app.novaos.widgets.NovaWidget
import app.novaos.widgets.rememberLook
import app.novaos.widgets.LocalWidgetLook
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ============================================================================ Widget-Übersicht

@Composable
fun WidgetsScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val nav = LocalNav.current
    val cfg = LocalConfig.current
    val p = LocalProfile.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportId by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val t = (cfg.widgetTemplates).firstOrNull { it.id == exportId }
        if (uri != null && t != null) scope.launch { c.backup.writeText(uri, NovaExport.encode(NovaExport(NovaExport.WIDGET, widgetTemplate = t))) }
        exportId = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val e = c.backup.readExport(uri, NovaExport.WIDGET)
            val t = e?.widgetTemplate
            if (t != null) {
                ctrl.saveTemplate(t.copy(id = newId("tpl"), builtIn = false))
                Toast.makeText(ctx, tr("Vorlage „${t.name}“ importiert", "Template “${t.name}” imported"), Toast.LENGTH_SHORT).show()
            } else Toast.makeText(ctx, tr("Keine gültige Widget-Datei", "Not a valid widget file"), Toast.LENGTH_LONG).show()
        }
    }
    SettingsScreen(tr("Widgets", "Widgets"), preview = true) {
        p.layout.pages.forEachIndexed { i, page ->
            val widgets = page.items.filter { it is HomeItem.Widget || it is HomeItem.AndroidWidget }
            if (widgets.isNotEmpty()) Group(tr("Seite ${i + 1}", "Page ${i + 1}")) {
                widgets.forEach { w ->
                    when (w) {
                        is HomeItem.Widget -> SettingRow(w.widget.type.label, "${w.pos.w} × ${w.pos.h} · ${w.widget.style.background.label}", onClick = { nav.push("widget_editor", w.id) },
                            trailing = { NovaIconButton(Icons.Filled.Delete, tr("Entfernen", "Remove"), { ctrl.removeItem(w.id) }) })
                        is HomeItem.AndroidWidget -> SettingRow(tr("Android-Widget", "Android widget"), w.provider.substringBefore('/'), trailing = { NovaIconButton(Icons.Filled.Delete, tr("Entfernen", "Remove"), { ctrl.removeItem(w.id) }) })
                        else -> Unit
                    }
                }
            }
        }
        Group(tr("Widget hinzufügen (Seite 1)", "Add widget (page 1)")) {
            ChoiceRow(tr("Typ", "Type"), WidgetType.entries.filter { it != WidgetType.CUSTOM }, WidgetType.CLOCK, { it.label }, { t -> ctrl.addWidget(t, 0) })
            InfoText(tr("Im Launcher: langes Drücken auf freie Fläche → „Widget hinzufügen“ zeigt alle Widgets mit Live-Vorschau.", "In the launcher: long press empty space → “Add widget” shows live previews."))
        }
        Group(tr("Eigene Vorlagen", "Own templates")) {
            if (cfg.widgetTemplates.isEmpty()) InfoText(tr("Speichere ein Widget im Editor als Vorlage oder importiere eine Datei.", "Save a widget as template in the editor or import a file."))
            cfg.widgetTemplates.forEach { t ->
                SettingRow(t.name, "${t.category.label} · ${t.widget.type.label}", onClick = { ctrl.addTemplate(t, 0) }, trailing = {
                    Row {
                        NovaIconButton(Icons.Filled.Upload, tr("Exportieren", "Export"), { exportId = t.id; exportLauncher.launch("${t.name}.novawidget.json") })
                        NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.deleteTemplate(t.id) })
                    }
                })
            }
            SettingRow(tr("Vorlage importieren", "Import template"), icon = Icons.Filled.Download, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
        }
    }
}

// ============================================================================ Widget-Editor

@Composable
fun WidgetStyleEditor(style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    Group(tr("Form & Hintergrund", "Shape & background")) {
        ChoiceRow(tr("Form", "Shape"), WidgetShape.entries, style.shape, { it.label }, { onChange(style.copy(shape = it)) })
        SliderRow(tr("Eckenradius", "Corner radius"), style.cornerDp, { onChange(style.copy(cornerDp = it)) }, 0f..48f, { "${it.roundToInt()} dp" })
        ChoiceRow(tr("Hintergrund", "Background"), SurfaceBackground.entries, style.background, { it.label }, { onChange(style.copy(background = it)) })
        SliderRow(tr("Transparenz", "Transparency"), style.transparency, { onChange(style.copy(transparency = it)) })
        ColorPickerRow(tr("Hintergrundfarbe", "Background color"), style.backgroundColor, { onChange(style.copy(backgroundColor = it)) }, allowNone = true, onNone = { onChange(style.copy(backgroundColor = null)) })
        if (style.background == SurfaceBackground.GRADIENT) ColorPickerRow(tr("Zweite Verlaufsfarbe", "Second gradient color"), style.backgroundColor2, { onChange(style.copy(backgroundColor2 = it)) }, allowNone = true, onNone = { onChange(style.copy(backgroundColor2 = null)) })
    }
    Group(tr("Rahmen, Schatten, Abstand", "Border, shadow, padding")) {
        SliderRow(tr("Rahmenbreite", "Border width"), style.borderWidth, { onChange(style.copy(borderWidth = it)) }, 0f..4f, { "%.1f dp".format(it) })
        ColorPickerRow(tr("Rahmenfarbe", "Border color"), style.borderColor, { onChange(style.copy(borderColor = it)) }, allowNone = true, onNone = { onChange(style.copy(borderColor = null)) })
        SliderRow(tr("Schatten", "Shadow"), style.shadow, { onChange(style.copy(shadow = it)) })
        SliderRow(tr("Innenabstand", "Padding"), style.paddingDp, { onChange(style.copy(paddingDp = it)) }, 0f..32f, { "${it.roundToInt()} dp" })
    }
    Group(tr("Schrift & Farben", "Font & colors")) {
        ChoiceRow(tr("Schrift", "Font"), listOf<FontChoice?>(null) + FontChoice.entries.filter { it != FontChoice.NOVA_DOT }, style.font, { it?.label ?: tr("Theme", "Theme") }, { onChange(style.copy(font = it)) })
        SliderRow(tr("Textgröße", "Text size"), style.textScale, { onChange(style.copy(textScale = it)) }, 0.6f..1.8f, { "${(it * 100).roundToInt()} %" })
        ColorPickerRow(tr("Textfarbe", "Text color"), style.textColor, { onChange(style.copy(textColor = it)) }, allowNone = true, onNone = { onChange(style.copy(textColor = null)) })
        ColorPickerRow(tr("Akzentfarbe", "Accent color"), style.accentColor, { onChange(style.copy(accentColor = it)) }, allowNone = true, onNone = { onChange(style.copy(accentColor = null)) })
        SwitchRow(tr("Punktmatrix für Zahlen", "Dot matrix numbers"), null, style.dotMatrix, { onChange(style.copy(dotMatrix = it)) })
    }
    Group(tr("Layout & Verhalten", "Layout & behavior")) {
        ChoiceRow(tr("Layout", "Layout"), WidgetLayoutVariant.entries, style.layout, { it.label }, { onChange(style.copy(layout = it)) })
        ChoiceRow(tr("Informationsdichte", "Information density"), InfoDensity.entries, style.density, { it.label }, { onChange(style.copy(density = it)) })
        ChoiceRow(tr("Animation", "Animation"), WidgetAnimation.entries, style.animation, { it.label }, { onChange(style.copy(animation = it)) })
        ChoiceRow(tr("Beim Antippen", "On tap"), WidgetTap.entries, style.tap, { it.label }, { onChange(style.copy(tap = it)) })
    }
}

@Composable
private fun WidgetPreviewBox(instance: WidgetInstance, w: Int, h: Int, id: String) {
    val cols = LocalProfile.current.layout.columns
    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        val cell: Dp = 300.dp / cols.coerceAtLeast(1)
        Box(Modifier.size(cell * w, cell * h)) { NovaWidget(instance, id, Modifier.fillMaxSize(), preview = true) }
    }
}

@Composable
fun WidgetEditorScreen(itemId: String?) {
    val c = LocalContainer.current
    val ctrl = c.controller
    val nav = LocalNav.current
    val p = LocalProfile.current
    val item = itemId?.let { id -> p.layout.pages.flatMap { it.items }.firstOrNull { it.id == id } } as? HomeItem.Widget
    var pickApp by remember { mutableStateOf<String?>(null) }
    var tplName by remember { mutableStateOf("") }
    var tplCat by remember { mutableStateOf(TemplateCategory.MINIMAL) }
    if (item == null) {
        SettingsScreen(tr("Widget-Editor", "Widget editor")) { InfoText(tr("Widget nicht gefunden.", "Widget not found.")) }
        return
    }
    val inst = item.widget
    fun update(f: (WidgetInstance) -> WidgetInstance) = ctrl.updateWidget(item.id, f(inst))
    SettingsScreen(inst.type.label) {
        WidgetPreviewBox(inst, item.pos.w, item.pos.h, item.id)
        Group(tr("Größe & Position", "Size & position")) {
            SliderRow(tr("Breite (Zellen)", "Width (cells)"), item.pos.w.toFloat(), { v -> ctrl.resizeItem(item.id, v.roundToInt(), item.pos.h) }, 1f..p.layout.columns.toFloat(), { "${it.roundToInt()}" }, steps = (p.layout.columns - 2).coerceAtLeast(0))
            SliderRow(tr("Höhe (Zellen)", "Height (cells)"), item.pos.h.toFloat(), { v -> ctrl.resizeItem(item.id, item.pos.w, v.roundToInt()) }, 1f..p.layout.rows.toFloat(), { "${it.roundToInt()}" }, steps = (p.layout.rows - 2).coerceAtLeast(0))
            SliderRow(tr("Spalte", "Column"), item.pos.x.toFloat(), { v -> ctrl.findItem(item.id)?.let { (pg, _) -> ctrl.moveItem(item.id, pg, v.roundToInt(), item.pos.y) } }, 0f..(p.layout.columns - item.pos.w).toFloat().coerceAtLeast(0f), { "${it.roundToInt() + 1}" })
            SliderRow(tr("Zeile", "Row"), item.pos.y.toFloat(), { v -> ctrl.findItem(item.id)?.let { (pg, _) -> ctrl.moveItem(item.id, pg, item.pos.x, v.roundToInt()) } }, 0f..(p.layout.rows - item.pos.h).toFloat().coerceAtLeast(0f), { "${it.roundToInt() + 1}" })
            InfoText(tr("Freies Verschieben und Skalieren: im Launcher „Homescreen bearbeiten“.", "Free moving and resizing: “Edit home screen” in the launcher."))
        }
        Group(tr("Inhalt", "Content")) {
            when (inst.type) {
                WidgetType.CLOCK -> SwitchRow(tr("Sekunden anzeigen", "Show seconds"), tr("Aktualisiert jede Sekunde (mehr Energie)", "Updates every second"), inst.config["seconds"] == "true", { on -> update { it.copy(config = it.config + ("seconds" to on.toString())) } })
                WidgetType.APP_SHORTCUTS, WidgetType.APP_INFO -> SettingRow(tr("App", "App"), inst.config["app"]?.let { c.apps.find(it)?.label } ?: tr("Nicht gewählt", "Not chosen"), onClick = { pickApp = "app" })
                WidgetType.FAVORITE_APPS -> {
                    ChoiceRow(tr("Quelle", "Source"), listOf<String?>(null) + AppCategories.all, inst.config["category"], { it ?: tr("Favoriten", "Favorites") }, { v ->
                        update { it.copy(config = if (v == null) it.config - "category" else it.config + ("category" to v)) }
                    })
                }
                WidgetType.APP_LINKS -> SettingRow(tr("App hinzufügen", "Add app"), inst.config["apps"]?.split('|')?.filter { it.isNotBlank() }?.joinToString { c.apps.find(it)?.label ?: "?" }, onClick = { pickApp = "apps" })
                WidgetType.PROGRESS -> ChoiceRow(tr("Fortschritt von", "Progress of"), ProgressSource.entries, runCatching { ProgressSource.valueOf(inst.config["source"] ?: "DAY") }.getOrDefault(ProgressSource.DAY), { it.label }, { v -> update { it.copy(config = it.config + ("source" to v.name)) } })
                WidgetType.PROJECTS, WidgetType.TASKS -> {
                    val projects = c.productivity.data.value.projects
                    ChoiceRow(tr("Projekt", "Project"), listOf<String?>(null) + projects.map { it.id }, inst.config["projectId"], { id -> projects.firstOrNull { it.id == id }?.name ?: tr("Alle", "All") }, { v ->
                        update { it.copy(config = if (v == null) it.config - "projectId" else it.config + ("projectId" to v)) }
                    })
                    SettingRow(tr("Aufgaben verwalten", "Manage tasks"), onClick = { nav.push("productivity", "tasks") })
                }
                WidgetType.NOTES -> SettingRow(tr("Notizen verwalten", "Manage notes"), onClick = { nav.push("productivity", "notes") })
                WidgetType.WEATHER -> SettingRow(tr("Ort & Einheit", "Location & unit"), onClick = { nav.push("weather") })
                WidgetType.CUSTOM -> SettingRow(tr("Im Widget Creator bearbeiten", "Edit in Widget Creator"), onClick = { nav.push("widget_creator_edit", inst.config["customId"]) })
                else -> InfoText(tr("Dieses Widget hat keine weiteren Inhaltsoptionen.", "No further content options."))
            }
            if (inst.type.needs != app.novaos.model.WidgetRequirement.NONE) InfoText(tr("Benötigt: ", "Requires: ") + inst.type.needs.label)
        }
        WidgetStyleEditor(inst.style) { s -> update { it.copy(style = s) } }
        if (inst.style.tap == WidgetTap.OPEN_TARGET) Group(tr("Tipp-Ziel", "Tap target")) {
            SettingRow(tr("App wählen", "Choose app"), (inst.style.tapTarget as? ShortcutTarget.App)?.let { c.apps.find(it.appKey)?.label }, onClick = { pickApp = "tap" })
        }
        Group(tr("Als Vorlage speichern", "Save as template")) {
            NovaTextField(tplName, { tplName = it }, Modifier.fillMaxWidth().padding(12.dp), tr("Name der Vorlage", "Template name"))
            ChoiceRow(tr("Kategorie", "Category"), TemplateCategory.entries, tplCat, { it.label }, { tplCat = it })
            Row(Modifier.padding(12.dp)) {
                NovaButton(tr("Vorlage speichern", "Save template"), {
                    val custom = inst.config["customId"]?.let { id -> LocalConfigHolder.customOf(c, id) }
                    ctrl.saveTemplate(WidgetTemplate(newId("tpl"), tplName.ifBlank { inst.type.label }, tplCat, inst.copy(id = "tpl"), item.pos.w, item.pos.h, custom))
                    tplName = ""
                })
            }
        }
        Group { SettingRow(tr("Widget entfernen", "Remove widget"), icon = Icons.Filled.Delete, onClick = { ctrl.removeItem(item.id); nav.back() }) }
    }
    pickApp?.let { mode ->
        AppPickerDialog(tr("App wählen", "Choose app"), { pickApp = null }) { k ->
            when (mode) {
                "app" -> update { it.copy(config = it.config + ("app" to k)) }
                "apps" -> update { it.copy(config = it.config + ("apps" to ((it.config["apps"]?.split('|')?.filter { s -> s.isNotBlank() } ?: emptyList()) + k).distinct().takeLast(4).joinToString("|"))) }
                "tap" -> update { it.copy(style = it.style.copy(tapTarget = ShortcutTarget.App(k))) }
            }
            pickApp = null
        }
    }
}

private object LocalConfigHolder {
    fun customOf(c: app.novaos.AppContainer, id: String): CustomWidgetDef? = c.config.config.value?.customWidgets?.firstOrNull { it.id == id }
}

// ============================================================================ Widget Creator

@Composable
fun WidgetCreatorListScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val nav = LocalNav.current
    val cfg = LocalConfig.current
    SettingsScreen(tr("Widget Creator", "Widget creator")) {
        InfoText(tr("Baue eigene Widgets aus Texten, Symbolen, Infofeldern, Fortschrittsbalken und Flächen – ohne Programmierkenntnisse.", "Build widgets from text, symbols, info fields, progress bars and shapes – no code."))
        Group(tr("Eigene Widgets", "Own widgets")) {
            if (cfg.customWidgets.isEmpty()) InfoText(tr("Noch keine eigenen Widgets.", "No custom widgets yet."))
            cfg.customWidgets.forEach { d ->
                SettingRow(d.name, tr("${d.elements.size} Elemente · ${d.w} × ${d.h}", "${d.elements.size} elements · ${d.w} × ${d.h}"), onClick = { nav.push("widget_creator_edit", d.id) }, trailing = {
                    Row {
                        NovaIconButton(Icons.Filled.ContentCopy, tr("Duplizieren", "Duplicate"), { ctrl.upsertCustomWidget(d.copy(id = newId("cw"), name = "${d.name} 2")) })
                        NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.deleteCustomWidget(d.id) })
                    }
                })
            }
            SettingRow(tr("Neues Widget", "New widget"), icon = Icons.Filled.Add, onClick = {
                val d = CustomWidgetDef(
                    newId("cw"), tr("Mein Widget", "My widget"), 2, 2, WidgetStyle(),
                    listOf(
                        WidgetElement(newId("e"), ElementKind.DOT_TEXT, 0.05f, 0.08f, 0.9f, 0.4f, data = DataField.TIME),
                        WidgetElement(newId("e"), ElementKind.DATA, 0.05f, 0.55f, 0.9f, 0.18f, data = DataField.DATE, sizeSp = 14f),
                        WidgetElement(newId("e"), ElementKind.PROGRESS, 0.05f, 0.8f, 0.9f, 0.1f, progress = ProgressSource.DAY),
                    ),
                )
                ctrl.upsertCustomWidget(d)
                nav.push("widget_creator_edit", d.id)
            })
        }
    }
}

@Composable
fun WidgetCreatorEditScreen(id: String?) {
    val c = LocalContainer.current
    val ctrl = c.controller
    val cfg = LocalConfig.current
    val def = cfg.customWidgets.firstOrNull { it.id == id }
    var selected by remember { mutableStateOf<String?>(null) }
    var pickApp by remember { mutableStateOf(false) }
    if (def == null) { SettingsScreen(tr("Widget Creator", "Widget creator")) { InfoText(tr("Widget nicht gefunden.", "Widget not found.")) }; return }
    fun save(d: CustomWidgetDef) = ctrl.upsertCustomWidget(d)
    fun editEl(eid: String, f: (WidgetElement) -> WidgetElement) = save(def.copy(elements = def.elements.map { if (it.id == eid) f(it) else it }))
    val sel = def.elements.firstOrNull { it.id == selected }

    SettingsScreen(def.name) {
        // Arbeitsfläche mit Live-Vorschau: Elemente ziehen und skalieren
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val cell = 300.dp / 4
            val w = cell * def.w
            val h = cell * def.h
            NovaSurface(Modifier.size(w, h), widgetShape(def.style.shape, def.style.cornerDp.dp), def.style.background, def.style.transparency,
                color = hexColor(def.style.backgroundColor, Nova.colors.widget), borderWidth = def.style.borderWidth.dp) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(def.style.paddingDp.dp)) {
                    val look = rememberLook(def.style, maxWidth, maxHeight)
                    val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    CompositionLocalProvider(LocalWidgetLook provides look) {
                        CustomWidgetCanvas(def, preview = true) { e ->
                            val isSel = e.id == selected
                            Box(
                                Modifier.offset(maxWidth * e.x, maxHeight * e.y).size(maxWidth * e.w, maxHeight * e.h)
                                    .border(if (isSel) 1.5.dp else 0.5.dp, if (isSel) Nova.colors.accent else Nova.colors.text.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .clickable { selected = e.id }
                                    .pointerInput(e.id, e.x, e.y) {
                                        var dx = 0f; var dy = 0f
                                        detectDragGestures(onDragStart = { selected = e.id; dx = 0f; dy = 0f }, onDragEnd = {
                                            editEl(e.id) { el -> el.copy(x = (el.x + dx / widthPx).coerceIn(0f, 1f - el.w), y = (el.y + dy / heightPx).coerceIn(0f, 1f - el.h)) }
                                        }) { ch, a -> ch.consume(); dx += a.x; dy += a.y }
                                    },
                            ) {
                                if (isSel) Box(
                                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(Nova.colors.accent)
                                        .pointerInput(e.id, e.w, e.h) {
                                            var dw = 0f; var dh = 0f
                                            detectDragGestures(onDragStart = { dw = 0f; dh = 0f }, onDragEnd = {
                                                editEl(e.id) { el -> el.copy(w = (el.w + dw / widthPx).coerceIn(0.05f, 1f - el.x), h = (el.h + dh / heightPx).coerceIn(0.05f, 1f - el.y)) }
                                            }) { ch, a -> ch.consume(); dw += a.x; dh += a.y }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
        InfoText(tr("Element antippen zum Auswählen, ziehen zum Verschieben, roten Punkt ziehen zum Skalieren.", "Tap to select, drag to move, drag the red dot to resize."))
        Group(tr("Widget", "Widget")) {
            NovaTextField(def.name, { v -> save(def.copy(name = v)) }, Modifier.fillMaxWidth().padding(12.dp), tr("Name", "Name"))
            SliderRow(tr("Breite (Zellen)", "Width (cells)"), def.w.toFloat(), { v -> save(def.copy(w = v.roundToInt())) }, 1f..4f, { "${it.roundToInt()}" }, steps = 2)
            SliderRow(tr("Höhe (Zellen)", "Height (cells)"), def.h.toFloat(), { v -> save(def.copy(h = v.roundToInt())) }, 1f..4f, { "${it.roundToInt()}" }, steps = 2)
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaButton(tr("Auf Homescreen", "Add to home"), {
                    ctrl.addWidget(WidgetType.CUSTOM, 0, WidgetInstance(newId("w"), WidgetType.CUSTOM, def.style, mapOf("customId" to def.id)), def.w, def.h)
                })
                NovaButton(tr("Als Vorlage", "As template"), {
                    ctrl.saveTemplate(WidgetTemplate(newId("tpl"), def.name, TemplateCategory.EXPERIMENTAL, WidgetInstance("tpl", WidgetType.CUSTOM, def.style, mapOf("customId" to def.id)), def.w, def.h, def))
                }, primary = false)
            }
        }
        Group(tr("Element hinzufügen", "Add element")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElementKind.entries.forEach { k ->
                    NovaButton(k.label, {
                        val e = WidgetElement(newId("e"), k, 0.1f, 0.1f, if (k == ElementKind.ICON) 0.25f else 0.6f, if (k == ElementKind.ICON) 0.25f else 0.2f,
                            text = if (k == ElementKind.TEXT) tr("Text", "Text") else "")
                        save(def.copy(elements = def.elements + e)); selected = e.id
                    }, primary = false, icon = Icons.Filled.Add)
                }
            }
        }
        if (sel != null) Group(tr("Element: ", "Element: ") + sel.kind.label) {
            when (sel.kind) {
                ElementKind.TEXT -> NovaTextField(sel.text, { v -> editEl(sel.id) { it.copy(text = v) } }, Modifier.fillMaxWidth().padding(12.dp), tr("Text", "Text"))
                ElementKind.DOT_TEXT -> {
                    NovaTextField(sel.text, { v -> editEl(sel.id) { it.copy(text = v) } }, Modifier.fillMaxWidth().padding(12.dp), tr("Text (leer = Infofeld)", "Text (empty = info field)"))
                    ChoiceRow(tr("Infofeld", "Info field"), DataField.entries, sel.data, { it.label }, { v -> editEl(sel.id) { it.copy(data = v) } })
                }
                ElementKind.DATA -> ChoiceRow(tr("Infofeld", "Info field"), DataField.entries, sel.data, { it.label }, { v -> editEl(sel.id) { it.copy(data = v) } })
                ElementKind.ICON -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaIcons.names.forEach { n ->
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (n == sel.icon) Nova.colors.accent else Nova.colors.text.copy(alpha = 0.08f)).clickable { editEl(sel.id) { it.copy(icon = n) } }, contentAlignment = Alignment.Center) {
                            Icon(NovaIcons.get(n), n, tint = if (n == sel.icon) Nova.colors.onAccent else Nova.colors.text, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                ElementKind.PROGRESS -> ChoiceRow(tr("Quelle", "Source"), ProgressSource.entries, sel.progress, { it.label }, { v -> editEl(sel.id) { it.copy(progress = v) } })
                ElementKind.SHAPE -> SliderRow(tr("Eckenradius", "Corner radius"), sel.cornerDp, { v -> editEl(sel.id) { it.copy(cornerDp = v) } }, 0f..40f, { "${it.roundToInt()} dp" })
            }
            if (sel.kind == ElementKind.TEXT || sel.kind == ElementKind.DATA) {
                SliderRow(tr("Schriftgröße", "Font size"), sel.sizeSp, { v -> editEl(sel.id) { it.copy(sizeSp = v) } }, 8f..64f, { "${it.roundToInt()} sp" })
                SliderRow(tr("Gewicht", "Weight"), sel.weight.toFloat(), { v -> editEl(sel.id) { it.copy(weight = (v / 100).roundToInt() * 100) } }, 100f..900f, { "${it.roundToInt()}" }, steps = 7)
                ChoiceRow(tr("Ausrichtung", "Alignment"), TextAlign.entries, sel.align, { it.label }, { v -> editEl(sel.id) { it.copy(align = v) } })
            }
            ColorPickerRow(tr("Farbe", "Color"), sel.color, { v -> editEl(sel.id) { it.copy(color = v) } }, allowNone = true, onNone = { editEl(sel.id) { it.copy(color = null) } })
            SliderRow(tr("Deckkraft", "Opacity"), sel.alpha, { v -> editEl(sel.id) { it.copy(alpha = v) } }, 0.1f..1f)
            SettingRow(tr("Beim Antippen öffnen", "Open on tap"), (sel.action as? ShortcutTarget.App)?.let { c.apps.find(it.appKey)?.label } ?: tr("Nichts", "Nothing"), onClick = { pickApp = true })
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaButton(tr("Nach vorne", "Bring forward"), { save(def.copy(elements = def.elements.filter { it.id != sel.id } + sel)) }, primary = false)
                NovaButton(tr("Löschen", "Delete"), { save(def.copy(elements = def.elements.filter { it.id != sel.id })); selected = null }, primary = false, icon = Icons.Filled.Delete)
            }
        }
        Group(tr("Elemente", "Elements")) {
            def.elements.forEach { e ->
                SettingRow(e.kind.label, when (e.kind) { ElementKind.TEXT -> e.text; ElementKind.DATA, ElementKind.DOT_TEXT -> e.text.ifBlank { e.data.label }; ElementKind.ICON -> e.icon; ElementKind.PROGRESS -> e.progress.label; ElementKind.SHAPE -> "" },
                    onClick = { selected = e.id }, trailing = { if (e.id == selected) Icon(Icons.Filled.Edit, null, tint = Nova.colors.accent) })
            }
        }
        WidgetStyleEditor(def.style) { s -> save(def.copy(style = s)) }
    }
    if (pickApp && sel != null) AppPickerDialog(tr("App beim Antippen", "App on tap"), { pickApp = false }) { k -> editEl(sel.id) { it.copy(action = ShortcutTarget.App(k)) }; pickApp = false }
}
