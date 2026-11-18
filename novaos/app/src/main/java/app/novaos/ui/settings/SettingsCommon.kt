package app.novaos.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.novaos.design.DotMatrixText
import app.novaos.design.Nova
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaTopBar
import app.novaos.design.SectionLabel
import app.novaos.design.SettingRow
import app.novaos.design.SettingsGroup
import app.novaos.model.SearchBarStyle
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.MiniHome
import app.novaos.ui.common.tr
import app.novaos.ui.launcher.SearchBar

/** Grundgerüst jeder Einstellungsseite mit optionaler Live-Vorschau. */
@Composable
fun SettingsScreen(title: String, preview: Boolean = false, actions: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    var showPreview by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        NovaTopBar(title, onBack = { nav.back() }) {
            if (preview) NovaIconButton(if (showPreview) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, tr("Vorschau umschalten", "Toggle preview"), { showPreview = !showPreview })
            actions()
        }
        if (preview) AnimatedVisibility(showPreview) {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { MiniHome(LocalProfile.current, width = 150.dp) }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionLabel(title)
    SettingsGroup(content = content)
}

@Composable
fun InfoText(text: String) {
    Text(text, style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun RootScreen() {
    val nav = LocalNav.current
    val cfg = LocalConfig.current
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            DotMatrixText("NOVAOS", dotSize = 4.dp, color = Nova.colors.text, dimColor = Nova.colors.text.copy(alpha = 0.06f))
            Box(Modifier.padding(start = 4.dp).size(9.dp).clip(CircleShape).background(Nova.colors.accent))
        }
        Text(
            tr("Profil: ${cfg.activeProfile.name} · Theme: ${cfg.activeProfile.theme.name}", "Profile: ${cfg.activeProfile.name} · Theme: ${cfg.activeProfile.theme.name}"),
            style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        SearchBar(q, { q = it }, SearchBarStyle.PILL, Modifier.fillMaxWidth().padding(horizontal = 16.dp), tr("Einstellungen durchsuchen …", "Search settings …"))
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
            if (q.isNotBlank()) {
                val hits = SettingsIndex.search(q)
                if (hits.isEmpty()) InfoText(tr("Keine Treffer", "No results"))
                SettingsGroup {
                    hits.forEach { h -> SettingRow(h.title, h.category, onClick = { nav.push(h.route) }) }
                }
            } else {
                val groups = listOf(
                    tr("System", "System") to listOf("general", "performance", "privacy", "permissions", "backup", "about"),
                    tr("Gestaltung", "Design") to listOf("personalization", "themes", "colors", "typography", "animations", "glass", "icons", "wallpaper"),
                    tr("Oberflächen", "Surfaces") to listOf("home", "drawer", "widgets", "widget_creator", "control_center", "notifications", "gestures", "profiles"),
                )
                val byRoute = Categories.associateBy { it.route }
                groups.forEach { (title, routes) ->
                    SectionLabel(title)
                    SettingsGroup {
                        routes.mapNotNull { byRoute[it] }.forEach { cat -> SettingRow(cat.title, cat.subtitle, icon = cat.icon, onClick = { nav.push(cat.route) }) }
                    }
                }
                SectionLabel(tr("Produktivität", "Productivity"))
                SettingsGroup { SettingRow(tr("Aufgaben, Projekte, Notizen", "Tasks, projects, notes"), tr("Daten für die Produktivitäts-Widgets", "Data for productivity widgets"), onClick = { nav.push("productivity") }) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Kleine Kennzeichnung für Funktionen, die eine Android-Freigabe benötigen. */
@Composable
fun PermissionBadge(text: String, granted: Boolean, onClick: () -> Unit) {
    val c = Nova.colors
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(if (granted) c.text.copy(alpha = 0.06f) else c.accent.copy(alpha = 0.15f))
            .clickable(enabled = !granted, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (granted) c.textSecondary else c.accent))
        Text(
            if (granted) tr("$text: erteilt", "$text: granted") else tr("$text: Tippen zum Erlauben", "$text: tap to allow"),
            style = Nova.type.caption.copy(fontWeight = FontWeight.Medium), color = c.text,
        )
    }
}
