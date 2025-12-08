package com.websmithing.gpstracker2.ui.components.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.ui.theme.SliderThumb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Provides a draggable vertical slider used to close a menu panel with a smooth animation.
 *
 * @param offsetPx Current vertical offset controlled by animation
 * @param maxOffsetPx Maximum allowed drag offset before closing is triggered
 * @param scope Coroutine scope for launching animations
 * @param onClose Callback executed when the slider indicates closing
 */
@Composable
fun MenuSlider(
    offsetPx: Animatable<Float, *>,
    maxOffsetPx: Float,
    scope: CoroutineScope,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmountPx ->
                        val newOffset = (offsetPx.value + dragAmountPx).coerceIn(0f, maxOffsetPx)
                        scope.launch { offsetPx.snapTo(newOffset) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetPx.value > maxOffsetPx * 0.35f) {
                                offsetPx.animateTo(maxOffsetPx, tween(250))
                                onClose()
                            } else {
                                offsetPx.animateTo(0f, tween(250))
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(4.dp)
                .background(SliderThumb, shape = RoundedCornerShape(2.dp))
        )
    }
}
