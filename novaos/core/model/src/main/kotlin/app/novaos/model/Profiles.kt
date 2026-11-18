package app.novaos.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProfileKind(val label: String) { STANDARD("Standard"), WORK("Work"), GAMING("Gaming"), MINIMAL("Minimal"), MEDIA("Media"), CUSTOM("Custom") }

@Serializable
sealed interface RuleCondition {
    /** Wochentage 1 = Montag … 7 = Sonntag; Minuten seit Mitternacht. end < start = über Mitternacht. */
    @Serializable @SerialName("time")
    data class TimeWindow(val days: Set<Int>, val startMinute: Int, val endMinute: Int) : RuleCondition

    /** deviceName = null: beliebiges verbundenes Bluetooth-Gerät. */
    @Serializable @SerialName("bluetooth")
    data class BluetoothDevice(val deviceName: String? = null) : RuleCondition

    @Serializable @SerialName("wifi")
    data class WifiNetwork(val ssid: String) : RuleCondition
}

fun RuleCondition.describe(): String = when (this) {
    is RuleCondition.TimeWindow -> {
        val names = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
        val d = days.sorted().joinToString(", ") { names[(it - 1).coerceIn(0, 6)] }
        "%s %02d:%02d–%02d:%02d".format(d, startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60)
    }
    is RuleCondition.BluetoothDevice -> "Bluetooth: ${deviceName ?: "beliebiges Gerät"}"
    is RuleCondition.WifiNetwork -> "WLAN: $ssid"
}

@Serializable
data class AutoRule(
    val id: String,
    val profileId: String,
    val condition: RuleCondition,
    val enabled: Boolean = true,
    /** Höhere Zahl gewinnt, wenn mehrere Regeln passen. */
    val priority: Int = 0,
)

/** Momentaufnahme des Gerätezustands für die Regelauswertung. */
data class RuleContext(
    val dayOfWeek: Int,
    val minuteOfDay: Int,
    val bluetoothDevices: Set<String> = emptySet(),
    val wifiSsid: String? = null,
)

object RuleEngine {
    fun matches(c: RuleCondition, ctx: RuleContext): Boolean = when (c) {
        is RuleCondition.TimeWindow -> {
            val inRange = if (c.startMinute <= c.endMinute) ctx.minuteOfDay in c.startMinute until c.endMinute
            else ctx.minuteOfDay >= c.startMinute || ctx.minuteOfDay < c.endMinute
            // Über Mitternacht zählt der Morgen zum Starttag des Vortags.
            val day = if (c.startMinute > c.endMinute && ctx.minuteOfDay < c.endMinute) ((ctx.dayOfWeek + 5) % 7) + 1 else ctx.dayOfWeek
            inRange && (c.days.isEmpty() || day in c.days)
        }
        is RuleCondition.BluetoothDevice ->
            if (c.deviceName.isNullOrBlank()) ctx.bluetoothDevices.isNotEmpty()
            else ctx.bluetoothDevices.any { it.equals(c.deviceName, ignoreCase = true) }
        is RuleCondition.WifiNetwork -> ctx.wifiSsid != null && ctx.wifiSsid.equals(c.ssid.trim(), ignoreCase = false)
    }

    /** Spezifität: konkrete Geräte/Netze vor Zeitfenstern. */
    private fun specificity(c: RuleCondition) = when (c) {
        is RuleCondition.WifiNetwork -> 3
        is RuleCondition.BluetoothDevice -> if (c.deviceName.isNullOrBlank()) 1 else 3
        is RuleCondition.TimeWindow -> 2
    }

    /** Liefert die Profil-ID der besten passenden Regel oder null. */
    fun select(rules: List<AutoRule>, validProfiles: Set<String>, ctx: RuleContext): String? =
        rules.filter { it.enabled && it.profileId in validProfiles && matches(it.condition, ctx) }
            .sortedWith(compareByDescending<AutoRule> { it.priority }.thenByDescending { specificity(it.condition) })
            .firstOrNull()?.profileId
}

@Serializable
data class DrawerFolder(val id: String, val name: String, val apps: List<String>)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind = ProfileKind.CUSTOM,
    val theme: ThemeSpec,
    val wallpaper: WallpaperSpec = WallpaperSpec(),
    val layout: HomeLayout = HomeLayout(),
    val controlCenterTiles: List<CcTile> = defaultCcTiles(),
    val gestures: Map<GestureType, GestureAction> = defaultGestures(),
    val iconOverrides: Map<String, IconOverride> = emptyMap(),
    val hiddenApps: Set<String> = emptySet(),
    val favorites: List<String> = emptyList(),
    val drawerFolders: List<DrawerFolder> = emptyList(),
    /** Eigene Kategorie je App (appKey -> Kategoriename). */
    val categoryOverrides: Map<String, String> = emptyMap(),
    val customOrder: List<String> = emptyList(),
)
