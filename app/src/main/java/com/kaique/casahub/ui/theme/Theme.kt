package com.kaique.casahub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFF2C14E)
val AlertRed = Color(0xFFD64545)

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDD),
    onPrimaryContainer = Color(0xFF062B21),
    secondary = Color(0xFF8A6A1F),
    secondaryContainer = Color(0xFFFBE6B5),
    onSecondaryContainer = Color(0xFF2B1F00),
    background = Color(0xFFF4F6F2),
    surface = Color(0xFFF4F6F2),
    surfaceContainer = Color(0xFFE9EDE7),
    surfaceContainerHigh = Color(0xFFE2E7E0),
    surfaceContainerLow = Color(0xFFEFF2EC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8ED6BD),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF155443),
    onPrimaryContainer = Color(0xFFCDEBDD),
    secondary = Color(0xFFF2C14E),
    secondaryContainer = Color(0xFF4A3A0C),
    onSecondaryContainer = Color(0xFFFBE6B5),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252C28),
    surfaceContainerLow = Color(0xFF161B18),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun CasaHubTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
