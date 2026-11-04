package com.glassplayer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MenuItem(
    val label: String,
    val checked: Boolean = false,
    val danger: Boolean = false,
    val action: () -> Unit,
)

/** Drives the single glass popup menu that lives above the whole app. */
@Stable
class MenuController {
    var open by mutableStateOf(false)
        private set
    var anchor by mutableStateOf(Rect.Zero)
        private set
    var items by mutableStateOf<List<MenuItem>>(emptyList())
        private set

    fun show(anchor: Rect, items: List<MenuItem>) {
        this.anchor = anchor
        this.items = items
        open = true
    }

    fun dismiss() {
        open = false
    }
}

val LocalMenu = staticCompositionLocalOf { MenuController() }

/** A glass icon button that opens the glass popup menu next to itself. */
@Composable
fun MenuButton(
    items: List<MenuItem>,
    size: Dp = 44.dp,
    icon: ImageVector = Icons.Rounded.MoreVert,
) {
    val menu = LocalMenu.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
        GlassIconButton(icon, { menu.show(bounds, items) }, size = size)
    }
}

@Composable
fun MenuHost(c: MenuController) {
    val density = LocalDensity.current
    val screenH = LocalConfiguration.current.screenHeightDp * density.density
    val below = c.anchor.center.y < screenH * 0.55f
    val origin = TransformOrigin(1f, if (below) 0f else 1f)

    Box(
        Modifier
            .fillMaxSize()
            .then(if (c.open) Modifier.clickableNoRipple { c.dismiss() } else Modifier)
    ) {
        AnchoredBox(anchor = c.anchor, below = below) {
            AnimatedVisibility(
                visible = c.open,
                enter = fadeIn(tween(110)) + scaleIn(
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
                    initialScale = 0.5f,
                    transformOrigin = origin,
                ),
                exit = fadeOut(tween(90)) + scaleOut(
                    animationSpec = tween(130),
                    targetScale = 0.7f,
                    transformOrigin = origin,
                ),
            ) {
                MenuCard(c)
            }
        }
    }
}

@Composable
private fun AnchoredBox(anchor: Rect, below: Boolean, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val margin = with(d) { 12.dp.roundToPx() }
    val gap = with(d) { 8.dp.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.firstOrNull()
            ?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable != null) {
                val x = (anchor.right.roundToInt() - placeable.width)
                    .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
                val y = if (below) {
                    (anchor.bottom.roundToInt() + gap)
                        .coerceAtMost(constraints.maxHeight - placeable.height - margin)
                } else {
                    (anchor.top.roundToInt() - gap - placeable.height).coerceAtLeast(margin)
                }
                placeable.place(x, y)
            }
        }
    }
}

@Composable
private fun MenuCard(c: MenuController) {
    val p = LocalGlass.current
    Glass(
        Modifier.width(264.dp),
        RoundedCornerShape(28.dp),
        base = p.dialogBase,
        cap = GlassLevel.Lens,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            c.items.forEachIndexed { i, item -> MenuRow(c, item, i) }
        }
    }
}

@Composable
private fun MenuRow(c: MenuController, item: MenuItem, index: Int) {
    val p = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(c.open) {
        if (c.open) {
            appear.snapTo(0f)
            delay(40L + index * 28L)
            appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * -10.dp.toPx()
            }
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (pressed) p.accent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null) {
                c.dismiss()
                item.action()
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            color = if (item.danger) Color(0xFFFF5A6E) else p.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.checked) {
            Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = p.accent)
        }
    }
}
