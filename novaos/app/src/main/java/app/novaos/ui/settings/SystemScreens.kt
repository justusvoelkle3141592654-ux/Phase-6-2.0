package app.novaos.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.novaos.BuildConfig
import app.novaos.design.ChoiceRow
import app.novaos.design.DotMatrixText
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaTextField
import app.novaos.design.SettingRow
import app.novaos.design.SliderRow
import app.novaos.design.SwitchRow
import app.novaos.model.AutoRule
import app.novaos.model.BackupContent
import app.novaos.model.BackupReadResult
import app.novaos.model.BackupSection
import app.novaos.model.NovaExport
import app.novaos.model.ProfileKind
import app.novaos.model.RuleCondition
import app.novaos.model.TemperatureUnit
import app.novaos.model.describe
import app.novaos.model.newId
import app.novaos.system.GeoResult
import app.novaos.system.HomeRole
import app.novaos.system.NovaPermission
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.tr
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ============================================================================ Allgemein & Wetter

@Composable
fun GeneralScreen() {
    val c = LocalContainer.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var isDefault by remember { mutableStateOf(HomeRole.isDefault(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = HomeRole.isDefault(ctx) }
    SettingsScreen(tr("Allgemein", "General")) {
        Group(tr("Standard-Startbildschirm", "Default home app")) {
            SettingRow(
                if (isDefault) tr("NovaOS ist Standard-Startbildschirm", "NovaOS is the default home app") else tr("NovaOS als Standard festlegen", "Set NovaOS as default"),
                tr("Android fragt immer selbst nach deiner Bestätigung.", "Android always asks for your confirmation."),
                onClick = { c.apps.startIntent(HomeRole.requestIntent(ctx) ?: HomeRole.homeSettingsIntent()) },
                trailing = { if (isDefault) Icon(Icons.Filled.Check, null, tint = Nova.colors.accent) },
            )
        }
        Group(tr("Sprache & Uhr", "Language & clock")) {
            SettingRow(tr("Sprache", "Language"), tr("Folgt der Systemsprache (Deutsch/Englisch). Ändern in den Android-Einstellungen pro App.", "Follows system language (German/English)."),
                onClick = { c.apps.startIntent(android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))) })
            SettingRow(tr("24-Stunden-Format", "24-hour format"), tr("Folgt der Android-Einstellung", "Follows Android setting"), onClick = { c.apps.startIntent(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)) })
        }
        Group(tr("Wetter", "Weather")) { SettingRow(tr("Ort & Einheit", "Location & unit"), onClick = { nav.push("weather") }) }
        Group(tr("Apps", "Apps")) {
            SwitchRow(tr("Vor dem Deinstallieren nachfragen", "Confirm before uninstall"), tr("Android zeigt zusätzlich immer einen eigenen Dialog", "Android always shows its own dialog too"), LocalConfig.current.general.confirmBeforeUninstall, { on -> c.controller.editGeneral { it.copy(confirmBeforeUninstall = on) } })
        }
        Group(tr("Einrichtung", "Setup")) {
            SettingRow(tr("Einrichtungsassistent erneut starten", "Run setup assistant again"), onClick = { nav.push("setup") })
        }
    }
}

