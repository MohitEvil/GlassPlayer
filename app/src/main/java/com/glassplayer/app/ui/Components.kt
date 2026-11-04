package com.glassplayer.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.AppViewModel
import com.glassplayer.app.Dlg
import com.glassplayer.app.Route
import com.glassplayer.app.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ------------------------------------------------------------------ artwork

object ArtLoader {
    private val small = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val large = object : LruCache<Long, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<Long>()

    fun peek(song: Song?, px: Int): Bitmap? =
        song?.let { (if (px > 300) large else small).get(it.id) }

    suspend fun load(ctx: Context, s: Song, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cache = if (px > 300) large else small
        cache.get(s.id)?.let { return@withContext it }
        if (synchronized(missing) { s.id in missing }) return@withContext null

        var bmp: Bitmap? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(ctx, s.uri)
            mmr.embeddedPicture?.let { bmp = decode(it, px) }
        } catch (_: Exception) {
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
        if (bmp == null && Build.VERSION.SDK_INT >= 29) {
            bmp = try {
                ctx.contentResolver.loadThumbnail(s.uri, Size(px, px), null)
            } catch (_: Exception) {
                null
            }
        }
        val result = bmp
        if (result != null) cache.put(s.id, result) else synchronized(missing) { missing.add(s.id) }
        result
    }

    private fun decode(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

@Composable
fun Artwork(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    px: Int = 200,
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(ArtLoader.peek(song, px), song?.id, px) {
        value = if (song == null) null else ArtLoader.load(ctx, song, px)
    }
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFC2E6), Color(0xFFFF8FC8)))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Rounded.MusicNote, null,
                Modifier.fillMaxSize(0.55f),
                tint = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

