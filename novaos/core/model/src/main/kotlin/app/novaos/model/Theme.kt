package app.novaos.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
enum class DotIntensity { OFF, ACCENT, BALANCED, STRONG }

@Serializable
data class ColorPalette(
    val primary: String,
    val secondary: String,
    val accent: String,
    val background: String,
    val surface: String,
    val text: String,
    val textSecondary: String,
    val icon: String,
    val widget: String,
    val border: String,
    val shadow: String,
    val gradientStart: String,
    val gradientEnd: String,
    val useGradient: Boolean = false,
    /** 0.5 = wenig, 1.0 = normal, 1.5 = hoher Kontrast. */
    val contrast: Float = 1f,
    /** Deckkraft von Oberflächen (Karten, Widgets) 0..1. */
    val surfaceOpacity: Float = 0.72f,
)

@Serializable
data class ThemeColors(
    val dark: ColorPalette,
    val light: ColorPalette,
    val mode: ThemeMode = ThemeMode.DARK,
    /** Akzentfarbe aus dem Hintergrundbild ableiten. */
    val dynamicFromWallpaper: Boolean = false,
    /** Eigene gespeicherte Paletten-Farben für die Farbauswahl. */
    val savedSwatches: List<String> = emptyList(),
)

@Serializable
enum class FontChoice(val label: String, val licensed: String) {
    SPACE_GROTESK("Space Grotesk", "OFL"),
    INTER("Inter", "OFL"),
    SPACE_MONO("Space Mono", "OFL"),
    JETBRAINS_MONO("JetBrains Mono", "OFL"),
    DOTO("Doto (Punkt)", "OFL"),
    NOVA_DOT("Nova Dot Matrix", "NovaOS"),
    SYSTEM_SANS("System Sans", "System"),
    SYSTEM_SERIF("System Serif", "System"),
    SYSTEM_MONO("System Mono", "System"),
}

@Serializable
data class TextStyleSpec(
    val font: FontChoice = FontChoice.SPACE_GROTESK,
    val sizeSp: Float = 14f,
    val weight: Int = 400,
    /** Buchstabenabstand in em. */
    val letterSpacing: Float = 0f,
    /** Zeilenhöhe als Vielfaches der Schriftgröße. */
    val lineHeight: Float = 1.25f,
    /** null = Textfarbe des Themes. */
    val color: String? = null,
    val alpha: Float = 1f,
)

@Serializable
data class TypographySpec(
    val clock: TextStyleSpec = TextStyleSpec(FontChoice.NOVA_DOT, 64f, 700),
    val headline: TextStyleSpec = TextStyleSpec(FontChoice.DOTO, 22f, 700, 0.04f),
    val body: TextStyleSpec = TextStyleSpec(FontChoice.SPACE_GROTESK, 14f, 400),
    val widget: TextStyleSpec = TextStyleSpec(FontChoice.SPACE_GROTESK, 13f, 500),
    val appLabel: TextStyleSpec = TextStyleSpec(FontChoice.SPACE_GROTESK, 11.5f, 500),
    /** Schrift für Punktmatrix-Elemente (Zahlen, Überschriften). */
    val dotFont: FontChoice = FontChoice.NOVA_DOT,
    val dotIntensity: DotIntensity = DotIntensity.STRONG,
    /** App-Namen in Punktmatrix (nur bei STRONG sinnvoll). */
    val dotAppLabels: Boolean = false,
)

@Serializable
data class GlassSpec(
    val enabled: Boolean = true,
    /** Durchsichtigkeit 0 (deckend) .. 1 (unsichtbar). */
    val transparency: Float = 0.45f,
    /** Unschärfe des Hintergrunds in dp. */
    val blurDp: Float = 18f,
    val reflection: Float = 0.25f,
    val depth: Float = 0.3f,
    val border: Float = 0.35f,
    val shadow: Float = 0.3f,
    val gloss: Float = 0.2f,
    /** Materialstärke = wie stark die Oberflächenfarbe das Glas einfärbt. */
    val thickness: Float = 0.5f,
    val gradient: Float = 0.3f,
    /** Abdunklung des Hintergrunds hinter Overlays. */
    val dim: Float = 0.35f,
)