@Composable
fun WeatherScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val w = LocalConfig.current.general.weather
    val req = LocalRequestPermission.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(w.cityName) }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val cache = c.weatherCache.data.value
    SettingsScreen(tr("Wetter", "Weather")) {
        InfoText(tr("Daten von Open-Meteo (kostenlos, ohne Konto, ohne API-Schlüssel). Abruf höchstens alle 30 Minuten, nur wenn der Homescreen sichtbar ist.", "Data from Open-Meteo (free, no account, no key). At most every 30 minutes while the home screen is visible."))
        Group {
            SwitchRow(tr("Wetter verwenden", "Use weather"), tr("Aus = keine Internetverbindung durch NovaOS", "Off = NovaOS uses no internet"), w.enabled, { on -> ctrl.editGeneral { it.copy(weather = it.weather.copy(enabled = on)) } })
            ChoiceRow(tr("Einheit", "Unit"), TemperatureUnit.entries, w.unit, { if (it == TemperatureUnit.CELSIUS) "°C" else "°F" }, { u -> ctrl.editGeneral { it.copy(weather = it.weather.copy(unit = u)) } })
            SwitchRow(tr("Aktuellen Standort verwenden", "Use current location"), tr("Benötigt ungefähren Standort", "Requires approximate location"), w.useDeviceLocation, { on ->
                if (on) req(Manifest.permission.ACCESS_COARSE_LOCATION)
                ctrl.editGeneral { it.copy(weather = it.weather.copy(useDeviceLocation = on)) }
            })
        }
        Group(tr("Ort manuell", "Manual location")) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaTextField(q, { q = it }, Modifier.weight(1f), tr("Stadt eingeben", "Enter city"))
                Spacer(Modifier.width(8.dp))
                NovaButton(tr("Suchen", "Search"), { searching = true; scope.launch { results = c.weather.searchCity(q); searching = false } })
            }
            if (searching) InfoText(tr("Suche …", "Searching …"))
            results.forEach { r ->
                SettingRow(r.name, listOf(r.admin, r.country).filter { it.isNotBlank() }.joinToString(", "), onClick = {
                    ctrl.editGeneral { it.copy(weather = it.weather.copy(cityName = r.name, latitude = r.latitude, longitude = r.longitude, useDeviceLocation = false)) }
                    results = emptyList(); q = r.name
                    scope.launch { c.weather.refreshIfNeeded(w.copy(cityName = r.name, latitude = r.latitude, longitude = r.longitude, useDeviceLocation = false), force = true) }
                })
            }
            if (w.latitude != null) InfoText(tr("Gewählt: ${w.cityName} (%.2f, %.2f)".format(w.latitude, w.longitude), "Selected: ${w.cityName}"))
        }
        Group(tr("Status", "Status")) {
            InfoText(
                if (cache.fetchedAtMs == 0L) tr("Noch nicht abgerufen", "Not fetched yet")
                else tr("Zuletzt: ", "Last: ") + Instant.ofEpochMilli(cache.fetchedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")) + (cache.error?.let { " – $it" } ?: ""),
            )
            SettingRow(tr("Jetzt aktualisieren", "Refresh now"), onClick = { scope.launch { c.weather.refreshIfNeeded(LocalConfigSnapshot.weather(c), force = true) } })
        }
    }
}

private object LocalConfigSnapshot {
    fun weather(c: app.novaos.AppContainer) = c.config.config.value?.general?.weather ?: app.novaos.model.WeatherSettings()
}

// ============================================================================ Profile

@Composable
fun ProfilesScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val nav = LocalNav.current
    val cfg = LocalConfig.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf(ProfileKind.CUSTOM) }
    var exportId by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val p = cfg.profiles.firstOrNull { it.id == exportId }
        if (uri != null && p != null) scope.launch { c.backup.writeText(uri, NovaExport.encode(NovaExport(NovaExport.PROFILE, profile = p))) }
        exportId = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val p = c.backup.readExport(uri, NovaExport.PROFILE)?.profile
            if (p != null) ctrl.importProfile(p) else Toast.makeText(ctx, tr("Keine gültige Profil-Datei", "Not a valid profile file"), Toast.LENGTH_LONG).show()
        }
    }
    SettingsScreen(tr("Profile", "Profiles")) {
        Group(tr("Deine Profile", "Your profiles")) {
            cfg.profiles.forEach { p ->
                val active = p.id == cfg.activeProfileId
                Column {
                    SettingRow(
                        p.name + if (active) tr(" · aktiv", " · active") else "",
                        "${p.kind.label} · ${p.theme.name}",
                        onClick = { ctrl.switchProfile(p.id) },
                        trailing = {
                            Row {
                                NovaIconButton(Icons.Filled.Edit, tr("Umbenennen", "Rename"), { renaming = p.id; name = p.name })
                                NovaIconButton(Icons.Filled.ContentCopy, tr("Duplizieren", "Duplicate"), { ctrl.duplicateProfile(p.id) })
                                NovaIconButton(Icons.Filled.Upload, tr("Exportieren", "Export"), { exportId = p.id; exportLauncher.launch("${p.name}.novaprofile.json") })
                                if (cfg.profiles.size > 1) NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.deleteProfile(p.id) })
                            }
                        },
                    )
                    if (renaming == p.id) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaTextField(name, { name = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        NovaButton(tr("OK", "OK"), { ctrl.renameProfile(p.id, name); renaming = null })
                    }
                }
            }
        }
        Group(tr("Neues Profil", "New profile")) {
            ChoiceRow(tr("Vorlage", "Template"), ProfileKind.entries, newKind, { it.label }, { newKind = it })
            NovaTextField(name, { name = it }, Modifier.fillMaxWidth().padding(12.dp), tr("Name", "Name"))
            Row(Modifier.padding(12.dp)) { NovaButton(tr("Profil erstellen", "Create profile"), { ctrl.createProfile(newKind, name); name = "" }) }
            SettingRow(tr("Profil importieren", "Import profile"), icon = Icons.Filled.Download, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
        }
        Group(tr("Automatisch wechseln", "Switch automatically")) {
            SwitchRow(tr("Regeln aktiv", "Rules active"), tr("Geprüft, wenn der Homescreen erscheint", "Checked when the home screen appears"), cfg.autoSwitchEnabled, { on -> ctrl.update { it.copy(autoSwitchEnabled = on) } })
            SettingRow(tr("Regeln bearbeiten", "Edit rules"), tr("${cfg.autoRules.size} Regeln", "${cfg.autoRules.size} rules"), onClick = { nav.push("profile_rules") })
        }
    }
}

