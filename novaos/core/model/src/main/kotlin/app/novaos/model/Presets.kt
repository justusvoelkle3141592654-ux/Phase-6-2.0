package app.novaos.model

/** Platzhalter für Standard-Apps, die beim ersten Start auf echte Apps aufgelöst werden. */
object RolePlaceholders {
    const val DIALER = "@role/dialer"
    const val SMS = "@role/sms"
    const val BROWSER = "@role/browser"
    const val CAMERA = "@role/camera"
    const val MUSIC = "@role/music"
    const val CALENDAR = "@role/calendar"
    const val EMAIL = "@role/email"
    const val MAPS = "@role/maps"
    const val GALLERY = "@role/gallery"
    const val SETTINGS = "@role/settings"
    val all = listOf(DIALER, SMS, BROWSER, CAMERA, MUSIC, CALENDAR, EMAIL, MAPS, GALLERY, SETTINGS)
    fun isPlaceholder(key: String) = key.startsWith("@role/")
}

private fun darkPalette(accent: String, bg: String = "#000000", surface: String = "#16161A", text: String = "#F2F2F2") = ColorPalette(
    primary = text, secondary = "#8E8E93", accent = accent, background = bg, surface = surface,
    text = text, textSecondary = "#9A9AA0", icon = text, widget = surface, border = "#33FFFFFF",
    shadow = "#000000", gradientStart = surface, gradientEnd = bg,
)

private fun lightPalette(accent: String, bg: String = "#F2F2F0", surface: String = "#FFFFFF", text: String = "#0D0D0F") = ColorPalette(
    primary = text, secondary = "#5C5C63", accent = accent, background = bg, surface = surface,
    text = text, textSecondary = "#5F5F66", icon = text, widget = surface, border = "#22000000",
    shadow = "#40000000", gradientStart = surface, gradientEnd = bg,
)

object ThemePresets {
    const val NOVA = "theme-nova"
    const val NOTHING_DOTS = "theme-nothing-dots"
    const val LIQUID_GLASS = "theme-liquid-glass"
    const val MONOCHROME = "theme-monochrome"
    const val DARK_PREMIUM = "theme-dark-premium"
    const val MINIMAL_ANDROID = "theme-minimal-android"
    const val RED_ACCENT = "theme-red-accent"
    const val SILVER = "theme-silver"
    const val EXPERIMENTAL = "theme-experimental"

    private const val RED = "#E5202E"

    val nova = ThemeSpec(
        id = NOVA, name = "NovaOS", builtIn = true,
        description = "Standard: dunkel, roter Akzent, starke Punktmatrix, dezentes Glas.",
        colors = ThemeColors(darkPalette(RED), lightPalette(RED)),
        wallpaper = WallpaperSpec(),
    )

