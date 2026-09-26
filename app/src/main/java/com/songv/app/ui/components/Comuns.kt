package com.songv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import com.songv.app.ui.theme.EstilosSongV
import com.songv.app.ui.theme.LocalCoresSongV
import java.util.Locale

// =============================================================================
// Formatação
// =============================================================================

fun formatarTempo(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}

/** "1 h 12 min", "38 min" — para durações de álbuns e playlists. */
fun formatarDuracaoLonga(ms: Long): String {
    val minutos = (ms / 60_000).toInt()
    return if (minutos >= 60) "${minutos / 60} h ${minutos % 60} min" else "$minutos min"
}

fun plural(n: Int, singular: String, pluralForma: String = singular + "s") = "$n ${if (n == 1) singular else pluralForma}"

// =============================================================================
// Botões e estados
// =============================================================================

/** O botão laranja de play — a única superfície laranja grande do app. */
@Composable
fun BotaoPlay(
    tocando: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tamanho: Dp = 64.dp,
    carregando: Boolean = false,
    cor: Color = LocalCoresSongV.current.sinal,
    corIcone: Color = LocalCoresSongV.current.noSinal,
) {
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pressionado) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "escalaPlay")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = cor,
        contentColor = corIcone,
        interactionSource = interacao,
        modifier = modifier.size(tamanho).scale(escala),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (carregando) {
                CircularProgressIndicator(color = corIcone, strokeWidth = 2.5.dp, modifier = Modifier.size(tamanho * 0.42f))
            } else {
                Icon(
                    if (tocando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (tocando) "Pausar" else "Tocar",
                    modifier = Modifier.size(tamanho * 0.5f),
                )
            }
        }
    }
}

/**
 * Botão de modo (aleatório, repetir, letra…) com um "LED" embaixo quando ligado — como a luz
 * indicadora de um aparelho de som.
 */
@Composable
fun BotaoModo(
    icone: ImageVector,
    descricao: String,
    ativo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corAtiva: Color = LocalCoresSongV.current.sinal,
    corInativa: Color = LocalContentColor.current.copy(alpha = 0.72f),
) {
    val cor by animateColorAsState(if (ativo) corAtiva else corInativa, tween(180), label = "corModo")
    val led by animateFloatAsState(if (ativo) 1f else 0f, tween(180), label = "led")
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = "$descricao, ${if (ativo) "ligado" else "desligado"}" }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = null, tint = cor)
            Canvas(Modifier.size(4.dp).align(Alignment.BottomCenter).graphicsLayer { translationY = 14.dp.toPx(); alpha = led }) {
                drawCircle(corAtiva)
            }
        }
    }
}

/** Barras animadas que marcam a faixa tocando nas listas. Paradas quando pausado. */
@Composable
fun IndicadorTocando(tocando: Boolean, modifier: Modifier = Modifier, cor: Color = LocalCoresSongV.current.sinal) {
    val transicao = rememberInfiniteTransition(label = "eq")
    val fases = listOf(0, 180, 360).map { atraso ->
        transicao.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, delayMillis = atraso), RepeatMode.Reverse),
            label = "barra$atraso",
        )
    }
    Canvas(modifier.size(16.dp)) {
        val largura = size.width / 5f
        fases.forEachIndexed { i, fase ->
            val altura = size.height * (if (tocando) fase.value else 0.3f + 0.2f * i)
            drawRoundRect(
                cor,
                topLeft = Offset(i * largura * 2f, size.height - altura),
                size = Size(largura, altura),
                cornerRadius = CornerRadius(largura / 2),
            )
        }
    }
}

