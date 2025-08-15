package com.tigonic.snoozely.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tigonic.snoozely.ui.theme.ThemeColors.Amoled.infoText

// --------------------------- TYPOGRAPHY --------------------------------------
val Typography = Typography()

// --------------------------- EXTRA COLORS ------------------------------------
data class ExtraColors(
    // Basis-„Brand“-Farben
    val brand1: Color,   // Primär / Akzent
    val brand2: Color,   // Sekundär
    val brand3: Color,   // Tertiär / Erfolg
    val brand4: Color,   // Warnung
    val brand5: Color,   // Info

    // Rollen für die UI
    val toggle: Color,
    val slider: Color,
    val heading: Color,
    val icon: Color,
    val infoText: Color,
    val popupBg: Color,
    val popupContent: Color,

    // Gradients / Effekte
    val wheelGradient: List<Color>,
    val shakeGradient: List<Color>,
    val wheelTrack: Color,
    val divider: Color
)

val LocalExtraColors = staticCompositionLocalOf {
    // Fallback – wird durch SnoozelyTheme überschrieben
    ExtraColors(
        brand1 = Color(0xFF7F7FFF),
        brand2 = Color(0xFF0AB1A4),
        brand3 = Color(0xFF7CD458),
        brand4 = Color(0xFFFFC857),
        brand5 = Color(0xFF88C0D0),
        toggle = Color(0xFF7F7FFF),
        slider = Color(0xFF7F7FFF),
        heading = Color(0xFF7F7FFF),
        icon = Color(0xFFE6E6E6),
        infoText = Color(0xFFBDBDBD),
        popupBg = Color(0xFF1E1E1E),
        popupContent = Color(0xFFEFEFEF),
        wheelGradient = listOf(Color(0xFFFFE000), Color(0xFF7CD458), Color(0xFF0AB1A4)),
        shakeGradient = listOf(Color(0xFFFFE000), Color(0xFF7CD458), Color(0xFF0AB1A4)),
        wheelTrack    = Color(0xFF2A2F3A), // brauchbarer Default für Dark
        divider = Color(0xFF6A6CFF)
    )
}

// ------------------------- THEME SPECS / REGISTRY ----------------------------
data class ThemeSpec(
    val id: String,            // "light" | "dark" | "amoled"
    val label: String,         // Anzeigename
    val prefersDark: Boolean,  // steuert Fallbacks
    val light: ColorScheme?,   // optional vorgegebenes Light-Schema
    val dark: ColorScheme?,    // optional vorgegebenes Dark-Schema
    val extraLight: ExtraColors,
    val extraDark: ExtraColors
)

object ThemeRegistry {
    private val _themes = linkedMapOf<String, ThemeSpec>()
    val themes: List<ThemeSpec> get() = _themes.values.toList()
    fun byId(id: String): ThemeSpec? = _themes[id]
    fun register(spec: ThemeSpec) { _themes[spec.id] = spec }
    fun clear() = _themes.clear()
}

/**
 * Farbsets je Theme, angelehnt an die drei Referenzbilder:
 *
 *  - DARK   (Bild 1): tiefe Flächen, „electric blue“ & cyanische Akzente
 *  - LIGHT  (Bild 2): helle Flächen, violett-indigofarbene Akzente
 *  - AMOLED (Bild 3): echtes Schwarz, neon-magenta Akzente
 */
private object ThemeColors {

    // -------- DARK (Bild 1) --------
    object Dark {
        val primary    = Color(0xFF6A6CFF) // electric blue
        val secondary  = Color(0xFF00B3FF) // cyan
        val success    = Color(0xFF22C55E)
        val warning    = Color(0xFFF59E0B)
        val info       = Color(0xFF60A5FA)
        val icon = Color(0xFF6A6CFF)

        val background = Color(0xaa403838)
        val surface    = Color(0xFF151824)
        val onSurface  = Color(0xFFE6E8EF)

        // Verlauf: Electric Blue → Cyan → leichtes Türkis
        val wheelGradient = listOf(
            Color(0xFF1E3A8A), // tiefes Blau
            primary,           // Electric Blue
            secondary,         // Cyan
            Color(0xFF0AE7CC)  // Türkis
        )
        val shakeGradient = listOf(primary, secondary, Color(0xFF0EA5E9))
        val wheelTrack    = Color(0xFF2E2E38)
        val divider = Color(0xFF2E2E38)
        val infoText = Color(0xFFB0B0B0)
    }

    // -------- LIGHT (Bild 2) --------
    object Light {
        val primary    = Color(0xFF8B5CF6) // violet
        val secondary  = Color(0xFF6366F1) // indigo
        val success    = Color(0xFF16A34A)
        val warning    = Color(0xFFF59E0B)
        val info       = Color(0xFF0EA5E9)
        val icon = primary

