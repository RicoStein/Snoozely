package com.tigonic.snoozely.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --------------------------- TYPO --------------------------------------------
val Typography = androidx.compose.material3.Typography()

// --------------------------- EXTRA COLORS ------------------------------------
// Deutlich unterscheidbare aktive/inaktive Farben
data class ExtraColors(
    // Neue, kontrastreiche Felder
    val toggleActive: Color,
    val toggleInactive: Color,
    val sliderActive: Color,
    val sliderInactiveTrack: Color,

    val iconActive: Color,
    val iconInactive: Color,
    val heading: Color,
    val infoText: Color,

    val popupBg: Color,
    val popupContent: Color,

    val wheelGradient: List<Color>,
    val shakeGradient: List<Color>,
    val wheelTrack: Color,
    val divider: Color,

    // NEU: Menüfarbe (Light = schwarz, Dark = weiß)
    val menu: Color
) {
    // Rückwärtskompatible Aliase (alte Bezeichner weiter verwendbar)
    val toggle: Color get() = toggleActive
    val slider: Color get() = sliderActive
    val icon: Color get() = iconActive
}

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(
        toggleActive = Color(0xFF1565C0),
        toggleInactive = Color(0xFFBDBDBD),
        sliderActive = Color(0xFF1565C0),
        sliderInactiveTrack = Color(0xFFE0E0E0),
        iconActive = Color(0xFF0D47A1),
        iconInactive = Color(0xFF9E9E9E),
        heading = Color(0xFF0D47A1),
        infoText = Color(0xFF616161),
        popupBg = Color.White,
        popupContent = Color(0xFF111111),
        wheelGradient = listOf(Color(0xFF90CAF9), Color(0xFF1565C0), Color(0xFF42A5F5)),
        shakeGradient = listOf(Color(0xFF1565C0), Color(0xFF4FC3F7), Color(0xFF03DAC6)),
        wheelTrack = Color(0xFFE0E0E0),
        divider = Color(0xFFE6E6E6),
        menu = Color.Black // Default (wird im Theme überschrieben)
    )
}

