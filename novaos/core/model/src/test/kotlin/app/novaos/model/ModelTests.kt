package app.novaos.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ColorUtilTest {
    @Test fun parsesAndFormatsHex() {
        assertEquals(0xFFE5202EL, ColorUtil.parseHex("#E5202E"))
        assertEquals(0xFFFFFFFFL, ColorUtil.parseHex("fff"))
        assertEquals(0x80112233L, ColorUtil.parseHex("#80112233"))
        assertNull(ColorUtil.parseHex("#12345"))
        assertNull(ColorUtil.parseHex("#GG0000"))
        assertEquals("#E5202E", ColorUtil.toHex(0xFFE5202EL))
        assertEquals("#80112233", ColorUtil.toHex(0x80112233L))
    }

    @Test fun contrastRatioMatchesWcag() {
        assertEquals(21.0, ColorUtil.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, ColorUtil.contrastRatio(0xFF777777, 0xFF777777), 0.001)
        assertEquals(0xFFFFFFFFL, ColorUtil.readableOn(0xFF000000))
        assertEquals(0xFF000000L, ColorUtil.readableOn(0xFFFFFFFF))
    }

    @Test fun allPresetPalettesHaveReadableText() {
        ThemePresets.all.forEach { t ->
            listOf(t.colors.dark, t.colors.light).forEach { p ->
                val ratio = ColorUtil.contrastRatio(ColorUtil.parseHex(p.text)!!, ColorUtil.parseHex(p.background)!!)
                assertTrue("${t.name}: Kontrast $ratio zu gering", ratio >= 4.5)
            }
        }
    }
}

class GridEngineTest {
    private val g = GridEngine(4, 6)
    private fun app(id: String, x: Int, y: Int, w: Int = 1, h: Int = 1) = HomeItem.App(id, ItemPos(x, y, w, h), "k$id")

    @Test fun findsFreeCellsAndRespectsBounds() {
        val items = listOf(app("a", 0, 0, 4, 2), app("b", 0, 2))
        assertEquals(ItemPos(1, 2), g.findFree(items, 1, 1))
        assertEquals(ItemPos(0, 3, 4, 2), g.findFree(items, 4, 2))
        assertFalse(g.fits(ItemPos(3, 0, 2, 1)))
        assertNull(g.findFree(items, 5, 1))
    }

    @Test fun moveSwapsEqualSizedItems() {
        val items = listOf(app("a", 0, 0), app("b", 1, 0))
        val moved = g.move(items, "a", ItemPos(1, 0))!!
        assertEquals(ItemPos(1, 0), moved.first { it.id == "a" }.pos)
        assertEquals(ItemPos(0, 0), moved.first { it.id == "b" }.pos)
    }

    @Test fun moveIntoLargerBlockerFails() {
        val items = listOf(app("a", 0, 5), app("w", 0, 0, 2, 2))
        assertNull(g.move(items, "a", ItemPos(1, 1)))
    }

    @Test fun resizeChecksCollisions() {
        val items = listOf(app("w", 0, 0, 2, 2), app("b", 2, 0))
        assertNull(g.resize(items, "w", 3, 2))
        assertNotNull(g.resize(items, "w", 2, 3))
    }

    @Test fun reflowAfterShrinkingGridReportsOverflow() {
        val small = GridEngine(3, 3)
        val items = (0 until 12).map { app("$it", it % 4, it / 4) }
        val (placed, overflow) = small.reflow(items)
        assertEquals(9, placed.size)
        assertEquals(3, overflow.size)
        assertTrue(placed.all { small.fits(it.pos) })
    }

    @Test fun freePositionDerivedFromGrid() {
        val p = ItemPos(1, 2, 2, 1).freeOrFromGrid(4, 6)
        assertEquals(0.25f, p.fx, 0.0001f)
        assertEquals(2f / 6f, p.fy, 0.0001f)
        assertEquals(0.5f, p.fw, 0.0001f)
    }
}

class RuleEngineTest {
    private val work = AutoRule("r1", "work", RuleCondition.TimeWindow(setOf(1, 2, 3, 4, 5), 8 * 60, 17 * 60))
    private val media = AutoRule("r2", "media", RuleCondition.BluetoothDevice("Buds"))
    private val night = AutoRule("r3", "minimal", RuleCondition.TimeWindow(setOf(5), 22 * 60, 2 * 60))
    private val office = AutoRule("r4", "work", RuleCondition.WifiNetwork("Office"), priority = 1)
    private val valid = setOf("work", "media", "minimal")

