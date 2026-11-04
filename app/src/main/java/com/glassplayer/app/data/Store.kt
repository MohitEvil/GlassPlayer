package com.glassplayer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Tiny SharedPreferences-backed store for settings, favorites and playlists. */
class Store(ctx: Context) {

    companion object {
        const val PREFS = "glass_player"
        const val KEY_PAUSE_UNPLUG = "pause_on_unplug"
    }

    val sp: SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    var bubbles: Boolean
        get() = sp.getBoolean("bubbles", true)
        set(v) = sp.edit().putBoolean("bubbles", v).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) = sp.edit().putBoolean("keep_screen_on", v).apply()

    var pauseOnUnplug: Boolean
        get() = sp.getBoolean(KEY_PAUSE_UNPLUG, true)
        set(v) = sp.edit().putBoolean(KEY_PAUSE_UNPLUG, v).apply()

    var hideShort: Boolean
        get() = sp.getBoolean("hide_short", false)
        set(v) = sp.edit().putBoolean("hide_short", v).apply()

    /** "default" | "song" | "custom" */
    var bgMode: String
        get() = sp.getString("bg_mode", "default") ?: "default"
        set(v) = sp.edit().putString("bg_mode", v).apply()

    var playerAdaptive: Boolean
        get() = sp.getBoolean("player_adaptive", true)
        set(v) = sp.edit().putBoolean("player_adaptive", v).apply()

    var bgDim: Float
        get() = sp.getFloat("bg_dim", 0.3f)
        set(v) = sp.edit().putFloat("bg_dim", v).apply()

    var bgBlur: Float
        get() = sp.getFloat("bg_blur", 0f)
        set(v) = sp.edit().putFloat("bg_blur", v).apply()

    /** "full" | "soft" | "off" */
    var glassFx: String
        get() = sp.getString("glass_fx", "full") ?: "full"
        set(v) = sp.edit().putString("glass_fx", v).apply()

    var frostedButtons: Boolean
        get() = sp.getBoolean("frosted_buttons", true)
        set(v) = sp.edit().putBoolean("frosted_buttons", v).apply()

    var tabAnim: Boolean
        get() = sp.getBoolean("tab_anim", true)
        set(v) = sp.edit().putBoolean("tab_anim", v).apply()

    /** "white" | "tabs" | "accent" | "off" */
    var tabGlow: String
        get() = sp.getString("tab_glow", "tabs") ?: "tabs"
        set(v) = sp.edit().putString("tab_glow", v).apply()

    var progressGlow: Boolean
        get() = sp.getBoolean("progress_glow", true)
        set(v) = sp.edit().putBoolean("progress_glow", v).apply()

    var searchGlow: Boolean
        get() = sp.getBoolean("search_glow", true)
        set(v) = sp.edit().putBoolean("search_glow", v).apply()

    var sort: String
        get() = sp.getString("sort", "Title") ?: "Title"
        set(v) = sp.edit().putString("sort", v).apply()

    // 8D audio
    var eightOn: Boolean
        get() = sp.getBoolean("eight_on", false)
        set(v) = sp.edit().putBoolean("eight_on", v).apply()

    /** 0..1, mapped to 0.5x..4.5x rotation speed. */
    var eightSpeed: Float
        get() = sp.getFloat("eight_speed", 0.25f)
        set(v) = sp.edit().putFloat("eight_speed", v).apply()

    var eightDepth: Float
        get() = sp.getFloat("eight_depth", 1f)
        set(v) = sp.edit().putFloat("eight_depth", v).apply()

    // Favorites
    fun loadFavorites(): Set<Long> {
        val raw = sp.getString("favorites", null) ?: return emptySet()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getLong(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun saveFavorites(ids: Set<Long>) {
        val a = JSONArray()
        ids.forEach { a.put(it) }
        sp.edit().putString("favorites", a.toString()).apply()
    }

    // Playlists
    fun loadPlaylists(): List<Playlist> {
        val raw = sp.getString("playlists", null) ?: return defaultPlaylists()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val s = o.getJSONArray("songs")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    songIds = (0 until s.length()).map { s.getLong(it) }
                )
            }
        } catch (_: Exception) {
            defaultPlaylists()
        }
    }

    fun savePlaylists(list: List<Playlist>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("songs", JSONArray().also { arr -> p.songIds.forEach { arr.put(it) } })
            })
        }
        sp.edit().putString("playlists", a.toString()).apply()
    }

    private fun defaultPlaylists() = listOf("Chill", "Driving", "Relax", "Workout").map {
        Playlist(UUID.randomUUID().toString(), it, emptyList())
    }
}
