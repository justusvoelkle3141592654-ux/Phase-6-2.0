package app.novaos.ui.launcher

import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.novaos.AppContainer
import app.novaos.data.RenderedWallpaper
import app.novaos.model.WallpaperSource
import app.novaos.model.WallpaperSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Overlays des Launchers (immer nur eines sichtbar). */
sealed interface Overlay {
    data object None : Overlay
    data object Drawer : Overlay
    data object ControlCenter : Overlay
    data object Notifications : Overlay
    data object Search : Overlay
    data object QuickMenu : Overlay
    data object ProfileSwitcher : Overlay
    data class Folder(val itemId: String) : Overlay
    data class WidgetPicker(val page: Int) : Overlay
    data class AppMenu(val appKey: String, val homeItemId: String? = null, val fromDock: Boolean = false) : Overlay
    data class ItemMenu(val itemId: String) : Overlay
    data class WidgetPanel(val itemId: String) : Overlay
}

class LauncherViewModel(val container: AppContainer) : ViewModel() {

    val config = container.config.config
    val apps = container.apps.apps

    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /** Wird beim Drücken der Home-Taste erhöht, damit der Homescreen zur Startseite springt. */
    private val _homePressed = MutableStateFlow(0)
    val homePressed: StateFlow<Int> = _homePressed.asStateFlow()

    fun open(o: Overlay) { _overlay.value = o }
    fun close() { _overlay.value = Overlay.None }
    fun setEdit(on: Boolean) { _editMode.value = on; if (on) close() }

    fun onHomePressed() {
        if (_overlay.value != Overlay.None || _editMode.value) { close(); _editMode.value = false } else _homePressed.value++
    }

    private val wallpaperCache = object : LruCache<String, RenderedWallpaper>(4) {}

    suspend fun renderWallpaper(spec: WallpaperSpec, source: WallpaperSource, w: Int, h: Int): RenderedWallpaper? {
        if (source is WallpaperSource.System) return null
        val key = "${spec.copy(perPage = emptyMap(), source = source).hashCode()}-$w-$h"
        wallpaperCache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) { container.wallpapers.render(spec, source, w, h) }?.also { wallpaperCache.put(key, it) }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LauncherViewModel(c) as T
    }
}
