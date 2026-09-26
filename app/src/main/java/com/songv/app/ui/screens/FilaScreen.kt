package com.songv.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.songv.app.data.ModoTema
import com.songv.app.player.ItemFila
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.Navegador
import com.songv.app.ui.components.EstadoVazio
import com.songv.app.ui.components.LinhaMusica
import com.songv.app.ui.components.alcaDeArraste
import com.songv.app.ui.components.formatarDuracaoLonga
import com.songv.app.ui.components.itemReordenavel
import com.songv.app.ui.components.plural
import com.songv.app.ui.components.rememberEstadoReordenacao
import com.songv.app.ui.theme.LocalCoresSongV
import com.songv.app.ui.theme.Marca
import com.songv.app.ui.theme.SongVTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilaScreen(vm: PlayerViewModel, nav: Navegador, modifier: Modifier = Modifier) {
    // A fila abre por cima do player: mesmo palco escuro, em qualquer tema.
    SongVTheme(modo = ModoTema.ESCURO, destaque = LocalCoresSongV.current.sinal) {
        val rep by vm.reproducao.collectAsState()
        val atual = rep.fila.getOrNull(rep.posicao)
        val estadoLista = rememberLazyListState()
        val deslocamento = rep.posicao + 1
        val reordenacao = rememberEstadoReordenacao<ItemFila>(estadoLista, chave = { it.chave }) { de, para ->
            vm.moverNaFila(de + deslocamento, para + deslocamento)
        }
        reordenacao.sincronizar(rep.aSeguir)

        Surface(modifier.fillMaxSize(), color = Marca.Palco, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.filaAberta = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Fechar a fila", modifier = Modifier.size(30.dp))
                }
                Text("Fila", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (rep.aSeguir.isNotEmpty()) {
                    TextButton(onClick = vm::limparAseguir) { Text("Limpar a seguir") }
                }
            }
            LazyColumn(state = estadoLista, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (atual != null) {
                    item(key = "titulo_atual") { TituloGrupo("Tocando agora", rep.origem) }
                    item(key = "atual_${atual.chave}") {
                        LinhaMusica(musica = atual.musica, ativa = true, tocando = rep.tocando, onClick = vm::alternarPlayPause)
                    }
                }
                item(key = "titulo_seguir") {
                    val duracao = rep.aSeguir.sumOf { it.musica.duracaoMs }
                    TituloGrupo(
                        "A seguir",
                        if (rep.aSeguir.isEmpty()) null
                        else "${plural(rep.aSeguir.size, "faixa")} · ${formatarDuracaoLonga(duracao)}" + if (rep.aleatorio) " · em ordem aleatória" else "",
                    )
                }
                if (rep.aSeguir.isEmpty()) {
                    item(key = "vazio") {
                        EstadoVazio(
                            Icons.Rounded.QueuePlayNext,
                            "Nada depois desta faixa",
                            "Use “Tocar a seguir” ou “Adicionar à fila” no menu ⋮ de qualquer faixa.",
                        )
                    }
                }
                itemsIndexed(reordenacao.itens, key = { _, item -> item.chave }) { _, item ->
                    val deslizar = rememberSwipeToDismissBoxState(
                        confirmValueChange = { valor ->
                            if (valor == SwipeToDismissBoxValue.EndToStart) vm.removerDaFila(item)
                            valor == SwipeToDismissBoxValue.EndToStart
                        },
                    )
                    val arrastando = reordenacao.estaArrastando(item)
                    SwipeToDismissBox(
                        state = deslizar,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainerOr()).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Rounded.Delete, contentDescription = "Remover da fila") }
                        },
                        modifier = Modifier
                            .then(if (arrastando) Modifier else Modifier.animateItemPlacement())
                            .itemReordenavel(reordenacao, item),
                    ) {
                        LinhaMusica(
                            musica = item.musica,
                            onClick = { vm.tocarItem(item) },
                            modifier = Modifier.background(if (arrastando) MaterialTheme.colorScheme.surfaceContainerHigh else Marca.Palco),
                            acessorio = {
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    contentDescription = "Arrastar para reordenar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp).padding(12.dp).alcaDeArraste(reordenacao, item),
                                )
                            },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun TituloGrupo(titulo: String, detalhe: String?) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        if (detalhe != null) {
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun androidx.compose.material3.ColorScheme.errorContainerOr() = error.copy(alpha = 0.22f)
