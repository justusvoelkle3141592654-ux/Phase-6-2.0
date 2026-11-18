package app.novaos.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.novaos.design.DotBar
import app.novaos.design.DotMatrixText
import app.novaos.design.DotRing
import app.novaos.design.Nova
import app.novaos.model.DotFont
import app.novaos.model.DotIntensity
import app.novaos.model.FontChoice
import app.novaos.model.TemperatureUnit
import app.novaos.model.WidgetInstance
import app.novaos.model.WidgetRequirement
import app.novaos.system.CalendarEvent
import app.novaos.system.SystemInfo
import app.novaos.system.WeatherService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal val germanLike: Boolean get() = Locale.getDefault().language == "de"

fun formatDate(ms: Long, withWeekday: Boolean = true): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val pattern = if (germanLike) (if (withWeekday) "EE, d. MMM" else "d. MMM") else (if (withWeekday) "EEE, MMM d" else "MMM d")
    return d.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun formatTime(ms: Long, use24h: Boolean): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return d.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
}

/** Großer Text in Punktmatrix, der die verfügbare Fläche ausfüllt. */
@Composable
fun FittedDots(text: String, maxW: Dp, maxH: Dp, color: Color, dim: Color = color.copy(alpha = 0.07f), modifier: Modifier = Modifier) {
    val wDots = DotFont.widthInDots(text).coerceAtLeast(1)
    val byW = maxW / (wDots * 1.32f)
    val byH = maxH / (DotFont.HEIGHT * 1.32f)
    DotMatrixText(text, modifier, min(byW, byH), color, dim)
}

// ============================================================================ Uhr

@Composable
fun BoxWithConstraintsScope.ClockWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val showSeconds = instance.config["seconds"] == "true"
    val now by rememberNow(showSeconds)
    val ctx = LocalContext.current
    val use24 = host.use24h || DateFormat.is24HourFormat(ctx)
    val time = formatTime(now, use24) + if (showSeconds) ":%02d".format((now / 1000) % 60) else ""
    val date = formatDate(now)
    val clockSpec = Nova.type.spec.clock
    val useDots = look.dots && clockSpec.font == FontChoice.NOVA_DOT
    val dimDots = Nova.style.dotsAtLeast(DotIntensity.STRONG)
    val clockColor = if (instance.style.textColor != null) look.text else Nova.type.clock.color

    if (look.compact) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (useDots) FittedDots(time, W * 0.55f, H * 0.8f, clockColor, if (dimDots) clockColor.copy(alpha = 0.07f) else Color.Transparent)
            else Text(time, style = Nova.type.clock.copy(fontSize = (H.value * 0.55f).sp, color = clockColor), maxLines = 1)
            Text(date, style = look.title, color = look.secondary, maxLines = 1)
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        val timeH = H * if (look.detailed) 0.58f else 0.68f
        if (useDots) FittedDots(time, W, timeH, clockColor, if (dimDots) clockColor.copy(alpha = 0.07f) else Color.Transparent)
        else Text(
            time,
            style = Nova.type.clock.copy(fontSize = (timeH.value * 0.82f).coerceAtMost(W.value / (time.length * 0.58f)).sp, color = clockColor, lineHeight = (timeH.value * 0.9f).sp),
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = look.title.copy(fontWeight = FontWeight.Medium), color = look.text.copy(alpha = 0.85f), maxLines = 1)
            if (look.detailed) {
                val w by host.weather.collectAsState()
                if (w.temperature != null) {
                    Spacer(Modifier.width(10.dp))
                    WeatherGlyph(w.weatherCode, w.isDay, Modifier.size(18.dp), look.text, look.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(formatTemp(w.temperature, host.temperatureUnit), style = look.body, color = look.secondary)
                }
            }
        }
    }
}

// ============================================================================ Wetter

fun formatTemp(t: Double?, unit: TemperatureUnit): String =
    if (t == null) "–" else "${t.roundToInt()}°" + if (unit == TemperatureUnit.FAHRENHEIT) "F" else "C"

