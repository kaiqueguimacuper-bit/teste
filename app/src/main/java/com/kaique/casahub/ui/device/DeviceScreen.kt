package com.kaique.casahub.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.DeviceKind
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.UiState
import com.kaique.casahub.ui.components.Eyebrow
import com.kaique.casahub.ui.components.RoundButton
import com.kaique.casahub.ui.components.rememberTick
import com.kaique.casahub.ui.theme.Palette
import com.kaique.casahub.ui.theme.kindColor

@Composable
fun DeviceScreen(device: Device, state: UiState, store: HomeStore, onBack: () -> Unit) {
    val tick = rememberTick()
    val accent = kindColor(device.kind)
    val offline = device.state.online == false
    val active = device.isActive && !offline
    val favorite = device.id in state.favorites
    val pending = device.id in state.pending

    // Ao abrir: pede o estado atualizado ao aparelho (volume/canal reais) e prepara as teclas da TV.
    LaunchedEffect(device.id) { store.deviceOpened(device) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(
            Modifier.fillMaxWidth().height(380.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = if (active) 0.20f else 0.07f), Color.Transparent))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onBack, size = 48.dp, iconSize = 22.dp)
                Spacer(Modifier.weight(1f))
                RoundButton(
                    if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                    { tick(); store.toggleFavorite(device.id) },
                    size = 48.dp, iconSize = 22.dp, tint = if (favorite) Palette.Amber else Palette.Text,
                )
                if (device.canToggle && device.state.switchOn != null && device.kind != DeviceKind.TV) {
                    Spacer(Modifier.width(10.dp))
                    val on = device.state.switchOn == true
                    RoundButton(
                        Icons.Rounded.PowerSettingsNew, if (on) "Desligar" else "Ligar",
                        { tick(); store.toggle(device) },
                        size = 48.dp, iconSize = 22.dp,
                        container = if (on) Palette.Text else Palette.Surface2,
                        tint = if (on) Palette.Bg else Palette.Text,
                        enabled = !offline,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(state.roomName(device.roomId), color = accent)
                Text(device.name, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(device.summary, style = MaterialTheme.typography.bodyLarge, color = if (active) accent else Palette.Muted)
            }
            if (pending) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = accent, trackColor = Palette.Surface2)
            }

            if (offline) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Coral.copy(alpha = 0.12f)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.WifiOff, null, tint = Palette.Coral)
                    Text(
                        "Sem conexão com o aparelho. Confira se ele está na tomada e no Wi-Fi.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
                    )
                }
            }

            when (device.kind) {
                DeviceKind.TV -> TvRemote(device, store)
                DeviceKind.LIGHT -> LightControl(device, store)
                DeviceKind.AC, DeviceKind.THERMOSTAT -> ClimateControl(device, store)
                DeviceKind.LOCK -> LockControl(device, store)
                DeviceKind.SHADE -> ShadeControl(device, store)
                DeviceKind.SPEAKER -> SpeakerControl(device, store)
                else -> GenericControl(device, store, accent)
            }

            if (device.kind != DeviceKind.TV) Readings(device.state)
        }
    }
}
