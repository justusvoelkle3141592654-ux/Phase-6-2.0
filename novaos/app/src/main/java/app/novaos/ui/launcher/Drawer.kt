package app.novaos.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novaos.design.ChipRow
import app.novaos.design.Nova
import app.novaos.design.NovaIconButton
import app.novaos.design.NovaSurface
import app.novaos.design.dotGrid
import app.novaos.model.AppCategories
import app.novaos.model.AppEntry
import app.novaos.model.AppSearch
import app.novaos.model.DrawerStyle
import app.novaos.model.HomeLayout
import app.novaos.model.SearchBarStyle
import app.novaos.ui.common.AppIconImage
import app.novaos.ui.common.LocalConfig
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.common.LocalProfile
import app.novaos.ui.common.tr
import kotlinx.coroutines.launch

private const val FILTER_ALL = "__all"
private const val FILTER_FAV = "__fav"
private const val FILTER_WORK = "__work"

/** Nova App Drawer: Suche, Favoriten, Kategorien, Ordner, alphabetische Navigation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(vm: LauncherViewModel, actions: LauncherActions) {
    val c = LocalContainer.current
    val profile = LocalProfile.current
    val config = LocalConfig.current
    val spec = profile.theme.drawer
    val all by vm.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FILTER_ALL) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (spec.searchAutoFocus) runCatching { focus.requestFocus() } }

    val visible = remember(all, profile.hiddenApps, profile.categoryOverrides) {
        all.filter { it.key !in profile.hiddenApps }.map { a -> a.toEntry().copy(category = profile.categoryOverrides[a.key] ?: a.category) }
    }
    val folders = profile.drawerFolders
    val hasWork = all.any { it.isWorkProfile }
    val filtered = remember(visible, query, filter, spec.sortMode, config.launchCounts, profile.customOrder, profile.favorites) {
        val base = when {
            filter == FILTER_FAV -> visible.filter { it.key in profile.favorites }
            filter == FILTER_WORK -> visible.filter { it.isWorkProfile }
            filter.startsWith("folder:") -> folders.firstOrNull { "folder:${it.id}" == filter }?.let { f -> visible.filter { it.key in f.apps } }.orEmpty()
            filter != FILTER_ALL -> visible.filter { it.category == filter }
            else -> visible
        }
        if (query.isNotBlank()) AppSearch.search(base, query) else AppSearch.sort(base, spec.sortMode, config.launchCounts, profile.customOrder)
    }

    // Nach unten ziehen am Listenanfang schließt den Drawer.
    var pull by remember { mutableFloatStateOf(0f) }
    val closeConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0) { pull += available.y; if (pull > 220f) { pull = 0f; vm.close() } } else pull = 0f
                return Offset.Zero
            }
        }
    }

    val bg = spec.background
    NovaSurface(Modifier.fillMaxSize(), RoundedCornerShape(0.dp), bg, spec.transparency, borderWidth = 0.dp, shadow = 0f) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
            // Griff + Suche
            Box(
                Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d > 25) vm.close() } },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Nova.colors.text.copy(alpha = 0.3f))) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchBar(query, { query = it }, spec.searchBar, Modifier.weight(1f).focusRequester(focus))
                NovaIconButton(Icons.Filled.Tune, tr("Drawer anpassen", "Customize drawer"), { actions.openSettings("drawer") })
            }
            if (spec.showCategories && query.isBlank()) {
                val options = buildList {
                    add(FILTER_ALL); if (profile.favorites.isNotEmpty()) add(FILTER_FAV)
                    addAll(AppCategories.all.filter { cat -> visible.any { it.category == cat } })
                    folders.forEach { add("folder:${it.id}") }
                    if (hasWork) add(FILTER_WORK)
                }
                Spacer(Modifier.height(10.dp))
                ChipRow(options, filter, { o ->
                    when {
                        o == FILTER_ALL -> tr("Alle", "All")
                        o == FILTER_FAV -> tr("Favoriten", "Favorites")
                        o == FILTER_WORK -> tr("Arbeit", "Work")
                        o.startsWith("folder:") -> folders.firstOrNull { "folder:${it.id}" == o }?.name ?: "?"
                        else -> o
                    }
                }, { filter = it }, Modifier.padding(horizontal = 16.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (filter == FILTER_ALL && query.isBlank() && profile.favorites.isNotEmpty() && spec.style != DrawerStyle.LIST) {
                FavoritesRow(vm, actions, profile.favorites.filter { k -> visible.any { it.key == k } }, spec.iconSizeDp)
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) tr("Keine Apps", "No apps") else tr("Keine Treffer für „$query“", "No results for “$query”"), color = Nova.colors.textSecondary, style = Nova.type.body)
                }
            }
            val layoutForCells = HomeLayout(iconSizeDp = spec.iconSizeDp, showLabels = spec.showLabels)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight().nestedScroll(closeConnection)) {
                    when (spec.style) {
                        DrawerStyle.VERTICAL_GRID -> GridApps(vm, actions, filtered, spec.columns, spec.iconSpacingDp, layoutForCells, query.isBlank() && spec.showAlphabetIndex)
                        DrawerStyle.LIST -> ListApps(vm, actions, filtered, query.isBlank() && spec.showAlphabetIndex)
                        DrawerStyle.PAGED -> PagedApps(vm, actions, filtered, spec.columns, layoutForCells)
                    }
                }
            }
            if (query.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.novaos.design.NovaButton(tr("Im Web suchen", "Search web"), {
                        actions.startSafely(android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query))
                    }, primary = false)
                }
            }
        }
    }
}

@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, style: SearchBarStyle, modifier: Modifier = Modifier, placeholder: String = tr("Apps suchen …", "Search apps …")) {
    val c = Nova.colors
    val shape = when (style) {
        SearchBarStyle.PILL, SearchBarStyle.GLASS -> RoundedCornerShape(24.dp)
        SearchBarStyle.UNDERLINE -> RoundedCornerShape(0.dp)
        SearchBarStyle.DOT_FRAME -> RoundedCornerShape(12.dp)
    }
    val base = Modifier.height(48.dp).clip(shape)
    val styled = when (style) {
        SearchBarStyle.PILL -> base.background(c.text.copy(alpha = 0.09f))
        SearchBarStyle.GLASS -> base.background(c.surface.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.18f), shape)
        SearchBarStyle.UNDERLINE -> base
        SearchBarStyle.DOT_FRAME -> base.dotGrid(c.text.copy(alpha = 0.12f), 6.dp, 0.7.dp).border(1.dp, c.text.copy(alpha = 0.3f), shape)
    }
    Box(modifier.then(styled).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = c.textSecondary, style = Nova.type.body)
                BasicTextField(value, onChange, singleLine = true, textStyle = Nova.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) Icon(Icons.Filled.Close, tr("Löschen", "Clear"), tint = c.textSecondary, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
        if (style == SearchBarStyle.UNDERLINE) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(c.text.copy(alpha = 0.4f)))
    }
}

@Composable
private fun FavoritesRow(vm: LauncherViewModel, actions: LauncherActions, keys: List<String>, iconSize: Float) {
    if (keys.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height((iconSize + 30).dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        keys.take(5).forEach { k ->
            Box(Modifier.weight(1f).fillMaxHeight()) { AppCell(vm, actions, k, HomeLayout(iconSizeDp = iconSize), editMode = false) }
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).height(1.dp).background(Nova.colors.text.copy(alpha = 0.1f)))
}

@Composable
private fun GridApps(vm: LauncherViewModel, actions: LauncherActions, apps: List<AppEntry>, columns: Int, spacing: Float, layout: HomeLayout, index: Boolean) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val sections = remember(apps) { AppSearch.sections(apps) }
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(columns.coerceIn(3, 7)),
            state = state,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
        ) {
            items(apps.size, key = { apps[it].key }) { i ->
                Box(Modifier.fillMaxWidth().height((layout.iconSizeDp + 30).dp)) { AppCell(vm, actions, apps[i].key, layout, editMode = false) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(40.dp)) }
        }
        if (index && sections.size > 3) AlphabetIndex(sections.map { it.first }) { letter ->
            sections.firstOrNull { it.first == letter }?.let { scope.launch { state.scrollToItem(it.second) } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListApps(vm: LauncherViewModel, actions: LauncherActions, apps: List<AppEntry>, index: Boolean) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = remember(apps) { AppSearch.sections(apps) }
    val view = androidx.compose.ui.platform.LocalView.current
    Row(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            items(apps, key = { it.key }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = { actions.launchApp(a.key, null, view) }, onLongClick = { vm.open(Overlay.AppMenu(a.key)) })
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(a.key, 36.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, style = Nova.type.body.copy(fontSize = 16.sp), color = Nova.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (a.isWorkProfile) Text(tr("Arbeit", "Work"), style = Nova.type.caption, color = Nova.colors.accent)
                }
            }
        }
        if (index && sections.size > 3) AlphabetIndex(sections.map { it.first }) { letter ->
            sections.firstOrNull { it.first == letter }?.let { scope.launch { state.scrollToItem(it.second) } }
        }
    }
}

@Composable
private fun PagedApps(vm: LauncherViewModel, actions: LauncherActions, apps: List<AppEntry>, columns: Int, layout: HomeLayout) {
    val perPage = columns * 5
    val pages = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
    val state = rememberPagerState { pages }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state, Modifier.weight(1f)) { p ->
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                apps.drop(p * perPage).take(perPage).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { a -> Box(Modifier.weight(1f).fillMaxHeight()) { AppCell(vm, actions, a.key, layout, editMode = false) } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                repeat(5 - (apps.drop(p * perPage).take(perPage).size + columns - 1) / columns) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages) { i -> Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape).background(if (i == state.currentPage) Nova.colors.text else Nova.colors.text.copy(alpha = 0.3f))) }
        }
    }
}

/** Buchstabenleiste: Tippen oder Ziehen springt zum Abschnitt. */
@Composable
private fun AlphabetIndex(letters: List<Char>, onLetter: (Char) -> Unit) {
    var active by remember { mutableStateOf<Char?>(null) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    Column(
        Modifier.width(26.dp).fillMaxHeight().padding(vertical = 8.dp)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragEnd = { active = null }) { ch, _ ->
                    val idx = ((ch.position.y / heightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
                    if (letters[idx] != active) { active = letters[idx]; onLetter(letters[idx]) }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l ->
            Text(
                l.toString(), style = Nova.type.caption.copy(fontSize = 10.sp, fontWeight = if (l == active) FontWeight.Bold else FontWeight.Normal),
                color = if (l == active) Nova.colors.accent else Nova.colors.textSecondary,
                modifier = Modifier.clickable { onLetter(l) },
            )
        }
    }
}