@Composable
fun ProfileRulesScreen() {
    val c = LocalContainer.current
    val ctrl = c.controller
    val cfg = LocalConfig.current
    val req = LocalRequestPermission.current
    var type by remember { mutableIntStateOf(0) }
    var profileId by remember { mutableStateOf(cfg.profiles.first().id) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var start by remember { mutableIntStateOf(8 * 60) }
    var end by remember { mutableIntStateOf(17 * 60) }
    var text by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    SettingsScreen(tr("Profil-Regeln", "Profile rules")) {
        InfoText(tr("Passt keine Regel, kehrt NovaOS zum zuletzt manuell gewählten Profil zurück. Es läuft kein Hintergrunddienst – die Regeln werden geprüft, wenn der Homescreen sichtbar wird.", "If no rule matches, NovaOS returns to the last manually chosen profile. No background service."))
        Group(tr("Regeln", "Rules")) {
            if (cfg.autoRules.isEmpty()) InfoText(tr("Noch keine Regeln.", "No rules yet."))
            cfg.autoRules.forEach { r ->
                SettingRow(
                    "${cfg.profiles.firstOrNull { it.id == r.profileId }?.name ?: "?"} ← ${r.condition.describe()}",
                    tr("Priorität ${r.priority}", "Priority ${r.priority}"),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            app.novaos.design.NovaSwitch(r.enabled, { on -> ctrl.setRules(cfg.autoRules.map { if (it.id == r.id) it.copy(enabled = on) else it }) })
                            NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { ctrl.setRules(cfg.autoRules.filter { it.id != r.id }) })
                        }
                    },
                )
            }
        }
        Group(tr("Neue Regel", "New rule")) {
            ChoiceRow(tr("Profil", "Profile"), cfg.profiles.map { it.id }, profileId, { id -> cfg.profiles.first { it.id == id }.name }, { profileId = it })
            ChoiceRow(tr("Bedingung", "Condition"), listOf(0, 1, 2), type, { listOf(tr("Uhrzeit/Tage", "Time/days"), "Bluetooth", tr("WLAN", "Wi-Fi"))[it] }, { type = it })
            when (type) {
                0 -> {
                    val names = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d -> app.novaos.design.NovaChip(names[d - 1], d in days, { days = if (d in days) days - d else days + d }) }
                    }
                    SliderRow(tr("Start", "Start"), start.toFloat(), { start = (it / 15).roundToInt() * 15 }, 0f..1425f, { "%02d:%02d".format(it.roundToInt() / 60, it.roundToInt() % 60) })
                    SliderRow(tr("Ende", "End"), end.toFloat(), { end = (it / 15).roundToInt() * 15 }, 0f..1440f, { "%02d:%02d".format(it.roundToInt() / 60 % 24, it.roundToInt() % 60) })
                }
                1 -> {
                    PermissionBadge(tr("Bluetooth-Geräte", "Bluetooth devices"), c.profileContext.hasBluetoothPermission()) { req(Manifest.permission.BLUETOOTH_CONNECT) }
                    NovaTextField(text, { text = it }, Modifier.fillMaxWidth().padding(12.dp), tr("Gerätename (leer = beliebig)", "Device name (empty = any)"))
                    val connected = c.profileContext.bluetoothDevices.value
                    if (connected.isNotEmpty()) connected.forEach { n -> SettingRow(tr("Verbunden: $n", "Connected: $n"), onClick = { text = n }) }
                }
                2 -> {
                    PermissionBadge(tr("Genauer Standort (für WLAN-Namen)", "Precise location (for Wi-Fi name)"), c.profileContext.hasLocationPermission()) { req(Manifest.permission.ACCESS_FINE_LOCATION) }
                    NovaTextField(text, { text = it }, Modifier.fillMaxWidth().padding(12.dp), tr("WLAN-Name (SSID)", "Wi-Fi name (SSID)"))
                    c.profileContext.ssid.value?.let { s -> SettingRow(tr("Aktuelles WLAN: $s", "Current Wi-Fi: $s"), onClick = { text = s }) }
                }
            }
            SliderRow(tr("Priorität", "Priority"), priority.toFloat(), { priority = it.roundToInt() }, 0f..10f, { "${it.roundToInt()}" }, steps = 9)
            Row(Modifier.padding(12.dp)) {
                NovaButton(tr("Regel hinzufügen", "Add rule"), {
                    val cond = when (type) {
                        0 -> RuleCondition.TimeWindow(days, start, end % 1440)
                        1 -> RuleCondition.BluetoothDevice(text.ifBlank { null })
                        else -> if (text.isBlank()) null else RuleCondition.WifiNetwork(text.trim())
                    }
                    if (cond != null) { ctrl.setRules(cfg.autoRules + AutoRule(newId("rule"), profileId, cond, priority = priority)); text = "" }
                })
            }
        }
    }
}

