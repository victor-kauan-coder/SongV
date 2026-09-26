package com.songv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica
import com.songv.app.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    musicas: List<Musica>,
    todasAsMusicas: List<Musica>,
    onVoltar: () -> Unit,
    onTocarTudo: () -> Unit,
    onMusicaClick: (Int) -> Unit,
    onRemoverMusica: (String) -> Unit,
    onAdicionarMusica: (String) -> Unit,
    onRenomear: (String) -> Unit,
    onExcluir: () -> Unit
) {
    var mostrarAdicionar by remember { mutableStateOf(false) }
    var mostrarRenomear by remember { mutableStateOf(false) }
    var mostrarMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = mostrarMenu, onDismissRequest = { mostrarMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Renomear") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { mostrarMenu = false; mostrarRenomear = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir playlist") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { mostrarMenu = false; onExcluir(); onVoltar() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarAdicionar = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar músicas") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (musicas.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Essa playlist ainda não tem músicas.\nToque em \"Adicionar músicas\" para começar.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        CabecalhoPlaylist(
                            nome = playlist.nome,
                            quantidadeMusicas = musicas.size,
                            capa = musicas.firstOrNull { it.capa != null }?.capa,
                            onTocarTudo = onTocarTudo
                        )
                    }
                    items(musicas.size, key = { musicas[it].id }) { indice ->
                        val musica = musicas[indice]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f)) {
                                ItemMusicaSimples(musica = musica, onClick = { onMusicaClick(indice) })
                            }
                            IconButton(onClick = { onRemoverMusica(musica.id) }) {
                                Icon(
                                    Icons.Filled.RemoveCircleOutline,
                                    contentDescription = "Remover da playlist",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    if (mostrarAdicionar) {
        SeletorMusicasDialog(
            todasAsMusicas = todasAsMusicas,
            idsJaNaPlaylist = musicas.map { it.id }.toSet(),
            onMusicaSelecionada = onAdicionarMusica,
            onFechar = { mostrarAdicionar = false }
        )
    }

    if (mostrarRenomear) {
        RenomearPlaylistDialog(
            nomeAtual = playlist.nome,
            onConfirmar = { novoNome -> onRenomear(novoNome); mostrarRenomear = false },
            onFechar = { mostrarRenomear = false }
        )
    }
}

@Composable
private fun CabecalhoPlaylist(
    nome: String,
    quantidadeMusicas: Int,
    capa: android.graphics.Bitmap?,
    onTocarTudo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (capa != null) {
                androidx.compose.foundation.Image(
                    bitmap = capa.asImageBitmap(),
                    contentDescription = nome,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            if (quantidadeMusicas == 1) "1 música" else "$quantidadeMusicas músicas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onTocarTudo, shape = RoundedCornerShape(24.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tocar tudo")
        }
    }
}

@Composable
private fun SeletorMusicasDialog(
    todasAsMusicas: List<Musica>,
    idsJaNaPlaylist: Set<String>,
    onMusicaSelecionada: (String) -> Unit,
    onFechar: () -> Unit
) {
    var termo by remember { mutableStateOf("") }
    val filtradas = remember(termo, todasAsMusicas) {
        if (termo.isBlank()) todasAsMusicas
        else todasAsMusicas.filter {
            it.titulo.contains(termo, ignoreCase = true) || it.artista.contains(termo, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Adicionar músicas") },
        text = {
            Column(modifier = Modifier.height(420.dp)) {
                OutlinedTextField(
                    value = termo,
                    onValueChange = { termo = it },
                    placeholder = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtradas.size, key = { filtradas[it].id }) { indice ->
                        val musica = filtradas[indice]
                        val jaAdicionada = musica.id in idsJaNaPlaylist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !jaAdicionada) { onMusicaSelecionada(musica.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(musica.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    musica.artista,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (jaAdicionada) {
                                Icon(Icons.Filled.Check, contentDescription = "Já na playlist", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = "Adicionar")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFechar) { Text("Concluído") }
        }
    )
}

@Composable
private fun RenomearPlaylistDialog(
    nomeAtual: String,
    onConfirmar: (String) -> Unit,
    onFechar: () -> Unit
) {
    var texto by remember { mutableStateOf(nomeAtual) }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Renomear playlist") },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (texto.isNotBlank()) onConfirmar(texto) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text("Cancelar") }
        }
    )
}
