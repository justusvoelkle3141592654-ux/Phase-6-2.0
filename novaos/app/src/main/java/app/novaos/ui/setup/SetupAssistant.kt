package app.novaos.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.novaos.design.ChoiceRow
import app.novaos.design.ColorPickerRow
import app.novaos.design.DotMatrixText
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaText
import app.novaos.design.PreviewChooser
import app.novaos.design.SettingsGroup
import app.novaos.design.SwitchRow
import app.novaos.model.AnimationStyle
import app.novaos.model.CcLayout
import app.novaos.model.Defaults
import app.novaos.model.DockPosition
import app.novaos.model.GestureAction
import app.novaos.model.GestureType
import app.novaos.model.IconRendering
import app.novaos.model.IconShape
import app.novaos.model.ThemeMode
import app.novaos.model.ThemePresets
import app.novaos.model.defaultGestures
import app.novaos.system.HomeRole
import app.novaos.system.NotificationHub
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.MiniHome
import app.novaos.ui.common.tr
import app.novaos.ui.settings.AnimationPreview

private const val STEPS = 12

/**
 * Nova Setup Assistant: führt durch Design, Modus, Farbe, Icons, Widgets, Homescreen, Kontrollzentrum,
 * Animationen, Gesten und Standardprofil – mit echter Vorschau. Jede Wahl wird sofort übernommen
 * und kann mit „Zurück“ geändert werden.
 */
