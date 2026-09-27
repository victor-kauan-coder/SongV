package com.songv.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.songv.app.letra.indiceLinhaAtual
import com.songv.app.letra.nomeIdioma
import com.songv.app.model.Letra
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import com.songv.app.player.ModoRepeticao
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.Navegador
import com.songv.app.ui.Rota
import com.songv.app.ui.components.BarraProgresso
import com.songv.app.ui.components.BotaoModo
import com.songv.app.ui.components.BotaoPlay
import com.songv.app.ui.components.BotaoPilula
import com.songv.app.ui.components.CapaArte
import com.songv.app.ui.components.LocalCapas
import com.songv.app.ui.theme.EstilosSongV
import com.songv.app.ui.theme.LocalCoresSongV
import com.songv.app.ui.theme.Marca
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** Escurece a cor da capa até virar um fundo onde texto branco sempre tem contraste. */
private fun tomDePalco(cor: Color): Color {
    var c = cor
    var passos = 0
    while (c.luminance() > 0.055f && passos < 30) {
        c = lerp(c, Color.Black, 0.1f)
        passos++
    }
    return c
}

private enum class Folha { TRADUCAO, TIMER, OPCOES_LETRA, SAIDA }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel, nav: Navegador, modifier: Modifier = Modifier) {
    val rep by vm.reproducao.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val estadoLetra by vm.letra.collectAsState()
    val timer by vm.timer.collectAsState()
    val saida by vm.saida.collectAsState()
    val noComputador = (saida as? com.songv.app.conexao.ConexaoComputador.Estado.Conectado)
        ?.takeIf { it.somNoComputador }?.computador?.nome
    val musica = rep.atual

