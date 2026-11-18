package app.novaos.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Position eines Elements. Im Rastermodus gelten x/y/w/h (Zellen).
 * Im freien Layoutmodus gelten fx/fy/fw/fh als Anteile der Seitenfläche (0..1).
 */
@Serializable
data class ItemPos(
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
    val fx: Float = -1f,
    val fy: Float = -1f,
    val fw: Float = -1f,
    val fh: Float = -1f,
) {
    val hasFree: Boolean get() = fx >= 0f && fy >= 0f && fw > 0f && fh > 0f

    /** Freie Position, aus der Rasterposition abgeleitet, falls keine eigene gesetzt ist. */
    fun freeOrFromGrid(columns: Int, rows: Int): ItemPos =
        if (hasFree) this else copy(
            fx = x.toFloat() / columns,
            fy = y.toFloat() / rows,
            fw = w.toFloat() / columns,
            fh = h.toFloat() / rows,
        )
}

@Serializable
sealed interface ShortcutTarget {
    @Serializable @SerialName("app")
    data class App(val appKey: String) : ShortcutTarget

    @Serializable @SerialName("nova")
    data class Nova(val area: NovaArea) : ShortcutTarget

    @Serializable @SerialName("settings")
    data class SystemSettings(val action: String, val label: String) : ShortcutTarget

    @Serializable @SerialName("url")
    data class Url(val url: String) : ShortcutTarget

    /** App-Verknüpfung (LauncherApps-Shortcut) einer App. */
    @Serializable @SerialName("deep")
    data class DeepShortcut(val packageName: String, val shortcutId: String, val userSerial: Long = 0) : ShortcutTarget
}

@Serializable
enum class NovaArea(val label: String) {
    CONTROL_CENTER("Kontrollzentrum"),
    NOTIFICATIONS("Benachrichtigungen"),
    DRAWER("App Drawer"),
    SEARCH("Suche"),
    SETTINGS("NovaOS Einstellungen"),
    QUICK_MENU("Schnellmenü"),
    WALLPAPER("Wallpaper"),
    THEMES("Themes"),
    PROFILES("Profile"),
    WIDGETS("Widgets"),
}

@Serializable
sealed interface HomeItem {
    val id: String
    val pos: ItemPos

    @Serializable @SerialName("app")
    data class App(override val id: String, override val pos: ItemPos, val appKey: String) : HomeItem

    @Serializable @SerialName("widget")
    data class Widget(override val id: String, override val pos: ItemPos, val widget: WidgetInstance) : HomeItem

    @Serializable @SerialName("folder")
    data class Folder(override val id: String, override val pos: ItemPos, val name: String, val apps: List<String>) : HomeItem

    @Serializable @SerialName("shortcut")
    data class Shortcut(override val id: String, override val pos: ItemPos, val label: String, val target: ShortcutTarget) : HomeItem

    /** Android-App-Widget (AppWidgetHost). */
    @Serializable @SerialName("android_widget")
    data class AndroidWidget(override val id: String, override val pos: ItemPos, val appWidgetId: Int, val provider: String) : HomeItem
}

fun HomeItem.withPos(p: ItemPos): HomeItem = when (this) {
    is HomeItem.App -> copy(pos = p)
    is HomeItem.Widget -> copy(pos = p)
    is HomeItem.Folder -> copy(pos = p)
    is HomeItem.Shortcut -> copy(pos = p)
    is HomeItem.AndroidWidget -> copy(pos = p)
}

@Serializable
data class HomePage(val id: String, val items: List<HomeItem> = emptyList())

@Serializable
data class HomeLayout(
    val columns: Int = 4,
    val rows: Int = 6,
    val iconSizeDp: Float = 58f,
    val iconSpacingDp: Float = 8f,
    val widgetSpacingDp: Float = 10f,
    val marginHorizontalDp: Float = 18f,
    val marginVerticalDp: Float = 8f,
    val freeLayout: Boolean = false,
    val showLabels: Boolean = true,
    val pages: List<HomePage> = listOf(HomePage(newId("page"))),
    val defaultPage: Int = 0,
    val dockApps: List<String> = emptyList(),
)