@Serializable
enum class AnimationStyle(val label: String) {
    MINIMAL("Minimal"),
    SMOOTH("Smooth"),
    LIQUID("Liquid"),
    ELASTIC("Elastic"),
    NOTHING_DOTS("Nothing Dots"),
    FLUID("Fluid"),
    CLASSIC_ANDROID("Classic Android"),
}

@Serializable
enum class AnimArea(val label: String) {
    OPEN("Öffnen"),
    CLOSE("Schließen"),
    APP_LAUNCH("App-Start"),
    WIDGET("Widgets"),
    FOLDER("Ordner"),
    PAGE("Seitenübergang"),
    CONTROL_CENTER("Kontrollzentrum"),
    NOTIFICATIONS("Benachrichtigungen"),
    TOUCH("Berührung"),
    BACKGROUND("Hintergrund"),
}

@Serializable
data class AnimationSpec(
    val style: AnimationStyle = AnimationStyle.SMOOTH,
    /** Geschwindigkeit: 0.25 (langsam) .. 3 (schnell). */
    val speed: Float = 1f,
    /** Grunddauer eines Übergangs in ms (vor Geschwindigkeitsfaktor). */
    val durationMs: Int = 320,
    /** Optionaler Stil pro Bereich, sonst [style]. */
    val overrides: Map<AnimArea, AnimationStyle> = emptyMap(),
    val touchFeedback: Boolean = true,
    /** Dezente Hintergrundanimation (Standard aus, um Akku zu schonen). */
    val backgroundAnimation: Boolean = false,
) {
    fun styleFor(area: AnimArea): AnimationStyle = overrides[area] ?: style
    fun scaledDuration(): Int = (durationMs / speed.coerceIn(0.25f, 3f)).toInt().coerceIn(40, 2000)
}

@Serializable
enum class IconShape(val label: String) {
    CIRCLE("Kreis"),
    SQUARE("Quadrat"),
    ROUNDED_SQUARE("Rounded Square"),
    SQUIRCLE("Squircle"),
    HEXAGON("Sechseck"),
    DIAMOND("Raute"),
    OCTAGON("Achteck"),
    DOT_RING("Punktring"),
    NONE("Freie Form"),
}

@Serializable
enum class IconRendering(val label: String) { ORIGINAL("Original farbig"), MONOCHROME("Monochrom"), DOTS("Punkte") }

@Serializable
data class IconStyleSpec(
    val shape: IconShape = IconShape.SQUIRCLE,
    val rendering: IconRendering = IconRendering.ORIGINAL,
    /** Größe des Symbols innerhalb der Form 0.5..1.2. */
    val glyphScale: Float = 1f,
    /** null = Hintergrund des App-Icons. */
    val background: String? = null,
    /** Symbolfarbe für Monochrom/Punkte. null = Icon-Farbe des Themes. */
    val foreground: String? = null,
    val strokeWidth: Float = 0f,
    val strokeColor: String? = null,
    val shadow: Float = 0f,
    val alpha: Float = 1f,
    /** Punktraster für die Punkt-Darstellung. */
    val dotGrid: Int = 18,
)

@Serializable
data class IconOverride(
    /** Pfad relativ zum App-Speicher (eigenes oder importiertes Icon). */
    val customIconFile: String? = null,
    val style: IconStyleSpec? = null,
    val label: String? = null,
)

@Serializable
data class IconPack(
    val id: String,
    val name: String,
    val style: IconStyleSpec,
    val overrides: Map<String, IconOverride> = emptyMap(),
)

@Serializable
enum class DockPosition(val label: String) { BOTTOM("Unten"), TOP("Oben"), HIDDEN("Ausgeblendet") }

@Serializable
enum class DockShape(val label: String) { PILL("Pille"), ROUNDED("Abgerundet"), SQUARE("Eckig"), DOTS("Punktrahmen"), NONE("Ohne Form") }

