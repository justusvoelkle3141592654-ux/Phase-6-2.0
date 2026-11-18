package app.novaos.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novaos.model.ColorUtil
import app.novaos.model.SurfaceBackground
import kotlin.math.roundToInt

/** Abschnittsüberschrift mit rotem Punkt (wie im Designkonzept). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Nova.colors.accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = Nova.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), color = Nova.colors.textSecondary)
    }
}

@Composable
fun NovaTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            NovaIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onBack)
        } else Spacer(Modifier.width(12.dp))
        Spacer(Modifier.width(4.dp))
        NovaText(title, Nova.type.headline, Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun NovaIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Nova.colors.text, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = Nova.colors
    val bg = if (primary) c.accent else c.text.copy(alpha = 0.08f)
    val fg = if (primary) c.onAccent else c.text
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.3f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.5f), style = Nova.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun NovaChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Nova.colors
    val bg by animateColorAsState(if (selected) c.text else c.text.copy(alpha = 0.07f), label = "chip")
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(bg)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp)
            .semantics { stateDescription = if (selected) "ausgewählt" else "" },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) c.background else c.text, style = Nova.type.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1) }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> NovaChip(label(o), o == selected, { onSelect(o) }) }
    }
}

/** Schalter im Punkt-Stil. */
@Composable
fun NovaSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Nova.colors
    val t by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    val track by animateColorAsState(if (checked) c.accent else c.text.copy(alpha = 0.16f), label = "track")
    Box(
        modifier.size(52.dp, 30.dp).clip(RoundedCornerShape(15.dp)).background(track.copy(alpha = if (enabled) track.alpha else 0.3f))
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "an" else "aus" },
    ) {
        Canvas(Modifier.size(52.dp, 30.dp)) {
            val r = size.height * 0.36f
            val x = size.height / 2 + t * (size.width - size.height)
            drawCircle(if (checked) c.onAccent else c.text, r, Offset(x, size.height / 2))
            if (!checked) for (i in 0..2) drawCircle(c.text.copy(alpha = 0.25f), 1.4.dp.toPx(), Offset(size.width - size.height * 0.45f - i * 5.dp.toPx(), size.height / 2))
        }
    }
}

/** Schieberegler mit Punktspur. */
@Composable
fun NovaSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onChangeFinished: (() -> Unit)? = null,
    description: String = "",
) {
    val c = Nova.colors
    val latest by rememberUpdatedState(onChange)
    var widthPx by remember { mutableStateOf(1f) }
    fun valueAt(x: Float): Float {
        var f = (x / widthPx).coerceIn(0f, 1f)
        if (steps > 0) f = (f * (steps + 1)).roundToInt() / (steps + 1).toFloat()
        return range.start + f * (range.endInclusive - range.start)
    }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .semantics { contentDescription = description; stateDescription = "%.0f %%".format(frac * 100) }
            .pointerInput(range, steps) {
                widthPx = size.width.toFloat()
                detectTapGestures { latest(valueAt(it.x)); onChangeFinished?.invoke() }
            }
            .pointerInput(range, steps) {
                widthPx = size.width.toFloat()
                detectDragGestures(onDragEnd = { onChangeFinished?.invoke() }) { ch, _ -> latest(valueAt(ch.position.x)) }
            },
    ) {
        val cy = size.height / 2
        val count = 28
        val step = size.width / count
        for (i in 0 until count) {
            val x = step * (i + 0.5f)
            val on = x <= frac * size.width
            drawCircle(if (on) c.accent else c.text.copy(alpha = 0.18f), if (on) 2.6.dp.toPx() else 2.dp.toPx(), Offset(x, cy))
        }
        drawCircle(c.text, 9.dp.toPx(), Offset((frac * size.width).coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), cy))
        drawCircle(c.accent, 3.5.dp.toPx(), Offset((frac * size.width).coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), cy))
    }
}

