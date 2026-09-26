package com.songv.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Diálogo simples para nomear e criar uma nova playlist. */
@Composable
fun CriarPlaylistDialog(
    onConfirmar: (String) -> Unit,
    onFechar: () -> Unit
) {
    var nome by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Nova playlist") },
        text = {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                placeholder = { Text("Nome da playlist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (nome.isNotBlank()) { onConfirmar(nome); onFechar() } }) {
                Text("Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text("Cancelar") }
        }
    )
}
