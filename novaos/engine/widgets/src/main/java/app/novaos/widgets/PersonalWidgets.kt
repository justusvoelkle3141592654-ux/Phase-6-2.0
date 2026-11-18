package app.novaos.widgets

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.novaos.design.DotBar
import app.novaos.design.DotMatrixText
import app.novaos.design.DotRing
import app.novaos.design.hexColor
import app.novaos.model.NotificationEntry
import app.novaos.model.NotificationGrouping
import app.novaos.model.ProgressSource
import app.novaos.model.WidgetInstance
import app.novaos.system.FavoriteContact
import app.novaos.system.NotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

// ============================================================================ Kommunikation

@Composable
private fun NotificationRow(n: NotificationEntry, now: Long) {
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { host.actions.openNotification(n.key) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(look.text.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(n.appLabel.take(1).uppercase(), style = look.body.copy(fontWeight = FontWeight.Bold), color = look.text)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title.ifBlank { n.appLabel }, style = look.body.copy(fontWeight = FontWeight.SemiBold), color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) Text(n.text, style = look.body.copy(fontSize = look.body.fontSize * 0.88f), color = look.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(NotificationGrouping.relativeTime(n.postedAtMs, now), style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = look.secondary)
    }
}

@Composable
fun BoxWithConstraintsScope.MessagesWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val look = LocalWidgetLook.current
    val all by NotificationHub.entries.collectAsState()
    val now by rememberNow()
    val msgs = all.filter { it.isMessage }.sortedByDescending { it.postedAtMs }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (germanLike) "Nachrichten" else "Messages", style = look.title, color = look.text, modifier = Modifier.weight(1f))
            if (msgs.isNotEmpty()) Text("${msgs.size}", style = look.title, color = look.accent)
        }
        if (msgs.isEmpty()) Text(if (germanLike) "Keine neuen Nachrichten" else "No new messages", style = look.body, color = look.secondary)
        msgs.take(look.maxItems).forEach { NotificationRow(it, now) }
    }
}

@Composable
fun BoxWithConstraintsScope.CommunicationWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val all by NotificationHub.entries.collectAsState()
    val now by rememberNow()
    val comm = all.filter { it.isMessage || it.category == android.app.Notification.CATEGORY_CALL || it.category == android.app.Notification.CATEGORY_EMAIL || it.category == android.app.Notification.CATEGORY_MISSED_CALL }
    val byApp = comm.groupBy { it.packageName }
    Column(Modifier.fillMaxSize()) {
        Text(if (germanLike) "Kommunikation" else "Communication", style = look.title, color = look.text)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            byApp.entries.take(5).forEach { (pkg, list) ->
                val key = host.apps.findByPackage(pkg)?.key
                Box(Modifier.clickable { key?.let { host.actions.launchApp(it) } }) {
                    if (key != null) host.appIcon(key, 36.dp) else Box(Modifier.size(36.dp).clip(CircleShape).background(look.text.copy(alpha = 0.1f)))
                    Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(look.accent), contentAlignment = Alignment.Center) {
                        Text("${list.size}", style = look.body.copy(fontSize = look.body.fontSize * 0.7f), color = app.novaos.design.Nova.colors.onAccent)
                    }
                }
            }
        }
        if (byApp.isEmpty()) Text(if (germanLike) "Alles erledigt" else "All caught up", style = look.body, color = look.secondary)
        comm.sortedByDescending { it.postedAtMs }.take((look.maxItems - 1).coerceAtLeast(1)).forEach { NotificationRow(it, now) }
    }
}

@Composable
private fun contactPhoto(uri: String?): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }.value
}