@Serializable
enum class SurfaceBackground(val label: String) { GLASS("Glas"), SOLID("Fläche"), GRADIENT("Verlauf"), DOTS("Punkte"), NONE("Keiner") }

@Serializable
data class DockSpec(
    val position: DockPosition = DockPosition.BOTTOM,
    val shape: DockShape = DockShape.PILL,
    val background: SurfaceBackground = SurfaceBackground.GLASS,
    val heightDp: Float = 76f,
    val transparency: Float = 0.4f,
    val showLabels: Boolean = false,
    val maxApps: Int = 4,
)

@Serializable
enum class DrawerStyle(val label: String) { VERTICAL_GRID("Vertikales Raster"), PAGED("Seiten"), LIST("Liste") }

@Serializable
enum class SortMode(val label: String) {
    ALPHABETICAL("A–Z"),
    ALPHABETICAL_DESC("Z–A"),
    MOST_USED("Meistgenutzt"),
    RECENTLY_INSTALLED("Zuletzt installiert"),
    CUSTOM("Eigene Reihenfolge"),
}

@Serializable
enum class SearchBarStyle(val label: String) { PILL("Pille"), GLASS("Glas"), UNDERLINE("Linie"), DOT_FRAME("Punktrahmen") }

@Serializable
enum class ScrollBehavior(val label: String) { SMOOTH("Weich"), SNAP("Einrasten"), BOUNCE("Federnd") }

@Serializable
data class DrawerSpec(
    val style: DrawerStyle = DrawerStyle.VERTICAL_GRID,
    val columns: Int = 4,
    val iconSizeDp: Float = 54f,
    val iconSpacingDp: Float = 18f,
    val background: SurfaceBackground = SurfaceBackground.GLASS,
    val transparency: Float = 0.25f,
    val searchBar: SearchBarStyle = SearchBarStyle.GLASS,
    val searchAutoFocus: Boolean = false,
    val showAlphabetIndex: Boolean = true,
    val showCategories: Boolean = true,
    val sortMode: SortMode = SortMode.ALPHABETICAL,
    val scroll: ScrollBehavior = ScrollBehavior.SMOOTH,
    val showLabels: Boolean = true,
)

@Serializable
enum class CcLayout(val label: String) {
    COMPACT("Compact"),
    NOTHING_GRID("Nothing Grid"),
    GLASS_PANEL("Glass Panel"),
    ANDROID_CLASSIC("Android Classic"),
    CUSTOM("Custom Layout"),
}

@Serializable
data class ControlCenterStyle(
    val layout: CcLayout = CcLayout.NOTHING_GRID,
    val columns: Int = 4,
    /** null = Akzentfarbe des Themes. */
    val activeColor: String? = null,
    val transparency: Float = 0.3f,
    /** Rundung der Kacheln 0 (eckig) .. 1 (rund). */
    val tileRoundness: Float = 1f,
    val background: SurfaceBackground = SurfaceBackground.GLASS,
)

@Serializable
enum class GroupingStyle(val label: String) { BY_APP("Nach App"), BY_TIME("Nach Zeit"), FLAT("Ungruppiert"), PRIORITY("Nach Wichtigkeit") }

@Serializable
data class NotificationSpec(
    val cardCornerDp: Float = 22f,
    val cardSpacingDp: Float = 10f,
    val background: SurfaceBackground = SurfaceBackground.GLASS,
    val fontScale: Float = 1f,
    val iconSizeDp: Float = 36f,
    val transparency: Float = 0.35f,
    val animation: AnimationStyle = AnimationStyle.SMOOTH,
    val grouping: GroupingStyle = GroupingStyle.BY_APP,
    val showPreviews: Boolean = true,
    val newestFirst: Boolean = true,
)

@Serializable
enum class PageTransition(val label: String) {
    SLIDE("Schieben"),
    FADE("Überblenden"),
    SCALE("Zoomen"),
    DEPTH("Tiefe"),
    CUBE("Würfel"),
    DOTS("Punkte"),
}

