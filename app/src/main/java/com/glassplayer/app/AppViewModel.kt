package com.glassplayer.app

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.glassplayer.app.data.SongColors
import com.glassplayer.app.ui.ArtLoader
import com.glassplayer.app.ui.extractSongColors
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.glassplayer.app.data.Group
import com.glassplayer.app.data.MusicRepository
import com.glassplayer.app.data.Playlist
import com.glassplayer.app.data.Song
import com.glassplayer.app.data.Store
import com.glassplayer.app.playback.EightDState
import com.glassplayer.app.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Kind { Playlist, Artist, Album, Genre, Folder, Favorites, Recent }

sealed interface Route {
    data object Home : Route
    data object Songs : Route
    data object Library : Route
    data object Search : Route
    data object Playlists : Route
    data object Artists : Route
    data object Albums : Route
    data object Genres : Route
    data object Folders : Route
    data object Settings : Route
    data class Detail(val kind: Kind, val key: String, val title: String) : Route
}

sealed interface Dlg {
    data object EightD : Dlg
    data object Sleep : Dlg
    data object Volume : Dlg
    data object Queue : Dlg
    data object Display : Dlg
    data object GlassMotion : Dlg
    data object Playback : Dlg
    data object LibraryOptions : Dlg
    data object Privacy : Dlg
    data object Permissions : Dlg
    data object Language : Dlg
    data object About : Dlg
    data class NewPlaylist(val thenAdd: Song? = null) : Dlg
    data class AddToPlaylist(val song: Song) : Dlg
    data class RenamePlaylist(val id: String, val name: String) : Dlg
    data class DeletePlaylist(val id: String, val name: String) : Dlg
    data class SongInfo(val song: Song) : Dlg
}

enum class SortMode(val label: String) {
    Title("Title"), Artist("Artist"), Added("Date added"), Duration("Duration")
}

