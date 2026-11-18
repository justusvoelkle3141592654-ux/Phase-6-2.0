package app.novaos

import app.novaos.model.AutoRule
import app.novaos.model.CustomWidgetDef
import app.novaos.model.Defaults
import app.novaos.model.GeneralSettings
import app.novaos.model.GridEngine
import app.novaos.model.HomeItem
import app.novaos.model.HomeLayout
import app.novaos.model.HomePage
import app.novaos.model.IconOverride
import app.novaos.model.IconPack
import app.novaos.model.ItemPos
import app.novaos.model.NovaConfig
import app.novaos.model.Profile
import app.novaos.model.ProfileKind
import app.novaos.model.RuleEngine
import app.novaos.model.ShortcutTarget
import app.novaos.model.ThemeSpec
import app.novaos.model.WidgetInstance
import app.novaos.model.WidgetTemplate
import app.novaos.model.WidgetType
import app.novaos.model.newId
import app.novaos.model.withPos
import kotlinx.coroutines.launch

/** Alle Änderungen an der Launcher-Konfiguration laufen über diese Klasse (Repository-Zugriff gebündelt). */
class NovaController(private val c: AppContainer) {

    private val repo get() = c.config
    val current: NovaConfig? get() = repo.config.value

    fun update(transform: (NovaConfig) -> NovaConfig) {
        c.scope.launch { repo.update(transform) }
    }

    fun editProfile(transform: (Profile) -> Profile) = update { it.updateActive(transform) }
    fun editTheme(transform: (ThemeSpec) -> ThemeSpec) = editProfile { it.copy(theme = transform(it.theme)) }
    fun editLayout(transform: (HomeLayout) -> HomeLayout) = editProfile { it.copy(layout = transform(it.layout)) }
    fun editGeneral(transform: (GeneralSettings) -> GeneralSettings) = update { it.copy(general = transform(it.general)) }

    // ------------------------------------------------------------------ Profile

    fun switchProfile(id: String, manual: Boolean = true) = update { cfg ->
        if (cfg.profiles.none { it.id == id }) cfg else cfg.copy(activeProfileId = id, manualProfileId = if (manual) id else cfg.manualProfileId)
    }

    fun nextProfile() = update { cfg ->
        val i = cfg.profiles.indexOfFirst { it.id == cfg.activeProfileId }
        val next = cfg.profiles[(i + 1) % cfg.profiles.size]
        cfg.copy(activeProfileId = next.id, manualProfileId = next.id)
    }

    /** Wählt anhand der Regeln das passende Profil (oder kehrt zum manuell gewählten zurück). */
    fun evaluateAutoProfile() {
        val cfg = current ?: return
        if (!cfg.autoSwitchEnabled || cfg.autoRules.isEmpty()) return
        val target = RuleEngine.select(cfg.autoRules, cfg.profiles.map { it.id }.toSet(), c.profileContext.current()) ?: cfg.manualProfileId
        if (target != cfg.activeProfileId) switchProfile(target, manual = false)
    }

    fun createProfile(kind: ProfileKind, name: String): String {
        val p = Defaults.profileFor(kind, name.ifBlank { kind.label })
        update { it.copy(profiles = it.profiles + p) }
        return p.id
    }

    fun duplicateProfile(id: String) = update { cfg ->
        val src = cfg.profiles.firstOrNull { it.id == id } ?: return@update cfg
        cfg.copy(profiles = cfg.profiles + src.copy(id = newId("profile"), name = "${src.name} (Kopie)"))
    }

    fun renameProfile(id: String, name: String) = update { it.updateProfile(id) { p -> p.copy(name = name.ifBlank { p.name }) } }

    fun deleteProfile(id: String) = update { cfg ->
        if (cfg.profiles.size <= 1) cfg else cfg.copy(profiles = cfg.profiles.filter { it.id != id }, autoRules = cfg.autoRules.filter { it.profileId != id })
    }

    fun importProfile(p: Profile) = update { it.copy(profiles = it.profiles + p.copy(id = newId("profile"))) }

    fun setRules(rules: List<AutoRule>) = update { it.copy(autoRules = rules) }

    // ------------------------------------------------------------------ Homescreen-Elemente

    private fun pageItems(layout: HomeLayout, page: Int) = layout.pages.getOrNull(page)?.items.orEmpty()

