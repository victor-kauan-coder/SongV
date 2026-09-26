package com.songv.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic // This one is AutoMirrored
import androidx.compose.material.icons.filled.KeyboardArrowDown      // This one is Standard
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.songv.app.model.LinhaLetra
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import com.songv.app.player.EstadoPlayer
import com.songv.app.player.ModoRepeticao
import com.songv.app.ui.formatarTempo
import kotlinx.coroutines.launch

// =============================================================================
// ESTADOS DA TELA DO PLAYER
// =============================================================================
private enum class PainelPlayer { CAPA, LETRA_PREVIEW, LETRA_FULL }

// =============================================================================
// TELA PRINCIPAL DO PLAYER
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    estado: EstadoPlayer,
    onVoltar: () -> Unit,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoritoClick: (String) -> Unit,
    onEmbaralharClick: () -> Unit,
    onRepeticaoClick: () -> Unit,
    onFilaClick: () -> Unit,
    onTraduzirLetra: (Musica) -> Unit,
    onAlternarTraducao: () -> Unit,
    onEscolherIdiomaTraducao: (String) -> Unit
) {
    val musica = estado.faixaAtual
    var painelAtual by remember { mutableStateOf(PainelPlayer.CAPA) }

    // Extrai paleta de cores da capa para o fundo dinamico
    val coresPaletas = remember(musica?.capa) {
        extrairPaleta(musica?.capa)
    }

    val corDominante = coresPaletas.first
    val corVibrante = coresPaletas.second
    val corEscura = coresPaletas.third

    // Gradiente FOSCO (opaco) baseado na paleta da capa
    val brushFundo = remember(corDominante, corEscura) {
        Brush.verticalGradient(
            colors = listOf(
                corDominante,
                corDominante.darken(0.3f),
                corEscura.darken(0.2f),
                Color.Black
            ),
            startY = 0f,
            endY = 2500f
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown, // Change this line
                            contentDescription = "Voltar",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    if (musica != null) {
                        val favoritada = musica.id in estado.favoritos
                        IconButton(onClick = { onFavoritoClick(musica.id) }) {
                            Icon(
                                if (favoritada) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (favoritada) "Remover dos favoritos" else "Favoritar",
                                tint = if (favoritada) corVibrante else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(onClick = onFilaClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Ver fila",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (musica == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(brushFundo),
                contentAlignment = Alignment.Center
            ) {
                Text("Nada tocando ainda.", color = Color.White.copy(alpha = 0.6f))
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brushFundo)
                .padding(padding)
        ) {
            Crossfade(
                targetState = painelAtual,
                animationSpec = tween(350),
                label = "crossfade_painel"
            ) { painel ->
                when (painel) {
                    PainelPlayer.CAPA -> {
                        PainelCapa(
                            musica = musica,
                            estado = estado,
                            corVibrante = corVibrante,
                            onPlayPause = onPlayPause,
                            onProxima = onProxima,
                            onAnterior = onAnterior,
                            onSeek = onSeek,
                            onEmbaralharClick = onEmbaralharClick,
                            onRepeticaoClick = onRepeticaoClick,
                            onVerLetra = {
                                painelAtual = if (musica.tipoLetra != TipoLetra.AUSENTE)
                                    PainelPlayer.LETRA_PREVIEW else PainelPlayer.CAPA
                            }
                        )
                    }
                    PainelPlayer.LETRA_PREVIEW -> {
                        PainelLetraPreview(
                            musica = musica,
                            estado = estado,
                            corVibrante = corVibrante,
                            onPlayPause = onPlayPause,
                            onProxima = onProxima,
                            onAnterior = onAnterior,
                            onSeek = onSeek,
                            onVerLetraFull = { painelAtual = PainelPlayer.LETRA_FULL },
                            onVoltarCapa = { painelAtual = PainelPlayer.CAPA }
                        )
                    }
                    PainelPlayer.LETRA_FULL -> {
                        PainelLetraFull(
                            musica = musica,
                            estado = estado,
                            corVibrante = corVibrante,
                            onPlayPause = onPlayPause,
                            onProxima = onProxima,
                            onAnterior = onAnterior,
                            onSeek = onSeek,
                            onVoltar = { painelAtual = PainelPlayer.LETRA_PREVIEW },
                            onTraduzirClick = { onTraduzirLetra(musica) },
                            onAlternarTraducaoClick = onAlternarTraducao,
                            onEscolherIdioma = onEscolherIdiomaTraducao
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// PAINEL DE CAPA (modo principal)
// =============================================================================
@Composable
private fun PainelCapa(
    musica: Musica,
    estado: EstadoPlayer,
    corVibrante: Color,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit,
    onSeek: (Long) -> Unit,
    onEmbaralharClick: () -> Unit,
    onRepeticaoClick: () -> Unit,
    onVerLetra: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // Capa grande com sombra
        CapaComEfeito(musica = musica, modifier = Modifier.fillMaxWidth(0.82f))

        Spacer(Modifier.height(36.dp))

        // Info da faixa
        InfoFaixaProfissional(musica = musica, corVibrante = corVibrante)

        Spacer(Modifier.height(28.dp))

        // Barra de progresso
        BarraProgressoProfissional(
            posicaoMs = estado.posicaoMs,
            duracaoMs = musica.duracaoMs,
            corAtiva = corVibrante,
            onSeek = onSeek
        )

        Spacer(Modifier.height(20.dp))

        // Controles principais
        ControlesPlayerProfissional(
            tocando = estado.tocando,
            embaralhado = estado.embaralhado,
            modoRepeticao = estado.modoRepeticao,
            corDestaque = corVibrante,
            onPlayPause = onPlayPause,
            onProxima = onProxima,
            onAnterior = onAnterior,
            onEmbaralharClick = onEmbaralharClick,
            onRepeticaoClick = onRepeticaoClick
        )

        Spacer(Modifier.height(28.dp))

        // Preview de letra (se houver)
        if (musica.tipoLetra != TipoLetra.AUSENTE) {
            LetraPreviewCard(
                musica = musica,
                posicaoMs = estado.posicaoMs,
                corVibrante = corVibrante,
                onClick = onVerLetra
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// =============================================================================
// PAINEL DE LETRA PREVIEW (com animacao fluida igual ao full)
// =============================================================================
@Composable
private fun PainelLetraPreview(
    musica: Musica,
    estado: EstadoPlayer,
    corVibrante: Color,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit,
    onSeek: (Long) -> Unit,
    onVerLetraFull: () -> Unit,
    onVoltarCapa: () -> Unit
) {
    val listState = rememberLazyListState()
    val escopo = rememberCoroutineScope()
    var alturaContainerPx by remember { mutableStateOf(0) }

    val indiceAtual = remember(estado.posicaoMs, musica.letra) {
        if (musica.tipoLetra == TipoLetra.SINCRONIZADA) {
            musica.letra.indexOfLast { it.tempoMs != null && it.tempoMs <= estado.posicaoMs }
                .coerceAtLeast(0)
        } else 0
    }

    // Scroll automatico fluido igual ao modo full
    LaunchedEffect(indiceAtual, alturaContainerPx) {
        if (alturaContainerPx == 0) return@LaunchedEffect
        escopo.launch {
            val offsetParaCentralizar = -(alturaContainerPx / 2) + 40
            listState.animateScrollToItem(
                index = indiceAtual,
                scrollOffset = offsetParaCentralizar
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Mini capa + info no topo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onVoltarCapa),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapaThumbPlayer(musica = musica, tamanho = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    musica.titulo,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    musica.artista,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onVoltarCapa) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown, // Change this line
                    contentDescription = "Voltar",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Card de preview de letra COM animacao fluida
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D0D0D)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Gradiente sutil no card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    corVibrante.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        "Lyrics preview",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                    )

                    // Lista de letra com animacao fluida igual ao full
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onSizeChanged { alturaContainerPx = it.height },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 60.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        itemsIndexed(musica.letra) { index, linha ->
                            val ativa = if (musica.tipoLetra == TipoLetra.SINCRONIZADA) {
                                index == indiceAtual
                            } else false

                            LinhaLetraAnimada(
                                linha = linha,
                                ativa = ativa,
                                corVibrante = corVibrante,
                                onClick = { linha.tempoMs?.let(onSeek) },
                                tamanhoFonteAtiva = 22.sp,
                                tamanhoFonteInativa = 17.sp
                            )
                        }
                    }

                    // Botao "Show lyrics"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .clickable(onClick = onVerLetraFull),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Show lyrics",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Controles compactos
        ControlesCompactos(
            tocando = estado.tocando,
            corDestaque = corVibrante,
            onPlayPause = onPlayPause,
            onProxima = onProxima,
            onAnterior = onAnterior
        )

        Spacer(Modifier.height(8.dp))

        BarraProgressoCompacta(
            posicaoMs = estado.posicaoMs,
            duracaoMs = musica.duracaoMs,
            corAtiva = corVibrante,
            onSeek = onSeek
        )
    }
}

// =============================================================================
// PAINEL DE LETRA FULL (tela cheia)
// =============================================================================
@Composable
private fun PainelLetraFull(
    musica: Musica,
    estado: EstadoPlayer,
    corVibrante: Color,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit,
    onSeek: (Long) -> Unit,
    onVoltar: () -> Unit,
    onTraduzirClick: () -> Unit,
    onAlternarTraducaoClick: () -> Unit,
    onEscolherIdioma: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val escopo = rememberCoroutineScope()
    var alturaContainerPx by remember { mutableStateOf(0) }
    var menuIdiomaAberto by remember { mutableStateOf(false) }

    val indiceAtual = remember(estado.posicaoMs, musica.letra) {
        if (musica.tipoLetra == TipoLetra.SINCRONIZADA) {
            musica.letra.indexOfLast { it.tempoMs != null && it.tempoMs <= estado.posicaoMs }
                .coerceAtLeast(0)
        } else 0
    }

    val chaveTraducao = remember(musica.id, estado.idiomaTraducao) { estado.chaveTraducao(musica.id) }
    val linhasTraduzidas = estado.letrasTraduzidas[chaveTraducao]
    val exibindoTraducao = estado.mostrandoTraducao && linhasTraduzidas != null

    LaunchedEffect(indiceAtual, alturaContainerPx) {
        if (alturaContainerPx == 0) return@LaunchedEffect
        escopo.launch {
            val offsetParaCentralizar = -(alturaContainerPx / 2) + 50
            listState.animateScrollToItem(
                index = indiceAtual,
                scrollOffset = offsetParaCentralizar
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVoltar) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown, // Change this line
                    contentDescription = "Voltar",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    musica.titulo,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    musica.artista,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Box {
                IconButton(onClick = { menuIdiomaAberto = true }) {
                    Icon(
                        Icons.Filled.Translate,
                        contentDescription = "Traduzir letra",
                        tint = if (exibindoTraducao) corVibrante else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                MenuTraducao(
                    expandido = menuIdiomaAberto,
                    idiomaAtual = estado.idiomaTraducao,
                    exibindoTraducao = exibindoTraducao,
                    temTraducaoEmCache = linhasTraduzidas != null,
                    onFechar = { menuIdiomaAberto = false },
                    onEscolherIdioma = { codigo ->
                        menuIdiomaAberto = false
                        onEscolherIdioma(codigo)
                        onTraduzirClick()
                    },
                    onAlternarExibicao = {
                        menuIdiomaAberto = false
                        onAlternarTraducaoClick()
                    }
                )
            }
        }

        if (estado.traduzindo) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
                color = corVibrante
            )
        }

        estado.erroTraducao?.let { mensagem ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Text(
                    mensagem,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Letra em tela cheia
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 140.dp)
                .onSizeChanged { alturaContainerPx = it.height },
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(musica.letra) { index, linha ->
                val ativa = if (musica.tipoLetra == TipoLetra.SINCRONIZADA) {
                    index == indiceAtual
                } else false

                LinhaLetraAnimada(
                    linha = linha,
                    ativa = ativa,
                    corVibrante = corVibrante,
                    onClick = { linha.tempoMs?.let(onSeek) },
                    tamanhoFonteAtiva = 26.sp,
                    tamanhoFonteInativa = 22.sp,
                    textoAlternativo = if (exibindoTraducao) linhasTraduzidas?.getOrNull(index) else null
                )
            }
        }

        // Controles overlay na base
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            BarraProgressoCompacta(
                posicaoMs = estado.posicaoMs,
                duracaoMs = musica.duracaoMs,
                corAtiva = corVibrante,
                onSeek = onSeek
            )

            Spacer(Modifier.height(8.dp))

            ControlesCompactos(
                tocando = estado.tocando,
                corDestaque = corVibrante,
                onPlayPause = onPlayPause,
                onProxima = onProxima,
                onAnterior = onAnterior
            )
        }
    }
}

// =============================================================================
// MENU DE TRADUÇÃO DE LETRA
// =============================================================================
@Composable
private fun MenuTraducao(
    expandido: Boolean,
    idiomaAtual: String,
    exibindoTraducao: Boolean,
    temTraducaoEmCache: Boolean,
    onFechar: () -> Unit,
    onEscolherIdioma: (String) -> Unit,
    onAlternarExibicao: () -> Unit
) {
    DropdownMenu(expanded = expandido, onDismissRequest = onFechar) {
        if (temTraducaoEmCache) {
            DropdownMenuItem(
                text = { Text(if (exibindoTraducao) "Ver letra original" else "Ver tradução") },
                leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                onClick = onAlternarExibicao
            )
            HorizontalDivider()
        }
        Text(
            "Traduzir para:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        com.songv.app.data.IDIOMAS_TRADUCAO.forEach { idioma ->
            DropdownMenuItem(
                text = { Text(idioma.nome) },
                trailingIcon = {
                    if (idioma.codigo == idiomaAtual) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                onClick = { onEscolherIdioma(idioma.codigo) }
            )
        }
    }
}

// =============================================================================
// COMPONENTE DE LINHA DE LETRA ANIMADA (reutilizado em preview e full)
// =============================================================================
@Composable
private fun LinhaLetraAnimada(
    linha: LinhaLetra,
    ativa: Boolean,
    corVibrante: Color,
    onClick: () -> Unit,
    tamanhoFonteAtiva: androidx.compose.ui.unit.TextUnit,
    tamanhoFonteInativa: androidx.compose.ui.unit.TextUnit,
    textoAlternativo: String? = null
) {
    val escala by animateFloatAsState(
        targetValue = if (ativa) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "escalaLetra"
    )

    val cor by animateColorAsState(
        targetValue = if (ativa) Color.White else Color.White.copy(alpha = 0.4f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "corLetra"
    )

    val alpha by animateFloatAsState(
        targetValue = if (ativa) 1f else 0.55f,
        animationSpec = tween(300),
        label = "alphaLetra"
    )

    Text(
        text = textoAlternativo ?: linha.texto,
        color = cor,
        fontSize = if (ativa) tamanhoFonteAtiva else tamanhoFonteInativa,
        fontWeight = if (ativa) FontWeight.Bold else FontWeight.SemiBold,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
                this.alpha = alpha
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    )
}

// =============================================================================
// COMPONENTES VISUAIS
// =============================================================================

@Composable
private fun CapaComEfeito(musica: Musica, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (musica.capa != null) {
            Image(
                bitmap = musica.capa.asImageBitmap(),
                contentDescription = "Capa de ${musica.titulo}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoFaixaProfissional(musica: Musica, corVibrante: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            musica.titulo,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            musica.artista,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraProgressoProfissional(
    posicaoMs: Long,
    duracaoMs: Long,
    corAtiva: Color,
    onSeek: (Long) -> Unit
) {
    var arrastando by remember { mutableStateOf(false) }
    var valorArraste by remember { mutableStateOf(0f) }

    val progresso = if (duracaoMs > 0) {
        if (arrastando) valorArraste else (posicaoMs.toFloat() / duracaoMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column {
        Slider(
            value = progresso,
            onValueChange = {
                arrastando = true
                valorArraste = it
            },
            onValueChangeFinished = {
                onSeek((valorArraste * duracaoMs).toLong())
                arrastando = false
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = corAtiva,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatarTempo(if (arrastando) (valorArraste * duracaoMs).toLong() else posicaoMs),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                formatarTempo(duracaoMs),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BarraProgressoCompacta(
    posicaoMs: Long,
    duracaoMs: Long,
    corAtiva: Color,
    onSeek: (Long) -> Unit
) {
    var arrastando by remember { mutableStateOf(false) }
    var valorArraste by remember { mutableStateOf(0f) }

    val progresso = if (duracaoMs > 0) {
        if (arrastando) valorArraste else (posicaoMs.toFloat() / duracaoMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            formatarTempo(if (arrastando) (valorArraste * duracaoMs).toLong() else posicaoMs),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )

        Slider(
            value = progresso,
            onValueChange = {
                arrastando = true
                valorArraste = it
            },
            onValueChangeFinished = {
                onSeek((valorArraste * duracaoMs).toLong())
                arrastando = false
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = corAtiva,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )

        Text(
            formatarTempo(duracaoMs),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ControlesPlayerProfissional(
    tocando: Boolean,
    embaralhado: Boolean,
    modoRepeticao: ModoRepeticao,
    corDestaque: Color,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit,
    onEmbaralharClick: () -> Unit,
    onRepeticaoClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onEmbaralharClick) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Embaralhar",
                tint = if (embaralhado) corDestaque else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(
            onClick = onAnterior,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = corDestaque.copy(alpha = 0.3f)
                ),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.fillMaxSize(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White
                )
            ) {
                Icon(
                    if (tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (tocando) "Pausar" else "Tocar",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        IconButton(
            onClick = onProxima,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Próxima",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(onClick = onRepeticaoClick) {
            Icon(
                if (modoRepeticao == ModoRepeticao.REPETIR_UMA) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Repetir",
                tint = if (modoRepeticao != ModoRepeticao.DESLIGADO) corDestaque else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ControlesCompactos(
    tocando: Boolean,
    corDestaque: Color,
    onPlayPause: () -> Unit,
    onProxima: () -> Unit,
    onAnterior: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAnterior) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(8.dp, CircleShape, ambientColor = corDestaque.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.fillMaxSize(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
            ) {
                Icon(
                    if (tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (tocando) "Pausar" else "Tocar",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        IconButton(onClick = onProxima) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Próxima",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun LetraPreviewCard(
    musica: Musica,
    posicaoMs: Long,
    corVibrante: Color,
    onClick: () -> Unit
) {
    val indiceAtual = if (musica.tipoLetra == TipoLetra.SINCRONIZADA) {
        musica.letra.indexOfLast {
            it.tempoMs != null && it.tempoMs <= posicaoMs
        }.coerceAtLeast(0)
    } else 0

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D0D0D)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                corVibrante.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    "Lyrics preview",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(12.dp))

                val inicio = (indiceAtual - 1).coerceAtLeast(0)
                val fim = (inicio + 3).coerceAtMost(musica.letra.size)

                if (musica.letra.isNotEmpty() && inicio < musica.letra.size) {
                    val fimSeguro = fim.coerceAtMost(musica.letra.size)
                    musica.letra.subList(inicio, fimSeguro).forEachIndexed { idx, linha ->
                        val isAtual = (inicio + idx) == indiceAtual
                        Text(
                            text = linha.texto,
                            color = if (isAtual) Color.White else Color.White.copy(alpha = 0.45f),
                            fontSize = if (isAtual) 18.sp else 15.sp,
                            fontWeight = if (isAtual) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Show lyrics",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CapaThumbPlayer(musica: Musica, tamanho: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        if (musica.capa != null) {
            Image(
                bitmap = musica.capa.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(tamanho / 2)
            )
        }
    }
}

// =============================================================================
// FUNCOES UTILITARIAS
// =============================================================================

private fun extrairPaleta(bitmap: Bitmap?): Triple<Color, Color, Color> {
    if (bitmap == null) {
        return Triple(
            Color(0xFF3D1B47),
            Color(0xFF9D4EDD),
            Color(0xFF0E0E14)
        )
    }

    return try {
        val palette = Palette.from(bitmap).generate()
        val dominante = palette.dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF3D1B47)
        val vibrante = palette.vibrantSwatch?.rgb?.let { Color(it) }
            ?: palette.lightVibrantSwatch?.rgb?.let { Color(it) }
            ?: dominante
        val escura = palette.darkMutedSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF0E0E14)

        Triple(dominante, vibrante, escura)
    } catch (e: Exception) {
        Triple(
            Color(0xFF3D1B47),
            Color(0xFF9D4EDD),
            Color(0xFF0E0E14)
        )
    }
}

/** Escurece uma cor por um fator (0.0 = mesma cor, 1.0 = preto) */
private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}
