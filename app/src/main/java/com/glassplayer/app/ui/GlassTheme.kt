package com.glassplayer.app.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Immutable
data class GlassPalette(
    val dark: Boolean,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val blue: Color,
    /** Fill for the cheap gradient-only glass. */
    val fillTop: Color,
    val fillBottom: Color,
    /** Lighter fill used on top of a real blurred backdrop. */
    val glassTop: Color,
    val glassBottom: Color,
    val borderA: Color,
    val borderB: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val dialogBase: Color,
)

val DarkPalette = GlassPalette(
    dark = true,
    text = Color.White,
    textDim = Color(0xB3FFFFFF),
    accent = Color(0xFFFF4B6E),
    blue = Color(0xFF5B9BFF),
    fillTop = Color(0x2EFFFFFF),
    fillBottom = Color(0x0FFFFFFF),
    glassTop = Color(0x29FFFFFF),
    glassBottom = Color(0x0FFFFFFF),
    borderA = Color(0x99FFFFFF),
    borderB = Color(0x404FD8FF),
    bgTop = Color(0xFF04040C),
    bgBottom = Color(0xFF0D0824),
    dialogBase = Color(0x8C0B0C18),
)

val LightPalette = GlassPalette(
    dark = false,
    text = Color(0xFF0B1A33),
    textDim = Color(0xFF5E6E85),
    accent = Color(0xFFE8325A),
    blue = Color(0xFF2F6BFF),
    fillTop = Color(0xB3FFFFFF),
    fillBottom = Color(0x59FFFFFF),
    glassTop = Color(0x80FFFFFF),
    glassBottom = Color(0x40FFFFFF),
    borderA = Color(0xE6FFFFFF),
    borderB = Color(0x66B9E4FF),
    bgTop = Color(0xFFD3EAF7),
    bgBottom = Color(0xFF9FC9E5),
    dialogBase = Color(0x99EAF3FA),
)

/** How much real glass a surface gets. Higher levels cost more GPU. */
enum class GlassLevel { Lite, Frost, Lens }

val LocalGlass = staticCompositionLocalOf { DarkPalette }

/** User cap on glass quality (Settings > Display options). */
val LocalFx = compositionLocalOf { GlassLevel.Lens }

/** What glass surfaces refract. Content screens use the background-only layer. */
val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

/** Background plus all screen content: used by chrome, menus and dialogs. */
val LocalScene = staticCompositionLocalOf<GraphicsLayer?> { null }

private val sdkMaxLevel: GlassLevel = when {
    Build.VERSION.SDK_INT >= 33 -> GlassLevel.Lens
    Build.VERSION.SDK_INT >= 31 -> GlassLevel.Frost
    else -> GlassLevel.Lite
}

/** Records this node's content into [layer] and also draws it normally. */
fun Modifier.recordTo(layer: GraphicsLayer): Modifier = this.drawWithContent {
    val w = size.width.toInt()
    val h = size.height.toInt()
    if (w > 0 && h > 0) {
        layer.record(this, layoutDirection, IntSize(w, h)) {
            this@drawWithContent.drawContent()
        }
        drawLayer(layer)
    } else {
        drawContent()
    }
}

private fun buildEffect(
    level: GlassLevel,
    shape: Shape,
    size: Size,
    d: Density,
    dark: Boolean,
): RenderEffect? {
    if (size.minDimension <= 0f) return null
    return with(d) {
        when (level) {
            GlassLevel.Frost -> {
                val r = 18.dp.toPx()
                BlurEffect(r, r, TileMode.Mirror)
            }
            GlassLevel.Lens -> if (Build.VERSION.SDK_INT >= 33) {
                val maxR = size.minDimension / 2f
                val radius = ((shape as? CornerBasedShape)?.topStart?.toPx(size, d) ?: maxR)
                    .coerceIn(0f, maxR)
                val bevel = minOf(maxR, 22.dp.toPx()).coerceAtLeast(2f)
                LensFx.build(
                    width = size.width,
                    height = size.height,
                    radius = radius,
                    bevel = bevel,
                    strength = bevel * 0.85f,
                    blurPx = 14.dp.toPx(),
                    dark = dark,
                )
            } else null
            GlassLevel.Lite -> null
        }
    }
}

private fun Modifier.glassBackdrop(
    layer: GraphicsLayer,
    level: GlassLevel,
    shape: Shape,
    dark: Boolean,
    pos: () -> Offset,
): Modifier = this
    .graphicsLayer {
        clip = true
        this.shape = shape
        renderEffect = buildEffect(level, shape, size, this, dark)
    }
    .drawBehind {
        val o = pos()
        translate(-o.x, -o.y) { drawLayer(layer) }
    }

/**
 * A liquid-glass surface. With a backdrop available it blurs (and on Android 13+ refracts)
 * whatever is behind it; otherwise it falls back to a translucent gradient.
 * [cap] limits the effect for cheap/small or numerous surfaces.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color? = null,
    base: Color = Color.Transparent,
    cap: GlassLevel = GlassLevel.Lens,
    pressScale: Float = 0.97f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalGlass.current
    val backdrop = LocalBackdrop.current
    val level = if (backdrop == null) GlassLevel.Lite else minOf(cap, LocalFx.current, sdkMaxLevel)
    val real = level != GlassLevel.Lite

    var pos by remember { mutableStateOf(Offset.Zero) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) pressScale else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 650f),
        label = "glassPress",
    )

    val top = when {
        tint != null -> tint.copy(alpha = 0.42f)
        real -> p.glassTop
        else -> p.fillTop
    }
    val bottom = when {
        tint != null -> tint.copy(alpha = 0.18f)
        real -> p.glassBottom
        else -> p.fillBottom
    }
    val borderBrush = if (tint != null) {
        Brush.linearGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.25f), tint.copy(alpha = 0.7f)))
    } else {
        Brush.linearGradient(listOf(p.borderA, p.borderB, p.borderA.copy(alpha = p.borderA.alpha * 0.35f)))
    }

    var m = modifier
    if (onClick != null) m = m.graphicsLayer { scaleX = scale; scaleY = scale }
    m = m.clip(shape).border(1.dp, borderBrush, shape)
    if (real) m = m.onGloballyPositioned { pos = it.positionInRoot() }
    if (onClick != null) m = m.clickable(interactionSource = source, indication = null, onClick = onClick)

    Box(m) {
        if (real && backdrop != null) {
            Box(Modifier.matchParentSize().glassBackdrop(backdrop, level, shape, p.dark) { pos })
        }
        Box(
            Modifier
                .matchParentSize()
                .background(base)
                .background(Brush.verticalGradient(listOf(top, bottom)))
        )
        content()
    }
}