    private fun HomeLayout.withPage(page: Int, items: List<HomeItem>): HomeLayout =
        copy(pages = pages.mapIndexed { i, p -> if (i == page) p.copy(items = items) else p })

    /** Fügt ein Element auf der Seite (oder der nächsten Seite mit Platz) hinzu. */
    fun addItem(page: Int, w: Int, h: Int, make: (ItemPos) -> HomeItem) {
        editLayout { l ->
            val g = GridEngine(l.columns, l.rows)
            val order = listOf(page) + l.pages.indices.filter { it != page }
            for (p in order) {
                val pos = g.findFree(pageItems(l, p), w.coerceAtMost(l.columns), h.coerceAtMost(l.rows)) ?: continue
                return@editLayout l.withPage(p, pageItems(l, p) + make(pos))
            }
            // Kein Platz: neue Seite anlegen
            val np = HomePage(newId("page"), listOf(make(ItemPos(0, 0, w.coerceAtMost(l.columns), h.coerceAtMost(l.rows)))))
            l.copy(pages = l.pages + np)
        }
    }

    fun addApp(key: String, page: Int) = addItem(page, 1, 1) { HomeItem.App(newId("i"), it, key) }

    fun addWidget(type: WidgetType, page: Int, instance: WidgetInstance? = null, w: Int = type.defaultW, h: Int = type.defaultH) =
        addItem(page, w, h) { HomeItem.Widget(newId("i"), it, instance?.copy(id = newId("w")) ?: WidgetInstance(newId("w"), type)) }

    fun addTemplate(t: WidgetTemplate, page: Int) {
        if (t.custom != null) update { cfg -> if (cfg.customWidgets.any { it.id == t.custom!!.id }) cfg else cfg.copy(customWidgets = cfg.customWidgets + t.custom!!) }
        val inst = if (t.custom != null) t.widget.copy(config = t.widget.config + ("customId" to t.custom!!.id)) else t.widget
        addWidget(t.widget.type, page, inst, t.w, t.h)
    }

    fun addShortcut(label: String, target: ShortcutTarget, page: Int) = addItem(page, 1, 1) { HomeItem.Shortcut(newId("i"), it, label, target) }

    fun addAndroidWidget(appWidgetId: Int, provider: String, page: Int, w: Int, h: Int) =
        addItem(page, w, h) { HomeItem.AndroidWidget(newId("i"), it, appWidgetId, provider) }

    fun removeItem(itemId: String) = editLayout { l -> l.copy(pages = l.pages.map { p -> p.copy(items = p.items.filter { it.id != itemId }) }) }

    fun findItem(itemId: String): Pair<Int, HomeItem>? {
        val l = current?.activeProfile?.layout ?: return null
        l.pages.forEachIndexed { i, p -> p.items.firstOrNull { it.id == itemId }?.let { return i to it } }
        return null
    }

    fun replaceItem(itemId: String, transform: (HomeItem) -> HomeItem) = editLayout { l ->
        l.copy(pages = l.pages.map { p -> p.copy(items = p.items.map { if (it.id == itemId) transform(it) else it }) })
    }

    fun updateWidget(itemId: String, instance: WidgetInstance) = replaceItem(itemId) { if (it is HomeItem.Widget) it.copy(widget = instance) else it }

