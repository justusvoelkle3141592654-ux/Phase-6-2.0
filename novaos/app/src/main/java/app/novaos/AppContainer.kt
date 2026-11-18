package app.novaos

import android.app.Application
import android.content.Context
import app.novaos.data.AppsRepository
import app.novaos.data.BackupManager
import app.novaos.data.ConfigRepository
import app.novaos.data.FileStore
import app.novaos.data.IconRenderer
import app.novaos.data.ProductivityRepository
import app.novaos.data.WallpaperRenderer
import app.novaos.data.WeatherCacheRepository
import app.novaos.model.HomeItem
import app.novaos.model.NovaConfig
import app.novaos.model.RolePlaceholders
import app.novaos.system.CalendarSource
import app.novaos.system.ContactsSource
import app.novaos.system.ControlActions
import app.novaos.system.PermissionCenter
import app.novaos.system.ProfileContextProvider
import app.novaos.system.ReminderScheduler
import app.novaos.system.SystemInfo
import app.novaos.system.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Manuelle Dependency Injection: alle Dienste werden einmal pro Prozess erzeugt und an
 * Activities/ViewModels weitergegeben.
 */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val files = FileStore(context)
    val config = ConfigRepository.create(context, scope)
    val productivity = ProductivityRepository.create(context, scope)
    val weatherCache = WeatherCacheRepository.create(context, scope)
    val apps = AppsRepository(context, scope)
    val icons = IconRenderer(files)
    val wallpapers = WallpaperRenderer(context, files)
    val backup = BackupManager(context, config, productivity, files, BuildConfig.VERSION_NAME)

    val systemInfo = SystemInfo(context)
    val controls = ControlActions(context)
    val weather = WeatherService(context, weatherCache)
    val calendar = CalendarSource(context)
    val contacts = ContactsSource(context)
    val reminders = ReminderScheduler(context)
    val profileContext = ProfileContextProvider(context)
    val permissions = PermissionCenter(context, controls, systemInfo)

    val controller = NovaController(this)

    init {
        // Platzhalter wie "@role/dialer" auf echte Apps abbilden, sobald App-Liste und Konfiguration geladen sind.
        scope.launch {
            combine(apps.apps.filter { it.isNotEmpty() }, config.config.filterNotNull()) { list, cfg -> list.size to cfg }
                .collect { (_, cfg) -> if (containsPlaceholders(cfg)) resolvePlaceholders() }
        }
        // Erinnerungen synchron halten (auch nach Neustart, da der Launcher beim Booten startet).
        scope.launch {
            productivity.data.map { d -> d.tasks.map { Triple(it.id, it.remindAtMs, it.done) } }.distinctUntilChanged().collect {
                reminders.sync(productivity.data.value.tasks)
            }
        }
    }

    private fun containsPlaceholders(cfg: NovaConfig): Boolean = cfg.profiles.any { p ->
        p.layout.dockApps.any(RolePlaceholders::isPlaceholder) ||
            p.layout.pages.any { pg -> pg.items.any { it is HomeItem.App && RolePlaceholders.isPlaceholder(it.appKey) } }
    }

    private suspend fun resolvePlaceholders() {
        val map = RolePlaceholders.all.associateWith { apps.resolvePlaceholder(it) }
        config.update { cfg ->
            cfg.copy(profiles = cfg.profiles.map { p ->
                val dock = p.layout.dockApps.mapNotNull { k -> if (RolePlaceholders.isPlaceholder(k)) map[k] else k }.distinct()
                val pages = p.layout.pages.map { pg ->
                    pg.copy(items = pg.items.mapNotNull { item ->
                        if (item is HomeItem.App && RolePlaceholders.isPlaceholder(item.appKey)) map[item.appKey]?.let { item.copy(appKey = it) } else item
                    })
                }
                p.copy(layout = p.layout.copy(dockApps = dock, pages = pages))
            })
        }
    }
}

class NovaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.nova: AppContainer get() = (applicationContext as NovaApp).container
