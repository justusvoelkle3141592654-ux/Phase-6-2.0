package app.novaos.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(val id: String, val name: String, val color: String = "#E5202E", val archived: Boolean = false)

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val projectId: String? = null,
    val done: Boolean = false,
    val dueAtMs: Long? = null,
    /** Zeitpunkt der Erinnerung (Benachrichtigung über AlarmManager). */
    val remindAtMs: Long? = null,
    val createdAtMs: Long = 0,
    val doneAtMs: Long? = null,
)

@Serializable
data class Note(
    val id: String,
    val text: String,
    val pinned: Boolean = false,
    val color: String? = null,
    val updatedAtMs: Long = 0,
)

@Serializable
data class ProductivityData(
    val projects: List<Project> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    fun openTasks(): List<TaskItem> = tasks.filter { !it.done }.sortedWith(compareBy({ it.dueAtMs ?: Long.MAX_VALUE }, { it.createdAtMs }))

    /** Fortschritt eines Projekts 0..1 (null, wenn keine Aufgaben). */
    fun projectProgress(projectId: String): Float? {
        val t = tasks.filter { it.projectId == projectId }
        if (t.isEmpty()) return null
        return t.count { it.done }.toFloat() / t.size
    }

    fun overallProgress(): Float? = if (tasks.isEmpty()) null else tasks.count { it.done }.toFloat() / tasks.size

    fun upcomingReminders(nowMs: Long): List<TaskItem> =
        tasks.filter { !it.done && it.remindAtMs != null && it.remindAtMs >= nowMs }.sortedBy { it.remindAtMs }

    companion object {
        fun sample(nowMs: Long): ProductivityData {
            val p = Project("project-nova", "NovaOS einrichten")
            return ProductivityData(
                projects = listOf(p),
                tasks = listOf(
                    TaskItem("task-1", "Hintergrundbild auswählen", p.id, done = true, createdAtMs = nowMs, doneAtMs = nowMs),
                    TaskItem("task-2", "Widgets anordnen", p.id, createdAtMs = nowMs),
                    TaskItem("task-3", "Eigenes Theme speichern", p.id, createdAtMs = nowMs),
                ),
                notes = listOf(Note("note-1", "Langes Drücken auf freie Fläche öffnet das Schnellmenü.", pinned = true, updatedAtMs = nowMs)),
            )
        }
    }
}