fun hasAudioPermission(ctx: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    val store = Store(app)

    // ---------------------------------------------------------------- settings
    var theme by mutableStateOf(store.theme)
        private set
    var bubbles by mutableStateOf(store.bubbles)
        private set
    var keepScreenOn by mutableStateOf(store.keepScreenOn)
        private set
    var pauseOnUnplug by mutableStateOf(store.pauseOnUnplug)
        private set
    var hideShort by mutableStateOf(store.hideShort)
        private set


    @JvmName("setThemeValue")

    fun setTheme(v: String) { theme = v; store.theme = v }
    @JvmName("setBubblesValue")
    fun setBubbles(v: Boolean) { bubbles = v; store.bubbles = v }
    @JvmName("setKeepScreenOnValue")
    fun setKeepScreenOn(v: Boolean) { keepScreenOn = v; store.keepScreenOn = v }
    @JvmName("setPauseOnUnplugValue")
    fun setPauseOnUnplug(v: Boolean) { pauseOnUnplug = v; store.pauseOnUnplug = v }
    @JvmName("setHideShortValue")
    fun setHideShort(v: Boolean) { hideShort = v; store.hideShort = v }

    // ------------------------------------------------- backgrounds + glass quality
    /** "default" | "song" | "custom" */
    var bgMode by mutableStateOf(store.bgMode)
        private set
    var playerAdaptive by mutableStateOf(store.playerAdaptive)
        private set
    var bgDim by mutableFloatStateOf(store.bgDim)
        private set
    var bgBlur by mutableFloatStateOf(store.bgBlur)
        private set

    /** "full" | "soft" | "off" */
    var glassFx by mutableStateOf(store.glassFx)
        private set


    @JvmName("setBgModeValue")

    fun setBgMode(v: String) { bgMode = v; store.bgMode = v }
    @JvmName("setPlayerAdaptiveValue")
    fun setPlayerAdaptive(v: Boolean) { playerAdaptive = v; store.playerAdaptive = v }
    @JvmName("setBgDimValue")
    fun setBgDim(v: Float) { bgDim = v; store.bgDim = v }
    @JvmName("setBgBlurValue")
    fun setBgBlur(v: Float) { bgBlur = v; store.bgBlur = v }
    @JvmName("setGlassFxValue")
    fun setGlassFx(v: String) { glassFx = v; store.glassFx = v }

    // Glass and motion
    var frostedButtons by mutableStateOf(store.frostedButtons)
        private set
    var tabAnim by mutableStateOf(store.tabAnim)
        private set
    /** "white" | "tabs" | "accent" | "off" */
    var tabGlow by mutableStateOf(store.tabGlow)
        private set
    var progressGlow by mutableStateOf(store.progressGlow)
        private set
    var searchGlow by mutableStateOf(store.searchGlow)
        private set


    @JvmName("setFrostedButtonsValue")

    fun setFrostedButtons(v: Boolean) { frostedButtons = v; store.frostedButtons = v }
    @JvmName("setTabAnimValue")
    fun setTabAnim(v: Boolean) { tabAnim = v; store.tabAnim = v }
    @JvmName("setTabGlowValue")
    fun setTabGlow(v: String) { tabGlow = v; store.tabGlow = v }
    @JvmName("setProgressGlowValue")
    fun setProgressGlow(v: Boolean) { progressGlow = v; store.progressGlow = v }
    @JvmName("setSearchGlowValue")
    fun setSearchGlow(v: Boolean) { searchGlow = v; store.searchGlow = v }

    // Custom background image, kept as a private downscaled copy so no URI permission is needed later.
    var customBg by mutableStateOf<ImageBitmap?>(null)
        private set
    private val bgFile: File get() = File(app.filesDir, "custom_bg.jpg")


    @JvmName("setCustomBackgroundValue")

    fun setCustomBackground(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBackground(uri) }
            if (bmp != null) {
                customBg = bmp.asImageBitmap()
                setBgMode("custom")
            }
        }
    }

    fun clearCustomBackground() {
        customBg = null
        runCatching { bgFile.delete() }
        if (bgMode == "custom") setBgMode("default")
    }

    private fun decodeBackground(uri: Uri): Bitmap? {
        val maxSide = 1600
        return try {
            val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxSide) {
                        val f = maxSide.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * f).toInt().coerceAtLeast(1),
                            (info.size.height * f).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return null
            }
            FileOutputStream(bgFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------- song colour palette
    var songColors by mutableStateOf<SongColors?>(null)
        private set
    private var colorFor = -1L

    private fun refreshColors(song: Song?) {
        if (song == null) {
            colorFor = -1L
            songColors = null
            return
        }
        if (song.id == colorFor) return
        colorFor = song.id
        viewModelScope.launch {
            val art = ArtLoader.load(app, song, 160)
            val colors = if (art != null) withContext(Dispatchers.Default) { extractSongColors(art) } else null
            if (colorFor == song.id) songColors = colors
        }
    }

    // ----------------------------------------------------------------- library
    var permissionGranted by mutableStateOf(hasAudioPermission(app))
        private set
    private var raw by mutableStateOf<List<Song>>(emptyList())
    var loading by mutableStateOf(false)
        private set

    val songs: List<Song> by derivedStateOf {
        if (hideShort) raw.filter { it.durationMs >= 30_000 } else raw
    }
    private val songById: Map<Long, Song> by derivedStateOf { raw.associateBy { it.id } }

    var sortMode by mutableStateOf(runCatching { SortMode.valueOf(store.sort) }.getOrDefault(SortMode.Title))
        private set
    var favoritesOnly by mutableStateOf(false)


    @JvmName("setSortValue")

    fun setSort(m: SortMode) { sortMode = m; store.sort = m.name }

    val sortedSongs: List<Song> by derivedStateOf {
        when (sortMode) {
            SortMode.Title -> songs.sortedBy { it.title.lowercase() }
            SortMode.Artist -> songs.sortedBy { it.artist.lowercase() }
            SortMode.Added -> songs.sortedByDescending { it.dateAdded }
            SortMode.Duration -> songs.sortedByDescending { it.durationMs }
        }
    }
    val recent: List<Song> by derivedStateOf { songs.sortedByDescending { it.dateAdded }.take(20) }

    val artists: List<Group> by derivedStateOf {
        songs.groupBy { it.artist }.map { Group(it.key, it.key, it.value) }
            .sortedBy { it.name.lowercase() }
    }
    val albums: List<Group> by derivedStateOf {
        songs.groupBy { it.albumId }.map { (id, list) -> Group(id.toString(), list.first().album, list) }
            .sortedBy { it.name.lowercase() }
    }
    val genres: List<Group> by derivedStateOf {
        songs.groupBy { it.genre }.map { Group(it.key, it.key, it.value) }
            .sortedBy { it.name.lowercase() }
    }
    val folders: List<Group> by derivedStateOf {
        songs.groupBy { it.folderPath }.map { (path, list) -> Group(path, list.first().folderName, list) }
            .sortedBy { it.name.lowercase() }
    }

    fun onPermissionResult() {
        permissionGranted = hasAudioPermission(app)
        if (permissionGranted) reload()
    }

    fun reload() {
        if (!hasAudioPermission(app)) return
        permissionGranted = true
        if (loading) return
        loading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MusicRepository.load(app) }
            if (list.size != raw.size || list.map { it.id } != raw.map { it.id }) raw = list
            loading = false
            syncFromController()
        }
    }

    // --------------------------------------------------------------- favorites
    var favorites by mutableStateOf(store.loadFavorites())
        private set

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        store.saveFavorites(favorites)
    }

    // --------------------------------------------------------------- playlists
    var playlists by mutableStateOf(store.loadPlaylists())
        private set

    val playlistGroups: List<Group> by derivedStateOf {
        playlists.map { p -> Group(p.id, p.name, p.songIds.mapNotNull { songById[it] }) }
    }

    private fun savePlaylists(list: List<Playlist>) {
        playlists = list
        store.savePlaylists(list)
    }

    fun createPlaylist(name: String, thenAdd: Song? = null) {
        val n = name.trim().ifEmpty { return }
        savePlaylists(
            playlists + Playlist(UUID.randomUUID().toString(), n, listOfNotNull(thenAdd?.id))
        )
    }

    fun renamePlaylist(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        savePlaylists(playlists.map { if (it.id == id) it.copy(name = n) else it })
    }

    fun deletePlaylist(id: String) = savePlaylists(playlists.filter { it.id != id })

    fun addToPlaylist(playlistId: String, songId: Long) {
        savePlaylists(playlists.map {
            if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        })
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        savePlaylists(playlists.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        })
    }

    fun detailSongs(r: Route.Detail): List<Song> = when (r.kind) {
        Kind.Playlist -> playlistGroups.firstOrNull { it.key == r.key }?.songs.orEmpty()
        Kind.Artist -> artists.firstOrNull { it.key == r.key }?.songs.orEmpty()
        Kind.Album -> albums.firstOrNull { it.key == r.key }?.songs.orEmpty()
        Kind.Genre -> genres.firstOrNull { it.key == r.key }?.songs.orEmpty()
        Kind.Folder -> folders.firstOrNull { it.key == r.key }?.songs.orEmpty()
        Kind.Favorites -> sortedSongs.filter { it.id in favorites }
        Kind.Recent -> recent
    }

    // -------------------------------------------------------------- navigation
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    var nowPlayingOpen by mutableStateOf(false)
    var dialog by mutableStateOf<Dlg?>(null)

    /** +1 pushing deeper, -1 going back, 0 switching tabs. Drives the screen transition. */
    var navDir by mutableIntStateOf(0)
        private set

    fun go(r: Route) { navDir = 1; stack.add(r) }
    fun back() { if (stack.size > 1) { navDir = -1; stack.removeAt(stack.lastIndex) } }
    fun tab(r: Route) { navDir = 0; stack.clear(); stack.add(r) }

    // ---------------------------------------------------------------- playback
    private var controller: MediaController? = null

    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var queueIndex by mutableIntStateOf(0)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromController()
    }

    private fun connect() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(listener)
                syncFromController()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun syncFromController() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        shuffleOn = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        queueIndex = c.currentMediaItemIndex
        if (queue.size != c.mediaItemCount) {
            queue = (0 until c.mediaItemCount).mapNotNull {
                songById[c.getMediaItemAt(it).mediaId.toLongOrNull()]
            }
        }
        val id = c.currentMediaItem?.mediaId?.toLongOrNull()
        currentSong = id?.let { songById[it] }
        if (currentSong == null) nowPlayingOpen = false
        refreshColors(currentSong)
    }

    private fun mediaItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(s.uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.artist)
                .setAlbumTitle(s.album)
                .build()
        )
        .build()

    /** Plays [list] starting at [index], keeping the user's current shuffle setting. */
    fun play(list: List<Song>, index: Int = 0) {
        val c = controller ?: return
        if (list.isEmpty()) return
        queue = list
        c.setMediaItems(list.map(::mediaItem), index.coerceIn(list.indices), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Song>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        play(list, list.indices.random())
    }

    fun shuffleAll() = playShuffled(songs)

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun skipTo(index: Int) {
        controller?.seekTo(index, 0L)
        controller?.play()
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun stopAndClear() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
        queue = emptyList()
        currentSong = null
        nowPlayingOpen = false
    }

    // ------------------------------------------------------------- sleep timer
    var sleepEndsAt by mutableLongStateOf(0L)
        private set
    private var sleepJob: Job? = null


    @JvmName("setSleepValue")

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepEndsAt = 0L
            return
        }
        sleepEndsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            sleepEndsAt = 0L
        }
    }

    // ---------------------------------------------------------------- 8D audio
    var eightOn by mutableStateOf(store.eightOn)
        private set
    var eightSpeed by mutableFloatStateOf(store.eightSpeed)
        private set
    var eightDepth by mutableFloatStateOf(store.eightDepth)
        private set
    var eightActive by mutableStateOf(false)
        private set

    val eightMultiplier: Float get() = 0.5f + eightSpeed * 4f
    val eightPeriod: Float get() = 9f / eightMultiplier

    private fun pushEight() {
        EightDState.enabled = eightOn
        EightDState.periodSeconds = eightPeriod
        EightDState.depth = eightDepth
        store.eightOn = eightOn
        store.eightSpeed = eightSpeed
        store.eightDepth = eightDepth
    }


    @JvmName("setEightOnValue")

    fun setEightOn(v: Boolean) { eightOn = v; pushEight() }
    @JvmName("setEightSpeedValue")
    fun setEightSpeed(v: Float) { eightSpeed = v; pushEight() }
    @JvmName("setEightDepthValue")
    fun setEightDepth(v: Float) { eightDepth = v; pushEight() }
    fun restartEight() { EightDState.restart = true }
    val eightSessionId: Int get() = EightDState.sessionId

    // Must stay last: it starts coroutines that touch properties declared above.
    init {
        connect()
        if (permissionGranted) reload()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                if (bgFile.exists()) BitmapFactory.decodeFile(bgFile.absolutePath) else null
            }
            if (saved != null) customBg = saved.asImageBitmap()
        }
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    positionMs = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    durationMs = if (d > 0) d else currentSong?.durationMs ?: 0L
                }
                eightActive = EightDState.isRotating()
                delay(250)
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
