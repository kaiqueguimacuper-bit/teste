package com.kaique.casahub.ui.device

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaique.casahub.data.Caps
import com.kaique.casahub.data.Command
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.DeviceState
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.Labels
import com.kaique.casahub.data.fmt
import com.kaique.casahub.ui.components.Eyebrow
import com.kaique.casahub.ui.components.Metric
import com.kaique.casahub.ui.components.OptionChip
import com.kaique.casahub.ui.components.Panel
import com.kaique.casahub.ui.components.RoundButton
import com.kaique.casahub.ui.components.rememberTick
import com.kaique.casahub.ui.theme.Display
import com.kaique.casahub.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Controle deslizante grande e vertical, no estilo de painel de parede. Envia ao soltar. */
@Composable
fun BigSlider(
    value: Int,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCommit: (Int) -> Unit,
    onChange: (Int) -> Unit = {},
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(0, 100) / 100f) }
    val commit by rememberUpdatedState(onCommit)
    val change by rememberUpdatedState(onChange)
    Box(
        modifier
            .width(132.dp)
            .height(320.dp)
            .clip(RoundedCornerShape(44.dp))
            .background(Palette.Surface2)
            .border(1.dp, Palette.Outline, RoundedCornerShape(44.dp))
            .alpha(if (enabled) 1f else 0.45f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    local = (1f - down.position.y / h).coerceIn(0f, 1f)
                    change((local * 100).roundToInt())
                    drag(down.id) { c ->
                        local = (1f - c.position.y / h).coerceIn(0f, 1f)
                        change((local * 100).roundToInt())
                        c.consume()
                    }
                    commit((local * 100).roundToInt())
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(local)
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f)))),
        )
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp).width(36.dp).height(5.dp)
                .clip(RoundedCornerShape(3.dp)).background(Palette.Text.copy(alpha = 0.25f)),
        )
    }
}

// ---------------- Luz ----------------

private val huePresets = listOf(0 to Color(0xFFFF4D4D), 8 to Color(0xFFFF8A3D), 15 to Color(0xFFFFC85C), 33 to Color(0xFF4FDD7A),
    50 to Color(0xFF4FD9F0), 66 to Color(0xFF5B7CFF), 76 to Color(0xFFA066FF), 88 to Color(0xFFFF66C4))

