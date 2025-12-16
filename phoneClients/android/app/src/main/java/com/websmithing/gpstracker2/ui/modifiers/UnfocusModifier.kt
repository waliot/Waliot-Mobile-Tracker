package com.websmithing.gpstracker2.ui.modifiers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput

@Stable
fun Modifier.unfocus(focusManager: FocusManager) =
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                focusManager.clearFocus()
            }
        )
    }
