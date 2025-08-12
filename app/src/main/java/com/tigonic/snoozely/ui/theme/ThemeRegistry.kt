package com.tigonic.snoozely.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Beschreibt ein Theme mit ID (Persistenz), Label (UI) und optionalen ColorSchemes.
 */
data class ThemeSpec(
    val id: String,                 // "light" | "dark" | "amoled"
    val label: String,              // Anzeigename im Dropdown
    val light: ColorScheme? = null, // eigenes Light-Schema (für "light")
    val dark: ColorScheme? = null,  // eigenes Dark-Schema (für "dark"/"amoled")
    val prefersDark: Boolean = false
)

object ThemeRegistry {
    private val _themes = linkedMapOf<String, ThemeSpec>()
    val themes: List<ThemeSpec> get() = _themes.values.toList()

    fun register(spec: ThemeSpec) {
        _themes[spec.id] = spec
    }

    fun byId(id: String): ThemeSpec? = _themes[id]
}

/**
 * Registriert exakt drei Themes:
 *  - "light"  : helles Material3 Schema
 *  - "dark"   : dunkles Material3 Schema
 *  - "amoled" : dunkles Schema mit echtem Schwarz
 */
fun registerDefaultThemes() {
    // Gemeinsame Brand-Farben (konsistent in allen drei Varianten)
    val brandPrimary   = Color(0xFF7F7FFF) // Violett
    val brandSecondary = Color(0xFF0AB1A4) // Türkis
    val brandTertiary  = Color(0xFFFFC857) // Gelb/Orange

    // LIGHT
    ThemeRegistry.register(
        ThemeSpec(
            id = "light",
            label = "Light",
            light = lightColorScheme(
                primary = brandPrimary,
                secondary = brandSecondary,
                tertiary = brandTertiary,
                background = Color(0xFFF7F7FA),
                surface = Color(0xFFFFFFFF),
                onPrimary = Color.White,
                onBackground = Color(0xFF121212),
                onSurface = Color(0xFF121212),
            ),
            prefersDark = false
        )
    )

    // DARK
    ThemeRegistry.register(
        ThemeSpec(
            id = "dark",
            label = "Dark",
            dark = darkColorScheme(
                primary = brandPrimary,
                secondary = brandSecondary,
                tertiary = brandTertiary,
                background = Color(0xFF101010),
                surface = Color(0xFF151515),
                onPrimary = Color.White,
                onBackground = Color(0xFFE6E6E6),
                onSurface = Color(0xFFE6E6E6),
            ),
            prefersDark = true
        )
    )

    // AMOLED (echtes Schwarz)
    ThemeRegistry.register(
        ThemeSpec(
            id = "amoled",
            label = "AMOLED",
            dark = darkColorScheme(
                primary = brandPrimary,
                secondary = brandSecondary.copy(alpha = 0.95f),
                tertiary = brandTertiary,
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                onPrimary = Color.White,
                onBackground = Color(0xFFE6E6E6),
                onSurface = Color(0xFFE6E6E6),
            ),
            prefersDark = true
        )
    )
}
