package com.songv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica

/**
 * Mostra a faixa atual + o que vem a seguir na fila, permitindo reordenar por arrastar
 * (pressione e segure, depois arraste) e tocar diretamente numa faixa da fila.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaScreen(
    faixaAtual: Musica?,
    proximasNaFila: List<Musica>,
    indiceFilaAtual: Int,
    onVoltar: () -> Unit,
    onTocarIndice: (Int) -> Unit,
    onMover: (Int, Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fila de reprodução", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (faixaAtual != null) {
                Text(
                    "Tocando agora",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ItemFila(
                    musica = faixaAtual,
                    destacada = true,
                    arrastavel = false,
                    onClick = {},
                    onArrastar = { _, _ -> }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (proximasNaFila.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nada na fila depois dessa música.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                Text(
                    "A seguir",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(proximasNaFila.size, key = { proximasNaFila[it].id }) { indiceRelativo ->
                        val indiceAbsoluto = indiceFilaAtual + 1 + indiceRelativo
                        ItemFila(
                            musica = proximasNaFila[indiceRelativo],
                            destacada = false,
                            arrastavel = true,
                            onClick = { onTocarIndice(indiceAbsoluto) },
                            onArrastar = { deltaPosicoes, _ ->
                                val alvo = (indiceAbsoluto + deltaPosicoes).coerceIn(0, indiceFilaAtual + proximasNaFila.size)
                                if (alvo != indiceAbsoluto) onMover(indiceAbsoluto, alvo)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Item de fila com alça de arraste. Usa detectDragGesturesAfterLongPress para não conflitar
 * com o scroll vertical da lista — só inicia o arraste depois de segurar a alça brevemente.
 */
@Composable
private fun ItemFila(
    musica: Musica,
    destacada: Boolean,
    arrastavel: Boolean,
    onClick: () -> Unit,
    onArrastar: (deltaPosicoes: Int, deltaPx: Float) -> Unit
) {
    var deslocamentoAcumuladoPx by remember { mutableStateOf(0f) }
    val alturaItemPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = 0f } // reservado para animação de arraste futura
            .background(if (destacada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .clickable(enabled = !destacada, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapaThumb(musica = musica, tamanho = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                musica.titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (destacada) FontWeight.Bold else FontWeight.Normal,
                color = if (destacada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                musica.artista,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (arrastavel) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Arrastar para reordenar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                deslocamentoAcumuladoPx += dragAmount.y
                                if (kotlin.math.abs(deslocamentoAcumuladoPx) >= alturaItemPx) {
                                    val delta = (deslocamentoAcumuladoPx / alturaItemPx).toInt()
                                    onArrastar(delta, deslocamentoAcumuladoPx)
                                    deslocamentoAcumuladoPx -= delta * alturaItemPx
                                }
                            },
                            onDragEnd = { deslocamentoAcumuladoPx = 0f }
                        )
                    }
            )
        }
    }
}
