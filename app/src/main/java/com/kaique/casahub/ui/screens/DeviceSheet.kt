package com.kaique.casahub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kaique.casahub.data.Caps
import com.kaique.casahub.data.Command
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.fmt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(
    device: Device,
    roomName: String,
    favorite: Boolean,
    pending: Boolean,
    store: HomeStore,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val s = device.state
    val offline = s.online == false
    fun send(cmd: Command, f: (com.kaique.casahub.data.DeviceState) -> com.kaique.casahub.data.DeviceState) =
        store.send(device, cmd, f)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Cabeçalho
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(device.icon(), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleLarge)
                    Text("$roomName · ${device.summary}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { store.toggleFavorite(device.id) }) {
                    Icon(
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                        tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (pending) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (offline) {
                Text(
                    "Este dispositivo está offline. Verifique se ele está ligado na tomada e conectado à rede.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Liga/desliga
            if (device.canToggle && s.switchOn != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Energia", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = s.switchOn == true, onCheckedChange = { store.toggle(device) }, enabled = !offline)
                }
            }

            // Brilho
            if (device.has(Caps.LEVEL)) {
                CommandSlider("Brilho", s.level ?: 0, 0f..100f, { "$it%" }, !offline) { v ->
                    send(Command(Caps.LEVEL, "setLevel", listOf(v))) { it.copy(level = v, switchOn = v > 0) }
                }
            }
            // Temperatura de cor
            if (device.has(Caps.COLOR_TEMP)) {
                CommandSlider("Temperatura de cor", s.colorTemp ?: 3000, 2000f..6500f, { "${it}K" }, !offline, step = 100) { v ->
                    send(Command(Caps.COLOR_TEMP, "setColorTemperature", listOf(v))) { it.copy(colorTemp = v) }
                }
            }
            // Cor
            if (device.has(Caps.COLOR)) {
                CommandSlider("Cor (matiz)", (s.hue ?: 0.0).roundToInt(), 0f..100f, { "$it" }, !offline) { v ->
                    send(Command(Caps.COLOR, "setHue", listOf(v))) { it.copy(hue = v.toDouble()) }
                }
            }

            // Fechadura
            if (device.has(Caps.LOCK)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { send(Command(Caps.LOCK, "lock")) { it.copy(locked = true) } },
                        enabled = !offline && s.locked != true,
                        modifier = Modifier.weight(1f),
                    ) { Text("Trancar") }
                    OutlinedButton(
                        onClick = { send(Command(Caps.LOCK, "unlock")) { it.copy(locked = false) } },
                        enabled = !offline && s.locked != false,
                        modifier = Modifier.weight(1f),
                    ) { Text("Destrancar") }
                }
            }

            // Termostato / ar-condicionado
            s.coolingSetpoint?.let { sp ->
                Setpoint("Resfriar até", sp, s.tempUnit, !offline) { v ->
                    send(Command(Caps.THERMOSTAT_COOL, "setCoolingSetpoint", listOf(v))) { it.copy(coolingSetpoint = v) }
                }
            }
            s.heatingSetpoint?.let { sp ->
                Setpoint("Aquecer até", sp, s.tempUnit, !offline) { v ->
                    send(Command(Caps.THERMOSTAT_HEAT, "setHeatingSetpoint", listOf(v))) { it.copy(heatingSetpoint = v) }
                }
            }

            // Persiana / cortina
            if (device.has(Caps.SHADE)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { send(Command(Caps.SHADE, "open")) { it.copy(shadeLevel = 100, shadeState = "open") } },
                        enabled = !offline, modifier = Modifier.weight(1f),
                    ) { Text("Abrir") }
                    OutlinedButton(
                        onClick = { send(Command(Caps.SHADE, "close")) { it.copy(shadeLevel = 0, shadeState = "closed") } },
                        enabled = !offline, modifier = Modifier.weight(1f),
                    ) { Text("Fechar") }
                }
            }
            if (device.has(Caps.SHADE_LEVEL)) {
                CommandSlider("Abertura", s.shadeLevel ?: 0, 0f..100f, { "$it%" }, !offline) { v ->
                    send(Command(Caps.SHADE_LEVEL, "setShadeLevel", listOf(v))) { it.copy(shadeLevel = v) }
                }
            }

            // Volume
            if (device.has(Caps.VOLUME)) {
                CommandSlider("Volume", s.volume ?: 0, 0f..100f, { "$it" }, !offline) { v ->
                    send(Command(Caps.VOLUME, "setVolume", listOf(v))) { it.copy(volume = v) }
                }
            }
            if (device.has(Caps.MUTE) && s.muted != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mudo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = s.muted == true,
                        onCheckedChange = { on -> send(Command(Caps.MUTE, if (on) "mute" else "unmute")) { it.copy(muted = on) } },
                        enabled = !offline,
                    )
                }
            }

            // Leituras
            val readings = buildList {
                s.temperature?.let { add("Temperatura" to "${fmt(it)}°${s.tempUnit ?: "C"}") }
                s.humidity?.let { add("Umidade" to "${fmt(it)}%") }
                s.contactOpen?.let { add("Porta/janela" to if (it) "Aberta" else "Fechada") }
                s.motion?.let { add("Movimento" to if (it) "Detectado" else "Nenhum") }
                s.waterLeak?.let { add("Água" to if (it) "Vazamento detectado!" else "Seco") }
                s.power?.let { add("Consumo agora" to "${fmt(it)} W") }
                s.battery?.let { add("Bateria" to "$it%") }
                s.thermostatMode?.let { add("Modo" to it) }
                s.online?.let { add("Conexão" to if (it) "Online" else "Offline") }
            }
            if (readings.isNotEmpty()) {
                HorizontalDivider()
                readings.forEach { (k, v) ->
                    Row {
                        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Slider que só envia o comando quando o dedo solta (evita dezenas de requisições). */
@Composable
private fun CommandSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    format: (Int) -> String,
    enabled: Boolean,
    step: Int = 1,
    onCommit: (Int) -> Unit,
) {
    var pos by remember(value) { mutableFloatStateOf(value.toFloat().coerceIn(range)) }
    val shown = ((pos / step).roundToInt() * step)
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(format(shown), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { if (shown != value) onCommit(shown) },
            valueRange = range,
            enabled = enabled,
        )
    }
}

@Composable
private fun Setpoint(label: String, value: Double, unit: String?, enabled: Boolean, onSet: (Double) -> Unit) {
    val stepVal = if (unit == "F") 1.0 else 0.5
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onSet(value - stepVal) }, enabled = enabled) { Icon(Icons.Rounded.Remove, "Diminuir") }
        Text(
            "${fmt(value)}°${unit ?: "C"}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        FilledTonalIconButton(onClick = { onSet(value + stepVal) }, enabled = enabled) { Icon(Icons.Rounded.Add, "Aumentar") }
    }
}
