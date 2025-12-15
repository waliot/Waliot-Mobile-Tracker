package com.websmithing.gpstracker2.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.websmithing.gpstracker2.R

@Composable
fun PermissionDialog(
    showDialog: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onConfirm) {
                    Text(stringResource(id = R.string.permission_button_grant))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.permission_button_deny))
                }
            },
            title = { Text(title) },
            text = { Text(message) }
        )
    }
}
