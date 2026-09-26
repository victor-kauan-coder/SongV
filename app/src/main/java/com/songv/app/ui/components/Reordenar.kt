package com.songv.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Reordenação por arraste numa LazyColumn. Enquanto o dedo arrasta, só a cópia local da lista
 * muda (resposta imediata, sem esperar o player ou o disco); ao soltar, [aoSoltar] recebe um
 * único movimento (de → para). As chaves dos itens na LazyColumn precisam ser [chave].
 */
@Stable
class EstadoReordenacao<T>(
    private val lista: LazyListState,
    private val chave: (T) -> Any,
    private val aoSoltar: (de: Int, para: Int) -> Unit,
) {
    var itens by mutableStateOf<List<T>>(emptyList())
        private set
    var arrastando by mutableStateOf<Any?>(null)
        private set
    var deslocamento by mutableFloatStateOf(0f)
        private set
    private var origem = -1

    fun sincronizar(novos: List<T>) {
        if (arrastando == null) itens = novos
    }

    fun iniciar(item: T) {
        arrastando = chave(item)
        origem = itens.indexOfFirst { chave(it) == chave(item) }
        deslocamento = 0f
    }

    fun arrastar(dy: Float) {
        val k = arrastando ?: return
        deslocamento += dy
        val visiveis = lista.layoutInfo.visibleItemsInfo
        val atual = visiveis.firstOrNull { it.key == k } ?: return
        val centro = atual.offset + deslocamento + atual.size / 2f
        val chavesDaLista = itens.map(chave).toSet()
        val alvo = visiveis.firstOrNull {
            it.key != k && it.key in chavesDaLista && centro >= it.offset && centro <= it.offset + it.size
        } ?: return
        val de = itens.indexOfFirst { chave(it) == k }
        val para = itens.indexOfFirst { chave(it) == alvo.key }
        if (de < 0 || para < 0) return
        itens = itens.toMutableList().apply { add(para, removeAt(de)) }
        // O item passa a ocupar o lugar do alvo: compensa para ele não "pular" sob o dedo.
        deslocamento += atual.offset - alvo.offset
    }

    fun terminar() {
        val k = arrastando ?: return
        val destino = itens.indexOfFirst { chave(it) == k }
        arrastando = null
        deslocamento = 0f
        if (origem >= 0 && destino >= 0 && origem != destino) aoSoltar(origem, destino)
        origem = -1
    }

    fun estaArrastando(item: T) = arrastando == chave(item)
}

@Composable
fun <T> rememberEstadoReordenacao(lista: LazyListState, chave: (T) -> Any, aoSoltar: (Int, Int) -> Unit) =
    remember(lista) { EstadoReordenacao(lista, chave, aoSoltar) }

/** Aplica o deslocamento visual e a elevação no item que está sendo arrastado. */
fun <T> Modifier.itemReordenavel(estado: EstadoReordenacao<T>, item: T): Modifier {
    val ativo = estado.estaArrastando(item)
    return this
        .zIndex(if (ativo) 1f else 0f)
        .graphicsLayer { translationY = if (ativo) estado.deslocamento else 0f }
        .then(if (ativo) Modifier.shadow(8.dp) else Modifier)
}

/** A alça (ícone de arrastar) que inicia a reordenação. */
fun <T> Modifier.alcaDeArraste(estado: EstadoReordenacao<T>, item: T): Modifier = pointerInput(item) {
    detectDragGestures(
        onDragStart = { estado.iniciar(item) },
        onDragEnd = { estado.terminar() },
        onDragCancel = { estado.terminar() },
    ) { mudanca, arraste ->
        mudanca.consume()
        estado.arrastar(arraste.y)
    }
}
