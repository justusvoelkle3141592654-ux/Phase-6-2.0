package app.novaos.ui.launcher

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import app.novaos.design.DotMatrixText
import app.novaos.design.Nova
import app.novaos.design.NovaIcons
import app.novaos.design.NovaSurface
import app.novaos.design.NovaText
import app.novaos.model.DockPosition
import app.novaos.model.DockShape
import app.novaos.model.DotIntensity
import app.novaos.model.FolderPreview
import app.novaos.model.GestureAction
import app.novaos.model.GestureType
import app.novaos.model.HomeItem
import app.novaos.model.HomeLayout
import app.novaos.model.IndicatorStyle
import app.novaos.model.NovaArea
import app.novaos.model.PageTransition
import app.novaos.model.Profile
import app.novaos.model.ScrollBehavior
import app.novaos.model.ShortcutTarget
import app.novaos.model.SurfaceBackground
import app.novaos.system.NotificationHub
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.tr
import app.novaos.widgets.NovaWidget
import app.novaos.widgets.formatDate
import app.novaos.widgets.rememberNow
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val LOOP_FACTOR = 1000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    actions: LauncherActions,
    widgetHost: NovaWidgetHost,
    profile: Profile,
    editMode: Boolean,
    homePressed: Int,
    onPage: (Int) -> Unit,
) {
    val layout = profile.layout
    val home = profile.theme.home
    val pageCount = layout.pages.size
    val loop = home.loopPages && pageCount > 1 && !editMode
    val virtualCount = if (loop) pageCount * LOOP_FACTOR else pageCount
    val startPage = if (loop) pageCount * (LOOP_FACTOR / 2) + layout.defaultPage else layout.defaultPage.coerceIn(0, pageCount - 1)
    val pager = rememberPagerState(initialPage = startPage) { virtualCount }
    val realPage = pager.currentPage % pageCount.coerceAtLeast(1)

    LaunchedEffect(pager, pageCount) { snapshotFlow { pager.currentPage % pageCount.coerceAtLeast(1) }.collect(onPage) }
    LaunchedEffect(homePressed) { if (homePressed > 0) pager.animateScrollToPage(if (loop) pager.currentPage - realPage + layout.defaultPage else layout.defaultPage) }
    LaunchedEffect(pageCount) { if (pager.currentPage >= virtualCount) pager.scrollToPage(virtualCount - 1) }

    val dock = profile.theme.dock
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        if (home.showStatusLine && !editMode) StatusLine(home.statusShowDate, home.statusShowBattery)
        if (dock.position == DockPosition.TOP) Dock(vm, actions, profile, editMode)
        if (editMode) EditToolbar(vm, actions, realPage, pageCount)
        val fling = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = if (home.scroll == ScrollBehavior.SNAP) 0.2f else 0.5f)
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
            flingBehavior = fling,
            key = { it },
        ) { virtual ->
            val index = virtual % pageCount.coerceAtLeast(1)
            val page = layout.pages.getOrNull(index) ?: return@HorizontalPager
            Box(Modifier.fillMaxSize().pageTransition(pager, virtual, home.pageTransition)) {
                PageContent(vm, actions, widgetHost, profile, index, page.items, editMode)
            }
        }
        PageIndicator(home.indicator, pageCount, realPage)
        if (dock.position == DockPosition.BOTTOM) Dock(vm, actions, profile, editMode)
        Spacer(Modifier.height(6.dp))
    }
}

