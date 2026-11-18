package app.novaos.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.novaos.MainActivity
import app.novaos.data.RenderedWallpaper
import app.novaos.design.Backdrop
import app.novaos.design.LocalBackdrop
import app.novaos.design.Nova
import app.novaos.design.NovaTheme
import app.novaos.design.dotGrid
import app.novaos.model.BackgroundEffect
import app.novaos.model.Profile
import app.novaos.model.WallpaperSource
import app.novaos.model.WidgetRequirement
import app.novaos.system.HomeRole
import app.novaos.system.NotificationHub
import app.novaos.system.hasPermission
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.setup.SetupAssistant
import app.novaos.widgets.LocalWidgetHost
import app.novaos.widgets.WidgetHost
import kotlinx.coroutines.launch

@Composable
fun LauncherRoot(vm: LauncherViewModel, actions: LauncherActions, widgetHost: NovaWidgetHost) {
    val cfg by vm.config.collectAsState()
    val config = cfg ?: run { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    val profile = config.activeProfile
    val container = LocalContainer.current
    val activity = LocalContext.current as MainActivity

    var currentPage by remember { mutableIntStateOf(profile.layout.defaultPage) }
    // Zurück auf dem Startbildschirm beendet den Launcher nicht (Overlays registrieren eigene Handler).
    BackHandler(enabled = true) { }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        val source = container.wallpapers.sourceFor(profile.wallpaper, currentPage)
        val rendered = produceState<RenderedWallpaper?>(null, profile.wallpaper, source, wPx, hPx) {
            value = vm.renderWallpaper(profile.wallpaper, source, wPx, hPx)
        }.value
        val systemWallpaper = source is WallpaperSource.System
        LaunchedEffect(systemWallpaper) { activity.setShowSystemWallpaper(systemWallpaper) }
        val dynamicAccent = remember(rendered, systemWallpaper) {
            (if (systemWallpaper) container.wallpapers.systemAccent() else rendered?.accent)?.let { Color(it) }
        }
        val sharp = remember(rendered) { rendered?.sharp?.asImageBitmap() }
        val blurred = remember(rendered) { rendered?.blurred?.asImageBitmap() }

        NovaTheme(profile.theme, config.general.performanceMode, dynamicAccent) {
            val host = app.novaos.ui.common.rememberNovaWidgetHost(profile, actions)
            CompositionLocalProvider(
                LocalConfig provides config,
                LocalProfile provides profile,
                LocalWidgetHost provides host,
                LocalBackdrop provides Backdrop(blurred, wPx, hPx),
            ) {
                if (!config.setupCompleted) {
                    WallpaperLayer(sharp, profile)
                    SetupAssistant(onFinish = { container.controller.completeSetup() }, requestHomeRole = { activity.requestHomeRole() })
                } else {
                    LauncherContent(vm, actions, widgetHost, sharp, profile, currentPage) { currentPage = it }
                }
            }
        }
    }
}

@Composable
fun WallpaperLayer(image: ImageBitmap?, profile: Profile) {
    val c = Nova.colors
    Box(Modifier.fillMaxSize()) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        when (profile.theme.home.backgroundEffect) {
            BackgroundEffect.NONE -> Unit
            BackgroundEffect.DOT_GRID -> Box(Modifier.fillMaxSize().dotGrid(c.text.copy(alpha = 0.05f), 18.dp, 0.9.dp))
            BackgroundEffect.VIGNETTE -> Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), radius = 1600f)))
            BackgroundEffect.SCANLINES -> Canvas(Modifier.fillMaxSize()) {
                var y = 0f
                val step = 4.dp.toPx()
                while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            }
        }
    }
}

@Composable
private fun LauncherContent(
    vm: LauncherViewModel,
    actions: LauncherActions,
    widgetHost: NovaWidgetHost,
    wallpaper: ImageBitmap?,
    profile: Profile,
    currentPage: Int,
    onPage: (Int) -> Unit,
) {
    val overlay by vm.overlay.collectAsState()
    val editMode by vm.editMode.collectAsState()
    val homePressed by vm.homePressed.collectAsState()
    val style = Nova.style

    BackHandler(enabled = overlay != Overlay.None || editMode) {
        if (overlay != Overlay.None) vm.close() else vm.setEdit(false)
    }

    Box(
        Modifier.fillMaxSize().launcherGestures(
            enabled = overlay == Overlay.None && !editMode,
            atFirstPage = { currentPage == 0 && !profile.theme.home.loopPages },
            atLastPage = { currentPage == profile.layout.pages.size - 1 && !profile.theme.home.loopPages },
        ) { type -> actions.perform(profile.gestures[type] ?: app.novaos.model.GestureAction.None) },
    ) {
        WallpaperLayer(wallpaper, profile)
        HomeScreen(vm, actions, widgetHost, profile, editMode, homePressed, onPage)

        // Abdunklung hinter Overlays
        AnimatedVisibility(overlay != Overlay.None, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.glass.dim.coerceIn(0f, 0.9f)))
                    .clickable(remember { MutableInteractionSource() }, null) { vm.close() },
            )
        }
        Overlays(vm, actions, widgetHost, overlay, currentPage)
    }
}