@Composable
fun BoxWithConstraintsScope.ContactsWidget(instance: WidgetInstance, calls: Boolean) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val contacts = produceState(emptyList<FavoriteContact>()) { value = host.contacts.starred(if (calls) 3 else 6) }.value
    if (contacts.isEmpty()) {
        Centered { Text(if (germanLike) "Keine Favoriten in Kontakte markiert" else "No starred contacts", style = look.body, color = look.secondary) }
        return
    }
    if (calls) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            contacts.take(if (H > 100.dp) 3 else 2).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { c.phone?.let { host.contacts.dial(it) } ?: host.contacts.open(c) }) {
                    Icon(Icons.Filled.Phone, null, tint = look.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(c.name, style = look.body, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        contacts.take((W.value / 64).toInt().coerceIn(1, 6)).forEach { c ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { host.contacts.open(c) }) {
                val photo = contactPhoto(c.photoUri)
                val size = (H * 0.55f).coerceAtMost(52.dp)
                Box(Modifier.size(size).clip(CircleShape).background(look.text.copy(alpha = 0.1f)).border(1.dp, look.text.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    if (photo != null) Image(photo, c.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(c.name.take(1), style = look.title, color = look.text)
                }
                Text(c.name.substringBefore(' '), style = look.body.copy(fontSize = look.body.fontSize * 0.85f), color = look.text, maxLines = 1)
            }
        }
    }
}

// ============================================================================ Produktivität

@Composable
fun BoxWithConstraintsScope.TasksWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val data by host.productivity.collectAsState()
    val projectId = instance.config["projectId"]
    val open = data.openTasks().filter { projectId == null || it.projectId == projectId }
    val done = data.tasks.count { it.done && (projectId == null || it.projectId == projectId) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (germanLike) "Aufgaben" else "Tasks", style = look.title, color = look.text, modifier = Modifier.weight(1f))
            Text("${open.size}", style = look.title, color = look.accent)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Add, if (germanLike) "Aufgabe hinzufügen" else "Add task", tint = look.text, modifier = Modifier.size(22.dp).clickable { host.actions.openProductivity("tasks:new") })
        }
        Spacer(Modifier.height(4.dp))
        if (open.isEmpty()) Text(if (germanLike) "Alles erledigt ($done)" else "All done ($done)", style = look.body, color = look.secondary)
        open.take(look.maxItems).forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.RadioButtonUnchecked, if (germanLike) "Erledigt" else "Done", tint = look.secondary,
                    modifier = Modifier.size(20.dp).clickable {
                        host.updateProductivity { d -> d.copy(tasks = d.tasks.map { if (it.id == t.id) it.copy(done = true, doneAtMs = System.currentTimeMillis()) else it }) }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(t.title, style = look.body, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (t.dueAtMs != null) Text(formatDate(t.dueAtMs!!, false), style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = if (t.dueAtMs!! < System.currentTimeMillis()) look.accent else look.secondary)
            }
        }
        if (look.detailed) {
            data.tasks.filter { it.done }.sortedByDescending { it.doneAtMs }.take(2).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = look.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.title, style = look.body.copy(textDecoration = TextDecoration.LineThrough), color = look.secondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun BoxWithConstraintsScope.ProjectsWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val data by host.productivity.collectAsState()
    val projects = data.projects.filter { !it.archived }
    if (projects.isEmpty()) {
        Centered { Text(if (germanLike) "Noch keine Projekte" else "No projects yet", style = look.body, color = look.secondary) }
        return
    }
    val selected = projects.firstOrNull { it.id == instance.config["projectId"] } ?: projects.first()
    if (H < 110.dp || projects.size == 1) {
        val p = data.projectProgress(selected.id) ?: 0f
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (germanLike) "Projekt" else "Project", style = look.body, color = look.secondary)
                Text(selected.name, style = look.title, color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${data.tasks.count { it.projectId == selected.id && !it.done }} ${if (germanLike) "offen" else "open"}", style = look.body, color = look.secondary)
            }
            Box(Modifier.size((H * 0.85f).coerceAtMost(64.dp)), contentAlignment = Alignment.Center) {
                DotRing(p, Modifier.fillMaxSize(), hexColor(selected.color, look.accent), look.text.copy(alpha = 0.14f), 28)
                if (look.dots) DotMatrixText("${(p * 100).roundToInt()}", dotSize = 1.8.dp, color = look.text)
                else Text("${(p * 100).roundToInt()}%", style = look.body, color = look.text)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(if (germanLike) "Projekte" else "Projects", style = look.title, color = look.text)
        projects.take(look.maxItems).forEach { pr ->
            val p = data.projectProgress(pr.id) ?: 0f
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pr.name, style = look.body, color = look.text, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${(p * 100).roundToInt()}%", style = look.body, color = look.secondary)
            }
            DotBar(p, Modifier.fillMaxWidth().height(8.dp), hexColor(pr.color, look.accent), look.text.copy(alpha = 0.12f), 30)
        }
    }
}

