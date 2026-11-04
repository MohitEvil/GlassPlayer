package com.glassplayer.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.glassplayer.app.AppViewModel
import com.glassplayer.app.data.SongColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun GradientBackdrop(top: Color, bottom: Color, glowA: Color, glowB: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val a = Offset(w * 0.2f, h * 0.65f)
        drawCircle(Brush.radialGradient(listOf(glowA, Color.Transparent), a, w * 0.8f), w * 0.8f, a)
        val b = Offset(w * 0.85f, h * 0.25f)
        drawCircle(Brush.radialGradient(listOf(glowB, Color.Transparent), b, w * 0.7f), w * 0.7f, b)
    }
}

@Composable
private fun DefaultBackdrop(dark: Boolean) {
    val p = LocalGlass.current
    GradientBackdrop(
        top = p.bgTop,
        bottom = p.bgBottom,
        glowA = if (dark) Color(0x557B3CFF) else Color(0x66FFFFFF),
        glowB = if (dark) Color(0x4430A8FF) else Color(0x55B7E9FF),
    )
}

/** Gradient built from the playing song's artwork colours; fades smoothly between songs. */
@Composable
fun SongBackdrop(colors: SongColors, dark: Boolean, intense: Boolean) {
    val spec = tween<Color>(1100)
    val c1 by animateColorAsState(colors.primary, spec, label = "songC1")
    val c2 by animateColorAsState(colors.secondary, spec, label = "songC2")
    val k = if (intense) 1f else 0.8f
    val top = if (dark) lerp(c1, Color.Black, if (intense) 0.64f else 0.76f)
    else lerp(c1, Color.White, if (intense) 0.60f else 0.70f)
    val bottom = if (dark) lerp(c2, Color.Black, if (intense) 0.78f else 0.86f)
    else lerp(c2, Color.White, 0.60f)
    GradientBackdrop(
        top = top,
        bottom = bottom,
        glowA = c2.copy(alpha = (if (dark) 0.55f else 0.45f) * k),
        glowB = c1.copy(alpha = (if (dark) 0.60f else 0.50f) * k),
    )
}

@Composable
private fun CustomImageBackdrop(vm: AppViewModel, dark: Boolean) {
    val bmp = vm.customBg ?: return
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur((vm.bgBlur * 24f).dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background((if (dark) Color.Black else Color.White).copy(alpha = vm.bgDim))
        )
    }
}

/** The app-wide background: default gradient, song colours, or the user's own image. */
@Composable
fun AppBackground(vm: AppViewModel, dark: Boolean) {
    val song = vm.songColors
    val key = when {
        vm.bgMode == "custom" && vm.customBg != null -> 2
        vm.bgMode == "song" && song != null -> 1
        else -> 0
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = key, animationSpec = tween(600), label = "appBg") { k ->
            when (k) {
                2 -> CustomImageBackdrop(vm, dark)
                1 -> if (song != null) SongBackdrop(song, dark, false) else DefaultBackdrop(dark)
                else -> DefaultBackdrop(dark)
            }
        }
        if (vm.bubbles) BubblesLayer(dark)
    }
}

/** Now Playing background: song colours when enabled, else whatever the app uses. */
@Composable
fun PlayerBackground(vm: AppViewModel, dark: Boolean) {
    val song = vm.songColors
    val adaptive = vm.playerAdaptive && song != null
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = adaptive, animationSpec = tween(500), label = "playerBg") { a ->
            if (a && song != null) {
                Box(Modifier.fillMaxSize()) {
                    SongBackdrop(song, dark, true)
                    if (vm.bubbles) BubblesLayer(dark)
                }
            } else {
                AppBackground(vm, dark)
            }
        }
    }
}

// ---------------------------------------------------------------- bubbles

private class Bubble(
    val x: Float, val y: Float, val r: Float,
    val loops: Int, val phase: Float,
)

private val bubbles = listOf(
    Bubble(0.08f, 0.10f, 0.20f, 1, 0.00f),
    Bubble(0.92f, 0.07f, 0.16f, 2, 0.35f),
    Bubble(0.78f, 0.34f, 0.07f, 3, 0.70f),
    Bubble(0.12f, 0.40f, 0.09f, 2, 0.15f),
    Bubble(0.95f, 0.58f, 0.22f, 1, 0.55f),
    Bubble(0.05f, 0.74f, 0.26f, 1, 0.80f),
    Bubble(0.55f, 0.92f, 0.18f, 2, 0.25f),
    Bubble(0.85f, 0.88f, 0.10f, 3, 0.45f),
    Bubble(0.40f, 0.52f, 0.05f, 3, 0.90f),
)

/** Slowly drifting iridescent bubbles. */
@Composable
fun BubblesLayer(dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "bubbles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val rimColors = if (dark) listOf(
            Color(0x9960E0FF), Color(0x99FF6EC7), Color(0x66FFE08A), Color(0x997B6BFF), Color(0x9960E0FF)
        ) else listOf(
            Color(0xCCFFFFFF), Color(0x8895D8FF), Color(0xAAFFFFFF), Color(0x88C9B8FF), Color(0xCCFFFFFF)
        )
        val tint = if (dark) Color(0xFF8E7BFF) else Color.White
        val rimW = 1.6.dp.toPx()

        for (b in bubbles) {
            val ang = 2f * PI.toFloat() * (t * b.loops + b.phase)
            val c = Offset((b.x + 0.025f * sin(ang)) * w, (b.y + 0.02f * cos(ang)) * h)
            val r = b.r * min(w, h) * 1.6f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, tint.copy(alpha = 0.07f), tint.copy(alpha = 0.22f)),
                    c, r
                ),
                r, c
            )
            drawCircle(Brush.sweepGradient(rimColors, c), r, c, style = Stroke(rimW))
            val hl = Offset(c.x - r * 0.35f, c.y - r * 0.45f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.45f), Color.Transparent),
                    hl, r * 0.35f
                ),
                r * 0.35f, hl
            )
        }
    }
}
