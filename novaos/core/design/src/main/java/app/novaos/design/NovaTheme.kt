package app.novaos.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.novaos.model.AnimationSpec
import app.novaos.model.ColorPalette
import app.novaos.model.ColorUtil
import app.novaos.model.DotIntensity
import app.novaos.model.GlassSpec
import app.novaos.model.TextStyleSpec
import app.novaos.model.ThemeMode
import app.novaos.model.ThemeSpec
import app.novaos.model.TypographySpec

fun hexColor(hex: String?, fallback: Color): Color = ColorUtil.parseHex(hex)?.let { Color(it.toInt()) } ?: fallback

fun Color.toHex(): String {
    val argb = (alpha * 255).toInt() shl 24 or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()
    return ColorUtil.toHex(argb.toLong() and 0xFFFFFFFFL)
}

@Immutable
data class NovaColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val icon: Color,
    val widget: Color,
    val border: Color,
    val shadow: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val useGradient: Boolean,
    val surfaceOpacity: Float,
    val isDark: Boolean,
) {
    val onAccent: Color get() = if (ColorUtil.contrastRatio(accent.argbLong(), 0xFFFFFFFF) >= 3.0) Color.White else Color.Black
}

fun Color.argbLong(): Long =
    ((alpha * 255).toLong() shl 24) or ((red * 255).toLong() shl 16) or ((green * 255).toLong() shl 8) or (blue * 255).toLong()

@Immutable
data class NovaTypography(
    val clock: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val widget: TextStyle,
    val appLabel: TextStyle,
    val caption: TextStyle,
    val spec: TypographySpec,
)

/** Aufgelöstes Theme für die Darstellung (Farben nach Modus, Schriften, Glas, Animation). */
@Immutable
data class NovaStyle(
    val theme: ThemeSpec,
    val colors: NovaColors,
    val type: NovaTypography,
    val glass: GlassSpec,
    val motion: AnimationSpec,
    val dots: DotIntensity,
    val performanceMode: Boolean,
) {
    /** Punktmatrix für eine Stufe aktiv? */
    fun dotsAtLeast(level: DotIntensity) = dots.ordinal >= level.ordinal
}

val LocalNova = staticCompositionLocalOf<NovaStyle> { error("NovaTheme fehlt") }

object Nova {
    val style: NovaStyle @Composable get() = LocalNova.current
    val colors: NovaColors @Composable get() = LocalNova.current.colors
    val type: NovaTypography @Composable get() = LocalNova.current.type
}

fun resolveColors(p: ColorPalette, dark: Boolean, dynamicAccent: Color?): NovaColors {
    val bg = hexColor(p.background, if (dark) Color.Black else Color.White)
    fun fg(hex: String, fallback: Color): Color {
        val c = hexColor(hex, fallback)
        val adjusted = ColorUtil.adjustContrast(c.argbLong(), bg.argbLong(), p.contrast)
        return Color(adjusted.toInt())
    }
    return NovaColors(
        primary = fg(p.primary, Color.White),
        secondary = hexColor(p.secondary, Color.Gray),
        accent = dynamicAccent ?: hexColor(p.accent, Color.Red),
        background = bg,
        surface = hexColor(p.surface, Color.DarkGray),
        text = fg(p.text, Color.White),
        textSecondary = fg(p.textSecondary, Color.Gray),
        icon = fg(p.icon, Color.White),
        widget = hexColor(p.widget, Color.DarkGray),
        border = hexColor(p.border, Color.White.copy(alpha = 0.2f)),
        shadow = hexColor(p.shadow, Color.Black),
        gradientStart = hexColor(p.gradientStart, Color.DarkGray),
        gradientEnd = hexColor(p.gradientEnd, Color.Black),
        useGradient = p.useGradient,
        surfaceOpacity = p.surfaceOpacity.coerceIn(0f, 1f),
        isDark = dark,
    )
}

fun TextStyleSpec.toTextStyle(defaultColor: Color): TextStyle = TextStyle(
    fontFamily = NovaFonts.family(font),
    fontSize = sizeSp.sp,
    fontWeight = FontWeight(weight.coerceIn(100, 900)),
    letterSpacing = letterSpacing.em,
    lineHeight = (sizeSp * lineHeight).sp,
    color = hexColor(color, defaultColor).let { it.copy(alpha = it.alpha * alpha.coerceIn(0f, 1f)) },
)

fun resolveStyle(theme: ThemeSpec, systemDark: Boolean, performanceMode: Boolean, dynamicAccent: Color?): NovaStyle {
    val dark = when (theme.colors.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val accent = if (theme.colors.dynamicFromWallpaper) dynamicAccent else null
    val colors = resolveColors(if (dark) theme.colors.dark else theme.colors.light, dark, accent)
    val t = theme.typography
    val type = NovaTypography(
        clock = t.clock.toTextStyle(colors.text),
        headline = t.headline.toTextStyle(colors.text),
        body = t.body.toTextStyle(colors.text),
        widget = t.widget.toTextStyle(colors.text),
        appLabel = t.appLabel.toTextStyle(colors.text),
        caption = t.body.copy(sizeSp = t.body.sizeSp * 0.82f).toTextStyle(colors.textSecondary),
        spec = t,
    )
    val glass = if (performanceMode) theme.glass.copy(blurDp = 0f, reflection = 0f, gloss = 0f, depth = 0f, shadow = 0f) else theme.glass
    val motion = if (performanceMode) theme.animation.copy(style = app.novaos.model.AnimationStyle.MINIMAL, durationMs = 140, backgroundAnimation = false) else theme.animation
    return NovaStyle(theme, colors, type, glass, motion, t.dotIntensity, performanceMode)
}

@Composable
fun NovaTheme(
    theme: ThemeSpec,
    performanceMode: Boolean = false,
    dynamicAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val style = remember(theme, systemDark, performanceMode, dynamicAccent) { resolveStyle(theme, systemDark, performanceMode, dynamicAccent) }
    val c = style.colors
    val scheme = if (c.isDark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, secondary = c.secondary, background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary, outline = c.border,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, secondary = c.secondary, background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary, outline = c.border,
    )
    CompositionLocalProvider(LocalNova provides style) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
