package app.novaos.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import app.novaos.model.RuleContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

/** Home-App-Rolle (Standard-Startbildschirm) über RoleManager. */
object HomeRole {
    fun isDefault(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** Systemdialog, mit dem der Benutzer NovaOS als Startbildschirm bestätigt. */
    fun requestIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java)
        return if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }

    fun homeSettingsIntent() = Intent(Settings.ACTION_HOME_SETTINGS)
}

/**
 * Sammelt den Gerätezustand für die automatische Profilwahl, solange der Launcher sichtbar ist
 * (Uhrzeit, verbundene Bluetooth-Geräte, WLAN-Name). Keine Hintergrunddienste.
 */
class ProfileContextProvider(private val context: Context) {

    private val _btDevices = MutableStateFlow<Set<String>>(emptySet())
    private val _ssid = MutableStateFlow<String?>(null)
    val bluetoothDevices: StateFlow<Set<String>> = _btDevices
    val ssid: StateFlow<String?> = _ssid

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var started = false

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = queryBluetooth()
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val info = caps.transportInfo as? WifiInfo
            val raw = info?.ssid
            _ssid.value = raw?.takeIf { it != WifiManager.UNKNOWN_SSID }?.trim('"')
        }
        override fun onLost(network: Network) { _ssid.value = null }
    }

    fun hasBluetoothPermission() = context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun hasLocationPermission() = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun start() {
        if (started) return
        started = true
        if (hasBluetoothPermission()) {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            runCatching { registerSystemReceiver(context, btReceiver, f) }
            queryBluetooth()
        }
        if (hasLocationPermission()) {
            val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            runCatching { cm.registerNetworkCallback(req, wifiCallback) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(btReceiver) }
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
    }

    @SuppressLint("MissingPermission")
    private fun queryBluetooth() {
        if (!hasBluetoothPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) { _btDevices.value = emptySet(); return }
        val found = mutableSetOf<String>()
        listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).forEach { profile ->
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    runCatching { proxy.connectedDevices.forEach { d -> found += (d.name ?: d.address) } }
                    _btDevices.value = found.toSet()
                    adapter.closeProfileProxy(p, proxy)
                }
                override fun onServiceDisconnected(p: Int) {}
            }, profile)
        }
    }

    fun current(): RuleContext {
        val cal = Calendar.getInstance()
        val dow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // Montag = 1
        return RuleContext(dow, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE), _btDevices.value, _ssid.value)
    }
}

/** Übersicht aller Freigaben, die NovaOS nutzen kann – mit Zweck und Weg zur Freigabe. */
enum class NovaPermission(val title: String, val purpose: String, val runtime: String? = null) {
    HOME_ROLE("Standard-Startbildschirm", "NovaOS ersetzt den Startbildschirm. Nötig für App-Verknüpfungen."),
    NOTIFICATION_ACCESS("Benachrichtigungszugriff", "Nova Notification Center, Nachrichten-Widgets, Musiksteuerung und App-Badges."),
    POST_NOTIFICATIONS("Benachrichtigungen senden", "Erinnerungen aus Aufgaben.", Manifest.permission.POST_NOTIFICATIONS),
    CALENDAR("Kalender", "Kalender- und Agenda-Widgets (nur lesen).", Manifest.permission.READ_CALENDAR),
    CONTACTS("Kontakte", "Favoritenkontakte und Anruf-Widgets (nur lesen).", Manifest.permission.READ_CONTACTS),
    LOCATION("Ungefährer Standort", "Wetter für den aktuellen Ort.", Manifest.permission.ACCESS_COARSE_LOCATION),
    FINE_LOCATION("Genauer Standort", "WLAN-Name für automatische Profile (Android-Vorgabe).", Manifest.permission.ACCESS_FINE_LOCATION),
    BLUETOOTH("Bluetooth-Geräte", "Profilwechsel bei verbundenen Kopfhörern.", Manifest.permission.BLUETOOTH_CONNECT),
    USAGE_ACCESS("Nutzungsdaten", "Bildschirmzeit-Widget."),
    WRITE_SETTINGS("Systemeinstellungen ändern", "Helligkeit und automatisches Drehen im Kontrollzentrum."),
    DND_ACCESS("„Nicht stören“-Zugriff", "Nicht-stören-Kachel im Kontrollzentrum."),
    DEVICE_ADMIN("Geräteadministrator (nur Sperren)", "Bildschirm per Doppeltippen oder Kachel sperren."),
}

class PermissionCenter(private val context: Context, private val controls: ControlActions, private val systemInfo: SystemInfo) {

    fun granted(p: NovaPermission): Boolean = when (p) {
        NovaPermission.HOME_ROLE -> HomeRole.isDefault(context)
        NovaPermission.NOTIFICATION_ACCESS -> NotificationHub.isAccessGranted(context)
        NovaPermission.USAGE_ACCESS -> systemInfo.hasUsageAccess()
        NovaPermission.WRITE_SETTINGS -> controls.canWriteSettings()
        NovaPermission.DND_ACCESS -> controls.hasDndAccess()
        NovaPermission.DEVICE_ADMIN -> controls.isAdminActive()
        NovaPermission.POST_NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        else -> p.runtime?.let { context.hasPermission(it) } ?: true
    }

    /** Einstellungs-Intent für Sonderfreigaben (null = Laufzeitberechtigung per Dialog anfragen). */
    fun settingsIntent(p: NovaPermission): Intent? = when (p) {
        NovaPermission.HOME_ROLE -> HomeRole.requestIntent(context) ?: HomeRole.homeSettingsIntent()
        NovaPermission.NOTIFICATION_ACCESS -> NotificationHub.accessSettingsIntent(context)
        NovaPermission.USAGE_ACCESS -> systemInfo.usageAccessIntent()
        NovaPermission.WRITE_SETTINGS -> controls.permissionIntent(app.novaos.model.CcPermission.WRITE_SETTINGS)
        NovaPermission.DND_ACCESS -> controls.permissionIntent(app.novaos.model.CcPermission.DND_ACCESS)
        NovaPermission.DEVICE_ADMIN -> controls.adminRequestIntent()
        else -> null
    }

    fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
}