        val background = Color(0xFFF6F6FC)
        val surface    = Color(0xFFFFFFFF)
        val onSurface  = Color(0xFF101112)

        // Verlauf: helles Violett → Primary → Indigo → sanftes Blau
        val wheelGradient = listOf(
            Color(0xFFE9D5FF), // sehr helles Violett
            primary,           // Violett
            secondary,         // Indigo
            Color(0xFF3B82F6)  // sanftes Blau
        )
        val shakeGradient = listOf(primary, secondary, Color(0xFFC084FC))
        val wheelTrack    = Color(0xFFCCCCCC)
        val divider = Color(0xFFDDDDDD)
        val infoText = Color(0xFF666666)
    }

    // -------- AMOLED (Bild 3) --------
    object Amoled {
        val primary    = Color(0xFFD946EF) // neon magenta
        val secondary  = Color(0xFF9333EA) // purple
        val success    = Color(0xFF22C55E)
        val warning    = Color(0xFFF97316)
        val info       = Color(0xFF67E8F9)
        val icon = primary

        val background = Color(0xFF000000)
        val surface    = Color(0xFF000000)
        val onSurface  = Color(0xFFEDEDED)

        // Verlauf: Neon Magenta → Primary → Purple → Neon Pink
        val wheelGradient = listOf(
            Color(0xFFFF00FF), // neon pink
            primary,           // neon magenta
            secondary,         // purple
            Color(0xFF7C3AED)  // tiefer Purple-Ton
        )
        val shakeGradient = listOf(primary, Color(0xFFFAFAFA), secondary)
        val wheelTrack    = Color(0xFF3A3A3A)
        val divider = Color(0xFF3A3A3A)
        val infoText = Color(0xFFB8B8B8)
    }
}

