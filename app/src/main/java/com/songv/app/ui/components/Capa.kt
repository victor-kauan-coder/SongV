package com.songv.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songv.app.data.CapaRepository
import com.songv.app.model.Musica
import com.songv.app.ui.theme.ArchivoExpandida
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs

val LocalCapas = staticCompositionLocalOf<CapaRepository> { error("CapaRepository não fornecido") }

/** Capas escolhidas à mão (id → arquivo). Quem lê recompõe quando o usuário troca uma capa. */
val LocalCapasProprias = compositionLocalOf<Map<String, String>> { emptyMap() }

/** Bitmap da capa, carregado fora da main thread. Para a versão grande, começa pela miniatura já em memória. */
@Composable
fun rememberCapa(musica: Musica?, grande: Boolean = false): ImageBitmap? {
    val repo = LocalCapas.current
    val propria = LocalCapasProprias.current[musica?.id]
    val inicial = remember(musica?.id, musica?.modificadoSeg, grande, propria) {
        musica?.let { (repo.emMemoria(it, grande) ?: if (grande) repo.emMemoria(it, false) else null)?.asImageBitmap() }
    }
    val bitmap by produceState(inicial, musica?.id, musica?.modificadoSeg, grande, propria) {
        // O estado sobrevive à troca de faixa (item reaproveitado numa lista que reordenou):
        // sem isto, a capa da faixa anterior ficava no lugar da nova.
        value = inicial
        if (musica != null && (value == null || grande)) {
            repo.carregar(musica, grande)?.let { value = it.asImageBitmap() }
        }
    }
    return bitmap
}

/**
 * Capa de uma faixa. Sem imagem embutida, desenha uma capa própria (cor derivada do álbum,
 * iniciais e sulcos de vinil) — nunca o ícone genérico de nota.
 */
@Composable
fun CapaArte(
    musica: Musica?,
    modifier: Modifier = Modifier,
    grande: Boolean = false,
    forma: Shape = MaterialTheme.shapes.small,
    descricao: String? = null,
) {
    val imagem = rememberCapa(musica, grande)
    Box(modifier.clip(forma)) {
        CapaGerada(
            semente = musica?.let { it.album.ifBlank { it.titulo } } ?: "SongV",
            rotulo = musica?.let { it.album.ifBlank { it.titulo } } ?: "",
            modifier = Modifier.fillMaxSize(),
        )
        if (imagem != null) {
            val alfa = remember(imagem) { Animatable(if (musica != null && capasJaVistas.contains(musica.id)) 1f else 0f) }
            LaunchedEffect(imagem) {
                alfa.animateTo(1f, tween(220))
                musica?.let { capasJaVistas.add(it.id) }
            }
            Image(
                bitmap = imagem,
                contentDescription = descricao,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = alfa.value },
            )
        }
    }
}

/** Faixas cuja capa já apareceu uma vez: não repetem o fade ao rolar a lista de volta. */
private val capasJaVistas: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

private val TONS_CAPA = listOf(
    Color(0xFF4A2E22), Color(0xFF2D3B34), Color(0xFF2B3346), Color(0xFF4A2B30),
    Color(0xFF3B3352), Color(0xFF4B3D1F), Color(0xFF23393E), Color(0xFF3A3431),
)

@Composable
fun CapaGerada(semente: String, rotulo: String, modifier: Modifier = Modifier) {
    val fundo = TONS_CAPA[abs(semente.lowercase().hashCode()) % TONS_CAPA.size]
    val sulco = lerp(fundo, Color.White, 0.10f)
    val tinta = lerp(fundo, Color.White, 0.62f)
    val iniciais = remember(rotulo) {
        rotulo.split(' ', '-', '_').filter { it.firstOrNull()?.isLetterOrDigit() == true }
            .take(2).joinToString("") { it.first().uppercase() }
    }
    BoxWithConstraints(modifier.background(fundo)) {
        val lado = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val centro = Offset(size.width * 1.02f, size.height * 1.02f)
            val passo = size.minDimension * 0.085f
            for (i in 3..11) {
                drawCircle(sulco, radius = passo * i, center = centro, style = Stroke(width = size.minDimension * 0.012f))
            }
        }
        if (lado >= 40.dp && iniciais.isNotEmpty()) {
            val tamanho = with(LocalDensity.current) { (lado * 0.30f).toSp() }
            Text(
                iniciais,
                color = tinta,
                fontFamily = ArchivoExpandida,
                fontWeight = FontWeight.Black,
                fontSize = tamanho,
                lineHeight = tamanho,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.align(Alignment.TopStart).padding(lado * 0.1f),
            )
        }
    }
}

/** Capa de playlist: mosaico 2×2 com capas de álbuns diferentes, ou uma capa só quando não há variedade. */
@Composable
fun MosaicoCapas(musicas: List<Musica>, nome: String, modifier: Modifier = Modifier, forma: Shape = MaterialTheme.shapes.small) {
    val proprias = LocalCapasProprias.current
    val distintas = remember(musicas, proprias) {
        musicas.filter { it.temCapa || it.id in proprias }.distinctBy { it.album.ifBlank { it.id } }.take(4)
    }
    Box(modifier.clip(forma).aspectRatio(1f)) {
        when {
            distintas.size >= 4 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    CapaArte(distintas[0], Modifier.weight(1f).fillMaxSize(), forma = RectangleShape)
                    CapaArte(distintas[1], Modifier.weight(1f).fillMaxSize(), forma = RectangleShape)
                }
                Row(Modifier.weight(1f)) {
                    CapaArte(distintas[2], Modifier.weight(1f).fillMaxSize(), forma = RectangleShape)
                    CapaArte(distintas[3], Modifier.weight(1f).fillMaxSize(), forma = RectangleShape)
                }
            }
            musicas.isNotEmpty() -> CapaArte(distintas.firstOrNull() ?: musicas.first(), Modifier.fillMaxSize(), forma = RectangleShape)
            else -> CapaGerada(nome, nome, Modifier.fillMaxSize())
        }
    }
}
