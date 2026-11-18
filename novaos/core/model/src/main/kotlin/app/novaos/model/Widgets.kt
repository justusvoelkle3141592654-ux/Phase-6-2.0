package app.novaos.model

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetCategory(val label: String) {
    SYSTEM("System"),
    COMMUNICATION("Kommunikation"),
    PRODUCTIVITY("Produktivität"),
    MEDIA("Medien"),
    APPS("Apps"),
    CUSTOM("Eigene"),
}

/**
 * Widget-Typen der Nova Widget Engine.
 * [needs] beschreibt die Android-Freigabe, die für echte Daten nötig ist.
 */
@Serializable
enum class WidgetType(
    val label: String,
    val category: WidgetCategory,
    val defaultW: Int,
    val defaultH: Int,
    val needs: WidgetRequirement = WidgetRequirement.NONE,
) {
    CLOCK("Uhr", WidgetCategory.SYSTEM, 4, 2),
    WEATHER("Wetter", WidgetCategory.SYSTEM, 2, 2, WidgetRequirement.NETWORK),
    CALENDAR("Kalender", WidgetCategory.SYSTEM, 2, 2, WidgetRequirement.CALENDAR),
    BATTERY("Akku", WidgetCategory.SYSTEM, 2, 2),
    STORAGE("Speicher", WidgetCategory.SYSTEM, 2, 1),
    RAM("Arbeitsspeicher", WidgetCategory.SYSTEM, 2, 1),
    SCREEN_TIME("Bildschirmzeit", WidgetCategory.SYSTEM, 2, 2, WidgetRequirement.USAGE_ACCESS),
    SYSTEM_STATUS("Systemstatus", WidgetCategory.SYSTEM, 4, 1),

    MESSAGES("Nachrichten", WidgetCategory.COMMUNICATION, 4, 2, WidgetRequirement.NOTIFICATION_ACCESS),
    FAVORITE_CONTACTS("Favoritenkontakte", WidgetCategory.COMMUNICATION, 4, 1, WidgetRequirement.CONTACTS),
    CALL_SHORTCUTS("Anrufe", WidgetCategory.COMMUNICATION, 2, 1, WidgetRequirement.CONTACTS),
    COMMUNICATION("Kommunikation", WidgetCategory.COMMUNICATION, 4, 2, WidgetRequirement.NOTIFICATION_ACCESS),

    TASKS("Aufgaben", WidgetCategory.PRODUCTIVITY, 2, 2),
    PROJECTS("Projekte", WidgetCategory.PRODUCTIVITY, 2, 1),
    NOTES("Notizen", WidgetCategory.PRODUCTIVITY, 2, 2),
    REMINDERS("Erinnerungen", WidgetCategory.PRODUCTIVITY, 2, 2),
    AGENDA("Kalenderübersicht", WidgetCategory.PRODUCTIVITY, 4, 2, WidgetRequirement.CALENDAR),
    PROGRESS("Fortschritt", WidgetCategory.PRODUCTIVITY, 2, 1),

    MUSIC("Musiksteuerung", WidgetCategory.MEDIA, 4, 1, WidgetRequirement.NOTIFICATION_ACCESS),
    ALBUM_ART("Albumcover", WidgetCategory.MEDIA, 2, 2, WidgetRequirement.NOTIFICATION_ACCESS),
    PLAYLIST("Warteschlange", WidgetCategory.MEDIA, 2, 2, WidgetRequirement.NOTIFICATION_ACCESS),
    MEDIA_LINKS("Medien-Apps", WidgetCategory.MEDIA, 4, 1),

    APP_SHORTCUTS("App-Schnellzugriffe", WidgetCategory.APPS, 2, 2, WidgetRequirement.DEFAULT_LAUNCHER),
    FAVORITE_APPS("Favoriten", WidgetCategory.APPS, 4, 1),
    APP_INFO("App-Informationen", WidgetCategory.APPS, 2, 1),
    APP_LINKS("Verknüpfungen", WidgetCategory.APPS, 4, 1),

    CUSTOM("Eigenes Widget", WidgetCategory.CUSTOM, 2, 2),
}

@Serializable
enum class WidgetRequirement(val label: String) {
    NONE("Keine Freigabe nötig"),
    NETWORK("Internet (Open-Meteo)"),
    CALENDAR("Kalenderzugriff"),
    CONTACTS("Kontaktzugriff"),
    NOTIFICATION_ACCESS("Benachrichtigungszugriff"),
    USAGE_ACCESS("Nutzungsdatenzugriff"),
    DEFAULT_LAUNCHER("NovaOS als Standard-Startbildschirm"),
}

@Serializable
enum class WidgetShape(val label: String) { ROUNDED("Abgerundet"), SQUARE("Eckig"), PILL("Pille"), CIRCLE("Kreis"), CUT("Abgeschrägt") }

@Serializable
enum class WidgetLayoutVariant(val label: String) { COMPACT("Kompakt"), STANDARD("Standard"), DETAILED("Detailliert") }

@Serializable
enum class InfoDensity(val label: String) { LOW("Wenig"), MEDIUM("Mittel"), HIGH("Viel") }

@Serializable
enum class WidgetAnimation(val label: String) { NONE("Keine"), FADE("Einblenden"), SCALE("Zoomen"), DOTS("Punkte"), SLIDE("Schieben") }

@Serializable
enum class WidgetTap(val label: String) { DEFAULT("Standard"), NONE("Nichts"), OPEN_TARGET("Ziel öffnen"), EDIT("Bearbeiten") }

