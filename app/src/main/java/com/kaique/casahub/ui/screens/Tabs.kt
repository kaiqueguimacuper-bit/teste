package com.kaique.casahub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.RoomFilter
import com.kaique.casahub.data.Scene
import com.kaique.casahub.data.UiState
import com.kaique.casahub.data.fmt
import com.kaique.casahub.ui.components.DeviceCard
import com.kaique.casahub.ui.components.Dot
import com.kaique.casahub.ui.components.Eyebrow
import com.kaique.casahub.ui.components.Metric
import com.kaique.casahub.ui.components.OptionChip
import com.kaique.casahub.ui.components.Panel
import com.kaique.casahub.ui.components.RoundButton
import com.kaique.casahub.ui.components.SectionTitle
import com.kaique.casahub.ui.components.rememberTick
import com.kaique.casahub.ui.theme.Body
import com.kaique.casahub.ui.theme.Palette
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.kaique.casahub.data.Caps
import com.kaique.casahub.data.DeviceKind

private val gridPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp)

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.deviceItems(list: List<Device>, state: UiState, store: HomeStore, onOpen: (Device) -> Unit, prefix: String, showRoom: Boolean) {
    items(list, key = { prefix + it.id }) { d ->
        DeviceCard(
            device = d,
            roomName = if (showRoom) state.roomName(d.roomId) else null,
            favorite = d.id in state.favorites,
            pending = d.id in state.pending,
            onOpen = { onOpen(d) },
            onToggle = { store.toggle(d) },
            onLongPress = { store.toggleFavorite(d.id) },
        )
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

fun sceneIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        "noite" in n || "dormir" in n -> Icons.Rounded.Bedtime
        "sair" in n || "saída" in n -> Icons.AutoMirrored.Rounded.Logout
        "chegu" in n || "chegar" in n || "casa" in n -> Icons.Rounded.Home
        "cinema" in n || "filme" in n || "tv" in n -> Icons.Rounded.Movie
        "manhã" in n || "acordar" in n || "dia" in n -> Icons.Rounded.WbSunny
        else -> Icons.Rounded.AutoAwesome
    }
}

// ================= Início =================

@Composable
fun HomeTab(state: UiState, store: HomeStore, onOpen: (Device) -> Unit, onPickLocation: () -> Unit) {
    val favs = state.devices.filter { it.id in state.favorites }.sortedBy { it.name.lowercase() }
    val activeNow = state.devices.filter { it.isActive && it.state.online != false && it.id !in state.favorites }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        full("header") { HomeHeader(state, store, onPickLocation) }
        full("hero") { HouseHero(state, store) }
        if (state.scenes.isNotEmpty()) {
            full("cenas_t") { SectionTitle("Cenas") }
            full("cenas") { ScenesRow(state, store) }
        }
        full("fav_t") { SectionTitle("Favoritos") }
        if (favs.isEmpty()) {
            full("fav_vazio") {
                Panel(Modifier.fillMaxWidth(), radius = 24.dp) {
                    Text("Nenhum favorito ainda", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Segure qualquer aparelho para fixá-lo aqui.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        } else deviceItems(favs, state, store, onOpen, "f_", showRoom = true)
        if (activeNow.isNotEmpty()) {
            full("ativos_t") { SectionTitle("Ligados agora") }
            deviceItems(activeNow, state, store, onOpen, "a_", showRoom = true)
        }
    }
}

@Composable
private fun HomeHeader(state: UiState, store: HomeStore, onPickLocation: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow(greeting())
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = state.locations.size > 1, onClick = onPickLocation),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.locationName, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.demo) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.Amber.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("DEMO", style = MaterialTheme.typography.labelSmall, color = Palette.Amber)
            }
            Spacer(Modifier.width(10.dp))
        }
        if (state.refreshing) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Accent)
            }
        } else RoundButton(Icons.Rounded.Refresh, "Atualizar", { store.refresh() }, size = 44.dp, iconSize = 20.dp)
    }
}

