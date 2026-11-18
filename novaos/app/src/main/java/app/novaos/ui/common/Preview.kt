package app.novaos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novaos.design.Backdrop
import app.novaos.design.LocalBackdrop
import app.novaos.design.Nova
import app.novaos.design.NovaSurface
import app.novaos.design.NovaTheme
import app.novaos.design.dotGrid
import app.novaos.model.BackgroundEffect
import app.novaos.model.DockPosition
import app.novaos.model.DockShape
import app.novaos.model.HomeItem
import app.novaos.model.Profile
import app.novaos.model.SurfaceBackground
import app.novaos.model.WallpaperSource
import app.novaos.widgets.LocalWidgetHost
import app.novaos.widgets.NovaWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Echte Live-Vorschau des Homescreens: nutzt dieselben Widgets, Icons und Hintergründe wie der Launcher,
 * nur verkleinert. Änderungen in Studios und im Einrichtungsassistenten sind sofort sichtbar.
 */
@Composable
fun MiniHome(profile: Profile, modifier: Modifier = Modifier, width: Dp = 180.dp, performanceMode: Boolean = false) {
    val c = LocalContainer.current
    val wpx = 360
    val hpx = 780
    val rendered = produceState<Pair<ImageBitmap, ImageBitmap>?>(null, profile.wallpaper) {
        value = withContext(Dispatchers.Default) {
            val src = profile.wallpaper.source
            if (src is WallpaperSource.System) null else c.wallpapers.render(profile.wallpaper, src, wpx, hpx)?.let { it.sharp.asImageBitmap() to it.blurred.asImageBitmap() }
        }
    }.value
    NovaTheme(profile.theme, performanceMode) {
        val scale = width / 360.dp
        Box(
            modifier.size(width, width * (780f / 360f)).clip(RoundedCornerShape(width * 0.09f))
                .border(1.dp, Nova.colors.text.copy(alpha = 0.2f), RoundedCornerShape(width * 0.09f))
                .background(Nova.colors.background),
        ) {
            if (rendered != null) Image(rendered.first, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (profile.theme.home.backgroundEffect == BackgroundEffect.DOT_GRID) Box(Modifier.fillMaxSize().dotGrid(Nova.colors.text.copy(alpha = 0.05f), 18.dp * scale, 0.9.dp * scale))
            CompositionLocalProvider(LocalBackdrop provides Backdrop(rendered?.second, 1, 1), LocalProfile provides profile) {
                // Inhalt wird in Originalgröße (360 dp breit) gezeichnet und skaliert.
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(360.dp, 780.dp)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) { PreviewContent(profile) }
            }
        }
    }
}

@Composable
private fun PreviewContent(profile: Profile) {
    val layout = profile.layout
    val page = layout.pages.getOrNull(layout.defaultPage) ?: layout.pages.firstOrNull()
    val dock = profile.theme.dock
    Column(Modifier.fillMaxSize().padding(top = 28.dp, bottom = 12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = layout.marginHorizontalDp.dp)) {
            val cw = maxWidth / layout.columns
            val ch = maxHeight / layout.rows
            page?.items?.forEach { item ->
                val p = if (layout.freeLayout) item.pos.freeOrFromGrid(layout.columns, layout.rows) else item.pos
                val x = if (layout.freeLayout) maxWidth * p.fx else cw * p.x
                val y = if (layout.freeLayout) maxHeight * p.fy else ch * p.y
                val w = if (layout.freeLayout) maxWidth * p.fw else cw * p.w
                val h = if (layout.freeLayout) maxHeight * p.fh else ch * p.h
                Box(Modifier.offset(x, y).size(w, h).padding(if (item is HomeItem.Widget) (layout.widgetSpacingDp / 2).dp else 0.dp)) {
                    when (item) {
                        is HomeItem.Widget -> if (LocalWidgetHost.current != null) NovaWidget(item.widget, item.id, Modifier.fillMaxSize(), preview = true)
                        else NovaSurface(Modifier.fillMaxSize()) { Text(item.widget.type.label, color = Nova.colors.text) }
                        is HomeItem.App -> PreviewIcon(item.appKey, layout.iconSizeDp.dp, layout.showLabels)
                        is HomeItem.Folder -> PreviewIcon(item.apps.firstOrNull() ?: "", layout.iconSizeDp.dp, layout.showLabels, item.name)
                        is HomeItem.Shortcut -> PreviewIcon("", layout.iconSizeDp.dp, layout.showLabels, item.label)
                        is HomeItem.AndroidWidget -> NovaSurface(Modifier.fillMaxSize()) { Text("Android-Widget", color = Nova.colors.textSecondary) }
                    }
                }
            }
        }
        if (dock.position != DockPosition.HIDDEN && layout.dockApps.isNotEmpty()) {
            val shape = when (dock.shape) { DockShape.PILL -> RoundedCornerShape(50); DockShape.SQUARE -> RoundedCornerShape(6.dp); else -> RoundedCornerShape(28.dp) }
            NovaSurface(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(dock.heightDp.dp), shape,
                if (dock.shape == DockShape.NONE) SurfaceBackground.NONE else if (dock.shape == DockShape.DOTS) SurfaceBackground.DOTS else dock.background, dock.transparency,
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    layout.dockApps.forEach { AppIconImage(it, (dock.heightDp - 22).dp.coerceAtMost(layout.iconSizeDp.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PreviewIcon(key: String, size: Dp, label: Boolean, name: String? = null) {
    val c = LocalContainer.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (key.isNotEmpty()) AppIconImage(key, size * 0.85f) else Box(Modifier.size(size * 0.85f).clip(RoundedCornerShape(16.dp)).background(Nova.colors.text.copy(alpha = 0.1f)))
        if (label) {
            Spacer(Modifier.height(3.dp))
            Text(name ?: c.apps.find(key)?.label ?: "", style = Nova.type.appLabel.copy(fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
