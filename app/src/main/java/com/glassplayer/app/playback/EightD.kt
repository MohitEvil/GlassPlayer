package com.glassplayer.app.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Live parameters shared between the UI and the audio thread (same process). */
object EightDState {
    @Volatile var enabled: Boolean = false
    @Volatile var periodSeconds: Float = 6f
    @Volatile var depth: Float = 1f
    @Volatile var restart: Boolean = false
    @Volatile var lastFrameAt: Long = 0L
    @Volatile var sessionId: Int = 0

    /** True while audio is actually flowing through the rotation. */
    fun isRotating(): Boolean =
        enabled && SystemClock.uptimeMillis() - lastFrameAt < 700
}

/**
 * On-device 8D effect. Sweeps an equal-power pan around the listener with a
 * sine LFO and darkens the signal while the source is "behind" the head, so it
 * reads as rotation rather than plain auto-pan. Works on 16-bit PCM, mono or stereo.
 */
@OptIn(UnstableApi::class)
class EightDAudioProcessor : BaseAudioProcessor() {

    private var phase = 0.0
    private var lpL = 0f
    private var lpR = 0f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            return AudioProcessor.AudioFormat.NOT_SET // inactive: pass through untouched
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inCh = inputAudioFormat.channelCount
        val frameBytes = 2 * inCh
        val frames = remaining / frameBytes
        val out = replaceOutputBuffer(frames * 4)

        val on = EightDState.enabled
        val depth = EightDState.depth
        val inc = 2.0 * PI / (EightDState.periodSeconds.toDouble() * inputAudioFormat.sampleRate)
        if (EightDState.restart) {
            phase = 0.0
            EightDState.restart = false
        }
        EightDState.lastFrameAt = SystemClock.uptimeMillis()

        val quarterPi = (PI / 4.0).toFloat()
        val root2 = sqrt(2f)

        for (i in 0 until frames) {
            val l0 = inputBuffer.short.toFloat()
            val r0 = if (inCh == 2) inputBuffer.short.toFloat() else l0
            var l = l0
            var r = r0
            if (on) {
                phase += inc
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                val s = sin(phase).toFloat()
                val c = cos(phase).toFloat()
                val a = (s * depth + 1f) * quarterPi
                l = l0 * cos(a) * root2
                r = r0 * sin(a) * root2
                // Source behind the head -> roll off the highs a little.
                val back = max(0f, -c) * depth * 0.65f
                lpL += 0.28f * (l - lpL)
                lpR += 0.28f * (r - lpR)
                l += (lpL - l) * back
                r += (lpR - r) * back
            }
            out.putShort(l.coerceIn(-32768f, 32767f).toInt().toShort())
            out.putShort(r.coerceIn(-32768f, 32767f).toInt().toShort())
        }
        val leftover = remaining - frames * frameBytes
        if (leftover > 0) inputBuffer.position(inputBuffer.position() + leftover)
        out.flip()
    }

    override fun onFlush() {
        lpL = 0f
        lpR = 0f
    }

    override fun onReset() {
        phase = 0.0
        lpL = 0f
        lpR = 0f
    }
}
