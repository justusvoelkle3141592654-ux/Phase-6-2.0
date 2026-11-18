package app.novaos.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.novaos.design.ChoiceRow
import app.novaos.design.ColorPickerRow
import app.novaos.design.DotMatrixText
import app.novaos.design.Nova
import app.novaos.design.NovaButton
import app.novaos.design.NovaFonts
import app.novaos.design.NovaMotion
import app.novaos.design.NovaSurface
import app.novaos.design.NovaText
import app.novaos.design.SettingRow
import app.novaos.design.SliderRow
import app.novaos.design.SwitchRow
import app.novaos.design.toTextStyle
import app.novaos.model.AnimArea
import app.novaos.model.AnimationStyle
import app.novaos.model.ColorPalette
import app.novaos.model.ColorUtil
import app.novaos.model.DotIntensity
import app.novaos.model.FontChoice
import app.novaos.model.SurfaceBackground
import app.novaos.model.TextStyleSpec
import app.novaos.model.ThemeMode
import app.novaos.model.ThemePresets
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PersonalizationScreen() {
    val nav = LocalNav.current
    val ctrl = LocalContainer.current.controller
    val theme = LocalProfile.current.theme
    SettingsScreen(tr("Personalisierung", "Personalization"), preview = true) {
        InfoText(tr("Nova Customization Engine: Jede Änderung gilt sofort für das aktive Profil.", "Nova Customization Engine: every change applies instantly to the active profile."))
        Group(tr("Schnell anpassen", "Quick adjust")) {
            ChoiceRow(tr("Modus", "Mode"), ThemeMode.entries, theme.colors.mode, {
                when (it) { ThemeMode.DARK -> tr("Dunkel", "Dark"); ThemeMode.LIGHT -> tr("Hell", "Light"); ThemeMode.SYSTEM -> "System" }
            }, { m -> ctrl.editTheme { it.copy(colors = it.colors.copy(mode = m)) } })
            ColorPickerRow(tr("Akzentfarbe", "Accent color"), theme.colors.dark.accent, { hex ->
                ctrl.editTheme { it.copy(colors = it.colors.copy(dark = it.colors.dark.copy(accent = hex), light = it.colors.light.copy(accent = hex))) }
            })
            ChoiceRow(tr("Punktmatrix", "Dot matrix"), DotIntensity.entries, theme.typography.dotIntensity, { dotLabel(it) }, { d ->
                ctrl.editTheme { it.copy(typography = it.typography.copy(dotIntensity = d)) }
            })
            SwitchRow(tr("Glaseffekte", "Glass effects"), null, theme.glass.enabled, { on -> ctrl.editTheme { it.copy(glass = it.glass.copy(enabled = on)) } })
        }
        Group(tr("Studios", "Studios")) {
            listOf(
                "themes" to tr("Themes", "Themes"), "colors" to tr("Farben & Theme-Editor", "Colors & theme editor"), "typography" to tr("Typografie-Studio", "Typography studio"),
                "icons" to tr("Icon-Studio", "Icon studio"), "animations" to tr("Animation Studio", "Animation studio"), "glass" to tr("Liquid-Glass-Studio", "Liquid glass studio"),
                "wallpaper" to tr("Wallpaper Engine", "Wallpaper engine"), "widgets" to tr("Widget-Editor", "Widget editor"), "widget_creator" to tr("Widget Creator", "Widget creator"),
                "home" to tr("Homescreen", "Home screen"), "drawer" to tr("App Drawer", "App drawer"), "control_center" to tr("Kontrollzentrum", "Control center"),
                "notifications" to tr("Benachrichtigungen", "Notifications"), "gestures" to tr("Gesten", "Gestures"), "profiles" to tr("Profile", "Profiles"),
            ).forEach { (r, t) -> SettingRow(t, onClick = { nav.push(r) }) }
        }
    }
}

