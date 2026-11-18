package app.novaos.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import app.novaos.data.DayForecast
import app.novaos.data.HourForecast
import app.novaos.data.WeatherCache
import app.novaos.data.WeatherCacheRepository
import app.novaos.model.TaskItem
import app.novaos.model.TemperatureUnit
import app.novaos.model.WeatherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.coroutines.resume

fun Context.hasPermission(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

// ============================================================================ Wetter

data class GeoResult(val name: String, val admin: String, val country: String, val latitude: Double, val longitude: Double)

/**
 * Wetterdaten von Open-Meteo (kostenlos, ohne API-Schlüssel). Abruf höchstens alle 30 Minuten
 * und nur, wenn der Homescreen sichtbar ist – kein Hintergrunddienst.
 */
class WeatherService(private val context: Context, private val cache: WeatherCacheRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "NovaOS-Launcher")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun searchCity(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val root = json.parseToJsonElement(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=de&format=json")).jsonObject
            (root["results"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
                GeoResult(
                    it["name"]?.jsonPrimitive?.content.orEmpty(),
                    it["admin1"]?.jsonPrimitive?.content.orEmpty(),
                    it["country"]?.jsonPrimitive?.content.orEmpty(),
                    it["latitude"]!!.jsonPrimitive.double,
                    it["longitude"]!!.jsonPrimitive.double,
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private suspend fun deviceLocation(): Location? {
        if (!context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = context.getSystemService(LocationManager::class.java)
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3_600_000) return last
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return last
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                val exec = Executors.newSingleThreadExecutor()
                runCatching {
                    lm.getCurrentLocation(provider, null, exec) { loc -> if (cont.isActive) cont.resume(loc); exec.shutdown() }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        } ?: last
    }

    /** Aktualisiert den Cache, wenn er älter als [maxAgeMs] ist (oder [force]). */
    suspend fun refreshIfNeeded(settings: WeatherSettings, force: Boolean = false, maxAgeMs: Long = 30 * 60_000) {
        if (!settings.enabled) return
        val current = cache.data.value
        val sameLocation = settings.useDeviceLocation || (current.latitude == settings.latitude && current.longitude == settings.longitude)
        if (!force && sameLocation && current.error == null && System.currentTimeMillis() - current.fetchedAtMs < maxAgeMs) return
        withContext(Dispatchers.IO) {
            try {
                var lat = settings.latitude
                var lon = settings.longitude
                var place = settings.cityName
                if (settings.useDeviceLocation) {
                    val loc = deviceLocation()
                    if (loc != null) { lat = loc.latitude; lon = loc.longitude; place = "Aktueller Standort" }
                }
                if (lat == null || lon == null) {
                    cache.update { it.copy(error = "Kein Ort festgelegt", fetchedAtMs = System.currentTimeMillis()) }
                    return@withContext
                }
                val unit = if (settings.unit == TemperatureUnit.FAHRENHEIT) "&temperature_unit=fahrenheit" else ""
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
                    "&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&forecast_days=5&timezone=auto$unit"
                cache.set(parse(get(url), lat, lon, place))
            } catch (t: Throwable) {
                Log.w("NovaOS", "Wetter nicht abrufbar", t)
                cache.update { it.copy(error = "Wetter nicht abrufbar", fetchedAtMs = System.currentTimeMillis()) }
            }
        }
    }

    internal fun parse(body: String, lat: Double, lon: Double, place: String): WeatherCache {
        val root = json.parseToJsonElement(body).jsonObject
        val cur = root["current"]!!.jsonObject
        val zone = runCatching { ZoneId.of(root["timezone"]!!.jsonPrimitive.content) }.getOrDefault(ZoneId.systemDefault())
        val hourly = root["hourly"]?.jsonObject
        val now = LocalDateTime.now(zone)
        val hours = hourly?.let { h ->
            val times = h["time"]!!.jsonArray.map { LocalDateTime.parse(it.jsonPrimitive.content) }
            val temps = h["temperature_2m"]!!.jsonArray
            val codes = h["weather_code"]!!.jsonArray
            times.indices.filter { !times[it].isBefore(now.withMinute(0).withSecond(0).withNano(0)) }.take(12).map {
                HourForecast(times[it].atZone(zone).toInstant().toEpochMilli(), temps[it].jsonPrimitive.doubleOrNull ?: 0.0, codes[it].jsonPrimitive.intOrNull ?: 0)
            }
        }.orEmpty()
        val daily = root["daily"]?.jsonObject
        val days = daily?.let { d ->
            val times = d["time"]!!.jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }
            times.indices.map {
                DayForecast(
                    times[it].atStartOfDay(zone).toInstant().toEpochMilli(),
                    d["temperature_2m_max"]!!.jsonArray[it].jsonPrimitive.doubleOrNull ?: 0.0,
                    d["temperature_2m_min"]!!.jsonArray[it].jsonPrimitive.doubleOrNull ?: 0.0,
                    d["weather_code"]!!.jsonArray[it].jsonPrimitive.intOrNull ?: 0,
                )
            }
        }.orEmpty()
        return WeatherCache(
            latitude = lat, longitude = lon, place = place,
            temperature = cur["temperature_2m"]?.jsonPrimitive?.doubleOrNull,
            apparent = cur["apparent_temperature"]?.jsonPrimitive?.doubleOrNull,
            weatherCode = cur["weather_code"]?.jsonPrimitive?.intOrNull,
            isDay = (cur["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1,
            humidity = cur["relative_humidity_2m"]?.jsonPrimitive?.intOrNull,
            wind = cur["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull,
            high = days.firstOrNull()?.high, low = days.firstOrNull()?.low,
            hourly = hours, daily = days,
            fetchedAtMs = System.currentTimeMillis(), error = null,
        )
    }

    companion object {
        /** WMO-Wettercodes (Open-Meteo) auf deutsche Beschreibung abbilden. */
        fun describe(code: Int?): String = when (code) {
            0 -> "Klar"
            1 -> "Überwiegend klar"
            2 -> "Teilweise bewölkt"
            3 -> "Bedeckt"
            45, 48 -> "Nebel"
            51, 53, 55 -> "Nieselregen"
            56, 57 -> "Gefrierender Niesel"
            61, 63, 65 -> "Regen"
            66, 67 -> "Gefrierender Regen"
            71, 73, 75, 77 -> "Schnee"
            80, 81, 82 -> "Regenschauer"
            85, 86 -> "Schneeschauer"
            95 -> "Gewitter"
            96, 99 -> "Gewitter mit Hagel"
            null -> "–"
            else -> "Wetter"
        }

        enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

        fun kind(code: Int?): Kind = when (code) {
            0, 1 -> Kind.CLEAR
            2 -> Kind.PARTLY
            3 -> Kind.CLOUDY
            45, 48 -> Kind.FOG
            in 51..67, in 80..82 -> Kind.RAIN
            in 71..77, 85, 86 -> Kind.SNOW
            in 95..99 -> Kind.STORM
            else -> Kind.CLOUDY
        }
    }
}

// ============================================================================ Kalender

data class CalendarEvent(val id: Long, val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean, val color: Int, val location: String)

class CalendarSource(private val context: Context) {
    fun hasAccess() = context.hasPermission(Manifest.permission.READ_CALENDAR)

    suspend fun events(fromMs: Long, toMs: Long, limit: Int = 50): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs); ContentUris.appendId(it, toMs)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out += CalendarEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "(Ohne Titel)" }, c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getInt(5), c.getString(6).orEmpty())
                }
            }
        }
        out
    }

    fun openEvent(id: Long) {
        val i = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    fun openDay(ms: Long) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, ms) }.build()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

