package com.kaique.casahub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.ui.screens.HomeScreen
import com.kaique.casahub.ui.screens.LoginScreen

@Composable
fun CasaHubApp(store: HomeStore) {
    val state by store.state.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = state.loggedIn,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tela",
    ) { loggedIn ->
        if (loggedIn) HomeScreen(state, store) else LoginScreen(state, store)
    }
}
