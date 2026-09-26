package com.songv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica

/** Tela de detalhe de um artista: capa em destaque (derivada da 1ª música com capa) + lista de faixas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistaScreen(
    nomeArtista: String,
    musicas: List<Musica>,
    onVoltar: () -> Unit,
    onTocarTudo: () -> Unit,
    onMusicaClick: (Int) -> Unit
) {
    val capaDestaque = musicas.firstOrNull { it.capa != null }?.capa

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(nomeArtista, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                CabecalhoArtista(
                    nomeArtista = nomeArtista,
                    capa = capaDestaque,
                    quantidadeMusicas = musicas.size,
                    onTocarTudo = onTocarTudo
                )
            }
            items(musicas.size, key = { musicas[it].id }) { indice ->
                val musica = musicas[indice]
                ItemMusicaSimples(
                    musica = musica,
                    onClick = { onMusicaClick(indice) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CabecalhoArtista(
    nomeArtista: String,
    capa: android.graphics.Bitmap?,
    quantidadeMusicas: Int,
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
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (capa != null) {
                androidx.compose.foundation.Image(
                    bitmap = capa.asImageBitmap(),
                    contentDescription = nomeArtista,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(nomeArtista, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            if (quantidadeMusicas == 1) "1 música" else "$quantidadeMusicas músicas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onTocarTudo, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tocar tudo")
        }
    }
}

@Composable
internal fun ItemMusicaSimples(musica: Musica, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapaThumb(musica = musica, tamanho = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                musica.titulo,
                style = MaterialTheme.typography.bodyLarge,
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
    }
}
