package com.tigonic.snoozely.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tigonic.snoozely.ui.theme.LocalExtraColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    cornerRadius: Dp = 2.dp,
    trackAlpha: Float = 0.12f,
    thumbAlpha: Float = 0.60f,
    lingerMs: Int = 800,   // Zeit nach Scroll-Ende
    fadeOutMs: Int = 500,  // Dauer des Ausblendens
    fadeInMs: Int = 50    // Dauer des Einblendens
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current

    val alphaAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var fadeJob by remember { mutableStateOf<Job?>(null) }

    // Reagiere robust auf Scroll-Änderungen (frame-genau)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                // Sofortiges (kurzes) Einblenden, laufendes Ausblenden abbrechen
                fadeJob?.cancel()
                if (alphaAnim.value < 1f) {
                    alphaAnim.animateTo(1f, tween(fadeInMs))
                }
            } else {
                // Nach Loslassen kurz sichtbar lassen, dann weich ausfaden
                fadeJob?.cancel()
                fadeJob = scope.launch {
                    delay(lingerMs.toLong())
                    // nur ausfaden, wenn immer noch nicht gescrolled wird
                    if (!scrollState.isScrollInProgress) {
                        alphaAnim.animateTo(0f, tween(fadeOutMs))
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.alpha(alphaAnim.value)) {
        val w = thickness.toPx()
        val h = size.height

        val total = h + scrollState.maxValue.toFloat()
        if (total <= 0f) return@Canvas

        val thumbHeight = max(24f, (h / total) * h)
        val range = (h - thumbHeight).coerceAtLeast(0f)
        val progress = if (scrollState.maxValue > 0)
            scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        else 0f
        val thumbTop = range * progress

        // Track (dezent)
        drawRoundRect(
            color = cs.onSurface.copy(alpha = trackAlpha),
            topLeft = Offset(x = size.width - w, y = 0f),
            size = Size(width = w, height = h),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )

        // Thumb (sichtbar, aber nicht zu kräftig)
        drawRoundRect(
            color = extra.infoText.copy(alpha = thumbAlpha),
            topLeft = Offset(x = size.width - w, y = thumbTop),
            size = Size(width = w, height = thumbHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }
}
