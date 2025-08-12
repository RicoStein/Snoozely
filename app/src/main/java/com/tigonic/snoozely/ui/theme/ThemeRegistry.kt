package com.tigonic.snoozely.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Beschreibt ein Theme mit ID (Persistenz), Label (UI) und optionalen ColorSchemes.
 * Wenn light/dark null sind, wird auf Dynamic oder Standard zurückgefallen.
 */
data class ThemeSpec(
    val id: String,                 // z.B. "system", "light", "dark", "amoled", "nord"
    val label: String,              // Anzeigename im Dropdown
    val light: ColorScheme? = null, // optional eigenes Light-Schema
    val dark: ColorScheme? = null,  // optional eigenes Dark-Schema
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

/** Standard-Themes + Beispiel-Themes registrieren. Einmalig bei App-Start aufrufen. */
fun registerDefaultThemes() {
    // System (Dynamic Colors, wenn verfügbar)
    ThemeRegistry.register(ThemeSpec(id = "system", label = "System"))

    // Klassisch Hell/Dunkel
    ThemeRegistry.register(ThemeSpec(id = "light", label = "Hell", light = lightColorScheme()))
    ThemeRegistry.register(ThemeSpec(id = "dark", label = "Dunkel", dark = darkColorScheme(), prefersDark = true))

    // Beispiel 1: AMOLED (echtes Schwarz)
    ThemeRegistry.register(
        ThemeSpec(
            id = "amoled",
            label = "AMOLED",
            dark = darkColorScheme(
                primary = Color(0xFF7F7FFF),
                secondary = Color(0xFF9696FF),
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                onPrimary = Color.White,
                onBackground = Color(0xFFE6E6E6),
                onSurface = Color(0xFFE6E6E6),
            ),
            prefersDark = true
        )
    )

    // Beispiel 2: Nord
    ThemeRegistry.register(
        ThemeSpec(
            id = "nord",
            label = "Nord",
            light = lightColorScheme(
                primary = Color(0xFF5E81AC),
                secondary = Color(0xFF88C0D0),
                background = Color(0xFFE5E9F0),
                surface = Color(0xFFECEFF4),
                onPrimary = Color.White,
                onBackground = Color(0xFF2E3440),
                onSurface = Color(0xFF2E3440),
            ),
            dark = darkColorScheme(
                primary = Color(0xFF88C0D0),
                secondary = Color(0xFF81A1C1),
                background = Color(0xFF2E3440),
                surface = Color(0xFF3B4252),
                onPrimary = Color(0xFF2E3440),
                onBackground = Color(0xFFECEFF4),
                onSurface = Color(0xFFECEFF4),
            )
        )
    )
}
