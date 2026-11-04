package com.glassplayer.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.glassplayer.app.data.SongColors
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Picks two vivid, distinct colours from artwork. Returns null for art with no real colour
 * (white/grey/black covers) so the app falls back to its default look.
 */
fun extractSongColors(source: Bitmap): SongColors? {
    val bmp = try {
        if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    } catch (_: Exception) {
        null
    } ?: return null

    val pal = try {
        Palette.from(bmp).maximumColorCount(24).generate()
    } catch (_: Exception) {
        return null
    }

    val swatches = listOfNotNull(
        pal.vibrantSwatch, pal.lightVibrantSwatch, pal.darkVibrantSwatch,
        pal.mutedSwatch, pal.lightMutedSwatch, pal.darkMutedSwatch, pal.dominantSwatch,
    ).distinctBy { it.rgb }
    if (swatches.isEmpty()) return null

    val best = swatches.maxByOrNull { score(it) } ?: return null
    if (best.hsl[1] < 0.18f) return null

    val bestHue = best.hsl[0]
    val second = swatches
        .filter { it !== best && it.hsl[1] > 0.15f && hueDistance(it.hsl[0], bestHue) > 24f }
        .maxByOrNull { score(it) }

    val c1 = tune(best.rgb, 0.55f, 0.38f, 0.58f)
    val c2 = if (second != null) tune(second.rgb, 0.5f, 0.35f, 0.6f)
    else tune(shiftHue(best.rgb, 38f), 0.55f, 0.38f, 0.58f)
    return SongColors(c1, c2)
}

/** A readable accent derived from [c] for the current theme. */
fun accentOf(c: Color, dark: Boolean): Color =
    tune(c.toArgb(), 0.65f, if (dark) 0.58f else 0.40f, if (dark) 0.70f else 0.52f)

private fun score(s: Palette.Swatch): Float {
    val h = s.hsl
    return h[1] * sqrt(s.population.toFloat()) * (1f - abs(h[2] - 0.5f))
}

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return min(d, 360f - d)
}

private fun shiftHue(rgb: Int, degrees: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}

private fun tune(rgb: Int, minS: Float, minL: Float, maxL: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = hsl[1].coerceAtLeast(minS).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(minL, maxL)
    return Color(ColorUtils.HSLToColor(hsl))
}
