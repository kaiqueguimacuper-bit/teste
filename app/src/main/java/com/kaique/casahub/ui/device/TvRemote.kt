package com.kaique.casahub.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaique.casahub.data.Caps
import com.kaique.casahub.data.Command
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.Labels
import com.kaique.casahub.data.RemoteKey
import com.kaique.casahub.data.TvApp
import com.kaique.casahub.data.TvCommands
import com.kaique.casahub.ui.components.Eyebrow
import com.kaique.casahub.ui.components.OptionChip
import com.kaique.casahub.ui.components.RoundButton
import com.kaique.casahub.ui.components.rememberTick
import com.kaique.casahub.ui.theme.Display
import com.kaique.casahub.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Toque que repete enquanto o dedo fica pressionado (volume, canal, setas).
 * [pressed] recebe true/false para o realce visual.
 */
@Composable
fun Modifier.repeatPress(enabled: Boolean = true, repeat: Boolean = true, onPress: () -> Unit, pressed: (Boolean) -> Unit = {}): Modifier {
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onPress)
    val setPressed by rememberUpdatedState(pressed)
    return this.pointerInput(enabled, repeat) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            setPressed(true)
            action()
            var job: Job? = null
            if (repeat) {
                job = scope.launch {
                    delay(450)
                    while (true) {
                        action()
                        delay(260)
                    }
                }
            }
            waitForUpOrCancellation()
            job?.cancel()
            setPressed(false)
        }
    }
}

