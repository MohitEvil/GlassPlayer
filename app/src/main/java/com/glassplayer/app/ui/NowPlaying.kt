package com.glassplayer.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.glassplayer.app.AppViewModel
import com.glassplayer.app.Dlg
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(vm: AppViewModel) {
    val base = LocalGlass.current
    val song = vm.currentSong ?: return
    val sc = vm.songColors

    // Player accent follows the artwork (when enabled), otherwise the app accent.
    val accentTarget = if (vm.playerAdaptive && sc != null) accentOf(sc.primary, base.dark) else base.accent
    val accent by animateColorAsState(accentTarget, tween(900), label = "playerAccent")
    val p = remember(base, accent) { base.copy(accent = accent) }
    val layer = rememberGraphicsLayer()

    val view = LocalView.current
    val keepOn = vm.keepScreenOn && vm.isPlaying
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val fav = song.id in vm.favorites

    CompositionLocalProvider(LocalGlass provides p, LocalBackdrop provides layer) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = 1f - (offsetY.value / 1800f).coerceIn(0f, 0.35f)
                }
                .clickableNoRipple { /* swallow taps so nothing underneath reacts */ }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY.value > 220f) {
                                vm.nowPlayingOpen = false
                            } else {
                                scope.launch { offsetY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                        },
                    ) { _, amount ->
                        scope.launch { offsetY.snapTo((offsetY.value + amount).coerceAtLeast(0f)) }
                    }
                }
        ) {
            Box(Modifier.fillMaxSize().recordTo(layer)) { PlayerBackground(vm, p.dark) }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val artSize = minOf(maxWidth - 40.dp, maxHeight * 0.42f)

                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(Icons.Rounded.ChevronLeft, { vm.nowPlayingOpen = false }, size = 46.dp)
                        Spacer(Modifier.weight(1f))
                        HeartButton(fav) { vm.toggleFavorite(song.id) }
                        Spacer(Modifier.size(10.dp))
                        MenuButton(
                            listOf(
                                MenuItem("Add to playlist") { vm.dialog = Dlg.AddToPlaylist(song) },
                                MenuItem("Song info") { vm.dialog = Dlg.SongInfo(song) },
                                MenuItem("Stop and close", danger = true) { vm.stopAndClear() },
                            ),
                            size = 46.dp,
                        )
                    }

                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ArtRing(vm, artSize)
                    }

                    Glass(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                            .navigationBarsPadding(),
                        RoundedCornerShape(40.dp),
                        base = if (p.dark) Color(0x40000000) else Color(0x26FFFFFF),
                        cap = GlassLevel.Lens,
                    ) {
                        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                            AnimatedContent(
                                targetState = song.title to song.artist,
                                transitionSpec = {
                                    (slideInVertically { it / 3 } + fadeIn(tween(260))) togetherWith
                                        (slideOutVertically { -it / 3 } + fadeOut(tween(160)))
                                },
                                label = "title",
                            ) { (title, artist) ->
                                Column {
                                    Text(
                                        title, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        artist, color = p.textDim, fontSize = 22.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            SeekBar(vm)
                            Spacer(Modifier.height(10.dp))
                            // "Frosted player buttons" off drops the buttons to the cheap fill.
                            CompositionLocalProvider(
                                LocalFx provides (if (vm.frostedButtons) LocalFx.current else GlassLevel.Lite)
                            ) { Controls(vm) }
                            Spacer(Modifier.height(18.dp))
                            ActionPill(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartButton(fav: Boolean, onClick: () -> Unit) {
    val p = LocalGlass.current
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(fav) {
        if (fav) {
            bounce.snapTo(0.55f)
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
    }
    Box(Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }) {
        GlassIconButton(
            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            onClick,
            size = 46.dp,
            tint = if (fav) p.accent else p.text,
        )
    }
}

@Composable
private fun ArtRing(vm: AppViewModel, size: Dp) {
    val p = LocalGlass.current
    val scale by animateFloatAsState(
        if (vm.isPlaying) 1f else 0.93f,
        spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "artScale",
    )
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse",
    )
    // Record-like spin: runs while playing, holds its angle on pause.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(vm.isPlaying) {
        if (vm.isPlaying) {
            while (true) {
                val from = spin.value % 360f
                spin.snapTo(from)
                spin.animateTo(from + 360f, tween(26_000, easing = LinearEasing))
            }
        }
    }
    val rim = if (p.dark) listOf(
        Color(0xCC60E0FF), Color(0xCCFF6EC7), Color(0x99FFE08A), Color(0xCC7B6BFF), Color(0xCC60E0FF)
    ) else listOf(
        Color(0xFFFFFFFF), Color(0xAA95D8FF), Color(0xEEFFFFFF), Color(0xAAC9B8FF), Color(0xFFFFFFFF)
    )
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        // soft glow in the song's colour
        Canvas(Modifier.requiredSize(size * 1.6f)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    listOf(p.accent.copy(alpha = if (vm.isPlaying) pulse else 0.16f), Color.Transparent),
                    c, r
                ),
                r, c
            )
        }
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin.value }) {
            Crossfade(vm.currentSong, animationSpec = tween(450), label = "art") { s ->
                Artwork(s, Modifier.fillMaxSize().padding(size * 0.045f), CircleShape, px = 640)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f - 2.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (p.dark) 0.10f else 0.25f)),
                    c, r
                ),
                r, c
            )
            drawCircle(Brush.sweepGradient(rim, c), r, c, style = Stroke(this.size.minDimension * 0.028f))
            drawCircle(
                Color.White.copy(alpha = 0.35f), r - this.size.minDimension * 0.028f, c,
                style = Stroke(1.dp.toPx())
            )
        }
    }
}

