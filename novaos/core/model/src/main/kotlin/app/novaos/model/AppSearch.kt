package app.novaos.model

import java.text.Normalizer

/** Minimale App-Beschreibung für Such- und Sortierlogik (plattformunabhängig). */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val category: String,
    val installedAtMs: Long = 0,
    val isWorkProfile: Boolean = false,
)

object AppCategories {
    const val SOCIAL = "Social"
    const val PRODUCTIVITY = "Produktivität"
    const val MEDIA = "Medien"
    const val GAMES = "Spiele"
    const val TOOLS = "Tools"
    const val OTHER = "Sonstige"
    val all = listOf(SOCIAL, PRODUCTIVITY, MEDIA, GAMES, TOOLS, OTHER)

    /**
     * Ordnet die Android-Kategorie (ApplicationInfo.category) einer NovaOS-Kategorie zu.
     * Werte laut Android: GAME=0, AUDIO=1, VIDEO=2, IMAGE=3, SOCIAL=4, NEWS=5, MAPS=6, PRODUCTIVITY=7, ACCESSIBILITY=8.
     */
    fun fromAndroid(category: Int, packageName: String): String = when (category) {
        0 -> GAMES
        1, 2, 3 -> MEDIA
        4 -> SOCIAL
        5 -> MEDIA
        6 -> TOOLS
        7 -> PRODUCTIVITY
        8 -> TOOLS
        else -> guess(packageName)
    }

    private fun guess(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            listOf("whatsapp", "telegram", "signal", "instagram", "facebook", "twitter", "messenger", "snapchat", "discord", "threema", "mms", "messaging").any { it in p } -> SOCIAL
            listOf("music", "spotify", "youtube", "netflix", "video", "photo", "gallery", "camera", "podcast", "radio", "player").any { it in p } -> MEDIA
            listOf("mail", "gm", "calendar", "docs", "office", "notes", "keep", "drive", "todo", "task", "slack", "teams").any { it in p } -> PRODUCTIVITY
            listOf("game", "games", "supercell", "king", "mojang").any { it in p } -> GAMES
            listOf("settings", "calculator", "clock", "files", "maps", "contacts", "dialer", "phone", "chrome", "browser", "vending", "store").any { it in p } -> TOOLS
            else -> OTHER
        }
    }
}

object AppSearch {

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace("ß", "ss")

    /** Trefferwert: höher = besser, 0 = kein Treffer. */
    fun score(label: String, query: String): Int {
        val q = normalize(query.trim())
        if (q.isEmpty()) return 1
        val l = normalize(label)
        return when {
            l == q -> 1000
            l.startsWith(q) -> 800 - l.length
            l.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 600 - l.length
            l.contains(q) -> 400 - l.indexOf(q)
            isSubsequence(q, l) -> 100
            else -> 0
        }
    }

    private fun isSubsequence(q: String, l: String): Boolean {
        var i = 0
        for (c in l) if (i < q.length && c == q[i]) i++
        return i == q.length && q.length >= 2
    }

    fun search(apps: List<AppEntry>, query: String): List<AppEntry> =
        apps.map { it to score(it.label, query) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenBy { normalize(it.first.label) })
            .map { it.first }

    fun sort(apps: List<AppEntry>, mode: SortMode, launchCounts: Map<String, Int>, customOrder: List<String>): List<AppEntry> = when (mode) {
        SortMode.ALPHABETICAL -> apps.sortedBy { normalize(it.label) }
        SortMode.ALPHABETICAL_DESC -> apps.sortedByDescending { normalize(it.label) }
        SortMode.MOST_USED -> apps.sortedWith(compareByDescending<AppEntry> { launchCounts[it.key] ?: 0 }.thenBy { normalize(it.label) })
        SortMode.RECENTLY_INSTALLED -> apps.sortedByDescending { it.installedAtMs }
        SortMode.CUSTOM -> {
            val idx = customOrder.withIndex().associate { it.value to it.index }
            apps.sortedWith(compareBy<AppEntry> { idx[it.key] ?: Int.MAX_VALUE }.thenBy { normalize(it.label) })
        }
    }

    /** Buchstabe für die alphabetische Navigation (Umlaute zum Grundbuchstaben, Ziffern/Sonstiges als "#"). */
    fun sectionLetter(label: String): Char {
        val c = normalize(label).firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    fun sections(apps: List<AppEntry>): List<Pair<Char, Int>> {
        val out = mutableListOf<Pair<Char, Int>>()
        var last: Char? = null
        apps.forEachIndexed { i, a ->
            val s = sectionLetter(a.label)
            if (s != last) { out += s to i; last = s }
        }
        return out
    }
}
