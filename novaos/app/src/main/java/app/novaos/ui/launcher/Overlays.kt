package app.novaos.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.novaos.design.Nova
import app.novaos.design.NovaMotion
import app.novaos.model.AnimArea

/** Zeigt das aktive Overlay mit der Animation des jeweiligen Bereichs. */
@Composable
fun Overlays(vm: LauncherViewModel, actions: LauncherActions, widgetHost: NovaWidgetHost, overlay: Overlay, currentPage: Int) {
    // Letztes Overlay merken, damit die Schließanimation noch den Inhalt zeigt.
    var last by remember { mutableStateOf<Overlay>(Overlay.None) }
    if (overlay != Overlay.None) last = overlay

    OverlaySlot(overlay == Overlay.Drawer, AnimArea.OPEN) { AppDrawer(vm, actions) }
    OverlaySlot(overlay == Overlay.ControlCenter, AnimArea.CONTROL_CENTER) { ControlCenterPanel(vm, actions) }
    OverlaySlot(overlay == Overlay.Notifications, AnimArea.NOTIFICATIONS) { NotificationCenter(vm, actions) }
    OverlaySlot(overlay == Overlay.Search, AnimArea.OPEN) { SearchOverlay(vm, actions) }
    OverlaySlot(overlay == Overlay.QuickMenu, AnimArea.OPEN) { QuickMenu(vm, actions, currentPage) }
    OverlaySlot(overlay == Overlay.ProfileSwitcher, AnimArea.OPEN) { ProfileSwitcher(vm) }
    OverlaySlot(overlay is Overlay.Folder, AnimArea.FOLDER) { (last as? Overlay.Folder)?.let { FolderOverlay(vm, actions, it.itemId) } }
    OverlaySlot(overlay is Overlay.WidgetPicker, AnimArea.OPEN) { (last as? Overlay.WidgetPicker)?.let { WidgetPicker(vm, actions, widgetHost, it.page) } }
    OverlaySlot(overlay is Overlay.AppMenu, AnimArea.OPEN) { (last as? Overlay.AppMenu)?.let { AppMenu(vm, actions, it, currentPage) } }
    OverlaySlot(overlay is Overlay.ItemMenu, AnimArea.OPEN) { (last as? Overlay.ItemMenu)?.let { ItemMenu(vm, actions, widgetHost, it.itemId) } }
    OverlaySlot(overlay is Overlay.WidgetPanel, AnimArea.WIDGET) { (last as? Overlay.WidgetPanel)?.let { WidgetPanel(vm, it.itemId) } }
}

@Composable
private fun OverlaySlot(visible: Boolean, area: AnimArea, content: @Composable BoxScope.() -> Unit) {
    val m = Nova.style.motion
    AnimatedVisibility(visible, enter = NovaMotion.enter(m, area), exit = NovaMotion.exit(m, if (area == AnimArea.OPEN) AnimArea.CLOSE else area)) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}
