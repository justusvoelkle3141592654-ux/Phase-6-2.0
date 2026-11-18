package app.novaos.ui.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novaos.MainActivity
import app.novaos.design.ChipRow
import app.novaos.design.Nova
import app.novaos.design.NovaSurface
import app.novaos.design.NovaText
import app.novaos.design.SectionLabel
import app.novaos.model.Defaults
import app.novaos.model.SurfaceBackground
import app.novaos.model.TemplateCategory
import app.novaos.model.WidgetCategory
import app.novaos.model.WidgetInstance
import app.novaos.model.WidgetType
import app.novaos.ui.common.DrawableImage
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import app.novaos.widgets.NovaWidget

private enum class PickerTab { NOVA, TEMPLATES, CUSTOM, ANDROID }

/** Widget-Auswahl mit Live-Vorschau. */
@Composable
fun WidgetPicker(vm: LauncherViewModel, actions: LauncherActions, widgetHost: NovaWidgetHost, page: Int) {
    val c = LocalContainer.current
    val cfg = LocalConfig.current
    var tab by remember { mutableStateOf(PickerTab.NOVA) }
    var category by remember { mutableStateOf(WidgetCategory.SYSTEM) }
    var tplCat by remember { mutableStateOf(TemplateCategory.MINIMAL) }
    NovaSurface(Modifier.fillMaxSize(), RoundedCornerShape(0.dp), SurfaceBackground.GLASS, 0.1f, borderWidth = 0.dp) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaText(tr("Widgets", "Widgets"), Nova.type.headline, Modifier.weight(1f))
                Text(tr("Schließen", "Close"), color = Nova.colors.accent, style = Nova.type.body, modifier = Modifier.clickable { vm.close() }.padding(8.dp))
            }
            ChipRow(PickerTab.entries, tab, {
                when (it) {
                    PickerTab.NOVA -> "Nova"
                    PickerTab.TEMPLATES -> tr("Vorlagen", "Templates")
                    PickerTab.CUSTOM -> tr("Eigene", "Custom")
                    PickerTab.ANDROID -> "Android"
                }
            }, { tab = it })
            Spacer(Modifier.height(10.dp))
            when (tab) {
                PickerTab.NOVA -> {
                    ChipRow(WidgetCategory.entries.filter { it != WidgetCategory.CUSTOM }, category, { it.label }, { category = it })
                    LazyColumn(Modifier.weight(1f)) {
                        items(WidgetType.entries.filter { it.category == category && it != WidgetType.CUSTOM }) { t ->
                            PickerEntry(t.label, t.needs.takeIf { it != app.novaos.model.WidgetRequirement.NONE }?.label, t.defaultW, t.defaultH, WidgetInstance("preview-${t.name}", t)) {
                                c.controller.addWidget(t, page); vm.close()
                            }
                        }
                    }
                }
                PickerTab.TEMPLATES -> {
                    ChipRow(TemplateCategory.entries, tplCat, { it.label }, { tplCat = it })
                    val list = (Defaults.widgetTemplates() + cfg.widgetTemplates).filter { it.category == tplCat }
                    LazyColumn(Modifier.weight(1f)) {
                        items(list, key = { it.id }) { t ->
                            PickerEntry(t.name, t.widget.type.label, t.w, t.h, t.widget.copy(id = "preview-${t.id}")) { c.controller.addTemplate(t, page); vm.close() }
                        }
                    }
                }
                PickerTab.CUSTOM -> {
                    LazyColumn(Modifier.weight(1f)) {
                        if (cfg.customWidgets.isEmpty()) item {
                            Text(tr("Noch keine eigenen Widgets. Lege sie im Widget Creator an.", "No custom widgets yet. Create them in the Widget Creator."), color = Nova.colors.textSecondary, style = Nova.type.body, modifier = Modifier.padding(12.dp))
                        }
                        items(cfg.customWidgets, key = { it.id }) { d ->
                            val inst = WidgetInstance("preview-${d.id}", WidgetType.CUSTOM, d.style, mapOf("customId" to d.id))
                            PickerEntry(d.name, tr("Eigenes Widget", "Custom widget"), d.w, d.h, inst) { c.controller.addWidget(WidgetType.CUSTOM, page, inst, d.w, d.h); vm.close() }
                        }
                        item {
                            Text(tr("Neues Widget im Widget Creator anlegen", "Create a new widget in Widget Creator"), color = Nova.colors.accent, style = Nova.type.body,
                                modifier = Modifier.clickable { vm.close(); actions.openSettings("widget_creator") }.padding(12.dp))
                        }
                    }
                }
                PickerTab.ANDROID -> AndroidWidgetList(vm, widgetHost, page)
            }
        }
    }
}

@Composable
private fun PickerEntry(title: String, subtitle: String?, w: Int, h: Int, instance: WidgetInstance, onAdd: () -> Unit) {
    val profile = LocalProfile.current
    val cols = profile.layout.columns
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onAdd).padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), color = Nova.colors.text, modifier = Modifier.weight(1f))
            Text("$w × $h", style = Nova.type.caption, color = Nova.colors.textSecondary)
        }
        if (subtitle != null) Text(subtitle, style = Nova.type.caption, color = Nova.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        val cell: Dp = 76.dp
        Box(Modifier.width(cell * w.coerceAtMost(cols)).height(cell * h.coerceAtMost(3))) {
            NovaWidget(instance, instance.id, Modifier.fillMaxSize(), preview = true)
        }
    }
}

@Composable
private fun AndroidWidgetList(vm: LauncherViewModel, widgetHost: NovaWidgetHost, page: Int) {
    val ctx = LocalContext.current
    val activity = ctx as MainActivity
    val profile = LocalProfile.current
    val density = LocalDensity.current
    val providers = remember { widgetHost.providers(ctx) }
    val grouped = remember(providers) { providers.groupBy { it.provider.packageName } }
    LazyColumn(Modifier.fillMaxSize()) {
        if (providers.isEmpty()) item { Text(tr("Keine Android-Widgets gefunden", "No Android widgets found"), color = Nova.colors.textSecondary, modifier = Modifier.padding(12.dp)) }
        grouped.forEach { (pkg, list) ->
            item(key = "h-$pkg") {
                val label = remember(pkg) { runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                SectionLabel(label)
            }
            items(list, key = { it.provider.flattenToString() }) { info ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        val l = profile.layout
                        val (w, h) = widgetHost.cellSize(info, l.columns, l.rows, 80f, 100f, density.density)
                        activity.addAndroidWidget(info, page, w, h); vm.close()
                    }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawableImage(remember(info) { info.loadPreviewImage(ctx, (density.density * 160).toInt()) ?: info.loadIcon(ctx, (density.density * 160).toInt()) }, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.loadLabel(ctx.packageManager), style = Nova.type.body, color = Nova.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (info.targetCellWidth > 0) info.targetCellWidth else "?"} × ${if (info.targetCellHeight > 0) info.targetCellHeight else "?"}", style = Nova.type.caption, color = Nova.colors.textSecondary)
                    }
                }
            }
        }
    }
}