fun dotLabel(d: DotIntensity) = when (d) {
    DotIntensity.OFF -> tr("Aus", "Off")
    DotIntensity.ACCENT -> tr("Akzent", "Accent")
    DotIntensity.BALANCED -> tr("Ausgewogen", "Balanced")
    DotIntensity.STRONG -> tr("Stark", "Strong")
}

// ============================================================================ Farben

@Composable
fun ColorsScreen() {
    val ctrl = LocalContainer.current.controller
    val theme = LocalProfile.current.theme
    var editDark by remember { mutableStateOf(theme.colors.mode != ThemeMode.LIGHT) }
    val p = if (editDark) theme.colors.dark else theme.colors.light
    fun edit(f: (ColorPalette) -> ColorPalette) = ctrl.editTheme { t ->
        t.copy(colors = if (editDark) t.colors.copy(dark = f(t.colors.dark)) else t.colors.copy(light = f(t.colors.light)))
    }
    SettingsScreen(tr("Farben", "Colors"), preview = true) {
        Group(tr("Grundlagen", "Basics")) {
            ChoiceRow(tr("Modus", "Mode"), ThemeMode.entries, theme.colors.mode, {
                when (it) { ThemeMode.DARK -> tr("Dunkel", "Dark"); ThemeMode.LIGHT -> tr("Hell", "Light"); ThemeMode.SYSTEM -> "System" }
            }, { m -> ctrl.editTheme { it.copy(colors = it.colors.copy(mode = m)) } })
            SwitchRow(tr("Dynamische Wallpaper-Farben", "Dynamic wallpaper colors"), tr("Akzentfarbe aus dem Hintergrundbild ableiten", "Derive accent from wallpaper"), theme.colors.dynamicFromWallpaper, { on ->
                ctrl.editTheme { it.copy(colors = it.colors.copy(dynamicFromWallpaper = on)) }
            })
            ChoiceRow(tr("Bearbeitete Palette", "Edited palette"), listOf(true, false), editDark, { if (it) tr("Dunkel", "Dark") else tr("Hell", "Light") }, { editDark = it })
        }
        Group(tr("Farb-Presets", "Color presets")) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePresets.all.take(9).forEach { t ->
                    val pal = if (editDark) t.colors.dark else t.colors.light
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(app.novaos.design.hexColor(pal.background, Nova.colors.background))
                            .then(Modifier.padding(7.dp)),
                    ) {
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape).background(app.novaos.design.hexColor(pal.accent, Nova.colors.accent))
                                .clickable { edit { pal } },
                        )
                    }
                }
            }
            InfoText(tr("Tippen übernimmt die komplette Palette eines Presets.", "Tap to apply a preset palette."))
        }
        Group(tr("Farben", "Colors")) {
            ColorPickerRow(tr("Primärfarbe", "Primary"), p.primary, { v -> edit { it.copy(primary = v) } })
            ColorPickerRow(tr("Sekundärfarbe", "Secondary"), p.secondary, { v -> edit { it.copy(secondary = v) } })
            ColorPickerRow(tr("Akzentfarbe", "Accent"), p.accent, { v -> edit { it.copy(accent = v) } })
            ColorPickerRow(tr("Hintergrund", "Background"), p.background, { v -> edit { it.copy(background = v) } })
            ColorPickerRow(tr("Oberfläche", "Surface"), p.surface, { v -> edit { it.copy(surface = v) } })
            ColorPickerRow(tr("Text", "Text"), p.text, { v -> edit { it.copy(text = v) } })
            ColorPickerRow(tr("Text sekundär", "Secondary text"), p.textSecondary, { v -> edit { it.copy(textSecondary = v) } })
            ColorPickerRow(tr("Icons", "Icons"), p.icon, { v -> edit { it.copy(icon = v) } })
            ColorPickerRow(tr("Widgets", "Widgets"), p.widget, { v -> edit { it.copy(widget = v) } })
            ColorPickerRow(tr("Rahmen", "Borders"), p.border, { v -> edit { it.copy(border = v) } })
            ColorPickerRow(tr("Schatten", "Shadows"), p.shadow, { v -> edit { it.copy(shadow = v) } })
        }
        Group(tr("Verläufe, Transparenz, Kontrast", "Gradients, transparency, contrast")) {
            SwitchRow(tr("Farbverlauf für Flächen", "Gradient surfaces"), null, p.useGradient, { on -> edit { it.copy(useGradient = on) } })
            ColorPickerRow(tr("Verlauf Start", "Gradient start"), p.gradientStart, { v -> edit { it.copy(gradientStart = v) } })
            ColorPickerRow(tr("Verlauf Ende", "Gradient end"), p.gradientEnd, { v -> edit { it.copy(gradientEnd = v) } })
            SliderRow(tr("Deckkraft der Oberflächen", "Surface opacity"), p.surfaceOpacity, { v -> edit { it.copy(surfaceOpacity = v) } })
            SliderRow(tr("Kontrast", "Contrast"), p.contrast, { v -> edit { it.copy(contrast = v) } }, 0.5f..1.5f, { "%.2f".format(it) })
            val ratio = ColorUtil.contrastRatio(ColorUtil.parseHex(p.text) ?: 0xFFFFFFFF, ColorUtil.parseHex(p.background) ?: 0xFF000000)
            InfoText(tr("Kontrastverhältnis Text/Hintergrund: %.1f : 1".format(ratio) + if (ratio < 4.5) " – unter WCAG-Empfehlung (4,5 : 1)" else " – gut lesbar",
                "Text/background contrast: %.1f : 1".format(ratio) + if (ratio < 4.5) " – below WCAG (4.5 : 1)" else " – readable"))
        }
        Group(tr("Eigene Palette", "Own palette")) {
            ColorPickerRow(tr("Farbe merken", "Save color"), null, { v ->
                ctrl.editTheme { it.copy(colors = it.colors.copy(savedSwatches = (it.colors.savedSwatches + v).distinct().takeLast(18))) }
            })
            if (theme.colors.savedSwatches.isNotEmpty()) ColorPickerRow(tr("Gemerkte Farben → Akzent", "Saved colors → accent"), p.accent, { v -> edit { it.copy(accent = v) } }, swatches = theme.colors.savedSwatches)
        }
    }
}