/** Seitenübergänge des Homescreens. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pageTransition(pager: PagerState, page: Int, t: PageTransition): Modifier = graphicsLayer {
    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
    val abs = offset.absoluteValue.coerceIn(0f, 1f)
    when (t) {
        PageTransition.SLIDE -> Unit
        PageTransition.FADE -> { translationX = offset * size.width; alpha = 1f - abs }
        PageTransition.SCALE -> { val s = 1f - 0.15f * abs; scaleX = s; scaleY = s; alpha = 1f - abs * 0.5f }
        PageTransition.DEPTH -> if (offset > 0) { translationX = offset * size.width; val s = 1f - 0.25f * abs; scaleX = s; scaleY = s; alpha = 1f - abs }
        PageTransition.CUBE -> { cameraDistance = 12f * density; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (offset < 0) 0f else 1f, 0.5f); rotationY = -offset * 70f }
        PageTransition.DOTS -> { alpha = ((1f - abs) * 5).toInt() / 5f; val s = 1f - 0.05f * abs; scaleX = s; scaleY = s }
    }
}

@Composable
private fun StatusLine(showDate: Boolean, showBattery: Boolean) {
    val c = LocalContainer.current
    val battery by c.systemInfo.battery.collectAsState()
    val now by rememberNow()
    val style = Nova.style
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showDate) Text(formatDate(now), style = Nova.type.caption, color = Nova.colors.text.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        if (showBattery) {
            if (style.dotsAtLeast(DotIntensity.STRONG)) DotMatrixText("${battery.level}%", dotSize = 1.6.dp, color = if (battery.level <= 15) Nova.colors.accent else Nova.colors.text.copy(alpha = 0.8f))
            else Text("${battery.level} %", style = Nova.type.caption, color = Nova.colors.text.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun EditToolbar(vm: LauncherViewModel, actions: LauncherActions, page: Int, pageCount: Int) {
    val c = LocalContainer.current
    NovaSurface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), RoundedCornerShape(26.dp), SurfaceBackground.GLASS, 0.2f) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ToolbarButton(Icons.Filled.Widgets, tr("Widgets", "Widgets")) { vm.open(Overlay.WidgetPicker(page)) }
            ToolbarButton(Icons.Filled.Add, tr("Seite +", "Page +")) { c.controller.addPage() }
            ToolbarButton(Icons.Filled.Delete, tr("Seite −", "Page −"), enabled = pageCount > 1) { c.controller.removePage(page) }
            ToolbarButton(Icons.Filled.Wallpaper, tr("Hintergrund", "Wallpaper")) { actions.openSettings("wallpaper") }
            ToolbarButton(Icons.Filled.Palette, tr("Design", "Design")) { actions.openSettings("personalization") }
            ToolbarButton(Icons.Filled.Check, tr("Fertig", "Done"), primary = true) { vm.setEdit(false) }
        }
    }
}

@Composable
private fun ToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val c = Nova.colors
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (primary) c.accent else c.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) c.onAccent else c.text.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(18.dp))
        }
        Text(label, style = Nova.type.caption, color = c.text.copy(alpha = if (enabled) 0.9f else 0.3f), maxLines = 1)
    }
}

@Composable
private fun PageIndicator(style: IndicatorStyle, count: Int, current: Int) {
    if (count <= 1 || style == IndicatorStyle.NONE) { Spacer(Modifier.height(10.dp)); return }
    val c = Nova.colors
    Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        when (style) {
            IndicatorStyle.DOTS -> repeat(count) { i ->
                Box(Modifier.padding(horizontal = 3.dp).size(if (i == current) 7.dp else 5.dp).clip(CircleShape).background(if (i == current) c.text else c.text.copy(alpha = 0.35f)))
            }
            IndicatorStyle.LINE -> repeat(count) { i ->
                Box(Modifier.padding(horizontal = 2.dp).size(if (i == current) 22.dp else 10.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == current) c.text else c.text.copy(alpha = 0.3f)))
            }
            IndicatorStyle.DOT_NUMBERS -> {
                DotMatrixText("${current + 1}/$count", dotSize = 1.5.dp, color = c.text.copy(alpha = 0.85f))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(c.accent))
            }
            IndicatorStyle.NONE -> Unit
        }
    }
}

// ============================================================================ Seiteninhalt

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageContent(
    vm: LauncherViewModel,
    actions: LauncherActions,
    widgetHost: NovaWidgetHost,
    profile: Profile,
    pageIndex: Int,
    items: List<HomeItem>,
    editMode: Boolean,
) {
    val layout = profile.layout
    val gestures = profile.gestures
    val actionsLatest by rememberUpdatedState(actions)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = layout.marginHorizontalDp.dp, vertical = layout.marginVerticalDp.dp)) {
        val pageW = maxWidth
        val pageH = maxHeight
        val cellW = pageW / layout.columns
        val cellH = pageH / layout.rows
        // Freie Fläche: Doppeltippen / langes Drücken (Gesten) bzw. Bearbeiten
        Box(
            Modifier.fillMaxSize().pointerInput(editMode, gestures) {
                detectTapGestures(
                    onDoubleTap = { if (!editMode) actionsLatest.perform(gestures[GestureType.DOUBLE_TAP] ?: GestureAction.None) },
                    onLongPress = { if (!editMode) actionsLatest.perform(gestures[GestureType.LONG_PRESS] ?: GestureAction.OpenQuickMenu) },
                )
            },
        )
        if (editMode) GridGuide(layout, cellW, cellH)
        items.forEach { item ->
            key(item.id) {
                HomeItemBox(vm, actions, widgetHost, layout, pageIndex, item, cellW, cellH, pageW, pageH, editMode)
            }
        }
    }
}

@Composable
private inline fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

@Composable
private fun GridGuide(layout: HomeLayout, cellW: Dp, cellH: Dp) {
    val c = Nova.colors
    if (layout.freeLayout) {
        Box(Modifier.fillMaxSize().border(1.dp, c.text.copy(alpha = 0.15f), RoundedCornerShape(18.dp)))
        return
    }
    for (y in 0 until layout.rows) for (x in 0 until layout.columns) {
        Box(Modifier.offset(cellW * x, cellH * y).size(cellW, cellH).padding(4.dp).border(1.dp, c.text.copy(alpha = 0.10f), RoundedCornerShape(12.dp)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeItemBox(
    vm: LauncherViewModel,
    actions: LauncherActions,
    widgetHost: NovaWidgetHost,
    layout: HomeLayout,
    pageIndex: Int,
    item: HomeItem,
    cellW: Dp,
    cellH: Dp,
    pageW: Dp,
    pageH: Dp,
    editMode: Boolean,
) {
    val c = LocalContainer.current
    val density = LocalDensity.current
    val free = layout.freeLayout
    val pos = if (free) item.pos.freeOrFromGrid(layout.columns, layout.rows) else item.pos
    val x = if (free) pageW * pos.fx else cellW * pos.x
    val y = if (free) pageH * pos.fy else cellH * pos.y
    val w = if (free) pageW * pos.fw else cellW * pos.w
    val h = if (free) pageH * pos.fh else cellH * pos.h
    var drag by remember(item.id, item.pos) { mutableStateOf(Offset.Zero) }
    var resize by remember(item.id, item.pos) { mutableStateOf(Offset.Zero) }
    val dragging = drag != Offset.Zero
    val pad = if (item is HomeItem.Widget || item is HomeItem.AndroidWidget) (layout.widgetSpacingDp / 2).dp else 0.dp
    val wPx = with(density) { w.toPx() }
    val hPx = with(density) { h.toPx() }
    val cellWPx = with(density) { cellW.toPx() }
    val cellHPx = with(density) { cellH.toPx() }
    val pageWPx = with(density) { pageW.toPx() }
    val pageHPx = with(density) { pageH.toPx() }

    val editModifier = if (editMode) Modifier.pointerInput(item.id, item.pos, free) {
        detectDragGestures(
            onDragEnd = {
                val d = drag
                drag = Offset.Zero
                if (free) {
                    c.controller.moveFree(item.id, pos.fx + d.x / pageWPx, pos.fy + d.y / pageHPx)
                } else {
                    val centerX = (pos.x * cellWPx + wPx / 2 + d.x)
                    val targetPage = when {
                        centerX < -cellWPx * 0.2f && pageIndex > 0 -> pageIndex - 1
                        centerX > pageWPx + cellWPx * 0.2f && pageIndex < layout.pages.size - 1 -> pageIndex + 1
                        else -> pageIndex
                    }
                    val nx = ((pos.x * cellWPx + d.x) / cellWPx).roundToInt()
                    val ny = ((pos.y * cellHPx + d.y) / cellHPx).roundToInt()
                    c.controller.moveItem(item.id, pageIndex, nx, ny, targetPage)
                }
            },
            onDragCancel = { drag = Offset.Zero },
        ) { ch, amount -> ch.consume(); drag += amount }
    } else Modifier

    Box(
        Modifier
            .offset { IntOffset((x.toPx() + drag.x).roundToInt(), (y.toPx() + drag.y).roundToInt()) }
            .size(w + with(density) { resize.x.toDp() }, h + with(density) { resize.y.toDp() })
            .zIndex(if (dragging) 10f else 0f)
            .graphicsLayer { if (dragging) { scaleX = 1.04f; scaleY = 1.04f; alpha = 0.92f } }
            .padding(pad)
            .then(editModifier),
    ) {
        when (item) {
            is HomeItem.App -> AppCell(vm, actions, item.appKey, layout, editMode, homeItemId = item.id)
            is HomeItem.Folder -> FolderCell(vm, item, layout, editMode)
            is HomeItem.Shortcut -> ShortcutCell(vm, actions, item, layout, editMode)
            is HomeItem.Widget -> NovaWidget(item.widget, item.id, Modifier.fillMaxSize(), editMode, onLongPress = if (editMode) null else ({ vm.open(Overlay.ItemMenu(item.id)) }))
            is HomeItem.AndroidWidget -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
                AndroidWidgetView(widgetHost, item.appWidgetId)
                if (editMode) Box(Modifier.fillMaxSize().background(Color.Transparent))
            }
        }
        if (editMode) {
            Box(Modifier.fillMaxSize().border(1.5.dp, Nova.colors.accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp)))
            // Menü (Entfernen/Bearbeiten)
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).size(26.dp).clip(CircleShape).background(Nova.colors.text)
                    .combinedClickable(onClickLabel = tr("Optionen", "Options")) { vm.open(Overlay.ItemMenu(item.id)) },
                contentAlignment = Alignment.Center,
            ) { Text("⋯", color = Nova.colors.background) }
            val resizable = item is HomeItem.Widget || item is HomeItem.AndroidWidget || free
            if (resizable) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(2.dp).size(28.dp).clip(CircleShape).background(Nova.colors.accent)
                        .pointerInput(item.id, item.pos) {
                            detectDragGestures(
                                onDragEnd = {
                                    val r = resize
                                    resize = Offset.Zero
                                    if (free) c.controller.resizeFree(item.id, pos.fw + r.x / pageWPx, pos.fh + r.y / pageHPx)
                                    else c.controller.resizeItem(item.id, ((wPx + r.x) / cellWPx).roundToInt().coerceAtLeast(1), ((hPx + r.y) / cellHPx).roundToInt().coerceAtLeast(1))
                                },
                                onDragCancel = { resize = Offset.Zero },
                            ) { ch, amount -> ch.consume(); resize += amount }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.OpenInFull, tr("Größe ändern", "Resize"), tint = Nova.colors.onAccent, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}

/** Gemeinsame Darstellung für App, Ordner, Verknüpfung: Symbol + Name. */
@Composable
fun IconWithLabel(
    label: String,
    layout: HomeLayout,
    showLabel: Boolean = layout.showLabels,
    badge: Int = 0,
    iconSize: Dp = layout.iconSizeDp.dp,
    icon: @Composable (Dp) -> Unit,
) {
    val style = Nova.style
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = maxWidth
        val labelH = if (showLabel) 18.dp else 0.dp
        val size = min(iconSize, min(maxWidth - 8.dp, maxHeight - labelH - 6.dp)).coerceAtLeast(20.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                icon(size)
                if (badge > 0 && LocalConfig.current.general.showNotificationBadges) {
                    Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-2).dp).size(16.dp).clip(CircleShape).background(Nova.colors.accent), contentAlignment = Alignment.Center) {
                        Text(if (badge > 9) "9+" else "$badge", style = Nova.type.caption.copy(fontSize = Nova.type.caption.fontSize * 0.75f), color = Nova.colors.onAccent)
                    }
                }
            }
            if (showLabel) {
                Spacer(Modifier.height(4.dp))
                if (style.theme.typography.dotAppLabels && style.dotsAtLeast(DotIntensity.STRONG) && label.length <= 10) {
                    DotMatrixText(label.uppercase(), dotSize = 1.25.dp, color = Nova.type.appLabel.color)
                } else {
                    Text(label, style = Nova.type.appLabel.copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(boxW))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    vm: LauncherViewModel,
    actions: LauncherActions,
    key: String,
    layout: HomeLayout,
    editMode: Boolean,
    homeItemId: String? = null,
    fromDock: Boolean = false,
    showLabel: Boolean = layout.showLabels,
) {
    val c = LocalContainer.current
    val profile = app.novaos.ui.common.LocalProfile.current
    val app = c.apps.find(key)
    val badges by NotificationHub.badges.collectAsState()
    val view = LocalView.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val label = profile.iconOverrides[key]?.label ?: app?.label ?: tr("Nicht installiert", "Not installed")
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { val b = it.boundsInWindow(); bounds = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()) }
            .then(
                if (editMode) Modifier else Modifier.combinedClickable(
                    onClickLabel = label,
                    onClick = { actions.launchApp(key, bounds, view) },
                    onLongClick = { vm.open(Overlay.AppMenu(key, homeItemId, fromDock)) },
                ),
            ),
    ) {
        IconWithLabel(label, layout, showLabel, badges[app?.packageName] ?: 0) { s -> AppIconImage(key, s, label = label) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCell(vm: LauncherViewModel, folder: HomeItem.Folder, layout: HomeLayout, editMode: Boolean) {
    val fs = Nova.style.theme.home.folder
    Box(
        Modifier.fillMaxSize().then(
            if (editMode) Modifier else Modifier.combinedClickable(
                onClickLabel = folder.name,
                onClick = { vm.open(Overlay.Folder(folder.id)) },
                onLongClick = { vm.open(Overlay.ItemMenu(folder.id)) },
            ),
        ),
    ) {
        IconWithLabel(folder.name, layout) { s -> FolderIcon(folder.apps, s, fs.preview, fs.shape, fs.background) }
    }
}

@Composable
fun FolderIcon(apps: List<String>, size: Dp, preview: FolderPreview, shape: app.novaos.model.IconShape, background: SurfaceBackground) {
    val c = Nova.colors
    val sh = if (shape == app.novaos.model.IconShape.CIRCLE || shape == app.novaos.model.IconShape.DOT_RING) CircleShape else RoundedCornerShape(size * 0.28f)
    NovaSurface(Modifier.size(size), sh, background, 0.35f, shadow = 0.1f) {
        when (preview) {
            FolderPreview.DOT -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DotMatrixText("${apps.size}", dotSize = size / 18, color = c.text)
            }
            FolderPreview.STACK -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                apps.take(3).reversed().forEachIndexed { i, k ->
                    Box(Modifier.offset((i * 4 - 4).dp, (i * 4 - 4).dp)) { AppIconImage(k, size * 0.55f) }
                }
            }
            FolderPreview.GRID_2, FolderPreview.GRID_3 -> {
                val n = if (preview == FolderPreview.GRID_2) 2 else 3
                Column(Modifier.fillMaxSize().padding(size * 0.12f), verticalArrangement = Arrangement.SpaceEvenly) {
                    apps.take(n * n).chunked(n).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { AppIconImage(it, size * (if (n == 2) 0.32f else 0.22f)) }
                            repeat(n - row.size) { Spacer(Modifier.size(size * (if (n == 2) 0.32f else 0.22f))) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCell(vm: LauncherViewModel, actions: LauncherActions, item: HomeItem.Shortcut, layout: HomeLayout, editMode: Boolean) {
    val c = Nova.colors
    Box(
        Modifier.fillMaxSize().then(
            if (editMode) Modifier else Modifier.combinedClickable(onClick = { actions.openTarget(item.target) }, onLongClick = { vm.open(Overlay.ItemMenu(item.id)) }),
        ),
    ) {
        IconWithLabel(item.label, layout) { s ->
            val t = item.target
            if (t is ShortcutTarget.App) AppIconImage(t.appKey, s)
            else Box(Modifier.size(s).clip(CircleShape).background(c.text.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(
                    when (t) {
                        is ShortcutTarget.Nova -> when (t.area) {
                            NovaArea.SETTINGS -> NovaIcons.get("settings")
                            NovaArea.SEARCH -> NovaIcons.get("search")
                            else -> NovaIcons.get("bolt")
                        }
                        is ShortcutTarget.Url -> NovaIcons.get("navigation")
                        else -> NovaIcons.get("star")
                    },
                    null, tint = c.text, modifier = Modifier.size(s * 0.5f),
                )
            }
        }
    }
}

// ============================================================================ Dock

@Composable
private fun Dock(vm: LauncherViewModel, actions: LauncherActions, profile: Profile, editMode: Boolean) {
    val d = profile.theme.dock
    val apps = profile.layout.dockApps
    if (apps.isEmpty() && !editMode) return
    val shape = when (d.shape) {
        DockShape.PILL -> RoundedCornerShape(percent = 50)
        DockShape.ROUNDED -> RoundedCornerShape(28.dp)
        DockShape.SQUARE -> RoundedCornerShape(6.dp)
        DockShape.DOTS, DockShape.NONE -> RoundedCornerShape(28.dp)
    }
    val bg = if (d.shape == DockShape.NONE) SurfaceBackground.NONE else if (d.shape == DockShape.DOTS) SurfaceBackground.DOTS else d.background
    val layout = profile.layout
    NovaSurface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(d.heightDp.dp),
        shape, bg, d.transparency, shadow = 0.25f,
        borderColor = if (d.shape == DockShape.DOTS) Nova.colors.text.copy(alpha = 0.25f) else null,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            apps.forEach { key ->
                Box(Modifier.weight(1f).fillMaxSize()) {
                    AppCell(vm, actions, key, layout.copy(iconSizeDp = (d.heightDp - 20f).coerceAtMost(layout.iconSizeDp)), editMode = false, fromDock = true, showLabel = d.showLabels)
                }
            }
            if (editMode && apps.isEmpty()) Text(tr("Apps per Menü „Zum Dock“ hinzufügen", "Add apps via “Add to dock”"), style = Nova.type.caption, color = Nova.colors.textSecondary)
        }
    }
}
