package com.songv.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica
import com.songv.app.model.Playlist
import com.songv.app.player.EstadoPlayer

/** Abas de navegação principais da tela inicial, no estilo Início / Músicas / Artistas / Playlists / Favoritas. */
enum class AbaBiblioteca(val label: String, val icone: ImageVector, val iconeSelecionado: ImageVector) {
    INICIO("Início", Icons.Outlined.Home, Icons.Filled.Home),
    MUSICAS("Músicas", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),
    ARTISTAS("Artistas", Icons.Outlined.Person, Icons.Filled.Person),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic),
    FAVORITAS("Favoritas", Icons.Filled.FavoriteBorder, Icons.Filled.Favorite)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaScreen(
    estado: EstadoPlayer,
    onMusicaClick: (Int) -> Unit,
    onAtualizarClick: () -> Unit,
    onConfiguracoesClick: () -> Unit,
    onFaixaAtualClick: () -> Unit,
    onFavoritoClick: (String) -> Unit,
    onBuscaChange: (String) -> Unit,
    onArtistaClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onCriarPlaylistClick: () -> Unit
) {
    var buscaVisivel by remember { mutableStateOf(false) }
    var abaSelecionada by remember { mutableStateOf(AbaBiblioteca.INICIO) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("SongV", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            buscaVisivel = !buscaVisivel
                            if (!buscaVisivel) onBuscaChange("")
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar")
                        }
                        IconButton(onClick = onAtualizarClick) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Atualizar biblioteca")
                        }
                        IconButton(onClick = onConfiguracoesClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                        }
                    }
                )
                AnimatedVisibility(visible = buscaVisivel, enter = expandVertically(), exit = shrinkVertically()) {
                    CampoBusca(termo = estado.termoBusca, onTermoChange = onBuscaChange)
                }
            }
        },
        bottomBar = {
            Column {
                if (estado.faixaAtual != null) {
                    MiniPlayerBar(estado = estado, onClick = onFaixaAtualClick)
                }
                BarraNavegacaoInferior(abaSelecionada = abaSelecionada, onAbaClick = { abaSelecionada = it })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                estado.carregandoBiblioteca -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                estado.biblioteca.isEmpty() -> {
                    EstadoVazio(mensagem = estado.erro ?: "Nenhuma música encontrada na pasta Music.")
                }
                else -> {
                    when (abaSelecionada) {
                        AbaBiblioteca.INICIO -> AbaInicio(
                            estado = estado,
                            onMusicaClick = onMusicaClick,
                            onFavoritoClick = onFavoritoClick,
                            onArtistaClick = onArtistaClick,
                            onPlaylistClick = onPlaylistClick,
                            onVerTodasMusicas = { abaSelecionada = AbaBiblioteca.MUSICAS },
                            onVerArtistas = { abaSelecionada = AbaBiblioteca.ARTISTAS },
                            onVerPlaylists = { abaSelecionada = AbaBiblioteca.PLAYLISTS }
                        )
                        AbaBiblioteca.MUSICAS -> AbaMusicas(
                            estado = estado,
                            listaBase = estado.bibliotecaFiltrada,
                            onMusicaClick = onMusicaClick,
                            onFavoritoClick = onFavoritoClick
                        )
                        AbaBiblioteca.FAVORITAS -> AbaMusicas(
                            estado = estado,
                            listaBase = estado.bibliotecaFiltrada.filter { it.id in estado.favoritos },
                            onMusicaClick = onMusicaClick,
                            onFavoritoClick = onFavoritoClick,
                            mensagemVazia = "Nenhuma música favoritada ainda. Toque no coração durante a reprodução."
                        )
                        AbaBiblioteca.ARTISTAS -> AbaArtistas(
                            estado = estado,
                            onArtistaClick = onArtistaClick
                        )
                        AbaBiblioteca.PLAYLISTS -> AbaPlaylists(
                            playlists = estado.playlists,
                            estado = estado,
                            onPlaylistClick = onPlaylistClick,
                            onCriarPlaylistClick = onCriarPlaylistClick
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// ABA INÍCIO — estilo "Home" do Spotify: carrosséis de destaque + acesso rápido
// =============================================================================
@Composable
private fun AbaInicio(
    estado: EstadoPlayer,
    onMusicaClick: (Int) -> Unit,
    onFavoritoClick: (String) -> Unit,
    onArtistaClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onVerTodasMusicas: () -> Unit,
    onVerArtistas: () -> Unit,
    onVerPlaylists: () -> Unit
) {
    val recentes = remember(estado.biblioteca) { estado.biblioteca.take(10) }
    val topArtistas = remember(estado.musicasPorArtista) {
        estado.musicasPorArtista.sortedByDescending { it.second.size }.take(10)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            SecaoCabecalho(titulo = "Adicionadas recentemente", onVerTudo = onVerTodasMusicas)
            LinhaCardsMusica(musicas = recentes, onMusicaClick = { idAbsoluto -> onMusicaClick(idAbsoluto) }, estado = estado)
        }

        if (estado.playlists.isNotEmpty()) {
            item {
                SecaoCabecalho(titulo = "Suas playlists", onVerTudo = onVerPlaylists)
                LinhaCardsPlaylist(playlists = estado.playlists, onPlaylistClick = onPlaylistClick)
            }
        }

        if (topArtistas.isNotEmpty()) {
            item {
                SecaoCabecalho(titulo = "Principais artistas", onVerTudo = onVerArtistas)
                LinhaCardsArtista(artistas = topArtistas, onArtistaClick = onArtistaClick)
            }
        }

        item {
            SecaoCabecalho(titulo = "Todas as músicas", onVerTudo = onVerTodasMusicas)
        }
        items(estado.biblioteca.take(6).size, key = { "inicio_${estado.biblioteca[it].id}" }) { indice ->
            val musica = estado.biblioteca[indice]
            ItemMusica(
                musica = musica,
                selecionada = indice == estado.indiceFilaAtual && estado.filaReproducao === estado.biblioteca,
                favoritada = musica.id in estado.favoritos,
                onClick = { onMusicaClick(indice) },
                onFavoritoClick = { onFavoritoClick(musica.id) }
            )
        }
    }
}

@Composable
private fun SecaoCabecalho(titulo: String, onVerTudo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onVerTudo) { Text("Ver tudo") }
    }
}

@Composable
private fun LinhaCardsMusica(musicas: List<Musica>, estado: EstadoPlayer, onMusicaClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(musicas.size, key = { "card_musica_${musicas[it].id}" }) { indiceLocal ->
            val musica = musicas[indiceLocal]
            val indiceAbsoluto = estado.biblioteca.indexOfFirst { it.id == musica.id }
            CardMusica(musica = musica, onClick = { onMusicaClick(indiceAbsoluto) })
        }
    }
}

@Composable
private fun CardMusica(musica: Musica, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        CapaThumb(musica = musica, tamanho = 120.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            musica.titulo,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            musica.artista,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LinhaCardsArtista(artistas: List<Pair<String, List<Musica>>>, onArtistaClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artistas.size, key = { "card_artista_${artistas[it].first}" }) { indice ->
            val (nomeArtista, musicas) = artistas[indice]
            CardArtista(nomeArtista = nomeArtista, capa = musicas.firstOrNull { it.capa != null }?.capa, onClick = { onArtistaClick(nomeArtista) })
        }
    }
}

@Composable
private fun CardArtista(nomeArtista: String, capa: android.graphics.Bitmap?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (capa != null) {
                Image(
                    bitmap = capa.asImageBitmap(),
                    contentDescription = nomeArtista,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            nomeArtista,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LinhaCardsPlaylist(playlists: List<Playlist>, onPlaylistClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists.size, key = { "card_playlist_${playlists[it].id}" }) { indice ->
            val playlist = playlists[indice]
            CardPlaylist(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
        }
    }
}

@Composable
private fun CardPlaylist(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (playlist.musicasIds.size == 1) "1 música" else "${playlist.musicasIds.size} músicas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// =============================================================================
// ABA MÚSICAS / FAVORITAS — lista simples reaproveitável
// =============================================================================
@Composable
private fun AbaMusicas(
    estado: EstadoPlayer,
    listaBase: List<Musica>,
    onMusicaClick: (Int) -> Unit,
    onFavoritoClick: (String) -> Unit,
    mensagemVazia: String? = null
) {
    if (listaBase.isEmpty()) {
        EstadoVazio(
            mensagem = mensagemVazia ?: if (estado.termoBusca.isNotBlank()) {
                "Nenhum resultado para \"${estado.termoBusca}\"."
            } else {
                "Nenhuma música encontrada."
            }
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(listaBase.size, key = { listaBase[it].id }) { indiceExibido ->
            val musica = listaBase[indiceExibido]
            val indiceAbsoluto = estado.biblioteca.indexOfFirst { it.id == musica.id }
            ItemMusica(
                musica = musica,
                selecionada = indiceAbsoluto == estado.indiceFilaAtual && estado.filaReproducao === estado.biblioteca,
                favoritada = musica.id in estado.favoritos,
                onClick = { onMusicaClick(indiceAbsoluto) },
                onFavoritoClick = { onFavoritoClick(musica.id) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// =============================================================================
// ABA ARTISTAS — lista de artistas agrupados
// =============================================================================
@Composable
private fun AbaArtistas(estado: EstadoPlayer, onArtistaClick: (String) -> Unit) {
    val grupos = estado.musicasPorArtista
    if (grupos.isEmpty()) {
        EstadoVazio(mensagem = "Nenhum artista encontrado.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(grupos.size, key = { grupos[it].first }) { indice ->
            val (nomeArtista, musicas) = grupos[indice]
            ItemArtista(nomeArtista = nomeArtista, quantidade = musicas.size, capa = musicas.firstOrNull { it.capa != null }?.capa, onClick = { onArtistaClick(nomeArtista) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ItemArtista(nomeArtista: String, quantidade: Int, capa: android.graphics.Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (capa != null) {
                Image(bitmap = capa.asImageBitmap(), contentDescription = nomeArtista, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(nomeArtista, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (quantidade == 1) "1 música" else "$quantidade músicas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

// =============================================================================
// ABA PLAYLISTS — lista de playlists do usuário + criar nova
// =============================================================================
@Composable
private fun AbaPlaylists(
    playlists: List<Playlist>,
    estado: EstadoPlayer,
    onPlaylistClick: (String) -> Unit,
    onCriarPlaylistClick: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCriarPlaylistClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Text("Criar nova playlist", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }

        if (playlists.isEmpty()) {
            item {
                Text(
                    "Você ainda não criou nenhuma playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(playlists.size, key = { playlists[it].id }) { indice ->
                val playlist = playlists[indice]
                ItemPlaylist(
                    playlist = playlist,
                    capa = estado.musicasDaPlaylist(playlist).firstOrNull { it.capa != null }?.capa,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ItemPlaylist(playlist: Playlist, capa: android.graphics.Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (capa != null) {
                Image(bitmap = capa.asImageBitmap(), contentDescription = playlist.nome, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.nome, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (playlist.musicasIds.size == 1) "1 música" else "${playlist.musicasIds.size} músicas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

// =============================================================================
// COMPONENTES COMPARTILHADOS
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoBusca(termo: String, onTermoChange: (String) -> Unit) {
    OutlinedTextField(
        value = termo,
        onValueChange = onTermoChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar por título ou artista") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (termo.isNotEmpty()) {
                IconButton(onClick = { onTermoChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Limpar busca")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun BarraNavegacaoInferior(abaSelecionada: AbaBiblioteca, onAbaClick: (AbaBiblioteca) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        AbaBiblioteca.values().forEach { aba ->
            val selecionada = abaSelecionada == aba
            NavigationBarItem(
                selected = selecionada,
                onClick = { onAbaClick(aba) },
                icon = {
                    Icon(
                        if (selecionada) aba.iconeSelecionado else aba.icone,
                        contentDescription = aba.label
                    )
                },
                label = { Text(aba.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                )
            )
        }
    }
}

@Composable
private fun EstadoVazio(mensagem: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            mensagem,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ItemMusica(
    musica: Musica,
    selecionada: Boolean,
    favoritada: Boolean,
    onClick: () -> Unit,
    onFavoritoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selecionada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapaThumb(musica = musica, tamanho = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                musica.titulo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Normal,
                color = if (selecionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
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
        IconButton(onClick = onFavoritoClick) {
            Icon(
                if (favoritada) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (favoritada) "Remover dos favoritos" else "Favoritar",
                tint = if (favoritada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CapaThumb(musica: Musica, tamanho: Dp, icone: ImageVector = Icons.Filled.MusicNote) {
    val bitmap = musica.capa
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Capa de ${musica.titulo}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(tamanho / 2)
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(estado: EstadoPlayer, onClick: () -> Unit) {
    val musica = estado.faixaAtual ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapaThumb(musica = musica, tamanho = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    musica.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    musica.artista,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (estado.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
