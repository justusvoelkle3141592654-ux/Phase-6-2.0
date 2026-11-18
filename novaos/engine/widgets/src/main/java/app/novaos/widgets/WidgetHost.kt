package app.novaos.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import app.novaos.data.AppsRepository
import app.novaos.data.WeatherCache
import app.novaos.model.CustomWidgetDef
import app.novaos.model.ProductivityData
import app.novaos.model.ShortcutTarget
import app.novaos.model.TemperatureUnit
import app.novaos.model.WidgetRequirement
import app.novaos.system.CalendarSource
import app.novaos.system.ContactsSource
import app.novaos.system.ControlActions
import app.novaos.system.SystemInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Aktionen, die Widgets beim Antippen auslösen. Umgesetzt vom Launcher. */
interface WidgetActions {
    fun launchApp(key: String)
    fun openTarget(target: ShortcutTarget)
    fun editWidget(itemId: String)
    fun requestRequirement(requirement: WidgetRequirement)
    fun openWeatherSettings()
    fun openProductivity(section: String)
    fun openNotification(key: String)
    fun openClock()
    fun openAppDetails(key: String)
    fun openMediaPlayer()
}

/** Alles, was Widgets an Daten und Diensten benötigen. Wird über [LocalWidgetHost] bereitgestellt. */
class WidgetHost(
    val systemInfo: SystemInfo,
    val weather: StateFlow<WeatherCache>,
    val temperatureUnit: TemperatureUnit,
    val refreshWeather: suspend (force: Boolean) -> Unit,
    val productivity: StateFlow<ProductivityData>,
    val updateProductivity: (transform: (ProductivityData) -> ProductivityData) -> Unit,
    val calendar: CalendarSource,
    val contacts: ContactsSource,
    val apps: AppsRepository,
    val controls: ControlActions,
    val actions: WidgetActions,
    val favorites: List<String>,
    val customWidgets: List<CustomWidgetDef>,
    val hasRequirement: (WidgetRequirement) -> Boolean,
    val appIcon: @Composable (key: String, size: Dp) -> Unit,
    val use24h: Boolean,
)

val LocalWidgetHost = staticCompositionLocalOf<WidgetHost?> { null }

/** Aktuelle Uhrzeit, aktualisiert zu jeder vollen Minute (bzw. Sekunde) – nur solange sichtbar. */
@Composable
fun rememberNow(everySecond: Boolean = false): State<Long> = produceState(System.currentTimeMillis(), everySecond) {
    while (true) {
        val now = System.currentTimeMillis()
        value = now
        val step = if (everySecond) 1_000L else 60_000L
        delay(step - now % step + 15)
    }
}

/** Führt [block] in einem Intervall aus, solange das Widget sichtbar ist. */
@Composable
fun Ticker(intervalMs: Long, block: suspend () -> Unit) {
    LaunchedEffect(intervalMs) {
        while (true) {
            block()
            delay(intervalMs)
        }
    }
}