    @Test fun timeWindowWeekdays() {
        assertEquals("work", RuleEngine.select(listOf(work), valid, RuleContext(1, 9 * 60)))
        assertNull(RuleEngine.select(listOf(work), valid, RuleContext(6, 9 * 60)))
        assertNull(RuleEngine.select(listOf(work), valid, RuleContext(1, 17 * 60)))
    }

    @Test fun overnightWindowCountsForStartDay() {
        assertEquals("minimal", RuleEngine.select(listOf(night), valid, RuleContext(5, 23 * 60)))
        assertEquals("minimal", RuleEngine.select(listOf(night), valid, RuleContext(6, 60)))
        assertNull(RuleEngine.select(listOf(night), valid, RuleContext(5, 60)))
    }

    @Test fun specificRulesWinOverTime() {
        val ctx = RuleContext(1, 9 * 60, bluetoothDevices = setOf("buds"))
        assertEquals("media", RuleEngine.select(listOf(work, media), valid, ctx))
        assertEquals("work", RuleEngine.select(listOf(work, media, office), valid, ctx.copy(wifiSsid = "Office")))
    }

    @Test fun disabledAndUnknownProfilesIgnored() {
        assertNull(RuleEngine.select(listOf(work.copy(enabled = false)), valid, RuleContext(1, 9 * 60)))
        assertNull(RuleEngine.select(listOf(work), setOf("media"), RuleContext(1, 9 * 60)))
    }
}

class GestureClassifierTest {
    @Test fun classifiesDirections() {
        assertEquals(GestureType.SWIPE_UP, GestureClassifier.classifySwipe(5f, -200f, 1, 50f))
        assertEquals(GestureType.SWIPE_DOWN, GestureClassifier.classifySwipe(0f, 200f, 1, 50f))
        assertEquals(GestureType.SWIPE_LEFT, GestureClassifier.classifySwipe(-200f, 10f, 1, 50f))
        assertEquals(GestureType.TWO_FINGER_DOWN, GestureClassifier.classifySwipe(0f, 200f, 2, 50f))
        assertNull(GestureClassifier.classifySwipe(10f, 20f, 1, 50f))
        assertNull(GestureClassifier.classifySwipe(150f, 150f, 1, 50f))
    }

    @Test fun reportsSystemConflicts() {
        assertNotNull(GestureClassifier.conflicts(GestureType.SWIPE_UP, true))
        assertNull(GestureClassifier.conflicts(GestureType.DOUBLE_TAP, true))
    }
}

class DotFontTest {
    @Test fun everyGlyphIsComplete() {
        DotFont.supported.forEach { c -> assertEquals(35, DotFont.glyph(c).size) }
        "0123456789:".forEach { assertTrue("$it fehlt", it in DotFont.supported) }
        ('A'..'Z').forEach { assertTrue("$it fehlt", it in DotFont.supported) }
    }

    @Test fun digitsAreDistinct() {
        val digits = "0123456789".map { DotFont.glyph(it).toList() }
        assertEquals(10, digits.toSet().size)
    }

    @Test fun lowercaseAndUnknownFallback() {
        assertEquals(DotFont.glyph('A').toList(), DotFont.glyph('a').toList())
        assertEquals(DotFont.glyph('Ü').toList(), DotFont.glyph('ü').toList())
        assertEquals(DotFont.glyph('?').toList(), DotFont.glyph('€').toList())
        assertEquals(17, DotFont.widthInDots("16:"))
    }
}

class AppSearchTest {
    private val apps = listOf(
        AppEntry("1", "WhatsApp", "com.whatsapp", AppCategories.SOCIAL, 3),
        AppEntry("2", "Wetter", "com.weather", AppCategories.TOOLS, 1),
        AppEntry("3", "Ärzte-App", "de.arzt", AppCategories.OTHER, 2),
        AppEntry("4", "Google Maps", "com.google.android.apps.maps", AppCategories.TOOLS, 0),
        AppEntry("5", "1Password", "com.onepassword", AppCategories.TOOLS, 5),
    )

    @Test fun searchRanksPrefixFirstAndIgnoresDiacritics() {
        assertEquals(listOf("2", "1", "5"), AppSearch.search(apps, "w").map { it.key })
        assertEquals("3", AppSearch.search(apps, "arz").first().key)
        assertEquals("4", AppSearch.search(apps, "maps").first().key)
        assertEquals("4", AppSearch.search(apps, "gmp").first().key)
        assertTrue(AppSearch.search(apps, "xyz").isEmpty())
    }

