package app.novaos.ui.common

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.novaos.model.Profile
import app.novaos.model.TemperatureUnit
import app.novaos.model.WidgetRequirement
import app.novaos.system.HomeRole
import app.novaos.system.NotificationHub
import app.novaos.system.hasPermission
import app.novaos.widgets.WidgetActions
import app.novaos.widgets.WidgetHost
import kotlinx.coroutines.launch

/** Stellt Widgets alle Daten und Dienste bereit (Launcher und Einstellungs-Vorschauen). */
@Composable
fun rememberNovaWidgetHost(profile: Profile, actions: WidgetActions): WidgetHost {
    val c = LocalContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = c.config.config.collectAsState().value
    val general = cfg?.general
    val custom = cfg?.customWidgets.orEmpty()
    return remember(profile.favorites, custom, general, actions) {
        WidgetHost(
            systemInfo = c.systemInfo,
            weather = c.weatherCache.data,
            temperatureUnit = general?.weather?.unit ?: TemperatureUnit.CELSIUS,
            refreshWeather = { force -> general?.weather?.let { c.weather.refreshIfNeeded(it, force) } },
            productivity = c.productivity.data,
            updateProductivity = { t -> scope.launch { c.productivity.update(t) } },
            calendar = c.calendar,
            contacts = c.contacts,
            apps = c.apps,
            controls = c.controls,
            actions = actions,
            favorites = profile.favorites,
            customWidgets = custom,
            hasRequirement = { r ->
                when (r) {
                    WidgetRequirement.NONE, WidgetRequirement.NETWORK -> true
                    WidgetRequirement.CALENDAR -> ctx.hasPermission(Manifest.permission.READ_CALENDAR)
                    WidgetRequirement.CONTACTS -> ctx.hasPermission(Manifest.permission.READ_CONTACTS)
                    WidgetRequirement.NOTIFICATION_ACCESS -> NotificationHub.isAccessGranted(ctx)
                    WidgetRequirement.USAGE_ACCESS -> c.systemInfo.hasUsageAccess()
                    WidgetRequirement.DEFAULT_LAUNCHER -> HomeRole.isDefault(ctx)
                }
            },
            appIcon = { key, size -> AppIconImage(key, size) },
            use24h = false,
        )
    }
}