/** Barra de progresso fina que engrossa ao arrastar. Números tabulares para não "dançarem". */
@Composable
fun BarraProgresso(
    posicaoMs: Long,
    duracaoMs: Long,
    onBuscar: (Long) -> Unit,
    modifier: Modifier = Modifier,
    corAtiva: Color = LocalCoresSongV.current.sinal,
    corTrilho: Color = LocalContentColor.current.copy(alpha = 0.18f),
    corTexto: Color = LocalContentColor.current.copy(alpha = 0.68f),
    mostrarTempos: Boolean = true,
) {
    var arrastando by remember { mutableStateOf(false) }
    var fracaoArraste by remember { mutableFloatStateOf(0f) }
    val fracao = if (arrastando) fracaoArraste else if (duracaoMs > 0) (posicaoMs.toFloat() / duracaoMs).coerceIn(0f, 1f) else 0f
    val espessura by animateFloatAsState(if (arrastando) 8f else 4f, tween(150), label = "espessura")

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = "Progresso: ${formatarTempo(posicaoMs)} de ${formatarTempo(duracaoMs)}" }
                .pointerInput(duracaoMs) {
                    detectTapGestures { o -> if (duracaoMs > 0) onBuscar(((o.x / size.width).coerceIn(0f, 1f) * duracaoMs).toLong()) }
                }
                .pointerInput(duracaoMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> arrastando = true; fracaoArraste = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { if (duracaoMs > 0) onBuscar((fracaoArraste * duracaoMs).toLong()); arrastando = false },
                        onDragCancel = { arrastando = false },
                    ) { change, _ ->
                        change.consume()
                        fracaoArraste = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
        ) {
            val h = espessura.dp.toPx()
            val y = size.height / 2 - h / 2
            drawRoundRect(corTrilho, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(corAtiva, Offset(0f, y), Size(size.width * fracao, h), CornerRadius(h / 2))
            if (arrastando) drawCircle(corAtiva, radius = 8.dp.toPx(), center = Offset(size.width * fracao, size.height / 2))
        }
        if (mostrarTempos) {
            Row(Modifier.fillMaxWidth()) {
                val exibida = if (arrastando) (fracaoArraste * duracaoMs).toLong() else posicaoMs
                Text(formatarTempo(exibida), style = MaterialTheme.typography.labelMedium.merge(EstilosSongV.numeros), color = corTexto)
                Spacer(Modifier.weight(1f))
                Text(formatarTempo(duracaoMs), style = MaterialTheme.typography.labelMedium.merge(EstilosSongV.numeros), color = corTexto)
            }
        }
    }
}

// =============================================================================
// Listas
// =============================================================================

/**
 * Linha de faixa. A tocando ganha título em destaque e as barras animadas; faixas com letra
 * sincronizada mostram um selo discreto (é o diferencial do SongV).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinhaMusica(
    musica: Musica,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ativa: Boolean = false,
    tocando: Boolean = false,
    numero: Int? = null,
    mostrarCapa: Boolean = numero == null,
    subtitulo: String = musica.artista,
    onMenu: (() -> Unit)? = null,
    acessorio: (@Composable () -> Unit)? = null,
) {
    val corTitulo = if (ativa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = 16.dp, end = if (onMenu != null || acessorio != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mostrarCapa) {
            Box(contentAlignment = Alignment.Center) {
                CapaArte(musica, Modifier.size(48.dp))
                if (ativa) {
                    Box(Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                        IndicadorTocando(tocando)
                    }
                }
            }
        } else if (numero != null) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (ativa) {
                    IndicadorTocando(tocando)
                } else {
                    Text(
                        "$numero",
                        style = MaterialTheme.typography.titleSmall.merge(EstilosSongV.numeros),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                musica.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = corTitulo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (musica.tipoLetra == TipoLetra.SINCRONIZADA) {
                    Icon(
                        Icons.Rounded.Lyrics,
                        contentDescription = "Letra sincronizada",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).padding(end = 1.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        acessorio?.invoke()
        if (onMenu != null) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções de ${musica.titulo}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CabecalhoSecao(titulo: String, modifier: Modifier = Modifier, acao: String? = null, onAcao: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (acao != null && onAcao != null) {
            TextButton(onClick = onAcao, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(acao, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Estado vazio que explica o que fazer, não só "nada aqui". */
@Composable
fun EstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acao: String? = null,
    onAcao: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (acao != null && onAcao != null) {
            Spacer(Modifier.height(20.dp))
            BotaoPilula(acao, onAcao)
        }
    }
}

/** Botão de texto em pílula, preenchido com a cor de sinal (ação principal de uma tela). */
@Composable
fun BotaoPilula(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    preenchido: Boolean = true,
) {
    val cores = LocalCoresSongV.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (preenchido) cores.sinal else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (preenchido) cores.noSinal else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icone != null) {
                Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(texto, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Arraste horizontal para pular faixa (mini player). */
fun Modifier.arrasteHorizontal(onEsquerda: () -> Unit, onDireita: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -80.dp.toPx()) onEsquerda() else if (total > 80.dp.toPx()) onDireita()
        },
    ) { _, delta -> total += delta }
}