    var modoLetra by rememberSaveable { mutableStateOf(false) }
    var folha by remember { mutableStateOf<Folha?>(null) }
    val contexto = LocalContext.current
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importarLetra) }
    val equalizador = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val capas = LocalCapas.current
    val capaPropria = com.songv.app.ui.components.LocalCapasProprias.current[musica?.id]
    val corCapa by produceState<Color?>(null, musica?.id, prefs.coresDaCapa, capaPropria) {
        value = if (prefs.coresDaCapa && musica != null) capas.corDominante(musica)?.let { Color(it) } else null
    }
    val sinal = LocalCoresSongV.current
    val tom by animateColorAsState(
        corCapa?.let(::tomDePalco) ?: lerp(Marca.Palco, sinal.sinal, 0.08f),
        tween(700),
        label = "tomPalco",
    )

    // Arrastar para baixo fecha o player (quando o conteúdo já está no topo).
    val densidade = LocalDensity.current
    val limiteFechar = with(densidade) { 160.dp.toPx() }
    val arraste = remember { Animatable(0f) }
    val escopo = rememberCoroutineScope()
    val conexaoFechar = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (arraste.value > 0f && available.y < 0f) {
                    val consumido = maxOf(available.y, -arraste.value)
                    escopo.launch { arraste.snapTo(arraste.value + consumido) }
                    return Offset(0f, consumido)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.Drag) {
                    escopo.launch { arraste.snapTo(arraste.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (arraste.value <= 0f) return Velocity.Zero
                if (arraste.value > limiteFechar || available.y > 2_500f) {
                    nav.playerAberto = false
                    arraste.snapTo(0f)
                } else {
                    arraste.animateTo(0f, spring(stiffness = 500f))
                }
                return available
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, arraste.value.roundToInt()) }
            .background(Brush.verticalGradient(0f to tom, 0.7f to Marca.Palco)),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                BarraTopo(
                    origem = rep.origem,
                    noComputador = noComputador,
                    modoLetra = modoLetra,
                    onFechar = { if (modoLetra) modoLetra = false else nav.playerAberto = false },
                    onMenu = { musica?.let { nav.menuMusica = it } },
                )
                if (musica == null) {
                    NadaTocando(Modifier.weight(1f)) { vm.alternarPlayPause() }
                    return@Column
                }
                if (modoLetra) {
                    ModoLetra(vm, musica, rep.tocando, estadoLetra, prefs, onFolha = { folha = it }, onImportar = { importar.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f))
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .nestedScroll(conexaoFechar)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        CarrosselCapas(vm, rep.fila.map { it.musica }, rep.posicao)
                        Spacer(Modifier.height(28.dp))
                        TituloFaixa(
                            musica = musica,
                            favorita = musica.id in prefs.favoritos,
                            onFavoritar = { vm.alternarFavorito(musica.id) },
                            onArtista = { nav.abrir(Rota.Artista(musica.artistas.first())) },
                        )
                        Spacer(Modifier.height(12.dp))
                        ProgressoPlayer(vm, Modifier.padding(horizontal = 24.dp))
                        Controles(vm, rep.tocando, rep.carregando, rep.aleatorio, rep.repeticao)
                        Spacer(Modifier.height(12.dp))
                        PainelAcoes(
                            letraAtiva = false,
                            timerAtivo = timer.ativo,
                            rotuloTimer = when {
                                timer.fimDaFaixa -> "Fim da faixa"
                                timer.terminaEm != null -> "${(timer.restanteMs() / 60_000 + 1)} min"
                                else -> "Timer"
                            },
                            onLetra = { modoLetra = true },
                            onFila = { nav.filaAberta = true },
                            onTimer = { folha = Folha.TIMER },
                            rotuloSaida = if (prefs.tocarNoComputador || noComputador != null) noComputador ?: "Tocar em" else null,
                            saidaAtiva = noComputador != null,
                            onSaida = { folha = Folha.SAIDA },
                            onEqualizador = {
                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, vm.player.audioSessionId)
                                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, contexto.packageName)
                                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                try {
                                    equalizador.launch(intent)
                                } catch (_: ActivityNotFoundException) {
                                    vm.avisar("Nenhum equalizador encontrado neste aparelho")
                                }
                            },
                        )
                        Spacer(Modifier.height(20.dp))
                        PreviaLetra(
                            vm = vm,
                            estadoLetra = estadoLetra,
                            buscaOnline = prefs.buscaOnline,
                            atrasoMs = prefs.atrasosLetra[musica.id] ?: 0L,
                            traduzindo = prefs.traduzirAutomaticamente,
                            onAbrir = { modoLetra = true },
                            onImportar = { importar.launch(arrayOf("*/*")) },
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }

    when (folha) {
        Folha.TRADUCAO -> TraducaoSheet(vm, prefs, estadoLetra, onFechar = { folha = null })
        Folha.TIMER -> TimerSheet(vm, timer, onFechar = { folha = null })
        Folha.SAIDA -> SaidaSheet(vm, onFechar = { folha = null })
        Folha.OPCOES_LETRA -> OpcoesLetraSheet(vm, estadoLetra, prefs.buscaOnline, onImportar = { importar.launch(arrayOf("*/*")) }, onFechar = { folha = null })
        null -> Unit
    }
}

@Composable
private fun BarraTopo(origem: String?, noComputador: String?, modoLetra: Boolean, onFechar: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onFechar) {
            Icon(
                if (modoLetra) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (modoLetra) "Voltar para a capa" else "Fechar o player",
                modifier = Modifier.size(30.dp),
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tocando de", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Text(origem ?: "Sua biblioteca", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (noComputador != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Rounded.Computer, contentDescription = null, tint = LocalCoresSongV.current.sinalNoPalco, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Tocando no $noComputador",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalCoresSongV.current.sinalNoPalco,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, contentDescription = "Opções da faixa") }
    }
}

@Composable
private fun NadaTocando(modifier: Modifier, onTocar: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Nada tocando", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Escolha uma faixa ou comece pela biblioteca inteira.", color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(20.dp))
        BotaoPilula("Tocar aleatório", onTocar, icone = Icons.Rounded.Shuffle)
    }
}