/** Einmalig beim App-Start aufrufen (z. B. in MainActivity.onCreate). */
fun registerDefaultThemes() {
    ThemeRegistry.clear()

    fun extrasFor(
        brand1: Color, brand2: Color, brand3: Color, brand4: Color, brand5: Color,
        iconOn: Color, popupBg: Color, popupContent: Color,
        wheel: List<Color>, shake: List<Color>, wheelTrack: Color,
        divider: Color, infoText: Color
    ) = ExtraColors(
        brand1 = brand1, brand2 = brand2, brand3 = brand3, brand4 = brand4, brand5 = brand5,
        toggle = brand1, slider = brand1, heading = brand1,
        icon = iconOn, infoText = infoText,
        popupBg = popupBg, popupContent = popupContent,
        wheelGradient = wheel, shakeGradient = shake, wheelTrack = wheelTrack,
        divider = divider
    )

    // LIGHT (Bild 2)
    ThemeRegistry.register(
        ThemeSpec(
            id = "light",
            label = "Light",
            prefersDark = false,
            light = lightColorScheme(
                primary     = ThemeColors.Light.primary,
                secondary   = ThemeColors.Light.secondary,
                tertiary    = ThemeColors.Light.success,   // „Erfolg“ als Tertiär
                background  = ThemeColors.Light.background,
                surface     = ThemeColors.Light.surface,
                onPrimary   = Color.White,
                onBackground= ThemeColors.Light.onSurface,
                onSurface   = ThemeColors.Light.onSurface,
            ),
            dark = null,
            extraLight = extrasFor(
                ThemeColors.Light.primary,
                ThemeColors.Light.secondary,
                ThemeColors.Light.success,
                ThemeColors.Light.warning,
                ThemeColors.Light.info,
                iconOn = ThemeColors.Light.icon,
                popupBg = ThemeColors.Light.surface,
                popupContent = ThemeColors.Light.onSurface,
                wheel = ThemeColors.Light.wheelGradient,
                shake = ThemeColors.Light.shakeGradient,
                wheelTrack = ThemeColors.Light.wheelTrack,
                divider = ThemeColors.Light.divider,
                infoText = ThemeColors.Light.infoText
            ),
            extraDark  = extrasFor( // Fallback, falls jemand light+dark kombiniert
                ThemeColors.Light.primary, ThemeColors.Light.secondary, ThemeColors.Light.success,
                ThemeColors.Light.warning, ThemeColors.Light.info,
                iconOn = ThemeColors.Light.icon, popupBg = Color(0xFF151515), popupContent = Color(0xFFEFEFEF),
                wheel = ThemeColors.Light.wheelGradient, shake = ThemeColors.Light.shakeGradient,  wheelTrack = ThemeColors.Light.wheelTrack,
                divider = ThemeColors.Light.divider,
                infoText = ThemeColors.Light.infoText
            )
        )
    )

    // DARK (Bild 1)
    ThemeRegistry.register(
        ThemeSpec(
            id = "dark",
            label = "Dark",
            prefersDark = true,
            light = null,
            dark = darkColorScheme(
                primary     = ThemeColors.Dark.primary,
                secondary   = ThemeColors.Dark.secondary,
                tertiary    = ThemeColors.Dark.success,
                background  = ThemeColors.Dark.background,
                surface     = ThemeColors.Dark.surface,
                onPrimary   = Color.White,
                onBackground= ThemeColors.Dark.onSurface,
                onSurface   = ThemeColors.Dark.onSurface,
            ),
            extraLight = extrasFor( // Fallback (z. B. in Dialogen)
                ThemeColors.Dark.primary, ThemeColors.Dark.secondary, ThemeColors.Dark.success,
                ThemeColors.Dark.warning, ThemeColors.Dark.info,
                iconOn = ThemeColors.Dark.icon, popupBg = Color(0xFFFFFFFF), popupContent = Color(0xFF101112),
                wheel = ThemeColors.Dark.wheelGradient, shake = ThemeColors.Dark.shakeGradient, wheelTrack = ThemeColors.Dark.wheelTrack,
                divider = ThemeColors.Dark.divider,
                infoText = ThemeColors.Dark.infoText
            ),
            extraDark  = extrasFor(
                ThemeColors.Dark.primary, ThemeColors.Dark.secondary, ThemeColors.Dark.success,
                ThemeColors.Dark.warning, ThemeColors.Dark.info,
                iconOn = ThemeColors.Dark.icon, popupBg = ThemeColors.Dark.surface,
                popupContent = ThemeColors.Dark.onSurface,
                wheel = ThemeColors.Dark.wheelGradient, shake = ThemeColors.Dark.shakeGradient, wheelTrack = ThemeColors.Dark.wheelTrack,
                divider = ThemeColors.Dark.divider,
                infoText = ThemeColors.Dark.infoText
            )
        )
    )

    // AMOLED (Bild 3)
    ThemeRegistry.register(
        ThemeSpec(
            id = "amoled",
            label = "AMOLED",
            prefersDark = true,
            light = null,
            dark = darkColorScheme(
                primary     = ThemeColors.Amoled.primary,
                secondary   = ThemeColors.Amoled.secondary,
                tertiary    = ThemeColors.Amoled.success,
                background  = ThemeColors.Amoled.background,
                surface     = ThemeColors.Amoled.surface,
                onPrimary   = Color.White,
                onBackground= ThemeColors.Amoled.onSurface,
                onSurface   = ThemeColors.Amoled.onSurface,
            ),
            extraLight = extrasFor( // nur als Fallback
                ThemeColors.Amoled.primary, ThemeColors.Amoled.secondary, ThemeColors.Amoled.success,
                ThemeColors.Amoled.warning, ThemeColors.Amoled.info,
                iconOn = ThemeColors.Amoled.icon, popupBg = Color(0xFFFFFFFF), popupContent = Color(0xFF101112),
                wheel = ThemeColors.Amoled.wheelGradient, shake = ThemeColors.Amoled.shakeGradient, wheelTrack = ThemeColors.Light.wheelTrack,
                divider = ThemeColors.Amoled.divider,
                infoText = ThemeColors.Amoled.infoText
            ),
            extraDark  = extrasFor(
                ThemeColors.Amoled.primary, ThemeColors.Amoled.secondary, ThemeColors.Amoled.success,
                ThemeColors.Amoled.warning, ThemeColors.Amoled.info,
                iconOn = ThemeColors.Amoled.icon, popupBg = ThemeColors.Amoled.surface,
                popupContent = ThemeColors.Amoled.onSurface,
                wheel = ThemeColors.Amoled.wheelGradient, shake = ThemeColors.Amoled.shakeGradient, wheelTrack = ThemeColors.Amoled.wheelTrack,
                divider = ThemeColors.Amoled.divider,
                infoText = ThemeColors.Amoled.infoText
            )
        )
    )
}

// --------------------------- COMPOSABLE THEME --------------------------------
@Composable
fun SnoozelyTheme(
    themeId: String = "system",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedId = when (themeId) {
        "system" -> if (isSystemInDarkTheme()) "dark" else "light"
        else -> themeId
    }

    val spec = ThemeRegistry.byId(resolvedId) ?: ThemeRegistry.byId("light")!!

    val useDark = spec.id == "dark" || spec.id == "amoled"
    val colorScheme = when {
        useDark -> spec.dark ?: darkColorScheme()
        else    -> spec.light ?: lightColorScheme()
    }
    val extras = if (useDark) spec.extraDark else spec.extraLight

    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