/** Eigene Wettersymbole (Punkte/Linien), passend zur Designsprache. */
@Composable
fun WeatherGlyph(code: Int?, isDay: Boolean, modifier: Modifier, color: Color, accent: Color) {
    val kind = WeatherService.kind(code)
    Canvas(modifier) {
        val s = size.minDimension
        fun sun(c: Offset, r: Float) {
            drawCircle(if (isDay) accent else color, r, c)
            if (isDay) for (i in 0 until 8) {
                val a = PI * 2 * i / 8
                drawCircle(accent, r * 0.16f, Offset(c.x + (cos(a) * r * 1.55f).toFloat(), c.y + (sin(a) * r * 1.55f).toFloat()))
            } else drawCircle(Color.Black.copy(alpha = 0.55f), r * 0.85f, Offset(c.x + r * 0.45f, c.y - r * 0.35f))
        }
        fun cloud(c: Offset, w: Float, alpha: Float = 1f) {
            val col = color.copy(alpha = alpha)
            drawCircle(col, w * 0.22f, Offset(c.x - w * 0.18f, c.y + w * 0.02f))
            drawCircle(col, w * 0.28f, Offset(c.x + w * 0.05f, c.y - w * 0.08f))
            drawCircle(col, w * 0.2f, Offset(c.x + w * 0.28f, c.y + w * 0.05f))
            drawRoundRect(col, Offset(c.x - w * 0.38f, c.y), Size(w * 0.84f, w * 0.24f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
        }
        fun drops(c: Offset, w: Float, snow: Boolean) {
            for (i in 0 until 3) {
                val x = c.x - w * 0.25f + i * w * 0.25f
                if (snow) drawCircle(color, w * 0.05f, Offset(x, c.y + w * 0.08f))
                else drawLine(accent, Offset(x, c.y), Offset(x - w * 0.05f, c.y + w * 0.16f), strokeWidth = w * 0.05f)
            }
        }
        val center = Offset(size.width / 2, size.height / 2)
        when (kind) {
            WeatherService.Companion.Kind.CLEAR -> sun(center, s * 0.24f)
            WeatherService.Companion.Kind.PARTLY -> { sun(Offset(center.x - s * 0.12f, center.y - s * 0.12f), s * 0.18f); cloud(Offset(center.x + s * 0.06f, center.y + s * 0.08f), s * 0.7f) }
            WeatherService.Companion.Kind.CLOUDY -> cloud(center, s * 0.8f)
            WeatherService.Companion.Kind.FOG -> for (i in 0 until 4) drawLine(color, Offset(s * 0.15f, s * (0.3f + i * 0.14f)), Offset(s * 0.85f, s * (0.3f + i * 0.14f)), strokeWidth = s * 0.05f)
            WeatherService.Companion.Kind.RAIN -> { cloud(Offset(center.x, center.y - s * 0.12f), s * 0.8f); drops(Offset(center.x, center.y + s * 0.16f), s, false) }
            WeatherService.Companion.Kind.SNOW -> { cloud(Offset(center.x, center.y - s * 0.12f), s * 0.8f); drops(Offset(center.x, center.y + s * 0.16f), s, true) }
            WeatherService.Companion.Kind.STORM -> {
                cloud(Offset(center.x, center.y - s * 0.12f), s * 0.8f)
                drawLine(accent, Offset(center.x, center.y + s * 0.08f), Offset(center.x - s * 0.08f, center.y + s * 0.26f), strokeWidth = s * 0.06f)
                drawLine(accent, Offset(center.x - s * 0.08f, center.y + s * 0.26f), Offset(center.x + s * 0.04f, center.y + s * 0.26f), strokeWidth = s * 0.06f)
            }
        }
    }
}

@Composable
fun BoxWithConstraintsScope.WeatherWidget(instance: WidgetInstance, preview: Boolean) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val w by host.weather.collectAsState()
    if (!preview) LaunchedEffect(Unit) { host.refreshWeather(false) }
    if (w.temperature == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            WeatherGlyph(2, true, Modifier.size(28.dp), look.text, look.accent)
            Text(if (w.error != null) w.error!! else "Wetter", style = look.title, color = look.text, maxLines = 1)
            Text("Tippen, um den Ort festzulegen", style = look.body, color = look.secondary, maxLines = 2)
        }
        return
    }
    val temp = formatTemp(w.temperature, host.temperatureUnit)
    if (look.compact || H < 100.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(w.weatherCode, w.isDay, Modifier.size(min(H * 0.8f, 40.dp)), look.text, look.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (look.dots) DotMatrixText(temp.removeSuffix("C").removeSuffix("F"), dotSize = 2.6.dp, color = look.text)
                else Text(temp, style = look.title, color = look.text, maxLines = 1)
                Text(WeatherService.describe(w.weatherCode), style = look.body, color = look.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (look.dots) FittedDots(temp.removeSuffix("C").removeSuffix("F"), W * 0.6f, H * 0.26f, look.text)
                else Text(temp, style = look.big, color = look.text, maxLines = 1)
                Text(WeatherService.describe(w.weatherCode), style = look.body, color = look.secondary, maxLines = 1)
            }
            WeatherGlyph(w.weatherCode, w.isDay, Modifier.size(40.dp), look.text, look.accent)
        }
        Spacer(Modifier.weight(1f))
        if (look.detailed && w.hourly.isNotEmpty() && W > 200.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                w.hourly.take(5).forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Instant.ofEpochMilli(h.epochMs).atZone(ZoneId.systemDefault()).hour.toString(), style = look.body.copy(fontSize = look.body.fontSize * 0.85f), color = look.secondary)
                        WeatherGlyph(h.code, true, Modifier.size(18.dp), look.text, look.accent)
                        Text("${h.temperature.roundToInt()}°", style = look.body, color = look.text)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            listOfNotNull(w.place.takeIf { it.isNotBlank() }, "H ${w.high?.roundToInt() ?: "–"}° T ${w.low?.roundToInt() ?: "–"}°").joinToString(" · "),
            style = look.body, color = look.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================================ Kalender

@Composable
private fun rememberEvents(days: Int, limit: Int): List<CalendarEvent> {
    val host = LocalWidgetHost.current ?: return emptyList()
    val now by rememberNow()
    val access = host.calendar.hasAccess()
    return produceState(emptyList<CalendarEvent>(), now / 900_000, access) {
        val start = System.currentTimeMillis()
        value = host.calendar.events(start - 3_600_000, start + days * 86_400_000L, limit).filter { it.endMs > start }
    }.value
}

@Composable
fun BoxWithConstraintsScope.CalendarWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val now by rememberNow()
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val first = today.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1 // Montag = 0
    val days = today.lengthOfMonth()
    val showEvents = W > 260.dp && host.calendar.hasAccess()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(today.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.uppercase() }, style = look.title, color = look.text, modifier = Modifier.weight(1f), maxLines = 1)
                if (!host.calendar.hasAccess() && W > 150.dp) Text("Termine: Freigabe", style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = look.accent,
                    modifier = Modifier.clickable { host.actions.requestRequirement(WidgetRequirement.CALENDAR) })
            }
            Spacer(Modifier.height(4.dp))
            val labels = if (germanLike) listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") else listOf("M", "T", "W", "T", "F", "S", "S")
            val cellStyle = look.body.copy(fontSize = (look.body.fontSize.value * 0.82f).sp, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it.take(2), style = cellStyle, color = look.secondary, modifier = Modifier.weight(1f), maxLines = 1) } }
            val rows = (offset + days + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    for (cIdx in 0 until 7) {
                        val day = r * 7 + cIdx - offset + 1
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = day in 1..days) {
                            host.calendar.openDay(today.withDayOfMonth(day.coerceIn(1, days)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        }, contentAlignment = Alignment.Center) {
                            if (day in 1..days) {
                                val isToday = day == today.dayOfMonth
                                if (isToday) Box(Modifier.size(22.dp).clip(CircleShape).background(look.accent))
                                Text("$day", style = cellStyle, color = if (isToday) Nova.colors.onAccent else look.text, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (showEvents) {
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(0.9f)) {
                Text(if (germanLike) "Termine" else "Events", style = look.title, color = look.text)
                EventList(rememberEvents(7, look.maxItems))
            }
        }
    }
}

@Composable
private fun EventList(events: List<CalendarEvent>) {
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val use24 = host.use24h || DateFormat.is24HourFormat(LocalContext.current)
    if (events.isEmpty()) { Text(if (germanLike) "Keine Termine" else "No events", style = look.body, color = look.secondary); return }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    events.take(look.maxItems).forEach { e ->
        val d = Instant.ofEpochMilli(e.beginMs).atZone(zone)
        val dayLabel = when (d.toLocalDate()) {
            today -> if (germanLike) "Heute" else "Today"
            today.plusDays(1) -> if (germanLike) "Morgen" else "Tomorrow"
            else -> formatDate(e.beginMs)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { host.calendar.openEvent(e.id) }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(if (e.color != 0) Color(e.color) else look.accent))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(e.title, style = look.body.copy(fontWeight = FontWeight.Medium), color = look.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$dayLabel · " + if (e.allDay) (if (germanLike) "Ganztägig" else "All day") else formatTime(e.beginMs, use24), style = look.body.copy(fontSize = look.body.fontSize * 0.85f), color = look.secondary, maxLines = 1)
            }
        }
    }
}

@Composable
fun BoxWithConstraintsScope.AgendaWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val look = LocalWidgetLook.current
    val now by rememberNow()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(now), style = look.title, color = look.text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        EventList(rememberEvents(14, look.maxItems))
    }
}