    /**
     * Verschiebt ein Element im Raster. Landet eine App auf einer anderen App, entsteht ein Ordner;
     * auf einem Ordner wird sie hinzugefügt.
     */
    fun moveItem(itemId: String, page: Int, targetX: Int, targetY: Int, targetPage: Int = page) = editLayout { l ->
        val g = GridEngine(l.columns, l.rows)
        val item = pageItems(l, page).firstOrNull { it.id == itemId } ?: return@editLayout l
        if (targetPage != page) {
            val destItems = pageItems(l, targetPage)
            val pos = g.findNearest(destItems, item.pos.copy(x = targetX, y = targetY)) ?: return@editLayout l
            return@editLayout l.withPage(page, pageItems(l, page).filter { it.id != itemId }).withPage(targetPage, destItems + item.withPos(pos))
        }
        val items = pageItems(l, page)
        val target = items.firstOrNull { it.id != itemId && it.pos.x == targetX && it.pos.y == targetY && it.pos.w == 1 && it.pos.h == 1 }
        if (item is HomeItem.App && target is HomeItem.App) {
            val folder = HomeItem.Folder(newId("i"), target.pos, "Ordner", listOf(target.appKey, item.appKey))
            return@editLayout l.withPage(page, items.filter { it.id != itemId && it.id != target.id } + folder)
        }
        if (item is HomeItem.App && target is HomeItem.Folder) {
            return@editLayout l.withPage(page, items.filter { it.id != itemId }.map { if (it.id == target.id) target.copy(apps = (target.apps + item.appKey).distinct()) else it })
        }
        val moved = g.move(items, itemId, ItemPos(targetX.coerceIn(0, l.columns - item.pos.w), targetY.coerceIn(0, l.rows - item.pos.h), item.pos.w, item.pos.h))
            ?: g.findNearest(items, item.pos.copy(x = targetX, y = targetY), itemId)?.let { p -> items.map { if (it.id == itemId) it.withPos(p) else it } }
            ?: return@editLayout l
        l.withPage(page, moved)
    }

    fun moveFree(itemId: String, fx: Float, fy: Float) = replaceItem(itemId) { item ->
        val l = current!!.activeProfile.layout
        val p = item.pos.freeOrFromGrid(l.columns, l.rows)
        item.withPos(p.copy(fx = fx.coerceIn(0f, 1f - p.fw), fy = fy.coerceIn(0f, 1f - p.fh)))
    }

    fun resizeItem(itemId: String, w: Int, h: Int) = editLayout { l ->
        val (page, _) = findItem(itemId) ?: return@editLayout l
        val g = GridEngine(l.columns, l.rows)
        val resized = g.resize(pageItems(l, page), itemId, w.coerceIn(1, l.columns), h.coerceIn(1, l.rows)) ?: return@editLayout l
        l.withPage(page, resized)
    }

    fun resizeFree(itemId: String, fw: Float, fh: Float) = replaceItem(itemId) { item ->
        val l = current!!.activeProfile.layout
        val p = item.pos.freeOrFromGrid(l.columns, l.rows)
        item.withPos(p.copy(fw = fw.coerceIn(0.08f, 1f - p.fx), fh = fh.coerceIn(0.05f, 1f - p.fy)))
    }

    fun renameFolder(itemId: String, name: String) = replaceItem(itemId) { if (it is HomeItem.Folder) it.copy(name = name) else it }

    fun removeFromFolder(itemId: String, appKey: String) = editLayout { l ->
        val (page, item) = findItem(itemId) ?: return@editLayout l
        val folder = item as? HomeItem.Folder ?: return@editLayout l
        val rest = folder.apps - appKey
        val items = pageItems(l, page)
        val newItems = when {
            rest.size >= 2 -> items.map { if (it.id == itemId) folder.copy(apps = rest) else it }
            rest.size == 1 -> items.map { if (it.id == itemId) HomeItem.App(newId("i"), folder.pos, rest.first()) else it }
            else -> items.filter { it.id != itemId }
        }
        // Herausgenommene App an freier Stelle ablegen
        val g = GridEngine(l.columns, l.rows)
        val free = g.findFree(newItems, 1, 1)
        l.withPage(page, if (free != null) newItems + HomeItem.App(newId("i"), free, appKey) else newItems)
    }

    fun addToDock(key: String) = editLayout { l ->
        val max = current?.activeProfile?.theme?.dock?.maxApps ?: 5
        if (key in l.dockApps || l.dockApps.size >= max.coerceAtLeast(1)) l else l.copy(dockApps = l.dockApps + key)
    }

    fun removeFromDock(key: String) = editLayout { it.copy(dockApps = it.dockApps - key) }

    fun addPage() = editLayout { it.copy(pages = it.pages + HomePage(newId("page"))) }

    fun removePage(index: Int) = editLayout { l ->
        if (l.pages.size <= 1) l else l.copy(pages = l.pages.filterIndexed { i, _ -> i != index }, defaultPage = l.defaultPage.coerceAtMost(l.pages.size - 2))
    }