@Composable
fun TvRemote(device: Device, store: HomeStore) {
    val tick = rememberTick()
    val s = device.state
    val on = s.switchOn != false
    val offline = s.online == false
    val ready = !offline
    val hasKeys = device.has(Caps.REMOTE) || device.has(Caps.KEYPAD)
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showKeypad by rememberSaveable { mutableStateOf(false) }

    fun send(cmd: Command?, coalesce: Boolean = false, optimistic: ((com.kaique.casahub.data.DeviceState) -> com.kaique.casahub.data.DeviceState)? = null) {
        tick()
        store.fire(device, cmd, coalesce, optimistic)
    }
    fun key(k: RemoteKey) {
        tick()
        store.pressKey(device, k, coalesce = true)
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // O que está passando
        NowPlaying(device)

        // Linha superior: energia, fonte, mudo, teclado
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LabeledButton("Energia") {
                RoundButton(
                    Icons.Rounded.PowerSettingsNew, if (on) "Desligar TV" else "Ligar TV",
                    onClick = { tick(); store.toggle(device, force = true) },
                    size = 60.dp,
                    container = if (on && !offline) Palette.Coral else Palette.Green,
                    tint = Palette.Bg,
                )
            }
            LabeledButton("Fonte") {
                RoundButton(Icons.Rounded.Input, "Escolher fonte", onClick = { tick(); showSources = !showSources },
                    size = 60.dp, container = if (showSources) Palette.Accent else Palette.Surface2,
                    tint = if (showSources) Palette.Bg else Palette.Text, enabled = ready)
            }
            LabeledButton(if (s.muted == true) "Sem som" else "Mudo") {
                RoundButton(
                    if (s.muted == true) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    "Mudo",
                    onClick = { send(TvCommands.mute(device, s.muted != true)) { it.copy(muted = s.muted != true) } },
                    size = 60.dp,
                    container = if (s.muted == true) Palette.Amber else Palette.Surface2,
                    tint = if (s.muted == true) Palette.Bg else Palette.Text,
                    enabled = ready && device.has(Caps.MUTE),
                )
            }
            LabeledButton("Números") {
                RoundButton(Icons.Rounded.Dialpad, "Teclado numérico", onClick = { tick(); showKeypad = !showKeypad },
                    size = 60.dp, container = if (showKeypad) Palette.Accent else Palette.Surface2,
                    tint = if (showKeypad) Palette.Bg else Palette.Text, enabled = ready)
            }
        }

        AnimatedVisibility(showSources, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val sources = s.inputSources.ifEmpty { listOf("dtv", "HDMI1", "HDMI2", "HDMI3") }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { src ->
                    OptionChip(Labels.input(src), selected = s.inputSource == src, onClick = {
                        send(TvCommands.input(device, src)) { it.copy(inputSource = src) }
                    })
                }
            }
        }

        AnimatedVisibility(showKeypad, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Keypad(device, onSend = { cmd, opt -> send(cmd, optimistic = opt) }, onDone = { showKeypad = false })
        }

        // Direcional
        Box(Modifier.alpha(if (ready) 1f else 0.45f)) {
            DPad(enabled = hasKeys && ready, onKey = ::key)
        }
        if (!hasKeys && !offline) {
            Text(
                "Esta TV não recebe teclas de navegação pela SmartThings. Volume, canal, fonte e apps continuam funcionando.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center,
            )
        }

        // Voltar, Início, Menu
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledButton("Voltar") { RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", { key(RemoteKey.BACK) }, size = 58.dp, enabled = hasKeys && ready) }
            LabeledButton("Início") { RoundButton(Icons.Rounded.Home, "Início", { key(RemoteKey.HOME) }, size = 58.dp, enabled = hasKeys && ready) }
            LabeledButton("Menu") { RoundButton(Icons.Rounded.Menu, "Menu", { key(RemoteKey.MENU) }, size = 58.dp, enabled = hasKeys && ready) }
        }

        // Volume e canal
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Rocker(
                label = "VOL",
                enabled = ready && device.has(Caps.VOLUME),
                onUp = { send(TvCommands.volumeUp(device), coalesce = true) { it.copy(volume = ((it.volume ?: 0) + 1).coerceAtMost(100), muted = false) } },
                onDown = { send(TvCommands.volumeDown(device), coalesce = true) { it.copy(volume = ((it.volume ?: 0) - 1).coerceAtLeast(0)) } },
                upIcon = Icons.Rounded.Add,
                downIcon = Icons.Rounded.Remove,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Volume")
                Text(
                    if (s.muted == true) "—" else (s.volume?.toString() ?: "—"),
                    fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 52.sp, color = Palette.Text,
                )
                VolumeBar(if (s.muted == true) 0 else (s.volume ?: 0))
            }
            Rocker(
                label = "CH",
                enabled = ready && device.has(Caps.CHANNEL),
                onUp = { send(TvCommands.channelUp(device), coalesce = true) },
                onDown = { send(TvCommands.channelDown(device), coalesce = true) },
                upIcon = Icons.Rounded.KeyboardArrowUp,
                downIcon = Icons.Rounded.KeyboardArrowDown,
            )
        }

        // Volume exato: arraste e solte (manda o valor certo para a TV)
        if (device.has(Caps.VOLUME)) {
            var vol by remember(s.volume) { mutableFloatStateOf((s.volume ?: 0).toFloat()) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Palette.Surface).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = Palette.Muted)
                Slider(
                    value = vol,
                    onValueChange = { vol = it },
                    onValueChangeFinished = {
                        val v = vol.roundToInt()
                        send(device.cmd(Caps.VOLUME, "setVolume", listOf(v))) { it.copy(volume = v, muted = false) }
                    },
                    valueRange = 0f..100f,
                    enabled = ready,
                    colors = SliderDefaults.colors(thumbColor = Palette.Text, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Surface3),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text("${vol.roundToInt()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            }
        }

        // Reprodução
        if (device.has(Caps.PLAYBACK)) {
            val playing = s.playback == "playing"
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Palette.Surface).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Rounded.FastRewind, "Voltar a fita", { send(TvCommands.playback(device, "rewind")) }, size = 48.dp, container = Color.Transparent, enabled = ready)
                RoundButton(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Reproduzir",
                    { send(TvCommands.playback(device, if (playing) "pause" else "play")) { it.copy(playback = if (playing) "paused" else "playing") } },
                    size = 60.dp, container = Palette.Text, tint = Palette.Bg, enabled = ready, iconSize = 30.dp,
                )
                RoundButton(Icons.Rounded.Stop, "Parar", { send(TvCommands.playback(device, "stop")) { it.copy(playback = "stopped") } }, size = 48.dp, container = Color.Transparent, enabled = ready)
                RoundButton(Icons.Rounded.FastForward, "Avançar", { send(TvCommands.playback(device, "fastForward")) }, size = 48.dp, container = Color.Transparent, enabled = ready)
            }
        }

        // Apps
        if (device.has(Caps.LAUNCH)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Abrir app")
                TvCommands.apps.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { app ->
                            AppTile(app, Modifier.weight(1f), enabled = ready) { send(TvCommands.launch(device, app)) { it.copy(playback = "playing") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(device: Device) {
    val s = device.state
    val line = when {
        s.online == false -> "TV offline"
        s.switchOn == false -> "TV desligada"
        s.inputSource != null && s.inputSource.lowercase() !in setOf("dtv", "digitaltv", "tv") -> "Fonte: ${Labels.input(s.inputSource)}"
        s.channel != null -> "Canal ${s.channel}" + (s.channelName?.let { " · $it" } ?: "")
        else -> "Ligada"
    }
    val dot = when {
        s.online == false -> Palette.Faint
        s.switchOn == false -> Palette.Coral
        else -> Palette.Green
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.Surface).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(line, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

@Composable
private fun LabeledButton(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}

/** Direcional circular com OK no centro. */
@Composable
private fun DPad(enabled: Boolean, onKey: (RemoteKey) -> Unit) {
    var pressed by remember { mutableStateOf<RemoteKey?>(null) }
    Box(
        Modifier
            .size(268.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Palette.Surface2, Palette.Surface)))
            .border(1.dp, Palette.Outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Realce do lado pressionado
        Canvas(Modifier.fillMaxSize()) {
            val k = pressed ?: return@Canvas
            val start = when (k) {
                RemoteKey.UP -> 225f
                RemoteKey.RIGHT -> 315f
                RemoteKey.DOWN -> 45f
                RemoteKey.LEFT -> 135f
                else -> return@Canvas
            }
            val w = size.minDimension * 0.18f
            drawArc(
                color = Palette.Accent.copy(alpha = 0.22f), startAngle = start, sweepAngle = 90f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                style = Stroke(width = w),
            )
        }
        val zone = Modifier.size(width = 104.dp, height = 84.dp)
        Arrow(Icons.Rounded.KeyboardArrowUp, "Cima", zone.align(Alignment.TopCenter), enabled, pressed == RemoteKey.UP,
            { onKey(RemoteKey.UP) }) { pressed = if (it) RemoteKey.UP else null }
        Arrow(Icons.Rounded.KeyboardArrowDown, "Baixo", zone.align(Alignment.BottomCenter), enabled, pressed == RemoteKey.DOWN,
            { onKey(RemoteKey.DOWN) }) { pressed = if (it) RemoteKey.DOWN else null }
        val side = Modifier.size(width = 84.dp, height = 104.dp)
        Arrow(Icons.Rounded.KeyboardArrowLeft, "Esquerda", side.align(Alignment.CenterStart), enabled, pressed == RemoteKey.LEFT,
            { onKey(RemoteKey.LEFT) }) { pressed = if (it) RemoteKey.LEFT else null }
        Arrow(Icons.Rounded.KeyboardArrowRight, "Direita", side.align(Alignment.CenterEnd), enabled, pressed == RemoteKey.RIGHT,
            { onKey(RemoteKey.RIGHT) }) { pressed = if (it) RemoteKey.RIGHT else null }

        // OK
        var okDown by remember { mutableStateOf(false) }
        Box(
            Modifier
                .size(108.dp)
                .scale(if (okDown) 0.94f else 1f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Accent, Palette.AccentDeep)))
                .repeatPress(enabled = enabled, repeat = false, onPress = { onKey(RemoteKey.OK) }, pressed = { okDown = it }),
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Palette.Bg)
        }
    }
}

@Composable
private fun Arrow(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    enabled: Boolean,
    active: Boolean,
    onPress: () -> Unit,
    onPressed: (Boolean) -> Unit,
) {
    Box(
        modifier.repeatPress(enabled = enabled, onPress = onPress, pressed = onPressed),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) Palette.Accent else Palette.Text, modifier = Modifier.size(40.dp))
    }
}