/** As capas da fila lado a lado; arrastar troca de faixa. A atual vem em alta resolução. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarrosselCapas(vm: PlayerViewModel, fila: List<Musica>, posicao: Int) {
    if (fila.isEmpty() || posicao < 0) return
    val posicaoAtual by rememberUpdatedState(posicao)
    val filaAtual by rememberUpdatedState(fila)
    val estado = rememberPagerState(initialPage = posicao) { filaAtual.size }

    LaunchedEffect(posicao) {
        if (estado.currentPage != posicao && posicao in 0 until estado.pageCount) {
            if ((estado.currentPage - posicao).absoluteValue > 2) estado.scrollToPage(posicao) else estado.animateScrollToPage(posicao)
        }
    }
    LaunchedEffect(estado) {
        snapshotFlow { estado.settledPage }.drop(1).collect { pagina ->
            if (pagina != posicaoAtual) vm.reproducao.value.fila.getOrNull(pagina)?.let(vm::tocarItem)
        }
    }

    HorizontalPager(
        state = estado,
        contentPadding = PaddingValues(horizontal = 28.dp),
        pageSpacing = 14.dp,
        key = { i -> filaAtual.getOrNull(i)?.id?.let { "$it#$i" } ?: i },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) { pagina ->
        val deslocamento = ((estado.currentPage - pagina) + estado.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
        CapaArte(
            musica = fila.getOrNull(pagina),
            grande = pagina == estado.currentPage,
            forma = MaterialTheme.shapes.medium,
            descricao = fila.getOrNull(pagina)?.let { "Capa de ${it.titulo}" },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    val escala = 1f - 0.12f * deslocamento
                    scaleX = escala
                    scaleY = escala
                    alpha = 1f - 0.45f * deslocamento
                }
                .shadow(elevation = 24.dp, shape = MaterialTheme.shapes.medium, ambientColor = Color.Black, spotColor = Color.Black),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TituloFaixa(musica: Musica, favorita: Boolean, onFavoritar: () -> Unit, onArtista: () -> Unit) {
    val sinal = LocalCoresSongV.current.sinalNoPalco
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                musica.titulo,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
            )
            Text(
                musica.artista,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClickLabel = "Ver artista", onClick = onArtista),
            )
        }
        IconButton(onClick = onFavoritar) {
            Icon(
                if (favorita) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorita) "Remover das favoritas" else "Adicionar às favoritas",
                tint = if (favorita) sinal else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ProgressoPlayer(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val p by vm.progresso.collectAsState()
    BarraProgresso(
        posicaoMs = p.posicaoMs,
        duracaoMs = p.duracaoMs,
        onBuscar = vm::buscar,
        corAtiva = LocalCoresSongV.current.sinal,
        corTrilho = Color.White.copy(alpha = 0.2f),
        corTexto = Color.White.copy(alpha = 0.65f),
        modifier = modifier,
    )
}

@Composable
private fun Controles(vm: PlayerViewModel, tocando: Boolean, carregando: Boolean, aleatorio: Boolean, repeticao: ModoRepeticao) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val corAtiva = LocalCoresSongV.current.sinalNoPalco
        BotaoModo(Icons.Rounded.Shuffle, "Aleatório", aleatorio, vm::alternarAleatorio, corAtiva = corAtiva)
        IconButton(onClick = vm::anterior, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Faixa anterior", modifier = Modifier.size(38.dp))
        }
        BotaoPlay(tocando, vm::alternarPlayPause, tamanho = 76.dp, carregando = carregando && !tocando)
        IconButton(onClick = vm::proxima, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima faixa", modifier = Modifier.size(38.dp))
        }
        BotaoModo(
            if (repeticao == ModoRepeticao.UMA) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            when (repeticao) {
                ModoRepeticao.DESLIGADO -> "Repetir"
                ModoRepeticao.TUDO -> "Repetir a fila"
                ModoRepeticao.UMA -> "Repetir esta faixa"
            },
            repeticao != ModoRepeticao.DESLIGADO,
            vm::alternarRepeticao,
            corAtiva = corAtiva,
        )
    }
}

/** Painel de botões rotulados, como a fileira de teclas de um aparelho de som. */
@Composable
private fun PainelAcoes(
    letraAtiva: Boolean,
    timerAtivo: Boolean,
    rotuloTimer: String,
    onLetra: () -> Unit,
    onFila: () -> Unit,
    onTimer: () -> Unit,
    onEqualizador: () -> Unit,
    rotuloSaida: String?,
    saidaAtiva: Boolean,
    onSaida: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TeclaAcao(Icons.Rounded.Lyrics, "Letra", letraAtiva, onLetra, Modifier.weight(1f))
        TeclaAcao(Icons.AutoMirrored.Rounded.QueueMusic, "Fila", false, onFila, Modifier.weight(1f))
        TeclaAcao(Icons.Rounded.Bedtime, rotuloTimer, timerAtivo, onTimer, Modifier.weight(1f))
        TeclaAcao(Icons.Rounded.Equalizer, "Equalizador", false, onEqualizador, Modifier.weight(1f))
        if (rotuloSaida != null) {
            TeclaAcao(if (saidaAtiva) Icons.Rounded.Computer else Icons.Rounded.Devices, rotuloSaida, saidaAtiva, onSaida, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TeclaAcao(icone: ImageVector, rotulo: String, ativa: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sinal = LocalCoresSongV.current.sinalNoPalco
    Column(
        modifier.clickable(onClickLabel = rotulo, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icone, contentDescription = null, tint = if (ativa) sinal else Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = if (ativa) sinal else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Cartão abaixo dos controles: a linha atual e a próxima, como uma legenda. Toque para abrir. */
@Composable
private fun PreviaLetra(
    vm: PlayerViewModel,
    estadoLetra: com.songv.app.player.EstadoLetra,
    buscaOnline: Boolean,
    atrasoMs: Long,
    traduzindo: Boolean,
    onAbrir: () -> Unit,
    onImportar: () -> Unit,
) {
    val letra = estadoLetra.letra ?: return
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        if (letra.tipo == TipoLetra.AUSENTE) {
            Box(Modifier.padding(vertical = 24.dp)) {
                SemLetra(estadoLetra.buscando, buscaOnline, vm::buscarLetraOnline, onImportar, onPesquisar = vm::abrirPesquisaLetra)
            }
            return@Surface
        }
        Column(Modifier.clickable(onClickLabel = "Abrir a letra", onClick = onAbrir).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Letra", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (traduzindo && estadoLetra.traducao != null) {
                    Text(
                        "Traduzida · ${nomeIdioma(estadoLetra.traducao.idioma)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalCoresSongV.current.sinalNoPalco,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val p by vm.progresso.collectAsState()
            val linhas = letra.linhas
            val atual = if (letra.tipo == TipoLetra.SINCRONIZADA) indiceLinhaAtual(linhas, p.posicaoMs + atrasoMs) else -1
            val indices = (atual.coerceAtLeast(0) until linhas.size).filter { linhas[it].texto.isNotBlank() }.take(3)
            indices.forEachIndexed { i, idx ->
                val l = linhas[idx]
                Text(
                    l.texto,
                    style = EstilosSongV.letra.copy(fontSize = EstilosSongV.letra.fontSize * 0.78f, lineHeight = EstilosSongV.letra.lineHeight * 0.78f),
                    color = Color.White.copy(alpha = if (i == 0 && atual >= 0) 1f else 0.5f),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
                estadoLetra.traducao?.linhas?.getOrNull(idx)?.takeIf { i == 0 && !it.equals(l.texto, true) }?.let { t ->
                    Text(t, style = EstilosSongV.traducao, color = lerp(LocalCoresSongV.current.sinalNoPalco, Color.White, 0.42f))
                }
            }
        }
    }
}

// =============================================================================
// Modo letra (tela cheia)
// =============================================================================

@Composable
private fun ModoLetra(
    vm: PlayerViewModel,
    musica: Musica,
    tocando: Boolean,
    estadoLetra: com.songv.app.player.EstadoLetra,
    prefs: com.songv.app.data.Preferencias,
    onFolha: (Folha) -> Unit,
    onImportar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val atraso = prefs.atrasosLetra[musica.id] ?: 0L
    var ajustandoSincronia by rememberSaveable { mutableStateOf(false) }
    val letra: Letra? = estadoLetra.letra
    val sinal = LocalCoresSongV.current.sinalNoPalco

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CapaArte(musica, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(musica.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(musica.artista, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (letra != null && letra.tipo != TipoLetra.AUSENTE) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ligada = prefs.traduzirAutomaticamente
                Ferramenta(
                    Icons.Rounded.Translate,
                    if (ligada) nomeIdioma(prefs.idiomaTraducao) else "Traduzir",
                    ativa = ligada,
                ) { onFolha(Folha.TRADUCAO) }
                if (letra.tipo == TipoLetra.SINCRONIZADA) {
                    Ferramenta(Icons.Rounded.SyncAlt, if (atraso == 0L) "Sincronia" else formatarAtraso(atraso), ativa = ajustandoSincronia || atraso != 0L) {
                        ajustandoSincronia = !ajustandoSincronia
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onFolha(Folha.OPCOES_LETRA) }) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "Mais opções da letra")
                }
            }
            AnimatedVisibility(ajustandoSincronia, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                AjusteSincronia(atraso, onAjustar = vm::ajustarAtraso)
            }
            if (estadoLetra.traduzindo) {
                LinearProgressIndicator(color = sinal, trackColor = Color.White.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(2.dp))
            }
            estadoLetra.avisoTraducao?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                letra == null -> CarregandoLetra(Modifier.align(Alignment.Center))
                letra.tipo == TipoLetra.AUSENTE -> SemLetra(estadoLetra.buscando, prefs.buscaOnline, vm::buscarLetraOnline, onImportar, Modifier.align(Alignment.Center), onPesquisar = vm::abrirPesquisaLetra)
                else -> {
                    val p by vm.progresso.collectAsState()
                    LetraView(
                        letra = letra,
                        posicaoMs = { p.posicaoMs + atraso },
                        traducao = if (prefs.traduzirAutomaticamente) estadoLetra.traducao else null,
                        exibicao = prefs.exibicaoTraducao,
                        mostrarRomanizacao = prefs.mostrarRomanizacao,
                        escala = prefs.escalaLetra,
                        onBuscar = { t -> vm.buscar((t - atraso).coerceAtLeast(0)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Controles compactos: a letra ocupa a tela, os controles ficam à mão.
        ProgressoPlayer(vm, Modifier.padding(horizontal = 24.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::anterior, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Faixa anterior", modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(24.dp))
            BotaoPlay(tocando, vm::alternarPlayPause, tamanho = 60.dp)
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = vm::proxima, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima faixa", modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun Ferramenta(icone: ImageVector, texto: String, ativa: Boolean, onClick: () -> Unit) {
    val cores = LocalCoresSongV.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (ativa) cores.sinal else Color.White.copy(alpha = 0.1f),
        contentColor = if (ativa) cores.noSinal else Color.White,
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(texto, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private fun formatarAtraso(ms: Long): String {
    val s = ms / 1000f
    return (if (ms > 0) "+" else "") + String.format(java.util.Locale("pt", "BR"), "%.2f s", s).replace(",00", ",0")
}

@Composable
private fun AjusteSincronia(atrasoMs: Long, onAjustar: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).background(Color.White.copy(alpha = 0.06f), MaterialTheme.shapes.medium).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                when {
                    atrasoMs > 0 -> "Letra adiantada ${formatarAtraso(atrasoMs)}"
                    atrasoMs < 0 -> "Letra atrasada ${formatarAtraso(-atrasoMs).removePrefix("+")}"
                    else -> "Letra no tempo original"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Text("Salvo só para esta faixa", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        BotaoAjuste("−0,25") { onAjustar(-250) }
        BotaoAjuste("+0,25") { onAjustar(250) }
        if (atrasoMs != 0L) BotaoAjuste("Zerar") { onAjustar(0) }
    }
}

@Composable
private fun BotaoAjuste(texto: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White.copy(alpha = 0.12f), contentColor = Color.White, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 6.dp)) {
        Text(texto, style = MaterialTheme.typography.labelLarge.merge(EstilosSongV.numeros), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
