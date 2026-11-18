package app.novaos.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.novaos.design.ChipRow
import app.novaos.design.ColorPickerRow
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaTextField
import app.novaos.design.SettingRow
import app.novaos.model.Note
import app.novaos.model.ProductivityData
import app.novaos.model.Project
import app.novaos.model.TaskItem
import app.novaos.model.newId
import app.novaos.system.hasPermission
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.tr
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ProdTab { TASKS, PROJECTS, NOTES }

/** Daten für Aufgaben-, Projekt-, Notiz- und Erinnerungs-Widgets (lokal gespeichert). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivityScreen(section: String?) {
    val c = LocalContainer.current
    val data by c.productivity.data.collectAsState()
    val scope = rememberCoroutineScope()
    val req = LocalRequestPermission.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(when { section?.startsWith("notes") == true -> ProdTab.NOTES; else -> ProdTab.TASKS }) }
    var text by remember { mutableStateOf("") }
    var projectFor by remember { mutableStateOf<String?>(null) }
    var dateFor by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // taskId, isReminder
    var timeFor by remember { mutableStateOf<Pair<String, Long>?>(null) }
    fun upd(f: (ProductivityData) -> ProductivityData) { scope.launch { c.productivity.update(f) } }
    val fmt = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
    fun fmtMs(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)

    SettingsScreen(tr("Produktivität", "Productivity")) {
        ChipRow(ProdTab.entries, tab, { when (it) { ProdTab.TASKS -> tr("Aufgaben", "Tasks"); ProdTab.PROJECTS -> tr("Projekte", "Projects"); ProdTab.NOTES -> tr("Notizen", "Notes") } }, { tab = it })
        Spacer(Modifier.padding(4.dp))
        when (tab) {
            ProdTab.TASKS -> {
                Group(tr("Neue Aufgabe", "New task")) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaTextField(text, { text = it }, Modifier.weight(1f), tr("Was ist zu tun?", "What needs doing?"))
                        Spacer(Modifier.width(8.dp))
                        NovaButton("+", { if (text.isNotBlank()) { upd { it.copy(tasks = it.tasks + TaskItem(newId("task"), text.trim(), createdAtMs = System.currentTimeMillis())) }; text = "" } })
                    }
                }
                Group(tr("Offen", "Open")) {
                    if (data.openTasks().isEmpty()) InfoText(tr("Keine offenen Aufgaben", "No open tasks"))
                    data.openTasks().forEach { t ->
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.RadioButtonUnchecked, tr("Erledigt", "Done"), tint = Nova.colors.textSecondary, modifier = Modifier.clickable {
                                    upd { d -> d.copy(tasks = d.tasks.map { if (it.id == t.id) it.copy(done = true, doneAtMs = System.currentTimeMillis()) else it }) }
                                })
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = Nova.type.body, color = Nova.colors.text)
                                    val meta = listOfNotNull(
                                        t.projectId?.let { pid -> data.projects.firstOrNull { it.id == pid }?.name },
                                        t.dueAtMs?.let { tr("Fällig ", "Due ") + fmtMs(it) },
                                        t.remindAtMs?.let { tr("Erinnerung ", "Reminder ") + fmtMs(it) },
                                    )
                                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = Nova.type.caption, color = Nova.colors.textSecondary)
                                }
                                NovaIconButton(Icons.Filled.Event, tr("Fälligkeit", "Due date"), { reminderFlag.value = false; dateFor = t.id to false })
                                NovaIconButton(Icons.Filled.Alarm, tr("Erinnerung", "Reminder"), {
                                    if (Build.VERSION.SDK_INT >= 33 && !ctx.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) req(Manifest.permission.POST_NOTIFICATIONS)
                                    reminderFlag.value = true
                                    dateFor = t.id to true
                                })
                                NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { upd { d -> d.copy(tasks = d.tasks.filter { it.id != t.id }) } })
                            }
                            if (data.projects.isNotEmpty()) Text(
                                tr("Projekt zuordnen", "Assign project"), style = Nova.type.caption, color = Nova.colors.accent,
                                modifier = Modifier.padding(start = 42.dp, bottom = 4.dp).clickable { projectFor = if (projectFor == t.id) null else t.id },
                            )
                            if (projectFor == t.id) ChipRow(listOf<String?>(null) + data.projects.map { it.id }, t.projectId, { id -> data.projects.firstOrNull { it.id == id }?.name ?: tr("Keins", "None") }, { pid ->
                                upd { d -> d.copy(tasks = d.tasks.map { if (it.id == t.id) it.copy(projectId = pid) else it }) }; projectFor = null
                            }, Modifier.padding(start = 42.dp))
                        }
                    }
                }
                Group(tr("Erledigt", "Done")) {
                    data.tasks.filter { it.done }.sortedByDescending { it.doneAtMs }.take(20).forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, tr("Wieder öffnen", "Reopen"), tint = Nova.colors.accent, modifier = Modifier.clickable {
                                upd { d -> d.copy(tasks = d.tasks.map { if (it.id == t.id) it.copy(done = false, doneAtMs = null) else it }) }
                            })
                            Spacer(Modifier.width(10.dp))
                            Text(t.title, style = Nova.type.body.copy(textDecoration = TextDecoration.LineThrough), color = Nova.colors.textSecondary, modifier = Modifier.weight(1f))
                            NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { upd { d -> d.copy(tasks = d.tasks.filter { it.id != t.id }) } })
                        }
                    }
                }
            }
            ProdTab.PROJECTS -> {
                Group(tr("Neues Projekt", "New project")) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NovaTextField(text, { text = it }, Modifier.weight(1f), tr("Projektname", "Project name"))
                        Spacer(Modifier.width(8.dp))
                        NovaButton("+", { if (text.isNotBlank()) { upd { it.copy(projects = it.projects + Project(newId("project"), text.trim())) }; text = "" } })
                    }
                }
                data.projects.forEach { p ->
                    Group(p.name) {
                        val prog = data.projectProgress(p.id)
                        InfoText(if (prog == null) tr("Noch keine Aufgaben", "No tasks yet") else tr("${(prog * 100).toInt()} % erledigt", "${(prog * 100).toInt()} % done"))
                        ColorPickerRow(tr("Farbe", "Color"), p.color, { col -> upd { d -> d.copy(projects = d.projects.map { if (it.id == p.id) it.copy(color = col) else it }) } })
                        SettingRow(if (p.archived) tr("Wiederherstellen", "Unarchive") else tr("Archivieren", "Archive"), onClick = { upd { d -> d.copy(projects = d.projects.map { if (it.id == p.id) it.copy(archived = !p.archived) else it }) } })
                        SettingRow(tr("Projekt löschen", "Delete project"), onClick = { upd { d -> d.copy(projects = d.projects.filter { it.id != p.id }, tasks = d.tasks.map { if (it.projectId == p.id) it.copy(projectId = null) else it }) } })
                    }
                }
            }
            ProdTab.NOTES -> {
                Group(tr("Neue Notiz", "New note")) {
                    NovaTextField(text, { text = it }, Modifier.fillMaxWidth().padding(12.dp), tr("Notiz …", "Note …"), singleLine = false, minLines = 3)
                    Row(Modifier.padding(12.dp)) { NovaButton(tr("Speichern", "Save"), { if (text.isNotBlank()) { upd { it.copy(notes = it.notes + Note(newId("note"), text.trim(), updatedAtMs = System.currentTimeMillis())) }; text = "" } }) }
                }
                data.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAtMs }).forEach { n ->
                    Group {
                        var edit by remember(n.id) { mutableStateOf(n.text) }
                        NovaTextField(edit, { edit = it; upd { d -> d.copy(notes = d.notes.map { x -> if (x.id == n.id) x.copy(text = it, updatedAtMs = System.currentTimeMillis()) else x }) } }, Modifier.fillMaxWidth().padding(12.dp), singleLine = false, minLines = 2)
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            NovaIconButton(Icons.Filled.PushPin, if (n.pinned) tr("Lösen", "Unpin") else tr("Anheften", "Pin"), { upd { d -> d.copy(notes = d.notes.map { if (it.id == n.id) it.copy(pinned = !n.pinned) else it }) } }, tint = if (n.pinned) Nova.colors.accent else Nova.colors.text)
                            NovaIconButton(Icons.Filled.Delete, tr("Löschen", "Delete"), { upd { d -> d.copy(notes = d.notes.filter { it.id != n.id }) } })
                        }
                    }
                }
            }
        }
    }

    dateFor?.let { (taskId, reminder) ->
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { dateFor = null },
            confirmButton = { TextButton({ val d = state.selectedDateMillis; dateFor = null; if (d != null) timeFor = taskId to d }) { Text("OK") } },
            dismissButton = {
                TextButton({
                    upd { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) (if (reminder) it.copy(remindAtMs = null) else it.copy(dueAtMs = null)) else it }) }
                    dateFor = null
                }) { Text(tr("Entfernen", "Remove")) }
            },
        ) { DatePicker(state) }
    }
    timeFor?.let { (taskId, dateUtc) ->
        val state = rememberTimePickerState(9, 0, true)
        androidx.compose.ui.window.Dialog(onDismissRequest = { timeFor = null }) {
            app.novaos.design.NovaSurface(Modifier, androidx.compose.foundation.shape.RoundedCornerShape(28.dp), app.novaos.model.SurfaceBackground.SOLID, 0f) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                    NovaButton("OK", {
                        val date = Instant.ofEpochMilli(dateUtc).atZone(ZoneId.of("UTC")).toLocalDate()
                        val ms = date.atTime(LocalTime.of(state.hour, state.minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val isReminder = reminderFlag.value
                        upd { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) (if (isReminder) it.copy(remindAtMs = ms) else it.copy(dueAtMs = ms)) else it }) }
                        timeFor = null
                    })
                }
            }
        }
    }
}

/** Merkt, ob gerade eine Erinnerung oder ein Fälligkeitsdatum gewählt wird. */
private val reminderFlag = androidx.compose.runtime.mutableStateOf(false)
