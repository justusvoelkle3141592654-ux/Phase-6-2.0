package app.novaos.model

import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * NovaOS-Backupformat (dokumentiert in docs/BACKUP_FORMAT.md).
 *
 * Eine Backup-Datei (.novabackup) ist ein ZIP-Archiv mit:
 *  - manifest.json      Formatkennung, Version, Prüfsummen
 *  - config.json        Launcher-Konfiguration (NovaConfig)
 *  - productivity.json  Aufgaben, Projekte, Notizen (optional)
 *  - files/...          Hintergrundbilder und eigene Icons (optional)
 */
@Serializable
data class BackupFileEntry(val path: String, val sha256: String, val size: Long)

@Serializable
data class BackupManifest(
    val format: String = FORMAT,
    val formatVersion: Int = VERSION,
    val appVersion: String,
    val createdAtMs: Long,
    val sections: List<BackupSection>,
    val configSha256: String,
    val productivitySha256: String? = null,
    val files: List<BackupFileEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "novaos-backup"
        const val VERSION = 1
    }
}

@Serializable
enum class BackupSection(val label: String) {
    LAYOUTS("Homescreen-Layouts"),
    WIDGETS("Widget-Konfigurationen"),
    ICONS("Icon-Zuordnungen und Icon-Pakete"),
    THEMES("Themes"),
    COLORS("Farben"),
    ANIMATIONS("Animationen"),
    GESTURES("Gesten"),
    PROFILES("Profile"),
    SETTINGS("Launcher-Einstellungen"),
    PRODUCTIVITY("Aufgaben, Projekte, Notizen"),
    FILES("Hintergrundbilder und eigene Icons"),
}

sealed interface BackupProblem {
    val message: String

    data object NotABackup : BackupProblem { override val message = "Die Datei ist kein NovaOS-Backup." }
    data class UnsupportedVersion(val found: Int) : BackupProblem {
        override val message = "Backup-Version $found wird von dieser NovaOS-Version nicht unterstützt (maximal ${BackupManifest.VERSION})."
    }
    data class Corrupt(val detail: String) : BackupProblem { override val message = "Das Backup ist beschädigt: $detail" }
    data class ChecksumMismatch(val part: String) : BackupProblem { override val message = "Prüfsumme stimmt nicht: $part wurde verändert oder ist beschädigt." }
    data class MissingPart(val part: String) : BackupProblem { override val message = "Im Backup fehlt: $part" }
}

data class BackupContent(
    val manifest: BackupManifest,
    val config: NovaConfig,
    val productivity: ProductivityData?,
    /** Pfad (relativ, ohne "files/") -> Inhalt. */
    val files: Map<String, ByteArray>,
)

sealed interface BackupReadResult {
    data class Ok(val content: BackupContent) : BackupReadResult
    data class Failed(val problems: List<BackupProblem>) : BackupReadResult
}

object BackupCodec {

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun safePath(p: String): Boolean =
        p.isNotBlank() && !p.startsWith("/") && !p.contains("..") && !p.contains('\\')

    fun write(
        out: OutputStream,
        config: NovaConfig,
        productivity: ProductivityData?,
        files: Map<String, ByteArray>,
        appVersion: String,
        sections: List<BackupSection> = BackupSection.entries,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val configBytes = NovaJsonPretty.encodeToString(NovaConfig.serializer(), config).toByteArray()
        val prodBytes = productivity?.let { NovaJsonPretty.encodeToString(ProductivityData.serializer(), it).toByteArray() }
        val fileEntries = files.filterKeys { safePath(it) }.map { (p, b) -> BackupFileEntry(p, sha256(b), b.size.toLong()) }
        val manifest = BackupManifest(
            appVersion = appVersion,
            createdAtMs = nowMs,
            sections = sections,
            configSha256 = sha256(configBytes),
            productivitySha256 = prodBytes?.let { sha256(it) },
            files = fileEntries,
        )
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            put("manifest.json", NovaJsonPretty.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            put("config.json", configBytes)
            if (prodBytes != null) put("productivity.json", prodBytes)
            files.filterKeys { safePath(it) }.forEach { (p, b) -> put("files/$p", b) }
        }
    }

