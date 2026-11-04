package com.glassplayer.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplayer.app.AppViewModel
import com.glassplayer.app.Dlg
import com.glassplayer.app.Route
import com.glassplayer.app.data.Song
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Glass dialogs drawn in the app's own tree (no system Dialog window), so they can refract
 * the screen behind them and animate in and out.
 */
@Composable
fun DialogHost(vm: AppViewModel) {
    val visible = vm.dialog != null
    var last by remember { mutableStateOf<Dlg?>(null) }
    LaunchedEffect(vm.dialog) { vm.dialog?.let { last = it } }
    val shown = vm.dialog ?: last
    val scrim by animateFloatAsState(if (visible) 1f else 0f, tween(240), label = "scrim")

    if (scrim > 0.001f || visible) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * scrim))
                .clickableNoRipple { vm.dialog = null }
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(160)) +
                    scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), 0.86f) +
                    slideInVertically(tween(260)) { it / 10 },
                exit = fadeOut(tween(120)) + scaleOut(tween(150), 0.94f),
            ) {
                Box(Modifier.clickableNoRipple { /* keep taps inside the card */ }) {
                    AnimatedContent(
                        targetState = shown,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "dialogContent",
                    ) { d ->
                        if (d != null) DialogContent(vm, d)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogContent(vm: AppViewModel, d: Dlg) {
    val close = { vm.dialog = null }
    when (d) {
        Dlg.EightD -> EightDDialog(vm, close)
        Dlg.Sleep -> SleepDialog(vm, close)
        Dlg.Volume -> VolumeDialog(close)
        Dlg.Queue -> QueueDialog(vm, close)
        Dlg.Display -> DisplayDialog(vm, close)
        Dlg.GlassMotion -> GlassMotionDialog(vm, close)
        Dlg.Playback -> PlaybackDialog(vm, close)
        Dlg.LibraryOptions -> LibraryDialog(vm, close)
        Dlg.Privacy -> InfoDialog(
            "Privacy policy",
            "Glass Player keeps everything on your device. It has no accounts, no analytics, and no network permission, so your library, favorites and playlists never leave your phone.",
            close
        )
        Dlg.Permissions -> PermissionsDialog(vm, close)
        Dlg.Language -> LanguageDialog(close)
        Dlg.About -> AboutDialog(close)
        is Dlg.NewPlaylist -> NameDialog("New playlist", "", "Create", close) { name ->
            vm.createPlaylist(name, d.thenAdd)
        }
        is Dlg.RenamePlaylist -> NameDialog("Rename playlist", d.name, "Save", close) { name ->
            vm.renamePlaylist(d.id, name)
        }
        is Dlg.DeletePlaylist -> ConfirmDialog(
            "Delete playlist",
            "Delete \"${d.name}\"? The songs themselves stay on your device.",
            "Delete", close
        ) {
            vm.deletePlaylist(d.id)
            if (vm.current is Route.Detail) vm.back()
        }
        is Dlg.AddToPlaylist -> AddToPlaylistDialog(vm, d.song, close)
        is Dlg.SongInfo -> SongInfoDialog(d.song, close)
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalGlass.current
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    Glass(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp)
            .heightIn(max = maxH),
        RoundedCornerShape(36.dp),
        base = p.dialogBase,
        cap = GlassLevel.Lens,
    ) {
        // Controls inside the card use the cheap gradient glass; the card itself is the real lens.
        CompositionLocalProvider(LocalFx provides GlassLevel.Lite) {
            Column(
                Modifier
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(24.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DialogTitle(text: String, trailing: @Composable () -> Unit = {}) {
    val p = LocalGlass.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun BodyText(text: String) {
    val p = LocalGlass.current
    Text(text, color = p.textDim, fontSize = 17.sp, lineHeight = 24.sp)
}

@Composable
private fun SectionLabel(text: String) {
    val p = LocalGlass.current
    Spacer(Modifier.height(14.dp))
    Text(text, color = p.text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DialogButtons(
    confirm: String,
    onConfirm: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val p = LocalGlass.current
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            Glass(Modifier.height(52.dp).width(124.dp), CircleShape, pressScale = 0.92f, onClick = onSecondary) {
                Text(
                    secondary, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        PinkButton(confirm, onConfirm, Modifier.width(124.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 19.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        GlassSwitch(checked, onChange)
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalGlass.current
    Glass(
        modifier.height(48.dp),
        CircleShape,
        tint = if (selected) p.accent else null,
        pressScale = 0.92f,
        onClick = onClick,
    ) {
        Text(
            label,
            color = if (selected) p.text else p.textDim,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: @Composable () -> Unit = {},
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickableNoRipple(onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(
                title, color = if (highlight) p.blue else p.text, fontSize = 18.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, color = p.textDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ------------------------------------------------------------------ 8D audio

@Composable
private fun EightDDialog(vm: AppViewModel, close: () -> Unit) {
    val p = LocalGlass.current
    val status = "${if (vm.eightOn) "On" else "Off"}  ·  session ${vm.eightSessionId}  ·  " +
        "rotation ${if (vm.eightActive) "active" else "inactive"}"

    GlassDialog(close) {
        DialogTitle("8D Audio") {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(p.accent.copy(alpha = 0.18f))
                    .border(1.dp, p.accent.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    "ON DEVICE", color = p.accent, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 2.sp
                )
            }
        }
        BodyText("Rotates the stereo field around your head. Headphones recommended.")
        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) {
            Text(
                status, color = p.textDim, fontSize = 17.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        SwitchRow("Enable 8D", vm.eightOn) { vm.setEightOn(it) }

        Text(
            String.format(
                Locale.US, "Rotation speed  ·  %.1f s per turn  (%.1fx)",
                vm.eightPeriod, vm.eightMultiplier
            ),
            color = p.text, fontSize = 19.sp,
        )
        GlassSlider(vm.eightSpeed, { vm.setEightSpeed(it) })

        Text(
            "Rotation depth  ·  ${(vm.eightDepth * 100).roundToInt()}%",
            color = p.text, fontSize = 19.sp,
        )
        GlassSlider(vm.eightDepth, { vm.setEightDepth(it) })

        DialogButtons("Done", close, "Restart") { vm.restartEight() }
    }
}

// ------------------------------------------------------------------ sleep / volume / queue

@Composable
private fun SleepDialog(vm: AppViewModel, close: () -> Unit) {
    val remaining by produceState(0L, vm.sleepEndsAt) {
        while (true) {
            value = (vm.sleepEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(1000)
        }
    }
    GlassDialog(close) {
        DialogTitle("Sleep timer")
        BodyText(
            if (vm.sleepEndsAt > 0) "Playback will pause in ${formatTime(remaining)}."
            else "Pause playback automatically after:"
        )
        Spacer(Modifier.height(14.dp))
        listOf(listOf(15, 30, 45), listOf(60, 90, 0)).forEach { rowMinutes ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowMinutes.forEach { m ->
                    ChoiceChip(
                        if (m == 0) "Off" else "$m min",
                        selected = m == 0 && vm.sleepEndsAt == 0L,
                        modifier = Modifier.weight(1f),
                    ) {
                        vm.setSleep(m)
                        close()
                    }
                }
            }
        }
        DialogButtons("Done", close)
    }
}

@Composable
private fun VolumeDialog(close: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    GlassDialog(close) {
        DialogTitle("Volume")
        GlassSlider(v, {
            v = it
            am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
        }, range = 0f..max)
        DialogButtons("Done", close)
    }
}

@Composable
private fun QueueDialog(vm: AppViewModel, close: () -> Unit) {
    GlassDialog(close, scroll = false) {
        DialogTitle("Up next")
        if (vm.queue.isEmpty()) {
            BodyText("The queue is empty.")
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(vm.queue) { i, s ->
                    ListRow(
                        title = s.title,
                        subtitle = s.artist,
                        highlight = i == vm.queueIndex,
                        leading = {
                            Artwork(s, Modifier.size(46.dp), RoundedCornerShape(12.dp))
                            Spacer(Modifier.width(12.dp))
                        },
                    ) { vm.skipTo(i) }
                }
            }
        }
        DialogButtons("Done", close)
    }
}

// ------------------------------------------------------------------ settings dialogs

@Composable
private fun DisplayDialog(vm: AppViewModel, close: () -> Unit) {
    val p = LocalGlass.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCustomBackground(uri)
    }
    val pickImage = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val hasCustom = vm.customBg != null

    GlassDialog(close) {
        DialogTitle("Display options")

        SectionLabel("Theme")
        ChipRow {
            listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (key, label) ->
                ChoiceChip(label, vm.theme == key, Modifier.weight(1f)) { vm.setTheme(key) }
            }
        }

        SectionLabel("App background")
        ChipRow {
            ChoiceChip("Default", vm.bgMode == "default", Modifier.weight(1f)) { vm.setBgMode("default") }
            ChoiceChip("Song colors", vm.bgMode == "song", Modifier.weight(1f)) { vm.setBgMode("song") }
            ChoiceChip("Custom", vm.bgMode == "custom" && hasCustom, Modifier.weight(1f)) {
                if (hasCustom) vm.setBgMode("custom") else pickImage()
            }
        }

        AnimatedVisibility(visible = vm.bgMode == "custom" && hasCustom) {
            Column {
                Spacer(Modifier.height(12.dp))
                ChipRow {
                    ChoiceChip("Change image", false, Modifier.weight(1f)) { pickImage() }
                    ChoiceChip("Remove", false, Modifier.weight(1f)) { vm.clearCustomBackground() }
                }
                SectionLabel("Dim  ·  ${(vm.bgDim * 100).roundToInt()}%")
                GlassSlider(vm.bgDim, { vm.setBgDim(it) }, range = 0f..0.85f)
                SectionLabel("Blur  ·  ${(vm.bgBlur * 100).roundToInt()}%")
                GlassSlider(vm.bgBlur, { vm.setBgBlur(it) })
            }
        }

        Spacer(Modifier.height(10.dp))
        SwitchRow("Song colors in player", vm.playerAdaptive) { vm.setPlayerAdaptive(it) }
        SwitchRow("Floating bubbles", vm.bubbles) { vm.setBubbles(it) }

        Spacer(Modifier.height(6.dp))
        Text(
            "Lens, glow and blur options are under Glass and motion.",
            color = p.textDim, fontSize = 14.sp, lineHeight = 20.sp,
        )

        DialogButtons("Done", close, "Effects") { vm.dialog = Dlg.GlassMotion }
    }
}

@Composable
private fun FeatureRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, color = p.text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(body, color = p.textDim, fontSize = 14.sp, lineHeight = 20.sp)
        }
        GlassSwitch(checked, onChange)
    }
}

@Composable
private fun GlassMotionDialog(vm: AppViewModel, close: () -> Unit) {
    val p = LocalGlass.current
    GlassDialog(close) {
        Text(
            "Glass and motion", color = p.accent, fontSize = 26.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))

        FeatureRow(
            "Liquid glass",
            "Lens, light and grain on the glass bars. Off: a plain frosted blur that is easier on the battery.",
            vm.glassFx == "full",
        ) { vm.setGlassFx(if (it) "full" else "soft") }

        FeatureRow(
            "Frosted player buttons",
            "The buttons in the player blur the picture under them. Uses more battery, and can stutter on slower phones.",
            vm.frostedButtons,
        ) { vm.setFrostedButtons(it) }

        FeatureRow(
            "Tab switch animation",
            "A glass lens glides to the tab and the bar wiggles. Off on slower phones until you turn it on.",
            vm.tabAnim,
        ) { vm.setTabAnim(it) }

        Spacer(Modifier.height(8.dp))
        Text("Tab glow", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("Each tab glows in its own color", color = p.textDim, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        ChipRow {
            ChoiceChip("White", vm.tabGlow == "white", Modifier.weight(1f)) { vm.setTabGlow("white") }
            ChoiceChip("Tab colors", vm.tabGlow == "tabs", Modifier.weight(1f)) { vm.setTabGlow("tabs") }
        }
        Spacer(Modifier.height(8.dp))
        ChipRow {
            ChoiceChip("Accent", vm.tabGlow == "accent", Modifier.weight(1f)) { vm.setTabGlow("accent") }
            ChoiceChip("Off", vm.tabGlow == "off", Modifier.weight(1f)) { vm.setTabGlow("off") }
        }
        Spacer(Modifier.height(6.dp))

        FeatureRow(
            "Progress glow",
            "A soft light at the end of the seek bar in the player.",
            vm.progressGlow,
        ) { vm.setProgressGlow(it) }

        FeatureRow(
            "Search glow",
            "The screen's edges light up in color while you search.",
            vm.searchGlow,
        ) { vm.setSearchGlow(it) }

        DialogButtons("Done", close)
    }
}

@Composable
private fun PlaybackDialog(vm: AppViewModel, close: () -> Unit) {
    GlassDialog(close) {
        DialogTitle("Playback")
        SwitchRow("Keep screen on while playing", vm.keepScreenOn) { vm.setKeepScreenOn(it) }
        SwitchRow("Pause when headphones disconnect", vm.pauseOnUnplug) { vm.setPauseOnUnplug(it) }
        DialogButtons("Done", close)
    }
}

@Composable
private fun LibraryDialog(vm: AppViewModel, close: () -> Unit) {
    GlassDialog(close) {
        DialogTitle("Library")
        BodyText("${plural(vm.songs.size, "song")} found on this device.")
        Spacer(Modifier.height(6.dp))
        SwitchRow("Hide tracks under 30 seconds", vm.hideShort) { vm.setHideShort(it) }
        DialogButtons("Done", close, "Rescan") { vm.reload() }
    }
}

@Composable
private fun PermissionsDialog(vm: AppViewModel, close: () -> Unit) {
    val ctx = LocalContext.current
    GlassDialog(close) {
        DialogTitle("Permissions")
        BodyText(
            if (vm.permissionGranted) "Audio access: granted. Glass Player can read the music on this device."
            else "Audio access: not granted. Glass Player can't build your library without it."
        )
        DialogButtons("Done", close, "App settings") {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun LanguageDialog(close: () -> Unit) {
    val ctx = LocalContext.current
    GlassDialog(close) {
        DialogTitle("Language")
        BodyText("Glass Player is available in English and follows your device language settings.")
        DialogButtons("Done", close, "Open settings") {
            val intent = if (Build.VERSION.SDK_INT >= 33) {
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            } else {
                Intent(Settings.ACTION_LOCALE_SETTINGS)
            }
            runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun AboutDialog(close: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    GlassDialog(close) {
        DialogTitle("About")
        BodyText("Glass Player $version\nA local music player with a liquid-glass interface, built with Jetpack Compose and Media3. Includes an on-device 8D audio effect.")
        DialogButtons("Done", close)
    }
}

@Composable
private fun InfoDialog(title: String, body: String, close: () -> Unit) {
    GlassDialog(close) {
        DialogTitle(title)
        BodyText(body)
        DialogButtons("Done", close)
    }
}

@Composable
private fun SongInfoDialog(song: Song, close: () -> Unit) {
    GlassDialog(close) {
        DialogTitle("Song info")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoLine("Title", song.title)
            InfoLine("Artist", song.artist)
            InfoLine("Album", song.album)
            InfoLine("Genre", song.genre)
            InfoLine("Length", formatTime(song.durationMs))
            InfoLine("Folder", song.folderPath)
        }
        DialogButtons("Done", close)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val p = LocalGlass.current
    Column {
        Text(label, color = p.textDim, fontSize = 13.sp)
        Text(value, color = p.text, fontSize = 17.sp)
    }
}

// ------------------------------------------------------------------ playlist dialogs

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    close: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val p = LocalGlass.current
    var text by remember { mutableStateOf(initial) }
    GlassDialog(close) {
        DialogTitle(title)
        Glass(Modifier.fillMaxWidth().height(54.dp), CircleShape) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 18.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp).fillMaxWidth(),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Playlist name", color = p.textDim, fontSize = 18.sp)
                    inner()
                },
            )
        }
        DialogButtons(confirm, {
            if (text.isNotBlank()) {
                onConfirm(text)
                close()
            }
        }, "Cancel") { close() }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    close: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassDialog(close) {
        DialogTitle(title)
        BodyText(body)
        DialogButtons(confirm, { onConfirm(); close() }, "Cancel") { close() }
    }
}

@Composable
private fun AddToPlaylistDialog(vm: AppViewModel, song: Song, close: () -> Unit) {
    val p = LocalGlass.current
    GlassDialog(close) {
        DialogTitle("Add to playlist")
        vm.playlists.forEach { pl ->
            ListRow(
                title = pl.name,
                subtitle = plural(pl.songIds.size, "song"),
                leading = {
                    Icon(Icons.Rounded.GraphicEq, null, Modifier.size(26.dp), tint = p.accent)
                    Spacer(Modifier.width(14.dp))
                },
            ) {
                vm.addToPlaylist(pl.id, song.id)
                close()
            }
        }
        ListRow(
            title = "New playlist…",
            leading = {
                Icon(Icons.Rounded.Add, null, Modifier.size(26.dp), tint = p.accent)
                Spacer(Modifier.width(14.dp))
            },
        ) { vm.dialog = Dlg.NewPlaylist(song) }
        DialogButtons("Done", close)
    }
}