// ============================================================================ Kontakte

data class FavoriteContact(val id: Long, val name: String, val photoUri: String?, val lookupKey: String, val phone: String?)

class ContactsSource(private val context: Context) {
    fun hasAccess() = context.hasPermission(Manifest.permission.READ_CONTACTS)

    suspend fun starred(limit: Int = 8): List<FavoriteContact> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val out = mutableListOf<FavoriteContact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.HAS_PHONE_NUMBER),
                "${ContactsContract.Contacts.STARRED}=1", null, "${ContactsContract.Contacts.TIMES_CONTACTED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    val phone = if (c.getInt(4) > 0) firstPhone(id) else null
                    out += FavoriteContact(id, c.getString(1).orEmpty(), c.getString(2), c.getString(3).orEmpty(), phone)
                }
            }
        }
        out
    }

    private fun firstPhone(id: Long): String? = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(id.toString()), null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Öffnet die Telefon-App mit der Nummer (ACTION_DIAL, keine Anrufberechtigung nötig). */
    fun dial(number: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun message(number: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun open(c: FavoriteContact) {
        val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

// ============================================================================ Erinnerungen

/** Erinnerungen zu Aufgaben über AlarmManager (ungefährer Zeitpunkt, keine Sonderberechtigung). */
class ReminderScheduler(private val context: Context) {
    private val alarm = context.getSystemService(AlarmManager::class.java)

    private fun pending(task: TaskItem, create: Boolean): PendingIntent? {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("id", task.id).putExtra("title", task.title)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, task.id.hashCode(), i, flags)
    }

    fun sync(tasks: List<TaskItem>) {
        val now = System.currentTimeMillis()
        tasks.forEach { t ->
            val at = t.remindAtMs
            if (!t.done && at != null && at > now) {
                pending(t, true)?.let { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, it) }
            } else {
                pending(t, false)?.let { alarm.cancel(it); it.cancel() }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nova_reminders", context.getString(R.string.nova_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = android.app.Notification.Builder(context, "nova_reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(intent.getStringExtra("title") ?: "Erinnerung")
            .setContentText("NovaOS Erinnerung")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(intent.getStringExtra("id").hashCode(), n)
    }
}

@Suppress("unused")
private fun JsonObject.intAt(k: String) = this[k]?.jsonPrimitive?.int