@Composable
fun LightControl(device: Device, store: HomeStore) {
    val tick = rememberTick()
    val s = device.state
    val offline = s.online == false
    var preview by remember(s.level) { mutableStateOf(s.level ?: 0) }
    val on = s.switchOn == true

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (device.has(Caps.LEVEL)) {
            Text(
                if (on) "$preview%" else "Desligada",
                fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 56.sp, color = Palette.Text,
            )
            BigSlider(
                value = if (on) s.level ?: 0 else 0,
                color = Palette.Amber,
                enabled = !offline,
                onChange = { preview = it },
                onCommit = { v ->
                    tick()
                    store.send(device, Command(Caps.LEVEL, "setLevel", listOf(v))) { it.copy(level = v, switchOn = v > 0) }
                },
            )
        } else {
            BigPower(on = on, enabled = !offline, color = Palette.Amber) { tick(); store.toggle(device) }
        }

        if (device.has(Caps.COLOR_TEMP)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Tom de branco")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("Quente", 2700, Color(0xFFFFB566)), Triple("Neutro", 4000, Color(0xFFFFE2B8)), Triple("Frio", 6000, Color(0xFFDDEBFF))).forEach { (name, k, c) ->
                        val sel = s.colorTemp != null && kotlin.math.abs(s.colorTemp - k) < 500
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Palette.Surface3 else Palette.Surface)
                                .border(1.dp, if (sel) c else Palette.Outline, RoundedCornerShape(20.dp))
                                .clickable(enabled = !offline) {
                                    tick()
                                    store.send(device, Command(Caps.COLOR_TEMP, "setColorTemperature", listOf(k))) { it.copy(colorTemp = k, switchOn = true) }
                                }
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(c))
                            Text(name, style = MaterialTheme.typography.labelLarge)
                            Text("${k}K", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                    }
                }
            }
        }

        if (device.has(Caps.COLOR)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Cor")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    huePresets.forEach { (h, c) ->
                        val sel = s.hue != null && kotlin.math.abs(s.hue - h) < 4
                        Box(
                            Modifier.size(46.dp).clip(CircleShape)
                                .border(3.dp, if (sel) Palette.Text else Color.Transparent, CircleShape)
                                .padding(5.dp).clip(CircleShape).background(c)
                                .clickable(enabled = !offline) {
                                    tick()
                                    store.send(device, Command(Caps.COLOR, "setColor", listOf(mapOf("hue" to h, "saturation" to 100)))) {
                                        it.copy(hue = h.toDouble(), saturation = 100.0, switchOn = true)
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BigPower(on: Boolean, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(200.dp).clip(CircleShape)
            .background(if (on) Brush.radialGradient(listOf(color, color.copy(alpha = 0.55f))) else Brush.radialGradient(listOf(Palette.Surface3, Palette.Surface2)))
            .border(1.dp, if (on) color else Palette.Outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PowerSettingsNew, null, tint = if (on) Palette.Bg else Palette.Text, modifier = Modifier.size(56.dp))
            Text(if (on) "Ligado" else "Desligado", style = MaterialTheme.typography.titleMedium, color = if (on) Palette.Bg else Palette.Muted)
        }
    }
}

// ---------------- Clima ----------------

@Composable
fun ClimateControl(device: Device, store: HomeStore) {
    val tick = rememberTick()
    val s = device.state
    val offline = s.online == false
    val cooling = s.coolingSetpoint != null
    val setpoint = s.coolingSetpoint ?: s.heatingSetpoint
    val on = s.switchOn != false
    val step = if (s.tempUnit == "F") 1.0 else 0.5
    val minT = if (s.tempUnit == "F") 60.0 else 16.0
    val maxT = if (s.tempUnit == "F") 86.0 else 30.0

    fun setTo(v: Double) {
        val t = v.coerceIn(minT, maxT)
        tick()
        if (cooling) store.send(device, Command(Caps.THERMOSTAT_COOL, "setCoolingSetpoint", listOf(t))) { it.copy(coolingSetpoint = t) }
        else store.send(device, Command(Caps.THERMOSTAT_HEAT, "setHeatingSetpoint", listOf(t))) { it.copy(heatingSetpoint = t) }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (setpoint != null) {
            Box(Modifier.size(280.dp).alpha(if (on && !offline) 1f else 0.5f), contentAlignment = Alignment.Center) {
                val frac = ((setpoint - minT) / (maxT - minT)).toFloat().coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 22.dp.toPx()
                    val inset = stroke / 2 + 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(Palette.Surface3, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(
                        Brush.sweepGradient(listOf(Palette.Cyan, Palette.Accent, Palette.Coral, Palette.Cyan)),
                        135f, 270f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow(if (cooling) "Resfriar até" else "Aquecer até")
                    Text("${fmt(setpoint)}°", fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 72.sp, color = Palette.Text)
                    s.temperature?.let { Text("Ambiente ${fmt(it)}°", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Rounded.Remove, "Diminuir", { setTo(setpoint - step) }, size = 64.dp, enabled = on && !offline)
                RoundButton(Icons.Rounded.Add, "Aumentar", { setTo(setpoint + step) }, size = 64.dp, enabled = on && !offline)
            }
        } else {
            BigPower(on = on, enabled = !offline, color = Palette.Cyan) { tick(); store.toggle(device) }
        }

        val modes = s.acModes.ifEmpty { if (device.has(Caps.AC_MODE)) listOf("auto", "cool", "dry", "wind", "heat") else emptyList() }
        if (modes.isNotEmpty()) {
            ChipGroup("Modo", modes.map { it to Labels.acMode(it) }, s.acMode, Palette.Cyan, enabled = !offline) { m ->
                tick()
                store.send(device, Command(Caps.AC_MODE, "setAirConditionerMode", listOf(m))) { it.copy(acMode = m, switchOn = true) }
            }
        }
        val fans = s.fanModes.ifEmpty { if (device.has(Caps.AC_FAN)) listOf("auto", "low", "medium", "high") else emptyList() }
        if (fans.isNotEmpty()) {
            ChipGroup("Ventilador", fans.map { it to Labels.fanMode(it) }, s.fanMode, Palette.Accent, enabled = !offline && on) { f ->
                tick()
                store.send(device, Command(Caps.AC_FAN, "setFanMode", listOf(f))) { it.copy(fanMode = f) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(title: String, options: List<Pair<String, String>>, selected: String?, color: Color, enabled: Boolean, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(title)
        FlowRow(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (id, label) ->
                OptionChip(label, selected = id == selected, color = color, onClick = { if (enabled) onPick(id) })
            }
        }
    }
}

// ---------------- Fechadura ----------------

@Composable
fun LockControl(device: Device, store: HomeStore) {
    val tick = rememberTick()
    val s = device.state
    val locked = s.locked != false
    val offline = s.online == false
    val color = if (locked) Palette.Green else Palette.Coral
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState {
        tick()
        val target = !locked
        store.send(device, Command(Caps.LOCK, if (target) "lock" else "unlock")) { it.copy(locked = target) }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(
            Modifier.size(240.dp)
                .pointerInput(offline, locked) {
                    if (offline) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown()
                        var done = false
                        val job: Job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(900))
                            done = true
                            act()
                        }
                        waitForUpOrCancellation()
                        if (!done) job.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                drawCircle(Palette.Surface3, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                drawArc(
                    color, -90f, 360f * progress.value, false,
                    Offset(stroke, stroke), Size(size.width - stroke * 2, size.height - stroke * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier.size(180.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Palette.Surface2))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = color, modifier = Modifier.size(72.dp))
            }
        }
        Text(if (locked) "Trancada" else "Destrancada", style = MaterialTheme.typography.headlineMedium, color = color)
        Text(
            if (offline) "Fechadura offline" else if (locked) "Segure o botão para destrancar" else "Segure o botão para trancar",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
        )
    }
}

// ---------------- Cortina ----------------

@Composable
fun ShadeControl(device: Device, store: HomeStore) {
    val tick = rememberTick()
    val s = device.state
    val offline = s.online == false
    var preview by remember(s.shadeLevel) { mutableStateOf(s.shadeLevel ?: 0) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(if (preview == 0) "Fechada" else "$preview% aberta", fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 44.sp)
        if (device.has(Caps.SHADE_LEVEL)) {
            BigSlider(
                value = s.shadeLevel ?: 0, color = Palette.Amber, enabled = !offline,
                onChange = { preview = it },
                onCommit = { v ->
                    tick()
                    store.send(device, Command(Caps.SHADE_LEVEL, "setShadeLevel", listOf(v))) { it.copy(shadeLevel = v) }
                },
            )
        }
        if (device.has(Caps.SHADE)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledRound("Abrir") {
                    RoundButton(Icons.Rounded.KeyboardArrowUp, "Abrir", {
                        tick(); store.send(device, Command(Caps.SHADE, "open")) { it.copy(shadeLevel = 100, shadeState = "open") }
                    }, size = 64.dp, enabled = !offline)
                }
                LabeledRound("Pausar") {
                    RoundButton(Icons.Rounded.Pause, "Pausar", { tick(); store.fire(device, Command(Caps.SHADE, "pause")) }, size = 64.dp, enabled = !offline)
                }
                LabeledRound("Fechar") {
                    RoundButton(Icons.Rounded.KeyboardArrowDown, "Fechar", {
                        tick(); store.send(device, Command(Caps.SHADE, "close")) { it.copy(shadeLevel = 0, shadeState = "closed") }
                    }, size = 64.dp, enabled = !offline)
                }
            }
        }
    }
}

@Composable
private fun LabeledRound(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
    }
}

// ---------------- Som / genérico ----------------

@Composable
fun SpeakerControl(device: Device, store: HomeStore) {
    val tick = rememberTick()
    val s = device.state
    val offline = s.online == false
    var vol by remember(s.volume) { mutableFloatStateOf((s.volume ?: 0).toFloat()) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (device.has(Caps.VOLUME)) {
            Text("${vol.roundToInt()}", fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 64.sp)
            Eyebrow("Volume")
            Slider(
                value = vol, onValueChange = { vol = it }, valueRange = 0f..100f, enabled = !offline,
                onValueChangeFinished = {
                    val v = vol.roundToInt(); tick()
                    store.send(device, Command(Caps.VOLUME, "setVolume", listOf(v))) { it.copy(volume = v) }
                },
                colors = SliderDefaults.colors(thumbColor = Palette.Pink, activeTrackColor = Palette.Pink, inactiveTrackColor = Palette.Surface3),
            )
        }
        if (device.has(Caps.PLAYBACK)) {
            val playing = s.playback == "playing"
            RoundButton(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Tocar",
                { tick(); store.fire(device, Command(Caps.PLAYBACK, if (playing) "pause" else "play")) { it.copy(playback = if (playing) "paused" else "playing") } },
                size = 72.dp, container = Palette.Text, tint = Palette.Bg, iconSize = 34.dp, enabled = !offline,
            )
        }
        if (!device.has(Caps.VOLUME) && !device.has(Caps.PLAYBACK) && device.canToggle) {
            BigPower(on = s.switchOn == true, enabled = !offline, color = Palette.Pink) { tick(); store.toggle(device) }
        }
    }
}

@Composable
fun GenericControl(device: Device, store: HomeStore, color: Color) {
    val tick = rememberTick()
    if (device.canToggle && device.state.switchOn != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BigPower(on = device.state.switchOn == true, enabled = device.state.online != false, color = color) { tick(); store.toggle(device) }
        }
    }
}

/** Leituras do aparelho em blocos. */
@Composable
fun Readings(s: DeviceState) {
    val items = buildList {
        s.temperature?.let { add(Triple("${fmt(it)}°", "Temperatura", Palette.Cyan)) }
        s.humidity?.let { add(Triple("${fmt(it)}%", "Umidade", Palette.Accent)) }
        s.contactOpen?.let { add(Triple(if (it) "Aberta" else "Fechada", "Porta/janela", if (it) Palette.Coral else Palette.Green)) }
        s.motion?.let { add(Triple(if (it) "Detectado" else "Nenhum", "Movimento", if (it) Palette.Amber else Palette.Text)) }
        s.waterLeak?.let { add(Triple(if (it) "Vazamento" else "Seco", "Água", if (it) Palette.Coral else Palette.Green)) }
        s.power?.let { add(Triple("${fmt(it)} W", "Consumo agora", Palette.Violet)) }
        s.energy?.let { add(Triple("${fmt(it)} kWh", "Energia total", Palette.Violet)) }
        s.battery?.let { add(Triple("$it%", "Bateria", if (it <= 15) Palette.Coral else Palette.Text)) }
    }
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("Leituras")
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (v, l, c) ->
                    Panel(Modifier.weight(1f), radius = 22.dp, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        Metric(v, l, color = c)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
