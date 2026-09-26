package com.songv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.songv.app.ui.theme.*

// Este arquivo reúne os componentes de seleção de tema (grade de paletas + seletor de cor livre)
// reaproveitados dentro da tela de Configurações (ver ConfiguracoesScreen.kt). Não têm mais uma
// tela/diálogo próprio: a paleta de cores agora só aparece dentro de Configurações, não na Home.

internal data class InfoTema(val tema: TemaApp, val corPrimaria: Color, val corFundo: Color)

@Composable
internal fun GradeTemas(
    temaAtual: TemaApp,
    corPersonalizada: Color,
    onTemaClick: (TemaApp) -> Unit,
    onPersonalizadoClick: () -> Unit,
    altura: androidx.compose.ui.unit.Dp = 420.dp
) {
    val temas = listOf(
        InfoTema(TemaApp.CLARO, EsquemaClaroCores.primary, EsquemaClaroCores.background),
        InfoTema(TemaApp.ESCURO, EsquemaEscuroCores.primary, EsquemaEscuroCores.background),
        InfoTema(TemaApp.ROXO_DARK, EsquemaRoxoDarkCores.primary, EsquemaRoxoDarkCores.background),
        InfoTema(TemaApp.VERDE, EsquemaVerdeCores.primary, EsquemaVerdeCores.background),
        InfoTema(TemaApp.AZUL, EsquemaAzulCores.primary, EsquemaAzulCores.background),
        InfoTema(TemaApp.SUNSET, EsquemaSunsetCores.primary, EsquemaSunsetCores.background),
        InfoTema(TemaApp.ROSA, EsquemaRosaCores.primary, EsquemaRosaCores.background),
        InfoTema(TemaApp.AMBAR, EsquemaAmbarCores.primary, EsquemaAmbarCores.background),
        InfoTema(TemaApp.CIANO, EsquemaCianoCores.primary, EsquemaCianoCores.background),
        InfoTema(TemaApp.NOITE_DOURADA, EsquemaNoiteDouradaCores.primary, EsquemaNoiteDouradaCores.background),
        InfoTema(TemaApp.VIOLETA_PROFUNDO, EsquemaVioletaProfundoCores.primary, EsquemaVioletaProfundoCores.background),
        InfoTema(TemaApp.PRATA_MINIMAL, EsquemaPrataMinimalCores.primary, EsquemaPrataMinimalCores.background),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(altura),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(temas) { info ->
            ItemTemaGrade(
                label = info.tema.label,
                corPrimaria = info.corPrimaria,
                corFundo = info.corFundo,
                selecionado = info.tema == temaAtual,
                onClick = { onTemaClick(info.tema) }
            )
        }
        item {
            ItemTemaGrade(
                label = TemaApp.PERSONALIZADO.label,
                corPrimaria = corPersonalizada,
                corFundo = Color(0xFF0E0E14),
                selecionado = temaAtual == TemaApp.PERSONALIZADO,
                icone = Icons.Filled.Tune,
                onClick = onPersonalizadoClick
            )
        }
    }
}

@Composable
private fun ItemTemaGrade(
    label: String,
    corPrimaria: Color,
    corFundo: Color,
    selecionado: Boolean,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selecionado) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(corFundo)
                .border(width = 2.dp, color = corPrimaria, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                selecionado -> Icon(Icons.Filled.Check, contentDescription = "Selecionado", tint = corPrimaria)
                icone != null -> Icon(icone, contentDescription = null, tint = corPrimaria, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/**
 * Seletor de cor livre estilo HSV: um quadrado de saturação/valor (matiz fixa vinda do slider
 * embaixo) mais um slider horizontal de matiz (arco-íris completo). Arrastar em qualquer um dos
 * dois atualiza a cor em tempo real.
 */
@Composable
internal fun SeletorCorLivre(corInicial: Color, onCorEscolhida: (Color) -> Unit) {
    val hsvInicial = remember(corInicial) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(corInicial.toArgb(), hsv)
        hsv
    }
    var matiz by remember { mutableStateOf(hsvInicial[0]) }        // 0..360
    var saturacao by remember { mutableStateOf(hsvInicial[1]) }    // 0..1
    var valor by remember { mutableStateOf(hsvInicial[2].coerceAtLeast(0.4f)) } // 0..1, nunca deixa ficar preto

    val corAtual = remember(matiz, saturacao, valor) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(matiz, saturacao, valor)))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(corAtual)
                    .border(width = 2.dp, color = Color.White.copy(alpha = 0.3f), shape = CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text("Arraste para escolher sua cor", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(16.dp))

        QuadradoSaturacaoValor(
            matiz = matiz,
            saturacao = saturacao,
            valor = valor,
            onAlterado = { s, v -> saturacao = s; valor = v }
        )

        Spacer(Modifier.height(16.dp))

        SliderMatiz(matiz = matiz, onMatizAlterada = { matiz = it })

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onCorEscolhida(corAtual) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aplicar cor")
        }
    }
}

@Composable
private fun QuadradoSaturacaoValor(
    matiz: Float,
    saturacao: Float,
    valor: Float,
    onAlterado: (saturacao: Float, valor: Float) -> Unit
) {
    val corMatizPura = remember(matiz) { Color(android.graphics.Color.HSVToColor(floatArrayOf(matiz, 1f, 1f))) }
    var tamanhoContainerPx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val densidade = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(listOf(Color.White, corMatizPura))
            )
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
            )
            .onSizeChanged { tamanhoContainerPx = it }
            .pointerInput(matiz) {
                fun processar(offset: Offset, tamanho: androidx.compose.ui.unit.IntSize) {
                    val s = (offset.x / tamanho.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / tamanho.height).coerceIn(0f, 1f)
                    onAlterado(s, v)
                }
                detectTapGestures { offset -> processar(offset, size) }
            }
            .pointerInput(matiz) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onAlterado(s, v)
                }
            }
    ) {
        // Indicador de posição atual — usa o tamanho REAL do container (capturado via
        // onSizeChanged) em vez do próprio tamanho do indicador, que era o bug original aqui:
        // graphicsLayer{} sozinho só enxerga o size do elemento que ele modifica.
        val xFrac = saturacao
        val yFrac = 1f - valor
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = (xFrac * tamanhoContainerPx.width) - with(densidade) { 10.dp.toPx() }
                    translationY = (yFrac * tamanhoContainerPx.height) - with(densidade) { 10.dp.toPx() }
                }
                .size(20.dp)
                .clip(CircleShape)
                .border(width = 2.dp, color = Color.White, shape = CircleShape)
        )
    }
}

@Composable
private fun SliderMatiz(matiz: Float, onMatizAlterada: (Float) -> Unit) {
    val coresArcoIris = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }

    var larguraContainerPx by remember { mutableStateOf(0) }
    val densidade = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(coresArcoIris))
            .onSizeChanged { larguraContainerPx = it.width }
            .pointerInput(Unit) {
                fun processar(x: Float, largura: Int) {
                    val fracao = (x / largura).coerceIn(0f, 1f)
                    onMatizAlterada(fracao * 360f)
                }
                detectTapGestures { offset -> processar(offset.x, size.width) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val fracao = (change.position.x / size.width).coerceIn(0f, 1f)
                    onMatizAlterada(fracao * 360f)
                }
            }
    ) {
        val xFrac = matiz / 360f
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = (xFrac * larguraContainerPx) - with(densidade) { 4.dp.toPx() } }
                .width(8.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
        )
    }
}
