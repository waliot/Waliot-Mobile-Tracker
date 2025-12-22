package com.websmithing.gpstracker2.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websmithing.gpstracker2.ui.theme.WaliotTheme

@Composable
fun LabeledBox(
    modifier: Modifier = Modifier.Companion,
    label: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        content()
    }
}

@Preview
@Composable
private fun LabeledBoxPreview() {
    WaliotTheme {
        LabeledBox(label = "Label") {
            Text("Content")
        }
    }
}
