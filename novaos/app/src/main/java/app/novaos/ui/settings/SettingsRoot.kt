package app.novaos.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import app.novaos.design.Nova
import app.novaos.design.NovaTheme
import app.novaos.model.AppSearch
import app.novaos.model.ShortcutTarget
import app.novaos.model.WidgetRequirement
import app.novaos.system.MediaHub
import app.novaos.system.NotificationHub
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.rememberNovaWidgetHost
import app.novaos.ui.common.tr
import app.novaos.ui.setup.SetupAssistant
import app.novaos.widgets.LocalWidgetHost
import app.novaos.widgets.WidgetActions

data class Route(val name: String, val arg: String? = null)

class Nav(private val stack: MutableList<Route>, private val onExit: () -> Unit) {
    val current: Route get() = stack.last()
    fun push(name: String, arg: String? = null) { stack.add(Route(name, arg)) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit() }
    fun replace(name: String, arg: String? = null) { stack[stack.lastIndex] = Route(name, arg) }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav fehlt") }

/** Laufzeitberechtigungen aus den Einstellungen anfragen. */
val LocalRequestPermission = staticCompositionLocalOf<(String) -> Unit> { {} }

data class Category(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

val Categories: List<Category>
    get() = listOf(
        Category("general", tr("Allgemein", "General"), tr("Sprache, Uhr, Wetter, Standardlauncher", "Language, clock, weather, default launcher"), Icons.Filled.Settings),
        Category("personalization", tr("Personalisierung", "Personalization"), tr("Customization Engine – alles auf einen Blick", "Customization engine overview"), Icons.Filled.Tune),
        Category("home", tr("Homescreen", "Home screen"), tr("Raster, Seiten, Dock, Übergänge", "Grid, pages, dock, transitions"), Icons.Filled.Home),
        Category("drawer", tr("App Drawer", "App drawer"), tr("Raster, Suche, Sortierung, ausgeblendete Apps", "Grid, search, sorting, hidden apps"), Icons.Filled.Apps),
        Category("icons", tr("Icons", "Icons"), tr("Icon-Studio, Formen, Icon-Pakete", "Icon studio, shapes, icon packs"), Icons.Filled.Brush),
        Category("widgets", tr("Widgets", "Widgets"), tr("Widgets bearbeiten, Vorlagen, Import/Export", "Edit widgets, templates, import/export"), Icons.Filled.Widgets),
        Category("widget_creator", tr("Widget Creator", "Widget creator"), tr("Eigene Widgets ohne Programmieren", "Build widgets without code"), Icons.Filled.DashboardCustomize),
        Category("themes", tr("Themes", "Themes"), tr("Bibliothek, eigene Themes, Import/Export", "Library, own themes, import/export"), Icons.Filled.Palette),
        Category("colors", tr("Farben", "Colors"), tr("Theme-Editor, Paletten, HEX", "Theme editor, palettes, HEX"), Icons.Filled.ColorLens),
        Category("typography", tr("Typografie", "Typography"), tr("Schriften, Größen, Punktmatrix", "Fonts, sizes, dot matrix"), Icons.Filled.TextFields),
        Category("animations", tr("Animationen", "Animations"), tr("Animation Studio mit Vorschau", "Animation studio with preview"), Icons.Filled.Animation),
        Category("glass", tr("Liquid Glass", "Liquid glass"), tr("Transparenz, Unschärfe, Reflexion", "Transparency, blur, reflection"), Icons.Filled.BlurOn),
        Category("control_center", tr("Kontrollzentrum", "Control center"), tr("Kacheln, Layouts, Farben", "Tiles, layouts, colors"), Icons.Filled.DashboardCustomize),
        Category("notifications", tr("Benachrichtigungen", "Notifications"), tr("Karten, Gruppierung, Zugriff", "Cards, grouping, access"), Icons.Filled.Notifications),
        Category("gestures", tr("Gesten", "Gestures"), tr("Nova Gesture Engine", "Nova gesture engine"), Icons.Filled.Gesture),
        Category("wallpaper", tr("Wallpaper", "Wallpaper"), tr("Eigene Bilder, generierte Designs, Effekte", "Own images, generated designs, effects"), Icons.Filled.Wallpaper),
        Category("profiles", tr("Profile", "Profiles"), tr("Work, Gaming, Minimal, Media, Regeln", "Work, gaming, minimal, media, rules"), Icons.Filled.Person),
        Category("performance", tr("Performance", "Performance"), tr("Effekte reduzieren, Akku schonen", "Reduce effects, save battery"), Icons.Filled.Speed),
        Category("privacy", tr("Datenschutz", "Privacy"), tr("Welche Daten NovaOS nutzt", "What data NovaOS uses"), Icons.Filled.PrivacyTip),
        Category("permissions", tr("Berechtigungen", "Permissions"), tr("Freigaben prüfen und erteilen", "Review and grant access"), Icons.Filled.Security),
        Category("backup", tr("Backup", "Backup"), tr("Sichern und wiederherstellen", "Back up and restore"), Icons.Filled.Backup),
        Category("about", tr("Über NovaOS", "About NovaOS"), tr("Version, Lizenzen, Funktionsübersicht", "Version, licenses, features"), Icons.Filled.Info),
    )

data class IndexEntry(val title: String, val category: String, val route: String, val keywords: String = "")

/** Durchsuchbarer Index aller Einstellungen. */
object SettingsIndex {
    private val entries: List<IndexEntry>
        get() = Categories.map { IndexEntry(it.title, it.subtitle, it.route) } + listOf(
            IndexEntry(tr("Rastergröße", "Grid size"), tr("Homescreen", "Home"), "home", "spalten zeilen columns rows"),
            IndexEntry(tr("Icon-Größe", "Icon size"), tr("Homescreen", "Home"), "home", "symbol"),
            IndexEntry(tr("Freier Layoutmodus", "Free layout"), tr("Homescreen", "Home"), "home", "frei raster"),
            IndexEntry(tr("Dock-Position und Form", "Dock position and shape"), tr("Homescreen", "Home"), "home", "dock"),
            IndexEntry(tr("Seitenübergänge", "Page transitions"), tr("Homescreen", "Home"), "home", "würfel cube tiefe"),
            IndexEntry(tr("Seitenindikator", "Page indicator"), tr("Homescreen", "Home"), "home", "punkte"),
            IndexEntry(tr("Ordnerdesign", "Folder design"), tr("Homescreen", "Home"), "home", "ordner folder"),
            IndexEntry(tr("Ausgeblendete Apps", "Hidden apps"), tr("App Drawer", "Drawer"), "drawer", "verstecken hide"),
            IndexEntry(tr("Suchleisten-Design", "Search bar style"), tr("App Drawer", "Drawer"), "drawer", "suche"),
            IndexEntry(tr("Icon-Form", "Icon shape"), tr("Icons", "Icons"), "icons", "kreis squircle sechseck"),
            IndexEntry(tr("Punkt-Icons", "Dot icons"), tr("Icons", "Icons"), "icons", "nothing dots monochrom"),
            IndexEntry(tr("Icon-Paket speichern", "Save icon pack"), tr("Icons", "Icons"), "icons", "paket pack"),
            IndexEntry(tr("Akzentfarbe", "Accent color"), tr("Farben", "Colors"), "colors", "rot hex"),
            IndexEntry(tr("Heller oder dunkler Modus", "Light or dark mode"), tr("Farben", "Colors"), "colors", "dark light hell dunkel"),
            IndexEntry(tr("Dynamische Wallpaper-Farben", "Dynamic wallpaper colors"), tr("Farben", "Colors"), "colors", "dynamisch"),
            IndexEntry(tr("Uhrentypografie", "Clock typography"), tr("Typografie", "Typography"), "typography", "uhr schrift"),
            IndexEntry(tr("Punktmatrix-Stärke", "Dot matrix level"), tr("Typografie", "Typography"), "typography", "punkt dots"),
            IndexEntry(tr("Animationsgeschwindigkeit", "Animation speed"), tr("Animationen", "Animations"), "animations", "tempo"),
            IndexEntry(tr("App-Startanimation", "App launch animation"), tr("Animationen", "Animations"), "animations", "start"),
            IndexEntry(tr("Hintergrundunschärfe", "Background blur"), tr("Liquid Glass", "Liquid glass"), "glass", "blur glas"),
            IndexEntry(tr("Kacheln anordnen", "Arrange tiles"), tr("Kontrollzentrum", "Control center"), "control_center", "kachel tile"),
            IndexEntry(tr("Benachrichtigungszugriff", "Notification access"), tr("Benachrichtigungen", "Notifications"), "notifications", "zugriff"),
            IndexEntry(tr("Doppeltippen zum Sperren", "Double tap to lock"), tr("Gesten", "Gestures"), "gestures", "sperren lock"),
            IndexEntry(tr("Eigenes Bild als Hintergrund", "Own image as wallpaper"), tr("Wallpaper", "Wallpaper"), "wallpaper", "foto bild"),
            IndexEntry(tr("Automatischer Profilwechsel", "Automatic profile switching"), tr("Profile", "Profiles"), "profiles", "regel zeit bluetooth wlan"),
            IndexEntry(tr("Wetter-Ort", "Weather location"), tr("Allgemein", "General"), "weather", "stadt ort"),
            IndexEntry(tr("Als Standardlauncher festlegen", "Set as default launcher"), tr("Allgemein", "General"), "general", "home standard"),
            IndexEntry(tr("Aufgaben und Notizen", "Tasks and notes"), tr("Produktivität", "Productivity"), "productivity", "todo notiz projekt erinnerung"),
            IndexEntry(tr("Einrichtungsassistent erneut starten", "Run setup assistant again"), tr("Allgemein", "General"), "setup", "assistent"),
        )

    fun search(q: String): List<IndexEntry> {
        val n = AppSearch.normalize(q.trim())
        if (n.isEmpty()) return emptyList()
        return entries.filter { e -> AppSearch.normalize("${e.title} ${e.category} ${e.keywords}").contains(n) }
    }
}

@Composable
fun SettingsRoot(initialRoute: String?, initialArg: String?, onExit: () -> Unit) {
    val c = LocalContainer.current
    val cfg = c.config.config.collectAsState().value ?: run { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    val stack = remember { mutableStateListOf(Route("root")).apply { if (initialRoute != null) add(Route(initialRoute, initialArg)) } }
    val nav = remember { Nav(stack, onExit) }
    val ctx = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { c.controls.refresh() }
    val profile = cfg.activeProfile

    val actions = remember {
        object : WidgetActions {
            override fun launchApp(key: String) { c.apps.launch(key, null, null) }
            override fun openTarget(target: ShortcutTarget) {
                when (target) {
                    is ShortcutTarget.App -> c.apps.launch(target.appKey, null, null)
                    is ShortcutTarget.SystemSettings -> c.apps.startIntent(Intent(target.action))
                    is ShortcutTarget.Url -> c.apps.startIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target.url)))
                    is ShortcutTarget.DeepShortcut -> c.apps.startShortcut(target.packageName, target.shortcutId, target.userSerial, null)
                    is ShortcutTarget.Nova -> Unit
                }
            }
            override fun editWidget(itemId: String) = nav.push("widget_editor", itemId)
            override fun requestRequirement(requirement: WidgetRequirement) {
                when (requirement) {
                    WidgetRequirement.CALENDAR -> permissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                    WidgetRequirement.CONTACTS -> permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    WidgetRequirement.NOTIFICATION_ACCESS -> c.apps.startIntent(NotificationHub.accessSettingsIntent(ctx))
                    WidgetRequirement.USAGE_ACCESS -> c.apps.startIntent(c.systemInfo.usageAccessIntent())
                    else -> nav.push("permissions")
                }
            }
            override fun openWeatherSettings() = nav.push("weather")
            override fun openProductivity(section: String) = nav.push("productivity", section)
            override fun openNotification(key: String) { NotificationHub.open(key) }
            override fun openClock() = c.apps.openClock()
            override fun openAppDetails(key: String) = c.apps.openAppDetails(key)
            override fun openMediaPlayer() = MediaHub.openPlayer(ctx)
        }
    }