// ------------------------------------------------------------------ buttons

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fill: Color? = null,
    cap: GlassLevel = if (size <= 40.dp) GlassLevel.Lite else GlassLevel.Frost,
    pressScale: Float = 0.86f,
    content: @Composable BoxScope.() -> Unit,
) {
    Glass(
        modifier.size(size),
        CircleShape,
        tint = fill,
        cap = cap,
        pressScale = pressScale,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = LocalGlass.current.text,
    fill: Color? = null,
    desc: String? = null,
) {
    GlassButton(onClick, modifier, size, fill) {
        Icon(icon, desc, Modifier.align(Alignment.Center).size(size * 0.48f), tint = tint)
    }
}

/** Play/pause glyph that morphs with a springy scale. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), 0.4f) + fadeIn(tween(120))) togetherWith
                (scaleOut(tween(110), 0.4f) + fadeOut(tween(90)))
        },
        label = "playPause",
    ) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            null, Modifier.size(size), tint = tint
        )
    }
}

@Composable
fun EqualizerBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(20.dp)) {
        val bw = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, v ->
            val h = size.height * (if (active) v else 0.3f)
            drawRoundRect(color, Offset(i * bw * 2f, size.height - h), androidx.compose.ui.geometry.Size(bw, h), CornerRadius(bw / 2f))
        }
    }
}

@Composable
fun GlassPillButton(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Glass(modifier.height(54.dp), CircleShape, cap = GlassLevel.Frost, pressScale = 0.93f, onClick = onClick) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ------------------------------------------------------------------ headers

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.Rounded.ChevronLeft, onBack, size = 46.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = p.text,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun SectionTitle(text: String, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Rounded.ChevronRight, null, tint = p.text, modifier = Modifier.size(26.dp))
    }
}

// --------------------------------------------------------------- song lists

@Composable
fun SongRow(
    vm: AppViewModel,
    song: Song,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalGlass.current
    val fav = song.id in vm.favorites
    val menu = buildList {
        add(MenuItem(if (fav) "Remove from favorites" else "Add to favorites") { vm.toggleFavorite(song.id) })
        add(MenuItem("Add to playlist") { vm.dialog = Dlg.AddToPlaylist(song) })
        if (onRemove != null) add(MenuItem("Remove from this playlist", danger = true) { onRemove() })
        add(MenuItem("Song info") { vm.dialog = Dlg.SongInfo(song) })
    }
    val titleColor by animateColorAsState(if (playing) p.blue else p.text, tween(250), label = "rowTitle")

    Glass(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        RoundedCornerShape(20.dp),
        cap = GlassLevel.Frost,
        pressScale = 0.975f,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp)) {
                Artwork(song, Modifier.fillMaxSize(), RoundedCornerShape(14.dp))
                if (playing) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerBars(vm.isPlaying, Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${song.artist}  •  ${song.album}",
                    color = if (playing) p.blue.copy(alpha = 0.85f) else p.textDim,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fav) {
                Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp), tint = p.accent)
                Spacer(Modifier.width(4.dp))
            }
            MenuButton(menu, size = 40.dp)
        }
    }
}

@Composable
fun SongList(
    vm: AppViewModel,
    songs: List<Song>,
    emptyText: String = "Nothing here yet",
    onRemove: ((Song) -> Unit)? = null,
) {
    val p = LocalGlass.current
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = p.textDim, fontSize = 17.sp)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 210.dp)) {
        items(songs, key = { it.id }) { s ->
            SongRow(
                vm, s,
                playing = vm.currentSong?.id == s.id,
                onClick = { vm.play(songs, songs.indexOf(s)) },
                onRemove = onRemove?.let { r -> { r(s) } },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

// -------------------------------------------------------- mini player + nav

@Composable
fun MiniPlayer(vm: AppViewModel, song: Song) {
    val p = LocalGlass.current
    Glass(
        Modifier.fillMaxWidth().height(64.dp),
        CircleShape,
        cap = GlassLevel.Lens,
        pressScale = 0.97f,
        onClick = { vm.nowPlayingOpen = true },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song, Modifier.size(46.dp), CircleShape)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist, color = p.textDim, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            MiniButton(Icons.Rounded.SkipPrevious) { vm.previous() }
            GlassButton({ vm.togglePlay() }, size = 44.dp) {
                Box(Modifier.align(Alignment.Center)) { PlayPauseIcon(vm.isPlaying, p.text, 24.dp) }
            }
            MiniButton(Icons.Rounded.SkipNext) { vm.next() }
            MiniButton(Icons.Rounded.Close) { vm.stopAndClear() }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, onClick: () -> Unit) {
    val p = LocalGlass.current
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = p.text)
    }
}

@Composable
private fun MiniPlayerSlot(vm: AppViewModel) {
    val current = vm.currentSong
    var last by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(current) { if (current != null) last = current }
    val song = current ?: last
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { it } +
            fadeIn(tween(200)) + scaleIn(tween(220), 0.92f),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
    ) {
        Column {
            if (song != null) MiniPlayer(vm, song)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun BottomChrome(vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val tabs = listOf<Route>(Route.Home, Route.Songs, Route.Library)
    val onSearch = vm.current == Route.Search
    val idx = if (onSearch) -1 else tabs.indexOf(vm.stack.first())

    var lastIdx by remember { mutableIntStateOf(0) }
    SideEffect { if (idx >= 0) lastIdx = idx }
    val shown = if (idx >= 0) idx else lastIdx
    val pos by animateFloatAsState(
        shown.toFloat(),
        if (vm.tabAnim) spring(dampingRatio = 0.6f, stiffness = 320f) else tween(180),
        label = "navPos",
    )
    val indicatorAlpha by animateFloatAsState(if (idx >= 0) 1f else 0f, tween(200), label = "navAlpha")

    // The whole bar squashes and rebounds when the tab changes.
    val wiggle = remember { Animatable(0f) }
    var prevIdx by remember { mutableIntStateOf(idx) }
    LaunchedEffect(idx) {
        if (idx != prevIdx && vm.tabAnim) {
            wiggle.snapTo(1f)
            wiggle.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 260f))
        }
        prevIdx = idx
    }

    // Glow colour follows the lens (blending between tabs while it glides).
    val glow = tabGlowColor(vm.tabGlow, pos, p.accent)
    val lensColor = glow ?: p.accent
    fun activeTint(i: Int): Color = when (vm.tabGlow) {
        "tabs" -> TabColors.all[i]
        "white" -> p.text
        else -> p.accent
    }
    val searchTint = when (vm.tabGlow) {
        "tabs" -> TabColors.Search
        "white" -> Color.White
        else -> p.accent
    }

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp),
    ) {
        MiniPlayerSlot(vm)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Glass(
                Modifier
                    .weight(1f)
                    .height(66.dp)
                    .graphicsLayer {
                        val w = wiggle.value
                        scaleX = 1f + 0.045f * w
                        scaleY = 1f - 0.07f * w
                    },
                CircleShape,
                cap = GlassLevel.Lens,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                    val itemW = maxWidth / 3

                    // coloured light pooling behind the lens
                    if (glow != null) {
                        Box(
                            Modifier
                                .offset { IntOffset((itemW.toPx() * pos).roundToInt(), 0) }
                                .width(itemW)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = indicatorAlpha }
                                .drawBehind {
                                    val r = size.width * 0.95f
                                    drawCircle(
                                        Brush.radialGradient(
                                            listOf(glow.copy(alpha = 0.55f), Color.Transparent),
                                            center, r
                                        ),
                                        r, center
                                    )
                                }
                        )
                    }

                    // the glass lens that glides to the selected tab
                    Glass(
                        Modifier
                            .offset { IntOffset((itemW.toPx() * pos).roundToInt(), 0) }
                            .width(itemW)
                            .fillMaxHeight()
                            .graphicsLayer { alpha = indicatorAlpha },
                        CircleShape,
                        tint = lensColor,
                        cap = GlassLevel.Lens,
                        pressScale = 1f,
                    ) {}

                    Row(Modifier.fillMaxSize()) {
                        NavItem("Home", Icons.Rounded.Home, idx == 0, activeTint(0)) { vm.tab(Route.Home) }
                        NavItem("Songs", Icons.Rounded.MusicNote, idx == 1, activeTint(1)) { vm.tab(Route.Songs) }
                        NavItem("Library", Icons.Rounded.LibraryMusic, idx == 2, activeTint(2)) { vm.tab(Route.Library) }
                    }
                }
            }
            GlassButton(
                onClick = { if (!onSearch) vm.go(Route.Search) },
                size = 66.dp,
                fill = if (onSearch) searchTint else null,
                cap = GlassLevel.Lens,
                pressScale = 0.88f,
            ) {
                Icon(
                    Icons.Rounded.Search, null,
                    Modifier.align(Alignment.Center).size(30.dp),
                    tint = if (onSearch && vm.tabGlow != "white") Color.White else p.text,
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val p = LocalGlass.current
    val color by animateColorAsState(if (selected) activeColor else p.textDim, tween(250), label = "navColor")
    val iconScale by animateFloatAsState(
        if (selected) 1.14f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 500f),
        label = "navIcon",
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, null,
                Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                tint = color,
            )
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
