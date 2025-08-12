package com.tigonic.snoozely.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ====== Typography ======
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

// ====== Extra Colors (5 Basisfarben + UI-Rollen) ======
data class ExtraColors(
    // 5 Basisfarben
    val brand1: Color,   // Primärakzent (z. B. Violett)
    val brand2: Color,   // Sekundär (z. B. Türkis)
    val brand3: Color,   // Erfolg/Grün
    val brand4: Color,   // Warn-/Gelb/Orange
    val brand5: Color,   // Info/Cyan

    // Abgeleitete Rollen
    val toggle: Color,
    val slider: Color,
    val heading: Color,
    val icon: Color,
    val infoText: Color,
    val popupBg: Color,
    val popupContent: Color,

    // Gradients
    val wheelGradient: List<Color>,
    val shakeGradient: List<Color>
)

val LocalExtraColors = staticCompositionLocalOf {
    // Fallback – wird in SnoozelyTheme je Theme überschrieben
    ExtraColors(
        brand1 = Color(0xFF7F7FFF),
        brand2 = Color(0xFF0AB1A4),
        brand3 = Color(0xFF7CD458),
        brand4 = Color(0xFFFFC857),
        brand5 = Color(0xFF88C0D0),
        toggle = Color(0xFF7F7FFF),
        slider = Color(0xFF7F7FFF),
        heading = Color(0xFF7F7FFF),
        icon = Color(0xFFFFFFFF),
        infoText = Color(0xFF9E9E9E),
        popupBg = Color(0xFF1E1E1E),
        popupContent = Color(0xFFFFFFFF),
        wheelGradient = listOf(Color(0xFFFFE000), Color(0xFF7CD458), Color(0xFF0AB1A4)),
        shakeGradient = listOf(Color(0xFFFFE000), Color(0xFF7CD458), Color(0xFF0AB1A4))
    )
}

/**
 * SnoozelyTheme steuert alles über eine Theme-ID:
 *   "light" | "dark" | "amoled"
 *
 * dynamicColor bleibt als Parameter vorhanden (falls dein Code ihn noch setzt),
 * wird hier aber NICHT genutzt – die drei Themes sind bewusst fix definiert.
 */
@Composable
fun SnoozelyTheme(
    themeId: String = "light",
    dynamicColor: Boolean = false, // ignoriert
    content: @Composable () -> Unit
) {
    val spec = ThemeRegistry.byId(themeId) ?: ThemeRegistry.byId("light")!!

    // „Dunkel?“ – AMOLED/Dark = dunkel, Light = hell
    val dark = when (spec.id) {
        "dark", "amoled" -> true
        else -> false
    }

    // Material-ColorScheme aus der Registry
    val colorScheme = when {
        dark  -> spec.dark ?: darkColorScheme()
        else  -> spec.light ?: lightColorScheme()
    }

    // ---- ExtraColors je Theme definieren ----
    val baseBrand1 = Color(0xFF7F7FFF)
    val baseBrand2 = Color(0xFF0AB1A4)
    val baseBrand3 = Color(0xFF7CD458)
    val baseBrand4 = Color(0xFFFFC857)
    val baseBrand5 = Color(0xFF88C0D0)

    val extra = when (spec.id) {
        "light" -> ExtraColors(
            brand1 = baseBrand1,
            brand2 = baseBrand2,
            brand3 = baseBrand3,
            brand4 = baseBrand4,
            brand5 = baseBrand5,
            toggle = baseBrand1,
            slider = baseBrand1,
            heading = baseBrand1,
            icon = Color(0xFF101010),
            infoText = Color(0xFF6B6B6B),
            popupBg = Color(0xFFFFFFFF),
            popupContent = Color(0xFF111111),
            wheelGradient = listOf(Color(0xFFFFE000), baseBrand3, baseBrand2),
            shakeGradient = listOf(Color(0xFFFFE000), baseBrand3, baseBrand2)
        )

        "dark" -> ExtraColors(
            brand1 = baseBrand1,
            brand2 = baseBrand2,
            brand3 = baseBrand3,
            brand4 = baseBrand4,
            brand5 = baseBrand5,
            toggle = baseBrand1,
            slider = baseBrand1,
            heading = baseBrand1,
            icon = Color(0xFFE6E6E6),
            infoText = Color(0xFFBDBDBD),
            popupBg = Color(0xFF1E1E1E),
            popupContent = Color(0xFFEFEFEF),
            wheelGradient = listOf(Color(0xFFFFE000), baseBrand3, baseBrand2),
            shakeGradient = listOf(Color(0xFFFFE000), baseBrand3, baseBrand2)
        )

        // AMOLED
        else -> ExtraColors(
            brand1 = baseBrand1,
            brand2 = baseBrand2.copy(alpha = 0.95f),
            brand3 = baseBrand3,
            brand4 = baseBrand4,
            brand5 = baseBrand5,
            toggle = baseBrand1,
            slider = baseBrand1,
            heading = baseBrand1,
            icon = Color(0xFFE6E6E6),
            infoText = Color(0xFF9E9E9E),
            popupBg = Color(0xFF000000),
            popupContent = Color(0xFFEAEAEA),
            wheelGradient = listOf(Color(0xFFFFE000), baseBrand3, baseBrand2),
            shakeGradient = listOf(Color(0xFFFFE000), baseBrand3, baseBrand2)
        )
    }

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
