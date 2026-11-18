package app.novaos.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.novaos.data.FileStore
import app.novaos.design.ChoiceRow
import app.novaos.design.ColorPickerRow
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaIcons
import app.novaos.design.NovaTextField
import app.novaos.design.SettingRow
import app.novaos.design.SliderRow
import app.novaos.design.hexColor
import app.novaos.model.IconOverride
import app.novaos.model.IconRendering
import app.novaos.model.IconShape
import app.novaos.model.IconStyleSpec
import app.novaos.model.NovaExport
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
private fun IconStyleEditor(style: IconStyleSpec, onChange: (IconStyleSpec) -> Unit) {
    ChoiceRow(tr("Form", "Shape"), IconShape.entries, style.shape, { it.label }, { onChange(style.copy(shape = it)) })
    ChoiceRow(tr("Darstellung", "Rendering"), IconRendering.entries, style.rendering, { it.label }, { onChange(style.copy(rendering = it)) })
    SliderRow(tr("Symbolgröße", "Glyph size"), style.glyphScale, { onChange(style.copy(glyphScale = it)) }, 0.5f..1.2f, { "${(it * 100).roundToInt()} %" })
    ColorPickerRow(tr("Hintergrundfarbe", "Background color"), style.background, { onChange(style.copy(background = it)) }, allowNone = true, onNone = { onChange(style.copy(background = null)) })
    ColorPickerRow(tr("Symbolfarbe", "Symbol color"), style.foreground, { onChange(style.copy(foreground = it)) }, allowNone = true, onNone = { onChange(style.copy(foreground = null)) })
    SliderRow(tr("Kontur", "Outline"), style.strokeWidth, { onChange(style.copy(strokeWidth = it)) }, 0f..4f, { "%.1f".format(it) })
    if (style.strokeWidth > 0f || style.shape == IconShape.DOT_RING) ColorPickerRow(tr("Konturfarbe", "Outline color"), style.strokeColor, { onChange(style.copy(strokeColor = it)) }, allowNone = true, onNone = { onChange(style.copy(strokeColor = null)) })
    SliderRow(tr("Schatten", "Shadow"), style.shadow, { onChange(style.copy(shadow = it)) })
    SliderRow(tr("Transparenz", "Opacity"), style.alpha, { onChange(style.copy(alpha = it)) }, 0.2f..1f)
    if (style.rendering == IconRendering.DOTS) SliderRow(tr("Punktraster", "Dot grid"), style.dotGrid.toFloat(), { onChange(style.copy(dotGrid = it.roundToInt())) }, 8f..36f, { "${it.roundToInt()}" })
}