// ============================================================================ Typografie

@Composable
private fun TextStyleEditor(title: String, spec: TextStyleSpec, fonts: List<FontChoice> = FontChoice.entries.filter { it != FontChoice.NOVA_DOT }, onChange: (TextStyleSpec) -> Unit) {
    Group(title) {
        Text(
            tr("Vorschau 16:42 Aa Ää", "Preview 16:42 Aa"),
            style = spec.toTextStyle(Nova.colors.text).let { if (it.fontSize.value > 40) it.copy(fontSize = it.fontSize * 0.6f) else it },
            modifier = Modifier.padding(12.dp),
        )
        ChoiceRow(tr("Schriftfamilie", "Font family"), fonts, spec.font, { it.label }, { f -> onChange(spec.copy(font = f)) })
        SliderRow(tr("Größe", "Size"), spec.sizeSp, { onChange(spec.copy(sizeSp = it)) }, 8f..96f, { "${it.roundToInt()} sp" })
        SliderRow(tr("Gewicht", "Weight"), spec.weight.toFloat(), { onChange(spec.copy(weight = (it / 100).roundToInt() * 100)) }, 100f..900f, { "${it.roundToInt()}" }, steps = 7)
        SliderRow(tr("Buchstabenabstand", "Letter spacing"), spec.letterSpacing, { onChange(spec.copy(letterSpacing = it)) }, -0.1f..0.3f, { "%.2f em".format(it) })
        SliderRow(tr("Zeilenabstand", "Line height"), spec.lineHeight, { onChange(spec.copy(lineHeight = it)) }, 0.8f..2f, { "%.2f×".format(it) })
        SliderRow(tr("Transparenz", "Opacity"), spec.alpha, { onChange(spec.copy(alpha = it)) }, 0.2f..1f)
        ColorPickerRow(tr("Schriftfarbe", "Text color"), spec.color, { onChange(spec.copy(color = it)) }, allowNone = true, onNone = { onChange(spec.copy(color = null)) })
    }
}

