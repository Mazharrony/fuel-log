package com.fuelexpenselog.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.ui.theme.FuelTheme

/** A two-button confirmation. [danger] colours the confirm action for a delete or discard. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    dismiss: String = stringResource(R.string.action_cancel),
) {
    val colors = FuelTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) colors.danger else colors.textPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, color = colors.textPrimary) }
        },
    )
}

/** "Discard changes?" - shown on back only when a form is dirty. */
@Composable
fun DiscardDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.discard_title),
        body = stringResource(R.string.discard_body),
        confirm = stringResource(R.string.action_discard),
        dismiss = stringResource(R.string.action_keep_editing),
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
        danger = true,
    )
}

/** A save or delete that failed, in words. Rare: an unreadable row is disabled up front. */
@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.save_failed, message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok), color = FuelTheme.colors.textPrimary)
            }
        },
    )
}
