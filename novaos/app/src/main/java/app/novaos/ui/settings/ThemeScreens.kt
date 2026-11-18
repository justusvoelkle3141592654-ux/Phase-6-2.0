package app.novaos.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaTextField
import app.novaos.design.SettingRow
import app.novaos.design.SwitchRow
import app.novaos.model.NovaExport
import app.novaos.model.ThemePresets
import app.novaos.model.ThemeSpec
import app.novaos.model.newId
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.MiniHome
import app.novaos.ui.common.tr
import kotlinx.coroutines.launch

/** Lokaler Theme-Marketplace: durchsuchen, Vorschau, installieren, bearbeiten, erstellen, exportieren, importieren. */
@Composable
fun ThemesScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val nav = LocalNav.current
    val cfg = LocalConfig.current
    val p = LocalProfile.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var withWallpaper by remember { mutableStateOf(true) }
    var newName by remember { mutableStateOf("") }
    var exportTheme by remember { mutableStateOf<ThemeSpec?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val t = exportTheme
        if (uri != null && t != null) scope.launch {
            // Eigene Bild-Hintergründe sind gerätespezifisch und werden beim Export durch den Standard ersetzt.
            val portable = t.copy(wallpaper = t.wallpaper?.takeIf { it.source !is app.novaos.model.WallpaperSource.Image })
            c.backup.writeText(uri, NovaExport.encode(NovaExport(NovaExport.THEME, theme = portable)))
        }
        exportTheme = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val t = c.backup.readExport(uri, NovaExport.THEME)?.theme
            if (t != null) {
                ctrl.upsertUserTheme(t.copy(id = newId("theme"), builtIn = false))
                Toast.makeText(ctx, tr("Theme „${t.name}“ importiert", "Theme “${t.name}” imported"), Toast.LENGTH_SHORT).show()
            } else Toast.makeText(ctx, tr("Keine gültige Theme-Datei", "Not a valid theme file"), Toast.LENGTH_LONG).show()
        }
    }
    val all = ThemePresets.all + cfg.userThemes
    val shown = all.filter { q.isBlank() || it.name.contains(q, true) || it.description.contains(q, true) }

    SettingsScreen(tr("Themes", "Themes")) {
        NovaTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), tr("Themes durchsuchen …", "Search themes …"))
        SwitchRow(tr("Hintergrund des Themes übernehmen", "Apply theme wallpaper"), null, withWallpaper, { withWallpaper = it })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            shown.forEach { t ->
                val active = p.theme.id == t.id
                Column(
                    Modifier.width(170.dp).clip(RoundedCornerShape(26.dp))
                        .border(if (active) 2.dp else 1.dp, if (active) Nova.colors.accent else Nova.colors.border, RoundedCornerShape(26.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val previewProfile = p.copy(theme = t, wallpaper = if (withWallpaper) t.wallpaper ?: p.wallpaper else p.wallpaper)
                    MiniHome(previewProfile, width = 148.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(t.name, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), color = Nova.colors.text, maxLines = 1)
                    Text(t.description, style = Nova.type.caption, color = Nova.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    NovaButton(if (active) tr("Aktiv", "Active") else tr("Installieren", "Install"), { ctrl.applyTheme(t, withWallpaper) }, enabled = !active)
                    Row {
                        NovaIconButton(Icons.Filled.Upload, tr("Exportieren", "Export"), { exportTheme = t; exportLauncher.launch("${t.name}.novatheme.json") })
                        if (!t.builtIn) NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.deleteUserTheme(t.id) })
                    }
                }
            }
        }
        Group(tr("Aktuelles Theme bearbeiten", "Edit current theme")) {
            SettingRow(tr("Farben", "Colors"), icon = Icons.Filled.Edit, onClick = { nav.push("colors") })
            SettingRow(tr("Typografie", "Typography"), onClick = { nav.push("typography") })
            SettingRow(tr("Icons", "Icons"), onClick = { nav.push("icons") })
            SettingRow(tr("Animationen", "Animations"), onClick = { nav.push("animations") })
            SettingRow(tr("Liquid Glass", "Liquid glass"), onClick = { nav.push("glass") })
            SettingRow(tr("Dock & Homescreen", "Dock & home"), onClick = { nav.push("home") })
            SettingRow(tr("Kontrollzentrum", "Control center"), onClick = { nav.push("control_center") })
            SettingRow(tr("App Drawer", "App drawer"), onClick = { nav.push("drawer") })
            SettingRow(tr("Benachrichtigungen", "Notifications"), onClick = { nav.push("notifications") })
            SettingRow(tr("Hintergrund", "Wallpaper"), onClick = { nav.push("wallpaper") })
            InfoText(tr("Änderungen gelten für das aktive Profil. Speichere sie als eigenes Theme, um sie wiederzuverwenden.", "Changes apply to the active profile. Save as own theme to reuse them."))
        }
        Group(tr("Eigenes Theme erstellen", "Create own theme")) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaTextField(newName, { newName = it }, Modifier.weight(1f), tr("Name des Themes", "Theme name"))
                Spacer(Modifier.width(8.dp))
                NovaButton(tr("Speichern", "Save"), {
                    val t = ctrl.saveThemeAs(newName)
                    ctrl.applyTheme(t, false)
                    newName = ""
                })
            }
            SettingRow(tr("Theme importieren", "Import theme"), icon = Icons.Filled.Download, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
            InfoText(tr("Eine Online-Community zum Teilen ist für eine spätere Version geplant; Themes funktionieren vollständig lokal und per Datei.", "An online community is planned for later; themes work fully locally and via files."))
        }
    }
}