    @Test fun sortingModes() {
        assertEquals("5", AppSearch.sort(apps, SortMode.ALPHABETICAL, emptyMap(), emptyList()).first().key)
        assertEquals("5", AppSearch.sort(apps, SortMode.RECENTLY_INSTALLED, emptyMap(), emptyList()).first().key)
        assertEquals("4", AppSearch.sort(apps, SortMode.MOST_USED, mapOf("4" to 9), emptyList()).first().key)
        assertEquals(listOf("3", "1"), AppSearch.sort(apps, SortMode.CUSTOM, emptyMap(), listOf("3", "1")).take(2).map { it.key })
    }

    @Test fun sectionLetters() {
        assertEquals('A', AppSearch.sectionLetter("Ärzte"))
        assertEquals('#', AppSearch.sectionLetter("1Password"))
        val sorted = AppSearch.sort(apps, SortMode.ALPHABETICAL, emptyMap(), emptyList())
        assertEquals(listOf('#', 'A', 'G', 'W'), AppSearch.sections(sorted).map { it.first })
    }

    @Test fun androidCategoryMapping() {
        assertEquals(AppCategories.GAMES, AppCategories.fromAndroid(0, "x"))
        assertEquals(AppCategories.SOCIAL, AppCategories.fromAndroid(-1, "org.telegram.messenger"))
    }
}

class CcGridTest {
    @Test fun arrangesTilesWithoutOverlap() {
        val placed = CcGrid.arrange(defaultCcTiles(), 4)
        val cells = placed.flatMap { p -> (p.col until p.col + p.span).flatMap { c -> (p.row until p.row + p.height).map { c to it } } }
        assertEquals(cells.size, cells.toSet().size)
        assertTrue(placed.all { it.col + it.span <= 4 })
        assertEquals(defaultCcTiles().size, placed.size)
    }
}

class NotificationGroupingTest {
    private val now = 10_000_000L
    private val entries = listOf(
        NotificationEntry("a", "com.chat", "Chat", "Anna", "Hallo", now - 60_000, isMessage = true),
        NotificationEntry("b", "com.mail", "Mail", "Rechnung", "", now - 3 * 3_600_000),
        NotificationEntry("c", "com.chat", "Chat", "Ben", "Hi", now - 120_000),
    )

    @Test fun groupsByApp() {
        val g = NotificationGrouping.group(entries, GroupingStyle.BY_APP, true, now)
        assertEquals(listOf("com.chat", "com.mail"), g.map { it.id })
        assertEquals(listOf("a", "c"), g[0].entries.map { it.key })
    }

    @Test fun groupsByPriorityAndTime() {
        assertEquals("Wichtig", NotificationGrouping.group(entries, GroupingStyle.PRIORITY, true, now).first().title)
        assertEquals(listOf("Letzte Stunde", "Heute"), NotificationGrouping.group(entries, GroupingStyle.BY_TIME, true, now).map { it.title })
        assertEquals("1 Min.", NotificationGrouping.relativeTime(now - 60_000, now))
    }
}

class ConfigAndBackupTest {

    @Test fun defaultConfigRoundTripsThroughJson() {
        val cfg = Defaults.config()
        val json = NovaJson.encodeToString(NovaConfig.serializer(), cfg)
        val back = NovaJson.decodeFromString(NovaConfig.serializer(), json)
        assertEquals(cfg, back)
        assertEquals(5, back.profiles.size)
        assertTrue(json.contains("\"@type\""))
    }

    @Test fun allThemesAndTemplatesSerialize() {
        ThemePresets.all.forEach { t ->
            val s = NovaExport.encode(NovaExport(NovaExport.THEME, theme = t))
            assertEquals(t, NovaExport.decode(s, NovaExport.THEME)!!.theme)
        }
        assertEquals(9, ThemePresets.all.size)
        assertEquals(TemplateCategory.entries.toSet(), Defaults.widgetTemplates().map { it.category }.toSet())
    }

    @Test fun exportRejectsWrongFormat() {
        val s = NovaExport.encode(NovaExport(NovaExport.THEME, theme = ThemePresets.nova))
        assertNull(NovaExport.decode(s, NovaExport.PROFILE))
        assertNull(NovaExport.decode("{kaputt", NovaExport.THEME))
    }

    @Test fun normalizeRepairsInvalidState() {
        val cfg = Defaults.config().copy(activeProfileId = "gibt-es-nicht", manualProfileId = "auch-nicht")
        val n = ConfigMigrations.normalize(cfg)
        assertEquals(n.profiles.first().id, n.activeProfileId)
        val broken = cfg.updateActive { it.copy(layout = it.layout.copy(columns = 40, pages = emptyList())) }
        val fixed = ConfigMigrations.normalize(broken).profiles.first().layout
        assertEquals(8, fixed.columns)
        assertEquals(1, fixed.pages.size)
    }

