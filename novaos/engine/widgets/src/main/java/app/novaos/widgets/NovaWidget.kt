package app.novaos.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novaos.design.Nova
import app.novaos.design.NovaFonts
import app.novaos.design.NovaSurface
import app.novaos.design.hexColor
import app.novaos.design.widgetShape
import app.novaos.model.InfoDensity
import app.novaos.model.WidgetAnimation
import app.novaos.model.WidgetInstance
import app.novaos.model.WidgetLayoutVariant
import app.novaos.model.WidgetRequirement
import app.novaos.model.WidgetStyle
import app.novaos.model.WidgetTap
import app.novaos.model.WidgetType

/** Aufgelöste Gestaltung eines Widgets für die Inhalte. */
data class WidgetLook(
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val body: TextStyle,
    val title: TextStyle,
    val big: TextStyle,
    val dots: Boolean,
    val layout: WidgetLayoutVariant,
    val density: InfoDensity,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    val compact: Boolean get() = layout == WidgetLayoutVariant.COMPACT || heightDp < 90.dp
    val detailed: Boolean get() = layout == WidgetLayoutVariant.DETAILED
    val maxItems: Int get() = when (density) { InfoDensity.LOW -> 2; InfoDensity.MEDIUM -> 4; InfoDensity.HIGH -> 7 }
}

val LocalWidgetLook = staticCompositionLocalOf<WidgetLook> { error("WidgetLook fehlt") }

