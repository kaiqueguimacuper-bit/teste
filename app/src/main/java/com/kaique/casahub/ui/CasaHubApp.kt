package com.kaique.casahub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.ui.components.rememberTick
import com.kaique.casahub.ui.device.DeviceScreen
import com.kaique.casahub.ui.screens.DevicesTab
import com.kaique.casahub.ui.screens.HomeTab
import com.kaique.casahub.ui.screens.LoginScreen
import com.kaique.casahub.ui.screens.ScenesTab
import com.kaique.casahub.ui.screens.SettingsTab
import com.kaique.casahub.ui.theme.Palette
import kotlinx.coroutines.delay

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Início", Icons.Rounded.Home),
    TabItem("Aparelhos", Icons.Rounded.GridView),
    TabItem("Cenas", Icons.Rounded.AutoAwesome),
    TabItem("Ajustes", Icons.Rounded.Settings),
)

/**
 * [initialScreen]: "home", "devices", "scenes", "settings" ou "device:<id>"
 * (usado para abrir uma tela direto, inclusive nas capturas automáticas).
 */
@Composable
fun CasaHubApp(store: HomeStore, initialScreen: String? = null) {
    val state by store.state.collectAsStateWithLifecycle()
    val startTab = when (initialScreen) {
        "devices" -> 1
        "scenes" -> 2
        "settings" -> 3
        else -> 0
    }
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var openId by rememberSaveable { mutableStateOf(initialScreen?.takeIf { it.startsWith("device:") }?.removePrefix("device:")) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        store.consumeMessage()
        snackbar.showSnackbar(m)
    }

    // Atualização automática enquanto o app está aberto.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, state.autoRefresh, state.loggedIn) {
        if (!state.autoRefresh || !state.loggedIn) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(if (state.demo) 8_000 else 20_000)
                store.refresh(silent = true)
            }
        }
    }

    BackHandler(enabled = state.loggedIn && openId != null) { store.deviceClosed(); openId = null }
    BackHandler(enabled = state.loggedIn && openId == null && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        if (!state.loggedIn) {
            LoginScreen(state, store)
        } else if (state.devices.isEmpty() && state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
        } else {
            val device = state.devices.firstOrNull { it.id == openId }
            AnimatedContent(
                targetState = device?.id,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                    } else {
                        fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220)))
                    }
                },
                label = "navegacao",
            ) { id ->
                val d = state.devices.firstOrNull { it.id == id }
                if (d != null) {
                    DeviceScreen(d, state, store, onBack = { store.deviceClosed(); openId = null })
                } else {
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            0 -> HomeTab(state, store, onOpen = { openId = it.id }, onPickLocation = { tab = 3 })
                            1 -> DevicesTab(state, store, onOpen = { openId = it.id })
                            2 -> ScenesTab(state, store)
                            else -> SettingsTab(state, store)
                        }
                        BottomBar(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (openId == null && state.loggedIn) 96.dp else 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Palette.Surface3,
                contentColor = Palette.Text,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun BottomBar(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val tick = rememberTick()
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Palette.Surface.copy(alpha = 0.96f))
            .border(1.dp, Palette.Outline, RoundedCornerShape(34.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (sel) Palette.Surface3 else Palette.Surface.copy(alpha = 0f))
                    .clickable { if (!sel) { tick(); onSelect(i) } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(t.icon, t.label, tint = if (sel) Palette.Accent else Palette.Muted, modifier = Modifier.size(22.dp))
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (sel) Palette.Text else Palette.Muted)
            }
        }
    }
}