@Composable
fun SetupAssistant(onFinish: () -> Unit, requestHomeRole: (() -> Unit)?) {
    val c = LocalContainer.current
    val ctrl = c.controller
    val cfg = LocalConfig.current
    val p = LocalProfile.current
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(Nova.colors.background.copy(alpha = 0.92f)).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        // Fortschritt als Punkte
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(STEPS) { i -> Box(Modifier.padding(3.dp).size(if (i == step) 9.dp else 6.dp).clip(CircleShape).background(if (i <= step) Nova.colors.accent else Nova.colors.text.copy(alpha = 0.25f))) }
        }
        if (step in 1..10) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MiniHome(p, width = 130.dp) }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "setup") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (s) {
                    0 -> {
                        Spacer(Modifier.height(60.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DotMatrixText("NOVAOS", dotSize = 6.dp, color = Nova.colors.text, dimColor = Nova.colors.text.copy(alpha = 0.06f))
                            Box(Modifier.padding(start = 6.dp).size(12.dp).clip(CircleShape).background(Nova.colors.accent))
                        }
                        Spacer(Modifier.height(20.dp))
                        NovaText(tr("Willkommen bei NovaOS", "Welcome to NovaOS"), Nova.type.headline, maxLines = 2, forceDots = false)
                        Text(tr("Gestalte dein persönliches Android-Erlebnis. In wenigen Schritten richtest du Design, Widgets, Bedienung und Profile ein – alles lässt sich später ändern.",
                            "Design your personal Android experience. Set up design, widgets, controls and profiles in a few steps – you can change everything later."),
                            style = Nova.type.body, color = Nova.colors.textSecondary)
                    }
                    1 -> {
                        Title(tr("1 · Designrichtung", "1 · Design direction"))
                        PreviewChooser(ThemePresets.all, ThemePresets.all.firstOrNull { it.id == p.theme.id } ?: ThemePresets.nova, { it.name }, { t -> ctrl.applyTheme(t, true) }, itemWidth = 130.dp) { t ->
                            MiniHome(p.copy(theme = t, wallpaper = t.wallpaper ?: p.wallpaper), width = 110.dp)
                        }
                        Text(p.theme.description, style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(12.dp))
                    }
                    2 -> {
                        Title(tr("2 · Hell oder dunkel", "2 · Light or dark"))
                        SettingsGroup {
                            ChoiceRow(tr("Modus", "Mode"), ThemeMode.entries, p.theme.colors.mode, {
                                when (it) { ThemeMode.DARK -> tr("Dunkel", "Dark"); ThemeMode.LIGHT -> tr("Hell", "Light"); ThemeMode.SYSTEM -> tr("Wie System", "System") }
                            }, { m -> ctrl.editTheme { it.copy(colors = it.colors.copy(mode = m)) } })
                        }
                    }
                    3 -> {
                        Title(tr("3 · Akzentfarbe", "3 · Accent color"))
                        SettingsGroup {
                            ColorPickerRow(tr("Akzent", "Accent"), p.theme.colors.dark.accent, { hex ->
                                ctrl.editTheme { it.copy(colors = it.colors.copy(dark = it.colors.dark.copy(accent = hex), light = it.colors.light.copy(accent = hex), dynamicFromWallpaper = false)) }
                            })
                            SwitchRow(tr("Farbe aus dem Hintergrund", "Color from wallpaper"), null, p.theme.colors.dynamicFromWallpaper, { on -> ctrl.editTheme { it.copy(colors = it.colors.copy(dynamicFromWallpaper = on)) } })
                        }
                    }
                    4 -> {
                        Title(tr("4 · Icon-Stil", "4 · Icon style"))
                        SettingsGroup {
                            ChoiceRow(tr("Darstellung", "Rendering"), IconRendering.entries, p.theme.icons.rendering, { it.label }, { r -> ctrl.editTheme { it.copy(icons = it.icons.copy(rendering = r)) } })
                            ChoiceRow(tr("Form", "Shape"), listOf(IconShape.SQUIRCLE, IconShape.CIRCLE, IconShape.ROUNDED_SQUARE, IconShape.SQUARE, IconShape.HEXAGON, IconShape.DOT_RING, IconShape.NONE), p.theme.icons.shape, { it.label }, { sh -> ctrl.editTheme { it.copy(icons = it.icons.copy(shape = sh)) } })
                        }
                    }
                    5 -> {
                        Title(tr("5 · Widget-Layout", "5 · Widget layout"))
                        val options = listOf(
                            tr("Standard (Uhr, Wetter, Projekt, Musik)", "Standard (clock, weather, project, music)") to { Defaults.standardLayout() },
                            tr("Minimal (große Uhr)", "Minimal (large clock)") to { Defaults.minimalLayout() },
                            tr("Produktiv (Agenda, Aufgaben)", "Productive (agenda, tasks)") to { Defaults.workLayout() },
                            tr("Medien (Album, Musik)", "Media (album, music)") to { Defaults.mediaLayout() },
                        )
                        SettingsGroup {
                            options.forEach { (label, make) ->
                                app.novaos.design.SettingRow(label, onClick = {
                                    val l = make()
                                    ctrl.editLayout { old -> l.copy(dockApps = old.dockApps.ifEmpty { l.dockApps }) }
                                })
                            }
                        }
                        Text(tr("Platzhalter-Apps werden automatisch durch deine Standard-Apps ersetzt.", "Placeholder apps are replaced with your default apps."), style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(12.dp))
                    }
                    6 -> {
                        Title(tr("6 · Homescreen-Aufbau", "6 · Home screen layout"))
                        SettingsGroup {
                            ChoiceRow(tr("Raster", "Grid"), listOf(4 to 6, 5 to 6, 4 to 5, 5 to 7), p.layout.columns to p.layout.rows, { "${it.first} × ${it.second}" }, { (cc, rr) -> ctrl.setGrid(cc, rr) })
                            SwitchRow(tr("Freier Layoutmodus", "Free layout"), null, p.layout.freeLayout, { on -> ctrl.editLayout { it.copy(freeLayout = on) } })
                            ChoiceRow(tr("Dock", "Dock"), DockPosition.entries, p.theme.dock.position, { it.label }, { d -> ctrl.editTheme { it.copy(dock = it.dock.copy(position = d)) } })
                            SwitchRow(tr("App-Namen", "App labels"), null, p.layout.showLabels, { on -> ctrl.editLayout { it.copy(showLabels = on) } })
                        }
                    }
                    7 -> {
                        Title(tr("7 · Kontrollzentrum", "7 · Control center"))
                        SettingsGroup {
                            ChoiceRow(tr("Layout", "Layout"), CcLayout.entries.filter { it != CcLayout.CUSTOM }, p.theme.controlCenter.layout, { it.label }, { l -> ctrl.editTheme { it.copy(controlCenter = it.controlCenter.copy(layout = l)) } })
                        }
                        Text(tr("Öffnen: auf dem Homescreen nach unten wischen.", "Open: swipe down on the home screen."), style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(12.dp))
                    }
                    8 -> {
                        Title(tr("8 · Animationen", "8 · Animations"))
                        SettingsGroup {
                            AnimationPreview(p.theme.animation.style, p.theme.animation.speed, p.theme.animation.durationMs)
                            ChoiceRow(tr("Stil", "Style"), AnimationStyle.entries, p.theme.animation.style, { it.label }, { s2 -> ctrl.editTheme { it.copy(animation = it.animation.copy(style = s2)) } })
                            SwitchRow(tr("Performance-Modus", "Performance mode"), tr("Weniger Effekte, mehr Akku", "Fewer effects, more battery"), cfg.general.performanceMode, { on -> ctrl.editGeneral { it.copy(performanceMode = on) } })
                        }
                    }
                    9 -> {
                        Title(tr("9 · Gesten", "9 · Gestures"))
                        val presets = listOf(
                            tr("Standard: ↑ Apps, ↓ Kontrollzentrum, → Benachrichtigungen", "Standard: ↑ apps, ↓ control center, → notifications") to defaultGestures(),
                            tr("Alternativ: ↓ Benachrichtigungen, 2 Finger ↓ Kontrollzentrum", "Alternative: ↓ notifications, two-finger ↓ control center") to defaultGestures() + mapOf(
                                GestureType.SWIPE_DOWN to GestureAction.OpenNotifications, GestureType.TWO_FINGER_DOWN to GestureAction.OpenControlCenter, GestureType.SWIPE_RIGHT to GestureAction.OpenSearch,
                            ),
                            tr("Minimal: nur ↑ Apps und ↓ Suche", "Minimal: only ↑ apps and ↓ search") to GestureType.entries.associateWith { GestureAction.None } + mapOf(
                                GestureType.SWIPE_UP to GestureAction.OpenDrawer, GestureType.SWIPE_DOWN to GestureAction.OpenSearch, GestureType.LONG_PRESS to GestureAction.OpenQuickMenu,
                            ),
                        )
                        SettingsGroup {
                            presets.forEach { (label, g) -> app.novaos.design.SettingRow(label, onClick = { ctrl.editProfile { it.copy(gestures = g) } }, trailing = { if (p.gestures == g) Text("✓", color = Nova.colors.accent) }) }
                        }
                        Text(tr("Doppeltippen sperrt den Bildschirm, sobald du die Geräteadministrator-Freigabe erteilst.", "Double tap locks the screen once device admin is granted."), style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(12.dp))
                    }
                    10 -> {
                        Title(tr("10 · Standardprofil", "10 · Default profile"))
                        SettingsGroup {
                            cfg.profiles.forEach { pr ->
                                app.novaos.design.SettingRow(pr.name, "${pr.kind.label} · ${pr.theme.name}", onClick = { ctrl.switchProfile(pr.id) }, trailing = { if (pr.id == cfg.activeProfileId) Text("✓", color = Nova.colors.accent) })
                            }
                        }
                        Text(tr("Hinweis: Das gewählte Profil bringt sein eigenes Design mit. Einstellungen aus den vorherigen Schritten gelten für das Profil, das dabei aktiv war.",
                            "Note: each profile brings its own design. Earlier steps applied to the profile active at that time."), style = Nova.type.caption, color = Nova.colors.textSecondary, modifier = Modifier.padding(12.dp))
                    }
                    else -> {
                        Title(tr("Fertig", "Done"))
                        Text(tr("Zum Schluss: NovaOS als Standard-Startbildschirm festlegen. Android fragt dich dazu selbst.", "Finally: set NovaOS as default home app. Android will ask you."), style = Nova.type.body, color = Nova.colors.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        NovaButton(if (HomeRole.isDefault(ctx)) tr("NovaOS ist Standard ✓", "NovaOS is default ✓") else tr("Als Standard festlegen", "Set as default"), {
                            if (requestHomeRole != null) requestHomeRole() else c.apps.startIntent(HomeRole.requestIntent(ctx) ?: HomeRole.homeSettingsIntent())
                        }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        NovaButton(tr("Benachrichtigungszugriff erlauben (optional)", "Allow notification access (optional)"), { c.apps.startIntent(NotificationHub.accessSettingsIntent(ctx)) }, Modifier.fillMaxWidth(), primary = false)
                        Spacer(Modifier.height(10.dp))
                        Text(tr("Weitere Freigaben (Kalender, Kontakte, Standort …) fragt NovaOS erst, wenn du eine Funktion nutzt.", "Other permissions are requested only when you use a feature."), style = Nova.type.caption, color = Nova.colors.textSecondary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) NovaButton(tr("Zurück", "Back"), { step-- }, primary = false) else Text(tr("Überspringen", "Skip"), color = Nova.colors.textSecondary, style = Nova.type.body,
                modifier = Modifier.padding(12.dp).clickable { onFinish() })
            Spacer(Modifier.weight(1f))
            if (step < STEPS - 1) NovaButton(tr("Weiter", "Next"), { step++ }, icon = Icons.AutoMirrored.Filled.ArrowForward)
            else NovaButton(tr("NovaOS starten", "Start NovaOS"), onFinish)
        }
    }
}

@Composable
private fun Title(text: String) {
    NovaText(text, Nova.type.headline, Modifier.padding(vertical = 10.dp), maxLines = 2)
}
