package com.glassplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.AppViewModel
import com.glassplayer.app.Dlg
import com.glassplayer.app.Kind
import com.glassplayer.app.Route
import com.glassplayer.app.SortMode
import com.glassplayer.app.data.Group
import com.glassplayer.app.data.Song

private fun settingsMenu(vm: AppViewModel) = listOf(
    MenuItem("Settings") { vm.go(Route.Settings) },
    MenuItem("Rescan library") { vm.reload() },
)

// ---------------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = LocalGlass.current
    LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
        item {
            ScreenHeader("Home") {
                GlassIconButton(Icons.Rounded.Add, { vm.dialog = Dlg.NewPlaylist() })
                Spacer(Modifier.width(10.dp))
                MenuButton(settingsMenu(vm))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPillButton("Favorites", Icons.Rounded.Favorite, p.accent, Modifier.weight(1f)) {
                    vm.go(Route.Detail(Kind.Favorites, "fav", "Favorites"))
                }
                GlassPillButton("Shuffle", Icons.Rounded.Shuffle, p.blue, Modifier.weight(1f)) {
                    vm.shuffleAll()
                }
            }
        }
        item { SectionTitle("Playlists") { vm.go(Route.Playlists) } }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.playlistGroups, key = { it.key }) { g ->
                    MediaCard(g.name, plural(g.songs.size, "song"), g.songs.firstOrNull()) {
                        vm.go(Route.Detail(Kind.Playlist, g.key, g.name))
                    }
                }
            }
        }
        item { SectionTitle("Recently Added") { vm.go(Route.Detail(Kind.Recent, "recent", "Recently added")) } }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.recent, key = { it.id }) { s ->
                    MediaCard(s.title, s.artist, s) {
                        vm.play(vm.recent, vm.recent.indexOf(s))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    art: com.glassplayer.app.data.Song?,
    onClick: () -> Unit,
) {
    val p = LocalGlass.current
    Glass(Modifier.width(118.dp), RoundedCornerShape(22.dp), cap = GlassLevel.Frost, pressScale = 0.94f, onClick = onClick) {
        Column {
            Artwork(
                art,
                Modifier.fillMaxWidth().aspectRatio(1f),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                px = 256,
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle, color = p.textDim, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --------------------------------------------------------------------- Songs

@Composable
fun SongsScreen(vm: AppViewModel) {
    val p = LocalGlass.current
    val list = if (vm.favoritesOnly) vm.sortedSongs.filter { it.id in vm.favorites } else vm.sortedSongs
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Songs") { MenuButton(settingsMenu(vm)) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                plural(list.size, "song"), color = p.textDim, fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(Icons.Rounded.Shuffle, { vm.playShuffled(list) })
            Spacer(Modifier.width(10.dp))
            GlassIconButton(
                if (vm.favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                { vm.favoritesOnly = !vm.favoritesOnly },
                tint = if (vm.favoritesOnly) p.accent else p.text,
            )
            Spacer(Modifier.width(10.dp))
            SortButton(vm)
        }
        SongList(
            vm, list,
            emptyText = if (vm.favoritesOnly) "No favorites yet" else "No songs found",
        )
    }
}

@Composable
private fun SortButton(vm: AppViewModel) {
    MenuButton(
        items = SortMode.entries.map { m ->
            MenuItem(m.label, checked = m == vm.sortMode) { vm.setSort(m) }
        },
        icon = Icons.Rounded.FilterList,
    )
}

// ------------------------------------------------------------------- Library

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val p = LocalGlass.current
    val rows: List<Triple<ImageVector, String, Route>> = listOf(
        Triple(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists", Route.Playlists),
        Triple(Icons.Rounded.Mic, "Artists", Route.Artists),
        Triple(Icons.Rounded.Album, "Albums", Route.Albums),
        Triple(Icons.Rounded.Headphones, "Genres", Route.Genres),
        Triple(Icons.Rounded.Folder, "Folders", Route.Folders),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Library") { MenuButton(settingsMenu(vm)) }
        Glass(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            RoundedCornerShape(30.dp),
        ) {
            Column {
                rows.forEachIndexed { i, (icon, label, route) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { vm.go(route) }
                            .padding(horizontal = 18.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, Modifier.size(28.dp), tint = p.accent)
                        Spacer(Modifier.width(18.dp))
                        Text(
                            label, color = p.text, fontSize = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ChevronRight, null, tint = p.textDim)
                    }
                    if (i < rows.lastIndex) {
                        HorizontalDivider(
                            Modifier.padding(start = 64.dp),
                            color = p.textDim.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------- Playlists/Artists/etc.

@Composable
fun GroupListScreen(vm: AppViewModel, title: String, kind: Kind, groups: List<Group>) {
    val p = LocalGlass.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = vm::back) {
            if (kind == Kind.Playlist) {
                GlassIconButton(Icons.Rounded.Add, { vm.dialog = Dlg.NewPlaylist() })
                Spacer(Modifier.width(10.dp))
            }
            MenuButton(settingsMenu(vm))
        }
        Text(
            plural(groups.size, "item"), color = p.textDim, fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = p.textDim, fontSize = 17.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 210.dp)) {
                items(groups, key = { it.key }) { g -> GroupRow(vm, kind, g, Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun GroupRow(vm: AppViewModel, kind: Kind, g: Group, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val menu = buildList {
        add(MenuItem("Play") { vm.play(g.songs, 0) })
        add(MenuItem("Shuffle") { vm.playShuffled(g.songs) })
        if (kind == Kind.Playlist) {
            add(MenuItem("Rename") { vm.dialog = Dlg.RenamePlaylist(g.key, g.name) })
            add(MenuItem("Delete") { vm.dialog = Dlg.DeletePlaylist(g.key, g.name) })
        }
    }
    Glass(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        RoundedCornerShape(24.dp),
        cap = GlassLevel.Frost,
        pressScale = 0.975f,
        onClick = { vm.go(Route.Detail(kind, g.key, g.name)) },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(g.songs.firstOrNull(), Modifier.size(60.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.name, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(plural(g.songs.size, "song"), color = p.textDim, fontSize = 16.sp)
            }
            MenuButton(menu, size = 40.dp)
        }
    }
}

// -------------------------------------------------------------------- Detail

@Composable
fun DetailScreen(vm: AppViewModel, r: Route.Detail) {
    val p = LocalGlass.current
    val songs = vm.detailSongs(r)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(r.title, onBack = vm::back) {
            if (songs.isNotEmpty()) {
                GlassIconButton(Icons.Rounded.PlayArrow, { vm.play(songs, 0) })
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Icons.Rounded.Shuffle, { vm.playShuffled(songs) })
            }
            if (r.kind == Kind.Playlist) {
                Spacer(Modifier.width(10.dp))
                MenuButton(
                    listOf(
                        MenuItem("Rename") { vm.dialog = Dlg.RenamePlaylist(r.key, r.title) },
                        MenuItem("Delete") { vm.dialog = Dlg.DeletePlaylist(r.key, r.title) },
                    )
                )
            }
        }
        Text(
            plural(songs.size, "song"), color = p.textDim, fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        val remover = { s: Song -> vm.removeFromPlaylist(r.key, s.id) }
        SongList(
            vm, songs,
            emptyText = if (r.kind == Kind.Playlist) "Add songs from the ⋮ menu on any track" else "Nothing here yet",
            onRemove = remover.takeIf { r.kind == Kind.Playlist },
        )
    }
}

// -------------------------------------------------------------------- Search

@Composable
fun SearchScreen(vm: AppViewModel) {
    val p = LocalGlass.current
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val results = remember(q, vm.songs) {
        val needle = q.trim()
        if (needle.isEmpty()) emptyList()
        else vm.songs.filter {
            it.title.contains(needle, true) || it.artist.contains(needle, true) ||
                it.album.contains(needle, true)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.Rounded.ChevronLeft, { vm.back() }, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Glass(Modifier.weight(1f).height(48.dp), CircleShape, cap = GlassLevel.Frost) {
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(p.accent),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        if (q.isEmpty()) Text("Search songs, artists, albums", color = p.textDim, fontSize = 17.sp)
                        inner()
                    },
                )
            }
        }
        SongList(vm, results, emptyText = if (q.isBlank()) "Type to search your music" else "No results")
    }
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val p = LocalGlass.current
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            GlassIconButton(
                Icons.Rounded.ChevronLeft, { vm.back() },
                Modifier.align(Alignment.CenterStart), size = 48.dp
            )
            Text(
                "Settings", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(14.dp))
        SettingsGroup(
            listOf(
                SettingsRowData(Icons.Rounded.Palette, "Display options") { vm.dialog = Dlg.Display },
                SettingsRowData(Icons.Rounded.AutoAwesome, "Glass and motion") { vm.dialog = Dlg.GlassMotion },
                SettingsRowData(Icons.Rounded.Tune, "Playback") { vm.dialog = Dlg.Playback },
                SettingsRowData(Icons.Rounded.Folder, "Library") { vm.dialog = Dlg.LibraryOptions },
            )
        )
        Spacer(Modifier.height(18.dp))
        SettingsGroup(
            listOf(
                SettingsRowData(Icons.Rounded.PrivacyTip, "Privacy policy") { vm.dialog = Dlg.Privacy },
                SettingsRowData(Icons.Rounded.Security, "Permissions") { vm.dialog = Dlg.Permissions },
                SettingsRowData(Icons.Rounded.Translate, "Language") { vm.dialog = Dlg.Language },
                SettingsRowData(Icons.Rounded.Info, "About") { vm.dialog = Dlg.About },
            )
        )
    }
}

private class SettingsRowData(val icon: ImageVector, val label: String, val action: () -> Unit)

@Composable
private fun SettingsGroup(rows: List<SettingsRowData>) {
    val p = LocalGlass.current
    Glass(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(30.dp)) {
        Column {
            rows.forEachIndexed { i, row ->
                val icon = row.icon
                val label = row.label
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickableNoRipple(row.action)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glass(Modifier.size(44.dp), RoundedCornerShape(14.dp), cap = GlassLevel.Lite) {
                        Icon(icon, null, Modifier.align(Alignment.Center).size(24.dp), tint = p.text)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(label, color = p.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = p.textDim)
                }
                if (i < rows.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = p.textDim.copy(alpha = 0.25f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Permission

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    val p = LocalGlass.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Glass(Modifier.fillMaxWidth(), RoundedCornerShape(34.dp), base = p.dialogBase) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.Headphones, null, Modifier.size(56.dp), tint = p.accent)
                Text("Your music, on your device", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    "Glass Player needs access to the audio files on this device so it can build your library. Nothing ever leaves your phone.",
                    color = p.textDim, fontSize = 16.sp, textAlign = TextAlign.Center
                )
                PinkButton("Allow access", onRequest, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun PinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    Box(
        modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(p.accent)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
