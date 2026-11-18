package app.novaos

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.novaos.model.Defaults
import app.novaos.model.NovaConfig
import app.novaos.model.ThemePresets
import app.novaos.ui.launcher.LauncherViewModel
import app.novaos.ui.launcher.Overlay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke-Tests auf der JVM (Robolectric): Startet Launcher und Einstellungen wirklich, rendert alle
 * Bildschirme und prüft, dass nichts abstürzt und Einstellungen gespeichert werden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherSmokeTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val container: AppContainer get() = context.nova

    private fun setConfig(transform: (NovaConfig) -> NovaConfig) = runBlocking {
        container.config.update(transform)
        container.config.flow.first()
    }

    private fun idle() {
        repeat(3) { compose.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }

    @Test fun setupAssistantIsShownOnFirstStart() {
        setConfig { Defaults.config() }
        ActivityScenario.launch(MainActivity::class.java).use {
            idle()
            val de = compose.onAllNodesWithText("Willkommen bei NovaOS").fetchSemanticsNodes().size
            val en = compose.onAllNodesWithText("Welcome to NovaOS").fetchSemanticsNodes().size
            assertTrue("Einrichtungsassistent nicht sichtbar", de + en > 0)
        }
    }

    @Test fun homeScreenAndAllOverlaysRender() {
        setConfig { Defaults.config().copy(setupCompleted = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idle()
            compose.onRoot().assertExistsCompat()
            var vm: LauncherViewModel? = null
            scenario.onActivity { a -> vm = androidx.lifecycle.ViewModelProvider(a, LauncherViewModel.Factory(a.nova))[LauncherViewModel::class.java] }
            val overlays = listOf(
                Overlay.Drawer, Overlay.ControlCenter, Overlay.Notifications, Overlay.Search, Overlay.QuickMenu,
                Overlay.ProfileSwitcher, Overlay.WidgetPicker(0),
            )
            overlays.forEach { o ->
                scenario.onActivity { vm!!.open(o) }
                idle()
                assertEquals(o, vm!!.overlay.value)
                scenario.onActivity { vm!!.close() }
                idle()
            }
            scenario.onActivity { vm!!.setEdit(true) }
            idle()
            scenario.onActivity { vm!!.setEdit(false) }
            idle()
        }
    }

    @Test fun everyThemeRendersOnHome() {
        ThemePresets.all.forEach { theme ->
            setConfig { Defaults.config().copy(setupCompleted = true).updateActive { p -> p.copy(theme = theme, wallpaper = theme.wallpaper ?: p.wallpaper) } }
            ActivityScenario.launch(MainActivity::class.java).use { idle() }
        }
    }

    @Test fun everyProfileRendersOnHome() {
        val cfg = Defaults.config().copy(setupCompleted = true)
        cfg.profiles.forEach { p ->
            setConfig { cfg.copy(activeProfileId = p.id, manualProfileId = p.id) }
            ActivityScenario.launch(MainActivity::class.java).use { idle() }
        }
    }

    @Test fun everySettingsScreenRenders() {
        setConfig { Defaults.config().copy(setupCompleted = true) }
        val firstWidget = container.config.config.value!!.activeProfile.layout.pages.first().items.first { it is app.novaos.model.HomeItem.Widget }.id
        val routes = listOf(
            "root", "general", "weather", "personalization", "home", "drawer", "icons", "widgets", "widget_creator",
            "themes", "colors", "typography", "animations", "glass", "control_center", "notifications", "gestures",
            "wallpaper", "profiles", "profile_rules", "performance", "privacy", "permissions", "backup", "about", "productivity", "setup",
        )
        routes.forEach { r ->
            ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(context, r)).use { idle() }
        }
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(context, "widget_editor", firstWidget)).use { idle() }
        val key = container.apps.apps.value.firstOrNull()?.key
        ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(context, "icon_app", key)).use { idle() }
    }

    @Test fun settingsChangesArePersisted() {
        setConfig { Defaults.config().copy(setupCompleted = true) }
        container.controller.editTheme { it.copy(colors = it.colors.copy(dark = it.colors.dark.copy(accent = "#00FF00"))) }
        container.controller.setGrid(5, 7)
        container.controller.editGeneral { it.copy(performanceMode = true) }
        idle()
        val cfg = runBlocking {
            kotlinx.coroutines.delay(200)
            container.config.flow.first()
        }
        assertEquals("#00FF00", cfg.activeProfile.theme.colors.dark.accent)
        assertEquals(5, cfg.activeProfile.layout.columns)
        assertEquals(7, cfg.activeProfile.layout.rows)
        assertTrue(cfg.general.performanceMode)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() { assertExists() }