/** Icon-Studio: globaler Stil, Einzel-Icons, eigene/importierte Icons, Icon-Pakete. */
@Composable
fun IconStudioScreen(appKey: String?) {
    val c = LocalContainer.current
    val ctrl = c.controller
    val cfg = LocalConfig.current
    val p = LocalProfile.current
    val apps by c.apps.apps.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(appKey) }
    var packName by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val sample = remember(apps, p.layout.dockApps) { (p.layout.dockApps + apps.take(12).map { it.key }).distinct().take(10) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val packId = pendingExport
        if (uri != null && packId != null) {
            val pack = cfg.iconPacks.firstOrNull { it.id == packId }
            if (pack != null) scope.launch { c.backup.writeText(uri, NovaExport.encode(NovaExport(NovaExport.ICON_PACK, iconPack = pack))) }
        }
        pendingExport = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val e = c.backup.readExport(uri, NovaExport.ICON_PACK)
            if (e?.iconPack != null) ctrl.importIconPack(e.iconPack!!)
            else android.widget.Toast.makeText(ctx, tr("Keine gültige Icon-Paket-Datei", "Not a valid icon pack file"), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = selected
        if (uri != null && key != null) scope.launch {
            val path = withContext(Dispatchers.IO) { c.files.importImage(uri, FileStore.ICONS, 512, png = true) }
            if (path != null) ctrl.setIconOverride(key, (p.iconOverrides[key] ?: IconOverride()).copy(customIconFile = path))
        }
    }

    SettingsScreen(tr("Icon-Studio", "Icon studio"), preview = true) {
        Group(tr("Vorschau", "Preview")) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                sample.forEach { k ->
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).border(if (k == selected) 2.dp else 0.dp, if (k == selected) Nova.colors.accent else Color.Transparent, RoundedCornerShape(14.dp)).clickable { selected = k }.padding(4.dp)) {
                        AppIconImage(k, 52.dp)
                    }
                }
            }
            InfoText(tr("Tippe auf ein Icon, um es einzeln zu bearbeiten.", "Tap an icon to edit it individually."))
        }
        Group(tr("Stil für alle Icons", "Style for all icons")) {
            IconStyleEditor(p.theme.icons) { s -> ctrl.editTheme { it.copy(icons = s) } }
        }
        val key = selected
        if (key != null) {
            val o = p.iconOverrides[key] ?: IconOverride()
            Group(tr("Einzelnes Icon: ", "Single icon: ") + (c.apps.find(key)?.label ?: key)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(key, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        NovaButton(tr("Bild importieren", "Import image"), { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, primary = false)
                        Spacer(Modifier.height(6.dp))
                        NovaButton(tr("Zurücksetzen", "Reset"), { ctrl.setIconOverride(key, null) }, primary = false)
                    }
                }
                NovaTextField(o.label ?: "", { v -> ctrl.setIconOverride(key, o.copy(label = v.ifBlank { null })) }, Modifier.fillMaxWidth().padding(12.dp), tr("Eigener App-Name", "Custom app name"))
                IconCreator { bmp ->
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { c.files.saveBitmap(bmp.asAndroidBitmap(), FileStore.ICONS) }
                        ctrl.setIconOverride(key, o.copy(customIconFile = path))
                    }
                }
                val custom = o.style != null
                SettingRow(tr("Eigener Stil für dieses Icon", "Own style for this icon"), if (custom) tr("Aktiv", "Active") else tr("Nutzt den globalen Stil", "Uses global style"), onClick = {
                    ctrl.setIconOverride(key, o.copy(style = if (custom) null else p.theme.icons))
                })
                if (o.style != null) IconStyleEditor(o.style!!) { s -> ctrl.setIconOverride(key, o.copy(style = s)) }
            }
        }
        Group(tr("Icon-Pakete", "Icon packs")) {
            cfg.iconPacks.forEach { pack ->
                SettingRow(pack.name, tr("${pack.overrides.size} Einzel-Icons · ${pack.style.shape.label}", "${pack.overrides.size} icons · ${pack.style.shape.label}"), onClick = { ctrl.applyIconPack(pack) }, trailing = {
                    Row {
                        NovaIconButton(Icons.Filled.Upload, tr("Exportieren", "Export"), { pendingExport = pack.id; exportLauncher.launch("${pack.name}.novaiconpack.json") })
                        NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.deleteIconPack(pack.id) })
                    }
                })
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaTextField(packName, { packName = it }, Modifier.weight(1f), tr("Name des Pakets", "Pack name"))
                Spacer(Modifier.width(8.dp))
                NovaButton(tr("Speichern", "Save"), { ctrl.saveIconPack(packName); packName = "" })
            }
            SettingRow(tr("Icon-Paket importieren", "Import icon pack"), icon = Icons.Filled.Download, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
        }
    }
}

/** Eigenes Icon gestalten: Symbol oder Buchstaben auf Form und Farbe – ohne Programmierkenntnisse. */
@Composable
private fun IconCreator(onCreate: (ImageBitmap) -> Unit) {
    var glyph by remember { mutableStateOf("star") }
    var bg by remember { mutableStateOf("#16161A") }
    var fg by remember { mutableStateOf("#F2F2F2") }
    var round by remember { mutableStateOf(0.3f) }
    val painter = rememberVectorPainter(NovaIcons.get(glyph))
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    Column(Modifier.padding(12.dp)) {
        Text(tr("Icon erstellen", "Create icon"), style = Nova.type.body, color = Nova.colors.text)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape((round * 32).dp)).background(hexColor(bg, Color.Black)), contentAlignment = Alignment.Center) {
                Icon(NovaIcons.get(glyph), null, tint = hexColor(fg, Color.White), modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(12.dp))
            NovaButton(tr("Als Icon verwenden", "Use as icon"), {
                val size = 256
                val img = ImageBitmap(size, size)
                val canvas = androidx.compose.ui.graphics.Canvas(img)
                CanvasDrawScope().draw(density, dir, canvas, Size(size.toFloat(), size.toFloat())) {
                    drawRoundRect(hexColor(bg, Color.Black), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * round, size * round))
                    val inset = size * 0.22f
                    translate(inset, inset) {
                        with(painter) { draw(Size(size - inset * 2, size - inset * 2), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(hexColor(fg, Color.White))) }
                    }
                }
                onCreate(img)
            })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NovaIcons.names.forEach { n ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (n == glyph) Nova.colors.accent else Nova.colors.text.copy(alpha = 0.08f)).clickable { glyph = n },
                    contentAlignment = Alignment.Center,
                ) { Icon(NovaIcons.get(n), n, tint = if (n == glyph) Nova.colors.onAccent else Nova.colors.text, modifier = Modifier.size(20.dp)) }
            }
        }
        ColorPickerRow(tr("Hintergrund", "Background"), bg, { bg = it })
        ColorPickerRow(tr("Symbol", "Symbol"), fg, { fg = it })
        SliderRow(tr("Rundung", "Roundness"), round, { round = it }, 0f..0.5f)
    }
}
