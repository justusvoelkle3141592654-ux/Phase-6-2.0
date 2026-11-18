package app.novaos

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.novaos.data.FileStore
import app.novaos.design.NovaTheme
import app.novaos.model.BackupReadResult
import app.novaos.model.BackupSection
import app.novaos.model.Defaults
import app.novaos.model.GeneratedWallpaper
import app.novaos.model.IconRendering
import app.novaos.model.IconShape
import app.novaos.model.IconStyleSpec
import app.novaos.model.InfoDensity
import app.novaos.model.ShortcutTarget
import app.novaos.model.SurfaceBackground
import app.novaos.model.ThemePresets
import app.novaos.model.WallpaperSource
import app.novaos.model.WallpaperSpec
import app.novaos.model.WidgetInstance
import app.novaos.model.WidgetLayoutVariant
import app.novaos.model.WidgetRequirement
import app.novaos.model.WidgetStyle
import app.novaos.model.WidgetType
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.rememberNovaWidgetHost
import app.novaos.widgets.LocalWidgetHost
import app.novaos.widgets.NovaWidget
import app.novaos.widgets.WidgetActions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private object NoActions : WidgetActions {
    override fun launchApp(key: String) = Unit
    override fun openTarget(target: ShortcutTarget) = Unit
    override fun editWidget(itemId: String) = Unit
    override fun requestRequirement(requirement: WidgetRequirement) = Unit
    override fun openWeatherSettings() = Unit
    override fun openProductivity(section: String) = Unit
    override fun openNotification(key: String) = Unit
    override fun openClock() = Unit
    override fun openAppDetails(key: String) = Unit
    override fun openMediaPlayer() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderTest {
    @get:Rule val compose = createComposeRule()
    private val container: AppContainer get() = ApplicationProvider.getApplicationContext<Context>().nova

    @Test fun everyWidgetTypeRendersInEveryLayoutAndBackground() {
        runBlocking { container.config.update { Defaults.config().copy(setupCompleted = true) } }
        val profile = runBlocking { container.config.flow.first().activeProfile }
        val variants = WidgetLayoutVariant.entries.flatMap { l -> SurfaceBackground.entries.map { b -> l to b } }
        compose.setContent {
            CompositionLocalProvider(LocalContainer provides container, LocalProfile provides profile) {
                NovaTheme(profile.theme) {
                    val host = rememberNovaWidgetHost(profile, NoActions)
                    CompositionLocalProvider(LocalWidgetHost provides host) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            WidgetType.entries.forEach { t ->
                                variants.forEachIndexed { i, (layout, bg) ->
                                    val style = WidgetStyle(layout = layout, background = bg, density = InfoDensity.entries[i % 3])
                                    NovaWidget(WidgetInstance("w-$t-$i", t, style), "i-$t-$i", Modifier.size((t.defaultW * 90).dp, (t.defaultH * 100).dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Widgets ohne Freigabe zeigen einen Hinweis statt leerer Fläche
        assertTrue(compose.onAllNodesWithText("Tippen: Benachrichtigungszugriff erlauben").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun builtInTemplatesRender() {
        val profile = Defaults.config().activeProfile
        compose.setContent {
            CompositionLocalProvider(LocalContainer provides container, LocalProfile provides profile) {
                ThemePresets.all.forEach { theme ->
                    NovaTheme(theme) {
                        val host = rememberNovaWidgetHost(profile, NoActions)
                        CompositionLocalProvider(LocalWidgetHost provides host) {
                            Column {
                                Defaults.widgetTemplates().forEach { t -> NovaWidget(t.widget.copy(id = "tpl-${theme.id}-${t.id}"), t.id, Modifier.size((t.w * 80).dp, (t.h * 90).dp), preview = true) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RendererTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun iconRendererHandlesAllShapesAndRenderings() {
        val c = context.nova
        val adaptive = AdaptiveIconDrawable(ColorDrawable(Color.WHITE), ColorDrawable(Color.RED))
        val plain = ColorDrawable(Color.BLUE)
        IconShape.entries.forEach { shape ->
            IconRendering.entries.forEach { r ->
                listOf(adaptive, plain, null).forEach { d ->
                    val style = IconStyleSpec(shape = shape, rendering = r, strokeWidth = 1f, shadow = 0.5f, alpha = 0.8f)
                    val bmp = c.icons.render("k-$shape-$r-${d?.javaClass?.simpleName}", d, style, 96, Color.WHITE, Color.BLACK)
                    assertEquals(96, bmp.width)
                    assertEquals(96, bmp.height)
                }
            }
        }
    }

    @Test fun wallpaperEngineGeneratesAndAdjusts() {
        val c = context.nova
        GeneratedWallpaper.entries.forEach { g ->
            val spec = WallpaperSpec(source = WallpaperSource.Generated(g, "#000000", "#333333", "#E5202E"), zoom = 1.5f, blur = 6f, brightness = 0.2f, contrast = 1.2f, overlayAlpha = 0.3f)
            val r = c.wallpapers.render(spec, spec.source, 360, 780)
            assertNotNull(r)
            assertEquals(360, r!!.sharp.width)
            assertTrue(r.blurred.width < r.sharp.width)
        }
        assertNull(c.wallpapers.render(WallpaperSpec(source = WallpaperSource.System), WallpaperSource.System, 360, 780))
        val solid = c.wallpapers.render(WallpaperSpec(source = WallpaperSource.Solid("#123456")), WallpaperSource.Solid("#123456"), 100, 200)!!
        assertEquals(0xFF123456.toInt(), solid.sharp.getPixel(50, 50))
    }

    @Test fun backupExportAndRestoreThroughFiles() = runBlocking {
        val c = context.nova
        c.config.update { Defaults.config().copy(setupCompleted = true) }
        c.files.write("${FileStore.WALLPAPERS}/test.jpg", byteArrayOf(9, 8, 7))
        c.config.update { it.updateActive { p -> p.copy(wallpaper = p.wallpaper.copy(source = WallpaperSource.Image("${FileStore.WALLPAPERS}/test.jpg"))) } }
        val before = c.config.flow.first()
        val f = File(context.cacheDir, "test.novabackup")
        val exported = c.backup.export(Uri.fromFile(f), BackupSection.entries.toSet())
        assertTrue(exported.isSuccess)
        assertEquals(1, exported.getOrThrow())

        // Konfiguration verändern und Datei löschen, dann wiederherstellen
        c.config.update { it.updateActive { p -> p.copy(name = "Geändert") } }
        c.files.delete("${FileStore.WALLPAPERS}/test.jpg")
        val read = c.backup.read(Uri.fromFile(f))
        assertTrue(read is BackupReadResult.Ok)
        c.backup.restore((read as BackupReadResult.Ok).content, BackupSection.entries.toSet())
        val after = c.config.flow.first()
        assertEquals(before.activeProfile.name, after.activeProfile.name)
        assertTrue(c.files.exists("${FileStore.WALLPAPERS}/test.jpg"))

        // Beschädigte Datei wird erkannt
        f.writeBytes(f.readBytes().copyOf(200))
        assertTrue(c.backup.read(Uri.fromFile(f)) is BackupReadResult.Failed)
    }
}
