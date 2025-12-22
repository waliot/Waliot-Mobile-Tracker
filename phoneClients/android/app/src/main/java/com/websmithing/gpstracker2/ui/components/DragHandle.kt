package com.websmithing.gpstracker2.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.R
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.ui.theme.WaliotTheme

@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 4.dp,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = Color(0x804B5A66),
) {
    val context = LocalContext.current
    val dragHandleDescription = context.getString(R.string.m3c_bottom_sheet_drag_handle_description)
    Surface(
        modifier =
            modifier
                .padding(vertical = 10.dp)
                .semantics {
                    contentDescription = dragHandleDescription
                },
        color = color,
        shape = shape,
    ) {
        Box(Modifier.size(width = width, height = height))
    }
}

@Preview
@Composable
private fun TrackingInfoSheetPreview() {
    WaliotTheme {
        DragHandle()
    }
}