    @Test fun backupRoundTrip() {
        val cfg = Defaults.config().copy(setupCompleted = true)
        val prod = ProductivityData.sample(1000)
        val bytes = BackupCodec.encode(cfg, prod, mapOf("wallpapers/a.jpg" to byteArrayOf(1, 2, 3)), "1.0.0")
        val result = BackupCodec.read(ByteArrayInputStream(bytes))
        assertTrue(result is BackupReadResult.Ok)
        val c = (result as BackupReadResult.Ok).content
        assertEquals(cfg, c.config)
        assertEquals(prod, c.productivity)
        assertEquals(listOf<Byte>(1, 2, 3), c.files["wallpapers/a.jpg"]!!.toList())
    }

    private fun rewrite(bytes: ByteArray, change: (String, ByteArray) -> ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zo ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zi ->
                while (true) {
                    val e = zi.nextEntry ?: break
                    val b = change(e.name, zi.readBytes()) ?: continue
                    zo.putNextEntry(ZipEntry(e.name)); zo.write(b); zo.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    @Test fun detectsTamperedCorruptForeignAndNewerBackups() {
        val bytes = BackupCodec.encode(Defaults.config(), null, emptyMap(), "1.0.0")

        val tampered = rewrite(bytes) { n, b -> if (n == "config.json") b.decodeToString().replace("NovaOS", "Hacked").toByteArray() else b }
        val r1 = BackupCodec.read(ByteArrayInputStream(tampered)) as BackupReadResult.Failed
        assertTrue(r1.problems.first() is BackupProblem.ChecksumMismatch)

        val r2 = BackupCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size / 2))) as BackupReadResult.Failed
        assertTrue(r2.problems.first() is BackupProblem.Corrupt || r2.problems.first() is BackupProblem.NotABackup || r2.problems.first() is BackupProblem.MissingPart)

        val r3 = BackupCodec.read(ByteArrayInputStream("hallo".toByteArray())) as BackupReadResult.Failed
        assertTrue(r3.problems.first() is BackupProblem.NotABackup)

        val newer = rewrite(bytes) { n, b -> if (n == "manifest.json") b.decodeToString().replace("\"formatVersion\": 1", "\"formatVersion\": 99").toByteArray() else b }
        val r4 = BackupCodec.read(ByteArrayInputStream(newer)) as BackupReadResult.Failed
        assertEquals(BackupProblem.UnsupportedVersion(99), r4.problems.first())

        val missing = rewrite(bytes) { n, b -> if (n == "config.json") null else b }
        val r5 = BackupCodec.read(ByteArrayInputStream(missing)) as BackupReadResult.Failed
        assertEquals(BackupProblem.MissingPart("config.json"), r5.problems.first())
    }

    @Test fun selectiveMerge() {
        val current = Defaults.config()
        val backup = current.updateActive { p ->
            p.copy(
                gestures = mapOf(GestureType.DOUBLE_TAP to GestureAction.OpenDrawer),
                theme = p.theme.copy(colors = ThemePresets.liquidGlass.colors, animation = AnimationSpec(style = AnimationStyle.ELASTIC)),
            )
        }.copy(general = GeneralSettings(performanceMode = true))

        val onlyGestures = BackupCodec.merge(current, backup, setOf(BackupSection.GESTURES))
        assertEquals(GestureAction.OpenDrawer, onlyGestures.activeProfile.gestures[GestureType.DOUBLE_TAP])
        assertEquals(current.activeProfile.theme.colors, onlyGestures.activeProfile.theme.colors)
        assertFalse(onlyGestures.general.performanceMode)

        val colorsAndSettings = BackupCodec.merge(current, backup, setOf(BackupSection.COLORS, BackupSection.SETTINGS))
        assertEquals(ThemePresets.liquidGlass.colors, colorsAndSettings.activeProfile.theme.colors)
        assertEquals(current.activeProfile.theme.animation, colorsAndSettings.activeProfile.theme.animation)
        assertTrue(colorsAndSettings.general.performanceMode)

        val other = Defaults.config()
        val profiles = BackupCodec.merge(other, backup, setOf(BackupSection.PROFILES))
        assertEquals(backup.profiles.map { it.id }, profiles.profiles.map { it.id })
    }

    @Test fun productivityProgress() {
        val d = ProductivityData.sample(0)
        assertEquals(1f / 3f, d.projectProgress("project-nova")!!, 0.001f)
        assertEquals(2, d.openTasks().size)
        assertNull(d.projectProgress("x"))
    }
}
