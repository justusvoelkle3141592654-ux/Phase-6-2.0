package app.novaos.ui.launcher

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.novaos.design.ChipRow
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaSurface
import app.novaos.design.NovaText
import app.novaos.design.NovaTextField
import app.novaos.model.GroupingStyle
import app.novaos.model.NotificationEntry
import app.novaos.model.NotificationGrouping
import app.novaos.system.NotificationHub
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.toBitmap
import app.novaos.ui.common.tr
import app.novaos.widgets.rememberNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nova Notification Center (eigene Oberfläche über die Notification Listener API). */
@Composable
fun NotificationCenter(vm: LauncherViewModel, actions: LauncherActions) {
    val ctx = LocalContext.current
    val c = LocalContainer.current
    val profile = LocalProfile.current
    val spec = profile.theme.notifications
    val granted = remember { NotificationHub.isAccessGranted(ctx) }
    val entries by NotificationHub.entries.collectAsState()
    val now by rememberNow()
    var grouping by remember(spec.grouping) { mutableStateOf(spec.grouping) }
    var newestFirst by remember(spec.newestFirst) { mutableStateOf(spec.newestFirst) }
    val groups = remember(entries, grouping, newestFirst, now / 60_000) { NotificationGrouping.group(entries, grouping, newestFirst, now) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).imePadding().padding(horizontal = 12.dp)
            .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -40) vm.close() } },
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NovaText(tr("Benachrichtigungen", "Notifications"), Nova.type.headline, Modifier.weight(1f))
            NovaIconButton(Icons.Filled.Tune, tr("Darstellung anpassen", "Customize"), { actions.openSettings("notifications") })
            if (entries.any { it.isClearable }) NovaIconButton(Icons.Filled.ClearAll, tr("Alle löschen", "Clear all"), { NotificationHub.dismissAll() })
        }
        if (!granted) {
            NovaSurface(Modifier.fillMaxWidth(), RoundedCornerShape(spec.cardCornerDp.dp), spec.background, spec.transparency) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Filled.NotificationsOff, null, tint = Nova.colors.accent)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Benachrichtigungszugriff fehlt", "Notification access missing"), style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), color = Nova.colors.text)
                    Text(
                        tr("NovaOS zeigt Benachrichtigungen erst an, wenn du den Zugriff in den Android-Einstellungen erlaubst. Die Daten bleiben auf dem Gerät.",
                            "NovaOS shows notifications after you allow access in Android settings. Data stays on the device."),
                        style = Nova.type.caption, color = Nova.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    NovaButton(tr("Zugriff erlauben", "Allow access"), { actions.startSafely(NotificationHub.accessSettingsIntent(ctx)) })
                }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipRow(GroupingStyle.entries, grouping, { it.label }, { grouping = it }, Modifier.weight(1f))
            Text(
                if (newestFirst) tr("Neueste zuerst", "Newest first") else tr("Älteste zuerst", "Oldest first"),
                style = Nova.type.caption, color = Nova.colors.accent,
                modifier = Modifier.clickable { newestFirst = !newestFirst }.padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(tr("Keine Benachrichtigungen", "No notifications"), color = Nova.colors.textSecondary, style = Nova.type.body)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spec.cardSpacingDp.dp)) {
            groups.forEach { g ->
                val isCollapsed = collapsed[g.id] == true
                item(key = "h-${g.id}") {
                    Row(Modifier.fillMaxWidth().clickable { collapsed[g.id] = !isCollapsed }.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (g.packageName != null) c.apps.findByPackage(g.packageName!!)?.let { AppIconImage(it.key, 20.dp) }
                        Spacer(Modifier.width(8.dp))
                        Text(g.title, style = Nova.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Nova.colors.text, modifier = Modifier.weight(1f))
                        Text("${g.entries.size}", style = Nova.type.caption, color = Nova.colors.textSecondary)
                        Icon(if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = Nova.colors.textSecondary)
                        if (g.packageName != null) Text(
                            tr("Verwalten", "Manage"), style = Nova.type.caption, color = Nova.colors.accent,
                            modifier = Modifier.clickable {
                                actions.startSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, g.packageName))
                            }.padding(start = 8.dp),
                        )
                    }
                }
                if (!isCollapsed) items(g.entries, key = { it.key }) { n -> NotificationCard(n, now, actions) }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun notificationIcon(n: NotificationEntry): ImageBitmap? {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, n.key) {
        value = withContext(Dispatchers.Default) {
            runCatching { (NotificationHub.largeIcon(n.key) ?: NotificationHub.smallIcon(n.key))?.loadDrawable(ctx)?.toBitmap(96)?.asImageBitmap() }.getOrNull()
        }
    }.value
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NotificationCard(n: NotificationEntry, now: Long, actions: LauncherActions) {
    val ctx = LocalContext.current
    val spec = LocalProfile.current.theme.notifications
    val colors = Nova.colors
    var replyFor by remember { mutableStateOf<Int?>(null) }
    var reply by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v != SwipeToDismissBoxValue.Settled && n.isClearable) { NotificationHub.dismiss(n.key); true } else false
    })
    SwipeToDismissBox(dismiss, backgroundContent = {}, enableDismissFromStartToEnd = n.isClearable, enableDismissFromEndToStart = n.isClearable) {
        NovaSurface(Modifier.fillMaxWidth().animateContentSize(), RoundedCornerShape(spec.cardCornerDp.dp), spec.background, spec.transparency, shadow = 0.15f) {
            Column(
                Modifier.fillMaxWidth().combinedClickable(onClick = { actions.openNotification(n.key) }, onLongClick = { expanded = !expanded }).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    val icon = notificationIcon(n)
                    Box(Modifier.size(spec.iconSizeDp.dp).clip(CircleShape).background(colors.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        if (icon != null) Image(icon, null, Modifier.fillMaxSize())
                        else Text(n.appLabel.take(1), color = colors.text)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(n.title.ifBlank { n.appLabel }, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = Nova.type.body.fontSize * spec.fontScale), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(NotificationGrouping.relativeTime(n.postedAtMs, now), style = Nova.type.caption, color = colors.textSecondary)
                        }
                        if (spec.showPreviews && n.text.isNotBlank()) {
                            Text(n.text, style = Nova.type.body.copy(fontSize = Nova.type.body.fontSize * spec.fontScale * 0.95f), color = colors.textSecondary, maxLines = if (expanded) 12 else 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (n.actions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        n.actions.take(3).forEach { a ->
                            Text(
                                a.title, style = Nova.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.text,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(colors.text.copy(alpha = 0.08f))
                                    .clickable { if (a.acceptsReply) replyFor = a.index else NotificationHub.performAction(ctx, n.key, a.index) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                if (replyFor != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NovaTextField(reply, { reply = it }, Modifier.weight(1f), placeholder = tr("Antwort …", "Reply …"))
                        NovaIconButton(Icons.AutoMirrored.Filled.Send, tr("Senden", "Send"), {
                            if (reply.isNotBlank() && NotificationHub.performAction(ctx, n.key, replyFor!!, reply)) { reply = ""; replyFor = null }
                        })
                    }
                }
            }
        }
    }
}