@Composable
fun TypographyScreen() {
    val ctrl = LocalContainer.current.controller
    val t = LocalProfile.current.theme.typography
    SettingsScreen(tr("Typografie", "Typography"), preview = true) {
        InfoText(tr("Mitgeliefert: Space Grotesk, Inter, Space Mono, JetBrains Mono, Doto (alle SIL Open Font License) sowie die eigene Nova-Punktmatrix.", "Bundled: Space Grotesk, Inter, Space Mono, JetBrains Mono, Doto (SIL OFL) and the own Nova dot matrix."))
        Group(tr("Punktmatrix", "Dot matrix")) {
            Box(Modifier.padding(12.dp)) { DotMatrixText("16:42", dotSize = 5.dp, color = Nova.colors.text, dimColor = Nova.colors.text.copy(alpha = 0.07f)) }
            ChoiceRow(tr("Stärke", "Level"), DotIntensity.entries, t.dotIntensity, { dotLabel(it) }, { d -> ctrl.editTheme { it.copy(typography = it.typography.copy(dotIntensity = d)) } })
            ChoiceRow(tr("Punktschrift", "Dot font"), listOf(FontChoice.NOVA_DOT, FontChoice.DOTO), t.dotFont, { it.label }, { f -> ctrl.editTheme { it.copy(typography = it.typography.copy(dotFont = f)) } })
            SwitchRow(tr("App-Namen in Punktmatrix", "Dot-matrix app labels"), tr("Nur bei Stärke „Stark“", "Only with level “Strong”"), t.dotAppLabels, { on -> ctrl.editTheme { it.copy(typography = it.typography.copy(dotAppLabels = on)) } })
        }
        TextStyleEditor(tr("Uhr", "Clock"), t.clock, FontChoice.entries) { s -> ctrl.editTheme { it.copy(typography = it.typography.copy(clock = s)) } }
        TextStyleEditor(tr("Überschriften", "Headlines"), t.headline) { s -> ctrl.editTheme { it.copy(typography = it.typography.copy(headline = s)) } }
        TextStyleEditor(tr("Fließtext", "Body"), t.body) { s -> ctrl.editTheme { it.copy(typography = it.typography.copy(body = s)) } }
        TextStyleEditor(tr("Widgets", "Widgets"), t.widget) { s -> ctrl.editTheme { it.copy(typography = it.typography.copy(widget = s)) } }
        TextStyleEditor(tr("App-Namen", "App labels"), t.appLabel) { s -> ctrl.editTheme { it.copy(typography = it.typography.copy(appLabel = s)) } }
    }
}

// ============================================================================ Animationen

@Composable
fun AnimationPreview(style: AnimationStyle, speed: Float, duration: Int) {
    val spec = app.novaos.model.AnimationSpec(style = style, speed = speed, durationMs = duration)
    var run by remember { mutableIntStateOf(0) }
    val a = remember(run, style) { Animatable(0f) }
    LaunchedEffect(run, style, speed, duration) {
        a.snapTo(0f)
        a.animateTo(1f, NovaMotion.spec(spec, AnimArea.OPEN))
        delay(500)
        run++
    }
    val v = a.value
    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxSize()) {
            Box(
                Modifier.offset(x = (v * 200).dp.coerceAtMost(220.dp)).size(46.dp).graphicsLayer {
                    val s = if (style == AnimationStyle.NOTHING_DOTS) ((v * 6).toInt() / 6f) else v
                    alpha = 0.3f + 0.7f * s; scaleX = 0.7f + 0.3f * s; scaleY = 0.7f + 0.3f * s
                }.clip(RoundedCornerShape(14.dp)).background(Nova.colors.accent),
            )
        }
    }
}