// ============================================================================ Akku / Speicher / RAM

@Composable
fun BoxWithConstraintsScope.BatteryWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val b by host.systemInfo.battery.collectAsState()
    if (look.compact) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (b.charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull, null, tint = if (b.level <= 15) look.accent else look.text)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${b.level} %", style = look.title, color = look.text)
                DotBar(b.level / 100f, Modifier.fillMaxWidth().height(8.dp), look.accent, look.text.copy(alpha = 0.14f))
            }
        }
        return
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
            DotRing(b.level / 100f, Modifier.fillMaxSize(), if (b.level <= 15) look.accent else look.accent, look.text.copy(alpha = 0.14f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (look.dots) DotMatrixText("${b.level}%", dotSize = 2.4.dp, color = look.text)
                else Text("${b.level}%", style = look.title, color = look.text)
                Text(if (b.charging) (if (germanLike) "Lädt" else "Charging") else (if (germanLike) "Akku" else "Battery"), style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = look.secondary)
            }
        }
        if (look.detailed) {
            Text(listOfNotNull(b.plugged.takeIf { it.isNotBlank() }, b.temperatureC?.let { "%.0f °C".format(it) }, b.health.takeIf { it.isNotBlank() }).joinToString(" · "), style = look.body, color = look.secondary, maxLines = 1)
        }
    }
}

