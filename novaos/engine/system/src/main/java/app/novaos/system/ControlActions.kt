package app.novaos.system

import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import app.novaos.model.CcPermission
import app.novaos.model.CcTileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Geräteadministrator ausschließlich für "Bildschirm sperren" (offizielle DevicePolicyManager-API). */
class NovaDeviceAdmin : DeviceAdminReceiver()

data class ControlState(
    val wifiConnected: Boolean = false,
    val bluetoothOn: Boolean = false,
    val airplane: Boolean = false,
    val flashlight: Boolean = false,
    val flashlightAvailable: Boolean = false,
    val dnd: Boolean = false,
    val autoRotate: Boolean = false,
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    val volume: Float = 0.5f,
    val locationOn: Boolean = false,
    val nfcOn: Boolean = false,
    val nfcAvailable: Boolean = false,
)

/**
 * Nova Control Center – alle Aktionen über offizielle Android-Schnittstellen.
 * WLAN, Bluetooth, Flugmodus, NFC, Standort und Hotspot dürfen normale Apps seit Android 10 nicht selbst
 * schalten; dafür werden die offiziellen Systempanels bzw. Einstellungsseiten geöffnet.
 */
class ControlActions(private val context: Context) {

    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, NovaDeviceAdmin::class.java)

    private val _state = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = _state

    private var torchCameraId: String? = runCatching {
        camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) _state.value = _state.value.copy(flashlight = enabled)
        }
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    private var listening = false

    fun startListening() {
        if (listening) return
        listening = true
        runCatching { camera.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper())) }
        val cr = context.contentResolver
        listOf(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
            Settings.System.getUriFor("volume_music_speaker"),
        ).forEach { runCatching { cr.registerContentObserver(it, false, observer) } }
        refresh()
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        runCatching { camera.unregisterTorchCallback(torchCallback) }
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun refresh() {
        val cr = context.contentResolver
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        val bt = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val nfc = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        _state.value = _state.value.copy(
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            bluetoothOn = bt,
            airplane = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            flashlightAvailable = torchCameraId != null,
            dnd = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL,
            autoRotate = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
            brightness = (Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(0f, 1f),
            autoBrightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat(),
            locationOn = runCatching { context.getSystemService(LocationManager::class.java).isLocationEnabled }.getOrDefault(false),
            nfcAvailable = nfc != null,
            nfcOn = nfc?.isEnabled == true,
        )
    }

    // ------------------------------------------------------------ Berechtigungen

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)
    fun hasDndAccess(): Boolean = notifications.isNotificationPolicyAccessGranted
    fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    fun permissionGranted(p: CcPermission): Boolean = when (p) {
        CcPermission.NONE -> true
        CcPermission.WRITE_SETTINGS -> canWriteSettings()
        CcPermission.DND_ACCESS -> hasDndAccess()
        CcPermission.NOTIFICATION_ACCESS -> NotificationHub.isAccessGranted(context)
        CcPermission.DEVICE_ADMIN -> isAdminActive()
    }

    fun permissionIntent(p: CcPermission): Intent? = when (p) {
        CcPermission.NONE -> null
        CcPermission.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        CcPermission.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        CcPermission.NOTIFICATION_ACCESS -> NotificationHub.accessSettingsIntent(context)
        CcPermission.DEVICE_ADMIN -> adminRequestIntent()
    }

    fun adminRequestIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NovaOS verwendet diese Freigabe nur, um den Bildschirm per Geste oder Kachel zu sperren.")

    fun removeAdmin() { runCatching { dpm.removeActiveAdmin(admin) } }

    // ------------------------------------------------------------ Aktionen

    /** Liefert ein Intent, falls die Aktion ein Systempanel/eine Freigabe benötigt; sonst null (direkt ausgeführt). */
    fun toggle(type: CcTileType): Intent? {
        when (type) {
            CcTileType.WIFI -> return Intent(Settings.Panel.ACTION_WIFI)
            CcTileType.INTERNET -> return Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            CcTileType.BLUETOOTH -> return Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            CcTileType.AIRPLANE -> return Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            CcTileType.NFC -> return Intent(Settings.Panel.ACTION_NFC)
            CcTileType.LOCATION -> return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            CcTileType.HOTSPOT -> return Intent(Settings.ACTION_WIRELESS_SETTINGS)
            CcTileType.VOLUME -> return Intent(Settings.Panel.ACTION_VOLUME)
            CcTileType.SETTINGS -> return Intent(Settings.ACTION_SETTINGS)
            CcTileType.BATTERY -> return Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            CcTileType.FLASHLIGHT -> {
                val id = torchCameraId ?: return null
                runCatching { camera.setTorchMode(id, !_state.value.flashlight) }
            }
            CcTileType.DND -> {
                if (!hasDndAccess()) return permissionIntent(CcPermission.DND_ACCESS)
                val on = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
                notifications.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            CcTileType.ROTATION -> {
                if (!canWriteSettings()) return permissionIntent(CcPermission.WRITE_SETTINGS)
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (_state.value.autoRotate) 0 else 1)
            }
            CcTileType.BRIGHTNESS -> {
                if (!canWriteSettings()) return permissionIntent(CcPermission.WRITE_SETTINGS)
                val auto = _state.value.autoBrightness
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            }
            CcTileType.LOCK -> if (!lockNow()) return adminRequestIntent()
            CcTileType.MEDIA -> MediaHub.playPause()
            else -> Unit
        }
        refresh()
        return null
    }

    /** Systemhelligkeit setzen (benötigt "Systemeinstellungen ändern"). */
    fun setBrightness(value: Float): Intent? {
        if (!canWriteSettings()) return permissionIntent(CcPermission.WRITE_SETTINGS)
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (value.coerceIn(0.01f, 1f) * 255).toInt())
        _state.value = _state.value.copy(brightness = value, autoBrightness = false)
        return null
    }

    fun setVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value.coerceIn(0f, 1f) * max).toInt(), 0) }
        _state.value = _state.value.copy(volume = value)
    }

    /** Sperrt den Bildschirm über den Geräteadministrator. false = Freigabe fehlt. */
    fun lockNow(): Boolean {
        if (!isAdminActive()) return false
        return runCatching { dpm.lockNow(); true }.getOrDefault(false)
    }

    fun isActive(type: CcTileType, s: ControlState = _state.value): Boolean = when (type) {
        CcTileType.WIFI, CcTileType.INTERNET -> s.wifiConnected
        CcTileType.BLUETOOTH -> s.bluetoothOn
        CcTileType.AIRPLANE -> s.airplane
        CcTileType.FLASHLIGHT -> s.flashlight
        CcTileType.DND -> s.dnd
        CcTileType.ROTATION -> s.autoRotate
        CcTileType.BRIGHTNESS -> s.autoBrightness
        CcTileType.LOCATION -> s.locationOn
        CcTileType.NFC -> s.nfcOn
        CcTileType.MEDIA -> MediaHub.state.value?.playing == true
        else -> false
    }
}