    fun movePage(from: Int, to: Int) = editLayout { l ->
        if (from !in l.pages.indices || to !in l.pages.indices) l else l.copy(pages = l.pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Ändert das Raster und ordnet Elemente neu an; überzählige Elemente wandern auf eine neue Seite. */
    fun setGrid(columns: Int, rows: Int) = editLayout { l ->
        val g = GridEngine(columns, rows)
        val overflow = mutableListOf<HomeItem>()
        val pages = l.pages.map { p ->
            val (placed, over) = g.reflow(p.items)
            overflow += over
            p.copy(items = placed)
        }.toMutableList()
        if (overflow.isNotEmpty()) {
            val (placed, _) = g.reflow(overflow.map { it.withPos(it.pos.copy(x = 0, y = 0)) })
            pages += HomePage(newId("page"), placed)
        }
        l.copy(columns = columns, rows = rows, pages = pages)
    }

    // ------------------------------------------------------------------ Apps

    fun toggleHidden(key: String) = editProfile { p -> p.copy(hiddenApps = if (key in p.hiddenApps) p.hiddenApps - key else p.hiddenApps + key) }
    fun toggleFavorite(key: String) = editProfile { p -> p.copy(favorites = if (key in p.favorites) p.favorites - key else p.favorites + key) }
    fun setCategory(key: String, category: String?) = editProfile { p ->
        p.copy(categoryOverrides = if (category == null) p.categoryOverrides - key else p.categoryOverrides + (key to category))
    }

    fun setIconOverride(key: String, o: IconOverride?) = editProfile { p ->
        p.copy(iconOverrides = if (o == null) p.iconOverrides - key else p.iconOverrides + (key to o))
    }

    fun recordLaunch(key: String) {
        if (current?.general?.trackAppUsage == false) return
        update { it.copy(launchCounts = it.launchCounts + (key to ((it.launchCounts[key] ?: 0) + 1))) }
    }

    // ------------------------------------------------------------------ Themes, Icons, Widgets

    fun applyTheme(theme: ThemeSpec, withWallpaper: Boolean = true) = editProfile { p ->
        p.copy(theme = theme, wallpaper = if (withWallpaper && theme.wallpaper != null) theme.wallpaper!! else p.wallpaper)
    }

    fun saveThemeAs(name: String): ThemeSpec {
        val p = current!!.activeProfile
        val t = p.theme.copy(id = newId("theme"), name = name.ifBlank { "Eigenes Theme" }, builtIn = false, wallpaper = p.wallpaper)
        update { it.copy(userThemes = it.userThemes + t) }
        return t
    }

    fun upsertUserTheme(t: ThemeSpec) = update { cfg ->
        cfg.copy(userThemes = if (cfg.userThemes.any { it.id == t.id }) cfg.userThemes.map { if (it.id == t.id) t else it } else cfg.userThemes + t)
    }

    fun deleteUserTheme(id: String) = update { it.copy(userThemes = it.userThemes.filter { t -> t.id != id }) }

    fun saveIconPack(name: String) = update { cfg ->
        val p = cfg.activeProfile
        cfg.copy(iconPacks = cfg.iconPacks + IconPack(newId("pack"), name.ifBlank { "Icon-Paket" }, p.theme.icons, p.iconOverrides))
    }

    fun applyIconPack(pack: IconPack) = editProfile { p -> p.copy(theme = p.theme.copy(icons = pack.style), iconOverrides = pack.overrides) }
    fun deleteIconPack(id: String) = update { it.copy(iconPacks = it.iconPacks.filter { p -> p.id != id }) }
    fun importIconPack(pack: IconPack) = update { it.copy(iconPacks = it.iconPacks + pack.copy(id = newId("pack"))) }

    fun upsertCustomWidget(def: CustomWidgetDef) = update { cfg ->
        cfg.copy(customWidgets = if (cfg.customWidgets.any { it.id == def.id }) cfg.customWidgets.map { if (it.id == def.id) def else it } else cfg.customWidgets + def)
    }

    fun deleteCustomWidget(id: String) = update { cfg ->
        cfg.copy(customWidgets = cfg.customWidgets.filter { it.id != id })
    }

    fun saveTemplate(t: WidgetTemplate) = update { cfg -> cfg.copy(widgetTemplates = cfg.widgetTemplates.filter { it.id != t.id } + t) }
    fun deleteTemplate(id: String) = update { it.copy(widgetTemplates = it.widgetTemplates.filter { t -> t.id != id }) }

    fun completeSetup() = update { it.copy(setupCompleted = true) }
}
