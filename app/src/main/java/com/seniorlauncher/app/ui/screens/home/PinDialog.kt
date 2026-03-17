package com.seniorlauncher.app.ui.screens.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.seniorlauncher.app.data.preferences.checkPin

@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    onCorrect: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submitPin() {
        if (checkPin(context, pin)) onCorrect() else error = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(8)
                    error = false
                },
                label = { Text("Introduce el PIN") },
                isError = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitPin() }),
                modifier = androidx.compose.ui.Modifier.focusRequester(focusRequester),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { submitPin() }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