@Serializable
enum class IndicatorStyle(val label: String) { DOTS("Punkte"), LINE("Linie"), DOT_NUMBERS("Punktzahlen"), NONE("Keiner") }

@Serializable
enum class BackgroundEffect(val label: String) { NONE("Keiner"), DOT_GRID("Punktraster"), VIGNETTE("Vignette"), SCANLINES("Linien") }

@Serializable
enum class FolderPreview(val label: String) { GRID_2("2 × 2"), GRID_3("3 × 3"), STACK("Stapel"), DOT("Punkt") }

@Serializable
data class FolderStyle(
    val shape: IconShape = IconShape.SQUIRCLE,
    val preview: FolderPreview = FolderPreview.GRID_2,
    val background: SurfaceBackground = SurfaceBackground.GLASS,
    val openAnimation: AnimationStyle = AnimationStyle.SMOOTH,
    val columns: Int = 4,
)

@Serializable
data class HomeStyle(
    val pageTransition: PageTransition = PageTransition.SLIDE,
    val indicator: IndicatorStyle = IndicatorStyle.DOTS,
    val backgroundEffect: BackgroundEffect = BackgroundEffect.DOT_GRID,
    val folder: FolderStyle = FolderStyle(),
    /** NovaOS-Statuszeile (Datum, Akku) oberhalb des Homescreens. */
    val showStatusLine: Boolean = true,
    val statusShowBattery: Boolean = true,
    val statusShowDate: Boolean = true,
    val scroll: ScrollBehavior = ScrollBehavior.SMOOTH,
    /** Endloses Blättern über die Seiten. */
    val loopPages: Boolean = false,
)

@Serializable
enum class GeneratedWallpaper(val label: String) {
    DOT_GRID("Punktraster"),
    DOT_WAVE("Punktwelle"),
    CHROME_ORBS("Chrom-Kugeln"),
    GEOMETRIC("Geometrie"),
    CONCENTRIC("Kreise"),
    GRADIENT("Verlauf"),
    SPLIT("Teilung"),
}

@Serializable
sealed interface WallpaperSource {
    @Serializable @SerialName("system")
    data object System : WallpaperSource

    @Serializable @SerialName("image")
    data class Image(val file: String) : WallpaperSource

    @Serializable @SerialName("generated")
    data class Generated(
        val pattern: GeneratedWallpaper,
        val colorA: String,
        val colorB: String,
        val accent: String,
        val seed: Int = 7,
    ) : WallpaperSource

    @Serializable @SerialName("solid")
    data class Solid(val color: String) : WallpaperSource
}

@Serializable
data class WallpaperSpec(
    val source: WallpaperSource = WallpaperSource.Generated(GeneratedWallpaper.CHROME_ORBS, "#000000", "#2A2A2E", "#E5202E"),
    /** Zoom 1..3 (Zuschneiden). */
    val zoom: Float = 1f,
    /** Bildausschnitt -1..1. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val blur: Float = 0f,
    /** -1..1 */
    val brightness: Float = 0f,
    /** 0.5..1.5 */
    val contrast: Float = 1f,
    val overlayColor: String = "#000000",
    val overlayAlpha: Float = 0f,
    /** Abweichende Hintergründe pro Homescreen-Seite (Seitenindex -> Quelle). */
    val perPage: Map<Int, WallpaperSource> = emptyMap(),
)

@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val description: String = "",
    val builtIn: Boolean = false,
    val colors: ThemeColors,
    val typography: TypographySpec = TypographySpec(),
    val glass: GlassSpec = GlassSpec(),
    val animation: AnimationSpec = AnimationSpec(),
    val icons: IconStyleSpec = IconStyleSpec(),
    val dock: DockSpec = DockSpec(),
    val drawer: DrawerSpec = DrawerSpec(),
    val controlCenter: ControlCenterStyle = ControlCenterStyle(),
    val notifications: NotificationSpec = NotificationSpec(),
    val home: HomeStyle = HomeStyle(),
    /** null = Hintergrund des Profils bleibt beim Anwenden erhalten. */
    val wallpaper: WallpaperSpec? = null,
)
