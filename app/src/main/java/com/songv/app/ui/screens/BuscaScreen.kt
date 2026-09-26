package com.songv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.data.normalizarBusca
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.Navegador
import com.songv.app.ui.Rota
import com.songv.app.ui.components.CabecalhoSecao
import com.songv.app.ui.components.EstadoVazio
import com.songv.app.ui.components.LinhaMusica
import com.songv.app.ui.components.MosaicoCapasPorId
import com.songv.app.ui.components.plural

@Composable
fun BuscaScreen(vm: PlayerViewModel, nav: Navegador, contentPadding: PaddingValues) {
    val bib by vm.biblioteca.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val rep by vm.reproducao.collectAsState()
    var termo by rememberSaveable { mutableStateOf("") }
    val foco = LocalFocusManager.current

    // Todas as palavras precisam aparecer, em qualquer ordem e sem acento: "legiao tempo" acha "Tempo Perdido — Legião Urbana".
    val palavras = remember(termo) { normalizarBusca(termo).split(' ').filter { it.isNotBlank() } }
    fun casa(texto: String) = palavras.all { texto.contains(it) }
    val faixas = remember(palavras, bib.musicas) { if (palavras.isEmpty()) emptyList() else bib.musicas.filter { casa(it.chaveBusca) } }
    val albuns = remember(palavras, bib.albuns) { if (palavras.isEmpty()) emptyList() else bib.albuns.filter { casa(normalizarBusca("${it.titulo} ${it.artista}")) } }
    val artistas = remember(palavras, bib.artistas) { if (palavras.isEmpty()) emptyList() else bib.artistas.filter { casa(normalizarBusca(it.nome)) } }
    val playlists = remember(palavras, prefs.playlists) { if (palavras.isEmpty()) emptyList() else prefs.playlists.filter { casa(normalizarBusca(it.nome)) } }

    fun registrar() { if (termo.isNotBlank()) vm.registrarBusca(termo) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp)) {
                Text("Buscar", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = termo,
                    onValueChange = { termo = it },
                    placeholder = { Text("Faixas, álbuns, artistas, playlists") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (termo.isNotEmpty()) {
                            IconButton(onClick = { termo = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Limpar busca") }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { registrar(); foco.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (palavras.isEmpty()) {
            if (prefs.buscasRecentes.isNotEmpty()) {
                item { CabecalhoSecao("Buscas recentes", acao = "Limpar", onAcao = { vm.limparBuscas() }) }
                items(prefs.buscasRecentes, key = { "busca_$it" }) { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable { termo = b }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(b, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (bib.albuns.isNotEmpty()) {
                item { CabecalhoSecao("Explorar álbuns") }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        bib.albuns.take(24).chunked(2).forEach { par ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                par.forEach { a -> CartaoAlbum(a, Modifier.weight(1f)) { nav.abrir(Rota.Album(a.chave)) } }
                                if (par.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            return@LazyColumn
        }

        if (faixas.isEmpty() && albuns.isEmpty() && artistas.isEmpty() && playlists.isEmpty()) {
            item {
                EstadoVazio(
                    icone = Icons.Rounded.SearchOff,
                    titulo = "Nada encontrado para “$termo”",
                    texto = "Confira a grafia ou tente só uma parte do nome. A busca ignora acentos e maiúsculas.",
                )
            }
            return@LazyColumn
        }

        if (artistas.isNotEmpty()) {
            item { CabecalhoSecao("Artistas") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artistas, key = { "a_${it.nome}" }) { a -> CartaoArtista(a) { registrar(); nav.abrir(Rota.Artista(a.nome)) } }
                }
            }
        }
        if (faixas.isNotEmpty()) {
            item { CabecalhoSecao("Faixas") }
            itemsIndexed(faixas.take(40), key = { _, m -> "f_${m.id}" }) { i, m ->
                LinhaMusica(
                    musica = m,
                    ativa = rep.atual?.id == m.id,
                    tocando = rep.tocando,
                    subtitulo = listOf(m.artista, m.album).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { registrar(); vm.tocar(faixas, i, origem = "Busca · $termo") },
                    onMenu = { nav.menuMusica = m },
                )
            }
            if (faixas.size > 40) {
                item {
                    TextButton(onClick = { registrar(); vm.tocar(faixas, origem = "Busca · $termo") }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Tocar todas as ${faixas.size} faixas encontradas")
                    }
                }
            }
        }
        if (albuns.isNotEmpty()) {
            item { CabecalhoSecao("Álbuns") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(albuns, key = { "al_${it.chave}" }) { a -> CartaoAlbum(a, Modifier.width(148.dp)) { registrar(); nav.abrir(Rota.Album(a.chave)) } }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item { CabecalhoSecao("Playlists") }
            items(playlists, key = { "p_${it.id}" }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { registrar(); nav.abrir(Rota.Playlist(p.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MosaicoCapasPorId(p.musicasIds, p.nome, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(p.nome, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(plural(p.musicasIds.size, "faixa"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