/** Tecla dupla vertical (volume/canal) que repete ao segurar. */
@Composable
private fun Rocker(label: String, enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit, upIcon: ImageVector, downIcon: ImageVector) {
    var upDown by remember { mutableStateOf(false) }
    var downDown by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(80.dp)
            .height(208.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(Palette.Surface2)
            .border(1.dp, Palette.Outline, RoundedCornerShape(40.dp))
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(if (upDown) Palette.Surface3 else Color.Transparent)
                .repeatPress(enabled = enabled, onPress = onUp, pressed = { upDown = it }),
            contentAlignment = Alignment.Center,
        ) { Icon(upIcon, "$label mais", tint = Palette.Text, modifier = Modifier.size(30.dp)) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(if (downDown) Palette.Surface3 else Color.Transparent)
                .repeatPress(enabled = enabled, onPress = onDown, pressed = { downDown = it }),
            contentAlignment = Alignment.Center,
        ) { Icon(downIcon, "$label menos", tint = Palette.Text, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun VolumeBar(volume: Int) {
    val bars = 10
    val lit = (volume.coerceIn(0, 100) + 9) / 10
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(bars) { i ->
            Box(
                Modifier.width(5.dp).height((6 + i * 2).dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < lit) Palette.Accent else Palette.Surface3),
            )
        }
    }
}

private fun appColors(name: String): Pair<Color, Color> = when (name) {
    "Netflix" -> Color(0xFFE50914) to Color(0xFF831010)
    "YouTube" -> Color(0xFFFF3B30) to Color(0xFF9E1B14)
    "Prime Video" -> Color(0xFF1FA8E0) to Color(0xFF0B5C8A)
    "Max" -> Color(0xFF5B3DF5) to Color(0xFF2A1B8F)
    else -> Palette.Accent to Palette.AccentDeep
}

@Composable
private fun AppTile(app: TvApp, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val (a, b) = appColors(app.name)
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(a, b)))
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(app.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/** Teclado numérico: com tvChannel digita o canal e envia "Ir"; sem ele manda cada número. */
@Composable
private fun Keypad(
    device: Device,
    onSend: (Command?, ((com.kaique.casahub.data.DeviceState) -> com.kaique.casahub.data.DeviceState)?) -> Unit,
    onDone: () -> Unit,
) {
    val byChannel = device.has(Caps.CHANNEL)
    var typed by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (byChannel) {
            Text(
                if (typed.isEmpty()) "Digite o canal" else typed,
                fontFamily = Display, fontSize = if (typed.isEmpty()) 18.sp else 34.sp,
                color = if (typed.isEmpty()) Palette.Muted else Palette.Text,
                modifier = Modifier.height(44.dp),
            )
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", if (byChannel) "Ir" else "OK"))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.forEach { k ->
                    val special = k == "Ir" || k == "OK"
                    Box(
                        Modifier.weight(1f).aspectRatio(1.9f).clip(RoundedCornerShape(18.dp))
                            .background(if (special) Palette.Accent else Palette.Surface2)
                            .clickable {
                                when {
                                    k == "⌫" -> typed = typed.dropLast(1)
                                    k == "Ir" -> {
                                        if (typed.isNotEmpty()) {
                                            val ch = typed
                                            onSend(TvCommands.channel(device, ch)) { it.copy(channel = ch, channelName = null) }
                                            typed = ""
                                            onDone()
                                        }
                                    }
                                    k == "OK" -> { onSend(TvCommands.key(device, RemoteKey.OK), null); onDone() }
                                    byChannel -> if (typed.length < 4) typed += k
                                    else -> onSend(TvCommands.number(device, k.toInt()), null)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = if (special) Palette.Bg else Palette.Text)
                    }
                }
            }
        }
    }
}

