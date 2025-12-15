package com.websmithing.gpstracker2.ui.modifiers

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Wraps an [onClick] lambda with another one that supports debouncing. The default deboucing time
 * is 1000ms.
 *
 * @return debounced onClick
 */
@Composable
inline fun debounced(crossinline onClick: () -> Unit, debounceTime: Long = 1000L): () -> Unit {
    var lastTimeClicked by remember { mutableStateOf(0L) }
    val onClickLambda: () -> Unit = {
        val now = SystemClock.uptimeMillis()
        if (now - lastTimeClicked > debounceTime) {
            onClick()
        }
        lastTimeClicked = now
    }
    return onClickLambda
}

/**
 * The same as [Modifier.clickable] with support to debouncing.
 */
fun Modifier.debouncedClickable(
    debounceTime: Long = 1000L,
    onClick: () -> Unit
): Modifier {
    return this.composed {
        val clickable = debounced(debounceTime = debounceTime, onClick = { onClick() })
        this.clickable { clickable() }
    }
}