/** Belegungslogik für das Homescreen-Raster. */
class GridEngine(private val columns: Int, private val rows: Int) {

    fun fits(pos: ItemPos): Boolean =
        pos.x >= 0 && pos.y >= 0 && pos.w >= 1 && pos.h >= 1 && pos.x + pos.w <= columns && pos.y + pos.h <= rows

    private fun overlaps(a: ItemPos, b: ItemPos): Boolean =
        a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

    fun canPlace(items: List<HomeItem>, pos: ItemPos, ignoreId: String? = null): Boolean =
        fits(pos) && items.none { it.id != ignoreId && overlaps(it.pos, pos) }

    /** Erste freie Position für ein Element der Größe w×h (zeilenweise von oben links). */
    fun findFree(items: List<HomeItem>, w: Int, h: Int): ItemPos? {
        for (y in 0..rows - h) for (x in 0..columns - w) {
            val p = ItemPos(x, y, w, h)
            if (canPlace(items, p)) return p
        }
        return null
    }

    /** Freie Position möglichst nahe an der Wunschposition. */
    fun findNearest(items: List<HomeItem>, wanted: ItemPos, ignoreId: String? = null): ItemPos? {
        val w = wanted.w.coerceIn(1, columns)
        val h = wanted.h.coerceIn(1, rows)
        var best: ItemPos? = null
        var bestDist = Int.MAX_VALUE
        for (y in 0..rows - h) for (x in 0..columns - w) {
            val p = wanted.copy(x = x, y = y, w = w, h = h)
            if (canPlace(items, p, ignoreId)) {
                val d = (x - wanted.x) * (x - wanted.x) + (y - wanted.y) * (y - wanted.y)
                if (d < bestDist) { bestDist = d; best = p }
            }
        }
        return best
    }

    /** Verschiebt ein Element. Ist das Ziel belegt, wird mit einem gleich großen Element getauscht. */
    fun move(items: List<HomeItem>, id: String, target: ItemPos): List<HomeItem>? {
        val item = items.firstOrNull { it.id == id } ?: return null
        val dest = item.pos.copy(x = target.x, y = target.y)
        if (!fits(dest)) return null
        if (canPlace(items, dest, id)) return items.map { if (it.id == id) it.withPos(dest) else it }
        val blocker = items.filter { it.id != id && overlaps(it.pos, dest) }
        if (blocker.size == 1 && blocker[0].pos.w == item.pos.w && blocker[0].pos.h == item.pos.h) {
            val other = blocker[0]
            return items.map {
                when (it.id) {
                    id -> it.withPos(dest)
                    other.id -> it.withPos(other.pos.copy(x = item.pos.x, y = item.pos.y))
                    else -> it
                }
            }
        }
        return null
    }

    fun resize(items: List<HomeItem>, id: String, w: Int, h: Int): List<HomeItem>? {
        val item = items.firstOrNull { it.id == id } ?: return null
        val p = item.pos.copy(w = w, h = h)
        return if (canPlace(items, p, id)) items.map { if (it.id == id) it.withPos(p) else it } else null
    }

    /** Passt Elemente nach einer Rasteränderung an. Elemente ohne Platz werden zurückgegeben. */
    fun reflow(items: List<HomeItem>): Pair<List<HomeItem>, List<HomeItem>> {
        val placed = mutableListOf<HomeItem>()
        val overflow = mutableListOf<HomeItem>()
        items.sortedWith(compareBy({ it.pos.y }, { it.pos.x })).forEach { item ->
            val clamped = item.pos.copy(w = item.pos.w.coerceAtMost(columns), h = item.pos.h.coerceAtMost(rows))
            val p = if (canPlace(placed, clamped)) clamped else findNearest(placed, clamped)
            if (p != null) placed += item.withPos(p) else overflow += item
        }
        return placed to overflow
    }
}

private var idCounter = 0L

/** Eindeutige, lesbare ID. */
fun newId(prefix: String): String {
    idCounter++
    val t = System.currentTimeMillis().toString(36)
    val r = (Math.random() * 1_679_616).toLong().toString(36)
    return "$prefix-$t$r${idCounter.toString(36)}"
}
