package com.kaique.casahub.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kaique.casahub.data.HomeStore
import com.kaique.casahub.data.UiState
import com.kaique.casahub.ui.components.Eyebrow
import com.kaique.casahub.ui.theme.Body
import com.kaique.casahub.ui.theme.Palette

@Composable
fun LoginScreen(state: UiState, store: HomeStore) {
    var token by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val err = state.error

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        // Fundo: anéis concêntricos que lembram um hub de sinal
        Canvas(Modifier.fillMaxWidth().height(420.dp)) {
            val c = Offset(size.width * 0.82f, size.height * 0.18f)
            drawCircle(Brush.radialGradient(listOf(Palette.Accent.copy(alpha = 0.35f), Palette.Bg.copy(alpha = 0f)), center = c, radius = size.width * 0.75f), radius = size.width * 0.75f, center = c)
            for (i in 1..5) drawCircle(Palette.Accent.copy(alpha = 0.10f / i * 2), radius = 70.dp.toPx() * i, center = c, style = Stroke(1.dp.toPx()))
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Palette.Accent, Palette.AccentDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Palette.Bg))
                Box(Modifier.size(34.dp).clip(CircleShape).border(2.dp, Palette.Bg, CircleShape))
            }
            Spacer(Modifier.height(36.dp))
            Eyebrow("CasaHub", color = Palette.Accent)
            Text("Sua casa inteira\nna palma da mão.", style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.35f, lineHeight = MaterialTheme.typography.headlineLarge.lineHeight * 1.3f))
            Text(
                "Luzes, TV com controle remoto completo, ar-condicionado, fechaduras e cenas da sua conta SmartThings.",
                style = MaterialTheme.typography.bodyLarge, color = Palette.Muted,
            )
            Spacer(Modifier.height(12.dp))

            // Campo do token
            Row(
                Modifier.fillMaxWidth().widthIn(max = 520.dp).height(60.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Surface)
                    .border(1.dp, if (err != null) Palette.Coral else Palette.Outline, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Key, null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (token.isEmpty()) Text("Cole seu token SmartThings", style = MaterialTheme.typography.bodyLarge, color = Palette.Faint)
                    BasicTextField(
                        value = token,
                        onValueChange = { token = it; if (state.error != null) store.clearError() },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Body, color = Palette.Text, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                        cursorBrush = SolidColor(Palette.Accent),
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { store.login(token) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icon(
                    if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Ocultar" else "Mostrar",
                    tint = Palette.Muted, modifier = Modifier.size(22.dp).clickable { show = !show },
                )
            }
            if (err != null) Text(err, style = MaterialTheme.typography.bodyMedium, color = Palette.Coral)

            PrimaryButton(if (state.loading) null else "Entrar", enabled = !state.loading && token.isNotBlank()) { store.login(token) }
            SecondaryButton("Explorar no modo demo", enabled = !state.loading) { store.startDemo() }

            Text(
                if (help) "Ocultar ajuda" else "Como conseguir o token?",
                style = MaterialTheme.typography.labelLarge, color = Palette.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { help = !help }.padding(vertical = 8.dp),
            )
            AnimatedVisibility(help) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Surface).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Step(1, "Abra account.smartthings.com/tokens e entre com sua conta Samsung.")
                    Step(2, "Toque em \"Generate new token\", marque todas as permissões e confirme.")
                    Step(3, "Copie o código gerado e cole no campo acima.")
                    Text(
                        "A Samsung faz os tokens pessoais novos expirarem em 24 horas. Quando expirar, o app pede outro.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                    )
                    SecondaryButton("Abrir página de tokens", enabled = true) { uri.openUri("https://account.smartthings.com/tokens") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun PrimaryButton(label: String?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50))
            .background(if (enabled || label == null) Palette.Accent else Palette.Surface3)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Palette.Bg)
        else Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Bg else Palette.Muted)
    }
}

@Composable
fun SecondaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50))
            .border(1.dp, Palette.Outline, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
    }
}
