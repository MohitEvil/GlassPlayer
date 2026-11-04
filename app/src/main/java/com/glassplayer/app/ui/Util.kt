package com.glassplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.util.Locale

/** Clickable without a ripple, for items that draw their own selected state. */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = source, indication = null, onClick = onClick)
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
