package com.glassplayer.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** One colour per tab, used for the lens tint and glow when "Tab colors" is chosen. */
object TabColors {
    val Home = Color(0xFF3D8BFF)
    val Songs = Color(0xFFFF4B6E)
    val Library = Color(0xFFFFA62B)
    val Search = Color(0xFF34C759)
    val all = listOf(Home, Songs, Library)
}

/**
 * Glow colour for a fractional tab position (0 = Home .. 2 = Library) so it blends while the lens
 * glides between tabs. Null means no glow.
 */
fun tabGlowColor(mode: String, pos: Float, accent: Color): Color? = when (mode) {
    "off" -> null
    "white" -> Color.White
    "accent" -> accent
    else -> {
        val p = pos.coerceIn(0f, 2f)
        val lo = p.toInt().coerceAtMost(1)
        lerp(TabColors.all[lo], TabColors.all[lo + 1], p - lo)
    }
}

/**
 * Light that runs around the screen's edges while searching: a rotating rainbow stroke drawn in a
 * few widths so it falls off softly like a glow.
 */
@Composable
fun EdgeGlow(visible: Boolean, modifier: Modifier = Modifier) {
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "edgeGlowFade")
    if (fade < 0.01f) return

    val angle by rememberInfiniteTransition(label = "edgeGlow").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "edgeGlowAngle",
    )
    val colorInts = remember {
        intArrayOf(
            Color(0xFF4F8CFF).toArgb(),
            Color(0xFFB45CFF).toArgb(),
            Color(0xFFFF4B8B).toArgb(),
            Color(0xFFFFA64B).toArgb(),
            Color(0xFF4F8CFF).toArgb(),
        )
    }

    Canvas(modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val sweep = android.graphics.SweepGradient(cx, cy, colorInts, null)
        sweep.setLocalMatrix(android.graphics.Matrix().apply { setRotate(angle, cx, cy) })
        val brush = ShaderBrush(sweep)
        val corner = CornerRadius(42.dp.toPx())

        val layers = listOf(34.dp to 0.07f, 22.dp to 0.12f, 12.dp to 0.22f, 5.dp to 0.55f, 2.dp to 0.95f)
        for ((width, a) in layers) {
            val sw = width.toPx()
            drawRoundRect(
                brush = brush,
                topLeft = Offset(sw / 2f, sw / 2f),
                size = Size(size.width - sw, size.height - sw),
                cornerRadius = corner,
                alpha = a * fade,
                style = Stroke(sw),
            )
        }
    }
}