// ============================================================================ Datenschutz & Berechtigungen

@Composable
fun PrivacyScreen() {
    SettingsScreen(tr("Datenschutz", "Privacy")) {
        Group(tr("Was NovaOS speichert", "What NovaOS stores")) {
            listOf(
                tr("Alle Einstellungen, Layouts, Themes, Aufgaben und Notizen liegen nur auf diesem Gerät (App-Speicher).", "All settings, layouts, themes, tasks and notes are stored only on this device."),
                tr("NovaOS hat kein Konto, keine Analyse und keine Werbung.", "No account, no analytics, no ads."),
                tr("Android-Cloud-Sicherung ist deaktiviert; Sicherungen erstellst du selbst als Datei (Backup).", "Android cloud backup is disabled; you create backups as files yourself."),
                tr("Startzähler für „Meistgenutzt“ werden nur lokal gezählt (abschaltbar unter Performance).", "Launch counters for “most used” stay local (can be disabled)."),
            ).forEach { InfoText("• $it") }
        }
        Group(tr("Internet", "Internet")) {
            InfoText(tr("Einzige Netzwerkverbindung: Wetterdaten von api.open-meteo.com und Ortssuche über geocoding-api.open-meteo.com. Übertragen werden nur Koordinaten bzw. der eingegebene Ortsname. Abschaltbar unter Allgemein → Wetter.", "Only network use: weather from api.open-meteo.com and city search via geocoding-api.open-meteo.com. Only coordinates or the typed city name are sent. Can be disabled."))
        }
        Group(tr("Gelesene Daten (nur mit Freigabe)", "Data read (only with permission)")) {
            InfoText(tr("Benachrichtigungen, Kalender, Kontakte, Bluetooth-Gerätenamen, WLAN-Name und Nutzungsdaten werden nur angezeigt bzw. für Profilregeln ausgewertet und nicht gespeichert oder weitergegeben.", "Notifications, calendar, contacts, Bluetooth names, Wi-Fi name and usage data are only displayed or used for rules; never stored or shared."))
        }
    }
}

@Composable
fun PermissionsScreen() {
    val c = LocalContainer.current
    val req = LocalRequestPermission.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    SettingsScreen(tr("Berechtigungen", "Permissions")) {
        InfoText(tr("NovaOS fragt Freigaben nur, wenn du eine Funktion nutzt. Keine Root-Rechte, keine Bedienungshilfen-Umgehung, keine versteckten Systemberechtigungen.", "NovaOS asks only when you use a feature. No root, no accessibility workarounds, no hidden system permissions."))
        Group {
            NovaPermission.entries.forEach { p ->
                @Suppress("UNUSED_VARIABLE") val t = tick
                val granted = c.permissions.granted(p)
                SettingRow(p.title, p.purpose, onClick = {
                    val i = c.permissions.settingsIntent(p)
                    if (i != null) c.apps.startIntent(i) else p.runtime?.let { req(it) }
                }, trailing = {
                    Text(if (granted) tr("Erteilt", "Granted") else tr("Erlauben", "Allow"), color = if (granted) Nova.colors.textSecondary else Nova.colors.accent, style = Nova.type.caption.copy(fontWeight = FontWeight.SemiBold))
                })
            }
        }
        Group {
            SettingRow(tr("Android-App-Einstellungen öffnen", "Open Android app settings"), tr("Freigaben dort jederzeit widerrufen", "Revoke permissions there any time"), onClick = { c.apps.startIntent(c.permissions.appSettingsIntent()) })
            if (c.controls.isAdminActive()) SettingRow(tr("Geräteadministrator entfernen", "Remove device admin"), tr("Nötig vor einer Deinstallation von NovaOS", "Required before uninstalling NovaOS"), onClick = { c.controls.removeAdmin(); tick++ })
        }
    }
}

// ============================================================================ Backup

