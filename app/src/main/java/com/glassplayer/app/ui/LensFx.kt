package com.glassplayer.app.ui

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Liquid-glass lens: blurs the backdrop, then bends it near the edges like a thick glass bevel
 * (with slight chromatic dispersion), boosts saturation, and adds specular rim light.
 * Needs Android 13+ (AGSL). Callers must check the SDK level.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object LensFx {

    private const val AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float sat;
uniform float hiGain;
uniform float grain;

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fc) {
    float2 hs = size * 0.5;
    float2 p = fc - hs;
    float d = sdBox(p, hs, radius);
    float2 g = float2(
        sdBox(p + float2(1.0, 0.0), hs, radius) - sdBox(p - float2(1.0, 0.0), hs, radius),
        sdBox(p + float2(0.0, 1.0), hs, radius) - sdBox(p - float2(0.0, 1.0), hs, radius));
    float gl = length(g);
    float2 n = gl > 0.0001 ? g / gl : float2(0.0, 0.0);

    float t = clamp(-d / bevel, 0.0, 1.0);
    float lens = pow(1.0 - t, 3.0);
    float2 off = -n * lens * strength;

    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    half4 cr = content.eval(clamp(fc + off * 1.12, lo, hi));
    half4 cg = content.eval(clamp(fc + off, lo, hi));
    half4 cb = content.eval(clamp(fc + off * 0.88, lo, hi));
    float3 col = float3(cr.r, cg.g, cb.b);

    float l = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(l), col, sat);

    float2 L = normalize(float2(-0.55, -0.83));
    float hiL = pow(max(dot(n, L), 0.0), 1.6) * lens;
    float loL = pow(max(dot(n, -L), 0.0), 1.6) * lens;
    col += hiGain * (hiL * 0.60 + loL * 0.22);

    float rim = 1.0 - smoothstep(0.0, 1.6, abs(d + 0.8));
    col += hiGain * rim * 0.14;

    // soft light from the top, then fine film grain so the frost reads as a material
    col += hiGain * (1.0 - fc.y / size.y) * 0.05;
    float gn = fract(sin(dot(fc, float2(12.9898, 78.233))) * 43758.5453);
    col += (gn - 0.5) * grain;

    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

    fun build(
        width: Float,
        height: Float,
        radius: Float,
        bevel: Float,
        strength: Float,
        blurPx: Float,
        dark: Boolean,
    ): RenderEffect {
        val shader = RuntimeShader(AGSL)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("sat", if (dark) 1.25f else 1.15f)
        shader.setFloatUniform("hiGain", if (dark) 1.0f else 0.7f)
        shader.setFloatUniform("grain", if (dark) 0.05f else 0.035f)

        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.MIRROR)
        // inner (blur) runs first, its output feeds the lens
        return android.graphics.RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
    }
}
