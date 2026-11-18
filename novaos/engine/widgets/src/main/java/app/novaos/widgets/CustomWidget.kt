package app.novaos.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.novaos.design.DotBar
import app.novaos.design.NovaIcons
import app.novaos.design.hexColor
import app.novaos.model.CustomWidgetDef
import app.novaos.model.DataField
import app.novaos.model.ElementKind
import app.novaos.model.WidgetElement
import app.novaos.model.WidgetInstance
import app.novaos.system.MediaHub
import app.novaos.system.SystemInfo
import app.novaos.system.WeatherService
import kotlin.math.roundToInt
import app.novaos.model.TextAlign as NovaAlign

@Composable
fun BoxWithConstraintsScope.CustomWidgetRenderer(instance: WidgetInstance, preview: Boolean) {
    val host = LocalWidgetHost.current ?: return
    val look = LocalWidgetLook.current
    val def = host.customWidgets.firstOrNull { it.id == instance.config["customId"] }
    if (def == null) {
        Centered { Text(if (germanLike) "Eigenes Widget nicht gefunden" else "Custom widget missing", style = look.body, color = look.secondary) }
        return
    }
    CustomWidgetCanvas(def, preview)
}

/** Zeichnet die Elemente eines Widget-Creator-Entwurfs (auch für die Live-Vorschau im Creator). */
@Composable
fun CustomWidgetCanvas(def: CustomWidgetDef, preview: Boolean, overlay: @Composable BoxWithConstraintsScope.(WidgetElement) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        def.elements.forEach { e ->
            Box(
                Modifier
                    .offset(maxWidth * e.x, maxHeight * e.y)
                    .size(maxWidth * e.w, maxHeight * e.h),
            ) {
                ElementView(e, preview)
            }
            overlay(e)
        }
    }
}

@Composable
fun dataText(field: DataField, preview: Boolean): String {
    val host = LocalWidgetHost.current ?: return field.sample
    val now = rememberNow().value
    val ctx = LocalContext.current
    return when (field) {
        DataField.TIME -> formatTime(now, host.use24h || DateFormat.is24HourFormat(ctx))
        DataField.DATE -> formatDate(now)
        DataField.WEEKDAY -> java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).dayOfWeek
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }
        DataField.BATTERY -> "${host.systemInfo.battery.collectAsState().value.level} %"
        DataField.STORAGE_FREE -> SystemInfo.formatBytes(host.systemInfo.storage().freeBytes) + if (germanLike) " frei" else " free"
        DataField.RAM_FREE -> SystemInfo.formatBytes(host.systemInfo.memory().availBytes) + if (germanLike) " frei" else " free"
        DataField.WEATHER_TEMP -> formatTemp(host.weather.collectAsState().value.temperature, host.temperatureUnit)
        DataField.WEATHER_DESC -> WeatherService.describe(host.weather.collectAsState().value.weatherCode)
        DataField.NEXT_EVENT -> if (preview) field.sample else "–"
        DataField.OPEN_TASKS -> "${host.productivity.collectAsState().value.openTasks().size} ${if (germanLike) "offen" else "open"}"
        DataField.MEDIA_TITLE -> MediaHub.state.collectAsState().value?.title ?: if (preview) field.sample else "–"
        DataField.MEDIA_ARTIST -> MediaHub.state.collectAsState().value?.artist ?: if (preview) field.sample else "–"
        DataField.DAY_PROGRESS -> "${(progressValue(app.novaos.model.ProgressSource.DAY) * 100).roundToInt()} %"
        DataField.YEAR_PROGRESS -> "${(progressValue(app.novaos.model.ProgressSource.YEAR) * 100).roundToInt()} %"
    }
}

@Composable
private fun ElementView(e: WidgetElement, preview: Boolean) {
    val host = LocalWidgetHost.current
    val look = LocalWidgetLook.current
    val color = hexColor(e.color, look.text)
    val align = when (e.align) { NovaAlign.START -> TextAlign.Start; NovaAlign.CENTER -> TextAlign.Center; NovaAlign.END -> TextAlign.End }
    val boxAlign = when (e.align) { NovaAlign.START -> Alignment.CenterStart; NovaAlign.CENTER -> Alignment.Center; NovaAlign.END -> Alignment.CenterEnd }
    val click = if (!preview && e.action != null && host != null) Modifier.clickable { host.actions.openTarget(e.action!!) } else Modifier
    Box(Modifier.fillMaxSize().alpha(e.alpha.coerceIn(0f, 1f)).then(click), contentAlignment = boxAlign) {
        when (e.kind) {
            ElementKind.TEXT -> Text(e.text, style = look.body.copy(fontSize = e.sizeSp.sp, fontWeight = FontWeight(e.weight), textAlign = align), color = color, overflow = TextOverflow.Ellipsis)
            ElementKind.DATA -> Text(dataText(e.data, preview), style = look.body.copy(fontSize = e.sizeSp.sp, fontWeight = FontWeight(e.weight), textAlign = align), color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ElementKind.DOT_TEXT -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = boxAlign) {
                val t = if (e.text.isBlank()) dataText(e.data, preview) else e.text
                FittedDots(t.uppercase(), maxWidth, maxHeight, color)
            }
            ElementKind.ICON -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = boxAlign) {
                Icon(NovaIcons.get(e.icon), e.icon, tint = color, modifier = Modifier.size(if (maxWidth < maxHeight) maxWidth else maxHeight))
            }
            ElementKind.PROGRESS -> DotBar(progressValue(e.progress), Modifier.fillMaxSize(), hexColor(e.color, look.accent), look.text.copy(alpha = 0.14f), 16)
            ElementKind.SHAPE -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.cornerDp.coerceAtLeast(0f).let { androidx.compose.ui.unit.Dp(it) })).background(hexColor(e.color, look.accent)))
        }
    }
}
