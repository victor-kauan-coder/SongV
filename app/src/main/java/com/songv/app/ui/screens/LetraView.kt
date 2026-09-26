package com.songv.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.songv.app.data.ExibicaoTraducao
import com.songv.app.letra.TradutorLetra
import com.songv.app.letra.indiceLinhaAtual
import com.songv.app.model.Letra
import com.songv.app.model.LinhaLetra
import com.songv.app.model.TipoLetra
import com.songv.app.ui.components.BotaoPilula
import com.songv.app.ui.theme.EstilosSongV
import com.songv.app.ui.theme.LocalCoresSongV
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance

private const val POSICAO_ALVO = 0.30f // a linha ativa fica a 30% da altura, sobrando espaço para as próximas

/**
 * A letra como legenda: a linha ativa em branco pleno, as outras recuadas; a tradução (e a
 * romanização) logo abaixo de cada linha, como uma legenda bilíngue de cinema.
 *
 * @param posicaoMs posição do player já somada ao ajuste de sincronia da faixa.
 */
@Composable
fun LetraView(
    letra: Letra,
    posicaoMs: () -> Long,
    traducao: TradutorLetra.Traducao?,
    exibicao: ExibicaoTraducao,
    mostrarRomanizacao: Boolean,
    escala: Float,
    onBuscar: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val linhas = letra.linhas
    val sincronizada = letra.tipo == TipoLetra.SINCRONIZADA
    val temIntro = sincronizada && (linhas.firstOrNull()?.tempoMs ?: 0L) > 2_500
    val deslocamentoItens = if (temIntro) 1 else 0

    val pos = posicaoMs()
    val atual = if (sincronizada) indiceLinhaAtual(linhas, pos) else -1

    val estado = rememberLazyListState()
    var seguindo by remember { mutableStateOf(true) }
    var ultimoToque by remember { mutableLongStateOf(0L) }

    // O usuário arrastou a letra: para de seguir por alguns segundos.
    LaunchedEffect(estado) {
        estado.interactionSource.interactions.filterIsInstance<DragInteraction.Start>().collect {
            seguindo = false
            ultimoToque = System.currentTimeMillis()
        }
    }
    LaunchedEffect(seguindo, ultimoToque) {
        if (!seguindo) {
            delay(5_000)
            if (!estado.isScrollInProgress) seguindo = true
        }
    }

    BoxWithConstraints(modifier) {
        val alturaPx = with(LocalDensity.current) { maxHeight.toPx() }
        val alvoPx = alturaPx * POSICAO_ALVO

        val indiceItem = if (atual < 0) 0 else atual + deslocamentoItens
        LaunchedEffect(indiceItem, seguindo, linhas, alturaPx) {
            if (!sincronizada || !seguindo || alturaPx <= 0f) return@LaunchedEffect
            val info = estado.layoutInfo.visibleItemsInfo.firstOrNull { it.index == indiceItem }
            if (info != null) {
                val delta = (info.offset - estado.layoutInfo.viewportStartOffset) - alvoPx
                estado.animateScrollBy(delta, tween(520, easing = FastOutSlowInEasing))
            } else {
                estado.scrollToItem(indiceItem)
            }
        }

        LazyColumn(
            state = estado,
            modifier = Modifier
                .fillMaxSize()
                // As linhas somem suavemente nas bordas, em vez de serem cortadas secas.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.07f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = if (sincronizada) maxHeight * POSICAO_ALVO else 16.dp + contentPadding.calculateTopPadding(),
                bottom = if (sincronizada) maxHeight * (1 - POSICAO_ALVO) else 48.dp + contentPadding.calculateBottomPadding(),
            ),
        ) {
            if (temIntro) {
                item(key = "intro") {
                    val inicio = linhas.first().tempoMs ?: 0L
                    PontosDePausa(
                        ativo = atual < 0,
                        progresso = { (posicaoMs().toFloat() / inicio).coerceIn(0f, 1f) },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            if (!sincronizada) {
                item(key = "aviso") {
                    Text(
                        "Letra sem sincronia",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            itemsIndexed(linhas, key = { i, _ -> "l$i" }) { i, linha ->
                if (sincronizada && linha.texto.isBlank()) {
                    val inicio = linha.tempoMs ?: 0L
                    val fim = linhas.getOrNull(i + 1)?.tempoMs ?: (inicio + 4_000)
                    PontosDePausa(
                        ativo = i == atual,
                        progresso = { ((posicaoMs() - inicio).toFloat() / (fim - inicio).coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LinhaDeLegenda(
                        linha = linha,
                        estado = when {
                            !sincronizada -> EstadoLinha.LEITURA
                            i == atual -> EstadoLinha.ATIVA
                            i < atual -> EstadoLinha.PASSADA
                            else -> EstadoLinha.FUTURA
                        },
                        traducao = traducao?.linhas?.getOrNull(i)?.takeUnless { it.equals(linha.texto.trim(), ignoreCase = true) },
                        romanizacao = if (mostrarRomanizacao) traducao?.romanizacao?.getOrNull(i) else null,
                        soTraducao = exibicao == ExibicaoTraducao.SO_TRADUCAO,
                        escala = escala,
                        onClick = linha.tempoMs?.let { t -> { onBuscar(t); seguindo = true } },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = sincronizada && !seguindo,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + contentPadding.calculateBottomPadding()),
        ) {
            BotaoPilula("Voltar à linha atual", onClick = { seguindo = true }, icone = Icons.Rounded.MyLocation)
        }
    }
}

private enum class EstadoLinha { ATIVA, PASSADA, FUTURA, LEITURA }

@Composable
private fun LinhaDeLegenda(
    linha: LinhaLetra,
    estado: EstadoLinha,
    traducao: String?,
    romanizacao: String?,
    soTraducao: Boolean,
    escala: Float,
    onClick: (() -> Unit)?,
) {
    val cores = LocalCoresSongV.current
    val alfa by animateFloatAsState(
        when (estado) {
            EstadoLinha.ATIVA -> 1f
            EstadoLinha.LEITURA -> 0.92f
            EstadoLinha.FUTURA -> 0.46f
            EstadoLinha.PASSADA -> 0.30f
        },
        tween(320),
        label = "alfaLinha",
    )
    val tamanho by animateFloatAsState(
        if (estado == EstadoLinha.ATIVA || estado == EstadoLinha.LEITURA) 1f else 0.965f,
        spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "escalaLinha",
    )
    val corLegenda = lerp(cores.sinalNoPalco, Color.White, 0.42f)
    val base = EstilosSongV.letra.let {
        if (estado == EstadoLinha.LEITURA) it.copy(fontSize = it.fontSize * 0.82f, lineHeight = it.lineHeight * 0.82f) else it
    }
    val estiloPrincipal = base.copy(fontSize = base.fontSize * escala, lineHeight = base.lineHeight * escala)
    val estiloLegenda = EstilosSongV.traducao.let { it.copy(fontSize = it.fontSize * escala, lineHeight = it.lineHeight * escala) }

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Pular para esta linha", onClick = onClick) else Modifier)
            .padding(vertical = 9.dp)
            .graphicsLayer {
                alpha = alfa
                scaleX = tamanho
                scaleY = tamanho
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        if (soTraducao && traducao != null) {
            Text(traducao, style = estiloPrincipal, color = Color.White)
            Text(linha.texto, style = estiloLegenda, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        } else {
            Text(linha.texto, style = estiloPrincipal, color = Color.White)
            if (romanizacao != null && !romanizacao.equals(linha.texto, ignoreCase = true)) {
                Text(
                    romanizacao,
                    style = estiloLegenda.copy(fontStyle = FontStyle.Italic, letterSpacing = 0.01.em),
                    color = Color.White.copy(alpha = 0.66f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (traducao != null) {
                Text(traducao, style = estiloLegenda, color = corLegenda, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Três pontos que se acendem conforme a introdução / o trecho instrumental avança. */
@Composable
private fun PontosDePausa(ativo: Boolean, progresso: () -> Float, modifier: Modifier = Modifier) {
    val alfa by animateFloatAsState(if (ativo) 1f else 0.3f, tween(300), label = "alfaPausa")
    Canvas(modifier.width(64.dp).height(20.dp).graphicsLayer { alpha = alfa }) {
        val p = if (ativo) progresso() else 0f
        val raio = size.height * 0.28f
        for (i in 0..2) {
            val acesa = ((p * 3f) - i).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = 0.3f + 0.7f * acesa),
                radius = raio * (0.85f + 0.3f * acesa),
                center = Offset(raio + i * size.width / 3f, size.height / 2),
            )
        }
    }
}

/** Letra ausente: explica e oferece buscar online ou importar um .lrc. */
@Composable
fun SemLetra(
    buscando: Boolean,
    buscaOnlineHabilitada: Boolean,
    onBuscarOnline: () -> Unit,
    onImportar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Esta faixa não tem letra", style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (buscaOnlineHabilitada) "Procure uma letra sincronizada na LRCLIB ou importe um arquivo .lrc do aparelho."
            else "Importe um arquivo .lrc do aparelho. A busca online está desligada nas configurações.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (buscaOnlineHabilitada) {
            if (buscando) {
                CircularProgressIndicator(color = LocalCoresSongV.current.sinalNoPalco, modifier = Modifier.size(32.dp))
            } else {
                BotaoPilula("Buscar letra online", onBuscarOnline, icone = Icons.Rounded.CloudDownload)
            }
            Spacer(Modifier.height(10.dp))
        }
        Surface(onClick = onImportar, color = Color.White.copy(alpha = 0.1f), contentColor = Color.White, shape = MaterialTheme.shapes.extraLarge) {
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importar arquivo .lrc", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun CarregandoLetra(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}
