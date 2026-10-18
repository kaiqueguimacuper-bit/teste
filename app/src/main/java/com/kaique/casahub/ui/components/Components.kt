package com.kaique.casahub.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.DeviceKind
import com.kaique.casahub.ui.theme.Palette
import com.kaique.casahub.ui.theme.kindColor

fun Device.icon(): ImageVector = when (kind) {
    DeviceKind.LIGHT -> Icons.Rounded.Lightbulb
    DeviceKind.PLUG -> Icons.Rounded.Power
    DeviceKind.TV -> Icons.Rounded.Tv
    DeviceKind.SPEAKER -> Icons.Rounded.Speaker
    DeviceKind.THERMOSTAT -> Icons.Rounded.Thermostat
    DeviceKind.AC -> Icons.Rounded.AcUnit
    DeviceKind.LOCK -> if (state.locked == false) Icons.Rounded.LockOpen else Icons.Rounded.Lock
    DeviceKind.CONTACT -> Icons.Rounded.MeetingRoom
    DeviceKind.MOTION -> Icons.Rounded.DirectionsRun
    DeviceKind.LEAK -> Icons.Rounded.WaterDrop
    DeviceKind.SHADE -> Icons.Rounded.ViewDay
    DeviceKind.SENSOR -> Icons.Rounded.DeviceThermostat
    DeviceKind.APPLIANCE -> Icons.Rounded.Kitchen
    DeviceKind.CAMERA -> Icons.Rounded.Videocam
    DeviceKind.OTHER -> if (capabilities.isEmpty()) Icons.Rounded.Devices else Icons.Rounded.Sensors
}

/** Vibração curta de tecla. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } }
}

/** Painel base: superfície com borda fina. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    color: Color = Palette.Surface,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .border(BorderStroke(1.dp, Palette.Outline.copy(alpha = 0.6f)), RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Rótulo pequeno em caixa alta. */
@Composable
fun Eyebrow(text: String, color: Color = Palette.Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Botão redondo com ícone; encolhe levemente ao tocar. */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = Palette.Surface2,
    tint: Color = Palette.Text,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "escala")
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(iconSize))
    }
}

/** Chip de opção (modo do ar, fonte da TV, cômodo…). */
@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit, color: Color = Palette.Accent, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) color else Palette.Surface2, label = "chip")
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Palette.Bg else Palette.Text,
            maxLines = 1,
        )
    }
}

/** Card de aparelho para as grades. Toque abre o aparelho; botão liga/desliga no canto. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceCard(
    device: Device,
    roomName: String?,
    favorite: Boolean,
    pending: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    val offline = device.state.online == false
    val active = device.isActive && !offline
    val accent = kindColor(device.kind)
    val glow by animateFloatAsState(if (active) 1f else 0f, label = "brilho")
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(shape)
            .background(Palette.Surface)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.30f * glow), accent.copy(alpha = 0.06f * glow), Color.Transparent),
                ),
            )
            .border(1.dp, if (active) accent.copy(alpha = 0.45f) else Palette.Outline.copy(alpha = 0.6f), shape)
            .combinedClickable(onClick = onOpen, onLongClick = { tick(); onLongPress() })
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (active) accent else Palette.Surface3),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = if (active) Palette.Bg else accent)
                    else Icon(
                        if (offline) Icons.Rounded.WifiOff else device.icon(), null,
                        tint = if (active) Palette.Bg else Palette.Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (favorite) Icon(Icons.Rounded.Star, "Favorito", tint = Palette.Amber, modifier = Modifier.padding(top = 2.dp, end = 6.dp).size(16.dp))
                if (device.canToggle && device.state.switchOn != null && !offline) {
                    val on = device.state.switchOn == true
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (on) Palette.Text else Palette.Surface3)
                            .clickable { tick(); onToggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew, if (on) "Desligar" else "Ligar",
                            tint = if (on) Palette.Bg else Palette.Muted, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(roomName, device.summary).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    offline || device.state.waterLeak == true -> Palette.Coral
                    active -> accent
                    else -> Palette.Muted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Pequeno indicador de valor (usado no resumo e nas leituras). */
@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
