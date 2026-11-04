package com.glassplayer.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.glassplayer.app.MainActivity
import com.glassplayer.app.data.Store

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private val eightD = EightDAudioProcessor()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == Store.KEY_PAUSE_UNPLUG) {
            player?.setHandleAudioBecomingNoisy(sp.getBoolean(key, true))
        }
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(eightD))
                .build()
        }

        val exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(prefs.getBoolean(Store.KEY_PAUSE_UNPLUG, true))
            .build()

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EightDState.sessionId = audioSessionId
            }
        })
        EightDState.sessionId = exo.audioSessionId
        player = exo

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, exo).setSessionActivity(open).build()

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