@Composable
private fun HouseHero(state: UiState, store: HomeStore) {
    val tick = rememberTick()
    val power = state.devices.mapNotNull { it.state.power }.sum()
    val hum = state.devices.mapNotNull { it.state.humidity }.takeIf { it.isNotEmpty() }?.average()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B2540), Color(0xFF141B2A), Palette.Surface)))
            .border(1.dp, Palette.Outline.copy(alpha = 0.7f), RoundedCornerShape(32.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Dentro de casa")
                Text(
                    state.avgTemperature?.let { fmt(Math.round(it * 10) / 10.0) + "°" } ?: "—",
                    style = MaterialTheme.typography.displayLarge,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hum?.let { Text("Umidade ${it.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted) }
                if (power > 0) Text("${fmt(Math.round(power).toDouble())} W agora", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Metric("${state.onCount}", "ligados", color = Palette.Accent)
            Metric("${state.devices.size}", "aparelhos")
            Metric("${state.offlineCount}", "offline", color = if (state.offlineCount > 0) Palette.Coral else Palette.Text)
        }
        val alerts = state.alerts
        if (alerts.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Palette.Green)
                Spacer(Modifier.width(10.dp))
                Text("Tudo em ordem: portas fechadas e nenhum alerta.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts.take(3).forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(Palette.Coral)
                        Spacer(Modifier.width(10.dp))
                        Text(a, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Palette.Text.copy(alpha = 0.08f))
                .clickable { tick(); store.allOff() }.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, null, tint = Palette.Coral, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Desligar tudo", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScenesRow(state: UiState, store: HomeStore) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.scenes, key = { it.id }) { sc -> SceneCard(sc, state.runningScene == sc.id, Modifier.width(132.dp)) { store.runScene(sc) } }
    }
}

@Composable
fun SceneCard(scene: Scene, running: Boolean, modifier: Modifier = Modifier, onRun: () -> Unit) {
    val tick = rememberTick()
    Column(
        modifier
            .height(112.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .border(1.dp, if (running) Palette.Accent else Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .clickable(enabled = !running) { tick(); onRun() }
            .padding(16.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Palette.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Accent)
            else Icon(sceneIcon(scene.name), null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(scene.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ================= Dispositivos =================

@Composable
fun DevicesTab(state: UiState, store: HomeStore, onOpen: (Device) -> Unit) {
    val visible = state.visibleDevices
    val grouped = state.roomFilter == RoomFilter.ALL && state.query.isBlank()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        full("titulo") {
            Column(Modifier.padding(top = 12.dp)) {
                Eyebrow("${state.devices.size} aparelhos · ${state.rooms.size} cômodos")
                Text("Dispositivos", style = MaterialTheme.typography.headlineLarge)
            }
        }
        full("busca") { SearchBox(state.query, store::setQuery) }
        full("comodos") { RoomChips(state, store) }
        if (visible.isEmpty()) {
            full("vazio") {
                Text(
                    when {
                        state.query.isNotBlank() -> "Nada encontrado para \"${state.query}\"."
                        state.roomFilter == RoomFilter.FAVORITES -> "Sem favoritos ainda. Segure um aparelho para favoritar."
                        else -> "Nenhum aparelho aqui."
                    },
                    color = Palette.Muted, modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        } else if (grouped) {
            val order = state.rooms.map { it.id } + listOf<String?>(null)
            val byRoom = visible.groupBy { d -> state.rooms.firstOrNull { it.id == d.roomId }?.id }
            order.forEach { roomId ->
                val list = byRoom[roomId].orEmpty()
                if (list.isNotEmpty()) {
                    full("h_${roomId ?: "none"}") {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(state.roomName(roomId), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(8.dp))
                            Text("${list.size}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                        }
                    }
                    deviceItems(list, state, store, onOpen, "r_", showRoom = false)
                }
            }
        } else deviceItems(visible, state, store, onOpen, "", showRoom = true)
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Palette.Surface)
            .border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Buscar aparelho ou cômodo", style = MaterialTheme.typography.bodyLarge, color = Palette.Faint)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = Body, color = Palette.Text, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Limpar", tint = Palette.Muted, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

@Composable
private fun RoomChips(state: UiState, store: HomeStore) {
    val chips = buildList {
        add(RoomFilter.ALL to "Todos")
        add(RoomFilter.FAVORITES to "Favoritos")
        state.rooms.forEach { add(it.id to it.name) }
        if (state.devices.any { d -> d.roomId == null || state.rooms.none { it.id == d.roomId } }) add(RoomFilter.NONE to "Sem cômodo")
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.first }) { (id, name) ->
            OptionChip(name, selected = state.roomFilter == id, onClick = { store.setRoomFilter(id) })
        }
    }
}

// ================= Cenas =================

@Composable
fun ScenesTab(state: UiState, store: HomeStore) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        full("titulo") {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Eyebrow("Um toque, vários aparelhos")
                Text("Cenas", style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (state.scenes.isEmpty()) {
            full("vazio") {
                Panel(Modifier.fillMaxWidth(), radius = 24.dp) {
                    Text("Nenhuma cena neste local", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Crie cenas no app SmartThings e elas aparecem aqui automaticamente.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        }
        items(state.scenes, key = { it.id }) { sc ->
            val running = state.runningScene == sc.id
            val tick = rememberTick()
            Column(
                Modifier.height(168.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Palette.Accent.copy(alpha = 0.16f), Palette.Surface)))
                    .border(1.dp, if (running) Palette.Accent else Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    .clickable(enabled = !running) { tick(); store.runScene(sc) }
                    .padding(18.dp),
            ) {
                Icon(sceneIcon(sc.name), null, tint = Palette.Accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.weight(1f))
                Text(sc.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Palette.Text), contentAlignment = Alignment.Center) {
                        if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.Bg)
                        else Icon(Icons.Rounded.PlayArrow, "Executar", tint = Palette.Bg, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "Executando…" else "Executar", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                }
            }
        }
    }
}

// ================= Ajustes =================

@Composable
fun SettingsTab(state: UiState, store: HomeStore) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = gridPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        full("titulo") {
            Text("Ajustes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp))
        }
        full("conta") {
            Panel(Modifier.fillMaxWidth(), radius = 24.dp) {
                Eyebrow("Conta")
                Spacer(Modifier.height(6.dp))
                Text(if (state.demo) "Modo demo" else "SmartThings conectada", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (state.demo) "Casa simulada para explorar o app sem conta." else "Usando seu token de acesso pessoal.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
            }
        }
        if (state.locations.size > 1) {
            full("locais") {
                Panel(Modifier.fillMaxWidth(), radius = 24.dp) {
                    Eyebrow("Local")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.locations, key = { it.id }) { l ->
                            OptionChip(l.name, selected = l.id == state.locationId, onClick = { store.selectLocation(l.id) })
                        }
                    }
                }
            }
        }
        full("auto") {
            Panel(Modifier.fillMaxWidth(), radius = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Atualização automática", style = MaterialTheme.typography.titleMedium)
                        Text("Lê o estado da casa a cada 20 segundos com o app aberto.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    Switch(
                        checked = state.autoRefresh, onCheckedChange = { store.setAutoRefresh(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.Bg),
                    )
                }
            }
        }
        full("sair") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Coral.copy(alpha = 0.12f))
                    .clickable { store.logout() }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = Palette.Coral)
                Spacer(Modifier.width(12.dp))
                Text(if (state.demo) "Sair do modo demo" else "Desconectar conta", style = MaterialTheme.typography.titleMedium, color = Palette.Coral)
            }
        }
        full("diag") { DiagnosticsPanel(state, store) }
        full("sobre") {
            Text("CasaHub 2.2 · controle seus aparelhos SmartThings", style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DiagnosticsPanel(state: UiState, store: HomeStore) {
    val clipboard = LocalClipboardManager.current
    var entries by remember { mutableStateOf(store.diagnostics()) }
    var open by remember { mutableStateOf(false) }
    val fmtTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val tvs = state.devices.filter { it.kind == DeviceKind.TV }

    fun report(): String = buildString {
        appendLine("CasaHub 2.2 — diagnóstico")
        appendLine("Modo: " + if (state.demo) "demo" else "SmartThings")
        tvs.forEach { tv ->
            appendLine("TV ${tv.name}: online=${tv.state.online} switch=${tv.state.switchOn} volume=${tv.state.volume}")
            appendLine("  remoteControl=${tv.has(Caps.REMOTE)} (${tv.componentFor(Caps.REMOTE)} v${tv.capVersions[Caps.REMOTE] ?: "-"}) keypad=${tv.has(Caps.KEYPAD)} refresh=${tv.has(Caps.REFRESH)}")
            state.remoteKeys[tv.id]?.let { appendLine("  teclas aceitas: ${it.joinToString()}") }
        }
        entries.forEach { e ->
            appendLine("${fmtTime.format(Date(e.time))} ${e.method} ${e.path} -> ${e.code} (${e.ms} ms)" + (e.detail?.let { " $it" } ?: ""))
        }
    }

    Panel(Modifier.fillMaxWidth(), radius = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Diagnóstico da conexão", style = MaterialTheme.typography.titleMedium)
                Text("Mostra as últimas respostas da SmartThings, para achar o motivo de um comando não funcionar.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip(if (open) "Ocultar" else "Ver registros", selected = open, onClick = { entries = store.diagnostics(); open = !open })
            OptionChip("Copiar relatório", selected = false, onClick = {
                entries = store.diagnostics()
                clipboard.setText(AnnotatedString(report()))
                store.notify("Relatório copiado. Cole na conversa para análise.")
            })
        }
        if (open) {
            Spacer(Modifier.height(12.dp))
            tvs.forEach { tv ->
                val via = when {
                    tv.has(Caps.REMOTE) -> "samsungvd.remoteControl"
                    tv.has(Caps.KEYPAD) -> "keypadInput"
                    else -> "nenhum (a TV não aceita teclas pela nuvem)"
                }
                Text("${tv.name}: teclas via $via", style = MaterialTheme.typography.bodySmall)
            }
            if (entries.isEmpty()) Text("Nenhuma chamada registrada ainda.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            entries.take(25).forEach { e ->
                val ok = e.code in 200..299
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Dot(if (ok) Palette.Green else Palette.Coral, 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("${e.method} ${e.path.take(60)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${fmtTime.format(Date(e.time))} · ${if (e.code < 0) "sem conexão" else e.code.toString()} · ${e.ms} ms" + (e.detail?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = if (ok) Palette.Muted else Palette.Coral, maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}
