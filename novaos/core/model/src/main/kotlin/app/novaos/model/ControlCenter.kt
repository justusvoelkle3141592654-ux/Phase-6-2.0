package app.novaos.model

import kotlinx.serialization.Serializable

/**
 * Kachelarten des Nova Control Center.
 * [direct] = NovaOS kann die Funktion selbst schalten (ggf. nach Freigabe [permission]).
 * Sonst öffnet die Kachel das offizielle Android-Panel bzw. die Einstellungsseite.
 */
@Serializable
enum class CcTileType(val label: String, val direct: Boolean, val permission: CcPermission = CcPermission.NONE) {
    WIFI("WLAN", false),
    BLUETOOTH("Bluetooth", false),
    AIRPLANE("Flugmodus", false),
    INTERNET("Internet", false),
    NFC("NFC", false),
    LOCATION("Standort", false),
    HOTSPOT("Hotspot", false),
    FLASHLIGHT("Taschenlampe", true),
    DND("Nicht stören", true, CcPermission.DND_ACCESS),
    ROTATION("Autom. drehen", true, CcPermission.WRITE_SETTINGS),
    BRIGHTNESS("Helligkeit", true, CcPermission.WRITE_SETTINGS),
    VOLUME("Lautstärke", true),
    MEDIA("Medien", true, CcPermission.NOTIFICATION_ACCESS),
    BATTERY("Akku", true),
    SETTINGS("Einstellungen", true),
    NOVA_SETTINGS("NovaOS", true),
    LOCK("Sperren", true, CcPermission.DEVICE_ADMIN),
    PROFILE("Profil", true),
    SHORTCUT("Schnellzugriff", true),
}

@Serializable
enum class CcPermission(val label: String) {
    NONE("Keine"),
    WRITE_SETTINGS("Systemeinstellungen ändern"),
    DND_ACCESS("Zugriff auf „Nicht stören“"),
    NOTIFICATION_ACCESS("Benachrichtigungszugriff"),
    DEVICE_ADMIN("Geräteadministrator (Sperren)"),
}

@Serializable
data class CcTile(
    val id: String,
    val type: CcTileType,
    /** Breite in Spalten (1 oder 2). */
    val span: Int = 1,
    /** Höhe in Zeilen (1 oder 2). */
    val height: Int = 1,
    val target: ShortcutTarget? = null,
    val label: String? = null,
)

fun defaultCcTiles(): List<CcTile> = listOf(
    CcTile("cc-wifi", CcTileType.WIFI, span = 2),
    CcTile("cc-bt", CcTileType.BLUETOOTH, span = 2),
    CcTile("cc-air", CcTileType.AIRPLANE),
    CcTile("cc-flash", CcTileType.FLASHLIGHT),
    CcTile("cc-dnd", CcTileType.DND),
    CcTile("cc-rot", CcTileType.ROTATION),
    CcTile("cc-bright", CcTileType.BRIGHTNESS, span = 4),
    CcTile("cc-vol", CcTileType.VOLUME, span = 4),
    CcTile("cc-media", CcTileType.MEDIA, span = 4, height = 2),
    CcTile("cc-batt", CcTileType.BATTERY),
    CcTile("cc-lock", CcTileType.LOCK),
    CcTile("cc-nova", CcTileType.NOVA_SETTINGS),
    CcTile("cc-settings", CcTileType.SETTINGS),
)

/** Ordnet Kacheln zeilenweise in einem Raster mit [columns] Spalten an. Liefert (Kachel, Spalte, Zeile). */
object CcGrid {
    data class Placed(val tile: CcTile, val col: Int, val row: Int, val span: Int, val height: Int)

    fun arrange(tiles: List<CcTile>, columns: Int): List<Placed> {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val out = mutableListOf<Placed>()
        tiles.forEach { t ->
            val span = t.span.coerceIn(1, columns)
            val h = t.height.coerceIn(1, 2)
            var row = 0
            while (true) {
                var placed = false
                for (col in 0..columns - span) {
                    val cells = (col until col + span).flatMap { c -> (row until row + h).map { r -> c to r } }
                    if (cells.none { it in occupied }) {
                        occupied += cells
                        out += Placed(t, col, row, span, h)
                        placed = true
                        break
                    }
                }
                if (placed) break
                row++
            }
        }
        return out
    }

    fun rowCount(placed: List<Placed>): Int = placed.maxOfOrNull { it.row + it.height } ?: 0
}