@Composable
fun AnimationsScreen() {
    val ctrl = LocalContainer.current.controller
    val cfg = LocalConfig.current
    val m = LocalProfile.current.theme.animation
    SettingsScreen(tr("Animation Studio", "Animation studio")) {
        Group(tr("Stil", "Style")) {
            AnimationPreview(m.style, m.speed, m.durationMs)
            ChoiceRow(tr("Animationsstil", "Animation style"), AnimationStyle.entries, m.style, { it.label }, { s -> ctrl.editTheme { it.copy(animation = it.animation.copy(style = s)) } })
            SliderRow(tr("Geschwindigkeit", "Speed"), m.speed, { v -> ctrl.editTheme { it.copy(animation = it.animation.copy(speed = v)) } }, 0.25f..3f, { "%.2f×".format(it) })
            SliderRow(tr("Übergangsdauer", "Transition duration"), m.durationMs.toFloat(), { v -> ctrl.editTheme { it.copy(animation = it.animation.copy(durationMs = v.roundToInt())) } }, 80f..1200f, { "${it.roundToInt()} ms" })
            SwitchRow(tr("Touch-Animationen", "Touch feedback"), null, m.touchFeedback, { on -> ctrl.editTheme { it.copy(animation = it.animation.copy(touchFeedback = on)) } })
            SwitchRow(tr("Hintergrundanimation", "Background animation"), tr("Standard aus, schont den Akku", "Off by default to save battery"), m.backgroundAnimation, { on -> ctrl.editTheme { it.copy(animation = it.animation.copy(backgroundAnimation = on)) } })
        }
        Group(tr("Pro Bereich", "Per area")) {
            AnimArea.entries.forEach { area ->
                val current = m.overrides[area]
                Column(Modifier.padding(vertical = 4.dp)) {
                    ChoiceRow(area.label, listOf<AnimationStyle?>(null) + AnimationStyle.entries, current, { it?.label ?: tr("Wie global", "Global") }, { s ->
                        ctrl.editTheme { t -> t.copy(animation = t.animation.copy(overrides = if (s == null) t.animation.overrides - area else t.animation.overrides + (area to s))) }
                    })
                    if (current != null) AnimationPreview(current, m.speed, m.durationMs)
                }
            }
        }
        Group(tr("Performance", "Performance")) {
            SwitchRow(tr("Performance-Modus", "Performance mode"), tr("Reduziert Animationen und Effekte global", "Reduces animations and effects"), cfg.general.performanceMode, { on -> ctrl.editGeneral { it.copy(performanceMode = on) } })
        }
    }
}

// ============================================================================ Liquid Glass

