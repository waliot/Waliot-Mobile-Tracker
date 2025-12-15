package com.websmithing.gpstracker2.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.websmithing.gpstracker2.R

@Composable
fun PermissionDeniedDialog(
    modifier: Modifier = Modifier.Companion,
    onDismissRequest: () -> Unit,
    text: String
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {},
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            ) {
                Text(context.getString(R.string.permission_button_settings))
            }
        },
        modifier = modifier,
    )
}