    fun encode(config: NovaConfig, productivity: ProductivityData?, files: Map<String, ByteArray>, appVersion: String, sections: List<BackupSection> = BackupSection.entries): ByteArray {
        val bos = ByteArrayOutputStream()
        write(bos, config, productivity, files, appVersion, sections)
        return bos.toByteArray()
    }

    /** Liest und prüft ein Backup vollständig. Erkennt fremde, beschädigte und inkompatible Dateien. */
    fun read(input: InputStream, maxBytes: Long = 200L * 1024 * 1024): BackupReadResult {
        val entries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var total = 0L
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > maxBytes) return BackupReadResult.Failed(listOf(BackupProblem.Corrupt("Datei ist zu groß.")))
                    entries[e.name] = bytes
                }
            }
        } catch (t: Throwable) {
            return BackupReadResult.Failed(listOf(BackupProblem.Corrupt(t.message ?: "ZIP nicht lesbar")))
        }
        if (entries.isEmpty()) return BackupReadResult.Failed(listOf(BackupProblem.NotABackup))
        val manifestBytes = entries["manifest.json"] ?: return BackupReadResult.Failed(listOf(BackupProblem.NotABackup))
        val manifest = try {
            NovaJson.decodeFromString(BackupManifest.serializer(), manifestBytes.decodeToString())
        } catch (t: Throwable) {
            return BackupReadResult.Failed(listOf(BackupProblem.NotABackup))
        }
        if (manifest.format != BackupManifest.FORMAT) return BackupReadResult.Failed(listOf(BackupProblem.NotABackup))
        if (manifest.formatVersion > BackupManifest.VERSION || manifest.formatVersion < 1) {
            return BackupReadResult.Failed(listOf(BackupProblem.UnsupportedVersion(manifest.formatVersion)))
        }
        val problems = mutableListOf<BackupProblem>()
        val configBytes = entries["config.json"]
        var config: NovaConfig? = null
        if (configBytes == null) problems += BackupProblem.MissingPart("config.json")
        else if (sha256(configBytes) != manifest.configSha256) problems += BackupProblem.ChecksumMismatch("config.json")
        else config = try {
            ConfigMigrations.normalize(NovaJson.decodeFromString(NovaConfig.serializer(), configBytes.decodeToString()))
        } catch (t: Throwable) {
            problems += BackupProblem.Corrupt("config.json: ${t.message?.take(160)}"); null
        }
        var prod: ProductivityData? = null
        if (manifest.productivitySha256 != null) {
            val pb = entries["productivity.json"]
            if (pb == null) problems += BackupProblem.MissingPart("productivity.json")
            else if (sha256(pb) != manifest.productivitySha256) problems += BackupProblem.ChecksumMismatch("productivity.json")
            else prod = try {
                NovaJson.decodeFromString(ProductivityData.serializer(), pb.decodeToString())
            } catch (t: Throwable) {
                problems += BackupProblem.Corrupt("productivity.json"); null
            }
        }
        val files = mutableMapOf<String, ByteArray>()
        manifest.files.forEach { f ->
            if (!safePath(f.path)) { problems += BackupProblem.Corrupt("Ungültiger Pfad ${f.path}"); return@forEach }
            val b = entries["files/${f.path}"]
            when {
                b == null -> problems += BackupProblem.MissingPart(f.path)
                sha256(b) != f.sha256 -> problems += BackupProblem.ChecksumMismatch(f.path)
                else -> files[f.path] = b
            }
        }
        if (problems.isNotEmpty() || config == null) return BackupReadResult.Failed(problems.ifEmpty { listOf(BackupProblem.Corrupt("Unbekannter Fehler")) })
        return BackupReadResult.Ok(BackupContent(manifest, config, prod, files))
    }

    /**
     * Übernimmt ausgewählte Bereiche aus einem Backup in die aktuelle Konfiguration.
     * PROFILES ersetzt alle Profile vollständig; die übrigen Bereiche werden in Profile mit gleicher ID
     * (bzw. in das aktive Profil) eingespielt.
     */
    fun merge(current: NovaConfig, backup: NovaConfig, sections: Set<BackupSection>): NovaConfig {
        var c = current
        if (BackupSection.PROFILES in sections) {
            c = c.copy(
                profiles = backup.profiles,
                activeProfileId = backup.activeProfileId,
                manualProfileId = backup.manualProfileId,
                autoRules = backup.autoRules,
                autoSwitchEnabled = backup.autoSwitchEnabled,
            )
        } else {
            val backupById = backup.profiles.associateBy { it.id }
            fun source(p: Profile): Profile = backupById[p.id] ?: if (p.id == current.activeProfileId) backup.activeProfile else p
            c = c.copy(profiles = c.profiles.map { p ->
                val b = source(p)
                if (b === p) return@map p
                var n = p
                if (BackupSection.LAYOUTS in sections) n = n.copy(layout = b.layout, wallpaper = b.wallpaper)
                if (BackupSection.WIDGETS in sections) n = n.copy(layout = mergeWidgets(n.layout, b.layout))
                if (BackupSection.ICONS in sections) n = n.copy(iconOverrides = b.iconOverrides, theme = n.theme.copy(icons = b.theme.icons))
                if (BackupSection.THEMES in sections) n = n.copy(theme = b.theme)
                if (BackupSection.COLORS in sections) n = n.copy(theme = n.theme.copy(colors = b.theme.colors))
                if (BackupSection.ANIMATIONS in sections) n = n.copy(theme = n.theme.copy(animation = b.theme.animation))
                if (BackupSection.GESTURES in sections) n = n.copy(gestures = b.gestures)
                n
            })
        }
        if (BackupSection.THEMES in sections) c = c.copy(userThemes = (c.userThemes.filter { t -> backup.userThemes.none { it.id == t.id } } + backup.userThemes))
        if (BackupSection.ICONS in sections) c = c.copy(iconPacks = (c.iconPacks.filter { t -> backup.iconPacks.none { it.id == t.id } } + backup.iconPacks))
        if (BackupSection.WIDGETS in sections) c = c.copy(
            customWidgets = c.customWidgets.filter { w -> backup.customWidgets.none { it.id == w.id } } + backup.customWidgets,
            widgetTemplates = c.widgetTemplates.filter { w -> backup.widgetTemplates.none { it.id == w.id } } + backup.widgetTemplates,
        )
        if (BackupSection.SETTINGS in sections) c = c.copy(general = backup.general)
        return ConfigMigrations.normalize(c.copy(setupCompleted = true))
    }

    /** Übernimmt Widget-Einstellungen aus [from] für Widgets mit gleicher Element-ID. */
    private fun mergeWidgets(target: HomeLayout, from: HomeLayout): HomeLayout {
        val widgets = from.pages.flatMap { it.items }.filterIsInstance<HomeItem.Widget>().associateBy { it.id }
        return target.copy(pages = target.pages.map { page ->
            page.copy(items = page.items.map { item ->
                if (item is HomeItem.Widget) widgets[item.id]?.let { item.copy(widget = it.widget) } ?: item else item
            })
        })
    }
}

