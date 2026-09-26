package com.songv.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.data.normalizarBusca
import com.songv.app.model.Musica
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.Navegador
import com.songv.app.ui.Rota
import com.songv.app.ui.components.BotaoModo
import com.songv.app.ui.components.BotaoPlay
import com.songv.app.ui.components.CabecalhoSecao
import com.songv.app.ui.components.CapaArte
import com.songv.app.ui.components.DialogoNome
import com.songv.app.ui.components.EstadoVazio
import com.songv.app.ui.components.LinhaMusica
import com.songv.app.ui.components.LocalCapas
import com.songv.app.ui.components.MosaicoCapas
import com.songv.app.ui.components.alcaDeArraste
import com.songv.app.ui.components.formatarDuracaoLonga
import com.songv.app.ui.components.itemReordenavel
import com.songv.app.ui.components.plural
import com.songv.app.ui.components.rememberEstadoReordenacao

// =============================================================================
// Estrutura comum: capa grande sobre um fundo tingido pela cor da capa
// =============================================================================

@Composable
private fun corDaCapa(musica: Musica?): Color {
    val capas = LocalCapas.current
    val base = MaterialTheme.colorScheme.background
    val cor by produceState<Color?>(null, musica?.id) { value = musica?.let { capas.corDominante(it) }?.let { Color(it) } }
    return cor?.let { lerp(base, it, 0.38f) } ?: MaterialTheme.colorScheme.surfaceContainer
}

@Composable
private fun CabecalhoColecao(
    faixaDaCor: Musica?,
    onVoltar: () -> Unit,
    arte: @Composable () -> Unit,
    titulo: String,
    linhaSecundaria: @Composable () -> Unit,
    info: String,
    tocandoEsta: Boolean,
    onTocar: () -> Unit,
    onAleatorio: () -> Unit,
    acoes: @Composable () -> Unit = {},
) {
    val tinta = corDaCapa(faixaDaCor)
    val fundo = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tinta, fundo)))) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
                Spacer(Modifier.weight(1f))
                acoes()
            }
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) { arte() }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(titulo, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                linhaSecundaria()
                Spacer(Modifier.height(2.dp))
                Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoModo(Icons.Rounded.Shuffle, "Tocar em ordem aleatória", ativo = false, onClick = onAleatorio)
                Spacer(Modifier.weight(1f))
                BotaoPlay(tocando = tocandoEsta, onClick = onTocar, tamanho = 56.dp)
            }
        }
    }
}

// =============================================================================
// Álbum
// =============================================================================

