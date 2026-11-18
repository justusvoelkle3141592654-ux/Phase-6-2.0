package app.novaos.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class GestureType(val label: String, val hint: String) {
    SWIPE_UP("Wischen nach oben", ""),
    SWIPE_DOWN("Wischen nach unten", ""),
    SWIPE_LEFT("Wischen nach links", "Auf der letzten Seite (sonst Seitenwechsel)"),
    SWIPE_RIGHT("Wischen nach rechts", "Auf der ersten Seite (sonst Seitenwechsel)"),
    DOUBLE_TAP("Doppeltippen", ""),
    LONG_PRESS("Langes Drücken", "Auf freier Fläche"),
    TWO_FINGER_UP("Zwei Finger nach oben", ""),
    TWO_FINGER_DOWN("Zwei Finger nach unten", ""),
    PINCH_IN("Zwei Finger zusammenziehen", ""),
}

@Serializable
sealed interface GestureAction {
    @Serializable @SerialName("none") data object None : GestureAction
    @Serializable @SerialName("control_center") data object OpenControlCenter : GestureAction
    @Serializable @SerialName("notifications") data object OpenNotifications : GestureAction
    @Serializable @SerialName("search") data object OpenSearch : GestureAction
    @Serializable @SerialName("drawer") data object OpenDrawer : GestureAction
    @Serializable @SerialName("lock") data object LockScreen : GestureAction
    @Serializable @SerialName("quick_menu") data object OpenQuickMenu : GestureAction
    @Serializable @SerialName("edit_home") data object EditHome : GestureAction
    @Serializable @SerialName("settings") data object OpenSettings : GestureAction
    @Serializable @SerialName("next_profile") data object NextProfile : GestureAction
    @Serializable @SerialName("app") data class LaunchApp(val appKey: String) : GestureAction
    @Serializable @SerialName("widget") data class OpenWidget(val widgetItemId: String) : GestureAction
}

fun GestureAction.label(): String = when (this) {
    GestureAction.None -> "Keine Aktion"
    GestureAction.OpenControlCenter -> "Kontrollzentrum öffnen"
    GestureAction.OpenNotifications -> "Benachrichtigungen öffnen"
    GestureAction.OpenSearch -> "App-Suche öffnen"
    GestureAction.OpenDrawer -> "App Drawer öffnen"
    GestureAction.LockScreen -> "Bildschirm sperren"
    GestureAction.OpenQuickMenu -> "Schnellmenü öffnen"
    GestureAction.EditHome -> "Homescreen bearbeiten"
    GestureAction.OpenSettings -> "NovaOS Einstellungen"
    GestureAction.NextProfile -> "Nächstes Profil"
    is GestureAction.LaunchApp -> "App starten"
    is GestureAction.OpenWidget -> "Widget öffnen"
}

val SimpleGestureActions: List<GestureAction> = listOf(
    GestureAction.None,
    GestureAction.OpenControlCenter,
    GestureAction.OpenNotifications,
    GestureAction.OpenSearch,
    GestureAction.OpenDrawer,
    GestureAction.LockScreen,
    GestureAction.OpenQuickMenu,
    GestureAction.EditHome,
    GestureAction.OpenSettings,
    GestureAction.NextProfile,
)

fun defaultGestures(): Map<GestureType, GestureAction> = mapOf(
    GestureType.SWIPE_UP to GestureAction.OpenDrawer,
    GestureType.SWIPE_DOWN to GestureAction.OpenControlCenter,
    GestureType.SWIPE_RIGHT to GestureAction.OpenNotifications,
    GestureType.SWIPE_LEFT to GestureAction.OpenSearch,
    GestureType.DOUBLE_TAP to GestureAction.LockScreen,
    GestureType.LONG_PRESS to GestureAction.OpenQuickMenu,
    GestureType.TWO_FINGER_DOWN to GestureAction.OpenNotifications,
    GestureType.TWO_FINGER_UP to GestureAction.OpenSearch,
    GestureType.PINCH_IN to GestureAction.EditHome,
)

/**
 * Erkennt eine Wischgeste aus der Gesamtbewegung.
 * Bewegungen, die in einem System-Gestenbereich beginnen, werden vom Aufrufer vorher verworfen.
 */
object GestureClassifier {
    /** Mindeststrecke in px, ab der eine Bewegung als Wischen gilt. */
    fun classifySwipe(dx: Float, dy: Float, pointers: Int, minDistancePx: Float): GestureType? {
        val ax = abs(dx)
        val ay = abs(dy)
        if (ax < minDistancePx && ay < minDistancePx) return null
        val vertical = ay > ax * 1.3f
        val horizontal = ax > ay * 1.3f
        return when {
            pointers >= 2 && vertical -> if (dy < 0) GestureType.TWO_FINGER_UP else GestureType.TWO_FINGER_DOWN
            pointers >= 2 -> null
            vertical -> if (dy < 0) GestureType.SWIPE_UP else GestureType.SWIPE_DOWN
            horizontal -> if (dx < 0) GestureType.SWIPE_LEFT else GestureType.SWIPE_RIGHT
            else -> null
        }
    }

    /** Hinweise auf Konflikte mit Android-Systemgesten. */
    fun conflicts(type: GestureType, gestureNavigation: Boolean): String? = when {
        gestureNavigation && type == GestureType.SWIPE_UP ->
            "Wische nicht vom unteren Bildschirmrand: dort liegt die Android-Geste für Startbildschirm und Übersicht."
        gestureNavigation && (type == GestureType.SWIPE_LEFT || type == GestureType.SWIPE_RIGHT) ->
            "Wische nicht vom seitlichen Rand: dort liegt die Android-Zurück-Geste."
        type == GestureType.SWIPE_DOWN ->
            "Wische nicht vom oberen Rand: dort öffnet Android die eigene Benachrichtigungsleiste."
        else -> null
    }
}
