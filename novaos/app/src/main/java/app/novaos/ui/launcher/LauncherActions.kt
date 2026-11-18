package app.novaos.ui.launcher

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import app.novaos.MainActivity
import app.novaos.R
import app.novaos.SettingsActivity
import app.novaos.model.AnimArea
import app.novaos.model.AnimationStyle
import app.novaos.model.GestureAction
import app.novaos.model.NovaArea
import app.novaos.model.ShortcutTarget
import app.novaos.model.WidgetRequirement
import app.novaos.system.MediaHub
import app.novaos.system.NotificationHub
import app.novaos.ui.common.tr
import app.novaos.widgets.WidgetActions

/** Verbindet Widgets, Gesten und Menüs mit Android (App-Start, Einstellungen, Freigaben). */
class LauncherActions(private val activity: MainActivity, private val vm: LauncherViewModel) : WidgetActions {

    private val c get() = vm.container

    /** Startanimation passend zum Animationsstil (offizielle ActivityOptions). */
    private fun launchOptions(view: View?, bounds: Rect?): Bundle? {
        val m = c.config.config.value?.activeProfile?.theme?.animation ?: return null
        val perf = c.config.config.value?.general?.performanceMode == true
        if (view == null || bounds == null || perf) return ActivityOptions.makeCustomAnimation(activity, R.anim.nova_fade_in, R.anim.nova_hold).toBundle()
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val x = bounds.left - loc[0]
        val y = bounds.top - loc[1]
        return when (m.styleFor(AnimArea.APP_LAUNCH)) {
            AnimationStyle.MINIMAL -> ActivityOptions.makeCustomAnimation(activity, R.anim.nova_fade_in, R.anim.nova_hold)
            AnimationStyle.NOTHING_DOTS -> ActivityOptions.makeCustomAnimation(activity, R.anim.nova_dots_enter, R.anim.nova_hold)
            AnimationStyle.LIQUID, AnimationStyle.FLUID -> ActivityOptions.makeClipRevealAnimation(view, x, y, bounds.width(), bounds.height())
            AnimationStyle.SMOOTH, AnimationStyle.ELASTIC -> ActivityOptions.makeScaleUpAnimation(view, x, y, bounds.width(), bounds.height())
            AnimationStyle.CLASSIC_ANDROID -> return null
        }.toBundle()
    }

    fun launchApp(key: String, bounds: Rect?, view: View?) {
        val ok = c.apps.launch(key, bounds, launchOptions(view, bounds))
        if (ok) c.controller.recordLaunch(key) else toast(tr("App konnte nicht gestartet werden", "App could not be started"))
    }

    override fun launchApp(key: String) = launchApp(key, null, null)

    fun startSafely(intent: Intent) {
        if (!c.apps.startIntent(intent)) toast(tr("Diese Funktion ist auf dem Gerät nicht verfügbar", "Not available on this device"))
    }

    override fun openTarget(target: ShortcutTarget) {
        when (target) {
            is ShortcutTarget.App -> launchApp(target.appKey)
            is ShortcutTarget.Nova -> openArea(target.area)
            is ShortcutTarget.SystemSettings -> startSafely(Intent(target.action))
            is ShortcutTarget.Url -> startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
            is ShortcutTarget.DeepShortcut -> if (!c.apps.startShortcut(target.packageName, target.shortcutId, target.userSerial, null)) toast(tr("Verknüpfung nicht verfügbar", "Shortcut unavailable"))
        }
    }

    fun openArea(area: NovaArea) {
        when (area) {
            NovaArea.CONTROL_CENTER -> vm.open(Overlay.ControlCenter)
            NovaArea.NOTIFICATIONS -> vm.open(Overlay.Notifications)
            NovaArea.DRAWER -> vm.open(Overlay.Drawer)
            NovaArea.SEARCH -> vm.open(Overlay.Search)
            NovaArea.QUICK_MENU -> vm.open(Overlay.QuickMenu)
            NovaArea.SETTINGS -> openSettings(null)
            NovaArea.WALLPAPER -> openSettings("wallpaper")
            NovaArea.THEMES -> openSettings("themes")
            NovaArea.PROFILES -> vm.open(Overlay.ProfileSwitcher)
            NovaArea.WIDGETS -> vm.open(Overlay.WidgetPicker(0))
        }
    }

    fun openSettings(route: String?, arg: String? = null) {
        activity.startActivity(SettingsActivity.intent(activity, route, arg))
    }

    override fun editWidget(itemId: String) = openSettings("widget_editor", itemId)

    override fun requestRequirement(requirement: WidgetRequirement) {
        when (requirement) {
            WidgetRequirement.NONE, WidgetRequirement.NETWORK -> Unit
            WidgetRequirement.CALENDAR -> activity.requestRuntime(Manifest.permission.READ_CALENDAR)
            WidgetRequirement.CONTACTS -> activity.requestRuntime(Manifest.permission.READ_CONTACTS)
            WidgetRequirement.NOTIFICATION_ACCESS -> startSafely(NotificationHub.accessSettingsIntent(activity))
            WidgetRequirement.USAGE_ACCESS -> startSafely(c.systemInfo.usageAccessIntent())
            WidgetRequirement.DEFAULT_LAUNCHER -> activity.requestHomeRole()
        }
    }

    override fun openWeatherSettings() = openSettings("weather")
    override fun openProductivity(section: String) = openSettings("productivity", section)
    override fun openNotification(key: String) { if (!NotificationHub.open(key)) toast(tr("Benachrichtigung kann nicht geöffnet werden", "Cannot open notification")) }
    override fun openClock() = c.apps.openClock()
    override fun openAppDetails(key: String) = c.apps.openAppDetails(key)
    override fun openMediaPlayer() = MediaHub.openPlayer(activity)

    fun lockScreen() {
        if (!c.controls.lockNow()) {
            toast(tr("Zum Sperren bitte die Geräteadministrator-Freigabe erteilen", "Grant device admin to lock the screen"))
            startSafely(c.controls.adminRequestIntent())
        }
    }

    fun perform(action: GestureAction) {
        when (action) {
            GestureAction.None -> Unit
            GestureAction.OpenControlCenter -> vm.open(Overlay.ControlCenter)
            GestureAction.OpenNotifications -> vm.open(Overlay.Notifications)
            GestureAction.OpenSearch -> vm.open(Overlay.Search)
            GestureAction.OpenDrawer -> vm.open(Overlay.Drawer)
            GestureAction.LockScreen -> lockScreen()
            GestureAction.OpenQuickMenu -> vm.open(Overlay.QuickMenu)
            GestureAction.EditHome -> vm.setEdit(true)
            GestureAction.OpenSettings -> openSettings(null)
            GestureAction.NextProfile -> c.controller.nextProfile()
            is GestureAction.LaunchApp -> launchApp(action.appKey)
            is GestureAction.OpenWidget -> vm.open(Overlay.WidgetPanel(action.widgetItemId))
        }
    }

    fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