    val nothingDots = ThemeSpec(
        id = NOTHING_DOTS, name = "Nothing Dots", builtIn = true,
        description = "Monochrom, Punktraster, Punkt-Icons, keine Glaseffekte.",
        colors = ThemeColors(
            darkPalette(RED, bg = "#000000", surface = "#111111").copy(surfaceOpacity = 0.92f),
            lightPalette(RED, bg = "#EDEDE9", surface = "#FAFAF7").copy(surfaceOpacity = 0.95f),
        ),
        typography = TypographySpec(
            headline = TextStyleSpec(FontChoice.DOTO, 22f, 800, 0.06f),
            body = TextStyleSpec(FontChoice.SPACE_MONO, 13.5f, 400),
            widget = TextStyleSpec(FontChoice.SPACE_MONO, 12.5f, 400),
            appLabel = TextStyleSpec(FontChoice.SPACE_MONO, 10.5f, 400, 0.02f),
            dotAppLabels = true,
        ),
        glass = GlassSpec(enabled = false),
        animation = AnimationSpec(style = AnimationStyle.NOTHING_DOTS),
        icons = IconStyleSpec(shape = IconShape.CIRCLE, rendering = IconRendering.DOTS, background = "#1A1A1A", foreground = "#F2F2F2"),
        dock = DockSpec(shape = DockShape.DOTS, background = SurfaceBackground.NONE),
        drawer = DrawerSpec(background = SurfaceBackground.SOLID, searchBar = SearchBarStyle.DOT_FRAME, transparency = 0.05f),
        controlCenter = ControlCenterStyle(CcLayout.NOTHING_GRID, background = SurfaceBackground.SOLID, transparency = 0.05f),
        notifications = NotificationSpec(background = SurfaceBackground.SOLID, cardCornerDp = 18f),
        home = HomeStyle(backgroundEffect = BackgroundEffect.DOT_GRID, indicator = IndicatorStyle.DOT_NUMBERS, folder = FolderStyle(shape = IconShape.CIRCLE, preview = FolderPreview.DOT)),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.DOT_GRID, "#000000", "#1C1C1C", RED)),
    )

    val liquidGlass = ThemeSpec(
        id = LIQUID_GLASS, name = "Liquid Glass", builtIn = true,
        description = "Transparente Glasflächen mit Tiefe und Lichtreflexen.",
        colors = ThemeColors(
            darkPalette("#7FB2FF", bg = "#05070C", surface = "#1C2433").copy(surfaceOpacity = 0.45f, useGradient = true, gradientStart = "#334A6B", gradientEnd = "#0B0F18"),
            lightPalette("#2F6FE0", bg = "#E8EEF6", surface = "#FFFFFF").copy(surfaceOpacity = 0.5f, useGradient = true),
        ),
        typography = TypographySpec(
            clock = TextStyleSpec(FontChoice.INTER, 72f, 200, -0.02f),
            headline = TextStyleSpec(FontChoice.INTER, 22f, 600),
            body = TextStyleSpec(FontChoice.INTER, 14f, 400),
            widget = TextStyleSpec(FontChoice.INTER, 13f, 500),
            appLabel = TextStyleSpec(FontChoice.INTER, 11.5f, 500),
            dotIntensity = DotIntensity.ACCENT,
        ),
        glass = GlassSpec(transparency = 0.62f, blurDp = 28f, reflection = 0.6f, depth = 0.6f, border = 0.6f, shadow = 0.45f, gloss = 0.55f, thickness = 0.35f, gradient = 0.6f),
        animation = AnimationSpec(style = AnimationStyle.LIQUID, durationMs = 380),
        icons = IconStyleSpec(shape = IconShape.SQUIRCLE, shadow = 0.35f),
        home = HomeStyle(backgroundEffect = BackgroundEffect.NONE, pageTransition = PageTransition.DEPTH, indicator = IndicatorStyle.LINE),
        controlCenter = ControlCenterStyle(CcLayout.GLASS_PANEL, transparency = 0.55f, tileRoundness = 0.55f),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.CHROME_ORBS, "#03060D", "#25344F", "#7FB2FF")),
    )

    val monochrome = ThemeSpec(
        id = MONOCHROME, name = "Monochrome", builtIn = true,
        description = "Nur Schwarz, Weiß und Grau. Monochrome Icons.",
        colors = ThemeColors(
            darkPalette("#FFFFFF", bg = "#0A0A0A", surface = "#1A1A1A"),
            lightPalette("#000000", bg = "#F5F5F5", surface = "#FFFFFF"),
        ),
        typography = TypographySpec(dotIntensity = DotIntensity.BALANCED),
        glass = GlassSpec(transparency = 0.3f, blurDp = 12f, reflection = 0.1f),
        icons = IconStyleSpec(shape = IconShape.ROUNDED_SQUARE, rendering = IconRendering.MONOCHROME, background = "#202020", foreground = "#FFFFFF"),
        animation = AnimationSpec(style = AnimationStyle.MINIMAL),
        home = HomeStyle(backgroundEffect = BackgroundEffect.NONE),
        controlCenter = ControlCenterStyle(CcLayout.COMPACT),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.GRADIENT, "#000000", "#2B2B2B", "#FFFFFF")),
    )

    val darkPremium = ThemeSpec(
        id = DARK_PREMIUM, name = "Dark Premium", builtIn = true,
        description = "Tiefes Schwarz, warme Goldtöne, edle Typografie.",
        colors = ThemeColors(
            darkPalette("#C9A55C", bg = "#050505", surface = "#141210", text = "#F3EEE4"),
            lightPalette("#9C7A35", bg = "#F4F0E8", surface = "#FFFDF8"),
        ),
        typography = TypographySpec(
            clock = TextStyleSpec(FontChoice.SPACE_GROTESK, 76f, 300, -0.03f),
            headline = TextStyleSpec(FontChoice.SPACE_GROTESK, 22f, 500, 0.02f),
            dotIntensity = DotIntensity.ACCENT,
        ),
        glass = GlassSpec(transparency = 0.35f, blurDp = 22f, reflection = 0.35f, gloss = 0.4f, border = 0.25f),
        animation = AnimationSpec(style = AnimationStyle.FLUID, durationMs = 360),
        icons = IconStyleSpec(shape = IconShape.CIRCLE, strokeWidth = 1f, strokeColor = "#66C9A55C"),
        home = HomeStyle(backgroundEffect = BackgroundEffect.VIGNETTE, indicator = IndicatorStyle.LINE),
        controlCenter = ControlCenterStyle(CcLayout.GLASS_PANEL, tileRoundness = 0.4f),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.CONCENTRIC, "#030303", "#1D1913", "#C9A55C")),
    )

    val minimalAndroid = ThemeSpec(
        id = MINIMAL_ANDROID, name = "Minimal Android", builtIn = true,
        description = "Klassische Android-Bedienung, ruhig und klar.",
        colors = ThemeColors(
            darkPalette("#8AB4F8", bg = "#121212", surface = "#1F1F1F"),
            lightPalette("#1A73E8", bg = "#FAFAFA", surface = "#FFFFFF"),
            mode = ThemeMode.SYSTEM,
        ),
        typography = TypographySpec(
            clock = TextStyleSpec(FontChoice.SYSTEM_SANS, 64f, 400),
            headline = TextStyleSpec(FontChoice.SYSTEM_SANS, 22f, 500),
            body = TextStyleSpec(FontChoice.SYSTEM_SANS, 14f, 400),
            widget = TextStyleSpec(FontChoice.SYSTEM_SANS, 13f, 400),
            appLabel = TextStyleSpec(FontChoice.SYSTEM_SANS, 12f, 400),
            dotIntensity = DotIntensity.OFF,
        ),
        glass = GlassSpec(enabled = false),
        animation = AnimationSpec(style = AnimationStyle.CLASSIC_ANDROID),
        icons = IconStyleSpec(shape = IconShape.CIRCLE),
        dock = DockSpec(shape = DockShape.ROUNDED, background = SurfaceBackground.SOLID),
        drawer = DrawerSpec(background = SurfaceBackground.SOLID, searchBar = SearchBarStyle.PILL, transparency = 0f),
        controlCenter = ControlCenterStyle(CcLayout.ANDROID_CLASSIC, background = SurfaceBackground.SOLID, tileRoundness = 0.6f),
        notifications = NotificationSpec(background = SurfaceBackground.SOLID),
        home = HomeStyle(backgroundEffect = BackgroundEffect.NONE, indicator = IndicatorStyle.DOTS),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.GEOMETRIC, "#101418", "#263238", "#8AB4F8")),
    )

    val redAccent = ThemeSpec(
        id = RED_ACCENT, name = "Red Accent", builtIn = true,
        description = "Schwarz mit kräftigem Rot, Punkt-Uhr, Glaskarten.",
        colors = ThemeColors(
            darkPalette("#FF2D3A", bg = "#000000", surface = "#1A0D0F").copy(useGradient = true, gradientStart = "#3A0A10", gradientEnd = "#000000"),
            lightPalette("#D7141F", bg = "#F6EDEE", surface = "#FFFFFF"),
        ),
        glass = GlassSpec(transparency = 0.4f, blurDp = 20f, reflection = 0.3f),
        icons = IconStyleSpec(shape = IconShape.SQUIRCLE, rendering = IconRendering.MONOCHROME, background = "#1E1012", foreground = "#FF2D3A"),
        controlCenter = ControlCenterStyle(CcLayout.NOTHING_GRID, activeColor = "#FF2D3A"),
        animation = AnimationSpec(style = AnimationStyle.ELASTIC),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.SPLIT, "#000000", "#1A0004", "#FF2D3A")),
    )

    val silver = ThemeSpec(
        id = SILVER, name = "Silver Edition", builtIn = true,
        description = "Helles Silber, gebürstete Flächen, feine Linien.",
        colors = ThemeColors(
            darkPalette("#C0C6CF", bg = "#15171A", surface = "#2A2D32", text = "#EEF1F4"),
            lightPalette("#5E6670", bg = "#DADDE2", surface = "#F3F4F6", text = "#1B1D21"),
            mode = ThemeMode.LIGHT,
        ),
        typography = TypographySpec(dotIntensity = DotIntensity.BALANCED),
        glass = GlassSpec(transparency = 0.5f, blurDp = 24f, reflection = 0.55f, gloss = 0.5f, border = 0.5f),
        icons = IconStyleSpec(shape = IconShape.ROUNDED_SQUARE, rendering = IconRendering.MONOCHROME, background = "#E9EBEE", foreground = "#3A3F46", shadow = 0.25f),
        animation = AnimationSpec(style = AnimationStyle.SMOOTH),
        home = HomeStyle(backgroundEffect = BackgroundEffect.NONE, indicator = IndicatorStyle.LINE),
        controlCenter = ControlCenterStyle(CcLayout.GLASS_PANEL, tileRoundness = 0.5f),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.CHROME_ORBS, "#C9CDD3", "#8A9099", "#FFFFFF")),
    )

    val experimental = ThemeSpec(
        id = EXPERIMENTAL, name = "Experimental", builtIn = true,
        description = "Ungewöhnliche Formen, Sechseck-Icons, Punktwellen.",
        colors = ThemeColors(
            darkPalette("#C6FF3D", bg = "#06070A", surface = "#12161C").copy(useGradient = true, gradientStart = "#1C2A12", gradientEnd = "#06070A"),
            lightPalette("#3E7D00", bg = "#EFF4E6", surface = "#FFFFFF"),
        ),
        typography = TypographySpec(
            body = TextStyleSpec(FontChoice.JETBRAINS_MONO, 13.5f, 400),
            widget = TextStyleSpec(FontChoice.JETBRAINS_MONO, 12.5f, 500),
            appLabel = TextStyleSpec(FontChoice.JETBRAINS_MONO, 10.5f, 500),
            dotAppLabels = true,
        ),
        glass = GlassSpec(transparency = 0.5f, blurDp = 16f, border = 0.7f),
        icons = IconStyleSpec(shape = IconShape.HEXAGON, rendering = IconRendering.DOTS, background = "#151A10", foreground = "#C6FF3D", dotGrid = 14),
        animation = AnimationSpec(style = AnimationStyle.ELASTIC, durationMs = 420),
        dock = DockSpec(shape = DockShape.SQUARE),
        home = HomeStyle(backgroundEffect = BackgroundEffect.SCANLINES, pageTransition = PageTransition.CUBE, indicator = IndicatorStyle.DOT_NUMBERS, folder = FolderStyle(shape = IconShape.HEXAGON)),
        controlCenter = ControlCenterStyle(CcLayout.NOTHING_GRID, tileRoundness = 0.2f),
        wallpaper = WallpaperSpec(source = WallpaperSource.Generated(GeneratedWallpaper.DOT_WAVE, "#06070A", "#1A2312", "#C6FF3D")),
    )

    val all: List<ThemeSpec> = listOf(nova, nothingDots, liquidGlass, monochrome, darkPremium, minimalAndroid, redAccent, silver, experimental)

    fun byId(id: String): ThemeSpec? = all.firstOrNull { it.id == id }
}

