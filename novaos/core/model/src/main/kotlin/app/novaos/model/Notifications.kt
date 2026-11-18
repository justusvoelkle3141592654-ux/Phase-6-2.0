package app.novaos.model

/** Plattformunabhängige Sicht auf eine Benachrichtigung (gefüllt vom NotificationListenerService). */
data class NotificationEntry(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val priority: Int = 0,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val groupKey: String? = null,
    val category: String? = null,
    val isMessage: Boolean = false,
    val actions: List<NotificationActionEntry> = emptyList(),
)

data class NotificationActionEntry(val index: Int, val title: String, val acceptsReply: Boolean)

data class NotificationGroup(val id: String, val title: String, val packageName: String?, val entries: List<NotificationEntry>)

object NotificationGrouping {

    fun group(entries: List<NotificationEntry>, style: GroupingStyle, newestFirst: Boolean, nowMs: Long): List<NotificationGroup> {
        val sorted = if (newestFirst) entries.sortedByDescending { it.postedAtMs } else entries.sortedBy { it.postedAtMs }
        return when (style) {
            GroupingStyle.FLAT -> if (sorted.isEmpty()) emptyList() else listOf(NotificationGroup("all", "Alle", null, sorted))
            GroupingStyle.BY_APP -> sorted.groupBy { it.packageName }.map { (pkg, list) ->
                NotificationGroup(pkg, list.first().appLabel, pkg, list)
            }
            GroupingStyle.PRIORITY -> {
                val important = sorted.filter { it.priority > 0 || it.isMessage }
                val rest = sorted - important.toSet()
                listOfNotNull(
                    important.takeIf { it.isNotEmpty() }?.let { NotificationGroup("important", "Wichtig", null, it) },
                    rest.takeIf { it.isNotEmpty() }?.let { NotificationGroup("other", "Weitere", null, it) },
                )
            }
            GroupingStyle.BY_TIME -> sorted.groupBy { timeBucket(it.postedAtMs, nowMs) }.map { (bucket, list) ->
                NotificationGroup("time-$bucket", bucket, null, list)
            }
        }
    }

    fun timeBucket(postedAtMs: Long, nowMs: Long): String {
        val minutes = (nowMs - postedAtMs) / 60_000
        return when {
            minutes < 60 -> "Letzte Stunde"
            minutes < 24 * 60 -> "Heute"
            minutes < 48 * 60 -> "Gestern"
            else -> "Älter"
        }
    }

    fun relativeTime(postedAtMs: Long, nowMs: Long): String {
        val minutes = (nowMs - postedAtMs) / 60_000
        return when {
            minutes < 1 -> "Jetzt"
            minutes < 60 -> "$minutes Min."
            minutes < 24 * 60 -> "${minutes / 60} Std."
            else -> "${minutes / (24 * 60)} T."
        }
    }
}
