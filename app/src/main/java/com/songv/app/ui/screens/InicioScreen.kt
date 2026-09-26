package com.songv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.songv.app.model.Album
import com.songv.app.model.Artista
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.Aba
import com.songv.app.ui.FiltroBiblioteca
import com.songv.app.ui.Navegador
import com.songv.app.ui.Rota
import com.songv.app.ui.components.CabecalhoSecao
import com.songv.app.ui.components.CapaArte
import com.songv.app.ui.components.EstadoVazio
import com.songv.app.ui.components.IndicadorTocando
import com.songv.app.ui.components.plural
import com.songv.app.ui.theme.EstilosSongV
import com.songv.app.ui.theme.LocalCoresSongV

@Composable
fun InicioScreen(vm: PlayerViewModel, nav: Navegador, contentPadding: PaddingValues) {
    val bib by vm.biblioteca.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val rep by vm.reproducao.collectAsState()

    val recentes = remember(prefs.historico, bib.porId) { prefs.historico.mapNotNull { bib.porId[it] }.take(6) }
    val maisTocadas = remember(prefs.contagens, bib.porId) {
        prefs.contagens.entries.sortedByDescending { it.value }.mapNotNull { e -> bib.porId[e.key]?.let { it to e.value } }.take(5)
    }
    val adicionadas = remember(bib.musicas) { bib.musicas.sortedByDescending { it.adicionadaEmSeg }.take(12) }
    val albuns = remember(bib.albuns) { bib.albuns.sortedByDescending { a -> a.musicas.maxOf { it.adicionadaEmSeg } }.take(12) }
    val artistas = remember(bib.artistas) { bib.artistas.sortedByDescending { it.musicas.size }.take(12) }
    val comLetra = remember(bib.musicas) { bib.musicas.filter { it.tipoLetra == TipoLetra.SINCRONIZADA } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarcaSongV(Modifier.weight(1f))
                IconButton(onClick = { nav.abrir(Rota.Configuracoes) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Configurações")
                }
            }
        }

        if (!bib.pronta || (bib.carregando && bib.musicas.isEmpty())) {
            item { CarregandoBiblioteca(bib.lidas, bib.total) }
            return@LazyColumn
        }

        if (bib.musicas.isEmpty()) {
            item {
                EstadoVazio(
                    icone = Icons.Rounded.LibraryMusic,
                    titulo = if (prefs.pastaBiblioteca.isBlank()) "Nenhuma música no aparelho" else "Nada na pasta ${prefs.pastaBiblioteca}",
                    texto = bib.erro ?: if (prefs.pastaBiblioteca.isBlank()) {
                        "Copie arquivos de áudio para o celular e toque em atualizar."
                    } else {
                        "O SongV procura música só na pasta ${prefs.pastaBiblioteca}. Você pode incluir todas as pastas do aparelho."
                    },
                    acao = if (prefs.pastaBiblioteca.isBlank()) "Atualizar" else "Buscar em todas as pastas",
                    onAcao = { if (prefs.pastaBiblioteca.isBlank()) vm.carregarBiblioteca(forcar = true) else vm.definirPasta("") },
                )
            }
            return@LazyColumn
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                item {
                    Atalho(Icons.Rounded.Shuffle, "Aleatório", destaque = true) {
                        vm.tocar(bib.musicas, origem = "Biblioteca", aleatorio = true)
                    }
                }
                if (prefs.favoritos.isNotEmpty()) {
                    item { Atalho(Icons.Rounded.Favorite, "Favoritas") { nav.abrir(Rota.Favoritas) } }
                }
                if (comLetra.isNotEmpty()) {
                    item {
                        Atalho(Icons.Rounded.Lyrics, "Com letra (${comLetra.size})") {
                            vm.tocar(comLetra, origem = "Com letra sincronizada", aleatorio = true)
                            nav.playerAberto = true
                        }
                    }
                }
                item { Atalho(Icons.Rounded.Refresh, "Atualizar") { vm.carregarBiblioteca(forcar = true) } }
            }
        }

        if (bib.carregando) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = LocalCoresSongV.current.sinal) }
        }

        if (recentes.isNotEmpty()) {
            item { CabecalhoSecao("Tocadas recentemente") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentes.chunked(2).forEach { par ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            par.forEach { m ->
                                AtalhoFaixa(
                                    musica = m,
                                    ativa = rep.atual?.id == m.id,
                                    tocando = rep.tocando,
                                    modifier = Modifier.weight(1f),
                                    onClick = { vm.tocar(recentes, recentes.indexOf(m), origem = "Tocadas recentemente") },
                                )
                            }
                            if (par.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (maisTocadas.size >= 3) {
            item { CabecalhoSecao("Mais tocadas") }
            itemsIndexed(maisTocadas, key = { _, (m, _) -> "top_${m.id}" }) { i, (m, vezes) ->
                LinhaRanking(
                    posicao = i + 1,
                    musica = m,
                    vezes = vezes,
                    ativa = rep.atual?.id == m.id,
                    onClick = { vm.tocar(maisTocadas.map { it.first }, i, origem = "Mais tocadas") },
                    onMenu = { nav.menuMusica = m },
                )
            }
        }

        if (albuns.isNotEmpty()) {
            item { CabecalhoSecao("Álbuns", acao = "Ver todos", onAcao = { nav.filtro = FiltroBiblioteca.ALBUNS; nav.trocarAba(Aba.BIBLIOTECA) }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(albuns, key = { it.chave }) { a -> CartaoAlbum(a, Modifier.width(148.dp)) { nav.abrir(Rota.Album(a.chave)) } }
                }
            }
        }

        item { CabecalhoSecao("Adicionadas recentemente") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(adicionadas, key = { _, m -> "add_${m.id}" }) { i, m ->
                    CartaoFaixa(m, Modifier.width(132.dp), onMenu = { nav.menuMusica = m }) {
                        vm.tocar(adicionadas, i, origem = "Adicionadas recentemente")
                    }
                }
            }
        }

        if (artistas.size > 1) {
            item { CabecalhoSecao("Artistas") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artistas, key = { "art_${it.nome}" }) { a -> CartaoArtista(a) { nav.abrir(Rota.Artista(a.nome)) } }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** "SongV" em Archivo Expandida, com o V na cor de sinal. */
@Composable
fun MarcaSongV(modifier: Modifier = Modifier) {
    val sinal = LocalCoresSongV.current.sinal
    Text(
        buildAnnotatedString {
            append("Song")
            withStyle(SpanStyle(color = sinal)) { append("V") }
        },
        style = EstilosSongV.marca,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
private fun CarregandoBiblioteca(lidas: Int, total: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lendo sua biblioteca", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (total > 0) "$lidas de $total faixas — capas, letras e tags" else "Procurando músicas no aparelho…",
            style = MaterialTheme.typography.bodyMedium.merge(EstilosSongV.numeros),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        val cor = LocalCoresSongV.current.sinal
        if (total > 0) {
            LinearProgressIndicator(progress = { lidas / total.toFloat() }, color = cor, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(color = cor, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Atalho(icone: ImageVector, texto: String, destaque: Boolean = false, onClick: () -> Unit) {
    val cores = LocalCoresSongV.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (destaque) cores.sinal else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (destaque) cores.noSinal else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Atalho compacto (capa + título) da grade "Tocadas recentemente". */
@Composable
private fun AtalhoFaixa(musica: Musica, ativa: Boolean, tocando: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapaArte(musica, Modifier.size(56.dp), forma = androidx.compose.ui.graphics.RectangleShape)
            Text(
                musica.titulo,
                style = MaterialTheme.typography.titleSmall,
                color = if (ativa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            if (ativa) {
                IndicadorTocando(tocando, Modifier.padding(end = 12.dp))
            }
        }
    }
}

@Composable
private fun LinhaRanking(posicao: Int, musica: Musica, vezes: Int, ativa: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$posicao",
            style = MaterialTheme.typography.displaySmall.merge(EstilosSongV.numeros),
            color = if (ativa) LocalCoresSongV.current.sinal else MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(44.dp),
        )
        CapaArte(musica, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                musica.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = if (ativa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${musica.artista} · ${plural(vezes, "vez", "vezes")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções")
        }
    }
}

@Composable
fun CartaoAlbum(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        CapaArte(album.faixaDaCapa, Modifier.fillMaxWidth().aspectRatio(1f), forma = MaterialTheme.shapes.medium)
        Spacer(Modifier.height(8.dp))
        Text(album.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(album.artista, album.ano?.toString()).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CartaoFaixa(musica: Musica, modifier: Modifier, onMenu: () -> Unit, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick, onClickLabel = "Tocar")) {
        CapaArte(musica, Modifier.fillMaxWidth().aspectRatio(1f), forma = MaterialTheme.shapes.medium)
        Spacer(Modifier.height(8.dp))
        Text(musica.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(musica.artista, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CartaoArtista(artista: Artista, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            CapaArte(artista.faixaDaCapa, Modifier.size(104.dp), forma = CircleShape)
        }
        Spacer(Modifier.height(8.dp))
        Text(artista.nome, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(plural(artista.musicas.size, "faixa"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