object Defaults {

    private fun w(type: WidgetType, style: WidgetStyle = WidgetStyle(), config: Map<String, String> = emptyMap()) =
        WidgetInstance(newId("w"), type, style, config)

    private fun widgetItem(type: WidgetType, x: Int, y: Int, w: Int = type.defaultW, h: Int = type.defaultH, style: WidgetStyle = WidgetStyle()) =
        HomeItem.Widget(newId("i"), ItemPos(x, y, w, h), w(type, style))

    private fun appItem(key: String, x: Int, y: Int) = HomeItem.App(newId("i"), ItemPos(x, y), key)

    /** Homescreen wie im Designkonzept: Punkt-Uhr, Wetter, Projekt, Musik, Dock. */
    fun standardLayout(): HomeLayout = HomeLayout(
        pages = listOf(
            HomePage(
                newId("page"),
                listOf(
                    widgetItem(WidgetType.CLOCK, 0, 0, 4, 2, WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, shadow = 0f)),
                    widgetItem(WidgetType.WEATHER, 0, 2, 2, 1),
                    widgetItem(WidgetType.PROJECTS, 2, 2, 2, 1),
                    widgetItem(WidgetType.MUSIC, 0, 3, 4, 1),
                    widgetItem(WidgetType.BATTERY, 0, 4, 2, 2),
                    widgetItem(WidgetType.CALENDAR, 2, 4, 2, 2),
                ),
            ),
            HomePage(
                newId("page"),
                listOf(
                    widgetItem(WidgetType.TASKS, 0, 0, 2, 2),
                    widgetItem(WidgetType.NOTES, 2, 0, 2, 2),
                    widgetItem(WidgetType.SYSTEM_STATUS, 0, 2, 4, 1),
                    appItem(RolePlaceholders.EMAIL, 0, 3),
                    appItem(RolePlaceholders.MAPS, 1, 3),
                    appItem(RolePlaceholders.GALLERY, 2, 3),
                    appItem(RolePlaceholders.SETTINGS, 3, 3),
                ),
            ),
        ),
        dockApps = listOf(RolePlaceholders.DIALER, RolePlaceholders.SMS, RolePlaceholders.BROWSER, RolePlaceholders.CAMERA),
    )

    fun workLayout(): HomeLayout = HomeLayout(
        pages = listOf(
            HomePage(
                newId("page"),
                listOf(
                    widgetItem(WidgetType.CLOCK, 0, 0, 4, 1, WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, layout = WidgetLayoutVariant.COMPACT)),
                    widgetItem(WidgetType.AGENDA, 0, 1, 4, 2),
                    widgetItem(WidgetType.TASKS, 0, 3, 2, 2),
                    widgetItem(WidgetType.PROJECTS, 2, 3, 2, 1),
                    widgetItem(WidgetType.REMINDERS, 2, 4, 2, 1),
                    appItem(RolePlaceholders.EMAIL, 0, 5),
                    appItem(RolePlaceholders.CALENDAR, 1, 5),
                ),
            ),
        ),
        dockApps = listOf(RolePlaceholders.DIALER, RolePlaceholders.EMAIL, RolePlaceholders.BROWSER, RolePlaceholders.CALENDAR),
    )

    fun gamingLayout(): HomeLayout = HomeLayout(
        pages = listOf(
            HomePage(
                newId("page"),
                listOf(
                    widgetItem(WidgetType.CLOCK, 0, 0, 4, 1, WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, layout = WidgetLayoutVariant.COMPACT)),
                    widgetItem(WidgetType.SYSTEM_STATUS, 0, 1, 4, 1),
                    HomeItem.Widget(newId("i"), ItemPos(0, 2, 4, 2), w(WidgetType.FAVORITE_APPS, config = mapOf("category" to "Spiele"))),
                    widgetItem(WidgetType.MUSIC, 0, 4, 4, 1),
                ),
            ),
        ),
        dockApps = listOf(RolePlaceholders.DIALER, RolePlaceholders.SMS, RolePlaceholders.BROWSER, RolePlaceholders.MUSIC),
    )

    fun minimalLayout(): HomeLayout = HomeLayout(
        columns = 4,
        rows = 6,
        pages = listOf(
            HomePage(
                newId("page"),
                listOf(
                    widgetItem(WidgetType.CLOCK, 0, 1, 4, 2, WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, layout = WidgetLayoutVariant.DETAILED)),
                    widgetItem(WidgetType.WEATHER, 1, 3, 2, 1, WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, layout = WidgetLayoutVariant.COMPACT)),
                ),
            ),
        ),
        dockApps = listOf(RolePlaceholders.DIALER, RolePlaceholders.SMS, RolePlaceholders.CAMERA),
    )

    fun mediaLayout(): HomeLayout = HomeLayout(
        pages = listOf(
            HomePage(
                newId("page"),
                listOf(
                    widgetItem(WidgetType.CLOCK, 0, 0, 4, 1, WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, layout = WidgetLayoutVariant.COMPACT)),
                    widgetItem(WidgetType.ALBUM_ART, 0, 1, 4, 3),
                    widgetItem(WidgetType.MUSIC, 0, 4, 4, 1),
                    widgetItem(WidgetType.MEDIA_LINKS, 0, 5, 4, 1),
                ),
            ),
        ),
        dockApps = listOf(RolePlaceholders.MUSIC, RolePlaceholders.GALLERY, RolePlaceholders.BROWSER, RolePlaceholders.CAMERA),
    )

    fun profileFor(kind: ProfileKind, name: String = kind.label): Profile {
        val id = newId("profile")
        return when (kind) {
            ProfileKind.STANDARD -> Profile(id, name, kind, ThemePresets.nova, ThemePresets.nova.wallpaper!!, standardLayout())
            ProfileKind.WORK -> Profile(id, name, kind, ThemePresets.minimalAndroid.copy(colors = ThemePresets.minimalAndroid.colors.copy(mode = ThemeMode.DARK)), ThemePresets.minimalAndroid.wallpaper!!, workLayout())
            ProfileKind.GAMING -> Profile(id, name, kind, ThemePresets.redAccent, ThemePresets.redAccent.wallpaper!!, gamingLayout())
            ProfileKind.MINIMAL -> Profile(id, name, kind, ThemePresets.nothingDots, ThemePresets.nothingDots.wallpaper!!, minimalLayout())
            ProfileKind.MEDIA -> Profile(id, name, kind, ThemePresets.darkPremium, ThemePresets.darkPremium.wallpaper!!, mediaLayout())
            ProfileKind.CUSTOM -> Profile(id, name, kind, ThemePresets.nova, ThemePresets.nova.wallpaper!!, HomeLayout(dockApps = listOf(RolePlaceholders.DIALER, RolePlaceholders.SMS, RolePlaceholders.BROWSER, RolePlaceholders.CAMERA)))
        }
    }

    fun config(): NovaConfig {
        val standard = profileFor(ProfileKind.STANDARD)
        val profiles = listOf(
            standard,
            profileFor(ProfileKind.WORK),
            profileFor(ProfileKind.GAMING),
            profileFor(ProfileKind.MINIMAL),
            profileFor(ProfileKind.MEDIA),
        )
        return NovaConfig(profiles = profiles, manualProfileId = standard.id, activeProfileId = standard.id)
    }

    fun widgetTemplates(): List<WidgetTemplate> {
        fun t(name: String, cat: TemplateCategory, type: WidgetType, style: WidgetStyle, w: Int = type.defaultW, h: Int = type.defaultH) =
            WidgetTemplate("tpl-${cat.name.lowercase()}-${type.name.lowercase()}-${name.hashCode().toUInt()}", name, cat, WidgetInstance("tpl", type, style), w, h, builtIn = true)
        val glass = WidgetStyle(background = SurfaceBackground.GLASS, transparency = 0.6f, borderWidth = 1f, shadow = 0.4f, dotMatrix = false)
        val dots = WidgetStyle(background = SurfaceBackground.DOTS, transparency = 0.1f, dotMatrix = true, font = FontChoice.SPACE_MONO)
        val minimal = WidgetStyle(background = SurfaceBackground.NONE, borderWidth = 0f, shadow = 0f, dotMatrix = false, layout = WidgetLayoutVariant.COMPACT)
        val mono = WidgetStyle(background = SurfaceBackground.SOLID, backgroundColor = "#111111", textColor = "#FFFFFF", accentColor = "#FFFFFF", transparency = 0f, dotMatrix = true)
        val android = WidgetStyle(background = SurfaceBackground.SOLID, cornerDp = 28f, transparency = 0.05f, borderWidth = 0f, dotMatrix = false, font = FontChoice.SYSTEM_SANS)
        val premium = WidgetStyle(background = SurfaceBackground.GRADIENT, backgroundColor = "#1D1913", backgroundColor2 = "#050505", accentColor = "#C9A55C", borderColor = "#55C9A55C", dotMatrix = false, font = FontChoice.SPACE_GROTESK)
        val exp = WidgetStyle(shape = WidgetShape.CUT, background = SurfaceBackground.DOTS, accentColor = "#C6FF3D", borderWidth = 2f, borderColor = "#C6FF3D", font = FontChoice.JETBRAINS_MONO)
        val prod = WidgetStyle(layout = WidgetLayoutVariant.DETAILED, density = InfoDensity.HIGH, dotMatrix = false)
        return listOf(
            t("Klare Uhr", TemplateCategory.MINIMAL, WidgetType.CLOCK, minimal, 4, 2),
            t("Wetter schlicht", TemplateCategory.MINIMAL, WidgetType.WEATHER, minimal, 2, 1),
            t("Punkt-Uhr", TemplateCategory.NOTHING_DOTS, WidgetType.CLOCK, dots, 4, 2),
            t("Punkt-Akku", TemplateCategory.NOTHING_DOTS, WidgetType.BATTERY, dots),
            t("Punkt-Kalender", TemplateCategory.NOTHING_DOTS, WidgetType.CALENDAR, dots),
            t("Glas-Wetter", TemplateCategory.LIQUID_GLASS, WidgetType.WEATHER, glass),
            t("Glas-Musik", TemplateCategory.LIQUID_GLASS, WidgetType.MUSIC, glass, 4, 1),
            t("Agenda", TemplateCategory.PRODUCTIVITY, WidgetType.AGENDA, prod, 4, 2),
            t("Aufgabenliste", TemplateCategory.PRODUCTIVITY, WidgetType.TASKS, prod, 2, 2),
            t("Projektfortschritt", TemplateCategory.PRODUCTIVITY, WidgetType.PROJECTS, prod, 4, 1),
            t("Mono Status", TemplateCategory.MONOCHROME, WidgetType.SYSTEM_STATUS, mono, 4, 1),
            t("Mono Speicher", TemplateCategory.MONOCHROME, WidgetType.STORAGE, mono),
            t("Material Uhr", TemplateCategory.MODERN_ANDROID, WidgetType.CLOCK, android, 4, 2),
            t("Material Akku", TemplateCategory.MODERN_ANDROID, WidgetType.BATTERY, android),
            t("Gold-Uhr", TemplateCategory.PREMIUM, WidgetType.CLOCK, premium, 4, 2),
            t("Premium Album", TemplateCategory.PREMIUM, WidgetType.ALBUM_ART, premium, 2, 2),
            t("Hex-Status", TemplateCategory.EXPERIMENTAL, WidgetType.SYSTEM_STATUS, exp, 4, 1),
            t("Tagesfortschritt", TemplateCategory.EXPERIMENTAL, WidgetType.PROGRESS, exp, 2, 1),
        )
    }
}
