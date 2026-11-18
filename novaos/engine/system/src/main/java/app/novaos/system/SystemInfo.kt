package app.novaos.system

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

data class BatteryState(
    val level: Int = 0,
    val charging: Boolean = false,
    val plugged: String = "",
    val temperatureC: Float? = null,
    val health: String = "",
    val voltage: Int = 0,
)

data class StorageState(val totalBytes: Long, val freeBytes: Long) {
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else 1f - freeBytes.toFloat() / totalBytes
}

data class MemoryState(val totalBytes: Long, val availBytes: Long, val lowMemory: Boolean) {
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else 1f - availBytes.toFloat() / totalBytes
}

data class ScreenTimeState(val totalMs: Long, val topApps: List<Pair<String, Long>>)

/** Systemwerte für Widgets. Akku über Broadcast, Speicher/RAM bei Bedarf (keine Hintergrundprozesse). */
class SystemInfo(private val context: Context) {

    private val _battery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = _battery

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = parse(i)
    }
    private var registered = false

    fun start() {
        if (registered) return
        registered = true
        registerSystemReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { parse(it) }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun parse(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Netzteil"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kabellos"
            else -> ""
        }
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Überhitzt"
            BatteryManager.BATTERY_HEALTH_COLD -> "Kalt"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Defekt"
            else -> ""
        }
        _battery.value = BatteryState(
            level = if (level >= 0) level * 100 / scale else 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = plugged,
            temperatureC = temp,
            health = health,
            voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
        )
    }

    fun storage(): StorageState = runCatching {
        val s = StatFs(Environment.getDataDirectory().path)
        StorageState(s.totalBytes, s.availableBytes)
    }.getOrDefault(StorageState(0, 0))

    fun memory(): MemoryState {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return MemoryState(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Bildschirmzeit heute (Summe der Vordergrundzeit). Nur mit Nutzungsdatenzugriff. */
    fun screenTimeToday(): ScreenTimeState? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val stats = runCatching { usm.queryAndAggregateUsageStats(start, now) }.getOrNull() ?: return null
        val pm = context.packageManager
        val filtered = stats.values.filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
        val top = filtered.sortedByDescending { it.totalTimeInForeground }.take(4).map {
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, PackageManager.ApplicationInfoFlags.of(0))).toString() }.getOrDefault(it.packageName)
            label to it.totalTimeInForeground
        }
        return ScreenTimeState(filtered.sumOf { it.totalTimeInForeground }, top)
    }

    companion object {
        fun formatBytes(b: Long): String {
            val gb = b / 1_000_000_000.0
            return if (gb >= 1) "%.1f GB".format(gb).replace('.', ',') else "%d MB".format(b / 1_000_000)
        }

        fun formatDuration(ms: Long): String {
            val m = ms / 60_000
            return if (m >= 60) "${m / 60} Std. ${m % 60} Min." else "$m Min."
        }
    }
}

/** Registriert einen Empfänger für System-Broadcasts (nicht exportiert, Android 13+ konform). */
fun registerSystemReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter): Intent? =
    if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    else context.registerReceiver(receiver, filter)