@Composable
fun AlbumScreen(vm: PlayerViewModel, nav: Navegador, chave: String, contentPadding: PaddingValues) {
    val bib by vm.biblioteca.collectAsState()
    val rep by vm.reproducao.collectAsState()
    val album = bib.albuns.firstOrNull { it.chave == chave }
    if (album == null) {
        ColecaoIndisponivel(nav, "Álbum não encontrado", "Ele pode ter sido removido do aparelho.")
        return
    }
    val origem = "Álbum · ${album.titulo}"
    val tocandoEste = rep.origem == origem && rep.tocando
    val multidisco = album.musicas.mapNotNull { it.disco }.distinct().size > 1

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            CabecalhoColecao(
                faixaDaCor = album.faixaDaCapa,
                onVoltar = { nav.voltar() },
                arte = {
                    CapaArte(
                        album.faixaDaCapa,
                        Modifier.fillMaxWidth(0.66f).aspectRatio(1f),
                        grande = true,
                        forma = MaterialTheme.shapes.medium,
                        descricao = "Capa de ${album.titulo}",
                    )
                },
                titulo = album.titulo,
                linhaSecundaria = {
                    Text(
                        album.artista,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { nav.abrir(Rota.Artista(album.musicas.first().artistas.first())) },
                    )
                },
                info = listOfNotNull("Álbum", album.ano?.toString(), plural(album.musicas.size, "faixa"), formatarDuracaoLonga(album.duracaoMs)).joinToString(" · "),
                tocandoEsta = tocandoEste,
                onTocar = { if (tocandoEste) vm.alternarPlayPause() else vm.tocar(album.musicas, origem = origem) },
                onAleatorio = { vm.tocar(album.musicas, origem = origem, aleatorio = true) },
                acoes = {
                    IconButton(onClick = { vm.tocarAseguir(album.musicas) }) { Icon(Icons.Rounded.QueuePlayNext, contentDescription = "Tocar álbum a seguir") }
                    IconButton(onClick = { nav.paraPlaylist = album.musicas }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Adicionar álbum a uma playlist") }
                },
            )
        }
        var discoAnterior: Int? = null
        album.musicas.forEachIndexed { i, m ->
            if (multidisco && m.disco != discoAnterior) {
                discoAnterior = m.disco
                item(key = "disco_${m.disco}") {
                    Text(
                        "Disco ${m.disco ?: 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            item(key = m.id) {
                LinhaMusica(
                    musica = m,
                    numero = m.faixa ?: (i + 1),
                    ativa = rep.atual?.id == m.id,
                    tocando = rep.tocando,
                    subtitulo = if (m.artista != album.artista) m.artista else formatarDuracaoCurta(m),
                    onClick = { vm.tocar(album.musicas, i, origem = origem) },
                    onMenu = { nav.menuMusica = m },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatarDuracaoCurta(m: Musica) = com.songv.app.ui.components.formatarTempo(m.duracaoMs)


// =============================================================================
// Artista
// =============================================================================

@Composable
fun ArtistaScreen(vm: PlayerViewModel, nav: Navegador, nome: String, contentPadding: PaddingValues) {
    val bib by vm.biblioteca.collectAsState()
    val rep by vm.reproducao.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val artista = bib.artistas.firstOrNull { it.nome == nome }
    if (artista == null) {
        ColecaoIndisponivel(nav, "Artista não encontrado", "As faixas dele podem ter sido removidas.")
        return
    }
    val origem = "Artista · ${artista.nome}"
    val tocandoEste = rep.origem == origem && rep.tocando
    val populares = remember(artista, prefs.contagens) {
        artista.musicas.filter { (prefs.contagens[it.id] ?: 0) > 0 }.sortedByDescending { prefs.contagens[it.id] ?: 0 }.take(5)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            CabecalhoColecao(
                faixaDaCor = artista.faixaDaCapa,
                onVoltar = { nav.voltar() },
                arte = { CapaArte(artista.faixaDaCapa, Modifier.size(180.dp), grande = true, forma = CircleShape) },
                titulo = artista.nome,
                linhaSecundaria = {},
                info = listOfNotNull(
                    plural(artista.musicas.size, "faixa"),
                    artista.albuns.takeIf { it.isNotEmpty() }?.let { plural(it.size, "álbum", "álbuns") },
                    formatarDuracaoLonga(artista.musicas.sumOf { it.duracaoMs }),
                ).joinToString(" · "),
                tocandoEsta = tocandoEste,
                onTocar = { if (tocandoEste) vm.alternarPlayPause() else vm.tocar(artista.musicas, origem = origem) },
                onAleatorio = { vm.tocar(artista.musicas, origem = origem, aleatorio = true) },
            )
        }
        if (populares.size >= 2) {
            item { CabecalhoSecao("Você mais ouve") }
            itemsIndexed(populares, key = { _, m -> "pop_${m.id}" }) { i, m ->
                LinhaMusica(
                    musica = m,
                    ativa = rep.atual?.id == m.id,
                    tocando = rep.tocando,
                    subtitulo = m.album.ifBlank { m.artista },
                    onClick = { vm.tocar(populares, i, origem = origem) },
                    onMenu = { nav.menuMusica = m },
                )
            }
        }
        if (artista.albuns.isNotEmpty()) {
            item { CabecalhoSecao("Álbuns") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artista.albuns, key = { it.chave }) { a -> CartaoAlbum(a, Modifier.width(148.dp)) { nav.abrir(Rota.Album(a.chave)) } }
                }
            }
        }
        item { CabecalhoSecao("Todas as faixas") }
        itemsIndexed(artista.musicas, key = { _, m -> m.id }) { i, m ->
            LinhaMusica(
                musica = m,
                ativa = rep.atual?.id == m.id,
                tocando = rep.tocando,
                subtitulo = m.album.ifBlank { m.artista },
                onClick = { vm.tocar(artista.musicas, i, origem = origem) },
                onMenu = { nav.menuMusica = m },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =============================================================================
// Playlist e Favoritas
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(vm: PlayerViewModel, nav: Navegador, id: String?, contentPadding: PaddingValues) {
    val bib by vm.biblioteca.collectAsState()
    val rep by vm.reproducao.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val favoritas = id == null
    val playlist = if (favoritas) null else prefs.playlists.firstOrNull { it.id == id }
    if (!favoritas && playlist == null) {
        // Excluída (talvez agora mesmo, pelo menu): volta sozinha.
        LaunchedEffect(Unit) { nav.voltar() }
        return
    }
    val nome = playlist?.nome ?: "Favoritas"
    val musicas = remember(playlist, prefs.favoritos, bib.porId) {
        if (favoritas) bib.musicas.filter { it.id in prefs.favoritos } else playlist!!.musicasIds.mapNotNull { bib.porId[it] }
    }
    val origem = if (favoritas) "Favoritas" else "Playlist · $nome"
    val tocandoEsta = rep.origem == origem && rep.tocando

    var adicionando by rememberSaveable { mutableStateOf(false) }
    var renomeando by rememberSaveable { mutableStateOf(false) }
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }

    val estadoLista = rememberLazyListState()
    val reordenacao = rememberEstadoReordenacao<Musica>(estadoLista, chave = { it.id }) { de, para ->
        playlist?.let { vm.moverNaPlaylist(it.id, de, para) }
    }
    reordenacao.sincronizar(musicas)
    val exibidas = reordenacao.itens

    LazyColumn(Modifier.fillMaxSize(), state = estadoLista, contentPadding = contentPadding) {
        item {
            CabecalhoColecao(
                faixaDaCor = musicas.firstOrNull { it.temCapa },
                onVoltar = { nav.voltar() },
                arte = {
                    if (favoritas) {
                        val sinal = com.songv.app.ui.theme.LocalCoresSongV.current
                        Box(
                            Modifier.fillMaxWidth(0.56f).aspectRatio(1f).background(sinal.sinal, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = sinal.noSinal, modifier = Modifier.size(72.dp)) }
                    } else {
                        MosaicoCapas(musicas, nome, Modifier.fillMaxWidth(0.62f), forma = MaterialTheme.shapes.medium)
                    }
                },
                titulo = nome,
                linhaSecundaria = {},
                info = "${if (favoritas) "Suas favoritas" else "Playlist"} · ${plural(musicas.size, "faixa")} · ${formatarDuracaoLonga(musicas.sumOf { it.duracaoMs })}",
                tocandoEsta = tocandoEsta,
                onTocar = { if (tocandoEsta) vm.alternarPlayPause() else vm.tocar(musicas, origem = origem) },
                onAleatorio = { vm.tocar(musicas, origem = origem, aleatorio = true) },
                acoes = {
                    if (!favoritas) {
                        IconButton(onClick = { adicionando = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Adicionar faixas") }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções da playlist") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Renomear") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = { menu = false; renomeando = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Excluir playlist") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = { menu = false; confirmarExclusao = true },
                                )
                            }
                        }
                    }
                },
            )
        }
        if (musicas.isEmpty()) {
            item {
                if (favoritas) {
                    EstadoVazio(
                        Icons.Rounded.FavoriteBorder,
                        "Nenhuma favorita ainda",
                        "Toque no coração no player ou use o menu ⋮ de uma faixa para guardá-la aqui.",
                    )
                } else {
                    EstadoVazio(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        "Playlist vazia",
                        "Adicione faixas da sua biblioteca. Depois é só arrastar pela alça para mudar a ordem.",
                        acao = "Adicionar faixas",
                        onAcao = { adicionando = true },
                    )
                }
            }
        }
        itemsIndexed(exibidas, key = { _, m -> m.id }) { i, m ->
            val arrastando = reordenacao.estaArrastando(m)
            val fundo by animateFloatAsState(if (arrastando) 1f else 0f, label = "fundoArraste")
            LinhaMusica(
                musica = m,
                ativa = rep.atual?.id == m.id,
                tocando = rep.tocando,
                subtitulo = m.artista,
                onClick = { vm.tocar(exibidas, i, origem = origem) },
                onMenu = { nav.menuMusica = m },
                modifier = Modifier
                    .then(if (arrastando) Modifier else Modifier.animateItemPlacement())
                    .itemReordenavel(reordenacao, m)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = fundo)),
                acessorio = {
                    if (favoritas) {
                        IconButton(onClick = { vm.alternarFavorito(m.id) }) {
                            Icon(Icons.Rounded.Favorite, contentDescription = "Remover das favoritas", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { playlist?.let { vm.removerDaPlaylist(it.id, m.id) } }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remover da playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Arrastar para reordenar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp).padding(12.dp).alcaDeArraste(reordenacao, m),
                        )
                    }
                },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (adicionando && playlist != null) {
        AdicionarFaixasSheet(
            todas = bib.musicas,
            jaNaPlaylist = playlist.musicasIds.toSet(),
            onFechar = { adicionando = false },
            onConfirmar = { ids -> vm.adicionarNaPlaylist(playlist.id, ids); adicionando = false },
        )
    }
    if (renomeando && playlist != null) {
        DialogoNome(
            titulo = "Renomear playlist",
            confirmar = "Salvar",
            inicial = playlist.nome,
            onConfirmar = { vm.renomearPlaylist(playlist.id, it); renomeando = false },
            onFechar = { renomeando = false },
        )
    }
    if (confirmarExclusao && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir “${playlist.nome}”?") },
            text = { Text("As faixas continuam na biblioteca; só a playlist some. Dá para desfazer logo em seguida.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; vm.excluirPlaylist(playlist.id); nav.voltar() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
        )
    }
}

/** Seleção múltipla com busca para adicionar faixas a uma playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdicionarFaixasSheet(
    todas: List<Musica>,
    jaNaPlaylist: Set<String>,
    onFechar: () -> Unit,
    onConfirmar: (List<String>) -> Unit,
) {
    var termo by remember { mutableStateOf("") }
    val selecionadas = remember { mutableStateListOf<String>() }
    val filtradas = remember(termo, todas) {
        val palavras = normalizarBusca(termo).split(' ').filter { it.isNotBlank() }
        if (palavras.isEmpty()) todas else todas.filter { m -> palavras.all { m.chaveBusca.contains(it) } }
    }
    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Adicionar faixas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onConfirmar(selecionadas.toList()) }, enabled = selecionadas.isNotEmpty()) {
                    Text(if (selecionadas.isEmpty()) "Adicionar" else "Adicionar ${selecionadas.size}")
                }
            }
            TextField(
                value = termo,
                onValueChange = { termo = it },
                placeholder = { Text("Buscar na biblioteca") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(filtradas, key = { it.id }) { m ->
                    val ja = m.id in jaNaPlaylist
                    val marcada = m.id in selecionadas
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !ja) { if (marcada) selecionadas.remove(m.id) else selecionadas.add(m.id) }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .graphicsLayer { alpha = if (ja) 0.45f else 1f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CapaArte(m, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (ja) "Já está na playlist" else m.artista,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (ja) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                        } else {
                            Checkbox(checked = marcada, onCheckedChange = { if (it) selecionadas.add(m.id) else selecionadas.remove(m.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColecaoIndisponivel(nav: Navegador, titulo: String, texto: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = { nav.voltar() }, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
        }
        EstadoVazio(Icons.Rounded.Search, titulo, texto)
    }
}