@Composable
private fun SeekBar(vm: AppViewModel) {
    val p = LocalGlass.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = maxOf(vm.durationMs, 1L)
    val frac = dragging ?: (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f)

    GlassSlider(
        value = frac,
        onChange = { dragging = it },
        onFinished = {
            dragging?.let { vm.seekTo((it * dur).toLong()) }
            dragging = null
        },
        glow = vm.progressGlow,
    )
    Row(Modifier.fillMaxWidth()) {
        Text(
            formatTime(dragging?.let { (it * dur).toLong() } ?: vm.positionMs),
            color = p.textDim, fontSize = 15.sp, modifier = Modifier.weight(1f)
        )
        Text(formatTime(vm.durationMs), color = p.textDim, fontSize = 15.sp)
    }
}

@Composable
private fun Controls(vm: AppViewModel) {
    val p = LocalGlass.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            Icons.Rounded.Shuffle, { vm.toggleShuffle() }, size = 54.dp,
            tint = if (vm.shuffleOn) p.accent else p.text,
            fill = if (vm.shuffleOn) p.accent else null,
        )
        GlassIconButton(Icons.Rounded.SkipPrevious, { vm.previous() }, size = 64.dp)
        GlassButton({ vm.togglePlay() }, size = 84.dp, fill = p.accent, cap = GlassLevel.Lens, pressScale = 0.9f) {
            Box(Modifier.align(Alignment.Center)) { PlayPauseIcon(vm.isPlaying, Color.White, 42.dp) }
        }
        GlassIconButton(Icons.Rounded.SkipNext, { vm.next() }, size = 64.dp)
        GlassIconButton(
            if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            { vm.cycleRepeat() }, size = 54.dp,
            tint = if (vm.repeatMode != Player.REPEAT_MODE_OFF) p.accent else p.text,
            fill = if (vm.repeatMode != Player.REPEAT_MODE_OFF) p.accent else null,
        )
    }
}

@Composable
private fun ActionPill(vm: AppViewModel) {
    val p = LocalGlass.current
    val song = vm.currentSong ?: return
    Glass(Modifier.fillMaxWidth().height(60.dp), CircleShape, cap = GlassLevel.Frost) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillIcon(Icons.AutoMirrored.Rounded.QueueMusic, p.text) { vm.dialog = Dlg.Queue }
            PillIcon(Icons.AutoMirrored.Rounded.VolumeUp, p.text) { vm.dialog = Dlg.Volume }
            PillIcon(Icons.Rounded.Timer, if (vm.sleepEndsAt > 0) p.accent else p.text) { vm.dialog = Dlg.Sleep }
            PillIcon(Icons.Rounded.GraphicEq, if (vm.eightOn) p.accent else p.text) { vm.dialog = Dlg.EightD }
            PillIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, p.text) { vm.dialog = Dlg.AddToPlaylist(song) }
        }
    }
}

@Composable
private fun PillIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val color by animateColorAsState(tint, tween(250), label = "pillTint")
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = color)
    }
}
