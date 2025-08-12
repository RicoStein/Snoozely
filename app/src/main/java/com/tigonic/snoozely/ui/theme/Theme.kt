package com.tigonic.snoozely.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ====== Typography & Default ColorSchemes (nur als Fallback) ======
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

private val DefaultDark = darkColorScheme()
private val DefaultLight = lightColorScheme()

/**
 * Neues API: SnoozelyTheme per themeId + dynamicColor (statt enum).
 * themeId wird in DataStore persistiert und über ThemeRegistry aufgelöst.
 */
@Composable
fun SnoozelyTheme(
    themeId: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val spec = ThemeRegistry.byId(themeId) ?: ThemeRegistry.byId("system")!!

    // „Dunkel?“: system/dark/amoled etc. erzwingen dunkel; andere hell
    val dark = when (spec.id) {
        "system" -> isSystemInDarkTheme()
        "dark", "amoled" -> true
        else -> false
    }

    val ctx = LocalContext.current
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && spec.id in listOf("system", "light", "dark")) {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
            when {
                dark  -> spec.dark ?: DefaultDark
                else  -> spec.light ?: DefaultLight
            }
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
