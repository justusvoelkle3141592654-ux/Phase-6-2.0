package app.novaos.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novaos.model.DotFont
import app.novaos.model.DotIntensity
import app.novaos.model.FontChoice
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Text in der eigenen NovaOS-Punktmatrix (5 × 7). [dimColor] zeichnet die nicht leuchtenden Punkte
 * schwach mit – wie bei einer echten Punktanzeige.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dotSize: Dp = 4.dp,
    color: Color = Nova.colors.text,
    dimColor: Color = Color.Transparent,
    charSpacing: Int = 1,
    pitchFactor: Float = 1.32f,
) {
    val chars = remember(text) { text.toCharArray() }
    val density = LocalDensity.current
    val pitchPx = with(density) { dotSize.toPx() } * pitchFactor
    val widthDots = DotFont.widthInDots(text, charSpacing).coerceAtLeast(1)
    val w = with(density) { (widthDots * pitchPx).toDp() }
    val h = with(density) { (DotFont.HEIGHT * pitchPx).toDp() }
    Canvas(modifier.size(w, h)) {
        val r = dotSize.toPx() / 2f
        var x0 = 0f
        for (c in chars) {
            val g = DotFont.glyph(c)
            for (row in 0 until DotFont.HEIGHT) for (col in 0 until DotFont.WIDTH) {
                val on = g[row * DotFont.WIDTH + col]
                val cc = if (on) color else dimColor
                if (cc.alpha > 0f) drawCircle(cc, r, Offset(x0 + col * pitchPx + pitchPx / 2, row * pitchPx + pitchPx / 2))
            }
            x0 += (DotFont.WIDTH + charSpacing) * pitchPx
        }
    }
}

/**
 * Zeigt Text entweder in Punktmatrix oder in normaler Schrift – abhängig von der Punktmatrix-Stufe des Themes.
 * [minLevel] = ab welcher Stufe dieser Text als Punkte erscheint.
 */
@Composable
fun NovaText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minLevel: DotIntensity = DotIntensity.BALANCED,
    dotHeight: Dp? = null,
    color: Color = style.color,
    maxLines: Int = 1,
    forceDots: Boolean? = null,
) {
    val s = Nova.style
    val useDots = forceDots ?: (s.dotsAtLeast(minLevel) && s.dots != DotIntensity.OFF)
    if (useDots && s.type.spec.dotFont == FontChoice.NOVA_DOT && text.length <= 24) {
        val fontPx = style.fontSize.value
        val dot = dotHeight ?: (fontPx * 0.105f).dp
        DotMatrixText(text.uppercase(), modifier, dot, color, color.copy(alpha = if (s.dotsAtLeast(DotIntensity.STRONG)) 0.07f else 0f))
    } else {
        val st = if (useDots) style.copy(fontFamily = NovaFonts.doto) else style
        Text(text, modifier, color = color, style = st, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Dezentes Punktraster als Hintergrund. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 14.dp, radius: Dp = 0.9.dp): Modifier = drawBehind {
    val step = spacing.toPx()
    val r = radius.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

/** Fortschritt als Punktring (0..1). */
@Composable
fun DotRing(progress: Float, modifier: Modifier = Modifier, color: Color = Nova.colors.accent, track: Color = Nova.colors.text.copy(alpha = 0.14f), dots: Int = 36) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val dotR = radius * 0.075f
        val ring = radius - dotR
        val lit = (progress.coerceIn(0f, 1f) * dots).toInt()
        for (i in 0 until dots) {
            val a = -PI / 2 + 2 * PI * i / dots
            drawCircle(
                if (i < lit) color else track,
                dotR,
                Offset(center.x + (cos(a) * ring).toFloat(), center.y + (sin(a) * ring).toFloat()),
            )
        }
    }
}

/** Fortschritt als Punktreihe. */
@Composable
fun DotBar(progress: Float, modifier: Modifier = Modifier, color: Color = Nova.colors.accent, track: Color = Nova.colors.text.copy(alpha = 0.14f), count: Int = 20) {
    Canvas(modifier) {
        val step = size.width / count
        val r = minOf(step * 0.34f, size.height / 2f)
        val lit = (progress.coerceIn(0f, 1f) * count + 0.001f).toInt()
        for (i in 0 until count) drawCircle(if (i < lit) color else track, r, Offset(step * (i + 0.5f), size.height / 2f))
    }
}
