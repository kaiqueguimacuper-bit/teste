package com.kaique.casahub.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kaique.casahub.R
import com.kaique.casahub.data.DeviceKind

/** Paleta do CasaHub: grafite azulado com cores de estado por tipo de aparelho. */
object Palette {
    val Bg = Color(0xFF0A0D12)
    val Surface = Color(0xFF131820)
    val Surface2 = Color(0xFF1A2029)
    val Surface3 = Color(0xFF232A35)
    val Outline = Color(0xFF2B3340)
    val Text = Color(0xFFF1F4F9)
    val Muted = Color(0xFF8C97A8)
    val Faint = Color(0xFF5B6576)

    val Accent = Color(0xFF7AA2FF)      // azul elétrico (ações principais)
    val AccentDeep = Color(0xFF3F63D8)
    val OnAccent = Color(0xFF07122E)

    val Amber = Color(0xFFFFC85C)       // luz
    val Cyan = Color(0xFF5DD9F0)        // clima
    val Violet = Color(0xFFB79CFF)      // tomadas / eletro
    val Green = Color(0xFF4FDDA4)       // seguro / ok
    val Coral = Color(0xFFFF7B72)       // alerta / desligar
    val Pink = Color(0xFFFF8FC7)        // som
}

fun kindColor(kind: DeviceKind): Color = when (kind) {
    DeviceKind.LIGHT -> Palette.Amber
    DeviceKind.TV, DeviceKind.CAMERA -> Palette.Accent
    DeviceKind.SPEAKER -> Palette.Pink
    DeviceKind.AC, DeviceKind.THERMOSTAT, DeviceKind.SENSOR -> Palette.Cyan
    DeviceKind.LOCK -> Palette.Green
    DeviceKind.CONTACT, DeviceKind.MOTION, DeviceKind.LEAK -> Palette.Coral
    DeviceKind.SHADE -> Palette.Amber
    DeviceKind.PLUG, DeviceKind.APPLIANCE, DeviceKind.OTHER -> Palette.Violet
}

val Display = FontFamily(
    Font(R.font.sora_300, FontWeight.Light),
    Font(R.font.sora_600, FontWeight.SemiBold),
    Font(R.font.sora_700, FontWeight.Bold),
)

val Body = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 64.sp, lineHeight = 66.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
)

private val Scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.AccentDeep,
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Amber,
    onSecondary = Color(0xFF2A1C00),
    secondaryContainer = Palette.Surface3,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Cyan,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface3,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Coral,
    onError = Color(0xFF2D0503),
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Bg,
)

@Composable
fun CasaHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography) {
        // Texto claro por padrão em todas as telas (o fundo é sempre escuro).
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
