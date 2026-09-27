package com.songv.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica
import com.songv.app.player.Progresso
import com.songv.app.ui.theme.LocalCoresSongV
import com.songv.app.ui.theme.acabamento
import com.songv.app.ui.theme.sombra
import com.songv.app.ui.theme.superficie
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

@Composable
fun MiniPlayer(
    musica: Musica,
    tocando: Boolean,
    progresso: StateFlow<Progresso>,
    onAbrir: () -> Unit,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit,
    modifier: Modifier = Modifier,
    /** Aplicado exatamente sobre o cartão (depois da margem): é onde entra o desfoque do vidro. */
    fundo: Modifier = Modifier,
) {
    val sinal = LocalCoresSongV.current.sinal
    val a = acabamento
    val forma = MaterialTheme.shapes.medium
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = forma,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            // A sombra vem antes da superfície: se ficasse no Surface, seria desenhada por cima dela.
            .shadow(a.sombra(6.dp), forma)
            .then(fundo)
            .superficie(a, forma, MaterialTheme.colorScheme.surfaceContainerHigh, sobreConteudo = true),
    ) {
        Column {
            Row(
                Modifier
                    .clickable(onClickLabel = "Abrir o player", onClick = onAbrir)
                    .arrasteHorizontal(onEsquerda = onProxima, onDireita = onAnterior)
                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = musica,
                    transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                    contentKey = { it.id },
                    label = "miniFaixa",
                    modifier = Modifier.weight(1f),
                ) { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CapaArte(m, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                m.artista,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(onClick = onPlayPause) {
                    Icon(if (tocando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (tocando) "Pausar" else "Tocar")
                }
                IconButton(onClick = onProxima) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima faixa")
                }
            }
            LinhaProgresso(progresso, sinal)
        }
    }
}

/** Só este pedaço observa a posição — o resto do mini player não recompõe a cada tique. */
@Composable
private fun LinhaProgresso(progresso: StateFlow<Progresso>, cor: androidx.compose.ui.graphics.Color) {
    val p by progresso.collectAsState()
    val trilho = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val f = if (p.duracaoMs > 0) (p.posicaoMs.toFloat() / p.duracaoMs).coerceIn(0f, 1f) else 0f
            drawLine(trilho, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
            drawLine(cor, Offset(0f, size.height / 2), Offset(size.width * f, size.height / 2), strokeWidth = size.height)
        }
    }
}
