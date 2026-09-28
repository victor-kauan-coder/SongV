package com.songv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.songv.app.data.Ordenacao
import com.songv.app.model.Artista
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.FiltroBiblioteca
import com.songv.app.ui.Navegador
import com.songv.app.ui.Rota
import com.songv.app.ui.components.BotaoPilula
import com.songv.app.ui.components.CapaArte
import com.songv.app.ui.components.DialogoNome
import com.songv.app.ui.components.EstadoVazio
import com.songv.app.ui.components.LinhaMusica
import com.songv.app.ui.components.MosaicoCapasPorId
import com.songv.app.ui.components.formatarDuracaoLonga
import com.songv.app.ui.components.plural
import com.songv.app.ui.theme.LocalCoresSongV

@Composable
fun BibliotecaScreen(vm: PlayerViewModel, nav: Navegador, contentPadding: PaddingValues) {
    val bib by vm.biblioteca.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val rep by vm.reproducao.collectAsState()
    val pc by vm.bibliotecaComputador.collectAsState()
    var criando by rememberSaveable { mutableStateOf(false) }

    val cabecalho: @Composable (Dp) -> Unit = { margem ->
        Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
            Row(Modifier.padding(start = margem, end = (margem - 12.dp).coerceAtLeast(0.dp)), verticalAlignment = Alignment.CenterVertically) {
                Text("Biblioteca", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                when (nav.filtro) {
                    FiltroBiblioteca.MUSICAS, FiltroBiblioteca.COMPUTADOR -> MenuOrdenacao(prefs.ordenacao, vm::definirOrdenacao)
                    FiltroBiblioteca.PLAYLISTS -> IconButton(onClick = { criando = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Nova playlist")
                    }
                    else -> Unit
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = margem),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            ) {
                // O computador só aparece conectado (ou enquanto a aba dele está aberta).
                items(FiltroBiblioteca.entries.filter { it != FiltroBiblioteca.COMPUTADOR || pc != null || nav.filtro == it }) { f ->
                    FilterChip(
                        selected = nav.filtro == f,
                        onClick = { nav.filtro = f },
                        label = { Text(if (f == FiltroBiblioteca.COMPUTADOR) pc?.nome ?: f.rotulo else f.rotulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (f == FiltroBiblioteca.COMPUTADOR) {
                            { Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LocalCoresSongV.current.sinal,
                            selectedLabelColor = LocalCoresSongV.current.noSinal,
                        ),
                        border = null,
                    )
                }
            }
        }
    }

    when (nav.filtro) {
        FiltroBiblioteca.MUSICAS -> {
            val lista = remember(bib.musicas, prefs.ordenacao) { vm.ordenar(bib.musicas, prefs.ordenacao) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                item { cabecalho(16.dp) }
                if (lista.isEmpty()) {
                    item { EstadoVazio(Icons.Rounded.LibraryMusic, "Nenhuma faixa ainda", "As músicas da pasta escolhida aparecem aqui. Ajuste a pasta em Configurações.") }
                } else {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${plural(lista.size, "faixa")} · ${formatarDuracaoLonga(lista.sumOf { it.duracaoMs })}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            BotaoPilula("Aleatório", { vm.tocar(lista, origem = "Biblioteca", aleatorio = true) }, icone = Icons.Rounded.Shuffle)
                        }
                    }
                    itemsIndexed(lista, key = { _, m -> m.id }) { i, m ->
                        LinhaMusica(
                            musica = m,
                            ativa = rep.atual?.id == m.id,
                            tocando = rep.tocando,
                            subtitulo = if (prefs.ordenacao == Ordenacao.ALBUM && m.album.isNotBlank()) "${m.album} · ${m.artista}" else m.artista,
                            onClick = { vm.tocar(lista, i, origem = "Biblioteca") },
                            onMenu = { nav.menuMusica = m },
                        )
                    }
                }
            }
        }

        FiltroBiblioteca.COMPUTADOR -> {
            val atual = pc
            val lista = remember(atual, prefs.ordenacao) { atual?.let { vm.ordenar(it.musicas, prefs.ordenacao) }.orEmpty() }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                item { cabecalho(16.dp) }
                when {
                    atual == null -> item {
                        EstadoVazio(Icons.Rounded.Computer, "Computador desconectado", "Conecte em Configurações › Computador para navegar e tocar as músicas dele aqui.")
                    }
                    lista.isEmpty() -> item {
                        EstadoVazio(Icons.Rounded.Computer, "Nada no ${atual.nome}", "O SongV do computador não encontrou músicas na pasta configurada.")
                    }
                    else -> {
                        item {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${plural(lista.size, "faixa")} · no ${atual.nome}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                BotaoPilula("Aleatório", { vm.tocar(lista, origem = atual.nome, aleatorio = true) }, icone = Icons.Rounded.Shuffle)
                            }
                        }
                        itemsIndexed(lista, key = { _, m -> m.id }) { i, m ->
                            LinhaMusica(
                                musica = m,
                                ativa = rep.atual?.id == m.id,
                                tocando = rep.tocando,
                                subtitulo = if (prefs.ordenacao == Ordenacao.ALBUM && m.album.isNotBlank()) "${m.album} · ${m.artista}" else m.artista,
                                onClick = { vm.tocar(lista, i, origem = atual.nome) },
                                onMenu = { nav.menuMusica = m },
                            )
                        }
                    }
                }
            }
        }

        FiltroBiblioteca.ALBUNS -> {
            val direcao = LocalLayoutDirection.current
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(direcao) + 16.dp,
                    end = contentPadding.calculateEndPadding(direcao) + 16.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { cabecalho(0.dp) }
                if (bib.albuns.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EstadoVazio(Icons.Rounded.Album, "Nenhum álbum", "Álbuns são montados pela tag de álbum dos arquivos. Faixas sem essa tag ficam só em Faixas.")
                    }
                }
                items(bib.albuns, key = { it.chave }) { a -> CartaoAlbum(a) { nav.abrir(Rota.Album(a.chave)) } }
            }
        }

        FiltroBiblioteca.ARTISTAS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { cabecalho(16.dp) }
            if (bib.artistas.isEmpty()) {
                item { EstadoVazio(Icons.Rounded.Person, "Nenhum artista", "Os artistas vêm das tags das faixas.") }
            }
            items(bib.artistas, key = { "art_${it.nome}" }) { a -> LinhaArtista(a) { nav.abrir(Rota.Artista(a.nome)) } }
        }

        FiltroBiblioteca.PLAYLISTS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { cabecalho(16.dp) }
            item {
                val sinal = LocalCoresSongV.current
                LinhaColecao(
                    titulo = "Favoritas",
                    subtitulo = plural(prefs.favoritos.size, "faixa"),
                    onClick = { nav.abrir(Rota.Favoritas) },
                    arte = {
                        Surface(color = sinal.sinal, shape = MaterialTheme.shapes.small, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = sinal.noSinal) }
                        }
                    },
                )
            }
            items(prefs.playlists, key = { it.id }) { p ->
                LinhaColecao(
                    titulo = p.nome,
                    subtitulo = plural(p.musicasIds.size, "faixa"),
                    onClick = { nav.abrir(Rota.Playlist(p.id)) },
                    arte = { MosaicoCapasPorId(p.musicasIds, p.nome, Modifier.size(56.dp)) },
                )
            }
            if (prefs.playlists.isEmpty()) {
                item {
                    EstadoVazio(
                        Icons.Rounded.Add,
                        "Crie sua primeira playlist",
                        "Junte faixas de qualquer álbum. Também dá para adicionar pelo menu ⋮ de cada faixa.",
                        acao = "Nova playlist",
                        onAcao = { criando = true },
                    )
                }
            }
        }
    }

    if (criando) {
        DialogoNome(titulo = "Nova playlist", confirmar = "Criar", onConfirmar = { vm.criarPlaylist(it); criando = false }, onFechar = { criando = false })
    }
}

@Composable
private fun MenuOrdenacao(atual: Ordenacao, onEscolher: (Ordenacao) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aberto = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar faixas") }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            Ordenacao.entries.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.rotulo) },
                    onClick = { onEscolher(o); aberto = false },
                    trailingIcon = { if (o == atual) Icon(Icons.Rounded.Check, contentDescription = "Selecionada") },
                )
            }
        }
    }
}

@Composable
private fun LinhaArtista(artista: Artista, onClick: () -> Unit) {
    LinhaColecao(
        titulo = artista.nome,
        subtitulo = buildString {
            append(plural(artista.musicas.size, "faixa"))
            if (artista.albuns.isNotEmpty()) append(" · ${plural(artista.albuns.size, "álbum", "álbuns")}")
        },
        onClick = onClick,
        arte = { CapaArte(artista.faixaDaCapa, Modifier.size(56.dp), forma = CircleShape) },
    )
}

@Composable
fun LinhaColecao(titulo: String, subtitulo: String, onClick: () -> Unit, arte: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        arte()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
    Spacer(Modifier.height(2.dp))
}
