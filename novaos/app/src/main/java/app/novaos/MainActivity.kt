package app.novaos

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import app.novaos.system.HomeRole
import app.novaos.system.MediaHub
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.launcher.LauncherActions
import app.novaos.ui.launcher.LauncherRoot
import app.novaos.ui.launcher.LauncherViewModel
import app.novaos.ui.launcher.NovaWidgetHost

/** Die Startbildschirm-Activity (Home-Rolle). */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels { LauncherViewModel.Factory(nova) }
    lateinit var actions: LauncherActions
        private set
    lateinit var widgetHost: NovaWidgetHost
        private set

    private val runtimePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { nova.controls.refresh() }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val p = pendingWidget ?: return@registerForActivityResult
        if (r.resultCode == RESULT_OK) configureOrAdd(p) else { widgetHost.deleteAppWidgetId(p.id); pendingWidget = null }
    }

    private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo, val page: Int, val w: Int, val h: Int)
    private var pendingWidget: PendingWidget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetHost = NovaWidgetHost(this)
        actions = LauncherActions(this, vm)
        setContent {
            CompositionLocalProvider(LocalContainer provides nova) {
                LauncherRoot(vm, actions, widgetHost)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val c = nova
        c.systemInfo.start()
        c.controls.startListening()
        c.profileContext.start()
        runCatching { widgetHost.startListening() }
    }

    override fun onResume() {
        super.onResume()
        nova.controller.evaluateAutoProfile()
        MediaHub.refresh(this)
        nova.controls.refresh()
    }

    override fun onStop() {
        super.onStop()
        val c = nova
        c.systemInfo.stop()
        c.controls.stopListening()
        c.profileContext.stop()
        runCatching { widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }

    /** System-Hintergrund durchscheinen lassen (nur wenn das Profil "System-Hintergrund" nutzt). */
    fun setShowSystemWallpaper(show: Boolean) {
        if (show) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    fun requestRuntime(permission: String) = runtimePermission.launch(permission)

    fun requestHomeRole() {
        val i = HomeRole.requestIntent(this)
        if (i != null) roleRequest.launch(i) else actions.startSafely(HomeRole.homeSettingsIntent())
    }

    // ------------------------------------------------------------------ Android-Widgets

    fun addAndroidWidget(info: AppWidgetProviderInfo, page: Int, w: Int, h: Int) {
        val id = widgetHost.allocateAppWidgetId()
        val p = PendingWidget(id, info, page, w, h)
        pendingWidget = p
        val allowed = runCatching { widgetHost.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (allowed) configureOrAdd(p) else bindWidget.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
        )
    }

    private fun configureOrAdd(p: PendingWidget) {
        if (p.info.configure != null) {
            runCatching { widgetHost.startAppWidgetConfigureActivityForResult(this, p.id, 0, REQUEST_CONFIGURE, null) }
                .onFailure { finishWidget(p) }
        } else finishWidget(p)
    }

    private fun finishWidget(p: PendingWidget) {
        nova.controller.addAndroidWidget(p.id, p.info.provider.flattenToString(), p.page, p.w, p.h)
        pendingWidget = null
    }

    @Deprecated("AppWidget-Konfiguration nutzt startActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) {
            val p = pendingWidget ?: return
            if (resultCode == RESULT_OK) finishWidget(p) else { widgetHost.deleteAppWidgetId(p.id); pendingWidget = null }
        }
    }

    companion object {
        private const val REQUEST_CONFIGURE = 4711
    }
}