// ------------------------- THEME SPECS / REGISTRY ----------------------------
data class ThemeSpec(
    val id: String, // "light" | "dark"
    val label: String,
    val prefersDark: Boolean,
    val light: androidx.compose.material3.ColorScheme?,
    val dark: androidx.compose.material3.ColorScheme?,
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

// --------------------------- COLOR PRESETS -----------------------------------
private object ThemeColors {

    // LIGHT (klassisch, hohe Unterscheidbarkeit aktiv/inaktiv)
    object Light {
        // Einheitlicher Akzent – kräftiges Android-Blau
        val primary = Color(0xFF1565C0)
        val secondary = Color(0xFF03DAC6)
        val tertiary = Color(0xFF2E7D32)

        // Flächen
        val background = Color(0xFFF7F7F7) // sehr helles Grau
        val surface = Color(0xFFFFFFFF) // weiß
        val outline = Color(0xFFBDBDBD)

        // Texte/Icons
        val onPrimary = Color.White
        val onBg = Color(0xFF111111)
        val onSurf = Color(0xFF111111)
        val infoText = Color(0xFF5F6368)

        // Komponentenfarben (starke Kontraste)
        val toggleActive = primary
        val toggleInactive = Color(0xFF9E9E9E) // deutlich grauer
        val sliderActive = primary
        val sliderInactiveTrack = Color(0xFFDDDDDD)

        val iconActive = Color(0xFF0D47A1) // dunkleres Blau
        val iconInactive = Color(0xFF9E9E9E)

        val heading = Color(0xFF0D47A1)

        val wheelGradient = listOf(
            Color(0xFFB3D9FF), // heller Start
            Color(0xFF4A90E2), // leuchtendes Blau
            Color(0xFF1565C0)  // Primärblau
        )
        val shakeGradient = listOf(primary, Color(0xFF4FC3F7), secondary)
        val wheelTrack = Color(0xFFEBEEF3) // heller als zuvor
        val divider = Color(0xFFDFE1E5) // deutlich sichtbar

        // NEU: Menü-Farbe
        val menu = Color.Black
    }

    // DARK (dunkelgrau, weiße Schrift, aktive in exakt dem gleichen Blau wie Light)
    object Dark {
        // Gleicher aktiver Blauton wie Light
        val primary = Color(0xFF1565C0)
        val secondary = Color(0xFF03DAC6)
        val tertiary = Color(0xFF81C784)

        val background = Color(0xFF000000)
        val surface = Color(0xFF222222)
        val outline = Color(0xFF3A3A3A)

        val onPrimary = Color.White
        val onBg = Color(0xFFF2F2F2)
        val onSurf = Color(0xFFF2F2F2)
        val infoText = Color(0xFFBDBDBD)

        val toggleActive = primary
        val toggleInactive = Color(0xFF616161)
        val sliderActive = primary
        val sliderInactiveTrack = Color(0xFF2C2C2C)

        val iconActive = primary
        val iconInactive = Color(0xFF777777)

        val heading = Color(0xFFFFFFFF)

        // Wheel: heller und leuchtender
        val wheelGradient = listOf(
            Color(0xFF445166), // heller Start, bläulicher
            Color(0xFF5BA8FF), // helleres Blau
            Color(0xFF2778D1)  // kräftiges Mittelblau
        )
        val wheelTrack = Color(0xFF3A3F46) // etwas heller

        val shakeGradient = listOf(primary, Color(0xFF4DD0E1), secondary)
        val divider = Color(0xFF2A2A2A)
        // NEU: Menü-Farbe
        val menu = Color.White
    }
}

// --------------------------- REGISTER THEMES ----------------------------------
fun registerDefaultThemes() {
    ThemeRegistry.clear()

    fun extrasOf(
        toggleActive: Color, toggleInactive: Color,
        sliderActive: Color, sliderInactiveTrack: Color,
        iconActive: Color, iconInactive: Color,
        heading: Color, infoText: Color,
        popupBg: Color, popupContent: Color,
        wheel: List<Color>, shake: List<Color>,
        wheelTrack: Color, divider: Color,
        menu: Color
    ) = ExtraColors(
        toggleActive = toggleActive,
        toggleInactive = toggleInactive,
        sliderActive = sliderActive,
        sliderInactiveTrack = sliderInactiveTrack,
        iconActive = iconActive,
        iconInactive = iconInactive,
        heading = heading,
        infoText = infoText,
        popupBg = popupBg,
        popupContent = popupContent,
        wheelGradient = wheel,
        shakeGradient = shake,
        wheelTrack = wheelTrack,
        divider = divider,
        menu = menu
    )

    // LIGHT
    ThemeRegistry.register(
        ThemeSpec(
            id = "light",
            label = "Light",
            prefersDark = false,
            light = lightColorScheme(
                primary      = ThemeColors.Light.primary,
                secondary    = ThemeColors.Light.secondary,
                tertiary     = ThemeColors.Light.tertiary,
                background   = ThemeColors.Light.background,
                surface      = ThemeColors.Light.surface,
                outline      = ThemeColors.Light.outline,
                onPrimary    = ThemeColors.Light.onPrimary,
                onBackground = ThemeColors.Light.onBg,
                onSurface    = ThemeColors.Light.onSurf
            ),
            dark = null,
            extraLight = extrasOf(
                toggleActive = ThemeColors.Light.toggleActive,
                toggleInactive = ThemeColors.Light.toggleInactive,
                sliderActive = ThemeColors.Light.sliderActive,
                sliderInactiveTrack = ThemeColors.Light.sliderInactiveTrack,
                iconActive = ThemeColors.Light.iconActive,
                iconInactive = ThemeColors.Light.iconInactive,
                heading = ThemeColors.Light.heading,
                infoText = ThemeColors.Light.infoText,
                popupBg = ThemeColors.Light.surface,
                popupContent = ThemeColors.Light.onSurf,
                wheel = ThemeColors.Light.wheelGradient,
                shake = ThemeColors.Light.shakeGradient,
                wheelTrack = ThemeColors.Light.wheelTrack,
                divider = ThemeColors.Light.divider,
                menu = ThemeColors.Light.menu
            ),
            extraDark = extrasOf( // Fallback
                toggleActive = ThemeColors.Light.toggleActive,
                toggleInactive = ThemeColors.Light.toggleInactive,
                sliderActive = ThemeColors.Light.sliderActive,
                sliderInactiveTrack = ThemeColors.Light.sliderInactiveTrack,
                iconActive = ThemeColors.Light.iconActive,
                iconInactive = ThemeColors.Light.iconInactive,
                heading = ThemeColors.Light.heading,
                infoText = ThemeColors.Light.infoText,
                popupBg = Color.White,
                popupContent = Color(0xFF111111),
                wheel = ThemeColors.Light.wheelGradient,
                shake = ThemeColors.Light.shakeGradient,
                wheelTrack = ThemeColors.Light.wheelTrack,
                divider = ThemeColors.Light.divider,
                menu = ThemeColors.Light.menu
            )
        )
    )

    // DARK (gleiche aktive/inaktive Logik wie Light)
    ThemeRegistry.register(
        ThemeSpec(
            id = "dark",
            label = "Dark",
            prefersDark = true,
            light = null,
            dark = darkColorScheme(
                primary      = ThemeColors.Dark.primary,
                secondary    = ThemeColors.Dark.secondary,
                tertiary     = ThemeColors.Dark.tertiary,
                background   = ThemeColors.Dark.background,
                surface      = ThemeColors.Dark.surface,
                outline      = ThemeColors.Dark.outline,
                onPrimary    = ThemeColors.Dark.onPrimary,
                onBackground = ThemeColors.Dark.onBg,
                onSurface    = ThemeColors.Dark.onSurf
            ),
            extraLight = extrasOf( // Fallback
                toggleActive = ThemeColors.Dark.toggleActive,
                toggleInactive = ThemeColors.Dark.toggleInactive,
                sliderActive = ThemeColors.Dark.sliderActive,
                sliderInactiveTrack = ThemeColors.Dark.sliderInactiveTrack,
                iconActive = ThemeColors.Dark.iconActive,
                iconInactive = ThemeColors.Dark.iconInactive,
                heading = ThemeColors.Dark.heading,
                infoText = ThemeColors.Dark.infoText,
                popupBg = Color.White,
                popupContent = Color(0xFF111111),
                wheel = ThemeColors.Dark.wheelGradient,
                shake = ThemeColors.Dark.shakeGradient,
                wheelTrack = ThemeColors.Dark.wheelTrack,
                divider = ThemeColors.Dark.divider,
                menu = ThemeColors.Dark.menu
            ),
            extraDark = extrasOf(
                toggleActive = ThemeColors.Dark.toggleActive,
                toggleInactive = ThemeColors.Dark.toggleInactive,
                sliderActive = ThemeColors.Dark.sliderActive,
                sliderInactiveTrack = ThemeColors.Dark.sliderInactiveTrack,
                iconActive = ThemeColors.Dark.iconActive,
                iconInactive = ThemeColors.Dark.iconInactive,
                heading = ThemeColors.Dark.heading,
                infoText = ThemeColors.Dark.infoText,
                popupBg = ThemeColors.Dark.surface,
                popupContent = ThemeColors.Dark.onSurf,
                wheel = ThemeColors.Dark.wheelGradient,
                shake = ThemeColors.Dark.shakeGradient,
                wheelTrack = ThemeColors.Dark.wheelTrack,
                divider = ThemeColors.Dark.divider,
                menu = ThemeColors.Dark.menu
            )
        )
    )
}

// --------------------------- COMPOSABLE THEME --------------------------------
@Composable
fun SnoozelyTheme(
    themeId: String = "system",
    dynamicColor: Boolean = false, // Dieser Parameter wird aktuell nicht verwendet, bleibt für Zukunft
    content: @Composable () -> Unit
) {
    val resolvedId = when (themeId) {
        "system" -> if (isSystemInDarkTheme()) "dark" else "light"
        else -> themeId
    }
    val spec = ThemeRegistry.byId(resolvedId) ?: ThemeRegistry.byId("light")!!

    val useDark = spec.id == "dark"
    val colorScheme = if (useDark) (spec.dark ?: darkColorScheme()) else (spec.light ?: lightColorScheme())
    val extras = if (useDark) spec.extraDark else spec.extraLight

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // 1. Systemleisten transparent machen für Edge-to-Edge
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            // 2. Icons der Systemleisten (Status, Navigation) an das App-Theme anpassen
            //    isAppearanceLightStatusBars = true -> Icons werden dunkel (für helles App-Theme)
            //    isAppearanceLightStatusBars = false -> Icons werden hell (für dunkles App-Theme)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