/** Zeile in Einstellungen mit Titel, Beschreibung und optionalem Steuerelement. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Nova.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.text.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Nova.type.body.copy(fontWeight = FontWeight.Medium), color = c.text)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = Nova.type.caption, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() }
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) =
    SettingRow(title, subtitle, onClick = { if (enabled) onChange(!checked) }, trailing = { NovaSwitch(checked, onChange, enabled = enabled) })

@Composable
fun SliderRow(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    format: (Float) -> String = { "${(it * 100).roundToInt()} %" },
    steps: Int = 0,
    subtitle: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Nova.type.body.copy(fontWeight = FontWeight.Medium), color = Nova.colors.text, modifier = Modifier.weight(1f))
            Text(format(value), style = Nova.type.caption, color = Nova.colors.textSecondary)
        }
        if (subtitle != null) Text(subtitle, style = Nova.type.caption, color = Nova.colors.textSecondary)
        NovaSlider(value, onChange, range = range, steps = steps, description = title)
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(title, style = Nova.type.body.copy(fontWeight = FontWeight.Medium), color = Nova.colors.text)
        if (subtitle != null) Text(subtitle, style = Nova.type.caption, color = Nova.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        ChipRow(options, selected, label, onSelect)
    }
}

/** Gruppierte Einstellungen auf einer Karte. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    NovaSurface(modifier.fillMaxWidth(), RoundedCornerShape(24.dp), SurfaceBackground.SOLID, transparency = 0.45f, color = Nova.colors.surface, shadow = 0f) {
        Column(Modifier.padding(6.dp), content = content)
    }
}

/** Farbauswahl: Farbpunkte + HEX-Eingabe. */
@Composable
fun ColorPickerRow(
    title: String,
    hex: String?,
    onChange: (String) -> Unit,
    swatches: List<String> = DefaultSwatches,
    allowNone: Boolean = false,
    onNone: (() -> Unit)? = null,
) {
    val c = Nova.colors
    var editing by remember(hex) { mutableStateOf(hex ?: "") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Nova.type.body.copy(fontWeight = FontWeight.Medium), color = c.text, modifier = Modifier.weight(1f))
            Box(Modifier.size(22.dp).clip(CircleShape).background(hexColor(hex, Color.Transparent)).border(1.dp, c.border, CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (allowNone) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, if (hex == null) c.accent else c.border, CircleShape).clickable { onNone?.invoke() },
                    contentAlignment = Alignment.Center,
                ) { Text("Auto", style = Nova.type.caption.copy(fontSize = 8.sp), color = c.text) }
            }
            swatches.forEach { s ->
                val selected = hex != null && ColorUtil.parseHex(s) == ColorUtil.parseHex(hex)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(hexColor(s, Color.Gray))
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) c.accent else c.border, CircleShape)
                        .clickable(onClickLabel = s) { onChange(s) },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Filled.Check, null, tint = hexColor(ColorUtil.toHex(ColorUtil.readableOn(ColorUtil.parseHex(s)!!)), Color.White), modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HEX", style = Nova.type.caption, color = c.textSecondary)
            Spacer(Modifier.width(10.dp))
            NovaTextField(
                editing,
                { v ->
                    editing = v.take(9)
                    if (ColorUtil.isValidHex(editing) && editing.removePrefix("#").length in setOf(6, 8)) onChange(ColorUtil.toHex(ColorUtil.parseHex(editing)!!))
                },
                Modifier.width(150.dp),
                placeholder = "#RRGGBB",
                capitalization = KeyboardCapitalization.Characters,
                isError = editing.isNotEmpty() && !ColorUtil.isValidHex(editing),
            )
        }
    }
}

val DefaultSwatches = listOf(
    "#FFFFFF", "#F2F2F2", "#9A9AA0", "#3A3A3F", "#16161A", "#000000",
    "#E5202E", "#FF2D3A", "#FF7A1A", "#F5C518", "#C6FF3D", "#3DDC84",
    "#2EC4B6", "#7FB2FF", "#2F6FE0", "#7C4DFF", "#E040FB", "#C9A55C",
)

@Composable
fun NovaTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    isError: Boolean = false,
    minLines: Int = 1,
) {
    val c = Nova.colors
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(c.text.copy(alpha = 0.07f))
            .border(1.dp, if (isError) c.accent else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = Nova.type.body, color = c.textSecondary.copy(alpha = 0.7f))
        BasicTextField(
            value, onChange,
            textStyle = Nova.type.body.copy(color = c.text),
            singleLine = singleLine,
            minLines = minLines,
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = capitalization),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Auswahl aus Vorschaukarten (z. B. Stile mit Vorschau). */
@Composable
fun <T> PreviewChooser(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 120.dp,
    preview: @Composable (T) -> Unit,
) {
    val c = Nova.colors
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Column(
                Modifier.width(itemWidth).clip(RoundedCornerShape(20.dp))
                    .border(if (sel) 2.dp else 1.dp, if (sel) c.accent else c.border, RoundedCornerShape(20.dp))
                    .clickable(role = Role.RadioButton) { onSelect(o) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                preview(o)
                Spacer(Modifier.height(6.dp))
                Text(label(o), style = Nova.type.caption.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
