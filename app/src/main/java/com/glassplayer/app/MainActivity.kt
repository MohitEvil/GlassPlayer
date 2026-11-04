package com.glassplayer.app

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.glassplayer.app.ui.AppBackground
import com.glassplayer.app.ui.BottomChrome
import com.glassplayer.app.ui.DarkPalette
import com.glassplayer.app.ui.DetailScreen
import com.glassplayer.app.ui.DialogHost
import com.glassplayer.app.ui.EdgeGlow
import com.glassplayer.app.ui.GlassLevel
import com.glassplayer.app.ui.GroupListScreen
import com.glassplayer.app.ui.HomeScreen
import com.glassplayer.app.ui.LibraryScreen
import com.glassplayer.app.ui.LightPalette
import com.glassplayer.app.ui.LocalBackdrop
import com.glassplayer.app.ui.LocalFx
import com.glassplayer.app.ui.LocalGlass
import com.glassplayer.app.ui.LocalMenu
import com.glassplayer.app.ui.LocalScene
import com.glassplayer.app.ui.MenuController
import com.glassplayer.app.ui.MenuHost
import com.glassplayer.app.ui.NowPlayingScreen
import com.glassplayer.app.ui.PermissionScreen
import com.glassplayer.app.ui.SearchScreen
import com.glassplayer.app.ui.SettingsScreen
import com.glassplayer.app.ui.SongsScreen
import com.glassplayer.app.ui.accentOf
import com.glassplayer.app.ui.recordTo

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlassApp(vm) }
    }
}

@Composable
fun GlassApp(vm: AppViewModel) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (vm.theme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    // With "Song colors" as the app background the accent follows the artwork too.
    val basePalette = if (dark) DarkPalette else LightPalette
    val songAccent = vm.songColors?.let { accentOf(it.primary, dark) }
    val accentTarget = if (vm.bgMode == "song" && songAccent != null) songAccent else basePalette.accent
    val accent by animateColorAsState(accentTarget, tween(900), label = "appAccent")
    val palette = remember(basePalette, accent) { basePalette.copy(accent = accent) }

    val fx = when (vm.glassFx) {
        "soft" -> GlassLevel.Frost
        "off" -> GlassLevel.Lite
        else -> GlassLevel.Lens
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // Permission flow
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionResult() }
    val requestPermissions = {
        val perms = buildList {
            add(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }
    LaunchedEffect(Unit) { if (!vm.permissionGranted) requestPermissions() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onPermissionResult() }

    val menu = remember { MenuController() }

    // Later handlers win, so overlays are registered last.
    BackHandler(enabled = vm.nowPlayingOpen || vm.stack.size > 1) {
        if (vm.nowPlayingOpen) vm.nowPlayingOpen = false else vm.back()
    }
    BackHandler(enabled = menu.open) { menu.dismiss() }
    BackHandler(enabled = vm.dialog != null) { vm.dialog = null }

    // What glass can refract, from the innermost tier outwards:
    //  bg      the app background only      -> cards and rows inside screens
    //  content background + screens         -> nav bar and mini player (lists scroll under them)
    //  scene   everything below the overlays -> popup menus and dialogs
    val bgLayer = rememberGraphicsLayer()
    val contentLayer = rememberGraphicsLayer()
    val sceneLayer = rememberGraphicsLayer()

    CompositionLocalProvider(
        LocalGlass provides palette,
        LocalFx provides fx,
        LocalMenu provides menu,
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme(primary = palette.accent)
            else lightColorScheme(primary = palette.accent)
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().recordTo(sceneLayer)) {
                    Box(Modifier.fillMaxSize().recordTo(contentLayer)) {
                        Box(Modifier.fillMaxSize().recordTo(bgLayer)) {
                            AppBackground(vm, dark)
                        }

                        CompositionLocalProvider(LocalBackdrop provides bgLayer) {
                            if (!vm.permissionGranted) {
                                PermissionScreen(onRequest = { requestPermissions() })
                            } else {
                                AnimatedContent(
                                    targetState = vm.current,
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        val ease = FastOutSlowInEasing
                                        when (vm.navDir) {
                                            // push: new screen glides in from the right, old one recedes
                                            1 -> (slideInHorizontally(tween(380, easing = ease)) { it / 5 } +
                                                fadeIn(tween(300)) +
                                                scaleIn(tween(380, easing = ease), 0.96f)) togetherWith
                                                (slideOutHorizontally(tween(380, easing = ease)) { -it / 7 } +
                                                    fadeOut(tween(220)) +
                                                    scaleOut(tween(380, easing = ease), 0.94f))
                                            // pop: current screen slides away over the one below
                                            -1 -> (slideInHorizontally(tween(380, easing = ease)) { -it / 7 } +
                                                fadeIn(tween(300)) +
                                                scaleIn(tween(380, easing = ease), 0.94f)) togetherWith
                                                (slideOutHorizontally(tween(380, easing = ease)) { it / 5 } +
                                                    fadeOut(tween(220)) +
                                                    scaleOut(tween(380, easing = ease), 0.96f))
                                            // tabs: soft fade-through
                                            else -> (fadeIn(tween(260)) + scaleIn(tween(260), 0.97f)) togetherWith
                                                (fadeOut(tween(150)) + scaleOut(tween(150), 0.98f))
                                        }.apply {
                                            targetContentZIndex = if (vm.navDir < 0) -1f else 1f
                                        }
                                    },
                                    label = "route",
                                ) { route ->
                                    when (route) {
                                        Route.Home -> HomeScreen(vm)
                                        Route.Songs -> SongsScreen(vm)
                                        Route.Library -> LibraryScreen(vm)
                                        Route.Search -> SearchScreen(vm)
                                        Route.Playlists -> GroupListScreen(vm, "Playlists", Kind.Playlist, vm.playlistGroups)
                                        Route.Artists -> GroupListScreen(vm, "Artists", Kind.Artist, vm.artists)
                                        Route.Albums -> GroupListScreen(vm, "Albums", Kind.Album, vm.albums)
                                        Route.Genres -> GroupListScreen(vm, "Genres", Kind.Genre, vm.genres)
                                        Route.Folders -> GroupListScreen(vm, "Folders", Kind.Folder, vm.folders)
                                        Route.Settings -> SettingsScreen(vm)
                                        is Route.Detail -> DetailScreen(vm, route)
                                    }
                                }
                            }
                        }
                    }

                    if (vm.permissionGranted) {
                        CompositionLocalProvider(LocalBackdrop provides contentLayer) {
                            AnimatedVisibility(
                                visible = vm.current != Route.Settings,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 360f)) { it } + fadeIn(),
                                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
                            ) {
                                BottomChrome(vm)
                            }
                        }

                        // Now Playing paints and refracts its own song-coloured background.
                        AnimatedVisibility(
                            visible = vm.nowPlayingOpen && vm.currentSong != null,
                            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 300f)) { it } + fadeIn(tween(200)),
                            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
                        ) {
                            NowPlayingScreen(vm)
                        }
                    }

                    // Rainbow light around the screen's edges while searching.
                    EdgeGlow(
                        visible = vm.searchGlow && vm.permissionGranted &&
                            vm.current == Route.Search && !vm.nowPlayingOpen,
                    )
                }

                // Popup menu and dialogs live in this tree (not in system windows) so they can
                // refract the whole screen behind them.
                CompositionLocalProvider(
                    LocalBackdrop provides sceneLayer,
                    LocalScene provides sceneLayer,
                ) {
                    MenuHost(menu)
                    DialogHost(vm)
                }
            }
        }
    }
}
