package app.novaos.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novaos.AppContainer
import app.novaos.design.Nova
import app.novaos.design.argbLong
import app.novaos.model.IconStyleSpec
import app.novaos.model.NovaConfig
import app.novaos.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Einfache Zweisprachigkeit (Deutsch/Englisch) nach Systemsprache. */
fun tr(de: String, en: String): String = if (Locale.getDefault().language == "de") de else en

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer fehlt") }
val LocalConfig = staticCompositionLocalOf<NovaConfig> { error("Konfiguration fehlt") }
val LocalProfile = staticCompositionLocalOf<Profile> { error("Profil fehlt") }

fun Drawable.toBitmap(sizePx: Int): Bitmap {
    val b = Bitmap.createBitmap(sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(b))
    return b
}

/**
 * App-Icon im aktuellen Icon-Stil (Profil + individuelle Überschreibung).
 * Wird im Hintergrund gerendert und zwischengespeichert.
 */
@Composable
fun AppIconImage(key: String, size: Dp, modifier: Modifier = Modifier, styleOverride: IconStyleSpec? = null, label: String? = null) {
    val container = LocalContainer.current
    val profile = LocalProfile.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val override = profile.iconOverrides[key]
    val style = styleOverride ?: override?.style ?: profile.theme.icons
    val fg = Nova.colors.icon.argbLong().toInt()
    val bg = Nova.colors.surface.argbLong().toInt()
    val dpi = (density.density * 160).toInt()
    val bmp = produceState<ImageBitmap?>(null, key, style, px, fg, bg, override?.customIconFile) {
        value = withContext(Dispatchers.Default) {
            val d = container.apps.loadIcon(key, dpi)
            container.icons.render(key, d, style, px, fg, bg, override?.customIconFile).asImageBitmap()
        }
    }.value
    if (bmp != null) Image(bmp, label, modifier.size(size))
    else Box(modifier.size(size).clip(CircleShape).background(Nova.colors.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        Text((label ?: container.apps.find(key)?.label ?: "?").take(1), color = Nova.colors.textSecondary)
    }
}

@Composable
fun DrawableImage(drawable: Drawable?, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp = produceState<ImageBitmap?>(null, drawable, px) {
        value = drawable?.let { d -> withContext(Dispatchers.Default) { d.toBitmap(px).asImageBitmap() } }
    }.value
    if (bmp != null) Image(bmp, null, modifier.size(size)) else Box(modifier.size(size))
}

val SmallGap = 8.dp
