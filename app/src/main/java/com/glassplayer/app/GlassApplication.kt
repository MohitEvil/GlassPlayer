package com.glassplayer.app

import android.app.Application
import com.glassplayer.app.data.Store
import com.glassplayer.app.playback.EightDState

class GlassApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Restore 8D settings before the playback service can start on its own
        // (e.g. a media-button resume while the UI is closed).
        val store = Store(this)
        EightDState.enabled = store.eightOn
        EightDState.periodSeconds = 9f / (0.5f + store.eightSpeed * 4f)
        EightDState.depth = store.eightDepth
    }
}