@Composable
fun rememberLook(style: WidgetStyle, w: Dp, h: Dp): WidgetLook {
    val c = Nova.colors
    val t = Nova.type
    val text = hexColor(style.textColor, c.text)
    val family = style.font?.let { NovaFonts.family(it) }
    val body = t.widget.copy(color = text, fontSize = t.widget.fontSize * style.textScale).let { if (family != null) it.copy(fontFamily = family) else it }
    return WidgetLook(
        text = text,
        secondary = text.copy(alpha = 0.62f),
        accent = hexColor(style.accentColor, c.accent),
        body = body,
        title = body.copy(fontSize = body.fontSize * 1.2f, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        big = body.copy(fontSize = body.fontSize * 2.4f, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        dots = style.dotMatrix && Nova.style.dots != app.novaos.model.DotIntensity.OFF,
        layout = style.layout,
        density = style.density,
        widthDp = w,
        heightDp = h,
    )
}

/**
 * Rendert ein Widget der Nova Widget Engine mit Rahmen (Form, Hintergrund, Rahmen, Schatten) und Inhalt.
 * @param onEdit wird bei langem Drücken bzw. Tipp-Aktion "Bearbeiten" aufgerufen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovaWidget(
    instance: WidgetInstance,
    itemId: String,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    preview: Boolean = false,
) {
    val host = LocalWidgetHost.current
    val style = instance.style
    val c = Nova.colors
    val appear = remember(instance.id) { Animatable(if (style.animation == WidgetAnimation.NONE || preview) 1f else 0f) }
    val motion = Nova.style.motion
    LaunchedEffect(instance.id, style.animation) {
        if (style.animation != WidgetAnimation.NONE && !preview) appear.animateTo(1f, app.novaos.design.NovaMotion.spec(motion, app.novaos.model.AnimArea.WIDGET))
    }
    val a = appear.value
    val animMod = when (style.animation) {
        WidgetAnimation.NONE -> Modifier
        WidgetAnimation.FADE -> Modifier.alpha(a)
        WidgetAnimation.SCALE -> Modifier.graphicsLayer { scaleX = 0.9f + 0.1f * a; scaleY = 0.9f + 0.1f * a; alpha = a }
        WidgetAnimation.SLIDE -> Modifier.graphicsLayer { translationY = (1f - a) * 40f; alpha = a }
        WidgetAnimation.DOTS -> Modifier.graphicsLayer { alpha = if (a < 1f) (a * 6).toInt() / 6f else 1f }
    }
    val tap: (() -> Unit)? = when {
        editMode || preview -> null
        style.tap == WidgetTap.NONE -> null
        style.tap == WidgetTap.EDIT -> ({ host?.actions?.editWidget(itemId) })
        style.tap == WidgetTap.OPEN_TARGET && style.tapTarget != null -> ({ host?.actions?.openTarget(style.tapTarget!!) })
        else -> defaultTap(instance, host)
    }
    val gesture = if (onLongPress != null || tap != null) Modifier.combinedClickable(
        onClick = { tap?.invoke() },
        onLongClick = onLongPress,
        onClickLabel = instance.type.label,
    ) else Modifier

    NovaSurface(
        modifier = modifier.then(animMod).then(gesture),
        shape = widgetShape(style.shape, style.cornerDp.dp),
        background = style.background,
        transparency = style.transparency,
        color = hexColor(style.backgroundColor, c.widget),
        color2 = style.backgroundColor2?.let { hexColor(it, c.widget) },
        borderWidth = style.borderWidth.dp,
        borderColor = style.borderColor?.let { hexColor(it, c.border) },
        shadow = style.shadow,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(style.paddingDp.dp)) {
            val look = rememberLook(style, maxWidth, maxHeight)
            CompositionLocalProvider(LocalWidgetLook provides look) {
                if (host == null) {
                    Text(instance.type.label, style = look.body, color = look.text)
                } else {
                    val req = instance.type.needs
                    val missing = req != WidgetRequirement.NONE && req != WidgetRequirement.NETWORK && !host.hasRequirement(req) && !requirementOptional(instance.type)
                    if (missing && !preview) RequirementHint(instance.type, req) { host.actions.requestRequirement(req) }
                    else WidgetContent(instance, itemId, preview)
                }
            }
        }
    }
}

/** Widgets, die auch ohne Freigabe sinnvoll etwas anzeigen. */
private fun requirementOptional(t: WidgetType) = t == WidgetType.CALENDAR || t == WidgetType.APP_SHORTCUTS

private fun defaultTap(instance: WidgetInstance, host: WidgetHost?): (() -> Unit)? {
    host ?: return null
    return when (instance.type) {
        WidgetType.CLOCK -> ({ host.actions.openClock() })
        WidgetType.WEATHER -> ({ host.actions.openWeatherSettings() })
        WidgetType.TASKS, WidgetType.PROJECTS, WidgetType.PROGRESS -> ({ host.actions.openProductivity("tasks") })
        WidgetType.NOTES -> ({ host.actions.openProductivity("notes") })
        WidgetType.REMINDERS -> ({ host.actions.openProductivity("reminders") })
        WidgetType.MUSIC, WidgetType.ALBUM_ART, WidgetType.PLAYLIST -> ({ host.actions.openMediaPlayer() })
        WidgetType.BATTERY -> ({ host.controls.toggle(app.novaos.model.CcTileType.BATTERY)?.let { host.apps.startIntent(it) } })
        WidgetType.STORAGE -> ({ host.apps.startIntent(android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) })
        WidgetType.APP_INFO -> instance.config["app"]?.let { k -> { host.actions.openAppDetails(k) } }
        WidgetType.CUSTOM -> null
        else -> null
    }
}

@Composable
private fun RequirementHint(type: WidgetType, req: WidgetRequirement, onClick: () -> Unit) {
    val look = LocalWidgetLook.current
    Column(
        Modifier.fillMaxSize().clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.LockOpen, null, tint = look.accent)
        Text(type.label, style = look.title, color = look.text, textAlign = TextAlign.Center, maxLines = 1)
        Text("Tippen: ${req.label} erlauben", style = look.body, color = look.secondary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun BoxWithConstraintsScope.WidgetContent(instance: WidgetInstance, itemId: String, preview: Boolean) {
    when (instance.type) {
        WidgetType.CLOCK -> ClockWidget(instance)
        WidgetType.WEATHER -> WeatherWidget(instance, preview)
        WidgetType.CALENDAR -> CalendarWidget(instance)
        WidgetType.AGENDA -> AgendaWidget(instance)
        WidgetType.BATTERY -> BatteryWidget(instance)
        WidgetType.STORAGE -> StorageWidget(instance)
        WidgetType.RAM -> RamWidget(instance)
        WidgetType.SCREEN_TIME -> ScreenTimeWidget(instance)
        WidgetType.SYSTEM_STATUS -> SystemStatusWidget(instance)
        WidgetType.MESSAGES -> MessagesWidget(instance)
        WidgetType.COMMUNICATION -> CommunicationWidget(instance)
        WidgetType.FAVORITE_CONTACTS -> ContactsWidget(instance, calls = false)
        WidgetType.CALL_SHORTCUTS -> ContactsWidget(instance, calls = true)
        WidgetType.TASKS -> TasksWidget(instance)
        WidgetType.PROJECTS -> ProjectsWidget(instance)
        WidgetType.NOTES -> NotesWidget(instance)
        WidgetType.REMINDERS -> RemindersWidget(instance)
        WidgetType.PROGRESS -> ProgressWidget(instance)
        WidgetType.MUSIC -> MusicWidget(instance)
        WidgetType.ALBUM_ART -> AlbumArtWidget(instance)
        WidgetType.PLAYLIST -> PlaylistWidget(instance)
        WidgetType.MEDIA_LINKS -> MediaLinksWidget(instance)
        WidgetType.APP_SHORTCUTS -> AppShortcutsWidget(instance)
        WidgetType.FAVORITE_APPS -> FavoriteAppsWidget(instance)
        WidgetType.APP_INFO -> AppInfoWidget(instance)
        WidgetType.APP_LINKS -> AppLinksWidget(instance)
        WidgetType.CUSTOM -> CustomWidgetRenderer(instance, preview)
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
