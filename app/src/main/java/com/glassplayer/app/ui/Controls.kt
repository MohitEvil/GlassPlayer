package com.glassplayer.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Liquid toggle: the thumb stretches while pressed and springs into place. */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "swT")
    val thumbW by animateDpAsState(if (pressed) 40.dp else 30.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "swW")
    val track = lerp(p.text.copy(alpha = 0.14f), p.accent, t)

    Box(
        modifier
            .size(62.dp, 36.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, Brush.linearGradient(listOf(p.borderA, p.borderB)), CircleShape)
            .clickable(interactionSource = source, indication = null) { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset { IntOffset(((56.dp - thumbW).toPx() * t).roundToInt(), 0) }
                .size(thumbW, 30.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE4ECF8))))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
    }
}

/** Liquid slider with a glass bead thumb that swells while dragged. */
@Composable
fun GlassSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    onFinished: () -> Unit = {},
    glow: Boolean = false,
) {
    val p = LocalGlass.current
    val onChangeNow by rememberUpdatedState(onChange)
    val onFinishedNow by rememberUpdatedState(onFinished)
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (dragging) 1.3f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "thumb")
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pad = 14.dp.toPx()
                    dragging = true
                    fun emit(x: Float) {
                        val f = ((x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onChangeNow(range.start + f * span)
                    }
                    emit(down.position.x)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        emit(c.position.x)
                        c.consume()
                    }
                    dragging = false
                    onFinishedNow()
                }
            }
    ) {
        val pad = 14.dp.toPx()
        val trackH = 8.dp.toPx()
        val cy = size.height / 2f
        val w = size.width - 2 * pad
        val corner = CornerRadius(trackH / 2f)

        drawRoundRect(p.text.copy(alpha = 0.16f), Offset(pad, cy - trackH / 2f), Size(w, trackH), corner)
        drawRoundRect(
            Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.7f), p.accent), startX = pad, endX = pad + w),
            Offset(pad, cy - trackH / 2f),
            Size(w * frac, trackH),
            corner,
        )

        val c = Offset(pad + w * frac, cy)
        val r = 13.dp.toPx() * grow
        if (glow) {
            // light spilling along the filled part of the track, pooling into a halo at the thumb
            val tail = 70.dp.toPx().coerceAtMost(w * frac)
            if (tail > 1f) {
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, p.accent.copy(alpha = 0.50f)),
                        startX = pad + w * frac - tail,
                        endX = pad + w * frac,
                    ),
                    Offset(pad + w * frac - tail, cy - trackH * 1.2f),
                    Size(tail, trackH * 2.4f),
                    CornerRadius(trackH * 1.2f),
                )
            }
            val halo = 30.dp.toPx() * grow
            drawCircle(
                Brush.radialGradient(listOf(p.accent.copy(alpha = 0.60f), Color.Transparent), c, halo),
                halo, c
            )
        }
        drawCircle(p.accent.copy(alpha = 0.30f * ((grow - 1f) / 0.3f).coerceIn(0f, 1f)), r * 1.7f, c)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Color(0xFFE6EDF8)),
                Offset(c.x - r * 0.25f, c.y - r * 0.3f),
                r * 1.4f,
            ),
            r, c
        )
        drawCircle(p.accent.copy(alpha = 0.92f), r * 0.38f, c)
        drawCircle(Color.White.copy(alpha = 0.75f), r, c, style = Stroke(1.dp.toPx()))
    }
}