@Composable
private fun BarStat(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, fraction: Float) {
    val look = LocalWidgetLook.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = look.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = look.body, color = look.secondary, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${(fraction * 100).roundToInt()} %", style = look.body, color = look.text)
        }
        Spacer(Modifier.height(6.dp))
        DotBar(fraction, Modifier.fillMaxWidth().height(10.dp), look.accent, look.text.copy(alpha = 0.14f), 24)
        Spacer(Modifier.height(4.dp))
        Text(value, style = look.body.copy(fontSize = look.body.fontSize * 0.85f), color = look.secondary, maxLines = 1)
    }
}

@Composable
fun BoxWithConstraintsScope.StorageWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val now by rememberNow()
    val s = remember(now / 300_000) { host.systemInfo.storage() }
    BarStat(Icons.Filled.SdStorage, if (germanLike) "Speicher" else "Storage", "${SystemInfo.formatBytes(s.freeBytes)} ${if (germanLike) "frei von" else "free of"} ${SystemInfo.formatBytes(s.totalBytes)}", s.usedFraction)
}

@Composable
fun BoxWithConstraintsScope.RamWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    var m by remember { mutableStateOf(host.systemInfo.memory()) }
    Ticker(15_000) { m = host.systemInfo.memory() }
    BarStat(Icons.Filled.Memory, "RAM", "${SystemInfo.formatBytes(m.availBytes)} ${if (germanLike) "verfügbar" else "available"}", m.usedFraction)
}

@Composable
fun BoxWithConstraintsScope.ScreenTimeWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val now by rememberNow()
    val st = produceState(host.systemInfo.screenTimeToday(), now / 300_000) { value = host.systemInfo.screenTimeToday() }.value
    if (st == null) {
        Centered { Text(if (germanLike) "Nutzungsdaten nicht freigegeben" else "Usage access missing", style = look.body, color = look.secondary, textAlign = TextAlign.Center) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(if (germanLike) "Bildschirmzeit heute" else "Screen time today", style = look.body, color = look.secondary)
        Text(SystemInfo.formatDuration(st.totalMs), style = look.title, color = look.text)
        Spacer(Modifier.height(6.dp))
        val max = st.topApps.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        st.topApps.take(look.maxItems.coerceAtMost(4)).forEach { (label, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = look.body.copy(fontSize = look.body.fontSize * 0.85f), color = look.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SystemInfo.formatDuration(ms), style = look.body.copy(fontSize = look.body.fontSize * 0.8f), color = look.secondary)
            }
            DotBar(ms / max.toFloat(), Modifier.fillMaxWidth().height(6.dp), look.accent, look.text.copy(alpha = 0.1f), 30)
        }
    }
}

@Composable
fun BoxWithConstraintsScope.SystemStatusWidget(instance: WidgetInstance) {
    val W = this.maxWidth
    val H = this.maxHeight
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val b by host.systemInfo.battery.collectAsState()
    val cs by host.controls.state.collectAsState()
    var mem by remember { mutableStateOf(host.systemInfo.memory()) }
    val now by rememberNow()
    val storage = remember(now / 300_000) { host.systemInfo.storage() }
    Ticker(20_000) { mem = host.systemInfo.memory(); host.controls.refresh() }
    @Composable
    fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, active: Boolean = true) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (active) look.text else look.secondary.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(value, style = look.body.copy(fontSize = look.body.fontSize * 0.85f), color = look.secondary, maxLines = 1)
        }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Item(if (b.charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull, "${b.level}%")
        Item(Icons.Filled.SdStorage, "${(storage.usedFraction * 100).roundToInt()}%")
        Item(Icons.Filled.Memory, "${(mem.usedFraction * 100).roundToInt()}%")
        Item(if (cs.wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff, if (cs.wifiConnected) "WLAN" else (if (germanLike) "Aus" else "Off"), cs.wifiConnected)
        Item(Icons.Filled.Bluetooth, if (cs.bluetoothOn) (if (germanLike) "An" else "On") else (if (germanLike) "Aus" else "Off"), cs.bluetoothOn)
    }
}