@Composable
fun GlassScreen() {
    val ctrl = LocalContainer.current.controller
    val g = LocalProfile.current.theme.glass
    fun e(f: (app.novaos.model.GlassSpec) -> app.novaos.model.GlassSpec) = ctrl.editTheme { it.copy(glass = f(it.glass)) }
    SettingsScreen(tr("Liquid-Glass-Studio", "Liquid glass studio"), preview = true) {
        Group(tr("Vorschau", "Preview")) {
            Box(Modifier.fillMaxWidth().height(110.dp).padding(12.dp)) {
                NovaSurface(Modifier.fillMaxSize(), RoundedCornerShape(26.dp), SurfaceBackground.GLASS, g.transparency) {
                    Column(Modifier.padding(16.dp)) {
                        NovaText(tr("Glasfläche", "Glass surface"), Nova.type.headline)
                        Text(tr("So wirken Karten, Dock und Panels.", "This is how cards, dock and panels look."), style = Nova.type.caption, color = Nova.colors.textSecondary)
                    }
                }
            }
        }
        Group(tr("Material", "Material")) {
            SwitchRow(tr("Glaseffekte aktiv", "Glass effects on"), tr("Aus = flache, deckende Flächen (sparsam)", "Off = flat opaque surfaces"), g.enabled, { on -> e { it.copy(enabled = on) } })
            AnimatedVisibility(g.enabled) {
                Column {
                    SliderRow(tr("Glastransparenz", "Transparency"), g.transparency, { v -> e { it.copy(transparency = v) } })
                    SliderRow(tr("Hintergrundunschärfe", "Background blur"), g.blurDp, { v -> e { it.copy(blurDp = v) } }, 0f..40f, { "${it.roundToInt()} dp" })
                    SliderRow(tr("Lichtreflexion", "Light reflection"), g.reflection, { v -> e { it.copy(reflection = v) } })
                    SliderRow(tr("Oberflächentiefe", "Surface depth"), g.depth, { v -> e { it.copy(depth = v) } })
                    SliderRow(tr("Rahmenintensität", "Border intensity"), g.border, { v -> e { it.copy(border = v) } })
                    SliderRow(tr("Schatten", "Shadow"), g.shadow, { v -> e { it.copy(shadow = v) } })
                    SliderRow(tr("Glanz", "Gloss"), g.gloss, { v -> e { it.copy(gloss = v) } })
                    SliderRow(tr("Materialstärke", "Material thickness"), g.thickness, { v -> e { it.copy(thickness = v) } })
                    SliderRow(tr("Transparente Verläufe", "Transparent gradients"), g.gradient, { v -> e { it.copy(gradient = v) } })
                }
            }
            SliderRow(tr("Hintergrundabdunklung (Overlays)", "Background dim (overlays)"), g.dim, { v -> e { it.copy(dim = v) } }, 0f..0.9f)
        }
        InfoText(tr("Die Unschärfe wird einmal pro Hintergrundbild berechnet und nicht laufend neu gezeichnet – das schont Akku und Leistung.", "Blur is computed once per wallpaper, not continuously – saving battery."))
    }
}

// ============================================================================ Performance

@Composable
fun PerformanceScreen() {
    val container = LocalContainer.current
    val ctrl = container.controller
    val cfg = LocalConfig.current
    SettingsScreen(tr("Performance", "Performance")) {
        Group {
            SwitchRow(tr("Performance-Modus", "Performance mode"), tr("Keine Unschärfe, Schatten und Reflexe; kurze, einfache Animationen", "No blur, shadows and reflections; short simple animations"), cfg.general.performanceMode, { on -> ctrl.editGeneral { it.copy(performanceMode = on) } })
            SwitchRow(tr("Haptisches Feedback", "Haptic feedback"), null, cfg.general.haptics, { on -> ctrl.editGeneral { it.copy(haptics = on) } })
            SwitchRow(tr("App-Nutzung zählen", "Count app launches"), tr("Für die Sortierung „Meistgenutzt“, nur lokal", "For “most used” sorting, local only"), cfg.general.trackAppUsage, { on -> ctrl.editGeneral { it.copy(trackAppUsage = on) } })
        }
        Group(tr("So spart NovaOS Energie", "How NovaOS saves energy")) {
            listOf(
                tr("Keine Hintergrunddienste: Wetter, Profile und Systemwerte werden nur aktualisiert, während der Homescreen sichtbar ist.", "No background services: data updates only while the home screen is visible."),
                tr("Hintergrundbilder und Glas-Unschärfe werden einmal berechnet und zwischengespeichert.", "Wallpapers and glass blur are computed once and cached."),
                tr("App-Icons werden einmal gerendert und zwischengespeichert.", "App icons are rendered once and cached."),
                tr("Keine dauerhaften Animationen; Hintergrundanimation ist standardmäßig aus.", "No permanent animations; background animation is off by default."),
            ).forEach { InfoText("• $it") }
        }
        Group(tr("Zwischenspeicher", "Cache")) {
            SettingRow(tr("Icon-Zwischenspeicher leeren", "Clear icon cache"), tr("Icons werden neu gezeichnet", "Icons will be redrawn"), onClick = { container.icons.clear() })
        }
    }
}
