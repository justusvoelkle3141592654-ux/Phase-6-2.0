package app.novaos.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

@Serializable
data class WeatherSettings(
    val enabled: Boolean = true,
    val useDeviceLocation: Boolean = false,
    val cityName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@Serializable
data class GeneralSettings(
    /** Reduziert Glas, Unschärfe und Animationen. */
    val performanceMode: Boolean = false,
    val haptics: Boolean = true,
    val weather: WeatherSettings = WeatherSettings(),
    /** Doppeltippen-Sperre per Geräteadministrator verwenden. */
    val lockViaDeviceAdmin: Boolean = true,
    /** Startzähler für die Sortierung „Meistgenutzt“ erfassen (nur lokal). */
    val trackAppUsage: Boolean = true,
    val confirmBeforeUninstall: Boolean = true,
    val showNotificationBadges: Boolean = true,
)

@Serializable
data class NovaConfig(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val setupCompleted: Boolean = false,
    val profiles: List<Profile>,
    /** Zuletzt manuell gewähltes Profil (Rückfall, wenn keine Regel passt). */
    val manualProfileId: String,
    val activeProfileId: String,
    val autoSwitchEnabled: Boolean = true,
    val autoRules: List<AutoRule> = emptyList(),
    val userThemes: List<ThemeSpec> = emptyList(),
    val iconPacks: List<IconPack> = emptyList(),
    val customWidgets: List<CustomWidgetDef> = emptyList(),
    val widgetTemplates: List<WidgetTemplate> = emptyList(),
    val general: GeneralSettings = GeneralSettings(),
    /** Startzähler je App für „Meistgenutzt“. */
    val launchCounts: Map<String, Int> = emptyMap(),
) {
    val activeProfile: Profile get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

    fun updateActive(transform: (Profile) -> Profile): NovaConfig {
        val id = activeProfile.id
        return copy(profiles = profiles.map { if (it.id == id) transform(it) else it })
    }

    fun updateProfile(id: String, transform: (Profile) -> Profile): NovaConfig =
        copy(profiles = profiles.map { if (it.id == id) transform(it) else it })

    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

/** Gemeinsame JSON-Konfiguration für Speicherung, Backups und Exporte. */
val NovaJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "@type"
    prettyPrint = false
    allowSpecialFloatingPointValues = false
}

val NovaJsonPretty: Json = Json(NovaJson) { prettyPrint = true }

object ConfigMigrations {
    /**
     * Bringt eine geladene Konfiguration auf den aktuellen Stand und repariert inkonsistente Werte
     * (z. B. aktive Profil-ID, die es nicht mehr gibt).
     */
    fun normalize(c: NovaConfig): NovaConfig {
        var cfg = c
        if (cfg.profiles.isEmpty()) return Defaults.config()
        val ids = cfg.profiles.map { it.id }.toSet()
        if (cfg.activeProfileId !in ids) cfg = cfg.copy(activeProfileId = cfg.profiles.first().id)
        if (cfg.manualProfileId !in ids) cfg = cfg.copy(manualProfileId = cfg.activeProfileId)
        cfg = cfg.copy(
            profiles = cfg.profiles.map { p ->
                val l = p.layout
                val cols = l.columns.coerceIn(2, 8)
                val rows = l.rows.coerceIn(3, 10)
                val pages = if (l.pages.isEmpty()) listOf(HomePage(newId("page"))) else l.pages
                p.copy(layout = l.copy(columns = cols, rows = rows, pages = pages, defaultPage = l.defaultPage.coerceIn(0, pages.size - 1)))
            },
            autoRules = cfg.autoRules.filter { it.profileId in ids },
            schemaVersion = NovaConfig.CURRENT_SCHEMA,
        )
        return cfg
    }
}