/** Einzelner Export von Themes, Widget-Vorlagen, Profilen und Icon-Paketen als JSON-Datei. */
@Serializable
data class NovaExport(
    val format: String,
    val formatVersion: Int = 1,
    val theme: ThemeSpec? = null,
    val widgetTemplate: WidgetTemplate? = null,
    val profile: Profile? = null,
    val iconPack: IconPack? = null,
) {
    companion object {
        const val THEME = "novaos-theme"
        const val WIDGET = "novaos-widget"
        const val PROFILE = "novaos-profile"
        const val ICON_PACK = "novaos-iconpack"

        fun encode(e: NovaExport): String = NovaJsonPretty.encodeToString(serializer(), e)

        /** Liest eine Export-Datei. Liefert null, wenn Format oder Inhalt nicht passen. */
        fun decode(text: String, expectedFormat: String): NovaExport? = try {
            val e = NovaJson.decodeFromString(serializer(), text)
            val complete = when (expectedFormat) {
                THEME -> e.theme != null
                WIDGET -> e.widgetTemplate != null
                PROFILE -> e.profile != null
                ICON_PACK -> e.iconPack != null
                else -> false
            }
            if (e.format == expectedFormat && e.formatVersion in 1..1 && complete) e else null
        } catch (t: Throwable) {
            null
        }
    }
}
