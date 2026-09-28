package com.songv.app.ui.components

import com.songv.app.conexao.ComputadorRemoto
import com.songv.app.ui.theme.acabamento
import com.songv.app.ui.theme.corDeFolha
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica
import com.songv.app.model.Playlist
import com.songv.app.model.TipoLetra

@Composable
private fun ItemAcao(icone: ImageVector, texto: String, onClick: () -> Unit, destaque: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icone,
            contentDescription = null,
            tint = if (destaque) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Ações de uma faixa, abertas pelo "⋮" ou pressionando a linha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuMusicaSheet(
    musica: Musica,
    favoritada: Boolean,
    onFechar: () -> Unit,
    onTocarAseguir: () -> Unit,
    onAdicionarFila: () -> Unit,
    onAdicionarPlaylist: () -> Unit,
    onFavoritar: () -> Unit,
    onIrAlbum: (() -> Unit)?,
    onIrArtista: () -> Unit,
    onTrocarCapa: () -> Unit,
    onRestaurarCapa: (() -> Unit)?,
) {
    val contexto = LocalContext.current
    var detalhes by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = acabamento.corDeFolha(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CapaArte(musica, Modifier.size(56.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(musica.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(musica.artista, musica.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.width(8.dp))
            ItemAcao(Icons.Rounded.QueuePlayNext, "Tocar a seguir", { onTocarAseguir(); onFechar() })
            ItemAcao(Icons.Rounded.AddToQueue, "Adicionar à fila", { onAdicionarFila(); onFechar() })
            ItemAcao(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar a uma playlist", { onAdicionarPlaylist(); onFechar() })
            ItemAcao(
                if (favoritada) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (favoritada) "Remover das favoritas" else "Adicionar às favoritas",
                { onFavoritar(); onFechar() },
                destaque = favoritada,
            )
            onIrAlbum?.let { ItemAcao(Icons.Rounded.Album, "Ir para o álbum", { it(); onFechar() }) }
            ItemAcao(Icons.Rounded.Person, "Ir para o artista", { onIrArtista(); onFechar() })
            // Faixas do computador não têm arquivo neste aparelho: nada de trocar capa ou compartilhar.
            val local = !ComputadorRemoto.ehDoComputador(musica.id)
            if (local) ItemAcao(Icons.Rounded.AddPhotoAlternate, "Trocar a capa", { onTrocarCapa(); onFechar() })
            onRestaurarCapa?.let { ItemAcao(Icons.Rounded.SettingsBackupRestore, "Voltar à capa original", { it(); onFechar() }) }
            if (local) ItemAcao(Icons.Rounded.Share, "Compartilhar arquivo", {
                val envio = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, musica.uri)
                    putExtra(Intent.EXTRA_TITLE, musica.titulo)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                contexto.startActivity(Intent.createChooser(envio, "Compartilhar “${musica.titulo}”"))
                onFechar()
            })
            ItemAcao(Icons.Rounded.Info, "Detalhes do arquivo", { detalhes = true })
        }
    }
    if (detalhes) {
        AlertDialog(
            onDismissRequest = { detalhes = false },
            confirmButton = { TextButton(onClick = { detalhes = false }) { Text("Fechar") } },
            title = { Text(musica.titulo) },
            text = {
                Column {
                    Detalhe("Artista", musica.artista)
                    if (musica.album.isNotBlank()) Detalhe("Álbum", musica.album)
                    musica.ano?.let { Detalhe("Ano", "$it") }
                    musica.faixa?.let { Detalhe("Faixa", "$it") }
                    Detalhe("Duração", formatarTempo(musica.duracaoMs))
                    Detalhe("Tamanho", "%.1f MB".format(musica.tamanhoBytes / 1_048_576f))
                    Detalhe(
                        "Letra",
                        when (musica.tipoLetra) {
                            TipoLetra.SINCRONIZADA -> "Sincronizada"
                            TipoLetra.SIMPLES -> "Sem sincronia"
                            TipoLetra.AUSENTE -> "Não tem"
                        },
                    )
                    Detalhe("Arquivo", if (ComputadorRemoto.ehDoComputador(musica.id)) "No computador" else musica.caminho)
                }
            },
        )
    }
}

@Composable
private fun Detalhe(rotulo: String, valor: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Escolher a playlist de destino (ou criar uma nova ali mesmo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscolherPlaylistSheet(
    musicas: List<Musica>,
    playlists: List<Playlist>,
    onFechar: () -> Unit,
    onEscolher: (Playlist) -> Unit,
    onCriar: (String) -> Unit,
) {
    var criando by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = acabamento.corDeFolha(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                if (musicas.size == 1) "Adicionar “${musicas.first().titulo}” a…" else "Adicionar ${musicas.size} faixas a…",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
            )
            ItemAcao(Icons.Rounded.Add, "Nova playlist", { criando = true }, destaque = true)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onEscolher(p); onFechar() }.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MosaicoCapasPorId(p.musicasIds, p.nome, Modifier.size(44.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nome, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(plural(p.musicasIds.size, "faixa"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
    }
    if (criando) {
        DialogoNome(
            titulo = "Nova playlist",
            confirmar = "Criar e adicionar",
            onConfirmar = { nome -> onCriar(nome); criando = false; onFechar() },
            onFechar = { criando = false },
        )
    }
}

/** Resolve os ids da playlist para o mosaico (precisa da biblioteca, vinda por CompositionLocal). */
@Composable
fun MosaicoCapasPorId(ids: List<String>, nome: String, modifier: Modifier = Modifier) {
    val porId = LocalBiblioteca.current
    val musicas = remember(ids, porId) { ids.mapNotNull { porId[it] } }
    MosaicoCapas(musicas, nome, modifier)
}

val LocalBiblioteca = androidx.compose.runtime.staticCompositionLocalOf<Map<String, Musica>> { emptyMap() }

@Composable
fun DialogoNome(
    titulo: String,
    confirmar: String,
    onConfirmar: (String) -> Unit,
    onFechar: () -> Unit,
    inicial: String = "",
) {
    var texto by rememberSaveable { mutableStateOf(inicial) }
    val foco = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.take(60) },
                placeholder = { Text("Nome da playlist") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { if (texto.isNotBlank()) onConfirmar(texto.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(texto.trim()) }, enabled = texto.isNotBlank()) { Text(confirmar) }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
    LaunchedEffect(Unit) { foco.requestFocus() }
}