@Composable
fun BoxWithConstraintsScope.NotesWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val data by host.productivity.collectAsState()
    val note = data.notes.firstOrNull { it.id == instance.config["noteId"] }
        ?: data.notes.sortedWith(compareByDescending<app.novaos.model.Note> { it.pinned }.thenByDescending { it.updatedAtMs }).firstOrNull()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.StickyNote2, null, tint = look.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (germanLike) "Notiz" else "Note", style = look.body, color = look.secondary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Add, if (germanLike) "Notiz hinzufügen" else "Add note", tint = look.text, modifier = Modifier.size(22.dp).clickable { host.actions.openProductivity("notes:new") })
        }
        Spacer(Modifier.height(6.dp))
        Text(note?.text ?: (if (germanLike) "Tippen, um eine Notiz zu schreiben" else "Tap to write a note"), style = look.body, color = if (note == null) look.secondary else look.text, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BoxWithConstraintsScope.RemindersWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val data by host.productivity.collectAsState()
    val now by rememberNow()
    val list = data.upcomingReminders(now)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Alarm, null, tint = look.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (germanLike) "Erinnerungen" else "Reminders", style = look.title, color = look.text)
        }
        if (list.isEmpty()) Text(if (germanLike) "Keine anstehenden Erinnerungen" else "No upcoming reminders", style = look.body, color = look.secondary)
        list.take(look.maxItems).forEach { t ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(t.title, style = look.body, color = look.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(t.remindAtMs!!, false) + " " + formatTime(t.remindAtMs!!, true), style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = look.secondary)
            }
        }
    }
}

/** Fortschrittswert 0..1 für eine Quelle (auch im Widget Creator verwendet). */
@Composable
fun progressValue(source: ProgressSource): Float {
    val host = LocalWidgetHost.current ?: return 0f
    val now by rememberNow()
    return when (source) {
        ProgressSource.BATTERY -> host.systemInfo.battery.collectAsState().value.level / 100f
        ProgressSource.STORAGE -> host.systemInfo.storage().usedFraction
        ProgressSource.RAM -> host.systemInfo.memory().usedFraction
        ProgressSource.TASKS -> host.productivity.collectAsState().value.overallProgress() ?: 0f
        ProgressSource.DAY -> {
            val z = ZoneId.systemDefault()
            val start = LocalDate.now(z).atStartOfDay(z).toInstant().toEpochMilli()
            ((now - start) / 86_400_000f).coerceIn(0f, 1f)
        }
        ProgressSource.YEAR -> {
            val d = LocalDate.now()
            (d.dayOfYear - 1) / d.lengthOfYear().toFloat()
        }
        ProgressSource.MEDIA -> {
            val m = app.novaos.system.MediaHub.state.collectAsState().value
            if (m == null || m.durationMs <= 0) 0f else (m.currentPosition(android.os.SystemClock.elapsedRealtime()) / m.durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun BoxWithConstraintsScope.ProgressWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val look = LocalWidgetLook.current
    val source = runCatching { ProgressSource.valueOf(instance.config["source"] ?: "DAY") }.getOrDefault(ProgressSource.DAY)
    val v = progressValue(source)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(source.label, style = look.body, color = look.secondary, modifier = Modifier.weight(1f))
            if (look.dots) DotMatrixText("${(v * 100).roundToInt()}%", dotSize = 2.dp, color = look.text)
            else Text("${(v * 100).roundToInt()} %", style = look.title, color = look.text)
        }
        Spacer(Modifier.height(8.dp))
        DotBar(v, Modifier.fillMaxWidth().height(12.dp), look.accent, look.text.copy(alpha = 0.12f), 24)
    }
}

@Suppress("unused")
private fun squareModifier() = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
