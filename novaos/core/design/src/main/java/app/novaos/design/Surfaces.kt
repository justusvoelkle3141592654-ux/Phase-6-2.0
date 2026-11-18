package app.novaos.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.novaos.model.SurfaceBackground
import app.novaos.model.WidgetShape
import kotlin.math.roundToInt

/**
 * Weichgezeichneter Hintergrund für Glasflächen. Statt teurer Echtzeit-Unschärfe wird eine einmal
 * berechnete, verkleinerte und weichgezeichnete Kopie des Wallpapers passend zur Position gezeichnet.
 */
class Backdrop(val image: ImageBitmap?, val rootWidth: Int, val rootHeight: Int)

val LocalBackdrop = staticCompositionLocalOf { Backdrop(null, 1, 1) }

fun widgetShape(shape: WidgetShape, corner: Dp): Shape = when (shape) {
    WidgetShape.ROUNDED -> RoundedCornerShape(corner)
    WidgetShape.SQUARE -> RoundedCornerShape(2.dp)
    WidgetShape.PILL -> RoundedCornerShape(percent = 50)
    WidgetShape.CIRCLE -> RoundedCornerShape(percent = 50)
    WidgetShape.CUT -> CutCornerShape(corner * 0.6f)
}

private fun DrawScope.drawBackdrop(backdrop: Backdrop, pos: Offset) {
    val img = backdrop.image ?: return
    val sx = img.width / backdrop.rootWidth.toFloat()
    val sy = img.height / backdrop.rootHeight.toFloat()
    val src = IntOffset((pos.x * sx).roundToInt().coerceIn(0, img.width - 1), (pos.y * sy).roundToInt().coerceIn(0, img.height - 1))
    val srcSize = IntSize(
        (size.width * sx).roundToInt().coerceIn(1, img.width - src.x),
        (size.height * sy).roundToInt().coerceIn(1, img.height - src.y),
    )
    drawImage(img, srcOffset = src, srcSize = srcSize, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
}

/**
 * Universelle NovaOS-Oberfläche: Glas, Fläche, Verlauf, Punkte oder ohne Hintergrund.
 * @param transparency 0 = deckend, 1 = unsichtbar
 */
@Composable
fun NovaSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    background: SurfaceBackground = SurfaceBackground.GLASS,
    transparency: Float = 0.35f,
    color: Color = Nova.colors.surface,
    color2: Color? = null,
    borderWidth: Dp = 1.dp,
    borderColor: Color? = null,
    shadow: Float = 0.2f,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = Nova.style
    val glass = style.glass
    val c = style.colors
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val alpha = (1f - transparency).coerceIn(0f, 1f)
    val effectiveBg = if (background == SurfaceBackground.GLASS && !glass.enabled) SurfaceBackground.SOLID else background
    val elevation = if (style.performanceMode) 0.dp else (shadow.coerceIn(0f, 1f) * 14).dp

    var m = modifier
    if (elevation > 0.dp && effectiveBg != SurfaceBackground.NONE) m = m.shadow(elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
    m = m.clip(shape)
    m = when (effectiveBg) {
        SurfaceBackground.GLASS -> m
            .onGloballyPositioned { pos = it.positionInRoot() }
            .drawBehind {
                if (glass.blurDp > 0f) drawBackdrop(backdrop, pos)
                // Materialfarbe
                drawRect(color.copy(alpha = (glass.thickness * alpha).coerceIn(0f, 1f) * 0.9f + 0.04f))
                // Transparenter Verlauf
                if (glass.gradient > 0f) drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f * glass.gradient), Color.Transparent, c.shadow.copy(alpha = 0.16f * glass.gradient))),
                )
                // Lichtreflexion oben links und Glanz
                if (glass.reflection > 0f) drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.22f * glass.reflection), Color.Transparent),
                        start = Offset.Zero, end = Offset(size.width * 0.55f, size.height * 0.55f),
                    ),
                )
                if (glass.gloss > 0f) drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f * glass.gloss), Color.Transparent), startY = 0f, endY = size.height * 0.18f),
                )
            }
        SurfaceBackground.SOLID -> m.background(color.copy(alpha = alpha.coerceAtLeast(0.02f)))
        SurfaceBackground.GRADIENT -> m.background(
            Brush.linearGradient(listOf((color2 ?: c.gradientStart).copy(alpha = alpha), color.copy(alpha = alpha * 0.85f))),
        )
        SurfaceBackground.DOTS -> m.background(color.copy(alpha = alpha * 0.9f)).dotGrid(c.text.copy(alpha = 0.10f), 9.dp, 0.8.dp)
        SurfaceBackground.NONE -> m
    }
    val bw = if (effectiveBg == SurfaceBackground.NONE) 0.dp else borderWidth
    if (bw > 0.dp) {
        val bc = borderColor ?: if (effectiveBg == SurfaceBackground.GLASS) Color.White.copy(alpha = 0.08f + 0.3f * glass.border) else c.border
        m = m.border(bw, if (effectiveBg == SurfaceBackground.GLASS && borderColor == null) Brush.linearGradient(listOf(bc, bc.copy(alpha = bc.alpha * 0.25f))) else Brush.linearGradient(listOf(bc, bc)), shape)
    }
    Box(m, content = content)
}

/** Kurzform für Karten im Stil des aktuellen Themes. */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    background: SurfaceBackground = SurfaceBackground.GLASS,
    transparency: Float = 1f - Nova.colors.surfaceOpacity,
    content: @Composable BoxScope.() -> Unit,
) = NovaSurface(modifier, RoundedCornerShape(corner), background, transparency, content = content)