@Composable
fun BackupScreen() {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sections by remember { mutableStateOf(BackupSection.entries.toSet()) }
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupContent?>(null) }
    var restoreSections by remember { mutableStateOf(BackupSection.entries.toSet()) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val r = c.backup.export(uri, sections)
            status = r.fold({ tr("Backup gespeichert ($it Dateien eingeschlossen).", "Backup saved ($it files included).") }, { tr("Fehler: ${it.message}", "Error: ${it.message}") })
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            when (val r = c.backup.read(uri)) {
                is BackupReadResult.Ok -> { pending = r.content; restoreSections = r.content.manifest.sections.toSet(); status = null }
                is BackupReadResult.Failed -> { pending = null; status = r.problems.joinToString("\n") { "• " + it.message } }
            }
        }
    }
    SettingsScreen(tr("Backup & Restore", "Backup & restore")) {
        InfoText(tr("Format: .novabackup (ZIP mit manifest.json, config.json, productivity.json und Bildern). Prüfsummen erkennen beschädigte oder veränderte Dateien.", "Format: .novabackup (ZIP with manifest, config, productivity and images). Checksums detect damaged files."))
        Group(tr("Sichern", "Back up")) {
            BackupSection.entries.forEach { s ->
                SwitchRow(s.label, null, s in sections, { on -> sections = if (on) sections + s else sections - s })
            }
            Row(Modifier.padding(12.dp)) {
                NovaButton(tr("Backup erstellen", "Create backup"), {
                    val stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
                    create.launch("NovaOS_$stamp.novabackup")
                }, icon = Icons.Filled.Upload)
            }
        }
        Group(tr("Wiederherstellen", "Restore")) {
            Row(Modifier.padding(12.dp)) { NovaButton(tr("Backup-Datei wählen", "Choose backup file"), { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, primary = false, icon = Icons.Filled.Download) }
            pending?.let { b ->
                val date = Instant.ofEpochMilli(b.manifest.createdAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                InfoText(tr("Gültiges Backup vom $date (NovaOS ${b.manifest.appVersion}), ${b.config.profiles.size} Profile, ${b.files.size} Dateien.", "Valid backup from $date, ${b.config.profiles.size} profiles, ${b.files.size} files."))
                b.manifest.sections.forEach { s -> SwitchRow(s.label, null, s in restoreSections, { on -> restoreSections = if (on) restoreSections + s else restoreSections - s }) }
                Row(Modifier.padding(12.dp)) {
                    NovaButton(tr("Ausgewähltes wiederherstellen", "Restore selected"), {
                        scope.launch {
                            c.backup.restore(b, restoreSections)
                            status = tr("Wiederhergestellt.", "Restored."); pending = null
                            c.icons.clear()
                        }
                    })
                }
            }
        }
        status?.let { s -> Group { InfoText(s) } }
    }
}

// ============================================================================ Über

@Composable
fun AboutScreen() {
    SettingsScreen(tr("Über NovaOS", "About NovaOS")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DotMatrixText("NOVAOS", dotSize = 5.dp, color = Nova.colors.text, dimColor = Nova.colors.text.copy(alpha = 0.06f))
        }
        InfoText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Group(tr("Implementierte Bereiche", "Implemented areas")) {
            listOf(
                "Launcher Core (Home-Rolle, LauncherApps, Arbeitsprofil)", "Homescreen mit Raster und freiem Layout, Seiten, Dock, Ordner",
                "Nova App Drawer", "Nova Customization Engine mit Live-Vorschau", "Icon-Studio inkl. Punkt-Icons und Icon-Paketen",
                "Nova Widget Engine (27 Widget-Typen) + Android-Widgets", "Widget-Editor, Widget Creator, Vorlagen mit Import/Export",
                "Nova Control Center (5 Layouts)", "Nova Notification Center", "Nova Gesture Engine", "Theme-System mit 9 Designs (lokaler Marketplace)",
                "Profile mit automatischen Regeln", "Wallpaper Engine", "Einrichtungsassistent", "Backup & Restore",
            ).forEach { InfoText("• $it") }
        }
        Group(tr("Schriften & Lizenzen", "Fonts & licenses")) {
            InfoText("Space Grotesk, Inter, Space Mono, JetBrains Mono, Doto – SIL Open Font License 1.1. Nova Dot Matrix – Eigenentwicklung.")
            InfoText(tr("Wetterdaten: Open-Meteo.com (CC BY 4.0).", "Weather data: Open-Meteo.com (CC BY 4.0)."))
            InfoText("AndroidX, Jetpack Compose, Kotlin, kotlinx.serialization – Apache License 2.0.")
        }
    }
}

@Suppress("unused")
private fun noop() = Unit