@Serializable
data class WidgetStyle(
    val shape: WidgetShape = WidgetShape.ROUNDED,
    val cornerDp: Float = 26f,
    val background: SurfaceBackground = SurfaceBackground.GLASS,
    /** null = Widget-Farbe des Themes. */
    val backgroundColor: String? = null,
    val backgroundColor2: String? = null,
    val transparency: Float = 0.35f,
    val font: FontChoice? = null,
    val textScale: Float = 1f,
    val textColor: String? = null,
    val accentColor: String? = null,
    val borderWidth: Float = 1f,
    val borderColor: String? = null,
    val shadow: Float = 0.2f,
    val paddingDp: Float = 14f,
    val layout: WidgetLayoutVariant = WidgetLayoutVariant.STANDARD,
    val density: InfoDensity = InfoDensity.MEDIUM,
    val animation: WidgetAnimation = WidgetAnimation.FADE,
    val tap: WidgetTap = WidgetTap.DEFAULT,
    val tapTarget: ShortcutTarget? = null,
    val dotMatrix: Boolean = true,
)

@Serializable
data class WidgetInstance(
    val id: String,
    val type: WidgetType,
    val style: WidgetStyle = WidgetStyle(),
    /** Typspezifische Einstellungen, z. B. "app" für App-Widgets oder "customId". */
    val config: Map<String, String> = emptyMap(),
)

@Serializable
enum class ElementKind(val label: String) { TEXT("Text"), DOT_TEXT("Punkttext"), ICON("Symbol"), DATA("Infofeld"), PROGRESS("Fortschritt"), SHAPE("Fläche") }

@Serializable
enum class DataField(val label: String, val sample: String) {
    TIME("Uhrzeit", "16:42"),
    DATE("Datum", "Mo., 27. Okt."),
    WEEKDAY("Wochentag", "Montag"),
    BATTERY("Akku %", "78 %"),
    STORAGE_FREE("Freier Speicher", "84 GB frei"),
    RAM_FREE("Freier RAM", "3,1 GB frei"),
    WEATHER_TEMP("Temperatur", "12 °C"),
    WEATHER_DESC("Wetterlage", "Leicht bewölkt"),
    NEXT_EVENT("Nächster Termin", "10:00 Meeting"),
    OPEN_TASKS("Offene Aufgaben", "3 offen"),
    MEDIA_TITLE("Titel", "Better Days"),
    MEDIA_ARTIST("Interpret", "Künstler"),
    DAY_PROGRESS("Tagesfortschritt", "68 %"),
    YEAR_PROGRESS("Jahresfortschritt", "82 %"),
}

@Serializable
enum class ProgressSource(val label: String) { BATTERY("Akku"), STORAGE("Speicher belegt"), RAM("RAM belegt"), TASKS("Aufgaben erledigt"), DAY("Tag"), YEAR("Jahr"), MEDIA("Wiedergabe") }

@Serializable
enum class TextAlign(val label: String) { START("Links"), CENTER("Mitte"), END("Rechts") }

/** Ein Element des Widget Creators. Position und Größe als Anteile der Widgetfläche. */
@Serializable
data class WidgetElement(
    val id: String,
    val kind: ElementKind,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val text: String = "",
    val icon: String = "star",
    val data: DataField = DataField.TIME,
    val progress: ProgressSource = ProgressSource.BATTERY,
    val color: String? = null,
    val sizeSp: Float = 16f,
    val weight: Int = 500,
    val align: TextAlign = TextAlign.START,
    val cornerDp: Float = 12f,
    val alpha: Float = 1f,
    val action: ShortcutTarget? = null,
)

@Serializable
data class CustomWidgetDef(
    val id: String,
    val name: String,
    val w: Int = 2,
    val h: Int = 2,
    val style: WidgetStyle = WidgetStyle(),
    val elements: List<WidgetElement> = emptyList(),
)

@Serializable
enum class TemplateCategory(val label: String) {
    MINIMAL("Minimalistisch"),
    NOTHING_DOTS("Nothing Dots"),
    LIQUID_GLASS("Liquid Glass"),
    PRODUCTIVITY("Productivity"),
    MONOCHROME("Monochrome"),
    MODERN_ANDROID("Modern Android"),
    PREMIUM("Premium"),
    EXPERIMENTAL("Experimental"),
}

/** Gespeicherte Widget-Vorlage (eingebaut, eigene oder importiert). */
@Serializable
data class WidgetTemplate(
    val id: String,
    val name: String,
    val category: TemplateCategory,
    val widget: WidgetInstance,
    val w: Int,
    val h: Int,
    /** Bei CUSTOM: die zugehörige Creator-Definition (wird beim Import mit übernommen). */
    val custom: CustomWidgetDef? = null,
    val builtIn: Boolean = false,
)

/** Symbole, die im Widget Creator und Icon Studio verfügbar sind (Namen werden in der UI auf Vektorgrafiken abgebildet). */
val CreatorIcons: List<String> = listOf(
    "star", "heart", "bolt", "home", "work", "music", "phone", "message", "mail", "camera",
    "calendar", "clock", "alarm", "battery", "wifi", "bluetooth", "cloud", "sun", "moon", "fire",
    "map", "navigation", "game", "book", "code", "terminal", "settings", "search", "person", "group",
    "shopping", "coffee", "fitness", "flight", "car", "pets", "leaf", "lock", "photo", "video",
)
