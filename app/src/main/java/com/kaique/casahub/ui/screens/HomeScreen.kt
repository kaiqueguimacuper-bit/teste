package com.kaique.casahub.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kaique.casahub.data.Device
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.RoomFilter
import com.kaique.casahub.data.UiState
import com.kaique.casahub.data.fmt
import com.kaique.casahub.ui.theme.AlertRed
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, store: HomeStore) {
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var locMenu by remember { mutableStateOf(false) }

    // Mensagens rápidas (comando falhou, cena executada…)
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        store.consumeMessage()
        snackbar.showSnackbar(m)
    }

    // Atualização automática enquanto o app está na tela.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, state.autoRefresh) {
        if (!state.autoRefresh) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            store.refresh(silent = true)
            while (true) {
                delay(if (state.demo) 8_000 else 20_000)
                store.refresh(silent = true)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column(Modifier.clickable(enabled = state.locations.size > 1) { locMenu = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.locationName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.locations.size > 1) Icon(Icons.Rounded.ArrowDropDown, "Trocar local")
                        }
                        Text(
                            subtitle(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DropdownMenu(expanded = locMenu, onDismissRequest = { locMenu = false }) {
                            state.locations.forEach { l ->
                                DropdownMenuItem(text = { Text(l.name) }, onClick = { locMenu = false; store.selectLocation(l.id) })
                            }
                        }
                    }
                },
                actions = {
                    if (state.demo) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                            Text("DEMO", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { store.refresh() }) { Icon(Icons.Rounded.Refresh, "Atualizar") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.autoRefresh) "Desativar atualização automática" else "Ativar atualização automática") },
                                onClick = { menu = false; store.setAutoRefresh(!state.autoRefresh) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.demo) "Sair do modo demo" else "Sair da conta") },
                                onClick = { menu = false; store.logout() },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                state.loading && state.devices.isEmpty() -> CenterBox { CircularProgressIndicator() }
                state.error != null && state.devices.isEmpty() -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.error ?: "", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { store.refresh() }) { Text("Tentar de novo") }
                        OutlinedButton(onClick = { store.logout() }) { Text("Trocar token") }
                    }
                }
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { store.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DeviceGrid(state, store, onOpen = { selectedId = it.id })
                }
            }
        }
    }

    val selected = state.devices.firstOrNull { it.id == selectedId }
    if (selected != null) {
        DeviceSheet(
            device = selected,
            roomName = state.roomName(selected.roomId),
            favorite = selected.id in state.favorites,
            pending = selected.id in state.pending,
            store = store,
            onDismiss = { selectedId = null },
        )
    }
}

private fun subtitle(s: UiState): String {
    val parts = mutableListOf("${s.devices.size} dispositivos")
    if (s.onCount > 0) parts += "${s.onCount} ligados"
    if (s.lastUpdated > 0) parts += "às " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.lastUpdated))
    return parts.joinToString(" · ")
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun DeviceGrid(state: UiState, store: HomeStore, onOpen: (Device) -> Unit) {
    val visible = state.visibleDevices
    val grouped = state.roomFilter == RoomFilter.ALL && state.query.isBlank()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full("busca") { SearchField(state.query, store::setQuery) }
        full("resumo") { SummaryCard(state, store) }
        if (state.scenes.isNotEmpty()) full("cenas") { ScenesRow(state, store) }
        full("comodos") { RoomChips(state, store) }

        if (visible.isEmpty()) {
            full("vazio") {
                Text(
                    when {
                        state.query.isNotBlank() -> "Nada encontrado para \"${state.query}\"."
                        state.roomFilter == RoomFilter.FAVORITES -> "Sem favoritos ainda. Segure um dispositivo para favoritar."
                        else -> "Nenhum dispositivo aqui."
                    },
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (grouped) {
            val favs = visible.filter { it.id in state.favorites }
            if (favs.isNotEmpty()) {
                full("h_fav") { SectionHeader("Favoritos") }
                tiles(favs, state, store, onOpen, keyPrefix = "fav_")
            }
            val byRoom = visible.groupBy { d -> state.rooms.firstOrNull { it.id == d.roomId }?.id }
            val order = state.rooms.map { it.id } + listOf<String?>(null)
            order.forEach { roomId ->
                val list = byRoom[roomId].orEmpty()
                if (list.isNotEmpty()) {
                    full("h_${roomId ?: "none"}") { SectionHeader(state.roomName(roomId)) }
                    tiles(list, state, store, onOpen, keyPrefix = "r_")
                }
            }
        } else {
            tiles(visible, state, store, onOpen, keyPrefix = "")
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.tiles(
    list: List<Device>,
    state: UiState,
    store: HomeStore,
    onOpen: (Device) -> Unit,
    keyPrefix: String,
) {
    items(list, key = { keyPrefix + it.id }) { d ->
        DeviceTile(
            device = d,
            favorite = d.id in state.favorites,
            pending = d.id in state.pending,
            onToggle = { store.toggle(d) },
            onOpen = { onOpen(d) },
            onFavorite = { store.toggleFavorite(d.id) },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Buscar dispositivo ou cômodo") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Limpar") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SummaryCard(state: UiState, store: HomeStore) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stat("${state.onCount}", "ligados", Modifier.weight(1f))
                Stat(state.avgTemperature?.let { fmt(Math.round(it * 10) / 10.0) + "°" } ?: "—", "temperatura", Modifier.weight(1f))
                Stat("${state.offlineCount}", "offline", Modifier.weight(1f))
            }
            state.alerts.take(4).forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = AlertRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(a, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilledTonalButton(onClick = { store.allOff() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.roomFilter == RoomFilter.ALL && state.query.isBlank()) "Desligar tudo" else "Desligar os desta lista")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScenesRow(state: UiState, store: HomeStore) {
    Column {
        SectionHeader("Cenas")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.scenes, key = { it.id }) { sc ->
                val running = state.runningScene == sc.id
                AssistChip(
                    onClick = { if (!running) store.runScene(sc) },
                    label = { Text(sc.name) },
                    leadingIcon = {
                        if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.PlayArrow, null, Modifier.size(AssistChipDefaults.IconSize))
                    },
                    shape = RoundedCornerShape(50),
                )
            }
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
            FilterChip(
                selected = state.roomFilter == id,
                onClick = { store.setRoomFilter(id) },
                label = { Text(name) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceTile(
    device: Device,
    favorite: Boolean,
    pending: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val offline = device.state.online == false
    val active = device.isActive && !offline
    val container by animateColorAsState(
        when {
            offline -> MaterialTheme.colorScheme.surfaceContainerLow
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "cor",
    )
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onFavorite() },
            ),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(
                        if (offline) Icons.Rounded.WifiOff else device.icon(),
                        null,
                        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (favorite) Icon(Icons.Rounded.Star, "Favorito", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                if (device.canToggle && device.state.switchOn != null) {
                    Switch(
                        checked = device.state.switchOn == true,
                        onCheckedChange = { onToggle() },
                        enabled = !offline,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(device.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                device.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (offline || device.state.waterLeak == true) AlertRed else content.copy(alpha = 0.75f),
            )
        }
    }
}