    BackHandler { nav.back() }
    NovaTheme(profile.theme, cfg.general.performanceMode) {
        val host = rememberNovaWidgetHost(profile, actions)
        CompositionLocalProvider(
            LocalConfig provides cfg,
            LocalProfile provides profile,
            LocalNav provides nav,
            LocalWidgetHost provides host,
            LocalRequestPermission provides { p: String -> permissionLauncher.launch(p) },
        ) {
            Box(Modifier.fillMaxSize().background(Nova.colors.background)) {
                AnimatedContent(stack.last(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "settings") { r ->
                    Screen(r)
                }
            }
        }
    }
}

@Composable
private fun Screen(r: Route) {
    val c = LocalContainer.current
    val nav = LocalNav.current
    when (r.name) {
        "root" -> RootScreen()
        "general" -> GeneralScreen()
        "weather" -> WeatherScreen()
        "personalization" -> PersonalizationScreen()
        "home" -> HomeSettingsScreen()
        "drawer" -> DrawerSettingsScreen()
        "icons" -> IconStudioScreen(null)
        "icon_app" -> IconStudioScreen(r.arg)
        "widgets" -> WidgetsScreen()
        "widget_editor" -> WidgetEditorScreen(r.arg)
        "widget_creator" -> WidgetCreatorListScreen()
        "widget_creator_edit" -> WidgetCreatorEditScreen(r.arg)
        "themes" -> ThemesScreen()
        "colors" -> ColorsScreen()
        "typography" -> TypographyScreen()
        "animations" -> AnimationsScreen()
        "glass" -> GlassScreen()
        "control_center" -> ControlCenterSettingsScreen()
        "notifications" -> NotificationSettingsScreen()
        "gestures" -> GesturesScreen()
        "wallpaper" -> WallpaperScreen()
        "profiles" -> ProfilesScreen()
        "profile_rules" -> ProfileRulesScreen()
        "performance" -> PerformanceScreen()
        "privacy" -> PrivacyScreen()
        "permissions" -> PermissionsScreen()
        "backup" -> BackupScreen()
        "about" -> AboutScreen()
        "productivity" -> ProductivityScreen(r.arg)
        "setup" -> SetupAssistant(onFinish = { c.controller.completeSetup(); nav.back() }, requestHomeRole = null)
        else -> RootScreen()
    }
}

@Suppress("unused")
private val categoryIconsUsed = listOf(Icons.Filled.AccountTree)
